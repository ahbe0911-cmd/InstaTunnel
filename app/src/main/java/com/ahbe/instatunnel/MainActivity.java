package com.ahbe.instatunnel;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST = 2001;
    private static final int APP_PICKER_REQUEST = 2002;

    private TextView statusView;
    private TextView detailView;
    private TextView selectedLabelView;
    private TextView selectedPackageView;
    private TextView routeChipView;
    private TextView orbIconView;
    private ImageView selectedIconView;
    private Button connectButton;
    private Button selectAppButton;
    private Button openAppButton;

    private boolean connected;
    private boolean connecting;
    private String selectedPackage = "";
    private String selectedLabel = "";

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!InstaVpnService.ACTION_STATUS.equals(intent.getAction())) return;
            String state = intent.getStringExtra(InstaVpnService.EXTRA_STATE);
            String message = intent.getStringExtra(InstaVpnService.EXTRA_MESSAGE);
            int latency = intent.getIntExtra(InstaVpnService.EXTRA_LATENCY, -1);
            connected = "connected".equals(state);
            connecting = "connecting".equals(state);
            updateUi(message, latency, state);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 13, 26));
        getWindow().setNavigationBarColor(Color.rgb(8, 13, 26));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        loadSelection();
        setContentView(buildUi());

        IntentFilter filter = new IntentFilter(InstaVpnService.ACTION_STATUS);
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 50);
        }
    }

    private void loadSelection() {
        SharedPreferences prefs = getSharedPreferences(InstaVpnService.PREFS_NAME, MODE_PRIVATE);
        selectedPackage = prefs.getString(InstaVpnService.PREF_TARGET_PACKAGE, "");
        selectedLabel = prefs.getString(InstaVpnService.PREF_TARGET_LABEL, "");

        if (!selectedPackage.isEmpty()) {
            try {
                getPackageManager().getApplicationInfo(selectedPackage, 0);
            } catch (PackageManager.NameNotFoundException e) {
                selectedPackage = "";
                selectedLabel = "";
            }
        }

        if (selectedPackage.isEmpty()) {
            try {
                ApplicationInfo instagram = getPackageManager().getApplicationInfo("com.instagram.android", 0);
                CharSequence label = instagram.loadLabel(getPackageManager());
                saveSelection("com.instagram.android", label == null ? "Instagram" : label.toString());
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
    }

    private void saveSelection(String packageName, String label) {
        selectedPackage = packageName == null ? "" : packageName.trim();
        selectedLabel = label == null || label.trim().isEmpty() ? selectedPackage : label.trim();
        getSharedPreferences(InstaVpnService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(InstaVpnService.PREF_TARGET_PACKAGE, selectedPackage)
                .putString(InstaVpnService.PREF_TARGET_LABEL, selectedLabel)
                .apply();
        refreshSelectedAppUi();
    }

    private View buildUi() {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackground(backgroundGradient());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        shell.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(brandRow, lp(-1, -2, 0, 0, 0, 18));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(this);
        title.setText("InstaTunnel");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        brandText.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("VPN انتخابی برای یک برنامه مشخص");
        subtitle.setTextColor(Color.rgb(139, 151, 178));
        subtitle.setTextSize(14);
        brandText.addView(subtitle);

        TextView versionChip = new TextView(this);
        versionChip.setText("v0.3");
        versionChip.setTextColor(Color.rgb(187, 198, 255));
        versionChip.setTextSize(12);
        versionChip.setGravity(Gravity.CENTER);
        versionChip.setBackground(roundRect(Color.rgb(29, 36, 67), 16, Color.rgb(78, 94, 151), 1));
        brandRow.addView(versionChip, new LinearLayout.LayoutParams(dp(62), dp(34)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(18), dp(22), dp(18), dp(22));
        hero.setBackground(roundRect(Color.rgb(16, 24, 43), 28, Color.rgb(34, 48, 77), 1));
        hero.setElevation(dp(8));
        root.addView(hero, lp(-1, -2, 0, 0, 0, 16));

        FrameLayout orb = new FrameLayout(this);
        orb.setBackground(circleDrawable(Color.rgb(21, 31, 57), Color.rgb(63, 81, 181), 2));
        hero.addView(orb, new LinearLayout.LayoutParams(dp(126), dp(126)));

        orbIconView = new TextView(this);
        orbIconView.setText("⚡");
        orbIconView.setTextSize(42);
        orbIconView.setTextColor(Color.rgb(163, 175, 255));
        orbIconView.setGravity(Gravity.CENTER);
        orb.addView(orbIconView, new FrameLayout.LayoutParams(-1, -1));

        statusView = new TextView(this);
        statusView.setText("آماده اتصال");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(25);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hero.addView(statusView, lp(-1, -2, 0, 16, 0, 6));

        detailView = new TextView(this);
        detailView.setText("برنامه هدف را انتخاب کن؛ مسیر سالم به‌صورت خودکار بررسی می‌شود.");
        detailView.setTextColor(Color.rgb(155, 165, 188));
        detailView.setTextSize(14);
        detailView.setGravity(Gravity.CENTER);
        detailView.setLineSpacing(dp(2), 1.2f);
        hero.addView(detailView, lp(-1, -2, 6, 0, 6, 12));

        routeChipView = new TextView(this);
        routeChipView.setText("●  مسیر خودکار");
        routeChipView.setTextColor(Color.rgb(129, 226, 199));
        routeChipView.setTextSize(12);
        routeChipView.setGravity(Gravity.CENTER);
        routeChipView.setBackground(roundRect(Color.rgb(17, 48, 49), 15, Color.rgb(36, 91, 82), 1));
        hero.addView(routeChipView, new LinearLayout.LayoutParams(dp(130), dp(32)));

        TextView section = new TextView(this);
        section.setText("برنامه هدف");
        section.setTextColor(Color.rgb(176, 187, 211));
        section.setTextSize(13);
        section.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        root.addView(section, lp(-1, -2, 4, 0, 4, 8));

        LinearLayout appCard = new LinearLayout(this);
        appCard.setOrientation(LinearLayout.HORIZONTAL);
        appCard.setGravity(Gravity.CENTER_VERTICAL);
        appCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        appCard.setBackground(roundRect(Color.rgb(15, 23, 41), 22, Color.rgb(31, 45, 72), 1));
        appCard.setElevation(dp(4));
        appCard.setOnClickListener(v -> openAppPicker());
        root.addView(appCard, lp(-1, -2, 0, 0, 0, 14));

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setPadding(dp(6), dp(6), dp(6), dp(6));
        iconWrap.setBackground(roundRect(Color.rgb(26, 36, 60), 18, Color.rgb(42, 58, 90), 1));
        appCard.addView(iconWrap, new LinearLayout.LayoutParams(dp(64), dp(64)));

        selectedIconView = new ImageView(this);
        selectedIconView.setImageResource(android.R.drawable.sym_def_app_icon);
        selectedIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconWrap.addView(selectedIconView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout appText = new LinearLayout(this);
        appText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams appTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        appTextLp.setMargins(dp(12), 0, dp(12), 0);
        appCard.addView(appText, appTextLp);

        selectedLabelView = new TextView(this);
        selectedLabelView.setTextColor(Color.WHITE);
        selectedLabelView.setTextSize(17);
        selectedLabelView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        appText.addView(selectedLabelView);

        selectedPackageView = new TextView(this);
        selectedPackageView.setTextColor(Color.rgb(125, 137, 163));
        selectedPackageView.setTextSize(12);
        selectedPackageView.setSingleLine(true);
        appText.addView(selectedPackageView);

        TextView arrow = new TextView(this);
        arrow.setText("‹");
        arrow.setTextColor(Color.rgb(149, 162, 193));
        arrow.setTextSize(34);
        arrow.setGravity(Gravity.CENTER);
        appCard.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(48)));

        selectAppButton = new Button(this);
        selectAppButton.setText("انتخاب یا تغییر برنامه");
        selectAppButton.setAllCaps(false);
        selectAppButton.setTextSize(15);
        selectAppButton.setTextColor(Color.rgb(203, 210, 255));
        selectAppButton.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        selectAppButton.setBackground(roundRect(Color.rgb(28, 35, 65), 18, Color.rgb(74, 87, 145), 1));
        selectAppButton.setOnClickListener(v -> openAppPicker());
        root.addView(selectAppButton, lp(-1, dp(52), 0, 0, 0, 14));

        connectButton = new Button(this);
        connectButton.setText("اتصال هوشمند");
        connectButton.setAllCaps(false);
        connectButton.setTextSize(18);
        connectButton.setTextColor(Color.WHITE);
        connectButton.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        connectButton.setBackground(connectGradient());
        connectButton.setOnClickListener(v -> onConnectClicked());
        root.addView(connectButton, lp(-1, dp(62), 0, 0, 0, 10));

        openAppButton = new Button(this);
        openAppButton.setText("باز کردن برنامه انتخاب‌شده");
        openAppButton.setAllCaps(false);
        openAppButton.setTextSize(14);
        openAppButton.setTextColor(Color.rgb(182, 193, 220));
        openAppButton.setBackground(roundRect(Color.rgb(14, 21, 38), 18, Color.rgb(37, 50, 78), 1));
        openAppButton.setEnabled(false);
        openAppButton.setOnClickListener(v -> openSelectedApp());
        root.addView(openAppButton, lp(-1, dp(50), 0, 0, 0, 18));

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        infoCard.setBackground(roundRect(Color.rgb(12, 19, 34), 20, Color.rgb(29, 42, 68), 1));
        root.addView(infoCard, lp(-1, -2, 0, 0, 0, 0));

        TextView infoTitle = new TextView(this);
        infoTitle.setText("حالت اتصال");
        infoTitle.setTextColor(Color.WHITE);
        infoTitle.setTextSize(14);
        infoTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        infoCard.addView(infoTitle);

        TextView info = new TextView(this);
        info.setText("فقط ترافیک برنامه‌ای که انتخاب می‌کنی وارد تونل می‌شود. قبل از اتصال، پراکسی با یک اتصال واقعی TLS بررسی می‌شود. DNS نیز داخل تونل نگه داشته می‌شود.");
        info.setTextColor(Color.rgb(132, 145, 171));
        info.setTextSize(13);
        info.setLineSpacing(dp(3), 1.18f);
        infoCard.addView(info, lp(-1, -2, 0, 8, 0, 0));

        refreshSelectedAppUi();
        return shell;
    }

    private void openAppPicker() {
        if (connected || connecting) {
            detailView.setText("برای تغییر برنامه ابتدا اتصال را قطع کن.");
            return;
        }
        Intent picker = new Intent(this, AppPickerActivity.class)
                .putExtra(AppPickerActivity.EXTRA_CURRENT_PACKAGE, selectedPackage);
        startActivityForResult(picker, APP_PICKER_REQUEST);
    }

    private void onConnectClicked() {
        if (connected || connecting) {
            Intent stop = new Intent(this, InstaVpnService.class).setAction(InstaVpnService.ACTION_STOP);
            startService(stop);
            return;
        }

        if (selectedPackage.isEmpty()) {
            detailView.setText("اول برنامه‌ای را که باید از تونل عبور کند انتخاب کن.");
            openAppPicker();
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
        connecting = true;
        updateUi("در حال آماده‌سازی موتور VPN…", -1, "connecting");
        Intent start = new Intent(this, InstaVpnService.class)
                .setAction(InstaVpnService.ACTION_START)
                .putExtra(InstaVpnService.EXTRA_TARGET_PACKAGE, selectedPackage)
                .putExtra(InstaVpnService.EXTRA_TARGET_LABEL, selectedLabel);
        ContextCompat.startForegroundService(this, start);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == APP_PICKER_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE);
            String label = data.getStringExtra(AppPickerActivity.EXTRA_LABEL);
            if (pkg != null && !pkg.trim().isEmpty()) {
                saveSelection(pkg, label);
                detailView.setText("برنامه انتخاب شد. حالا اتصال هوشمند را بزن.");
            }
            return;
        }

        if (requestCode == VPN_REQUEST && resultCode == Activity.RESULT_OK) {
            startTunnel();
        } else if (requestCode == VPN_REQUEST) {
            connecting = false;
            updateUi("مجوز VPN داده نشد.", -1, "error");
        }
    }

    private void updateUi(String message, int latency, String state) {
        if (connected) {
            statusView.setText("متصل شد");
            statusView.setTextColor(Color.rgb(123, 228, 197));
            orbIconView.setText("✓");
            orbIconView.setTextColor(Color.rgb(123, 228, 197));
            routeChipView.setText("●  تونل فعال");
            connectButton.setText("قطع اتصال");
            connectButton.setBackground(disconnectGradient());
            openAppButton.setEnabled(!selectedPackage.isEmpty());
            openAppButton.setAlpha(1f);
        } else if (connecting) {
            statusView.setText("در حال اتصال…");
            statusView.setTextColor(Color.rgb(183, 193, 255));
            orbIconView.setText("…");
            orbIconView.setTextColor(Color.rgb(183, 193, 255));
            routeChipView.setText("●  در حال بررسی مسیر");
            connectButton.setText("لغو اتصال");
            connectButton.setBackground(disconnectGradient());
            openAppButton.setEnabled(false);
            openAppButton.setAlpha(.45f);
        } else {
            statusView.setText("آماده اتصال");
            statusView.setTextColor("error".equals(state) ? Color.rgb(255, 142, 142) : Color.WHITE);
            orbIconView.setText("error".equals(state) ? "!" : "⚡");
            orbIconView.setTextColor("error".equals(state) ? Color.rgb(255, 142, 142) : Color.rgb(163, 175, 255));
            routeChipView.setText("error".equals(state) ? "●  مسیر ناموفق" : "●  مسیر خودکار");
            connectButton.setText("دوباره تلاش کن".equals(message) ? "دوباره تلاش کن" : "اتصال هوشمند");
            connectButton.setBackground(connectGradient());
            openAppButton.setEnabled(false);
            openAppButton.setAlpha(.45f);
        }

        selectAppButton.setEnabled(!connected && !connecting);
        selectAppButton.setAlpha(selectAppButton.isEnabled() ? 1f : .45f);

        String text = message == null ? "" : message;
        if (latency >= 0) text += "\nتأخیر مسیر تاییدشده: " + latency + " ms";
        detailView.setText(text);
    }

    private void refreshSelectedAppUi() {
        if (selectedLabelView == null || selectedPackageView == null || selectedIconView == null) return;

        if (selectedPackage.isEmpty()) {
            selectedLabelView.setText("هنوز برنامه‌ای انتخاب نشده");
            selectedPackageView.setText("برای انتخاب این کارت را لمس کن");
            selectedIconView.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }

        String label = selectedLabel.isEmpty() ? selectedPackage : selectedLabel;
        selectedLabelView.setText(label);
        selectedPackageView.setText(selectedPackage);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(selectedPackage);
            selectedIconView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            selectedIconView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void openSelectedApp() {
        if (selectedPackage.isEmpty()) return;
        Intent launch = getPackageManager().getLaunchIntentForPackage(selectedPackage);
        if (launch != null) startActivity(launch);
    }

    private Drawable backgroundGradient() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 13, 26), Color.rgb(10, 18, 36), Color.rgb(7, 11, 22)}
        );
    }

    private Drawable connectGradient() {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(92, 91, 230), Color.rgb(76, 131, 255)}
        );
        gd.setCornerRadius(dp(20));
        return gd;
    }

    private Drawable disconnectGradient() {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(161, 67, 87), Color.rgb(205, 76, 93)}
        );
        gd.setCornerRadius(dp(20));
        return gd;
    }

    private Drawable circleDrawable(int fill, int stroke, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fill);
        gd.setStroke(dp(strokeDp), stroke);
        return gd;
    }

    private Drawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) gd.setStroke(dp(strokeDp), stroke);
        return gd;
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
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
