package com.frontline.offline;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline presentation only: no device, Android, game-state or persistence dependencies.
 * Keys are the first column of each catalog row below. Named fields are positional arguments
 * in English first-occurrence order; fields(key) enumerates that public call contract.
 * Numbers use the chosen locale; callers pass numbers, not preformatted numeric strings.
 * String arguments (names, dates, clock times) are already localized presentation values.
 * Use text for new code. translate/legacy recognize historical English sentences during
 * migration, leaving unknown text and language-independent identifiers unchanged.
 * Hindi Unicode is intentional: this catalog must bundle real Devanagari, not transliteration.
 * Rendering, native font fallback, shaping and wrapping belong to the graphics adapter.
 */
public final class Localization {
    public static final List<String> LANGUAGES = Collections.unmodifiableList(
        java.util.Arrays.asList("en", "id", "hi"));
    private static final Pattern FIELD = Pattern.compile("\\{([a-z][a-z_]*)(?::(02|2f))?\\}");
    private static final Pattern NUMBER = Pattern.compile("-?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?");
    private static final Pattern FACTION_STATUS = Pattern.compile("^(YOU|Ember|Auric|Vesper|Iron|Frost) (OUT|[0-9]+%)$");
    private static final Pattern DIFFICULTY_RECORD = Pattern.compile("^(Easy|Normal|Hard) ([0-9,]+) / ([0-9]+:[0-9]{2})$");
    private static final Pattern SLASH = Pattern.compile("^(.+?) / (.+)$");
    private static final Map<String, Map<String, String>> CATALOGS = new LinkedHashMap<>();
    private static final Map<String, List<String>> FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> LITERALS = new LinkedHashMap<>();
    private static final List<Sentence> SENTENCES = new ArrayList<>();

    static {
        for (String language : LANGUAGES) CATALOGS.put(language, new LinkedHashMap<String, String>());
        ui();
        results();
        tutorialAndRules();
        content();
        laterModes();
        for (Map.Entry<String, String> entry : CATALOGS.get("en").entrySet()) {
            String key = entry.getKey(), english = entry.getValue();
            if (FIELDS.get(key).isEmpty()) {
                if (!LITERALS.containsKey(english)) LITERALS.put(english, key);
                String upper = english.toUpperCase(Locale.ROOT);
                if (!LITERALS.containsKey(upper)) LITERALS.put(upper, key);
            } else {
                Sentence sentence = new Sentence(key, english);
                if (sentence.literalLetters > 0) SENTENCES.add(sentence);
            }
        }
        Collections.sort(SENTENCES, new Comparator<Sentence>() {
            @Override public int compare(Sentence a, Sentence b) {
                return Integer.compare(b.literalLetters, a.literalLetters);
            }
        });
        for (String language : LANGUAGES)
            CATALOGS.put(language, Collections.unmodifiableMap(CATALOGS.get(language)));
    }

    private Localization() {}

    /** Exactly en/id/hi; invalid, absent or unsupported preferences safely select English. */
    public static String language(String language) {
        return LANGUAGES.contains(language) ? language : "en";
    }

    /** Selector labels intentionally never follow the current interface language. */
    public static String nativeName(String language) {
        String code = language(language);
        return "id".equals(code) ? "Bahasa Indonesia" : "hi".equals(code) ? "हिन्दी" : "English";
    }

    public static Set<String> keys() { return CATALOGS.get("en").keySet(); }
    public static Map<String, String> catalog(String language) { return CATALOGS.get(language(language)); }
    public static List<String> fields(String key) {
        List<String> fields = FIELDS.get(key);
        return fields == null ? Collections.<String>emptyList() : fields;
    }

