package com.blackfalcon.fatanews;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, CARD = 0xFF121212, EDGE = 0xFF2A2A2A;
    private static final int[] COLORS = {0xFF2ECC71, 0xFFFF8A65, 0xFF64B5F6, 0xFFFFD54F, 0xFFBA68C8, 0xFF4DD0E1, 0xFFF06292, 0xFFAED581};

    private SharedPreferences sp;
    private boolean urdu = true;
    private int newsLang = -1; // -1 all, 0 Urdu, 1 English
    private String filter = "all", query = "";
    private final Map<String, List<Feeds.Item>> data = new HashMap<>();
    private final Map<String, String> status = new HashMap<>();
    private final List<Feeds.Item> visible = new ArrayList<>();
    private int generation = 0, finished = 0;
    private long lastUpdated = 0;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root, chipRow, langRow;
    private TextView titleTv, statusTv, uiBtn;
    private EditText search;
    private SwipeRefreshLayout srl;
    private ListView list;
    private ItemAdapter adapter;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 5 * 60 * 1000L); }
    };

    private String t(String en, String ur) { return urdu ? ur : en; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("fata", MODE_PRIVATE);
        urdu = sp.getBoolean("urdu", true);
        newsLang = sp.getInt("newsLang", -1);
        for (Section s : Section.ALL) loadCache(s.id);
        buildUi();
        applyTexts();
        rebuildChips();
        rebuildLangRow();
        rebuildList();
    }

    @Override protected void onResume() { super.onResume(); handler.post(autoRefresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(autoRefresh); }
    @Override protected void onDestroy() { super.onDestroy(); pool.shutdownNow(); }

    // ----------------------------------------------------------------- cache
    private static String flat(String s) { return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }

    private void saveCache(String id, List<Feeds.Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Feeds.Item it : items) {
            sb.append(it.ts).append('\t').append(it.lang).append('\t').append(flat(it.title)).append('\t')
                    .append(flat(it.link)).append('\t').append(flat(it.pub)).append('\n');
        }
        sp.edit().putString("cache_" + id, sb.toString()).apply();
    }

    private void loadCache(String id) {
        String raw = sp.getString("cache_" + id, "");
        List<Feeds.Item> items = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length >= 5) {
                Feeds.Item it = new Feeds.Item();
                it.sectionId = id;
                try { it.ts = Long.parseLong(p[0]); it.lang = Integer.parseInt(p[1]); } catch (NumberFormatException e) { it.ts = 0; }
                it.title = p[2]; it.link = p[3]; it.pub = p[4];
                if (!it.title.isEmpty() && !it.link.isEmpty()) items.add(it);
            }
        }
        if (!items.isEmpty()) data.put(id, items);
    }

    // --------------------------------------------------------------- loading
    private void refresh() {
        generation++;
        final int gen = generation;
        finished = 0;
        for (Section s : Section.ALL) status.put(s.id, "loading");
        srl.setRefreshing(true);
        updateStatus();
        for (final Section s : Section.ALL) {
            pool.execute(() -> {
                List<Feeds.Item> items = null;
                try { items = Feeds.load(s, 60); } catch (Exception ignored) { }
                final List<Feeds.Item> res = items;
                runOnUiThread(() -> {
                    if (gen != generation || isFinishing()) return;
                    finished++;
                    if (res != null && !res.isEmpty()) {
                        data.put(s.id, res);
                        status.put(s.id, "ok");
                        saveCache(s.id, res);
                        lastUpdated = System.currentTimeMillis();
                    } else {
                        status.put(s.id, res == null ? "error" : "empty");
                    }
                    if (finished >= Section.ALL.length) srl.setRefreshing(false);
                    rebuildList();
                });
            });
        }
    }

    // ------------------------------------------------------------------- UI
    private TextView tv(int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(r));
        return g;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF0B1F12);
        bar.setPadding(dp(16), dp(10), dp(10), dp(10));
        titleTv = tv(21, Color.WHITE, true);
        uiBtn = tv(13, Color.BLACK, true);
        uiBtn.setGravity(Gravity.CENTER);
        uiBtn.setPadding(dp(12), dp(7), dp(12), dp(7));
        uiBtn.setBackground(round(GREEN, GREEN, 16));
        uiBtn.setClickable(true);
        uiBtn.setOnClickListener(v -> {
            urdu = !urdu;
            sp.edit().putBoolean("urdu", urdu).apply();
            applyTexts();
            rebuildChips();
            rebuildLangRow();
            rebuildList();
        });
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(uiBtn);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setPadding(dp(8), dp(8), dp(8), dp(2));
        hs.addView(chipRow);

        langRow = new LinearLayout(this);
        langRow.setPadding(dp(8), dp(2), dp(8), dp(2));

        search = new EditText(this);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF707070);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setPadding(dp(16), dp(8), dp(16), dp(8));
        search.setBackground(round(CARD, EDGE, 20));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { query = s.toString().trim(); rebuildList(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.setMargins(dp(12), dp(4), dp(12), dp(2));

        statusTv = tv(12, GRAY, false);
        statusTv.setPadding(dp(16), dp(4), dp(16), dp(4));

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(dp(10), 0, dp(10), dp(6));
        list.setClipToPadding(false);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Feeds.Item it = visible.get(pos);
            Intent i = new Intent(this, BrowserActivity.class);
            i.putExtra("url", it.link);
            i.putExtra("title", it.title);
            startActivity(i);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Feeds.Item it = visible.get(pos);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, it.title + "\n" + it.link);
            startActivity(Intent.createChooser(i, null));
            return true;
        });

        // swipe down anywhere on the list to refresh
        srl = new SwipeRefreshLayout(this);
        srl.setColorSchemeColors(GREEN, 0xFFFFD54F, 0xFF64B5F6);
        srl.setProgressBackgroundColorSchemeColor(0xFF1C1C1C);
        srl.setOnRefreshListener(() -> refresh());
        srl.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView credit = tv(12, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(14), dp(4), dp(14), dp(6));
        credit.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        root.addView(bar);
        root.addView(hs);
        root.addView(langRow);
        root.addView(search, sl);
        root.addView(statusTv);
        root.addView(srl, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(credit);
        setContentView(root);
    }

    private void applyTexts() {
        root.setLayoutDirection(urdu ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        titleTv.setText(t("\uD83C\uDFD4 FATA News", "\uD83C\uDFD4 فاٹا خبریں"));
        uiBtn.setText(urdu ? "English" : "اردو");
        search.setHint(t("Search news", "خبروں میں تلاش کریں"));
    }

    private String nameOf(Section s) { return urdu ? s.nameUr : s.nameEn; }

    private int colorOf(String sectionId) {
        for (int i = 0; i < Section.ALL.length; i++) if (Section.ALL[i].id.equals(sectionId)) return COLORS[i % COLORS.length];
        return GREEN;
    }

    private void rebuildChips() {
        chipRow.removeAllViews();
        addChip("all", t("All", "تمام"), GREEN);
        for (Section s : Section.ALL) addChip(s.id, nameOf(s), colorOf(s.id));
    }

    private void addChip(final String id, String label, int color) {
        boolean on = id.equals(filter);
        TextView b = tv(13, on ? Color.BLACK : color, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackground(round(on ? color : 0xFF101010, color, 18));
        b.setClickable(true);
        b.setOnClickListener(v -> { filter = id; rebuildChips(); rebuildList(); list.setSelection(0); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), 0, dp(3), 0);
        chipRow.addView(b, lp);
    }

    private void rebuildLangRow() {
        langRow.removeAllViews();
        String[] names = {t("All languages", "تمام زبانیں"), "اردو", "English"};
        final int[] vals = {-1, 0, 1};
        for (int i = 0; i < 3; i++) {
            final int val = vals[i];
            boolean on = newsLang == val;
            TextView b = tv(12, on ? Color.BLACK : 0xFFB0B0B0, true);
            b.setText(names[i]);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(10), dp(6), dp(10), dp(6));
            b.setBackground(round(on ? 0xFFB0B0B0 : 0xFF101010, 0xFF555555, 14));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                newsLang = val;
                sp.edit().putInt("newsLang", val).apply();
                rebuildLangRow();
                rebuildList();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), 0, dp(3), 0);
            langRow.addView(b, lp);
        }
    }

    private static String key(String title) { return title.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }

    private void rebuildList() {
        visible.clear();
        Map<String, Feeds.Item> merged = new LinkedHashMap<>();
        for (Section s : Section.ALL) {
            if (!filter.equals("all") && !filter.equals(s.id)) continue;
            List<Feeds.Item> items = data.get(s.id);
            if (items == null) continue;
            for (Feeds.Item it : items) {
                if (newsLang != -1 && it.lang != newsLang) continue;
                String k = key(it.title);
                Feeds.Item old = merged.get(k);
                if (old == null || (old.pub.isEmpty() && !it.pub.isEmpty())) merged.put(k, it);
            }
        }
        String q = query.toLowerCase(Locale.ROOT);
        for (Feeds.Item it : merged.values()) {
            if (!q.isEmpty() && !it.title.toLowerCase(Locale.ROOT).contains(q)) continue;
            visible.add(it);
        }
        Collections.sort(visible, new Comparator<Feeds.Item>() {
            @Override public int compare(Feeds.Item a, Feeds.Item b) { return Long.compare(b.ts, a.ts); }
        });
        while (visible.size() > 500) visible.remove(visible.size() - 1);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void updateStatus() {
        int loading = 0, bad = 0;
        for (Section s : Section.ALL) {
            String st = status.get(s.id);
            if ("loading".equals(st)) loading++; else if ("error".equals(st)) bad++;
        }
        StringBuilder sb = new StringBuilder();
        if (loading > 0) sb.append(t("Loading… ", "لوڈ ہو رہا ہے… ")).append(finished).append("/").append(Section.ALL.length).append("   ");
        if (lastUpdated > 0) sb.append(t("Updated ", "اپڈیٹ ")).append(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(lastUpdated))).append("   ");
        sb.append(visible.size()).append(t(" stories", " خبریں"));
        if (loading == 0 && bad > 0) sb.append("   •   ").append(t("no connection for " + bad + " section(s) — saved news shown", "رابطہ نہیں ہوا — محفوظ خبریں دکھائی جا رہی ہیں"));
        if (loading == 0 && visible.isEmpty() && bad == 0) sb.append("   •   ").append(t("Swipe down to refresh", "ریفریش کے لیے نیچے کھینچیں"));
        statusTv.setText(sb.toString());
    }

    // ------------------------------------------------------------------ list
    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int i) { return visible.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) {
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setPadding(0, dp(4), 0, dp(4));
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackground(round(CARD, EDGE, 14));
                View strip = new View(MainActivity.this);
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(12), dp(10), dp(12), dp(10));
                TextView title = tv(17, Color.WHITE, true);
                title.setLineSpacing(0, 1.15f);
                title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                TextView meta = tv(11, GRAY, false);
                meta.setPadding(0, dp(6), 0, 0);
                col.addView(title);
                col.addView(meta);
                card.addView(strip, new LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT));
                card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                wrap.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                wrap.setTag(new View[]{strip, title, meta});
                convert = wrap;
            }
            View[] v = (View[]) convert.getTag();
            Feeds.Item it = visible.get(pos);
            int color = colorOf(it.sectionId);
            v[0].setBackgroundColor(color);
            ((TextView) v[1]).setText(it.title);
            long now = System.currentTimeMillis();
            Section s = Section.byId(it.sectionId);
            boolean fresh = now - it.ts < 3 * 3600000L;
            String meta = (it.pub.isEmpty() ? "" : it.pub + "  •  ") + Dates.ago(it.ts, now, urdu ? 1 : 0)
                    + (s == null ? "" : "  •  " + nameOf(s)) + (fresh ? "  \uD83D\uDD34 " + t("NEW", "تازہ") : "");
            ((TextView) v[2]).setText(meta);
            ((TextView) v[2]).setTextColor(fresh ? color : GRAY);
            return convert;
        }
    }
}
