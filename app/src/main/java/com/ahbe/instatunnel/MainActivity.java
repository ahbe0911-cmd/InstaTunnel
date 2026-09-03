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
    private TextView routeChipView;
    private TextView orbIconView;
    private TextView selectedLabelView;
    private TextView selectedPackageView;
    private TextView profileNameView;
    private TextView profileTypeView;
    private ImageView selectedIconView;
    private Button connectButton;
    private Button openAppButton;

    private boolean connected;
    private boolean connecting;
    private String selectedPackage = "";
    private String selectedLabel = "";
    private boolean receiverRegistered;

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
        getWindow().setStatusBarColor(Color.rgb(7, 11, 23));
        getWindow().setNavigationBarColor(Color.rgb(7, 11, 23));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        loadSelection();
        setContentView(buildUi());

        IntentFilter filter = new IntentFilter(InstaVpnService.ACTION_STATUS);
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 50);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSelectedAppUi();
        refreshProfileUi();
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
        root.setPadding(dp(18), dp(24), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(brandRow, lp(-1, -2, 0, 0, 0, 16));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text("InstaTunnel", 29, Color.WHITE, true);
        brandText.addView(title);
        TextView subtitle = text("تونل انتخابی برای هر برنامه", 13, Color.rgb(137, 150, 178), false);
        brandText.addView(subtitle);

        TextView versionChip = text("v0.4", 12, Color.rgb(195, 202, 255), true);
        versionChip.setGravity(Gravity.CENTER);
        versionChip.setBackground(roundRect(Color.rgb(30, 37, 68), 16, Color.rgb(74, 86, 145), 1));
        brandRow.addView(versionChip, new LinearLayout.LayoutParams(dp(62), dp(34)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(18), dp(20), dp(18), dp(20));
        hero.setBackground(roundRect(Color.rgb(15, 23, 42), 27, Color.rgb(35, 48, 77), 1));
        hero.setElevation(dp(7));
        root.addView(hero, lp(-1, -2, 0, 0, 0, 16));

        FrameLayout orb = new FrameLayout(this);
        orb.setBackground(circleDrawable(Color.rgb(21, 31, 58), Color.rgb(81, 88, 214), 2));
        hero.addView(orb, new LinearLayout.LayoutParams(dp(116), dp(116)));

        orbIconView = text("⚡", 41, Color.rgb(170, 180, 255), true);
        orbIconView.setGravity(Gravity.CENTER);
        orb.addView(orbIconView, new FrameLayout.LayoutParams(-1, -1));

        statusView = text("آماده اتصال", 24, Color.WHITE, true);
        statusView.setGravity(Gravity.CENTER);
        hero.addView(statusView, lp(-1, -2, 0, 14, 0, 5));

        detailView = text("یک کانفیگ یا ساب اضافه کن و برنامه هدف را انتخاب کن.", 13, Color.rgb(154, 166, 191), false);
        detailView.setGravity(Gravity.CENTER);
        detailView.setLineSpacing(dp(2), 1.18f);
        hero.addView(detailView, lp(-1, -2, 6, 0, 6, 11));

        routeChipView = text("●  بدون کانفیگ", 11, Color.rgb(255, 188, 128), true);
        routeChipView.setGravity(Gravity.CENTER);
        routeChipView.setPadding(dp(12), 0, dp(12), 0);
        routeChipView.setBackground(roundRect(Color.rgb(57, 39, 28), 14, Color.rgb(100, 67, 42), 1));
        hero.addView(routeChipView, new LinearLayout.LayoutParams(-2, dp(31)));

        root.addView(sectionTitle("مسیر اتصال"), lp(-1, -2, 4, 0, 4, 8));
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.setGravity(Gravity.CENTER_VERTICAL);
        profileCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        profileCard.setBackground(roundRect(Color.rgb(13, 21, 38), 21, Color.rgb(31, 44, 71), 1));
        profileCard.setOnClickListener(v -> openConfigManager());
        root.addView(profileCard, lp(-1, -2, 0, 0, 0, 10));

        TextView routeIcon = text("⇄", 25, Color.rgb(157, 169, 255), true);
        routeIcon.setGravity(Gravity.CENTER);
        routeIcon.setBackground(roundRect(Color.rgb(30, 37, 66), 16, Color.rgb(54, 65, 108), 1));
        profileCard.addView(routeIcon, new LinearLayout.LayoutParams(dp(55), dp(55)));

        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams profileTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        profileTextLp.setMargins(dp(12), 0, dp(10), 0);
        profileCard.addView(profileText, profileTextLp);

        profileNameView = text("هیچ کانفیگی انتخاب نشده", 15, Color.WHITE, true);
        profileNameView.setSingleLine(true);
        profileText.addView(profileNameView);
        profileTypeView = text("برای افزودن کانفیگ یا ساب لمس کن", 11, Color.rgb(111, 126, 156), false);
        profileText.addView(profileTypeView, lp(-1, -2, 0, 3, 0, 0));

        TextView profileArrow = text("‹", 32, Color.rgb(142, 157, 190), false);
        profileArrow.setGravity(Gravity.CENTER);
        profileCard.addView(profileArrow, new LinearLayout.LayoutParams(dp(30), dp(45)));

        Button configButton = actionButton("کانفیگ و اشتراک (ساب)", false);
        configButton.setOnClickListener(v -> openConfigManager());
        root.addView(configButton, lp(-1, dp(52), 0, 0, 0, 16));

        root.addView(sectionTitle("برنامه هدف"), lp(-1, -2, 4, 0, 4, 8));
        LinearLayout appCard = new LinearLayout(this);
        appCard.setOrientation(LinearLayout.HORIZONTAL);
        appCard.setGravity(Gravity.CENTER_VERTICAL);
        appCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        appCard.setBackground(roundRect(Color.rgb(13, 21, 38), 21, Color.rgb(31, 44, 71), 1));
        appCard.setOnClickListener(v -> openAppPicker());
        root.addView(appCard, lp(-1, -2, 0, 0, 0, 10));

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setPadding(dp(5), dp(5), dp(5), dp(5));
        iconWrap.setBackground(roundRect(Color.rgb(28, 37, 61), 16, Color.rgb(48, 61, 94), 1));
        appCard.addView(iconWrap, new LinearLayout.LayoutParams(dp(58), dp(58)));

        selectedIconView = new ImageView(this);
        selectedIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        selectedIconView.setImageResource(android.R.drawable.sym_def_app_icon);
        iconWrap.addView(selectedIconView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout appText = new LinearLayout(this);
        appText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams appTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        appTextLp.setMargins(dp(12), 0, dp(10), 0);
        appCard.addView(appText, appTextLp);

        selectedLabelView = text("انتخاب برنامه", 15, Color.WHITE, true);
        appText.addView(selectedLabelView);
        selectedPackageView = text("", 11, Color.rgb(111, 126, 156), false);
        selectedPackageView.setSingleLine(true);
        appText.addView(selectedPackageView, lp(-1, -2, 0, 3, 0, 0));

        TextView appArrow = text("‹", 32, Color.rgb(142, 157, 190), false);
        appArrow.setGravity(Gravity.CENTER);
        appCard.addView(appArrow, new LinearLayout.LayoutParams(dp(30), dp(45)));

        Button selectAppButton = actionButton("انتخاب یا تغییر برنامه", false);
        selectAppButton.setOnClickListener(v -> openAppPicker());
        root.addView(selectAppButton, lp(-1, dp(52), 0, 0, 0, 16));

        connectButton = actionButton("اتصال", true);
        connectButton.setTextSize(18);
        connectButton.setOnClickListener(v -> onConnectClicked());
        root.addView(connectButton, lp(-1, dp(62), 0, 0, 0, 9));

        openAppButton = actionButton("باز کردن برنامه انتخاب‌شده", false);
        openAppButton.setEnabled(false);
        openAppButton.setOnClickListener(v -> openSelectedApp());
        root.addView(openAppButton, lp(-1, dp(50), 0, 0, 0, 16));

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(15), dp(14), dp(15), dp(14));
        infoCard.setBackground(roundRect(Color.rgb(10, 17, 31), 19, Color.rgb(27, 39, 63), 1));
        root.addView(infoCard, lp(-1, -2, 0, 0, 0, 0));
        infoCard.addView(text("اتصال واقعی با Xray", 14, Color.WHITE, true));
        TextView info = text("کانفیگ انتخاب‌شده ابتدا داخل Xray اجرا و با یک اتصال SOCKS واقعی به Instagram آزمایش می‌شود؛ سپس فقط برنامه انتخاب‌شده وارد VPN می‌شود.", 12, Color.rgb(127, 141, 170), false);
        info.setLineSpacing(dp(3), 1.16f);
        infoCard.addView(info, lp(-1, -2, 0, 6, 0, 0));

        refreshSelectedAppUi();
        refreshProfileUi();
        return shell;
    }

    private TextView sectionTitle(String value) {
        return text(value, 13, Color.rgb(179, 190, 215), true);
    }

    private void openConfigManager() {
        if (connected || connecting) {
            detailView.setText("برای تغییر کانفیگ ابتدا اتصال را قطع کن.");
            return;
        }
        startActivity(new Intent(this, ConfigManagerActivity.class));
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
            startService(new Intent(this, InstaVpnService.class).setAction(InstaVpnService.ACTION_STOP));
            return;
        }

        if (ProfileManager.getSelected(this) == null) {
            detailView.setText("اول یک کانفیگ یا ساب اضافه و یک کانفیگ را انتخاب کن.");
            openConfigManager();
            return;
        }

        if (selectedPackage.isEmpty()) {
            detailView.setText("اول برنامه هدف را انتخاب کن.");
            openAppPicker();
            return;
        }

        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission, VPN_REQUEST);
        else startTunnel();
    }

    private void startTunnel() {
        connecting = true;
        updateUi("در حال راه‌اندازی Xray…", -1, "connecting");
        Intent start = new Intent(this, InstaVpnService.class)
                .setAction(InstaVpnService.ACTION_START)
                .putExtra(InstaVpnService.EXTRA_TARGET_PACKAGE, selectedPackage)
                .putExtra(InstaVpnService.EXTRA_TARGET_LABEL, selectedLabel);
        ContextCompat.startForegroundService(this, start);
    }

    private void refreshProfileUi() {
        if (profileNameView == null || profileTypeView == null || routeChipView == null) return;
        ProfileManager.Profile p = ProfileManager.getSelected(this);
        if (p == null) {
            profileNameView.setText("هیچ کانفیگی انتخاب نشده");
            profileTypeView.setText("برای افزودن کانفیگ یا ساب لمس کن");
            routeChipView.setText("●  بدون کانفیگ");
            routeChipView.setTextColor(Color.rgb(255, 188, 128));
            routeChipView.setBackground(roundRect(Color.rgb(57, 39, 28), 14, Color.rgb(100, 67, 42), 1));
        } else {
            profileNameView.setText(p.name);
            profileTypeView.setText(p.displayType() + " • آماده استفاده");
            if (!connected && !connecting) {
                routeChipView.setText("●  " + p.displayType());
                routeChipView.setTextColor(Color.rgb(133, 231, 201));
                routeChipView.setBackground(roundRect(Color.rgb(17, 49, 48), 14, Color.rgb(38, 91, 82), 1));
            }
        }
    }

    private void refreshSelectedAppUi() {
        if (selectedLabelView == null || selectedPackageView == null || selectedIconView == null) return;
        if (selectedPackage.isEmpty()) {
            selectedLabelView.setText("انتخاب برنامه");
            selectedPackageView.setText("برای انتخاب لمس کن");
            selectedIconView.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(selectedPackage, 0);
            CharSequence label = app.loadLabel(getPackageManager());
            Drawable icon = app.loadIcon(getPackageManager());
            selectedLabelView.setText(label == null ? selectedLabel : label.toString());
            selectedPackageView.setText(selectedPackage);
            selectedIconView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            selectedLabelView.setText(selectedLabel.isEmpty() ? selectedPackage : selectedLabel);
            selectedPackageView.setText(selectedPackage);
        }
    }

    private void openSelectedApp() {
        if (selectedPackage.isEmpty()) return;
        Intent launch = getPackageManager().getLaunchIntentForPackage(selectedPackage);
        if (launch != null) startActivity(launch);
        else detailView.setText("این برنامه اکتیویتی قابل اجرا ندارد.");
    }

    private void updateUi(String message, int latency, String state) {
        if (statusView == null) return;
        if (message != null && !message.trim().isEmpty()) detailView.setText(message);

        if ("connected".equals(state)) {
            statusView.setText("متصل");
            orbIconView.setText("✓");
            orbIconView.setTextColor(Color.rgb(125, 235, 200));
            connectButton.setText("قطع اتصال");
            openAppButton.setEnabled(true);
            routeChipView.setText(latency >= 0 ? "●  " + latency + " ms" : "●  متصل");
            routeChipView.setTextColor(Color.rgb(125, 235, 200));
            routeChipView.setBackground(roundRect(Color.rgb(17, 55, 49), 14, Color.rgb(39, 104, 88), 1));
        } else if ("connecting".equals(state)) {
            statusView.setText("در حال اتصال…");
            orbIconView.setText("…");
            orbIconView.setTextColor(Color.rgb(177, 185, 255));
            connectButton.setText("لغو اتصال");
            openAppButton.setEnabled(false);
            routeChipView.setText("●  در حال بررسی");
            routeChipView.setTextColor(Color.rgb(255, 211, 138));
        } else if ("error".equals(state)) {
            connected = false;
            connecting = false;
            statusView.setText("اتصال ناموفق");
            orbIconView.setText("!");
            orbIconView.setTextColor(Color.rgb(255, 132, 145));
            connectButton.setText("تلاش دوباره");
            openAppButton.setEnabled(false);
            routeChipView.setText("●  خطای کانفیگ");
            routeChipView.setTextColor(Color.rgb(255, 146, 154));
            routeChipView.setBackground(roundRect(Color.rgb(61, 31, 38), 14, Color.rgb(108, 49, 60), 1));
        } else {
            connected = false;
            connecting = false;
            statusView.setText("آماده اتصال");
            orbIconView.setText("⚡");
            orbIconView.setTextColor(Color.rgb(170, 180, 255));
            connectButton.setText("اتصال");
            openAppButton.setEnabled(false);
            refreshProfileUi();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST) {
            if (resultCode == Activity.RESULT_OK) startTunnel();
            else updateUi("مجوز VPN صادر نشد.", -1, "error");
            return;
        }
        if (requestCode == APP_PICKER_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            String pkg = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGE);
            String label = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_LABEL);
            if (pkg != null && !pkg.trim().isEmpty()) saveSelection(pkg, label);
        }
    }

    private Button actionButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setBackground(primary ? connectGradient() : roundRect(Color.rgb(27, 35, 63), 17, Color.rgb(68, 80, 133), 1));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    private GradientDrawable backgroundGradient() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(7, 11, 23), Color.rgb(9, 16, 31), Color.rgb(10, 14, 28)});
        return d;
    }

    private GradientDrawable connectGradient() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(88, 73, 220), Color.rgb(70, 91, 221)});
        d.setCornerRadius(dp(19));
        return d;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) d.setStroke(dp(strokeWidthDp), strokeColor);
        return d;
    }

    private GradientDrawable circleDrawable(int color, int strokeColor, int strokeWidthDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(strokeWidthDp), strokeColor);
        return d;
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
        if (receiverRegistered) {
            try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
