package com.ahbe.instatunnel;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST = 2001;

    private TextView statusView;
    private TextView detailView;
    private Button connectButton;
    private Button instagramButton;
    private boolean connected;
    private boolean connecting;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!InstaVpnService.ACTION_STATUS.equals(intent.getAction())) return;
            String state = intent.getStringExtra(InstaVpnService.EXTRA_STATE);
            String message = intent.getStringExtra(InstaVpnService.EXTRA_MESSAGE);
            int latency = intent.getIntExtra(InstaVpnService.EXTRA_LATENCY, -1);
            connected = "connected".equals(state);
            connecting = "connecting".equals(state);
            updateUi(message, latency);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildUi());

        IntentFilter filter = new IntentFilter(InstaVpnService.ACTION_STATUS);
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 50);
        }
    }

    private View buildUi() {
        int pad = dp(22);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 245, 247));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(42), pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("InstaTunnel");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(title.getTypeface(), 1);
        root.addView(title, lp(-1, -2, 0, 0, 0, 8));

        TextView subtitle = new TextView(this);
        subtitle.setText("اتصال هوشمند فقط برای اینستاگرام");
        subtitle.setTextSize(17);
        subtitle.setTextColor(Color.rgb(95, 99, 104));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 30));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(22));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(24));
        card.setBackground(bg);
        root.addView(card, lp(-1, -2, 0, 0, 0, 22));

        statusView = new TextView(this);
        statusView.setText("آماده اتصال");
        statusView.setTextSize(24);
        statusView.setTextColor(Color.rgb(30, 136, 229));
        statusView.setGravity(Gravity.CENTER);
        statusView.setTypeface(statusView.getTypeface(), 1);
        card.addView(statusView, lp(-1, -2, 0, 0, 0, 12));

        detailView = new TextView(this);
        detailView.setText("با زدن دکمه، برنامه مسیرهای SOCKS5 عمومی را بررسی می‌کند و یک مسیر سالم را انتخاب می‌کند.");
        detailView.setTextSize(15);
        detailView.setTextColor(Color.rgb(110, 110, 115));
        detailView.setGravity(Gravity.CENTER);
        detailView.setLineSpacing(0, 1.25f);
        card.addView(detailView, lp(-1, -2, 0, 0, 0, 18));

        connectButton = new Button(this);
        connectButton.setText("اتصال هوشمند");
        connectButton.setTextSize(18);
        connectButton.setAllCaps(false);
        connectButton.setOnClickListener(v -> onConnectClicked());
        card.addView(connectButton, lp(-1, dp(58), 0, 0, 0, 10));

        instagramButton = new Button(this);
        instagramButton.setText("باز کردن اینستاگرام");
        instagramButton.setTextSize(16);
        instagramButton.setAllCaps(false);
        instagramButton.setEnabled(false);
        instagramButton.setOnClickListener(v -> openInstagram());
        card.addView(instagramButton, lp(-1, dp(52), 0, 0, 0, 0));

        TextView notes = new TextView(this);
        notes.setText("• فقط بسته com.instagram.android وارد VPN می‌شود.\n• برای اتصال نیازی به وارد کردن کانفیگ یا داشتن سرور شخصی نیست.\n• پراکسی‌های عمومی متعلق به اشخاص ثالث هستند و کیفیت آن‌ها تضمین‌شده نیست.\n• اطلاعات HTTPS اینستاگرام رمزگذاری است، ولی متادیتای اتصال می‌تواند برای پراکسی قابل مشاهده باشد.");
        notes.setTextSize(14);
        notes.setTextColor(Color.rgb(117, 117, 117));
        notes.setLineSpacing(dp(2), 1.3f);
        root.addView(notes, lp(-1, -2, 0, 6, 0, 0));

        return scroll;
    }

    private void onConnectClicked() {
        if (connected || connecting) {
            Intent stop = new Intent(this, InstaVpnService.class).setAction(InstaVpnService.ACTION_STOP);
            startService(stop);
            return;
        }

        Intent permission = VpnService.prepare(this);
        if (permission != null) {
            startActivityForResult(permission, VPN_REQUEST);
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        Intent start = new Intent(this, InstaVpnService.class).setAction(InstaVpnService.ACTION_START);
        ContextCompat.startForegroundService(this, start);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == Activity.RESULT_OK) {
            startTunnel();
        } else if (requestCode == VPN_REQUEST) {
            updateUi("مجوز VPN داده نشد.", -1);
        }
    }

    private void updateUi(String message, int latency) {
        if (connected) {
            statusView.setText("متصل شد");
            connectButton.setText("قطع اتصال");
            instagramButton.setEnabled(true);
        } else if (connecting) {
            statusView.setText("در حال اتصال…");
            connectButton.setText("لغو");
            instagramButton.setEnabled(false);
        } else {
            statusView.setText("آماده اتصال");
            connectButton.setText("اتصال هوشمند");
            instagramButton.setEnabled(false);
        }

        String text = message == null ? "" : message;
        if (latency >= 0) text += "\nتأخیر مسیر: " + latency + " ms";
        detailView.setText(text);
    }

    private void openInstagram() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.instagram.android");
        if (launch != null) startActivity(launch);
        else {
            Intent settings = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            startActivity(settings);
        }
    }

    private LinearLayout.LayoutParams lp(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }
}
