package com.blackfalcon.fatanews;

import android.os.Build;
import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Downloads and reads the Google News RSS search results. */
public final class Feeds {
    private Feeds() { }

    public static final class Item {
        public String sectionId = "", title = "", link = "", pub = "";
        public int lang; // 0 Urdu, 1 English
        public long ts;
    }

    private static String clean(String t) {
        if (t == null) return "";
        String s;
        if (Build.VERSION.SDK_INT >= 24) s = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString();
        else s = legacy(t);
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    @SuppressWarnings("deprecation")
    private static String legacy(String t) { return Html.fromHtml(t).toString(); }

    static List<Item> fetch(String urlStr, String sectionId, int lang, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) FataNews/1.0");
        c.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
        int code = c.getResponseCode();
        if (code / 100 != 2) { c.disconnect(); throw new Exception("HTTP " + code); }
        InputStream in = new BufferedInputStream(c.getInputStream());
        try {
            return parse(in, sectionId, lang, max);
        } finally {
            try { in.close(); } catch (Exception ignored) { }
            c.disconnect();
        }
    }

    static List<Item> parse(InputStream in, String sectionId, int lang, int max) throws Exception {
        XmlPullParser xp = Xml.newPullParser();
        xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        xp.setInput(in, null);
        List<Item> out = new ArrayList<>();
        boolean inItem = false;
        String title = "", link = "", date = "", pub = "";
        long base = System.currentTimeMillis();
        int ev = xp.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT && out.size() < max) {
            if (ev == XmlPullParser.START_TAG) {
                String n = xp.getName();
                if ("item".equals(n)) {
                    inItem = true; title = ""; link = ""; date = ""; pub = "";
                } else if (inItem) {
                    if ("title".equals(n)) title = xp.nextText();
                    else if ("link".equals(n)) link = xp.nextText();
                    else if ("pubDate".equals(n)) date = xp.nextText();
                    else if ("source".equals(n)) pub = xp.nextText();
                }
            } else if (ev == XmlPullParser.END_TAG && "item".equals(xp.getName()) && inItem) {
                inItem = false;
                String t = clean(title), p = clean(pub);
                if (!p.isEmpty() && t.endsWith(" - " + p)) t = t.substring(0, t.length() - p.length() - 3).trim();
                else t = Dates.stripSource(t);
                String l = link == null ? "" : link.trim();
                if (!t.isEmpty() && !l.isEmpty()) {
                    Item it = new Item();
                    it.sectionId = sectionId;
                    it.lang = lang;
                    it.title = t;
                    it.link = l;
                    it.pub = p;
                    long ts = Dates.parse(date);
                    it.ts = ts > 0 ? Math.min(ts, base) : base - out.size() * 1000L;
                    out.add(it);
                }
            }
            ev = xp.next();
        }
        return out;
    }

    /** Urdu and English results of one section, joined. Fails only if both fail. */
    public static List<Item> load(Section s, int max) throws Exception {
        List<Item> all = new ArrayList<>();
        Exception last = null;
        for (int lang = 0; lang < 2; lang++) {
            try {
                all.addAll(fetch(s.url(lang), s.id, lang, max));
            } catch (Exception e) {
                last = e;
            }
        }
        if (all.isEmpty() && last != null) throw last;
        return all;
    }
}
