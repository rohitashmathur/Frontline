package com.frontline.offline;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public final class Challenge {
    public static final int CAMPAIGN = 0, HOLD_KING = 1, KEEP_KING = 2, BUDGET = 3;
    public static final String DAILY_VERSION = "daily-v10-1";
    private static final long DAY_MILLIS = 86_400_000L;

    // Initial timers and deployment budgets are provisional; human playtesting is required.
    public static final Challenge[] PRESETS = {
        new Challenge("Crown Watch", 0, HOLD_KING, 20, 0),
        new Challenge("Three Front Watch", 2, HOLD_KING, 30, 0),
        new Challenge("Gilded Watch", 12, HOLD_KING, 45, 0),
        new Challenge("Home Guard", 0, KEEP_KING, 45, 0),
        new Challenge("Province Guard", 8, KEEP_KING, 60, 0),
        new Challenge("Veiled Guard", 18, KEEP_KING, 90, 0),
        new Challenge("Lean Frontier", 0, BUDGET, 0, 120),
        new Challenge("Lean Three Fronts", 2, BUDGET, 0, 220),
        new Challenge("Lean Gilded Front", 12, BUDGET, 0, 300)
    };

    public final String name;
    public final int sector, type, budget;
    public final float seconds;

    public Challenge(String name, int sector, int type, float seconds, int budget) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Challenge name");
        if (sector < 0 || sector >= GameModel.LEVELS.length) throw new IllegalArgumentException("Challenge sector");
        if (type < HOLD_KING || type > BUDGET) throw new IllegalArgumentException("Challenge type");
        if (!Float.isFinite(seconds) || seconds < 0 || seconds > 86400 || budget < 0
            || (type == BUDGET ? seconds != 0 || budget == 0 : seconds == 0 || budget != 0))
            throw new IllegalArgumentException("Challenge objective");
        this.name = name; this.sector = sector; this.type = type;
        this.seconds = seconds; this.budget = budget;
    }

    public String objective() {
        if (type == BUDGET) return "Win; deploy at most " + budget + " troops";
        String duration = Float.toString(seconds);
        if (duration.endsWith(".0")) duration = duration.substring(0, duration.length() - 2);
        if (type == HOLD_KING) return "Hold marked king continuously for " + duration + "s";
        return "Retain starting king for " + duration + "s; never lose it";
    }

    public GameModel create(int difficulty, long seed, int id, String dailyDate) {
        String originalDate = dailyDate == null ? "" : dailyDate;
        if (!originalDate.isEmpty() && !validDate(originalDate)) throw new IllegalArgumentException("Daily date");
        if (id < -1 || id >= PRESETS.length || id == -1 && !originalDate.isEmpty())
            throw new IllegalArgumentException("Challenge identity");
        GameModel model = new GameModel(sector, difficulty, seed);
        int target = model.originalKing(type == HOLD_KING ? 1 : GameModel.PLAYER);
        model.configureChallenge(type, target, seconds, budget);
        model.challengeId = id; model.dailyDate = originalDate; model.aiVersion = 1;
        return model;
    }

    public static String date(long millis) {
        return dateFormat().format(new Date(millis));
    }

    public static boolean validDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        ParsePosition position = new ParsePosition(0);
        Date parsed = dateFormat().parse(value, position);
        return parsed != null && position.getIndex() == value.length();
    }

    private static SimpleDateFormat dateFormat() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        GregorianCalendar calendar = new GregorianCalendar(utc, Locale.US);
        calendar.setGregorianChange(new Date(Long.MIN_VALUE));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setCalendar(calendar);
        format.setLenient(false);
        return format;
    }

    public static long dailySeed(String value) {
        if (!validDate(value)) throw new IllegalArgumentException("Daily date");
        // FNV-1a over the ASCII version followed by the ISO date, without a separator.
        String input = DAILY_VERSION + value;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public static int dailyId(String value) {
        return (int) Math.floorMod(dailySeed(value), (long) PRESETS.length);
    }

    public static long untilReset(long millis) {
        return DAY_MILLIS - Math.floorMod(millis, DAY_MILLIS);
    }
}
