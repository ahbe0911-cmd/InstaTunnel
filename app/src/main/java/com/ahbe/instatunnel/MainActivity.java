package com.ahbe.instatunnel;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST = 2001;

    private TextView statusView;
    private TextView detailView;
    private TextView selectedAppView;
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
            updateUi(message, latency);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        if (!selectedPackage.isEmpty() && getPackageManager().getLaunchIntentForPackage(selectedPackage) == null) {
            selectedPackage = "";
            selectedLabel = "";
        }

        if (selectedPackage.isEmpty()) {
            Intent instagram = getPackageManager().getLaunchIntentForPackage("com.instagram.android");
            if (instagram != null) {
                saveSelection("com.instagram.android", "Instagram");
            }
        }
    }

    private void saveSelection(String packageName, String label) {
        selectedPackage = packageName == null ? "" : packageName;
        selectedLabel = label == null ? selectedPackage : label;
        getSharedPreferences(InstaVpnService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(InstaVpnService.PREF_TARGET_PACKAGE, selectedPackage)
                .putString(InstaVpnService.PREF_TARGET_LABEL, selectedLabel)
                .apply();
        refreshSelectedAppUi();
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
        subtitle.setText("تونل هوشمند برای برنامه‌ای که خودت انتخاب می‌کنی");
        subtitle.setTextSize(17);
        subtitle.setTextColor(Color.rgb(95, 99, 104));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 24));

        LinearLayout appCard = new LinearLayout(this);
        appCard.setOrientation(LinearLayout.VERTICAL);
        appCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        android.graphics.drawable.GradientDrawable appBg = new android.graphics.drawable.GradientDrawable();
        appBg.setColor(Color.WHITE);
        appBg.setCornerRadius(dp(22));
        appCard.setBackground(appBg);
        root.addView(appCard, lp(-1, -2, 0, 0, 0, 16));

        TextView appTitle = new TextView(this);
        appTitle.setText("برنامه هدف");
        appTitle.setTextSize(16);
        appTitle.setTextColor(Color.rgb(55, 55, 60));
        appTitle.setTypeface(appTitle.getTypeface(), 1);
        appCard.addView(appTitle, lp(-1, -2, 0, 0, 0, 8));

        selectedAppView = new TextView(this);
        selectedAppView.setTextSize(15);
        selectedAppView.setTextColor(Color.rgb(80, 80, 85));
        selectedAppView.setLineSpacing(0, 1.15f);
        appCard.addView(selectedAppView, lp(-1, -2, 0, 0, 0, 12));

        selectAppButton = new Button(this);
        selectAppButton.setText("انتخاب برنامه");
        selectAppButton.setTextSize(16);
        selectAppButton.setAllCaps(false);
        selectAppButton.setOnClickListener(v -> showAppPicker());
        appCard.addView(selectAppButton, lp(-1, dp(52), 0, 0, 0, 0));

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
        detailView.setText("یک برنامه انتخاب کن؛ سپس با زدن اتصال، مسیرهای موجود بررسی می‌شوند و مسیر سالم انتخاب می‌شود.");
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

        openAppButton = new Button(this);
        openAppButton.setText("باز کردن برنامه");
        openAppButton.setTextSize(16);
        openAppButton.setAllCaps(false);
        openAppButton.setEnabled(false);
        openAppButton.setOnClickListener(v -> openSelectedApp());
        card.addView(openAppButton, lp(-1, dp(52), 0, 0, 0, 0));

        TextView notes = new TextView(this);
        notes.setText("• فقط برنامه‌ای که انتخاب می‌کنی وارد VPN می‌شود.\n• برنامه هدف را هر زمان که اتصال قطع است می‌توانی عوض کنی.\n• برای اتصال نیازی به وارد کردن کانفیگ یا داشتن سرور شخصی نیست.\n• پراکسی‌های عمومی متعلق به اشخاص ثالث هستند و کیفیت یا حریم خصوصی آن‌ها تضمین‌شده نیست.");
        notes.setTextSize(14);
        notes.setTextColor(Color.rgb(117, 117, 117));
        notes.setLineSpacing(dp(2), 1.3f);
        root.addView(notes, lp(-1, -2, 0, 6, 0, 0));

        refreshSelectedAppUi();
        return scroll;
    }

    private void showAppPicker() {
        if (connected || connecting) return;

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);

        List<AppEntry> allApps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName) || !seen.add(packageName)) continue;
            CharSequence labelCs = info.loadLabel(pm);
            String label = labelCs == null ? packageName : labelCs.toString();
            allApps.add(new AppEntry(label, packageName));
        }
        allApps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(8), dp(16), 0);

        EditText search = new EditText(this);
        search.setHint("جستجوی نام برنامه…");
        search.setSingleLine(true);
        container.addView(search, new LinearLayout.LayoutParams(-1, dp(54)));

        ListView list = new ListView(this);
        container.addView(list, new LinearLayout.LayoutParams(-1, dp(420)));

        List<AppEntry> visibleApps = new ArrayList<>(allApps);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                toDisplayStrings(visibleApps)
        );
        list.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("انتخاب برنامه هدف")
                .setView(container)
                .setNegativeButton("انصراف", null)
                .create();

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleApps.size()) return;
            AppEntry entry = visibleApps.get(position);
            saveSelection(entry.packageName, entry.label);
            dialog.dismiss();
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                visibleApps.clear();
                for (AppEntry entry : allApps) {
                    if (q.isEmpty()
                            || entry.label.toLowerCase(Locale.ROOT).contains(q)
                            || entry.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                        visibleApps.add(entry);
                    }
                }
                adapter.clear();
                adapter.addAll(toDisplayStrings(visibleApps));
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    private List<String> toDisplayStrings(List<AppEntry> apps) {
        List<String> rows = new ArrayList<>();
        for (AppEntry app : apps) {
            rows.add(app.label + "\n" + app.packageName);
        }
        return rows;
    }

    private void onConnectClicked() {
        if (connected || connecting) {
            Intent stop = new Intent(this, InstaVpnService.class).setAction(InstaVpnService.ACTION_STOP);
            startService(stop);
            return;
        }

        if (selectedPackage.isEmpty()) {
            detailView.setText("ابتدا برنامه‌ای را که باید از تونل عبور کند انتخاب کن.");
            showAppPicker();
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
        Intent start = new Intent(this, InstaVpnService.class)
                .setAction(InstaVpnService.ACTION_START)
                .putExtra(InstaVpnService.EXTRA_TARGET_PACKAGE, selectedPackage)
                .putExtra(InstaVpnService.EXTRA_TARGET_LABEL, selectedLabel);
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
            openAppButton.setEnabled(!selectedPackage.isEmpty());
        } else if (connecting) {
            statusView.setText("در حال اتصال…");
            connectButton.setText("لغو");
            openAppButton.setEnabled(false);
        } else {
            statusView.setText("آماده اتصال");
            connectButton.setText("اتصال هوشمند");
            openAppButton.setEnabled(false);
        }

        selectAppButton.setEnabled(!connected && !connecting);

        String text = message == null ? "" : message;
        if (latency >= 0) text += "\nتأخیر مسیر: " + latency + " ms";
        detailView.setText(text);
    }

    private void refreshSelectedAppUi() {
        if (selectedAppView == null) return;
        if (selectedPackage.isEmpty()) {
            selectedAppView.setText("هنوز برنامه‌ای انتخاب نشده است");
        } else {
            String label = selectedLabel.isEmpty() ? selectedPackage : selectedLabel;
            selectedAppView.setText(label + "\n" + selectedPackage);
        }
    }

    private void openSelectedApp() {
        if (selectedPackage.isEmpty()) return;
        Intent launch = getPackageManager().getLaunchIntentForPackage(selectedPackage);
        if (launch != null) {
            startActivity(launch);
        } else {
            Intent settings = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + selectedPackage)
            );
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

    private static class AppEntry {
        final String label;
        final String packageName;

        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }
}
