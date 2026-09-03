package com.ahbe.instatunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hev.htproxy.TProxyService;

public class InstaVpnService extends VpnService {
    public static final String ACTION_START = "com.ahbe.instatunnel.START";
    public static final String ACTION_STOP = "com.ahbe.instatunnel.STOP";
    public static final String ACTION_STATUS = "com.ahbe.instatunnel.STATUS";

    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_LATENCY = "latency";
    public static final String EXTRA_TARGET_PACKAGE = "target_package";
    public static final String EXTRA_TARGET_LABEL = "target_label";

    public static final String PREFS_NAME = "instatunnel_prefs";
    public static final String PREF_TARGET_PACKAGE = "target_package";
    public static final String PREF_TARGET_LABEL = "target_label";

    private static final String CHANNEL_ID = "instatunnel_vpn";
    private static final int NOTIFICATION_ID = 41;
    private static final int LOCAL_SOCKS_PORT = 10808;
    private static final String MAPPED_DNS = "198.18.0.2";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean stopping;
    private volatile boolean starting;
    private volatile boolean tunnelStarted;
    private ParcelFileDescriptor vpnInterface;
    private String targetPackage = "";
    private String targetLabel = "";
    private ProfileManager.Profile activeProfile;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopTunnel("اتصال قطع شد.");
            return START_NOT_STICKY;
        }

        readTarget(intent);
        if (!starting && !tunnelStarted) {
            startForeground(NOTIFICATION_ID, buildNotification("در حال آماده‌سازی کانفیگ…", false));
            starting = true;
            stopping = false;
            sendStatus("connecting", "در حال آماده‌سازی کانفیگ انتخاب‌شده…", -1);
            worker.execute(this::connectInBackground);
        }
        return START_STICKY;
    }

    private void readTarget(Intent intent) {
        String requestedPackage = intent == null ? null : intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        String requestedLabel = intent == null ? null : intent.getStringExtra(EXTRA_TARGET_LABEL);

        if (requestedPackage != null && !requestedPackage.trim().isEmpty()) {
            targetPackage = requestedPackage.trim();
            targetLabel = requestedLabel == null || requestedLabel.trim().isEmpty()
                    ? targetPackage
                    : requestedLabel.trim();

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_TARGET_PACKAGE, targetPackage)
                    .putString(PREF_TARGET_LABEL, targetLabel)
                    .apply();
            return;
        }

        if (targetPackage.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            targetPackage = prefs.getString(PREF_TARGET_PACKAGE, "");
            targetLabel = prefs.getString(PREF_TARGET_LABEL, "");
        }
    }

    private void connectInBackground() {
        try {
            if (targetPackage.isEmpty()) {
                throw new Exception("هیچ برنامه‌ای برای عبور از تونل انتخاب نشده است.");
            }
            if (!isTargetInstalled()) {
                throw new Exception("برنامه انتخاب‌شده روی گوشی نصب نیست: " + targetPackage);
            }

            activeProfile = ProfileManager.getSelected(this);
            if (activeProfile == null) {
                throw new Exception("کانفیگی انتخاب نشده است. از بخش «کانفیگ و ساب» یک کانفیگ اضافه و انتخاب کن.");
            }

            sendStatus("connecting", "راه‌اندازی " + activeProfile.displayType() + " • " + activeProfile.name + "…", -1);
            String xrayConfig = ProfileManager.buildXrayConfig(activeProfile, LOCAL_SOCKS_PORT);
            XrayEngine.start(this, xrayConfig);
            if (stopping) return;

            Thread.sleep(250);
            int latency = probeLocalSocks();
            if (stopping) return;

            sendStatus("connecting", "کانفیگ با اتصال واقعی به Instagram تایید شد؛ در حال ساخت VPN…", latency);
            establishVpn();
            if (stopping) return;

            updateNotification(displayTarget() + " • " + activeProfile.name, true);
            sendStatus(
                    "connected",
                    "متصل شد. فقط ترافیک " + displayTarget() + " از «" + activeProfile.name + "» عبور می‌کند.",
                    latency
            );
        } catch (Throwable e) {
            cleanup();
            starting = false;
            String message = e.getMessage() == null ? "اتصال برقرار نشد." : e.getMessage();
            sendStatus("error", message, -1);
            stopForeground(true);
            stopSelf();
        }
    }

    private int probeLocalSocks() throws Exception {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", LOCAL_SOCKS_PORT), 3500);
            socket.setSoTimeout(9000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            out.write(new byte[]{0x05, 0x01, 0x00});
            out.flush();
            byte[] hello = readExactly(in, 2);
            if (hello[0] != 0x05 || hello[1] != 0x00) {
                throw new Exception("SOCKS داخلی Xray پاسخ معتبر نداد.");
            }

            byte[] domain = "www.instagram.com".getBytes(StandardCharsets.US_ASCII);
            byte[] req = new byte[7 + domain.length];
            req[0] = 0x05;
            req[1] = 0x01;
            req[2] = 0x00;
            req[3] = 0x03;
            req[4] = (byte) domain.length;
            System.arraycopy(domain, 0, req, 5, domain.length);
            req[5 + domain.length] = 0x01;
            req[6 + domain.length] = (byte) 0xBB;
            out.write(req);
            out.flush();

            byte[] head = readExactly(in, 4);
            if (head[0] != 0x05 || head[1] != 0x00) {
                int code = head[1] & 0xff;
                throw new Exception("کانفیگ نتوانست به Instagram وصل شود (SOCKS code " + code + ").");
            }
            skipSocksAddress(in, head[3] & 0xff);
        }
        return (int) ((System.nanoTime() - start) / 1_000_000L);
    }

    private byte[] readExactly(InputStream in, int count) throws Exception {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int n = in.read(data, offset, count - offset);
            if (n < 0) throw new Exception("پاسخ SOCKS ناقص بود.");
            offset += n;
        }
        return data;
    }

    private void skipSocksAddress(InputStream in, int atyp) throws Exception {
        if (atyp == 0x01) {
            readExactly(in, 4 + 2);
        } else if (atyp == 0x04) {
            readExactly(in, 16 + 2);
        } else if (atyp == 0x03) {
            int len = readExactly(in, 1)[0] & 0xff;
            readExactly(in, len + 2);
        } else {
            throw new Exception("پاسخ SOCKS نوع آدرس ناشناخته داشت.");
        }
    }

    private boolean isTargetInstalled() {
        try {
            getPackageManager().getApplicationInfo(targetPackage, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String displayTarget() {
        if (targetLabel != null && !targetLabel.trim().isEmpty()) return targetLabel.trim();
        if (targetPackage != null && !targetPackage.trim().isEmpty()) return targetPackage.trim();
        return "برنامه انتخاب‌شده";
    }

    private void establishVpn() throws Exception {
        Builder builder = new Builder()
                .setSession("InstaTunnel/per-App")
                .setBlocking(false)
                .setMtu(1400)
                .addAddress("198.18.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:1:fd00:1::1", 64)
                .addRoute("::", 0)
                .addDnsServer(MAPPED_DNS);

        builder.addAllowedApplication(targetPackage);
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);

        vpnInterface = builder.establish();
        if (vpnInterface == null) throw new Exception("Android نتوانست رابط VPN را ایجاد کند.");

        File config = new File(getCacheDir(), "hev-instatunnel.yml");
        String yaml = "misc:\n" +
                "  task-stack-size: 86016\n" +
                "  connect-timeout: 10000\n" +
                "  tcp-read-write-timeout: 300000\n" +
                "  udp-read-write-timeout: 60000\n" +
                "  log-level: warn\n" +
                "tunnel:\n" +
                "  name: tun0\n" +
                "  mtu: 1400\n" +
                "  ipv4: 198.18.0.1\n" +
                "  ipv6: 'fd00:1:fd00:1::1'\n" +
                "  icmp: 'reply'\n" +
                "socks5:\n" +
                "  address: '127.0.0.1'\n" +
                "  port: " + LOCAL_SOCKS_PORT + "\n" +
                "  udp: 'udp'\n" +
                "mapdns:\n" +
                "  address: " + MAPPED_DNS + "\n" +
                "  port: 53\n" +
                "  network: 240.0.0.0\n" +
                "  netmask: 240.0.0.0\n" +
                "  cache-size: 10000\n";

        try (FileOutputStream out = new FileOutputStream(config, false)) {
            out.write(yaml.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        boolean started = TProxyService.TProxyStartService(config.getAbsolutePath(), vpnInterface.getFd());
        if (!started) throw new Exception("موتور TUN نتوانست شروع شود.");

        Thread.sleep(350);
        if (!TProxyService.TProxyIsRunning()) {
            throw new Exception("موتور TUN بلافاصله متوقف شد.");
        }

        tunnelStarted = true;
        starting = false;
    }

    private synchronized void stopTunnel(String message) {
        stopping = true;
        cleanup();
        starting = false;
        sendStatus("disconnected", message, -1);
        stopForeground(true);
        stopSelf();
    }

    private synchronized void cleanup() {
        try {
            if (tunnelStarted || TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService();
            }
        } catch (Throwable ignored) {
        }
        tunnelStarted = false;

        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception ignored) {}
            vpnInterface = null;
        }

        try { XrayEngine.stop(); } catch (Throwable ignored) {}
        activeProfile = null;
    }

    private void sendStatus(String state, String message, int latency) {
        Intent i = new Intent(ACTION_STATUS)
                .setPackage(getPackageName())
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_LATENCY, latency);
        sendBroadcast(i);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "اتصال InstaTunnel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("وضعیت اتصال تونل برنامه انتخاب‌شده");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text, boolean connected) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, InstaVpnService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(connected ? "InstaTunnel متصل است" : "InstaTunnel")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع اتصال", stop)
                .build();
    }

    private void updateNotification(String text, boolean connected) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text, connected));
    }

    @Override
    public void onRevoke() {
        stopTunnel("مجوز VPN لغو شد.");
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        cleanup();
        worker.shutdownNow();
        super.onDestroy();
    }
}
