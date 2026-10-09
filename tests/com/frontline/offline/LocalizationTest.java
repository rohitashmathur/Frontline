package com.frontline.offline;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Catalog/adapter checks, not a substitute for native shaping or fluent-speaker review. */
public final class LocalizationTest {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z][a-z_]*)(?::(02|2f))?\\}");
    private static int checks;

    private LocalizationTest() {}

    public static int run() throws Exception {
        checks = 0;
        catalogs();
        contracts();
        contentInventory();
        literalAndTemplateCoverage();
        dynamicInventory();
        numbersAndIsolation();
        resultArguments();
        compactWidths();
        sourceKeyInventory();
        sourceLiteralInventory();
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " localization checks; "
            + Localization.keys().size() + " complete keys in each of en/id/hi.");
    }

    private static void catalogs() {
        check(Localization.LANGUAGES.equals(Arrays.asList("en", "id", "hi")), "Exactly three supported locales");
        check(Localization.keys().size() >= 400, "Complete offline catalog, not a partial draft");
        Map<String, String> english = Localization.catalog("en");
        for (String language : Localization.LANGUAGES) {
            Map<String, String> catalog = Localization.catalog(language);
            check(catalog.keySet().equals(english.keySet()), "All keys present in " + language);
            for (String key : english.keySet()) {
                String value = catalog.get(key);
                check(value != null && !value.trim().isEmpty(), "Nonempty " + language + ":" + key);
                check(signature(value).equals(signature(english.get(key))), "Same field counts/types " + language + ":" + key);
                Matcher fields = TOKEN.matcher(value);
                StringBuffer stripped = new StringBuffer();
                while (fields.find()) fields.appendReplacement(stripped, "");
                fields.appendTail(stripped);
                check(stripped.indexOf("{") < 0 && stripped.indexOf("}") < 0, "No malformed field " + language + ":" + key);
                Object[] args = samples(key, language);
                String formatted = Localization.text(language, key, args);
                check(!formatted.contains("{") && !formatted.contains("}"), "All fields formatted " + language + ":" + key);
            }
        }
        check("English".equals(Localization.nativeName("en")), "English native label");
        check("Bahasa Indonesia".equals(Localization.nativeName("id")), "Indonesian native label");
        check("हिन्दी".equals(Localization.nativeName("hi")), "Hindi native label");
        for (String language : Localization.LANGUAGES) {
            check("FRONTLINE".equals(Localization.text(language, "brand")), "Brand unchanged " + language);
            for (String selector : Localization.LANGUAGES)
                check(Localization.nativeName(selector).equals(Localization.text(language, "language." + selector)),
                    "Recoverable native selector " + language + ":" + selector);
        }
        expectUnsupported(() -> Localization.catalog("en").put("extra", "Extra"), "Read-only catalogs");
        expectUnsupported(() -> Localization.keys().remove("brand"), "Read-only key inventory");
        expectUnsupported(() -> Localization.fields("home.sector").add("extra"), "Read-only field contract");
    }

    private static Map<String, Integer> signature(String template) {
        Map<String, Integer> result = new HashMap<>();
        Matcher matcher = TOKEN.matcher(template);
        while (matcher.find()) {
            String token = matcher.group(1) + ":" + String.valueOf(matcher.group(2));
            result.put(token, result.containsKey(token) ? result.get(token) + 1 : 1);
        }
        return result;
    }

    private static void contracts() {
        fields("home.sector", "sector", "name");
        fields("budget.warning", "send", "remaining");
        fields("budget.warning_short", "send", "remaining");
        fields("budget.hud", "used", "budget", "remaining");
        fields("deploy.preview", "send", "left");
        fields("result.hold_completed", "elapsed", "held", "required");
        fields("result.keep_completed", "actual", "required");
        fields("result.budget_completed", "used", "budget", "remaining");
        fields("result.budget_exceeded", "used", "budget");
        fields("record.fewest_troops", "troops", "time");
        fields("result.battle_stats", "captures", "lost");
        fields("run.progress", "cleared", "total");
        fields("run.node_details", "difficulty", "opponents");
        fields("logistics.route_stats", "troops", "hops", "seconds");
        check("Sector 4 / First Contact".equals(Localization.text("en", "home.sector", 4, "First Contact")), "Home key English contract");
        check("BUDGET 125 / 120 / REMAINING 0".equals(Localization.text("en", "budget.hud", 125, 120, 0)), "ASCII budget separators");
        check("Best: 100 troops / 02:10".equals(Localization.text("en", "record.fewest_troops", 100, "02:10")), "Budget record arity");
        check("Not completed".equals(Localization.text("en", "record.unplayed")), "Uncompleted record copy");
        check("Sending 125 exceeds the remaining 120 troops.".equals(Localization.text("en", "budget.warning", 125, 120)), "Whole warning sentence");
        expectIllegal(() -> Localization.text("id", "budget.warning", 125), "Reject missing arguments");
        expectIllegal(() -> Localization.text("hi", "result.protected_king_lost", 1), "Reject extra arguments");
        check("missing.key".equals(Localization.text("hi", "missing.key")), "Unknown key remains diagnosable");
        check("".equals(Localization.text("hi", null)), "Null key safe");
        check("".equals(Localization.translate("hi", null)), "Null source safe");
    }

    private static void fields(String key, String... expected) {
        check(Localization.keys().contains(key), "Required key " + key);
        check(Localization.fields(key).equals(Arrays.asList(expected)), "Ordered named fields " + key);
    }

    private static Object[] samples(String key, String language) {
        List<String> names = Localization.fields(key);
        Object[] values = new Object[names.size()];
        for (int i = 0; i < values.length; i++) {
            String name = names.get(i);
            if (name.equals("time") || name.equals("three") || name.equals("two")) values[i] = "02:15";
            else if (name.equals("date")) values[i] = "2026-10-09";
            else if (name.equals("countdown")) values[i] = "23:59:59";
            else if (name.equals("route")) values[i] = "1 > 2 > 3";
            else if (name.equals("boost")) values[i] = new BigDecimal("1.44");
            else if (name.equals("difficulty")) values[i] = Localization.translate(language, "Normal");
            else if (name.equals("chapter") && Localization.catalog("en").get(key).contains("{chapter:02}")) values[i] = 2;
            else if (name.equals("name") || name.equals("chapter")) values[i] = "Name";
            else if (name.equals("record")) values[i] = Localization.translate(language, "No Normal record");
            else if (name.equals("status")) values[i] = Localization.translate(language, "TIME IMPROVED");
            else if (name.equals("theme")) values[i] = Localization.translate(language, "Signal");
            else if (name.equals("attempt")) values[i] = Localization.translate(language, "Sector 3");
            else values[i] = 12;
        }
        return values;
    }

    private static void contentInventory() {
        for (GameModel.Level level : GameModel.LEVELS) literal(level.name);
        check(GameModel.LEVELS.length == 60, "All 60 sector titles inventoried");
        for (Campaign.Chapter chapter : Campaign.CHAPTERS) {
            literal(chapter.name); literal(chapter.firstLine); literal(chapter.secondLine);
        }
        check(Campaign.CHAPTERS.length == 10, "All ten chapters and twenty story lines inventoried");
        for (String name : Campaign.FACTIONS) literal(name);
        for (String name : Campaign.SHORT_NAMES) literal(name);
        for (String name : Campaign.RULERS) literal(name);
        for (Challenge challenge : Challenge.PRESETS) {
            literal(challenge.name);
            for (String language : new String[] {"id", "hi"})
                check(!challenge.objective().equals(Localization.translate(language, challenge.objective())),
                    "Dynamic challenge objective " + challenge.name + ":" + language);
        }
        for (Logistics preset : Logistics.PRESETS) {
            literal(preset.name); literal(preset.description); literal(preset.level.name);
        }
        int[] perks = {1, 2, 4, 8, 16};
        String[] bonuses = {"+8%", "+15%", "+12%", "+15", "+10"};
        for (int i = 0; i < perks.length; i++) for (String language : Localization.LANGUAGES) {
            String effect = Localization.text(language, "perk." + perks[i] + ".effect");
            check(effect.contains(bonuses[i]), "Exact nonstacking effect " + perks[i] + ":" + language);
            check(Localization.fields("perk." + perks[i] + ".effect").isEmpty(), "Frozen perk values, no guessed effect");
        }
    }

    private static void literal(String english) {
        String key = null;
        for (Map.Entry<String, String> entry : Localization.catalog("en").entrySet()) {
            if ((entry.getValue().equals(english) || entry.getValue().toUpperCase(Locale.ROOT).equals(english))
                && Localization.fields(entry.getKey()).isEmpty()) {
                key = entry.getKey(); break;
            }
        }
        check(key != null, "Cataloged literal: " + english);
        boolean uppercase = !Localization.catalog("en").get(key).equals(english);
        for (String language : Localization.LANGUAGES) {
            String expected = Localization.text(language, key);
            if (uppercase) expected = expected.toUpperCase(locale(language));
            check(expected.equals(Localization.translate(language, english)),
                "Literal auto-map " + language + ":" + english);
        }
    }

    private static void literalAndTemplateCoverage() {
        Set<String> seen = new HashSet<>();
        for (String key : Localization.keys()) {
            String template = Localization.catalog("en").get(key);
            if (!seen.add(template)) continue;
            if (Localization.fields(key).isEmpty()) { literal(template); continue; }
            Matcher matcher = TOKEN.matcher(template);
            String prose = matcher.replaceAll("");
            long letters = prose.chars().filter(Character::isLetter).count();
            // All historical sentence patterns with real prose must translate, not just fall back.
            if (letters == 0) continue;
            String english = Localization.text("en", key, samples(key, "en"));
            for (String language : new String[] {"id", "hi"}) {
                String expected = Localization.text(language, key, samples(key, language));
                String actual = Localization.translate(language, english);
                check(expected.equals(actual), "Template parser " + language + ":" + key + " expected [" + expected + "] got [" + actual + "]");
                check(actual.equals(Localization.legacy(language, english)), "translate alias " + language + ":" + key);
            }
        }
    }

    private static void dynamicInventory() {
        String[][] cases = {
            {"BOOST x1.44", "hud.boost"}, {"3 KINGS", "hud.kings"},
            {"TILE 4 / MAX", "hud.tile_max"}, {"SEND 32 / LEFT 0 !", "deploy.preview_warning"},
            {"3 STARS  01:10", "hud.star_target"},
            {"12 / 60 CLEARED", "home.cleared"}, {"BEST TOTAL  12000", "home.total"},
            {"CHAPTER 02 / 10", "campaign.chapter"}, {"Locked - clear Sector 02", "campaign.locked"},
            {"No Hard record", "record.no_difficulty"},
            {"PERSONAL BEST  Unplayed", "record.personal_best"},
            {"LEGACY  1200 score / difficulty unknown", "record.legacy_score"},
            {"25% sends 8; 24 remain to defend.", "tutorial.send_amount"},
            {"Border Sparks unlocked", "result.chapter_unlocked"},
            {"45 reinforcements were lost at troop caps.", "result.cap_losses"},
            {"3 troops canceled in crossing attacks.", "result.intercepted"},
            {"5 territories captured this attempt.", "result.captures"},
            {"25 reinforcement troops lost to the cap.", "feedback.cap"},
            {"4 troops canceled in interception.", "feedback.intercept"},
            {"Capture complete / 12 troops survived.", "feedback.capture"},
            {"HOLD MARKED KING  00:12 / 00:20", "objective.hold_hud"},
            {"KEEP STARTING KING  00:12 / 00:45", "objective.keep_hud"},
            {"DEPLOYMENT BUDGET  100 / 120", "objective.budget_hud"},
            {"Win; deploy at most 120 troops", "objective.budget"},
            {"Hold marked king continuously for 20s", "objective.hold"},
            {"Retain starting king for 45s; never lose it", "objective.keep"},
            {"3 STARS <= 01:10 / 2 STARS <= 01:52", "brief.targets"},
            {"Resets 00:00 UTC / in 23:59:59", "daily.reset"},
            {"Saved daily: 2026-10-09", "daily.saved"},
            {"Signal / Locked", "theme.locked"}, {"All 60 sectors unlocked", "unlock.success"},
            {"Deployment budget exceeded: 125 / 120 troops.", "result.budget_exceeded"},
            {"Best: 120 troops / 02:15", "record.fewest_troops"},
            {"4 captures / 22 troops lost", "result.battle_stats"}
        };
        for (String[] pair : cases) for (String language : new String[] {"id", "hi"}) {
            String translated = Localization.translate(language, pair[0]);
            check(!translated.equals(pair[0]), "Historical dynamic sentence translated " + language + ":" + pair[0]);
            check(!translated.contains("{") && !translated.contains("}"), "Dynamic sentence has no unresolved fields");
        }
        for (String language : new String[] {"id", "hi"}) {
            String normal = Localization.translate(language, "Normal");
            String score = NumberFormat.getNumberInstance(locale(language)).format(1500);
            check((normal + " " + score + " / 02:15").equals(Localization.translate(language, "Normal 1500 / 02:15")), "Difficulty record composite");
            String record = "Cleared / Normal 1500 / 02:15 / Historical 700 / Legacy 900";
            String expected = Localization.text(language, "campaign.cleared", Localization.text(language, "record.legacy_suffix",
                Localization.text(language, "record.historical_suffix", normal + " " + score + " / 02:15", 700), 900));
            check(expected.equals(Localization.translate(language, record)), "Nested current/historical/legacy record " + language);
            check((Localization.translate(language, "YOU") + " " + Localization.translate(language, "OUT")).equals(Localization.translate(language, "YOU OUT")), "Faction elimination status");
            check((Localization.translate(language, "Ember") + " 42%").equals(Localization.translate(language, "Ember 42%")), "Faction share");
            check((Localization.translate(language, "Ember") + " / " + Localization.translate(language, "Pressure")).equals(Localization.translate(language, "Ember / Pressure")), "Faction style composite");
            check(("01 / " + Localization.translate(language, "First Contact")).equals(Localization.translate(language, "01 / First Contact")), "Battle title composite");
            check((normal + " / " + Localization.text(language, "attempt.sector", 3)).equals(Localization.translate(language, "Normal / Sector 3")), "Briefing difficulty/sector");
            String pb = "NORMAL / PB 02:15 / TIME IMPROVED";
            check(Localization.text(language, "result.campaign_pb", Localization.translate(language, "NORMAL"), "02:15", Localization.translate(language, "TIME IMPROVED")).equals(Localization.translate(language, pb)), "Campaign PB whole template");
            check(Localization.translate(language, "IN PROGRESS / Challenge / Sector 3").equals(Localization.text(language, "home.in_progress", Localization.text(language, "attempt.challenge", 3))), "Protected-attempt composed label");
            check(Localization.translate(language, "No Hard record").equals(Localization.text(language, "record.no_difficulty", Localization.translate(language, "Hard"))), "Inner difficulty in no-record phrase");
        }
        String[] opaque = {"daily-v10-1", "king_gain", "rulesVersion", "12345", "2026-10-09", "00:00 UTC", "A completely unknown sentence.", "seed=4711;rules=11"};
        for (String source : opaque) for (String language : Localization.LANGUAGES)
            check(source.equals(Localization.translate(language, source)), "Opaque identity remains unchanged " + source);
        for (String[] pair : cases) check(pair[0].equals(Localization.legacy("en", pair[0])), "English adapter preserves original output");
    }

    private static void numbersAndIsolation() throws Exception {
        GameModel model = new GameModel(0, 1, 123);
        byte[] before = model.save();
        Locale previous = Locale.getDefault();
        try {
            for (Locale device : new Locale[] {Locale.US, Locale.CHINA, new Locale("hi", "IN"), new Locale("id", "ID")}) {
                Locale.setDefault(device);
                for (String language : Localization.LANGUAGES) {
                    String expected = NumberFormat.getNumberInstance(locale(language)).format(12345);
                    check(Localization.text(language, "result.budget_remaining", 12345).contains(expected), "Locale-controlled grouped number, independent of device " + language);
                    NumberFormat fraction = NumberFormat.getNumberInstance(locale(language));
                    fraction.setGroupingUsed(false); fraction.setMinimumFractionDigits(2); fraction.setMaximumFractionDigits(2);
                    check(Localization.text(language, "hud.boost", 1.2).endsWith(fraction.format(1.2)), "Fixed-decimal multiplier " + language);
                    check(Localization.text(language, "result.hold_completed", 25.5, 20, 20).contains(NumberFormat.getNumberInstance(locale(language)).format(25.5)), "Fractional result seconds " + language);
                    check(Localization.text(language, "campaign.chapter", 1, 10).contains("01"), "Padded chapter number " + language);
                    Localization.translate(language, "2026-10-09 / UTC");
                }
            }
        } finally { Locale.setDefault(previous); }
        check(Arrays.equals(before, model.save()), "Translation and locale changes do not touch the battle");
        for (String unsupported : new String[] {null, "", "fr", "en-US", "ID", "in"})
            check(Localization.text("en", "settings.title").equals(Localization.text(unsupported, "settings.title")), "Unsupported locale uses English");
        check(Localization.translate("id", "Best: 12,345 troops / 02:15").equals(Localization.text("id", "record.fewest_troops", 12345, "02:15")), "Grouped English numeric parser");
        check(Localization.text("hi", "home.sector", 1, "$name\\path").endsWith("$name\\path"), "Arguments are literal, not regex replacement syntax");
    }

    private static Locale locale(String language) {
        return language.equals("id") ? new Locale("id", "ID") : language.equals("hi") ? new Locale("hi", "IN") : Locale.US;
    }

    private static void resultArguments() {
        String[] codes = {ObjectiveResult.IN_PROGRESS, ObjectiveResult.CAMPAIGN_WON, ObjectiveResult.HOLD_COMPLETED,
            ObjectiveResult.KEEP_COMPLETED, ObjectiveResult.BUDGET_COMPLETED, ObjectiveResult.BUDGET_EXCEEDED,
            ObjectiveResult.PROTECTED_KING_LOST, ObjectiveResult.PLAYER_ELIMINATED,
            ObjectiveResult.SURRENDERED, ObjectiveResult.LEGACY_DEFEAT};
        Object[][] args = {{}, {}, {25.5f, 20f, 20f}, {45f, 45f}, {100, 120, 20}, {125, 120}, {}, {}, {}, {}};
        for (int i = 0; i < codes.length; i++) for (String language : Localization.LANGUAGES) {
            check(Localization.keys().contains(codes[i]), "Model reason is cataloged " + codes[i]);
            String text = Localization.text(language, codes[i], args[i]);
            check(!text.equals(codes[i]) && !text.contains("{"), "Numeric model reason formats " + language + ":" + codes[i]);
        }
        check("Deployment budget exceeded: 125 / 120 troops.".equals(Localization.text("en", ObjectiveResult.BUDGET_EXCEEDED, 125, 120)), "Reproduced failure exact sentence");
        GameModel model = new GameModel(0, 1, 18);
        model.objectiveType = Challenge.HOLD_KING;
        model.outcome = GameModel.WON;
        model.elapsed = 25.5f; model.objectiveProgress = 20; model.objectiveSeconds = 20;
        ObjectiveResult facts = ObjectiveResult.evaluate(model);
        check(Arrays.equals(facts.numericArguments(), new Number[] {25.5f, 20f, 20f}), "Actual model argument order");
        for (String language : Localization.LANGUAGES)
            check(Localization.text(language, facts.code, (Object[]) facts.numericArguments()).equals(Localization.text(language, "result.hold_completed", 25.5f, 20f, 20f)), "Actual result presentation " + language);
    }

    private static void compactWidths() {
        FontRenderContext context = new FontRenderContext(null, true, true);
        Font font = new Font("SansSerif", Font.PLAIN, 12);
        for (String language : Localization.LANGUAGES) {
            width(font, context, Localization.text(language, "budget.warning_short", 125, 120), 376);
            width(font, context, Localization.text(language, "budget.hud", 120, 120, 0), 376);
            width(font, context, Localization.text(language, "deploy.preview", 125, 0), 220);
            for (int perk : new int[] {1, 2, 4, 8, 16})
                width(font, context, Localization.text(language, "perk." + perk + ".effect"), 376);
        }
    }

    private static void width(Font font, FontRenderContext context, String text, double available) {
        check(font.getStringBounds(text, context).getWidth() <= available, "Compact Java2D width <= " + available + ": " + text);
    }

    private static void sourceKeyInventory() throws Exception {
        Path source = Paths.get("app", "src", "main", "java", "com", "frontline", "offline");
        if (!Files.isDirectory(source)) return;
        String[] files = {"GameScene.java", "RunScreens.java", "ObjectiveResult.java", "LogisticsScreens.java"};
        Pattern call = Pattern.compile("\\b(?:tr\\(\\s*|t\\(\\s*language\\s*,\\s*)\"([a-z][a-z0-9_.]*[a-z0-9_])\"");
        Pattern reason = Pattern.compile("\"((?:result|record)\\.[a-z_]+)\"");
        for (String file : files) {
            Path path = source.resolve(file);
            if (!Files.isRegularFile(path)) continue;
            String contents = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Matcher matcher = (file.equals("ObjectiveResult.java") ? reason : call).matcher(contents);
            while (matcher.find()) check(Localization.keys().contains(matcher.group(1)), "Every current stable call key exists " + file + ":" + matcher.group(1));
        }
    }

    private static void sourceLiteralInventory() throws Exception {
        Path source = Paths.get("app", "src", "main", "java", "com", "frontline", "offline");
        if (!Files.isDirectory(source)) return;
        Pattern quoted = Pattern.compile("\"([^\"\\\\]*)\"");
        Set<String> seen = new HashSet<>();
        for (String file : new String[] {"GameScene.java", "MainActivity.java"}) {
            Path path = source.resolve(file);
            if (!Files.isRegularFile(path)) continue;
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.contains("IOException(") || line.contains("new Challenge(\"Saved Mission\"")) continue;
                Matcher matcher = quoted.matcher(line);
                while (matcher.find()) {
                    String literal = matcher.group(1);
                    // Concatenation fragments are covered by dynamicInventory, not literal lookup.
                    if (!literal.equals(literal.trim()) || !literal.contains(" ") || !literal.matches("[A-Za-z].*")
                        || literal.matches(".*%[0-9$]*[dfs].*")
                        || line.substring(matcher.end()).trim().startsWith("+")
                        || !seen.add(literal)) continue;
                    literal(literal);
                }
            }
        }
        check(seen.size() >= 70, "Current scene and native-dialog complete sentence inventory");
    }

    private static void expectIllegal(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }

    private static void expectUnsupported(Runnable action, String message) {
        try { action.run(); } catch (UnsupportedOperationException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
