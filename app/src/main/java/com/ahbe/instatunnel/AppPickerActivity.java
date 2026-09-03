package com.ahbe.instatunnel;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppPickerActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE = "picked_package";
    public static final String EXTRA_LABEL = "picked_label";
    public static final String EXTRA_SELECTED_PACKAGE = EXTRA_PACKAGE;
    public static final String EXTRA_SELECTED_LABEL = EXTRA_LABEL;
    public static final String EXTRA_CURRENT_PACKAGE = "current_package";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> visibleApps = new ArrayList<>();

    private EditText searchView;
    private ListView listView;
    private TextView countView;
    private TextView emptyView;
    private AppAdapter adapter;
    private String currentPackage = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(9, 14, 28));
        getWindow().setNavigationBarColor(Color.rgb(9, 14, 28));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        currentPackage = getIntent().getStringExtra(EXTRA_CURRENT_PACKAGE);
        if (currentPackage == null) currentPackage = "";

        setContentView(buildUi());
        loadApps();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(14));
        root.setBackground(backgroundGradient());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(38);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(8), 0, dp(8), 0);
        header.addView(titleBox, titleLp);

        TextView title = new TextView(this);
        title.setText("انتخاب برنامه");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleBox.addView(title);

        countView = new TextView(this);
        countView.setText("در حال خواندن برنامه‌های نصب‌شده…");
        countView.setTextColor(Color.rgb(157, 166, 189));
        countView.setTextSize(13);
        titleBox.addView(countView);

        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setHint("جستجو در برنامه‌های گوشی");
        searchView.setHintTextColor(Color.rgb(123, 134, 160));
        searchView.setTextColor(Color.WHITE);
        searchView.setTextSize(15);
        searchView.setPadding(dp(18), 0, dp(18), 0);
        searchView.setBackground(roundRect(Color.rgb(20, 29, 51), 18, Color.rgb(45, 59, 88), 1));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(56));
        searchLp.setMargins(0, dp(22), 0, dp(12));
        root.addView(searchView, searchLp);

        TextView hint = new TextView(this);
        hint.setText("یک برنامه را لمس کن؛ همان لحظه انتخاب و ذخیره می‌شود.");
        hint.setTextColor(Color.rgb(138, 148, 171));
        hint.setTextSize(13);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(dp(4), 0, dp(4), dp(10));
        root.addView(hint, hintLp);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(10));
        listView.setSelector(android.R.color.transparent);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(listView, listLp);

        emptyView = new TextView(this);
        emptyView.setText("برنامه‌ای پیدا نشد");
        emptyView.setTextColor(Color.rgb(157, 166, 189));
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new LinearLayout.LayoutParams(-1, dp(64)));

        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleApps.size()) return;
            AppEntry entry = visibleApps.get(position);
            Intent result = new Intent()
                    .putExtra(EXTRA_PACKAGE, entry.packageName)
                    .putExtra(EXTRA_LABEL, entry.label);
            setResult(Activity.RESULT_OK, result);
            finish();
        });

        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(s); }
            @Override public void afterTextChanged(Editable s) {}
        });

        return root;
    }

    private void loadApps() {
        worker.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppEntry> loaded = new ArrayList<>();

            for (ApplicationInfo info : installed) {
                if (info == null || info.packageName == null) continue;
                if (!info.enabled) continue;
                if (getPackageName().equals(info.packageName)) continue;
                if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;

                CharSequence labelCs = info.loadLabel(pm);
                String label = labelCs == null ? info.packageName : labelCs.toString().trim();
                if (label.isEmpty()) label = info.packageName;

                Drawable icon;
                try {
                    icon = info.loadIcon(pm);
                } catch (Throwable ignored) {
                    icon = getDrawable(android.R.drawable.sym_def_app_icon);
                }
                loaded.add(new AppEntry(label, info.packageName, icon));
            }

            Collator collator = Collator.getInstance(new Locale("fa"));
            collator.setStrength(Collator.PRIMARY);
            loaded.sort((a, b) -> collator.compare(a.label, b.label));

            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(loaded);
                applyFilter(searchView.getText());
            });
        });
    }

    private void applyFilter(CharSequence query) {
        String q = query == null ? "" : query.toString().trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        for (AppEntry app : allApps) {
            if (q.isEmpty()
                    || app.label.toLowerCase(Locale.ROOT).contains(q)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                visibleApps.add(app);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (countView != null) countView.setText(toPersianDigits(visibleApps.size()) + " برنامه قابل انتخاب");
        if (emptyView != null) emptyView.setVisibility(visibleApps.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleApps.size(); }
        @Override public Object getItem(int position) { return visibleApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(AppPickerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setBackground(roundRect(Color.rgb(16, 24, 43), 20, Color.rgb(31, 43, 69), 1));

                ImageView icon = new ImageView(AppPickerActivity.this);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

                LinearLayout texts = new LinearLayout(AppPickerActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, -2, 1f);
                textsLp.setMargins(dp(12), 0, dp(12), 0);
                row.addView(texts, textsLp);

                TextView label = new TextView(AppPickerActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(16);
                label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                texts.addView(label);

                TextView pkg = new TextView(AppPickerActivity.this);
                pkg.setTextColor(Color.rgb(126, 137, 163));
                pkg.setTextSize(12);
                pkg.setSingleLine(true);
                texts.addView(pkg);

                TextView mark = new TextView(AppPickerActivity.this);
                mark.setText("✓");
                mark.setGravity(Gravity.CENTER);
                mark.setTextSize(22);
                mark.setTextColor(Color.rgb(111, 224, 190));
                row.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));

                LinearLayout wrapper = new LinearLayout(AppPickerActivity.this);
                wrapper.setPadding(0, dp(5), 0, dp(5));
                wrapper.addView(row, new LinearLayout.LayoutParams(-1, -2));

                holder = new RowHolder(icon, label, pkg, mark);
                wrapper.setTag(holder);
                convertView = wrapper;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            AppEntry entry = visibleApps.get(position);
            holder.icon.setImageDrawable(entry.icon);
            holder.label.setText(entry.label);
            holder.pkg.setText(entry.packageName);
            holder.mark.setVisibility(entry.packageName.equals(currentPackage) ? View.VISIBLE : View.INVISIBLE);
            return convertView;
        }
    }

    private static class RowHolder {
        final ImageView icon;
        final TextView label;
        final TextView pkg;
        final TextView mark;

        RowHolder(ImageView icon, TextView label, TextView pkg, TextView mark) {
            this.icon = icon;
            this.label = label;
            this.pkg = pkg;
            this.mark = mark;
        }
    }

    private static class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;

        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private Drawable backgroundGradient() {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 13, 26), Color.rgb(12, 20, 39), Color.rgb(7, 11, 22)}
        );
        return gd;
    }

    private Drawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) gd.setStroke(dp(strokeDp), stroke);
        return gd;
    }

    private String toPersianDigits(int value) {
        String raw = Integer.toString(value);
        return raw.replace('0', '۰').replace('1', '۱').replace('2', '۲').replace('3', '۳')
                .replace('4', '۴').replace('5', '۵').replace('6', '۶').replace('7', '۷')
                .replace('8', '۸').replace('9', '۹');
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
