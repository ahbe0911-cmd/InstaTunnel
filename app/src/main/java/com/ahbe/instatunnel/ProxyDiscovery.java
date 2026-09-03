package com.ahbe.instatunnel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class ProxyDiscovery {
    interface StatusCallback {
        void onStatus(String message);
    }

    static final class ProxyNode {
        final String host;
        final int port;
        final int latencyMs;
        final boolean udpSupported;

        ProxyNode(String host, int port, int latencyMs, boolean udpSupported) {
            this.host = host;
            this.port = port;
            this.latencyMs = latencyMs;
            this.udpSupported = udpSupported;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private static final String TEST_HOST = "www.instagram.com";
    private static final int TEST_PORT = 443;

    private static final String[] SOURCES = new String[] {
            "https://raw.githubusercontent.com/proxio-io/proxy-list/main/socks5.txt",
            "https://cdn.jsdelivr.net/gh/proxio-io/proxy-list@main/socks5.txt",
            "https://raw.githubusercontent.com/proxmint/free-proxy-list/main/proxies/socks5.txt",
            "https://cdn.jsdelivr.net/gh/proxmint/free-proxy-list@main/proxies/socks5.txt",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks5.txt",
            "https://raw.githubusercontent.com/nguywnben/daily-proxy-updates/main/proxies/socks5.txt",
            "https://raw.githubusercontent.com/FosterG4/proxy-list/main/socks5.txt"
    };

    // چند مسیر اولیه که فقط در صورت مسدود بودن منابع آنلاین استفاده می‌شوند.
    // در هر اتصال باز هم با SOCKS5 + TLS واقعی اعتبارسنجی می‌شوند.
    private static final String[] FALLBACK = new String[] {
            "148.72.206.131:45395",
            "159.65.172.240:59166",
            "94.131.2.30:32099",
            "209.159.153.21:40736",
            "142.54.229.249:4145",
            "192.252.208.70:14282",
            "192.162.84.150:31999",
            "192.252.216.81:4145",
            "143.198.229.56:46370",
            "192.111.137.35:4145",
            "163.172.132.238:16379",
            "165.154.233.243:19092"
    };

    private ProxyDiscovery() {}

    static ProxyNode findBest(StatusCallback callback) throws Exception {
        List<String> candidates = loadCandidates(callback);
        if (candidates.isEmpty()) {
            throw new Exception("فهرست مسیرهای اتصال دریافت نشد. اینترنت عادی یا DNS گوشی را بررسی کن.");
        }

        Collections.shuffle(candidates);
        int maxTests = Math.min(240, candidates.size());
        candidates = new ArrayList<>(candidates.subList(0, maxTests));
        callback.onStatus("در حال تست واقعی " + maxTests + " مسیر با Instagram…");

        ExecutorService pool = Executors.newFixedThreadPool(36);
        CompletionService<ProxyNode> completion = new ExecutorCompletionService<>(pool);
        for (String value : candidates) completion.submit(() -> test(value));

        ProxyNode bestTcp = null;
        ProxyNode bestUdp = null;
        int completed = 0;
        int successes = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(22);

        try {
            while (completed < candidates.size() && System.nanoTime() < deadline) {
                Future<ProxyNode> future = completion.poll(700, TimeUnit.MILLISECONDS);
                if (future == null) continue;
                completed++;

                ProxyNode node;
                try {
                    node = future.get();
                } catch (Throwable ignored) {
                    continue;
                }
                if (node == null) continue;

                successes++;
                if (bestTcp == null || node.latencyMs < bestTcp.latencyMs) {
                    bestTcp = node;
                    callback.onStatus("یک مسیر TLS سالم پیدا شد؛ در حال پیدا کردن مسیر بهتر…");
                }
                if (node.udpSupported && (bestUdp == null || node.latencyMs < bestUdp.latencyMs)) {
                    bestUdp = node;
                    callback.onStatus("مسیر کامل TCP/UDP پیدا شد؛ در حال نهایی‌سازی…");
                }

                if (bestUdp != null && successes >= 3 && bestUdp.latencyMs <= 4200) break;
                if (bestTcp != null && successes >= 8 && bestTcp.latencyMs <= 2500) break;
            }
        } finally {
            pool.shutdownNow();
        }

        ProxyNode selected = bestUdp != null ? bestUdp : bestTcp;
        if (selected == null) {
            throw new Exception("هیچ مسیر SOCKS5 که اتصال واقعی TLS به Instagram برقرار کند پیدا نشد. دوباره تلاش کن.");
        }
        return selected;
    }

    private static List<String> loadCandidates(StatusCallback callback) {
        callback.onStatus("در حال دریافت فهرست‌های تازه مسیر…");
        Set<String> nodes = Collections.synchronizedSet(new HashSet<>());
        ExecutorService sourcePool = Executors.newFixedThreadPool(Math.min(SOURCES.length, 6));
        List<Future<?>> futures = new ArrayList<>();

        for (String source : SOURCES) {
            futures.add(sourcePool.submit(() -> fetchSource(source, nodes)));
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(9);
        for (Future<?> future : futures) {
            long left = deadline - System.nanoTime();
            if (left <= 0) break;
            try {
                future.get(Math.max(1, TimeUnit.NANOSECONDS.toMillis(left)), TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
        }
        sourcePool.shutdownNow();

        Collections.addAll(nodes, FALLBACK);
        return new ArrayList<>(nodes);
    }

    private static void fetchSource(String source, Set<String> nodes) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(4500);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "InstaTunnel/0.3 Android");
            connection.setRequestProperty("Accept", "text/plain,*/*");
            if (connection.getResponseCode() / 100 != 2) return;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int accepted = 0;
                while ((line = reader.readLine()) != null && accepted < 450 && nodes.size() < 1400) {
                    String value = extractHostPort(line);
                    if (value != null && nodes.add(value)) accepted++;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String extractHostPort(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.startsWith("#")) return null;

        int scheme = value.indexOf("://");
        if (scheme >= 0) value = value.substring(scheme + 3).trim();

        String[] pieces = value.split("\\s+");
        if (pieces.length > 1) value = pieces[pieces.length - 1].trim();

        int slash = value.indexOf('/');
        if (slash > 0) value = value.substring(0, slash);

        return isHostPort(value) ? value : null;
    }

    private static boolean isHostPort(String value) {
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1) return false;
        String host = value.substring(0, colon).trim();
        if (host.isEmpty() || host.length() > 255 || host.contains(" ")) return false;
        try {
            int port = Integer.parseInt(value.substring(colon + 1));
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static ProxyNode test(String value) {
        int colon = value.lastIndexOf(':');
        if (colon < 1) return null;
        String host = value.substring(0, colon).trim();
        int port;
        try {
            port = Integer.parseInt(value.substring(colon + 1).trim());
        } catch (Throwable e) {
            return null;
        }

        long started = System.nanoTime();
        if (!tlsProbe(host, port)) return null;
        int latency = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        boolean udp = udpAssociateProbe(host, port);
        return new ProxyNode(host, port, Math.max(1, latency), udp);
    }

    private static boolean tlsProbe(String proxyHost, int proxyPort) {
        Socket raw = new Socket();
        try {
            raw.connect(new InetSocketAddress(proxyHost, proxyPort), 1900);
            raw.setSoTimeout(4200);
            if (!socksGreeting(raw)) return false;
            if (!socksConnect(raw, TEST_HOST, TEST_PORT)) return false;

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket ssl = (SSLSocket) factory.createSocket(raw, TEST_HOST, TEST_PORT, true)) {
                ssl.setUseClientMode(true);
                ssl.setSoTimeout(4800);
                ssl.startHandshake();
                return HttpsURLConnection.getDefaultHostnameVerifier().verify(TEST_HOST, ssl.getSession());
            }
        } catch (Throwable ignored) {
            try { raw.close(); } catch (Throwable ignoredClose) {}
            return false;
        }
    }

    private static boolean udpAssociateProbe(String proxyHost, int proxyPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(proxyHost, proxyPort), 1300);
            socket.setSoTimeout(2200);
            if (!socksGreeting(socket)) return false;

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(new byte[]{0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
            out.flush();
            return readSocksReply(in);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean socksGreeting(Socket socket) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        out.write(new byte[]{0x05, 0x01, 0x00});
        out.flush();
        byte[] hello = readFully(in, 2);
        return hello[0] == 0x05 && hello[1] == 0x00;
    }

    private static boolean socksConnect(Socket socket, String domainName, int port) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        byte[] domain = domainName.getBytes(StandardCharsets.US_ASCII);
        if (domain.length > 255) return false;

        byte[] request = new byte[7 + domain.length];
        request[0] = 0x05;
        request[1] = 0x01;
        request[2] = 0x00;
        request[3] = 0x03;
        request[4] = (byte) domain.length;
        System.arraycopy(domain, 0, request, 5, domain.length);
        int p = 5 + domain.length;
        request[p] = (byte) ((port >>> 8) & 0xff);
        request[p + 1] = (byte) (port & 0xff);
        out.write(request);
        out.flush();
        return readSocksReply(in);
    }

    private static boolean readSocksReply(InputStream in) throws Exception {
        byte[] head = readFully(in, 4);
        if (head[0] != 0x05 || head[1] != 0x00) return false;

        int atyp = head[3] & 0xff;
        if (atyp == 0x01) {
            readFully(in, 6);
        } else if (atyp == 0x04) {
            readFully(in, 18);
        } else if (atyp == 0x03) {
            int len = readFully(in, 1)[0] & 0xff;
            readFully(in, len + 2);
        } else {
            return false;
        }
        return true;
    }

    private static byte[] readFully(InputStream in, int count) throws Exception {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int n = in.read(data, offset, count - offset);
            if (n < 0) throw new Exception("EOF");
            offset += n;
        }
        return data;
    }
}
