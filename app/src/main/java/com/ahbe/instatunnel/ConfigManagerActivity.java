package com.ahbe.instatunnel;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigManagerActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText configInput;
    private EditText subInput;
    private LinearLayout profileList;
    private TextView statusView;
    private Button fetchButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 11, 23));
        getWindow().setNavigationBarColor(Color.rgb(7, 11, 23));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildUi());
        refreshProfiles();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(7, 11, 23));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, lp(-1, -2, 0, 0, 0, 18));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text("کانفیگ و اشتراک", 26, Color.WHITE, true);
        titles.addView(title);
        TextView subtitle = text("VLESS • VMess • Trojan • Shadowsocks • SOCKS5 • Xray JSON", 12, Color.rgb(134, 148, 178), false);
        titles.addView(subtitle, lp(-1, -2, 0, 4, 0, 0));

        Button back = smallButton("بازگشت");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(82), dp(42)));

        LinearLayout pasteCard = card();
        root.addView(pasteCard, lp(-1, -2, 0, 0, 0, 14));
        pasteCard.addView(text("افزودن کانفیگ", 17, Color.WHITE, true));
        pasteCard.addView(text("یک یا چند لینک کانفیگ را پیست کن. متن Base64 ساب هم قابل تشخیص است.", 12, Color.rgb(137, 151, 180), false), lp(-1, -2, 0, 5, 0, 10));

        configInput = input("vless://...\nvmess://...\ntrojan://...", true);
        pasteCard.addView(configInput, lp(-1, dp(142), 0, 0, 0, 10));

        LinearLayout pasteActions = new LinearLayout(this);
        pasteActions.setOrientation(LinearLayout.HORIZONTAL);
        pasteCard.addView(pasteActions, lp(-1, dp(50), 0, 0, 0, 0));

        Button paste = actionButton("چسباندن", false);
        paste.setOnClickListener(v -> pasteClipboard());
        pasteActions.addView(paste, new LinearLayout.LayoutParams(0, -1, 1f));

        Button add = actionButton("افزودن", true);
        add.setOnClickListener(v -> addConfigs());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(0, -1, 1.35f);
        addLp.setMargins(dp(8), 0, 0, 0);
        pasteActions.addView(add, addLp);

        LinearLayout subCard = card();
        root.addView(subCard, lp(-1, -2, 0, 0, 0, 14));
        subCard.addView(text("لینک اشتراک (Subscription)", 17, Color.WHITE, true));
        subCard.addView(text("لینک ساب را وارد کن؛ برنامه محتوا را دریافت، رمزگشایی و کانفیگ‌های سازگار را اضافه می‌کند.", 12, Color.rgb(137, 151, 180), false), lp(-1, -2, 0, 5, 0, 10));

        subInput = input("https://example.com/subscription", false);
        subInput.setSingleLine(true);
        subInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        subCard.addView(subInput, lp(-1, dp(56), 0, 0, 0, 10));

        fetchButton = actionButton("دریافت و افزودن ساب", true);
        fetchButton.setOnClickListener(v -> fetchSubscription());
        subCard.addView(fetchButton, lp(-1, dp(54), 0, 0, 0, 0));

        statusView = text("", 13, Color.rgb(131, 218, 190), false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setVisibility(View.GONE);
        root.addView(statusView, lp(-1, -2, 0, 0, 0, 14));

        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(listHeader, lp(-1, -2, 4, 0, 4, 10));

        TextView listTitle = text("کانفیگ‌های ذخیره‌شده", 15, Color.rgb(191, 202, 227), true);
        listHeader.addView(listTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        Button clear = smallButton("حذف همه");
        clear.setOnClickListener(v -> {
            ProfileManager.clear(this);
            refreshProfiles();
            showStatus("همه کانفیگ‌ها حذف شدند.", false);
        });
        listHeader.addView(clear, new LinearLayout.LayoutParams(dp(92), dp(40)));

        profileList = new LinearLayout(this);
        profileList.setOrientation(LinearLayout.VERTICAL);
        root.addView(profileList, new LinearLayout.LayoutParams(-1, -2));

        TextView note = text("نکته: کانفیگ‌های دارای plugin در Shadowsocks و فرمت‌های اختصاصی غیر Xray ممکن است نیاز به تبدیل داشته باشند.", 11, Color.rgb(105, 119, 147), false);
        note.setGravity(Gravity.CENTER);
        root.addView(note, lp(-1, -2, 10, 18, 10, 0));
        return scroll;
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence value = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            configInput.setText(value == null ? "" : value.toString());
            configInput.setSelection(configInput.length());
        } else {
            Toast.makeText(this, "کلیپ‌بورد خالی است.", Toast.LENGTH_SHORT).show();
        }
    }

    private void addConfigs() {
        String text = configInput.getText().toString().trim();
        if (text.isEmpty()) {
            showStatus("ابتدا کانفیگ را وارد یا پیست کن.", true);
            return;
        }
        try {
            int added = ProfileManager.addFromText(this, text);
            refreshProfiles();
            if (added > 0) {
                configInput.setText("");
                showStatus(added + " کانفیگ اضافه شد.", false);
            } else {
                showStatus("کانفیگ جدیدی نبود؛ موارد تکراری اضافه نشدند.", false);
            }
        } catch (Exception e) {
            showStatus(e.getMessage() == null ? "کانفیگ قابل تشخیص نیست." : e.getMessage(), true);
        }
    }

    private void fetchSubscription() {
        String url = subInput.getText().toString().trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            showStatus("لینک ساب باید با http:// یا https:// شروع شود.", true);
            return;
        }

        fetchButton.setEnabled(false);
        showStatus("در حال دریافت اشتراک…", false);
        worker.execute(() -> {
            try {
                String body = download(url);
                int added = ProfileManager.addFromText(this, body);
                runOnUiThread(() -> {
                    fetchButton.setEnabled(true);
                    refreshProfiles();
                    showStatus(added > 0 ? added + " کانفیگ از ساب اضافه شد." : "ساب دریافت شد ولی کانفیگ جدیدی نداشت.", false);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "دریافت اشتراک ناموفق بود." : e.getMessage();
                runOnUiThread(() -> {
                    fetchButton.setEnabled(true);
                    showStatus(message, true);
                });
            }
        });
    }

    private String download(String address) throws Exception {
        URL url = new URL(address);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "InstaTunnel/0.4 Android");
        conn.setRequestProperty("Accept", "text/plain,*/*");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("خطای دریافت ساب: HTTP " + code);

        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > 2 * 1024 * 1024) throw new Exception("حجم ساب بیشتر از ۲ مگابایت است.");
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name()).trim();
        } finally {
            conn.disconnect();
        }
    }

    private void refreshProfiles() {
        if (profileList == null) return;
        profileList.removeAllViews();
        List<ProfileManager.Profile> profiles = ProfileManager.getProfiles(this);
        ProfileManager.Profile selected = ProfileManager.getSelected(this);

        if (profiles.isEmpty()) {
            LinearLayout empty = card();
            TextView icon = text("＋", 34, Color.rgb(122, 139, 184), true);
            icon.setGravity(Gravity.CENTER);
            empty.addView(icon);
            TextView emptyText = text("هنوز کانفیگی اضافه نشده", 14, Color.rgb(160, 173, 202), true);
            emptyText.setGravity(Gravity.CENTER);
            empty.addView(emptyText, lp(-1, -2, 0, 4, 0, 0));
            profileList.addView(empty, lp(-1, -2, 0, 0, 0, 0));
            return;
        }

        for (ProfileManager.Profile p : profiles) {
            boolean isSelected = selected != null && selected.id.equals(p.id);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(12), dp(12));
            row.setBackground(roundRect(
                    isSelected ? Color.rgb(22, 45, 52) : Color.rgb(13, 21, 38),
                    19,
                    isSelected ? Color.rgb(58, 142, 120) : Color.rgb(31, 44, 70),
                    isSelected ? 2 : 1));
            row.setOnClickListener(v -> {
                ProfileManager.select(this, p.id);
                refreshProfiles();
                showStatus("انتخاب شد: " + p.name, false);
            });

            TextView badge = text(p.displayType(), 10, isSelected ? Color.rgb(152, 238, 211) : Color.rgb(169, 181, 210), true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundRect(isSelected ? Color.rgb(20, 67, 58) : Color.rgb(27, 35, 58), 12, Color.TRANSPARENT, 0));
            row.addView(badge, new LinearLayout.LayoutParams(dp(82), dp(34)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, -2, 1f);
            infoLp.setMargins(dp(10), 0, dp(8), 0);
            row.addView(info, infoLp);

            TextView name = text(p.name, 14, Color.WHITE, true);
            name.setSingleLine(true);
            info.addView(name);
            TextView state = text(isSelected ? "● فعال" : "برای انتخاب لمس کن", 11,
                    isSelected ? Color.rgb(127, 224, 194) : Color.rgb(107, 121, 151), false);
            info.addView(state, lp(-1, -2, 0, 3, 0, 0));

            Button delete = smallButton("حذف");
            delete.setOnClickListener(v -> {
                ProfileManager.delete(this, p.id);
                refreshProfiles();
            });
            row.addView(delete, new LinearLayout.LayoutParams(dp(64), dp(38)));
            profileList.addView(row, lp(-1, -2, 0, 0, 0, 9));
        }
    }

    private void showStatus(String message, boolean error) {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(message);
        statusView.setTextColor(error ? Color.rgb(255, 137, 144) : Color.rgb(131, 218, 190));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(roundRect(Color.rgb(12, 19, 35), 22, Color.rgb(29, 42, 68), 1));
        card.setElevation(dp(3));
        return card;
    }

    private EditText input(String hint, boolean multiline) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setHintTextColor(Color.rgb(87, 101, 130));
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(13);
        edit.setPadding(dp(13), dp(10), dp(13), dp(10));
        edit.setGravity(multiline ? Gravity.TOP | Gravity.RIGHT : Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        edit.setBackground(roundRect(Color.rgb(8, 14, 27), 16, Color.rgb(39, 53, 82), 1));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        return edit;
    }

    private Button actionButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setTextColor(Color.WHITE);
        b.setBackground(roundRect(primary ? Color.rgb(73, 76, 191) : Color.rgb(28, 37, 62), 16,
                primary ? Color.rgb(110, 115, 235) : Color.rgb(52, 67, 104), 1));
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(Color.rgb(184, 196, 224));
        b.setBackground(roundRect(Color.rgb(23, 31, 52), 14, Color.rgb(48, 62, 94), 1));
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

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) d.setStroke(dp(strokeWidthDp), strokeColor);
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
        worker.shutdownNow();
        super.onDestroy();
    }
}
