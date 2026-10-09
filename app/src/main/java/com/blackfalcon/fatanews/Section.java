package com.blackfalcon.fatanews;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** A part of the tribal areas. News is found with Google News searches in Urdu and in English. */
public final class Section {
    public final String id, nameEn, nameUr, queryEn, queryUr;

    Section(String id, String nameEn, String nameUr, String queryEn, String queryUr) {
        this.id = id; this.nameEn = nameEn; this.nameUr = nameUr; this.queryEn = queryEn; this.queryUr = queryUr;
    }

    /** lang 0 = Urdu edition, 1 = English edition. */
    String url(int lang) {
        String q = (lang == 0 ? queryUr : queryEn) + " when:30d";
        try {
            String enc = URLEncoder.encode(q, "UTF-8");
            return lang == 0
                    ? "https://news.google.com/rss/search?q=" + enc + "&hl=ur&gl=PK&ceid=PK:ur"
                    : "https://news.google.com/rss/search?q=" + enc + "&hl=en-PK&gl=PK&ceid=PK:en";
        } catch (UnsupportedEncodingException e) {
            return "https://news.google.com/rss";
        }
    }

    public static final Section[] ALL = {
            new Section("fata", "FATA", "فاٹا",
                    "FATA OR \"tribal districts\" OR \"merged districts\" OR \"erstwhile FATA\" OR \"tribal areas\"",
                    "فاٹا OR \"قبائلی اضلاع\" OR \"ضم شدہ اضلاع\" OR \"قبائلی علاقے\" OR \"قبائلی علاقوں\""),
            new Section("bajaur", "Bajaur", "باجوڑ", "Bajaur", "باجوڑ"),
            new Section("mohmand", "Mohmand", "مہمند", "Mohmand", "مہمند"),
            new Section("khyber", "Khyber", "خیبر",
                    "\"Khyber district\" OR Tirah OR \"Landi Kotal\" OR Jamrud OR \"Bara tehsil\"",
                    "\"ضلع خیبر\" OR \"وادی تیراہ\" OR تیراہ OR \"لنڈی کوتل\" OR جمرود"),
            new Section("orakzai", "Orakzai", "اورکزئی", "Orakzai", "اورکزئی"),
            new Section("kurram", "Kurram", "کرم",
                    "Kurram OR Parachinar", "\"ضلع کرم\" OR \"پاڑا چنار\" OR \"کرم ایجنسی\""),
            new Section("nwaz", "North Waziristan", "شمالی وزیرستان",
                    "\"North Waziristan\" OR Miranshah OR \"Mir Ali\"", "\"شمالی وزیرستان\" OR \"میران شاہ\" OR \"میر علی\""),
            new Section("swaz", "South Waziristan", "جنوبی وزیرستان",
                    "\"South Waziristan\" OR Wana OR \"Ladha\"", "\"جنوبی وزیرستان\" OR وانا OR \"اپر جنوبی وزیرستان\""),
    };

    public static Section byId(String id) {
        for (Section s : ALL) if (s.id.equals(id)) return s;
        return null;
    }
}