    /** Unknown keys remain visible for diagnosis; wrong known-key arity is a wiring error. */
    public static String text(String language, String key, Object... args) {
        if (key == null) return "";
        String code = language(language), template = catalog(code).get(key);
        if (template == null) return key;
        List<String> names = fields(key);
        Object[] values = args == null ? new Object[0] : args;
        if (values.length != names.size())
            throw new IllegalArgumentException(key + " expects " + names + ", got " + values.length + " arguments");
        Matcher matcher = FIELD.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = values[names.indexOf(matcher.group(1))];
            matcher.appendReplacement(result, Matcher.quoteReplacement(format(code, value, matcher.group(2))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String translate(String language, String source) { return legacy(language, source); }
    public static String legacy(String language, String english) {
        return legacy(language(language), english, 0);
    }

    private static String legacy(String language, String english, int depth) {
        if (english == null) return "";
        if ("en".equals(language) || english.isEmpty() || depth > 8) return english;
        String key = LITERALS.get(english);
        if (key != null) {
            String translated = text(language, key);
            String original = CATALOGS.get("en").get(key);
            return !english.equals(original) && english.equals(original.toUpperCase(Locale.ROOT))
                ? translated.toUpperCase(locale(language)) : translated;
        }
        if (NUMBER.matcher(english).matches()) return rawNumber(language, english);
        if (english.matches("[0-9]+(?:\\.[0-9]+)?%"))
            return number(language, new BigDecimal(english.substring(0, english.length() - 1)), false) + "%";
        for (Sentence sentence : SENTENCES) {
            Matcher match = sentence.pattern.matcher(english);
            if (!match.matches()) continue;
            Object[] args = new Object[fields(sentence.key).size()];
            boolean compatible = true;
            for (int i = 0; i < sentence.names.size(); i++) {
                String value = match.group(i + 1), name = sentence.names.get(i);
                Object argument = NUMBER.matcher(value).matches() ? decimal(value)
                    : legacy(language, value, depth + 1);
                int index = fields(sentence.key).indexOf(name);
                if (args[index] != null && !args[index].equals(argument)) compatible = false;
                args[index] = argument;
            }
            if (compatible) return text(language, sentence.key, args);
        }
        // These are structural composites emitted by V10, not arbitrary word replacement.
        Matcher record = DIFFICULTY_RECORD.matcher(english);
        if (record.matches()) return text(language, "record.difficulty_score",
            legacy(language, record.group(1), depth + 1), decimal(record.group(2)), record.group(3));
        Matcher faction = FACTION_STATUS.matcher(english);
        if (faction.matches()) return legacy(language, faction.group(1), depth + 1) + " "
            + legacy(language, faction.group(2), depth + 1);
        Matcher slash = SLASH.matcher(english);
        if (slash.matches()) {
            String first = legacy(language, slash.group(1), depth + 1);
            String second = legacy(language, slash.group(2), depth + 1);
            if (!first.equals(slash.group(1)) || !second.equals(slash.group(2))) return first + " / " + second;
        }
        return english;
    }

    private static BigDecimal decimal(String english) { return new BigDecimal(english.replace(",", "")); }

    private static String rawNumber(String language, String english) {
        NumberFormat format = NumberFormat.getNumberInstance(locale(language));
        String digits = english.replace(",", "").replace("-", "");
        int point = digits.indexOf('.');
        format.setGroupingUsed(english.indexOf(',') >= 0);
        format.setMinimumIntegerDigits(point < 0 ? digits.length() : point);
        int fraction = point < 0 ? 0 : digits.length() - point - 1;
        format.setMinimumFractionDigits(fraction);
        format.setMaximumFractionDigits(fraction);
        return format.format(decimal(english));
    }

    private static Locale locale(String language) {
        return "id".equals(language) ? new Locale("id", "ID")
            : "hi".equals(language) ? new Locale("hi", "IN") : Locale.US;
    }

    private static String number(String language, Number value, boolean grouping) {
        NumberFormat format = NumberFormat.getNumberInstance(locale(language));
        format.setGroupingUsed(grouping);
        format.setMaximumFractionDigits(6);
        return format.format(value);
    }

    private static String format(String language, Object value, String style) {
        if (style == null) return value instanceof Number ? number(language, (Number) value, true) : String.valueOf(value);
        if (!(value instanceof Number)) throw new IllegalArgumentException("Numeric field requires a Number: " + value);
        NumberFormat format = NumberFormat.getNumberInstance(locale(language));
        format.setGroupingUsed(false);
        if ("02".equals(style)) { format.setMinimumIntegerDigits(2); format.setMaximumFractionDigits(0); }
        else { format.setMinimumFractionDigits(2); format.setMaximumFractionDigits(2); }
        return format.format(value);
    }

    private static void add(String key, String en, String id, String hi) {
        String[] values = {en, id, hi};
        List<String> fields = fieldNames(en);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || values[i].trim().isEmpty()
                || !new java.util.HashSet<>(fields).equals(new java.util.HashSet<>(fieldNames(values[i]))))
                throw new IllegalStateException("Incomplete catalog or incompatible fields: " + key);
            if (CATALOGS.get(LANGUAGES.get(i)).put(key, values[i]) != null)
                throw new IllegalStateException("Duplicate localization key: " + key);
        }
        FIELDS.put(key, Collections.unmodifiableList(fields));
    }

    private static List<String> fieldNames(String template) {
        List<String> names = new ArrayList<>();
        Matcher matcher = FIELD.matcher(template);
        while (matcher.find()) if (!names.contains(matcher.group(1))) names.add(matcher.group(1));
        return names;
    }

    private static final class Sentence {
        final String key;
        final Pattern pattern;
        final List<String> names = new ArrayList<>();
        int literalLetters;
        Sentence(String key, String template) {
            this.key = key;
            Matcher matcher = FIELD.matcher(template);
            StringBuilder regex = new StringBuilder("^");
            int end = 0;
            while (matcher.find()) {
                String literal = template.substring(end, matcher.start());
                for (int i = 0; i < literal.length(); i++) if (Character.isLetter(literal.charAt(i))) literalLetters++;
                regex.append(Pattern.quote(literal)).append(matcher.group(2) == null ? "(.+?)" : "([0-9]+(?:\\.[0-9]+)?)");
                names.add(matcher.group(1)); end = matcher.end();
            }
            String literal = template.substring(end);
            for (int i = 0; i < literal.length(); i++) if (Character.isLetter(literal.charAt(i))) literalLetters++;
            regex.append(Pattern.quote(literal)).append('$');
            pattern = Pattern.compile(regex.toString());
        }
    }

    private static void ui() {
        add("brand", "FRONTLINE", "FRONTLINE", "FRONTLINE");
        add("brand.tagline", "OFFLINE TACTICS", "TAKTIK OFFLINE", "ऑफ़लाइन रणनीति");
        add("settings.title", "Settings", "Pengaturan", "सेटिंग्स");
        add("settings.language", "Language / Bahasa / भाषा", "Language / Bahasa / भाषा", "Language / Bahasa / भाषा");
        add("language.en", "English", "English", "English");
        add("language.id", "Bahasa Indonesia", "Bahasa Indonesia", "Bahasa Indonesia");
        add("language.hi", "हिन्दी", "हिन्दी", "हिन्दी");
        add("settings.music", "Music", "Musik", "संगीत");
        add("settings.sound", "Sound Effects", "Efek Suara", "ध्वनि प्रभाव");
        add("settings.haptics", "Vibration", "Getaran", "कंपन");
        add("settings.difficulty", "Difficulty", "Kesulitan", "कठिनाई");
        add("settings.next_attempt", "NEXT ATTEMPT", "PERCOBAAN BERIKUT", "अगला प्रयास");
        add("settings.log", "Local Playtest Log", "Catatan Uji Lokal", "स्थानीय परीक्षण लॉग");
        add("settings.export", "Export CSV", "Ekspor CSV", "CSV निर्यात");
        add("settings.clear_log", "Clear Log", "Hapus Catatan", "लॉग मिटाएँ");
        add("difficulty.easy", "Easy", "Mudah", "आसान");
        add("difficulty.normal", "Normal", "Normal", "सामान्य");
        add("difficulty.hard", "Hard", "Sulit", "कठिन");
        add("action.back", "Back", "Kembali", "वापस");
        add("action.continue", "Continue", "Lanjutkan", "जारी रखें");
        add("action.done", "Done", "Selesai", "पूरा हुआ");
        add("action.next", "Next", "Berikutnya", "अगला");
        add("action.cancel", "Cancel", "Batal", "रद्द करें");
        add("action.restart", "Restart", "Mulai Ulang", "फिर शुरू करें");
        add("action.replace", "Replace", "Ganti", "बदलें");
        add("action.start_attempt", "Start Attempt", "Mulai Percobaan", "प्रयास शुरू करें");
        add("action.start_battle", "Start Battle", "Mulai Pertempuran", "लड़ाई शुरू करें");
        add("home.new_attempt", "New Attempt", "Percobaan Baru", "नया प्रयास");
        add("home.continue_battle", "Continue Battle", "Lanjutkan Pertempuran", "लड़ाई जारी रखें");
        add("home.select_sector", "Select Sector", "Pilih Sektor", "सेक्टर चुनें");
        add("home.menu", "Main Menu", "Menu Utama", "मुख्य मेनू");
        add("home.sector", "Sector {sector} / {name}", "Sektor {sector} / {name}", "सेक्टर {sector} / {name}");
        add("home.sector_padded", "SECTOR {sector:02} / {name}", "SEKTOR {sector:02} / {name}", "सेक्टर {sector:02} / {name}");
        add("home.in_progress", "IN PROGRESS / {attempt}", "BERLANGSUNG / {attempt}", "जारी है / {attempt}");
        add("home.cleared", "{cleared} / {total} CLEARED", "{cleared} / {total} SELESAI", "{cleared} / {total} पूरे");
        add("home.total", "BEST TOTAL  {score}", "TOTAL TERBAIK  {score}", "सर्वश्रेष्ठ कुल  {score}");
        add("pause.title", "Paused", "Dijeda", "रुका हुआ");
        add("pause.action", "Pause", "Jeda", "रोकें");
        add("pause.resume", "Resume", "Lanjutkan", "जारी रखें");
        add("pause.restart_sector", "Restart Sector", "Ulangi Sektor", "सेक्टर फिर शुरू करें");
        add("pause.restart_round", "Restart Round", "Ulangi Babak", "दौर फिर शुरू करें");
        add("campaign.title", "CAMPAIGN", "KAMPANYE", "अभियान");
        add("campaign.sectors", "Sectors", "Sektor", "सेक्टर");
        add("campaign.chapter", "CHAPTER {chapter:02} / {total:02}", "BAB {chapter:02} / {total:02}", "अध्याय {chapter:02} / {total:02}");
        add("campaign.rulers_three", "VOSS / SOL / VEIL", "VOSS / SOL / VEIL", "वॉस / सोल / वेल");
        add("campaign.rulers_five", "FIVE RIVAL RULERS", "LIMA PENGUASA RIVAL", "पाँच प्रतिद्वंद्वी शासक");
        add("campaign.previous", "Previous Chapter", "Bab Sebelumnya", "पिछला अध्याय");
        add("campaign.next", "Next Chapter", "Bab Berikutnya", "अगला अध्याय");
        add("campaign.locked", "Locked - clear Sector {sector:02}", "Terkunci - tuntaskan Sektor {sector:02}", "बंद - सेक्टर {sector:02} पूरा करें");
        add("campaign.cleared", "Cleared / {record}", "Selesai / {record}", "पूरा / {record}");
        add("campaign.selected", "Selected / {record}", "Dipilih / {record}", "चुना गया / {record}");
        add("campaign.unlocked", "Unlocked / {record}", "Terbuka / {record}", "खुला / {record}");
        add("record.no_difficulty", "No {difficulty} record", "Belum ada rekor {difficulty}", "{difficulty} का कोई रिकॉर्ड नहीं");
        add("record.legacy_suffix", "{record} / Legacy {score}", "{record} / Lama {score}", "{record} / पुराना {score}");
        add("record.legacy_score", "LEGACY  {score} score / difficulty unknown", "LAMA  skor {score} / kesulitan tak diketahui", "पुराना  स्कोर {score} / कठिनाई अज्ञात");
        add("record.personal_best", "PERSONAL BEST  {time}", "REKOR PRIBADI  {time}", "व्यक्तिगत सर्वश्रेष्ठ  {time}");
        add("record.best_score", "BEST SCORE  {score}", "SKOR TERBAIK  {score}", "सर्वश्रेष्ठ स्कोर  {score}");
        add("record.best_time", "BEST TIME  {time}", "WAKTU TERBAIK  {time}", "सर्वश्रेष्ठ समय  {time}");
        add("record.never_played", "Unplayed", "Belum dimainkan", "अभी नहीं खेला");
        add("challenges.title", "Challenges", "Tantangan", "चुनौतियाँ");
        add("challenges.missions", "Missions", "Misi", "मिशन");
        add("challenges.hold", "Hold King", "Kuasai Raja", "राजा पर कब्ज़ा");
        add("challenges.budget", "Troop Budget", "Batas Pasukan", "सैनिक सीमा");
        add("challenges.guidance", "Choose an objective. Improve your strategy on each attempt.", "Pilih tujuan. Asah strategi pada tiap percobaan.", "लक्ष्य चुनें। हर प्रयास में रणनीति सुधारें।");
        add("challenges.old_guidance", "Initial timers and budgets / playtest candidates", "Pilih tujuan dan coba strategi baru.", "लक्ष्य चुनें और नई रणनीति आज़माएँ।");
        add("daily.title", "Daily Mission", "Misi Harian", "दैनिक मिशन");
        add("daily.today", "Today's Mission", "Misi Hari Ini", "आज का मिशन");
        add("daily.continue", "Continue Saved Daily", "Lanjutkan Misi Tersimpan", "सहेजा दैनिक मिशन जारी रखें");
        add("daily.fixed_difficulty", "NORMAL / fixed difficulty", "NORMAL / kesulitan tetap", "सामान्य / तय कठिनाई");
        add("daily.reset", "Resets 00:00 UTC / in {countdown}", "Reset 00:00 UTC / dalam {countdown}", "रीसेट 00:00 UTC / {countdown} में");
        add("daily.saved", "Saved daily: {date}", "Misi tersimpan: {date}", "सहेजा दैनिक मिशन: {date}");
        add("daily.available", "Available", "Tersedia", "उपलब्ध");
        add("daily.completed", "Completed", "Selesai", "पूरा हुआ");
        add("daily.not_completed", "Not completed", "Belum selesai", "अभी पूरा नहीं");
        add("daily.failed", "Failed", "Gagal", "असफल");
        add("confirm.title", "Replace Battle?", "Ganti Pertempuran?", "लड़ाई बदलें?");
        add("confirm.discard", "The unfinished attempt will be discarded.", "Percobaan yang belum selesai akan dibuang.", "अधूरा प्रयास हटा दिया जाएगा।");
        add("confirm.keep_progress", "Scores and completed sectors are kept.", "Skor dan sektor selesai tetap tersimpan.", "स्कोर और पूरे सेक्टर सुरक्षित रहेंगे।");
        add("confirm.keep_battle", "Keep Battle", "Pertahankan Pertempuran", "लड़ाई बनाए रखें");
        add("attempt.sector", "Sector {sector}", "Sektor {sector}", "सेक्टर {sector}");
        add("attempt.challenge", "Challenge / Sector {sector}", "Tantangan / Sektor {sector}", "चुनौती / सेक्टर {sector}");
        add("attempt.daily", "Daily {date}", "Harian {date}", "दैनिक {date}");
        add("hud.boost", "BOOST x{boost:2f}", "BONUS x{boost:2f}", "बढ़त x{boost:2f}");
        add("hud.kings", "{kings} KINGS", "{kings} RAJA", "{kings} राजा");
        add("hud.out", "OUT", "KELUAR", "बाहर");
        add("hud.max", "MAX", "MAKS", "अधिकतम");
        add("hud.army", "ARMY", "PASUKAN", "सेना");
        add("hud.tile", "TILE {tile}", "PETAK {tile}", "क्षेत्र {tile}");
        add("hud.tile_max", "TILE {tile} / MAX", "PETAK {tile} / MAKS", "क्षेत्र {tile} / अधिकतम");
        add("hud.deploy", "DEPLOY", "KIRIM", "भेजें");
        add("deploy.preview", "SEND {send} / LEFT {left}", "KIRIM {send} / SISA {left}", "भेजें {send} / शेष {left}");
        add("deploy.preview_warning", "SEND {send} / LEFT {left} !", "KIRIM {send} / SISA {left} !", "भेजें {send} / शेष {left} !");
        add("budget.warning", "Sending {send} exceeds the remaining {remaining} troops.", "Mengirim {send} melebihi sisa jatah {remaining} pasukan.", "{send} सैनिक भेजना शेष {remaining} की सीमा से अधिक है।");
        add("budget.warning_short", "Over budget: {send} sent > {remaining} remaining", "Lewati batas: {send} > sisa {remaining}", "सीमा पार: {send} > शेष {remaining}");
        add("warning.budget", "Sending {send} exceeds the remaining {remaining} troops.", "Mengirim {send} melebihi sisa jatah {remaining} pasukan.", "{send} सैनिक भेजना शेष {remaining} की सीमा से अधिक है।");
        add("budget.hud", "BUDGET {used} / {budget} / REMAINING {remaining}", "BATAS {used} / {budget} / SISA {remaining}", "सीमा {used} / {budget} / शेष {remaining}");
        add("hud.star_target", "3 STARS  {time}", "3 BINTANG  {time}", "3 सितारे  {time}");
        add("camera.out", "Zoom Out", "Perkecil", "छोटा करें");
        add("camera.in", "Zoom In", "Perbesar", "बड़ा करें");
        add("camera.fit", "Fit Battlefield", "Tampilkan Seluruh Peta", "पूरा मैदान दिखाएँ");
        add("camera.title", "Larger Battlefield", "Medan Lebih Luas", "बड़ा युद्धक्षेत्र");
        add("camera.pinch", "Pinch to zoom; use two fingers to pan.", "Cubit untuk zoom; geser dengan dua jari.", "दो उँगलियों से ज़ूम करें और दृश्य खिसकाएँ।");
        add("camera.deploy", "One finger on your tile deploys troops.", "Satu jari di petakmu mengirim pasukan.", "अपने क्षेत्र से एक उँगली से सैनिक भेजें।");
        add("camera.pan", "When zoomed, drag other space to pan.", "Saat zoom, geser area lain untuk pindah.", "ज़ूम में अन्य जगह खींचकर दृश्य खिसकाएँ।");
        add("camera.restore", "The fit icon restores the full board.", "Ikon pas peta menampilkan seluruh medan.", "पूरा मैदान दिखाने वाला आइकन दृश्य लौटाता है।");
        add("unlock.enter", "Enter Code", "Masukkan Kode", "कोड डालें");
        add("unlock.hint", "Enter code", "Masukkan kode", "कोड डालें");
        add("unlock.invalid", "Invalid code", "Kode tidak valid", "गलत कोड");
        add("unlock.title", "Unlock Sectors", "Buka Sektor", "सेक्टर खोलें");
        add("unlock.action", "Unlock", "Buka", "खोलें");
        add("unlock.success", "All {count} sectors unlocked", "Semua {count} sektor terbuka", "सभी {count} सेक्टर खुल गए");
        add("export.success", "Playtest CSV exported", "CSV uji bermain diekspor", "परीक्षण CSV निर्यात हुआ");
        add("export.failed", "Could not export CSV", "Gagal mengekspor CSV", "CSV निर्यात नहीं हो सका");
        add("export.unavailable", "No document exporter available", "Ekspor dokumen tidak tersedia", "दस्तावेज़ निर्यात उपलब्ध नहीं");
        add("accessibility.battlefield", "Frontline battlefield", "Medan Frontline", "Frontline युद्धक्षेत्र");
    }

    private static void results() {
        add("result.in_progress", "Attempt in progress.", "Percobaan berlangsung.", "प्रयास जारी है।");
        add("result.campaign_won", "All rivals defeated.", "Semua rival dikalahkan.", "सभी प्रतिद्वंद्वी पराजित।");
        add("result.hold_completed", "Completed in {elapsed}s; held the marked king for {held} / {required}s.", "Selesai dalam {elapsed} dtk; raja dikuasai {held} / {required} dtk.", "{elapsed} सेकंड में पूरा; चिह्नित राजा पर {held} / {required} सेकंड कब्ज़ा।");
        add("result.keep_completed", "Starting king retained for {actual} / {required}s.", "Raja awal dipertahankan {actual} / {required} dtk.", "शुरुआती राजा {actual} / {required} सेकंड सुरक्षित।");
        add("result.budget_completed", "Completed with {used} / {budget} troops deployed; {remaining} remaining.", "Selesai: {used} / {budget} pasukan dikirim; sisa {remaining}.", "पूरा: {used} / {budget} सैनिक भेजे; {remaining} शेष।");
        add("result.budget_exceeded", "Deployment budget exceeded: {used} / {budget} troops.", "Batas pengiriman terlampaui: {used} / {budget} pasukan.", "सैनिक भेजने की सीमा पार: {used} / {budget} सैनिक।");
        add("result.protected_king_lost", "Protected starting king lost. The defence objective failed.", "Raja awal yang dilindungi hilang. Tujuan bertahan gagal.", "सुरक्षित रखना था, पर शुरुआती राजा खो गया। रक्षा लक्ष्य असफल।");
        add("result.player_eliminated", "Your territories and surviving armies were eliminated.", "Semua wilayah dan sisa pasukanmu telah dikalahkan.", "आपके सभी क्षेत्र और बची सेनाएँ हार गईं।");
        add("result.surrendered", "Surrender ended this attempt.", "Percobaan berakhir karena menyerah.", "समर्पण से प्रयास समाप्त हुआ।");
        add("result.legacy_defeat", "This historical attempt ended in defeat; its cause was not recorded.", "Percobaan lama ini kalah; penyebabnya tidak tercatat.", "यह पुराना प्रयास हार गया था; कारण दर्ज नहीं है।");
        add("failure.budget", "Deployment budget exceeded: {used} / {budget} troops.", "Batas pengiriman terlampaui: {used} / {budget} pasukan.", "सैनिक भेजने की सीमा पार: {used} / {budget} सैनिक।");
        add("failure.protected_king", "Protected starting king lost. The defence objective failed.", "Raja awal yang dilindungi hilang. Tujuan bertahan gagal.", "शुरुआती राजा खो गया। रक्षा लक्ष्य असफल।");
        add("failure.eliminated", "Your territories and surviving armies were eliminated.", "Semua wilayah dan sisa pasukanmu telah dikalahkan.", "आपके सभी क्षेत्र और बची सेनाएँ हार गईं।");
        add("failure.legacy", "The cause of this historical defeat was not recorded.", "Penyebab kekalahan lama ini tidak tercatat.", "इस पुरानी हार का कारण दर्ज नहीं है।");
        add("result.objective_complete", "Objective Complete", "Tujuan Tercapai", "लक्ष्य पूरा");
        add("result.objective_failed", "Objective Failed", "Tujuan Gagal", "लक्ष्य असफल");
        add("result.campaign_complete", "Campaign Complete", "Kampanye Selesai", "अभियान पूरा");
        add("result.chapter_secured", "Chapter Secured", "Bab Dituntaskan", "अध्याय पूरा");
        add("result.sector_secured", "Sector Secured", "Sektor Dikuasai", "सेक्टर सुरक्षित");
        add("result.sector_lost", "Sector Lost", "Sektor Hilang", "सेक्टर हारा");
        add("result.regroup", "Regroup. Try a new approach.", "Susun kembali. Coba strategi baru.", "फिर तैयारी करें। नई रणनीति आज़माएँ।");
        add("result.united", "THE FRONTIER IS UNITED", "PERBATASAN TELAH BERSATU", "सीमांत एकजुट है");
        add("result.chapter_unlocked", "{chapter} unlocked", "{chapter} terbuka", "{chapter} खुला");
        add("result.surrender", "RIVAL SURRENDER", "RIVAL MENYERAH", "प्रतिद्वंद्वी समर्पण");
        add("result.score_label", "SCORE", "SKOR", "स्कोर");
        add("result.army_label", "ARMY DEPLOYED", "PASUKAN DIKIRIM", "सेना भेजी गई");
        add("result.time_target", "TIME / 3-STAR TARGET", "WAKTU / TARGET 3 BINTANG", "समय / 3 सितारों का लक्ष्य");
        add("result.legacy_difficulty", "LEGACY / DIFFICULTY UNKNOWN", "LAMA / KESULITAN TAK DIKETAHUI", "पुराना / कठिनाई अज्ञात");
        add("result.time_improved", "TIME IMPROVED", "WAKTU MEMBAIK", "समय बेहतर");
        add("result.no_faster_time", "NO FASTER TIME", "BELUM LEBIH CEPAT", "समय बेहतर नहीं");
        add("result.best_score", "NEW BEST SCORE", "SKOR TERBAIK BARU", "नया सर्वश्रेष्ठ स्कोर");
        add("result.campaign_pb", "{difficulty} / PB {time} / {status}", "{difficulty} / REKOR {time} / {status}", "{difficulty} / सर्वश्रेष्ठ {time} / {status}");
        add("result.home_king_lost", "Your starting king was lost during this attempt.", "Raja awalmu hilang dalam percobaan ini.", "इस प्रयास में आपका शुरुआती राजा खो गया।");
        add("result.intercepted", "{troops} troops canceled in crossing attacks.", "{troops} pasukan gugur saat serangan berpapasan.", "आमने-सामने हमलों में {troops} सैनिक नष्ट।");
        add("result.cap_losses", "{troops} reinforcements were lost at troop caps.", "{troops} bantuan hilang karena batas pasukan.", "सैनिक सीमा पर {troops} सहायता सैनिक खोए।");
        add("result.captures", "{captures} territories captured this attempt.", "{captures} wilayah direbut pada percobaan ini.", "इस प्रयास में {captures} क्षेत्र जीते।");
        add("result.next_sector", "Next Sector", "Sektor Berikutnya", "अगला सेक्टर");
        add("result.all_sectors", "All Sectors", "Semua Sektor", "सभी सेक्टर");
        add("result.try_again", "Try Again", "Coba Lagi", "फिर कोशिश करें");
        add("result.retry_mission", "Retry Mission", "Ulangi Misi", "मिशन फिर करें");
        add("result.replay_sector", "Replay Sector", "Mainkan Lagi Sektor", "सेक्टर फिर खेलें");
        add("record.historical_completed", "Historical completion retained", "Penyelesaian lama tetap tersimpan", "पुराना पूरा किया मिशन सुरक्षित");
        add("record.unplayed", "Not completed", "Belum selesai", "अभी पूरा नहीं");
        add("record.best_elapsed", "{time} best completion", "Penyelesaian terbaik {time}", "सर्वश्रेष्ठ पूर्णता {time}");
        add("record.fewest_troops", "Best: {troops} troops / {time}", "Terbaik: {troops} pasukan / {time}", "सर्वश्रेष्ठ: {troops} सैनिक / {time}");
        add("record.completed", "Completed", "Selesai", "पूरा हुआ");
        add("record.historical", "Historical record", "Rekor lama", "पुराना रिकॉर्ड");
        add("record.improved", "NEW PERSONAL BEST", "REKOR PRIBADI BARU", "नया व्यक्तिगत सर्वश्रेष्ठ");
        add("result.deployed_label", "TROOPS DEPLOYED / LIMIT", "PASUKAN DIKIRIM / BATAS", "सैनिक भेजे / सीमा");
        add("result.budget_remaining", "{remaining} troops remaining", "Sisa {remaining} pasukan", "{remaining} सैनिक शेष");
        add("result.survival_label", "SURVIVAL / REQUIRED", "BERTAHAN / TARGET", "रक्षा समय / आवश्यक");
        add("result.hold_label", "CONTINUOUS HOLD / REQUIRED", "KUASAI TERUS / TARGET", "लगातार कब्ज़ा / आवश्यक");
        add("result.elapsed", "Elapsed {time}", "Waktu {time}", "बीता समय {time}");
        add("result.battle_stats", "{captures} captures / {lost} troops lost", "{captures} rebutan / {lost} pasukan gugur", "{captures} कब्ज़े / {lost} सैनिक खोए");
        add("metric.hold", "Hold {held} / {required}", "Kuasai {held} / {required}", "कब्ज़ा {held} / {required}");
        add("metric.survival", "Survival {actual} / {required}", "Bertahan {actual} / {required}", "रक्षा समय {actual} / {required}");
        add("metric.budget", "Deployed {used} / {budget}", "Dikirim {used} / {budget}", "भेजे {used} / {budget}");
        add("metric.remaining", "Remaining {remaining}", "Sisa {remaining}", "शेष {remaining}");
        add("metric.elapsed", "Elapsed {time}", "Waktu {time}", "बीता समय {time}");
    }

    private static void tutorialAndRules() {
        add("tutorial.title", "How to Play", "Cara Bermain", "कैसे खेलें");
        add("tutorial.claim", "Claim Territory", "Kuasai Wilayah", "क्षेत्र पर कब्ज़ा");
        add("tutorial.capture", "Capture a Tile", "Rebut Petak", "क्षेत्र जीतें");
        add("tutorial.reinforce", "Reinforce and Defend", "Perkuat dan Bertahan", "मज़बूती और रक्षा");
        add("tutorial.boost", "King Boost", "Bonus Raja", "राजा की बढ़त");
        add("tutorial.advance", "Win and Advance", "Menang dan Maju", "जीतें और आगे बढ़ें");
        add("tutorial.neutral", "NEUTRAL", "NETRAL", "तटस्थ");
        add("tutorial.rival", "RIVAL", "RIVAL", "प्रतिद्वंद्वी");
        add("tutorial.your_tiles", "Green territories are yours. They produce troops.", "Wilayah hijau milikmu. Wilayah ini menghasilkan pasukan.", "हरे क्षेत्र आपके हैं। इनमें सैनिक बनते हैं।");
        add("tutorial.king_growth", "King territories grow faster than ordinary ones.", "Petak raja menghasilkan pasukan lebih cepat.", "राजा वाले क्षेत्रों में सैनिक तेज़ी से बनते हैं।");
        add("tutorial.caps", "Troop limits: ordinary tiles 100, king tiles 125.", "Batas pasukan: petak biasa 100, petak raja 125.", "सैनिक सीमा: सामान्य क्षेत्र 100, राजा का क्षेत्र 125।");
        add("tutorial.captured", "CAPTURED", "DIREBUT", "कब्ज़ा हुआ");
        add("tutorial.swipe", "TRY A SWIPE", "COBA GESER", "खींचकर देखें");
        add("tutorial.drag", "Drag from your tile to attack or reinforce another.", "Geser dari petakmu untuk menyerang atau memberi bantuan.", "अपने क्षेत्र से खींचकर हमला या सहायता भेजें।");
        add("tutorial.combat", "Spend one troop per defender. Survivors capture it.", "Satu penyerang melawan satu pembela. Sisanya merebut petak.", "हर रक्षक पर एक सैनिक खर्च होता है। बचे सैनिक कब्ज़ा करते हैं।");
        add("tutorial.crossing", "Crossing enemy troops cancel one-for-one.", "Pasukan musuh yang berpapasan saling gugur satu lawan satu.", "आमने-सामने सैनिक एक के बदले एक नष्ट होते हैं।");
        add("tutorial.amounts", "Tap all three amounts, then swipe to reinforce.", "Coba ketiga jumlah, lalu geser untuk memberi bantuan.", "तीनों मात्राएँ चुनें, फिर सहायता भेजने के लिए खींचें।");
        add("tutorial.send_amount", "{percent}% sends {send}; {left} remain to defend.", "{percent}% mengirim {send}; {left} tersisa untuk bertahan.", "{percent}% से {send} जाते हैं; रक्षा के लिए {left} बचते हैं।");
        add("tutorial.reserves", "25% saves reserves. 100% leaves this tile exposed.", "25% menyisakan cadangan. 100% membuat petak tanpa pembela.", "25% से रिज़र्व बचता है। 100% से क्षेत्र बिना रक्षक रह जाता है।");
        add("tutorial.capture_king", "Capture King", "Rebut Raja", "राजा जीतें");
        add("tutorial.lose_king", "Lose King", "Lepas Raja", "राजा खोएँ");
        add("tutorial.king_practice", "Capture and lose a king in this practice.", "Latih merebut dan kehilangan raja di sini.", "इस अभ्यास में राजा जीतें और खोएँ।");
        add("tutorial.king_bonus", "One held enemy king gives your whole team x1.2 growth.", "Satu raja musuh yang dikuasai memberi pertumbuhan tim x1,2.", "एक दुश्मन राजा पर कब्ज़े से पूरी टीम की वृद्धि x1.2 होती है।");
        add("tutorial.king_loss", "Losing it removes that boost. Full table is in Rules.", "Kehilangannya menghapus bonus. Tabel lengkap ada di Aturan.", "उसे खोने पर बढ़त हटती है। पूरी तालिका नियमों में है।");
        add("tutorial.campaign_goal", "Campaign: eliminate rival tiles and armies.", "Kampanye: kalahkan semua petak dan pasukan rival.", "अभियान: प्रतिद्वंद्वी क्षेत्र और सेनाएँ समाप्त करें।");
        add("tutorial.stars", "Faster wins earn stars. See the target before play.", "Kemenangan cepat memberi bintang. Lihat target sebelum main.", "तेज़ जीत पर सितारे मिलते हैं। खेलने से पहले लक्ष्य देखें।");
        add("tutorial.unlocks", "Campaign wins unlock sectors; missions stay separate.", "Menang kampanye membuka sektor; misi tetap terpisah.", "अभियान की जीत से सेक्टर खुलते हैं; मिशन अलग हैं।");
        add("tutorial.previous", "Previous Step", "Langkah Sebelumnya", "पिछला चरण");
        add("tutorial.try_kings", "Try capture and loss above", "Coba rebut dan lepas raja", "ऊपर राजा जीतें और खोएँ");
        add("tutorial.try_amounts", "Try all three amounts", "Coba ketiga jumlah", "तीनों मात्राएँ आज़माएँ");
        add("tutorial.swipe_continue", "Swipe above to continue", "Geser di atas untuk lanjut", "जारी रखने के लिए ऊपर खींचें");
        add("tutorial.skip", "Skip Tutorial", "Lewati Tutorial", "अभ्यास छोड़ें");
        add("feedback.home_king", "Starting king lost / enemy-king bonus unaffected.", "Raja awal hilang / bonus raja musuh tetap.", "शुरुआती राजा खोया / दुश्मन राजा की बढ़त कायम।");
        add("feedback.king_gain", "Enemy king secured / team growth updated.", "Raja musuh direbut / pertumbuhan tim berubah.", "दुश्मन राजा जीता / टीम की वृद्धि बदली।");
        add("feedback.king_loss", "Held enemy king lost / team growth reduced.", "Raja musuh lepas / pertumbuhan tim turun.", "दुश्मन राजा खोया / टीम की वृद्धि घटी।");
        add("feedback.cap", "{troops} reinforcement troops lost to the cap.", "{troops} bantuan hilang karena batas.", "सीमा पर {troops} सहायता सैनिक खोए।");
        add("feedback.intercept", "{troops} troops canceled in interception.", "{troops} pasukan gugur saat berpapasan.", "रास्ते में {troops} सैनिक नष्ट।");
        add("feedback.capture", "Capture complete / {troops} troops survived.", "Petak direbut / {troops} pasukan selamat.", "कब्ज़ा पूरा / {troops} सैनिक बचे।");
        add("objective.hold_hud", "HOLD MARKED KING  {actual} / {required}", "KUASAI RAJA  {actual} / {required}", "चिह्नित राजा रखें  {actual} / {required}");
        add("objective.keep_hud", "KEEP STARTING KING  {actual} / {required}", "JAGA RAJA AWAL  {actual} / {required}", "शुरुआती राजा रखें  {actual} / {required}");
        add("objective.budget_hud", "DEPLOYMENT BUDGET  {used} / {budget}", "BATAS KIRIM  {used} / {budget}", "भेजने की सीमा  {used} / {budget}");
        add("objective.first", "Capture neutral space. Keep troops at home.", "Rebut petak netral. Sisakan pasukan di rumah.", "तटस्थ क्षेत्र जीतें। अपने यहाँ सैनिक बचाएँ।");
        add("objective.second", "Reinforce your front; 100% leaves no defenders.", "Perkuat garis depan; 100% tanpa pembela.", "मोर्चे को सहायता दें; 100% से कोई रक्षक नहीं बचता।");
        add("objective.third", "Hold enemy kings to boost your whole team.", "Kuasai raja musuh untuk bonus seluruh tim.", "टीम की बढ़त के लिए दुश्मन राजा रखें।");
        add("objective.eliminate", "Eliminate every rival territory and surviving army.", "Kalahkan semua petak dan sisa pasukan rival.", "हर प्रतिद्वंद्वी क्षेत्र और बची सेना हराएँ।");
        add("objective.budget", "Win; deploy at most {budget} troops", "Menang; kirim paling banyak {budget} pasukan", "जीतें; अधिकतम {budget} सैनिक भेजें");
        add("objective.hold", "Hold marked king continuously for {seconds}s", "Kuasai raja bertanda terus selama {seconds} dtk", "चिह्नित राजा पर लगातार {seconds} सेकंड कब्ज़ा रखें");
        add("objective.keep", "Retain starting king for {seconds}s; never lose it", "Jaga raja awal {seconds} dtk; jangan sampai hilang", "शुरुआती राजा {seconds} सेकंड रखें; कभी न खोएँ");
        add("brief.targets", "3 STARS <= {three} / 2 STARS <= {two}", "3 BINTANG <= {three} / 2 BINTANG <= {two}", "3 सितारे <= {three} / 2 सितारे <= {two}");
        add("brief.campaign", "Eliminate rivals. Any tile can be targeted.", "Kalahkan rival. Petak mana pun bisa ditargetkan.", "प्रतिद्वंद्वी हराएँ। कोई भी क्षेत्र निशाना बन सकता है।");
        add("brief.gaps", "Gaps do not block troop travel.", "Celah tidak menghalangi perjalanan pasukan.", "खाली जगह सैनिकों का रास्ता नहीं रोकती।");
        add("brief.hold_reset", "Losing the marked king resets the hold timer.", "Kehilangan raja bertanda mengulang waktu penguasaan.", "चिह्नित राजा खोने पर कब्ज़े का समय फिर शुरू होता है।");
        add("brief.king_failure", "Any loss of your starting king fails this mission.", "Kehilangan raja awal langsung menggagalkan misi.", "शुरुआती राजा खोते ही यह मिशन असफल होता है।");
        add("brief.budget_failure", "Exceeding the troop budget fails this mission.", "Melebihi batas pasukan menggagalkan misi.", "सैनिक सीमा पार करते ही यह मिशन असफल होता है।");
        add("brief.ai", "Pressure targets crowns; Guardians keep larger reserves.", "Penekan memburu raja; Penjaga menyimpan lebih banyak cadangan.", "दबाव शैली राजा पर हमला करती है; रक्षक अधिक रिज़र्व रखते हैं।");
        add("brief.defense_pressure", "Rivals focus on your king in this defence mission.", "Rival mengincar rajamu dalam misi bertahan ini.", "इस रक्षा मिशन में प्रतिद्वंद्वी आपके राजा को निशाना बनाते हैं।");
        add("ai.pressure", "Pressure", "Penekan", "दबाव");
        add("ai.guardian", "Guardian", "Penjaga", "रक्षक");
        add("mastery.title", "Mastery", "Penguasaan", "महारत");
        add("mastery.crown", "Crown Keeper", "Penjaga Mahkota", "ताज का रक्षक");
        add("mastery.chapter", "Normal Chapter", "Bab Normal", "सामान्य अध्याय");
        add("mastery.objective", "Objective Specialist", "Ahli Tujuan", "लक्ष्य विशेषज्ञ");
        add("mastery.crown_detail", "Win a new attempt without losing your starting king.", "Menangkan percobaan baru tanpa kehilangan raja awal.", "शुरुआती राजा खोए बिना नया प्रयास जीतें।");
        add("mastery.chapter_detail", "Win all six Border Sparks sectors on Normal.", "Menangkan keenam sektor Percikan Batas pada Normal.", "सामान्य कठिनाई में सीमा की चिंगारी के छहों सेक्टर जीतें।");
        add("mastery.objective_detail", "Win all three objective types.", "Menangkan ketiga jenis tujuan.", "तीनों प्रकार के लक्ष्य जीतें।");
        add("mastery.earned", "Earned", "Diraih", "प्राप्त");
        add("mastery.themes", "COSMETIC THEMES", "TEMA TAMPILAN", "सजावटी थीम");
        add("theme.classic", "Classic", "Klasik", "क्लासिक");
        add("theme.signal", "Signal", "Sinyal", "संकेत");
        add("theme.blueprint", "Blueprint", "Cetak Biru", "खाका");
        add("theme.locked", "{theme} / Locked", "{theme} / Terkunci", "{theme} / बंद");
        add("rules.menu", "Rules", "Aturan", "नियम");
        add("rules.title", "Field Manual", "Panduan Lapangan", "मैदानी मार्गदर्शिका");
        add("rules.targeting", "Any tile can be targeted. Gaps do not block travel.", "Semua petak bisa ditargetkan. Celah tidak menghalangi jalan.", "कोई भी क्षेत्र निशाना बन सकता है। खाली जगह रास्ता नहीं रोकती।");
        add("rules.caps", "Reinforcements beyond 100 / 125 are discarded.", "Bantuan di atas batas 100 / 125 dibuang.", "100 / 125 से अधिक सहायता सैनिक हटा दिए जाते हैं।");
        add("rules.boost_three", "Enemy kings held: 1 x1.2 / 2 x1.44 / 3 x1.728.", "Raja musuh dikuasai: 1 x1,2 / 2 x1,44 / 3 x1,728.", "दुश्मन राजा: 1 x1.2 / 2 x1.44 / 3 x1.728।");
        add("rules.boost_four", "Four enemy kings: x3. Each additional king: x1.2.", "Empat raja musuh: x3. Tiap raja tambahan: x1,2.", "चार दुश्मन राजा: x3। हर अतिरिक्त राजा: x1.2।");
        add("rules.home_king", "Your original king never counts toward that boost.", "Raja asalmu tidak dihitung dalam bonus itu.", "आपका मूल राजा इस बढ़त में नहीं गिना जाता।");
        add("rules.interception", "Hostile armies meeting in flight cancel one-for-one.", "Pasukan lawan yang berpapasan gugur satu lawan satu.", "रास्ते में मिली विरोधी सेनाएँ एक के बदले एक नष्ट होती हैं।");
        add("rules.surrender", "Surrender: >90% control for ten active seconds,", "Menyerah: kuasai >90% selama sepuluh detik aktif,", "समर्पण: दस सक्रिय सेकंड तक >90% नियंत्रण,");
        add("rules.surrender_safe", "and rivals cannot recapture any exposed tile.", "dan rival tak bisa merebut kembali petak terbuka.", "और प्रतिद्वंद्वी कोई असुरक्षित क्षेत्र वापस न जीत सकें।");
        add("rules.stars", "3 stars: at/below par. 2 stars: at/below 1.6x par.", "3 bintang: <= target. 2 bintang: <= 1,6x target.", "3 सितारे: लक्ष्य तक। 2 सितारे: लक्ष्य के 1.6x तक।");
        add("rules.score", "Score: 1000 + 50 per capture + time bonus", "Skor: 1000 + 50 per rebutan + bonus waktu", "स्कोर: 1000 + हर कब्ज़े पर 50 + समय बोनस");
        add("rules.score_bonus", "(up to 1200, minus 6 per second) + 250 per difficulty.", "(maks. 1200, kurang 6 per detik) + 250 per tingkat kesulitan.", "(अधिकतम 1200, हर सेकंड घटें 6) + हर कठिनाई स्तर पर 250।");
        add("rules.legacy", "Legacy records have unknown historical difficulty.", "Kesulitan pada rekor lama tidak diketahui.", "पुराने रिकॉर्ड की ऐतिहासिक कठिनाई अज्ञात है।");
        add("record.historical_suffix", "{record} / Historical {score}", "{record} / Riwayat {score}", "{record} / ऐतिहासिक {score}");
        add("record.difficulty_score", "{difficulty} {score} / {time}", "{difficulty} {score} / {time}", "{difficulty} {score} / {time}");
        add("record.difficulty_best", "{difficulty} / Best {time}", "{difficulty} / Terbaik {time}", "{difficulty} / सर्वश्रेष्ठ {time}");
        add("record.historical_word", "Historical", "Riwayat", "ऐतिहासिक");
        add("record.legacy_word", "Legacy", "Lama", "पुराना");
    }

    private static void content() {
        for (String[] row : LocalizationContent.ROWS) add(row[0], row[1], row[2], row[3]);
    }

    private static void laterModes() {
        add("menu.main", "Main Menu", "Menu Utama", "मुख्य मेनू");
        add("run.title", "Run Mode", "Mode Rangkaian", "पाँच लड़ाइयाँ");
        add("run.empty", "No run in progress.", "Tidak ada rangkaian aktif.", "कोई लड़ाई शृंखला जारी नहीं।");
        add("run.recovery", "Saved battle unavailable. Abandon this run to start again.", "Pertempuran tersimpan tidak tersedia. Tinggalkan rangkaian ini untuk memulai lagi.", "सहेजी हुई लड़ाई उपलब्ध नहीं। फिर शुरू करने के लिए यह शृंखला छोड़ें।");
        add("run.restart_confirm", "Restarting ends this battle as a defeat. Only the one run retry can replay it.", "Memulai ulang mengakhiri pertempuran ini sebagai kekalahan. Hanya satu kesempatan ulang rangkaian yang dapat memainkannya lagi.", "फिर शुरू करने पर यह लड़ाई हार मानी जाएगी। इसे दोबारा खेलने के लिए शृंखला का एकमात्र पुनः प्रयास इस्तेमाल होगा।");
        add("run.new", "New Run", "Rangkaian Baru", "नई शृंखला");
        add("run.continue", "Continue Run", "Lanjutkan Rangkaian", "शृंखला जारी रखें");
        add("run.progress", "{cleared} / {total} battles cleared", "{cleared} / {total} pertempuran selesai", "{cleared} / {total} लड़ाइयाँ जीतीं");
        add("run.retries", "Retries: {remaining}", "Ulang: {remaining}", "पुनः प्रयास: {remaining}");
        add("run.node_details", "{difficulty} / {opponents} rivals", "{difficulty} / {opponents} rival", "{difficulty} / {opponents} प्रतिद्वंद्वी");
        add("run.use_retry", "Use Retry", "Gunakan Ulang", "पुनः प्रयास करें");
        add("run.start_battle", "Start Battle", "Mulai Pertempuran", "लड़ाई शुरू करें");
        add("run.end", "End Run", "Akhiri Rangkaian", "शृंखला समाप्त करें");
        add("run.abandon", "Abandon Run", "Tinggalkan Rangkaian", "शृंखला छोड़ें");
        add("run.council", "Council", "Dewan", "परिषद");
        add("run.choose", "Choose", "Pilih", "चुनें");
        add("run.completed", "Run Complete", "Rangkaian Selesai", "शृंखला पूरी");
        add("run.defeated", "Run Defeated", "Rangkaian Kalah", "शृंखला हारी");
        add("run.abandoned", "Run Abandoned", "Rangkaian Ditinggalkan", "शृंखला छोड़ी गई");
        add("run.retries_used", "Retries used: {used}", "Ulang terpakai: {used}", "पुनः प्रयास किए: {used}");
        add("run.elapsed", "Elapsed {time}", "Waktu {time}", "बीता समय {time}");
        add("run.stats", "{captures} captures / {lost} troops lost", "{captures} rebutan / {lost} pasukan gugur", "{captures} कब्ज़े / {lost} सैनिक खोए");
        add("run.retry_confirm", "Use the one retry? The same battle and perks are kept.", "Pakai satu kesempatan ulang? Pertempuran dan bonus tetap sama.", "एकमात्र पुनः प्रयास करें? वही लड़ाई और लाभ बने रहेंगे।");
        add("run.retry_spent", "Retry used. No retries remain.", "Kesempatan ulang terpakai. Tidak ada yang tersisa.", "पुनः प्रयास इस्तेमाल हुआ। अब कोई नहीं बचा।");
        add("run.retry_unavailable", "No retries remain. This run has ended.", "Tidak ada kesempatan ulang. Rangkaian berakhir.", "कोई पुनः प्रयास नहीं बचा। शृंखला समाप्त।");
        add("run.abandon_confirm", "Abandon this run? Its unfinished battle and council will be discarded.", "Tinggalkan rangkaian? Pertempuran dan pilihan dewan yang belum selesai dibuang.", "शृंखला छोड़ें? अधूरी लड़ाई और परिषद की पसंद हट जाएँगी।");
        add("run.replace_confirm", "Starting another mode will abandon this run.", "Memulai mode lain akan meninggalkan rangkaian ini.", "दूसरा मोड शुरू करने पर यह शृंखला छूट जाएगी।");
        add("run.council_pending", "Choose one perk before the next battle.", "Pilih satu bonus sebelum pertempuran berikutnya.", "अगली लड़ाई से पहले एक लाभ चुनें।");
        add("run.council_choices", "{choices} perks available", "{choices} bonus tersedia", "{choices} लाभ उपलब्ध");
        add("run.perks", "Chosen Perks", "Bonus Pilihan", "चुने लाभ");
        add("run.no_perks", "No perks chosen yet.", "Belum ada bonus dipilih.", "अभी कोई लाभ नहीं चुना।");
        add("run.rules", "Classic rules / perks apply only to this run", "Aturan Klasik / bonus hanya untuk rangkaian ini", "क्लासिक नियम / लाभ केवल इस शृंखला में");
        add("perk.1.name", "Supply Lines", "Jalur Pasokan", "आपूर्ति मार्ग");
        add("perk.1.effect", "Ordinary tile production +8% in this run.", "Produksi petak biasa +8% di rangkaian ini.", "इस शृंखला में सामान्य क्षेत्र उत्पादन +8%।");
        add("perk.2.name", "Royal Industry", "Industri Kerajaan", "शाही उत्पादन");
        add("perk.2.effect", "King tile production +15% in this run.", "Produksi petak raja +15% di rangkaian ini.", "इस शृंखला में राजा क्षेत्र उत्पादन +15%।");
        add("perk.4.name", "Swift Convoys", "Konvoi Cepat", "तेज़ काफ़िले");
        add("perk.4.effect", "Convoy speed +12% in this run.", "Kecepatan konvoi +12% di rangkaian ini.", "इस शृंखला में काफ़िले की गति +12%।");
        add("perk.8.name", "Royal Reserves", "Cadangan Raja", "शाही रिज़र्व");
        add("perk.8.effect", "King tile troop cap +15 in this run.", "Batas pasukan petak raja +15 di rangkaian ini.", "इस शृंखला में राजा क्षेत्र सैनिक सीमा +15।");
        add("perk.16.name", "Crown Vanguard", "Pelopor Mahkota", "शाही अग्रदल");
        add("perk.16.effect", "Starting king troops +10 in this run.", "Pasukan awal di petak raja +10 di rangkaian ini.", "इस शृंखला में राजा के शुरुआती सैनिक +10।");
        add("logistics.title", "Logistics", "Logistik", "रसद");
        add("logistics.experimental", "Experimental", "Eksperimental", "प्रयोगात्मक");
        add("logistics.new", "New Logistics Battle", "Pertempuran Logistik Baru", "नई रसद लड़ाई");
        add("logistics.continue", "Continue Logistics", "Lanjutkan Logistik", "रसद जारी रखें");
        add("logistics.choose_map", "Choose Map", "Pilih Peta", "नक्शा चुनें");
        add("logistics.map.0", "Twin Causeways", "Dua Jalan Lintas", "जुड़वाँ मार्ग");
        add("logistics.map.1", "Broken Junction", "Simpang Terputus", "टूटा चौराहा");
        add("logistics.map.2", "Crown Circuit", "Lintasan Mahkota", "ताज का चक्र");
        add("logistics.map.0.description", "Connect your footholds around the central gap.", "Hubungkan pijakanmu mengitari celah tengah.", "बीच की खाली जगह के चारों ओर अपने ठिकाने जोड़ें।");
        add("logistics.map.1.description", "Secure the upper and lower crossings between three fronts.", "Amankan lintasan atas dan bawah di antara tiga garis depan.", "तीन मोर्चों के बीच ऊपरी और निचले रास्ते सुरक्षित करें।");
        add("logistics.map.2.description", "Four kings contest the circuit around a blocked centre.", "Empat raja memperebutkan lintasan mengitari pusat yang terhalang.", "बंद केंद्र के चारों ओर चार राजा मार्ग के लिए लड़ते हैं।");
        add("logistics.map.0.level", "Logistics: Twin Causeways", "Logistik: Dua Jalan Lintas", "रसद: जुड़वाँ मार्ग");
        add("logistics.map.1.level", "Logistics: Broken Junction", "Logistik: Simpang Terputus", "रसद: टूटा चौराहा");
        add("logistics.map.2.level", "Logistics: Crown Circuit", "Logistik: Lintasan Mahkota", "रसद: ताज का चक्र");
        add("logistics.rules", "Friendly connected tiles carry routes. Holes block travel.", "Rute melalui petak kawan yang terhubung. Celah menghalangi jalan.", "रास्ते जुड़े मित्र क्षेत्रों से गुज़रते हैं। खाली जगह राह रोकती है।");
        add("logistics.reinforce_rule", "Reinforcements travel through friendly connected tiles.", "Bantuan melalui petak kawan yang terhubung.", "सहायता जुड़े मित्र क्षेत्रों से गुज़रती है।");
        add("logistics.attack_rule", "Route through friendly tiles, then attack an adjacent target.", "Lewati petak kawan, lalu serang target di sebelahnya.", "मित्र क्षेत्रों से गुज़रें, फिर पड़ोसी लक्ष्य पर हमला करें।");
        add("logistics.route", "Route: {route}", "Rute: {route}", "रास्ता: {route}");
        add("logistics.eta", "ETA {seconds}s", "Tiba {seconds} dtk", "आगमन {seconds} सेकंड");
        add("logistics.route_preview", "{hops} hops / ETA {seconds}s", "{hops} langkah / tiba {seconds} dtk", "{hops} कदम / आगमन {seconds} सेकंड");
        add("logistics.route_stats", "{troops} troops / {hops} hops / ETA {seconds}s", "{troops} pasukan / {hops} langkah / tiba {seconds} dtk", "{troops} सैनिक / {hops} कदम / आगमन {seconds} सेकंड");
        add("logistics.legal_target", "Reachable target", "Target terjangkau", "पहुँचने योग्य लक्ष्य");
        add("logistics.refused", "No legal route. No troops sent.", "Tidak ada rute sah. Pasukan tidak dikirim.", "वैध रास्ता नहीं। कोई सैनिक नहीं भेजा।");
        add("logistics.refusal.no_route", "No connected friendly route to this target.", "Tidak ada rute kawan yang terhubung ke target.", "लक्ष्य तक जुड़ा मित्र रास्ता नहीं।");
        add("logistics.refusal.hole", "A gap blocks this route. No troops sent.", "Celah menghalangi rute. Pasukan tidak dikirim.", "खाली जगह रास्ता रोकती है। सैनिक नहीं भेजे।");
        add("logistics.refusal.source", "Select one of your tiles first.", "Pilih petak milikmu terlebih dahulu.", "पहले अपना क्षेत्र चुनें।");
        add("logistics.refusal.same_tile", "Choose a different target tile.", "Pilih petak target yang berbeda.", "अलग लक्ष्य क्षेत्र चुनें।");
        add("logistics.refusal.no_troops", "No troops available to send.", "Tidak ada pasukan untuk dikirim.", "भेजने के लिए सैनिक नहीं।");
        add("logistics.refusal.convoy_limit", "Too many convoys in flight. Try again shortly.", "Terlalu banyak konvoi berjalan. Coba sebentar lagi.", "बहुत से काफ़िले रास्ते में हैं। थोड़ी देर में कोशिश करें।");
        add("logistics.route_interrupted", "Route interrupted at tile {tile}; combat resolved there.", "Rute terputus di petak {tile}; pertempuran terjadi di sana.", "क्षेत्र {tile} पर रास्ता टूटा; वहीं लड़ाई हुई।");
        add("logistics.transit_rule", "If a transit tile changes owner, the convoy fights and stops there.", "Jika petak transit berganti pemilik, konvoi bertempur dan berhenti di sana.", "रास्ते के क्षेत्र का मालिक बदलने पर काफ़िला वहीं लड़ता और रुकता है।");
        add("logistics.no_attrition", "No distance attrition.", "Tidak ada kehilangan karena jarak.", "दूरी से सैनिक नहीं घटते।");
        add("logistics.separate_records", "Experimental records are separate from Classic.", "Rekor eksperimen terpisah dari Klasik.", "प्रयोगात्मक रिकॉर्ड क्लासिक से अलग हैं।");
        add("logistics.result_won", "Logistics Battle Won", "Pertempuran Logistik Menang", "रसद लड़ाई जीती");
        add("logistics.result_lost", "Logistics Battle Lost", "Pertempuran Logistik Kalah", "रसद लड़ाई हारी");
        add("logistics.retry", "Retry Map", "Ulangi Peta", "नक्शा फिर खेलें");
    }
}
