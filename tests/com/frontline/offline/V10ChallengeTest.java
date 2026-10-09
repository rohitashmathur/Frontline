package com.frontline.offline;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public final class V10ChallengeTest {
    private static final long DAY_MILLIS = 86_400_000L;
    private static int checks;

    public static int run() throws Exception {
        checks = 0;
        datesAndReset();
        String[] rotation = dailySeeds();
        localeIndependence(); presets(); configurableObjectives(); dailySetupsAndRestore(rotation);
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " focused V10 challenge checks.");
    }

    private static void presets() throws Exception {
        check(Challenge.CAMPAIGN == 0 && Challenge.HOLD_KING == 1
            && Challenge.KEEP_KING == 2 && Challenge.BUDGET == 3, "Objective IDs remain stable");
        check("daily-v10-1".equals(Challenge.LEGACY_DAILY_VERSION) && "daily-v11-2".equals(Challenge.DAILY_VERSION), "Daily rules versions are explicit");
        String[] names = {"Crown Watch", "Three Front Watch", "Gilded Watch", "Home Guard",
            "Province Guard", "Veiled Guard", "Lean Frontier", "Lean Three Fronts", "Lean Gilded Front"};
        int[] sectors = {0, 2, 12, 0, 8, 18, 0, 2, 12};
        float[] seconds = {20, 30, 45, 45, 60, 90, 0, 0, 0};
        int[] budgets = {0, 0, 0, 0, 0, 0, 120, 220, 300};
        int[][] kings = {{0, 5}, {0, 12, 4}, {0, 13}, {0, 5}, {0, 15, 3}, {0, 11, 3},
            {0, 5}, {0, 12, 4}, {0, 13}};
        check(Challenge.PRESETS.length == 9, "There are three curated presets per objective");
        int[] counts = new int[4];
        Set<String> uniqueNames = new HashSet<>();
        for (int i = 0; i < Challenge.PRESETS.length; i++) {
            Challenge preset = Challenge.PRESETS[i];
            int type = i / 3 + 1;
            check(preset != null && names[i].equals(preset.name), "Preset name/order: " + i);
            check(preset.sector == sectors[i] && preset.type == type
                && preset.seconds == seconds[i] && preset.budget == budgets[i], "Preset configuration: " + i);
            check(uniqueNames.add(preset.name), "Preset names are distinct");
            counts[preset.type]++;
            for (int difficulty = 0; difficulty < 3; difficulty++) {
                GameModel model = verifyFactory(preset, difficulty, 710 + i, i, "");
                verifyKings(model, kings[i]);
                GameModel campaign = new GameModel(preset.sector, difficulty, 710 + i);
                model.update(.1f); campaign.update(.1f);
                for (int tile = 0; tile < model.territories.size(); tile++)
                    check(model.territories.get(tile).troops == campaign.territories.get(tile).troops
                        || model.missionPressure != 0 && model.territories.get(tile).owner == GameModel.NEUTRAL,
                        "Challenge AI version does not add a production bonus");
            }
            if (type == Challenge.HOLD_KING)
                check(preset.objective().equals("Hold marked king continuously for " + (int) preset.seconds + "s"),
                    "Hold objective states continuous duration");
            else if (type == Challenge.KEEP_KING)
                check(preset.objective().equals("Retain starting king for " + (int) preset.seconds + "s; never lose it"),
                    "Retain objective states duration and permanent loss condition");
            else check(preset.objective().equals("Win; deploy at most " + preset.budget + " troops"),
                "Budget objective states a troop deployment limit");
        }
        check(counts[1] == 3 && counts[2] == 3 && counts[3] == 3, "Each objective has exactly three presets");
    }

    private static GameModel verifyFactory(Challenge challenge, int difficulty, long seed, int id, String date)
        throws Exception {
        GameModel actual = challenge.create(difficulty, seed, id, date);
        GameModel expected = new GameModel(challenge.sector, difficulty, seed);
        expected.configureChallenge(challenge.type,
            expected.originalKing(challenge.type == Challenge.HOLD_KING ? 1 : GameModel.PLAYER),
            challenge.seconds, challenge.budget);
        expected.challengeId = id; expected.dailyDate = date == null ? "" : date; expected.aiVersion = 1;
        expected.dailyVersion = expected.dailyDate.isEmpty() ? "" : Challenge.DAILY_VERSION;
        expected.missionPressure = actual.missionPressure;
        for (int tile = 0; tile < expected.territories.size(); tile++) if (expected.missionPressure != 0
            && expected.territories.get(tile).owner == GameModel.NEUTRAL)
            expected.territories.get(tile).troops = Math.max(1,Math.floor(expected.territories.get(tile).troops
                *(expected.missionPressure == GameModel.DEFENCE_HOME ? .4 : .15)));
        check(actual.levelIndex == challenge.sector && actual.difficulty == difficulty,
            "Factory preserves sector and caller's fixed difficulty");
        check(actual.challengeId == id && actual.dailyDate.equals(expected.dailyDate) && actual.aiVersion == 1,
            "Factory attaches challenge identity, original date, and V10 AI");
        check(actual.outcome == GameModel.PLAYING && actual.elapsed == 0 && actual.unitsSent == 0,
            "A newly configured objective starts with fresh counters");
        check(actual.objectiveType == challenge.type && actual.objectiveTarget == expected.objectiveTarget
            && actual.objectiveSeconds == challenge.seconds && actual.deploymentBudget == challenge.budget
            && actual.objectiveProgress == 0, "Factory configures the requested objective and target directly");
        check(Arrays.equals(actual.save(), expected.save()), "Factory passes the exact objective and king target to the model");
        return actual;
    }

    private static void verifyKings(GameModel model, int[] expectedIds) {
        check(model.level().opponents + 1 == expectedIds.length, "Preset has the expected number of factions");
        Set<Integer> distinctIds = new HashSet<>();
        for (int owner = 0; owner < expectedIds.length; owner++) {
            int id = model.originalKing(owner);
            check(id == expectedIds[owner], "King uses the validated starting territory for owner " + owner);
            check(id >= 0 && id < model.territories.size() && distinctIds.add(id), "Starting kings have distinct valid IDs");
            GameModel.Territory king = model.territories.get(id);
            check(king.capital && king.owner == owner && model.owned(owner) == 1,
                "Each faction starts on its own original king");
            for (int other = 0; other < owner; other++) {
                GameModel.Territory previous = model.territories.get(model.originalKing(other));
                check(king.x != previous.x || king.y != previous.y, "Starting king positions do not overlap");
            }
        }
    }

    private static void configurableObjectives() throws Exception {
        Challenge hold = new Challenge("Custom Hold", 3, Challenge.HOLD_KING, 12.5f, 0);
        Challenge keep = new Challenge("Custom Guard", 17, Challenge.KEEP_KING, 17.25f, 0);
        Challenge budget = new Challenge("Custom Budget", 29, Challenge.BUDGET, 0, 137);
        check(hold.objective().equals("Hold marked king continuously for 12.5s"), "Custom hold duration is not rounded");
        check(keep.objective().equals("Retain starting king for 17.25s; never lose it"), "Custom retention duration is not rounded");
        check(budget.objective().equals("Win; deploy at most 137 troops"), "Custom budget is independent of curated presets");
        for (Challenge custom : new Challenge[] {hold, keep, budget}) {
            GameModel model = verifyFactory(custom, 1, 42, -1, null);
            check(model.dailyDate.isEmpty(), "Custom non-daily challenges have no daily identity");
            check(Arrays.equals(model.save(), GameModel.restore(model.save()).save()), "Custom objective configuration is restorable");
        }
        verifyFactory(new Challenge("Full Day", 0, Challenge.HOLD_KING, 86400, 0), 1, 42, -1, "");
        verifyFactory(new Challenge("Large Budget", 0, Challenge.BUDGET, 0, Integer.MAX_VALUE), 1, 42, -1, "");
        for (String name : new String[] {null, "", " ", "\t\n"})
            rejects(() -> new Challenge(name, 0, Challenge.HOLD_KING, 1, 0), "Blank challenge name is rejected");
        for (int sector : new int[] {-1, GameModel.LEVELS.length, Integer.MAX_VALUE})
            rejects(() -> new Challenge("Invalid", sector, Challenge.HOLD_KING, 1, 0), "Invalid sector is rejected");
        for (int type : new int[] {-1, Challenge.CAMPAIGN, 4, Integer.MAX_VALUE})
            rejects(() -> new Challenge("Invalid", 0, type, 1, 0), "Only the three challenge types are accepted");
        for (int type : new int[] {Challenge.HOLD_KING, Challenge.KEEP_KING}) {
            for (float duration : new float[] {-1, 0, 86401, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
                rejects(() -> new Challenge("Invalid", 0, type, duration, 0), "Timed objectives need a finite positive duration up to one day");
            for (int limit : new int[] {-1, 1})
                rejects(() -> new Challenge("Invalid", 0, type, 1, limit), "Timed objectives do not also specify a budget");
        }
        for (float duration : new float[] {-1, 1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            rejects(() -> new Challenge("Invalid", 0, Challenge.BUDGET, duration, 1), "Budget objective has no timer");
        for (int limit : new int[] {-1, 0})
            rejects(() -> new Challenge("Invalid", 0, Challenge.BUDGET, 0, limit), "Budget must be positive");
        for (int difficulty : new int[] {-1, 3})
            rejects(() -> hold.create(difficulty, 42, -1, ""), "Factory rejects invalid difficulty");
        for (int id : new int[] {-2, Challenge.PRESETS.length, Integer.MAX_VALUE})
            rejects(() -> hold.create(1, 42, id, ""), "Factory rejects an unrestorable challenge ID");
        rejects(() -> hold.create(1, 42, -1, "2026-10-06"), "Daily identity requires a curated challenge ID");
        rejects(() -> hold.create(1, 42, -1, "2026-02-30"), "Factory rejects invalid daily identity");
    }

    private static void datesAndReset() throws Exception {
        for (String value : new String[] {"0001-01-01", "0999-12-31", "1582-10-10", "1600-02-29",
            "1900-02-28", "1999-12-31", "2000-02-29", "2024-02-29", "2026-10-06", "2100-03-01",
            "2400-02-29", "9999-12-31"})
            check(Challenge.validDate(value), "Valid Gregorian ISO date: " + value);
        for (String value : new String[] {null, "", "0000-01-01", "2026-1-01", "2026-01-1", "26-01-01",
            "10000-01-01", "2026-00-01", "2026-13-01", "2026-01-00", "2026-01-32", "2026-04-31",
            "1700-02-29", "1900-02-29", "2100-02-29", "2026-02-29", "2024-02-30", "2026/10/06",
            " 2026-10-06", "2026-10-06 ", "2026-10-06\n", "2026-10-06T00:00:00Z", "-001-01-01",
            "abcd-ef-gh", "\u0662\u0660\u0662\u0666-10-06"}) {
            check(!Challenge.validDate(value), "Non-strict or impossible date is rejected");
            rejects(() -> Challenge.dailySeed(value), "Invalid dates cannot seed a daily");
            rejects(() -> Challenge.dailyId(value), "Invalid dates cannot choose a preset");
        }
        check(Challenge.date(0).equals("1970-01-01") && Challenge.date(-1).equals("1969-12-31"),
            "UTC dates also handle timestamps before the epoch");
        String[][] transitions = {{"1999-12-31", "2000-01-01"}, {"2000-02-28", "2000-02-29"},
            {"2000-02-29", "2000-03-01"}, {"2024-02-28", "2024-02-29"}, {"2024-02-29", "2024-03-01"},
            {"2026-12-31", "2027-01-01"}, {"2100-02-28", "2100-03-01"}};
        for (String[] pair : transitions) {
            long before = utcMillis(pair[0] + " 23:59:59.999");
            check(Challenge.date(before).equals(pair[0]) && Challenge.date(before + 1).equals(pair[1]),
                "UTC date changes exactly at midnight across years and leap days");
            check(Challenge.untilReset(before) == 1 && Challenge.untilReset(before + 1) == DAY_MILLIS,
                "Countdown reaches midnight then begins the next full day");
        }
        check(Challenge.untilReset(0) == DAY_MILLIS && Challenge.untilReset(DAY_MILLIS / 2) == DAY_MILLIS / 2,
            "Countdown means time until the next 00:00 UTC");
        for (long millis : new long[] {-DAY_MILLIS, -DAY_MILLIS - 1, -1, 0, 1, DAY_MILLIS - 1,
            DAY_MILLIS, Long.MIN_VALUE, Long.MAX_VALUE}) {
            long remaining = Challenge.untilReset(millis);
            check(remaining > 0 && remaining <= DAY_MILLIS, "Countdown is positive and at most one day");
            check((millis % DAY_MILLIS + remaining) % DAY_MILLIS == 0, "Countdown points to a UTC midnight without overflow");
        }
    }

    private static String[] dailySeeds() throws Exception {
        String[] dates = {"1970-01-01", "2000-02-29", "2024-02-29", "2026-10-06", "2030-01-01", "2099-12-31"};
        long[] seeds = {-5441327380477092757L, 4076978942556965497L, -4773757595718894193L,
            3506050238638704035L, 6306319856464079119L, 6962207422773575449L};
        int[] ids = {5, 4, 8, 5, 7, 1};
        for (int i = 0; i < dates.length; i++) {
            check(Challenge.dailySeed(dates[i],Challenge.LEGACY_DAILY_VERSION) == seeds[i], "Legacy version/date FNV-1a seed is stable: " + dates[i]);
            check(Challenge.dailyId(dates[i],Challenge.LEGACY_DAILY_VERSION) == ids[i], "Legacy daily preset uses signed floorMod, including negative seeds");
            check(Challenge.dailySeed(dates[i]) != seeds[i], "New daily identity does not reinterpret legacy dates");
        }
        String[] rotation = new String[Challenge.PRESETS.length];
        Set<Long> uniqueSeeds = new HashSet<>();
        long start = utcMillis("2024-01-01 00:00:00.000");
        for (int day = 0; day < 366; day++) {
            String date = Challenge.date(start + day * DAY_MILLIS);
            long seed = Challenge.dailySeed(date);
            int id = Challenge.dailyId(date);
            check(Challenge.validDate(date) && seed == Challenge.dailySeed(date), "Every leap-year day seeds deterministically");
            check(id >= 0 && id < rotation.length && id == (int) Math.floorMod(seed, (long) rotation.length),
                "Every daily ID selects a curated preset");
            check(uniqueSeeds.add(seed), "Different dates have distinct seeds in the tested year");
            if (rotation[id] == null) rotation[id] = date;
        }
        for (String date : rotation) check(date != null, "The daily rotation reaches every curated preset");
        return rotation;
    }

    private static void localeIndependence() throws Exception {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        long before = utcMillis("2026-10-06 23:59:59.999");
        try {
            for (Locale locale : new Locale[] {new Locale("th", "TH"), new Locale("ar", "EG"), Locale.FRANCE}) {
                Locale.setDefault(locale);
                for (String zone : new String[] {"Asia/Kolkata", "Pacific/Kiritimati", "America/Los_Angeles"}) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone));
                    check(Challenge.date(before).equals("2026-10-06") && Challenge.date(before + 1).equals("2026-10-07"),
                        "Device locale/time zone cannot change the daily UTC date");
                    check(Challenge.validDate("2000-02-29") && !Challenge.validDate("1900-02-29"),
                        "Device locale cannot change Gregorian date validation");
                    check(Challenge.dailySeed("2026-10-06",Challenge.LEGACY_DAILY_VERSION) == 3506050238638704035L
                        && Challenge.dailyId("2026-10-06",Challenge.LEGACY_DAILY_VERSION) == 5 && Challenge.untilReset(before) == 1,
                        "Device locale/time zone cannot change the seed, preset, or reset countdown");
                }
            }
        } finally {
            Locale.setDefault(previousLocale); TimeZone.setDefault(previousZone);
        }
    }

    private static void dailySetupsAndRestore(String[] rotation) throws Exception {
        for (String date : new String[] {"1970-01-01", "2000-02-29", "2024-02-29", "2026-10-06", "2030-01-01", "2099-12-31"}) {
            int id = Challenge.dailyId(date);
            Challenge preset = Challenge.PRESETS[id];
            GameModel first = preset.create(1, Challenge.dailySeed(date), id, date);
            GameModel repeat = preset.create(1, Challenge.dailySeed(date), id, date);
            check(first.difficulty == 1 && Arrays.equals(first.save(), repeat.save()),
                "The same version/date repeats the entire setup at Normal difficulty across years and leap days");
        }
        for (int id = 0; id < rotation.length; id++) {
            String originalDate = rotation[id];
            Challenge preset = Challenge.PRESETS[id];
            GameModel active = preset.create(1, Challenge.dailySeed(originalDate), id, originalDate);
            GameModel repeat = preset.create(1, Challenge.dailySeed(originalDate), id, originalDate);
            check(active.difficulty == 1 && Arrays.equals(active.save(), repeat.save()),
                "Every daily preset repeats deterministically at Normal difficulty");
            if (preset.type == Challenge.HOLD_KING) active.territories.get(active.originalKing(1)).owner = GameModel.PLAYER;
            int source = active.originalKing(GameModel.PLAYER), target = -1;
            for (GameModel.Territory tile : active.territories)
                if (!tile.capital) { target = tile.id; break; }
            check(target >= 0 && active.launch(source, target, .25) > 0, "Active daily has deployments to preserve");
            for (int tick = 0; tick < 3; tick++) active.update(.1f);
            check(active.elapsed > 0 && active.unitsSent > 0 && active.outcome == GameModel.PLAYING,
                "All preset objectives can be saved with progress still active");
            byte[] saved = active.save();
            long before = utcMillis(originalDate + " 23:59:59.900");
            String nextDate = Challenge.date(before + Challenge.untilReset(before));
            check(!nextDate.equals(originalDate) && Challenge.dailySeed(nextDate) != Challenge.dailySeed(originalDate),
                "Crossing midnight creates a new daily date and seed");
            GameModel restored = GameModel.restore(saved);
            check(restored.dailyDate.equals(originalDate) && restored.challengeId == id
                && restored.levelIndex == preset.sector && restored.difficulty == 1 && restored.aiVersion == 1,
                "Restored active daily retains its original identity, sector, difficulty, and AI");
            check(Arrays.equals(saved, restored.save()), "Save/resume retains the original objective configuration and counters");
            check(restored.objectiveType == preset.type && restored.objectiveTarget == active.objectiveTarget
                && restored.objectiveSeconds == preset.seconds && restored.deploymentBudget == preset.budget
                && restored.objectiveProgress == active.objectiveProgress && restored.unitsSent == active.unitsSent,
                "Midnight restoration preserves objective target, timing, progress, and deployment usage");
            check(preset.type == Challenge.BUDGET ? restored.unitsSent > 0 : restored.objectiveProgress > 0,
                "Saved counters contain actual objective progress");
            int nextId = Challenge.dailyId(nextDate);
            GameModel next = Challenge.PRESETS[nextId].create(1, Challenge.dailySeed(nextDate), nextId, nextDate);
            check(next.dailyDate.equals(nextDate) && restored.dailyDate.equals(originalDate),
                "Building the next daily does not relabel or replace the retained battle");
            active.update(.1f); restored.update(.1f);
            check(Arrays.equals(active.save(), restored.save()), "Resumed daily progress matches uninterrupted progress");
        }
    }

    private static long utcMillis(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); format.setLenient(false);
        return format.parse(value).getTime();
    }

    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
