package com.ahbe.instatunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.hev.socks5.tunnel.HevSocks5Tunnel;

public class InstaVpnService extends VpnService {
    public static final String ACTION_START = "com.ahbe.instatunnel.START";
    public static final String ACTION_STOP = "com.ahbe.instatunnel.STOP";
    public static final String ACTION_STATUS = "com.ahbe.instatunnel.STATUS";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_LATENCY = "latency";

    private static final String CHANNEL_ID = "instatunnel_vpn";
    private static final int NOTIFICATION_ID = 41;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean stopping;
    private volatile boolean starting;
    private ParcelFileDescriptor vpnInterface;
    private HevSocks5Tunnel tunnel;

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

        if (!starting && tunnel == null) {
            startForeground(NOTIFICATION_ID, buildNotification("در حال پیدا کردن مسیر سالم…", false));
            starting = true;
            stopping = false;
            sendStatus("connecting", "در حال جستجوی مسیر سالم برای Instagram…", -1);
            worker.execute(this::connectInBackground);
        }
        return START_STICKY;
    }

    private void connectInBackground() {
        try {
            if (!isInstagramInstalled()) {
                throw new Exception("برنامه Instagram روی گوشی نصب نیست.");
            }

            ProxyDiscovery.ProxyNode node = ProxyDiscovery.findBest(message -> {
                if (!stopping) sendStatus("connecting", message, -1);
            });
            if (stopping) return;

            sendStatus("connecting", "مسیر " + node + " انتخاب شد؛ در حال ساخت VPN…", node.latencyMs);
            establishVpn(node);
            if (stopping) return;

            updateNotification("Instagram از تونل عبور می‌کند", true);
            sendStatus("connected", "اتصال برقرار شد. فقط ترافیک Instagram وارد تونل می‌شود.", node.latencyMs);
        } catch (Exception e) {
            cleanup();
            starting = false;
            String message = e.getMessage() == null ? "اتصال برقرار نشد." : e.getMessage();
            sendStatus("error", message, -1);
            stopForeground(true);
            stopSelf();
        }
    }

    private boolean isInstagramInstalled() {
        try {
            getPackageManager().getApplicationInfo("com.instagram.android", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void establishVpn(ProxyDiscovery.ProxyNode node) throws Exception {
        Builder builder = new Builder()
                .setSession("InstaTunnel")
                .setMtu(1500)
                .addAddress("198.18.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:1:fd00:1::1", 64)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8");

        builder.addAllowedApplication("com.instagram.android");
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setMetered(false);
            builder.setBlocking(true);
        }

        vpnInterface = builder.establish();
        if (vpnInterface == null) throw new Exception("Android نتوانست رابط VPN را ایجاد کند.");

        File config = new File(getCacheDir(), "hev-instatunnel.yml");
        String yaml = "tunnel:\n" +
                "  name: tun0\n" +
                "  mtu: 1500\n" +
                "  ipv4: 198.18.0.1\n" +
                "  ipv6: 'fd00:1:fd00:1::1'\n" +
                "socks5:\n" +
                "  address: " + node.host + "\n" +
                "  port: " + node.port + "\n" +
                "  udp: 'tcp'\n" +
                "misc:\n" +
                "  task-stack-size: 86016\n" +
                "  connect-timeout: 7000\n" +
                "  read-write-timeout: 60000\n" +
                "  log-level: error\n";

        try (FileOutputStream out = new FileOutputStream(config, false)) {
            out.write(yaml.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        tunnel = new HevSocks5Tunnel();
        tunnel.startAsync(config.getAbsolutePath(), vpnInterface.getFileDescriptor());
        if (!tunnel.isRunning()) throw new Exception("موتور TUN شروع نشد.");
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
        if (tunnel != null) {
            try { tunnel.stop(); } catch (Exception ignored) {}
            tunnel = null;
        }
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception ignored) {}
            vpnInterface = null;
        }
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
            channel.setDescription("وضعیت اتصال VPN مخصوص Instagram");
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
