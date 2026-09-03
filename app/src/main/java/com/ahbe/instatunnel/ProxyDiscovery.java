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

final class ProxyDiscovery {
    interface StatusCallback {
        void onStatus(String message);
    }

    static final class ProxyNode {
        final String host;
        final int port;
        final int latencyMs;

        ProxyNode(String host, int port, int latencyMs) {
            this.host = host;
            this.port = port;
            this.latencyMs = latencyMs;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private static final String[] SOURCES = new String[] {
            "https://raw.githubusercontent.com/proxio-io/proxy-list/main/socks5.txt",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
            "https://raw.githubusercontent.com/iplocate/free-proxy-list/main/protocols/socks5.txt"
    };

    // چند seed برای زمانی که منابع آنلاین موقتاً در دسترس نیستند. این‌ها نیز عمومی و ناپایدارند.
    private static final String[] FALLBACK = new String[] {
            "95.244.3.200:1080", "161.35.90.93:1082", "192.252.208.67:14287",
            "103.162.57.42:1080", "5.255.117.127:1080", "80.249.81.179:9050",
            "72.206.74.126:4145", "184.181.217.213:4145", "72.195.34.41:4145",
            "47.251.127.154:1080", "209.141.44.165:1080", "128.199.37.92:1080",
            "98.175.31.195:4145", "67.201.58.190:4145", "192.252.215.2:4145",
            "208.102.51.6:58208", "72.207.33.64:4145", "184.178.172.28:15294"
    };

    private ProxyDiscovery() {}

    static ProxyNode findBest(StatusCallback callback) throws Exception {
        List<String> candidates = loadCandidates(callback);
        if (candidates.isEmpty()) throw new Exception("هیچ مسیر اولیه‌ای پیدا نشد.");

        Collections.shuffle(candidates);
        int maxTests = Math.min(180, candidates.size());
        candidates = new ArrayList<>(candidates.subList(0, maxTests));

        callback.onStatus("در حال تست " + candidates.size() + " مسیر عمومی…");

        ExecutorService pool = Executors.newFixedThreadPool(28);
        CompletionService<ProxyNode> completion = new ExecutorCompletionService<>(pool);
        for (String value : candidates) {
            completion.submit(() -> test(value));
        }

        ProxyNode best = null;
        int completed = 0;
        int successes = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(24);

        try {
            while (completed < candidates.size() && System.nanoTime() < deadline) {
                Future<ProxyNode> f = completion.poll(900, TimeUnit.MILLISECONDS);
                if (f == null) continue;
                completed++;
                ProxyNode node;
                try {
                    node = f.get();
                } catch (Exception ignored) {
                    continue;
                }
                if (node == null) continue;
                successes++;
                if (best == null || node.latencyMs < best.latencyMs) {
                    best = node;
                    callback.onStatus("مسیر سالم پیدا شد؛ در حال بررسی مسیر سریع‌تر…");
                }
                if (successes >= 7 && best != null && best.latencyMs <= 900) break;
            }
        } finally {
            pool.shutdownNow();
        }

        if (best == null) {
            throw new Exception("مسیر SOCKS5 سالم برای Instagram پیدا نشد. دوباره تلاش کنید.");
        }
        return best;
    }

    private static List<String> loadCandidates(StatusCallback callback) {
        Set<String> nodes = new HashSet<>();
        callback.onStatus("در حال دریافت فهرست مسیرهای عمومی…");

        for (String source : SOURCES) {
            if (nodes.size() >= 450) break;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(source).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(7000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "InstaTunnel/0.1");
                if (c.getResponseCode() / 100 != 2) {
                    c.disconnect();
                    continue;
                }
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    int fromThisSource = 0;
                    while ((line = br.readLine()) != null && fromThisSource < 250) {
                        line = line.trim();
                        if (isHostPort(line)) {
                            nodes.add(line);
                            fromThisSource++;
                        }
                    }
                } finally {
                    c.disconnect();
                }
            } catch (Exception ignored) {
            }
        }

        Collections.addAll(nodes, FALLBACK);
        return new ArrayList<>(nodes);
    }

    private static boolean isHostPort(String value) {
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1) return false;
        try {
            int port = Integer.parseInt(value.substring(colon + 1));
            return port > 0 && port <= 65535 && value.substring(0, colon).length() <= 255;
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
        } catch (Exception e) {
            return null;
        }

        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1600);
            socket.setSoTimeout(2600);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            out.write(new byte[] {0x05, 0x01, 0x00});
            out.flush();
            byte[] hello = readFully(in, 2);
            if (hello[0] != 0x05 || hello[1] != 0x00) return null;

            byte[] domain = "www.instagram.com".getBytes(StandardCharsets.US_ASCII);
            byte[] req = new byte[7 + domain.length];
            req[0] = 0x05; // version
            req[1] = 0x01; // connect
            req[2] = 0x00;
            req[3] = 0x03; // domain
            req[4] = (byte) domain.length;
            System.arraycopy(domain, 0, req, 5, domain.length);
            int p = 5 + domain.length;
            req[p] = 0x01;      // 443 high byte
            req[p + 1] = (byte) 0xBB; // 443 low byte
            out.write(req);
            out.flush();

            byte[] head = readFully(in, 4);
            if (head[0] != 0x05 || head[1] != 0x00) return null;

            int atyp = head[3] & 0xff;
            if (atyp == 0x01) {
                readFully(in, 4 + 2);
            } else if (atyp == 0x04) {
                readFully(in, 16 + 2);
            } else if (atyp == 0x03) {
                int len = readFully(in, 1)[0] & 0xff;
                readFully(in, len + 2);
            } else {
                return null;
            }

            int latency = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return new ProxyNode(host, port, Math.max(latency, 1));
        } catch (Exception ignored) {
            return null;
        }
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
