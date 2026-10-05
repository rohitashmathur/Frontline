package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public final class GameModel {
    public static final int RULES_VERSION = 10;
    public static final int CLASSIC = 0, PRESSURE = 1, GUARDIAN = 2;
    public static final int CAPTURE_EVENT = 1, INTERCEPT_EVENT = 2, KING_GAIN_EVENT = 3,
        KING_LOSS_EVENT = 4, CAP_LOSS_EVENT = 5, HOME_KING_LOSS_EVENT = 6;
    public static final int NEUTRAL = -1, PLAYER = 0;
    public static final int PLAYING = 0, WON = 1, LOST = 2;
    public static final int NORMAL_CAP = 100, MAX_TROOPS = 125, MAX_CONVOYS = 900, MAX_TEAMS = 6;
    private static final int LEGACY_MAX_TROOPS = 1_000_000;
    private static final int CAMPAIGN = 0, HOLD_KING = 1, KEEP_KING = 2, BUDGET = 3;
    private static final int MAX_EVENTS = 432;
    public static final String[] DIFFICULTIES = {"Easy", "Normal", "Hard"};
    public static final Level[] LEVELS = {
        new Level("First Contact", 3, 2, 1, new int[] {}, 70),
        new Level("The Crossing", 4, 3, 1, new int[] {5}, 105),
        new Level("Three Fronts", 5, 3, 2, new int[] {6, 8}, 130),
        new Level("Broken Coast", 5, 4, 2, new int[] {2, 12, 17}, 150),
        new Level("Crossfire", 5, 5, 3, new int[] {6, 8, 16, 18}, 180),
        new Level("Last Stand", 6, 5, 3, new int[] {7, 10, 14, 19, 22}, 210),
        new Level("Foundry Road", 4, 3, new int[] {5,6}, 125, new int[] {0,11}, new int[] {1}, 32,36,8),
        new Level("Ashen Fields", 5, 3, new int[] {6,8}, 140, new int[] {10,4}, new int[] {1}, 34,38,8),
        new Level("Split Province", 5, 4, new int[] {2,7,12,17}, 165, new int[] {0,19,4}, new int[] {1,2}, 34,34,9),
        new Level("Redwater Basin", 5, 4, new int[] {6,8,11,13}, 170, new int[] {15,4,0}, new int[] {1,2}, 34,36,9),
        new Level("Smelter Ring", 5, 5, new int[] {6,8,12,16,18}, 185, new int[] {10,14,24}, new int[] {1,2}, 34,38,9),
        new Level("Voss Hold", 6, 4, new int[] {7,8,15,16}, 195, new int[] {0,23,5}, new int[] {1,2}, 36,40,10),
        new Level("Gilded Approach", 4, 4, new int[] {5,10}, 145, new int[] {0,15}, new int[] {2}, 34,42,9),
        new Level("Twin Bridges", 5, 4, new int[] {2,7,16,18}, 170, new int[] {15,4,0}, new int[] {2,1}, 36,36,10),
        new Level("Goldwind Reach", 6, 3, new int[] {7,10}, 165, new int[] {6,17,5}, new int[] {2,1}, 36,37,10),
        new Level("Mirror Provinces", 5, 5, new int[] {1,3,11,13,21,23}, 190, new int[] {10,14,24}, new int[] {2,3}, 36,38,10),
        new Level("League Causeway", 6, 4, new int[] {1,4,7,10,13,16}, 210, new int[] {0,23,5,18}, new int[] {2,1,3}, 38,35,11),
        new Level("Sol Citadel", 6, 5, new int[] {7,9,13,16,20,22}, 225, new int[] {0,29,5,24}, new int[] {2,1,3}, 38,38,11),
        new Level("Veiled Shore", 5, 3, new int[] {2,7,12}, 170, new int[] {0,14,4}, new int[] {3,2}, 34,38,10),
        new Level("Northern Signal", 5, 4, new int[] {6,7,8,16,18}, 185, new int[] {15,4,0}, new int[] {3,1}, 36,39,11),
        new Level("Obsidian Passage", 6, 4, new int[] {2,8,14,20}, 195, new int[] {0,23,5}, new int[] {3,2}, 36,40,11),
        new Level("Shattered March", 5, 5, new int[] {2,6,8,12,16,18,22}, 215, new int[] {10,14,0,24}, new int[] {3,1,2}, 38,36,11),
        new Level("Nightfall Circuit", 6, 5, new int[] {7,8,9,13,16,20,21,22}, 230, new int[] {0,29,5,24}, new int[] {3,2,1}, 38,38,12),
        new Level("Veil Bastion", 6, 5, new int[] {2,3,7,10,14,15,19,22,26,27}, 240, new int[] {12,17,0,29}, new int[] {3,2,1}, 40,40,12),
        new Level("Broken Accord", 5, 4, new int[] {2,6,8,12}, 205, new int[] {15,4,0,19}, new int[] {1,2,3}, 38,38,11),
        new Level("Three Banners", 6, 4, new int[] {7,9,14,16}, 220, new int[] {0,23,5,18}, new int[] {2,3,1}, 38,39,12),
        new Level("Iron Meridian", 6, 5, new int[] {1,4,7,10,13,16,19,22,25,28}, 240, new int[] {12,17,0,29}, new int[] {1,3,2}, 40,40,12),
        new Level("Fractured Throne", 6, 5, new int[] {2,7,9,14,16,21,23,26}, 250, new int[] {0,29,5,24}, new int[] {3,1,2}, 40,41,12),
        new Level("Last Convergence", 6, 5, new int[] {8,9,14,15,20,21}, 260, new int[] {12,17,0,29}, new int[] {2,1,3}, 40,42,13),
        new Level("Concord Citadel", 6, 5, new int[] {2,3,8,9,14,15,20,21,26,27}, 270, new int[] {12,17,0,29}, new int[] {1,2,3}, 40,44,13),
        new Level("Western Passage", 7, 6, new int[] {9,12,23,26}, 285, new int[] {0,41,6,35,20}, new int[] {4,1,2,3}, 42,44,12),
        new Level("Ironwater Coast", 7, 6, new int[] {8,10,17,24,31,33}, 300, new int[] {0,41,6,35,20}, new int[] {4,2,3,1}, 42,45,12),
        new Level("Rivet Provinces", 7, 7, new int[] {9,12,23,25,36,39}, 315, new int[] {0,48,6,42,27}, new int[] {4,1,3,2}, 44,45,13),
        new Level("Steel Causeway", 8, 6, new int[] {10,13,18,21,26,29,34,37}, 325, new int[] {0,47,7,40,23}, new int[] {4,3,2,1}, 44,46,13),
        new Level("Dominion Ring", 7, 7, new int[] {8,10,12,22,24,26,36,38,40}, 340, new int[] {0,48,6,42,27}, new int[] {4,2,1,3}, 44,47,13),
        new Level("Soren Fortress", 8, 7, new int[] {9,14,18,21,34,37,41,46}, 355, new int[] {0,55,7,48,31}, new int[] {4,1,2,3}, 46,48,14),
        new Level("Frostline Landing", 8, 7, new int[] {10,13,18,21,34,37,42,45}, 350, new int[] {0,55,7,48,24,31}, new int[] {5,4,1,2,3}, 44,45,12),
        new Level("Whitewater Reach", 8, 7, new int[] {9,11,13,25,27,29,41,43,45}, 365, new int[] {0,55,7,48,24,31}, new int[] {5,2,4,3,1}, 44,46,13),
        new Level("Frozen Channels", 8, 8, new int[] {10,13,18,21,42,45,50,53}, 380, new int[] {0,63,7,56,24,31}, new int[] {5,3,1,4,2}, 46,46,13),
        new Level("Glacier Divide", 9, 7, new int[] {11,15,20,24,38,42,47,51}, 390, new int[] {0,62,8,54,27,35}, new int[] {5,4,2,1,3}, 46,47,14),
        new Level("Icebound Circuit", 8, 8, new int[] {9,11,13,17,21,41,45,49,51,53}, 400, new int[] {0,63,7,56,24,31}, new int[] {5,1,3,2,4}, 46,48,14),
        new Level("Kestrel Anchorage", 9, 7, new int[] {10,12,14,16,28,30,32,34,46,48,50,52}, 415, new int[] {0,62,8,54,27,35}, new int[] {5,4,1,2,3}, 48,49,14),
        new Level("Broken Coalition", 8, 7, new int[] {10,13,18,21,34,37,42,45}, 370, new int[] {7,48,0,55,31,24}, new int[] {1,4,5,2,3}, 46,47,13),
        new Level("Five Banners", 8, 8, new int[] {10,13,18,21,42,45,50,53}, 390, new int[] {56,7,63,0,31,24}, new int[] {4,5,2,3,1}, 46,48,14),
        new Level("Cinder and Steel", 9, 7, new int[] {11,15,20,24,38,42,47,51}, 405, new int[] {8,54,0,62,35,27}, new int[] {1,4,3,5,2}, 48,48,14),
        new Level("Scattered Oaths", 8, 8, new int[] {9,11,13,17,19,21,41,43,45,49,51,53}, 420, new int[] {0,63,7,56,24,31}, new int[] {3,2,5,1,4}, 48,49,14),
        new Level("Fivefold Siege", 9, 8, new int[] {11,15,20,24,47,51,56,60}, 440, new int[] {0,71,8,63,27,35}, new int[] {4,1,2,3,5}, 48,50,15),
        new Level("Coalition Keep", 9, 8, new int[] {10,12,14,16,28,30,32,34,46,48,50,52,64,66,68,70}, 450, new int[] {0,71,8,63,27,35}, new int[] {4,5,1,2,3}, 50,50,15),
        new Level("Throne Road", 8, 8, new int[] {10,13,18,21,42,45,50,53}, 405, new int[] {24,31,0,63,7,56}, new int[] {5,2,4,1,3}, 48,48,14),
        new Level("Royal Meridian", 9, 7, new int[] {11,15,20,24,38,42,47,51}, 420, new int[] {27,35,0,62,8,54}, new int[] {2,5,1,3,4}, 48,49,14),
        new Level("Crown Crossroads", 9, 8, new int[] {10,12,14,16,46,48,50,52}, 440, new int[] {0,71,8,63,27,35}, new int[] {5,4,3,2,1}, 50,49,15),
        new Level("Kings of the Divide", 9, 8, new int[] {11,15,20,24,38,42,47,51,56,60}, 450, new int[] {0,71,8,63,27,35}, new int[] {3,1,5,4,2}, 50,50,15),
        new Level("Four Crown Summit", 9, 8, new int[] {10,12,14,16,28,30,32,34,46,48,50,52}, 465, new int[] {0,71,8,63,27,35}, new int[] {4,2,1,5,3}, 50,51,15),
        new Level("Sovereign Bastion", 9, 8, new int[] {11,13,15,20,22,24,47,49,51,56,58,60}, 480, new int[] {0,71,8,63,27,35}, new int[] {5,4,1,2,3}, 50,52,16),
        new Level("Horizon's Edge", 8, 8, new int[] {10,13,18,21,42,45,50,53}, 430, new int[] {7,56,0,63,24,31}, new int[] {1,2,3,4,5}, 48,49,14),
        new Level("The Long Frontier", 9, 7, new int[] {11,15,20,24,38,42,47,51}, 440, new int[] {8,54,0,62,27,35}, new int[] {2,3,4,5,1}, 50,50,15),
        new Level("Alliance Bridge", 9, 8, new int[] {10,12,14,16,46,48,50,52}, 455, new int[] {63,8,71,0,35,27}, new int[] {3,4,5,1,2}, 50,50,15),
        new Level("Final Assembly", 9, 8, new int[] {11,15,20,24,38,42,47,51,56,60}, 470, new int[] {35,27,0,71,8,63}, new int[] {4,5,1,2,3}, 50,51,15),
        new Level("Beyond the Accord", 9, 8, new int[] {10,12,14,16,28,30,32,34,46,48,50,52}, 485, new int[] {71,0,63,8,35,27}, new int[] {5,1,2,3,4}, 52,52,16),
        new Level("United Citadel", 9, 8, new int[] {11,13,15,20,22,24,47,49,51,56,58,60}, 500, new int[] {27,35,8,63,0,71}, new int[] {1,2,3,4,5}, 52,54,16)
    };

    public static final class Level {
        public final String name;
        public final int columns, rows, opponents, parSeconds;
        final int[] holes, startingCells, factions;
        final int playerTroops, rivalTroops, neutralMinimum;
        Level(String name, int columns, int rows, int opponents, int[] holes, int parSeconds) {
            this.name = name; this.columns = columns; this.rows = rows;
            this.opponents = opponents; this.holes = holes; this.parSeconds = parSeconds;
            startingCells = null; factions = new int[opponents];
            for (int i = 0; i < opponents; i++) factions[i] = i+1;
            playerTroops = 32; rivalTroops = 32; neutralMinimum = 7;
        }
        Level(String name, int columns, int rows, int[] holes, int parSeconds, int[] startingCells,
            int[] factions, int playerTroops, int rivalTroops, int neutralMinimum) {
            this.name = name; this.columns = columns; this.rows = rows;
            this.holes = holes; this.parSeconds = parSeconds; this.startingCells = startingCells;
            this.factions = factions; opponents = factions.length;
            this.playerTroops = playerTroops; this.rivalTroops = rivalTroops; this.neutralMinimum = neutralMinimum;
        }
        public int faction(int owner) { return owner == PLAYER ? 0 : factions[owner-1]; }
    }

    public static final class Territory {
        public final int id;
        public final float x, y;
        public int owner;
        public double troops;
        public boolean capital;
        private int originalOwner = NEUTRAL;
        Territory(int id, float x, float y, int owner, double troops) {
            this.id = id; this.x = x; this.y = y; this.owner = owner; this.troops = troops;
        }
        public int count() { return (int) Math.floor(troops + 0.00001); }
    }

    public static final class Troop {
        public final int source, target, owner;
        public final float duration;
        public float age;
        public int units = 1;
        Troop(int source, int target, int owner, float duration, float age) {
            this.source = source; this.target = target; this.owner = owner;
            this.duration = duration; this.age = age;
        }
        public float progress() { return Math.max(0, Math.min(1, age / duration)); }
    }

    public static final class Clash {
        public final float x, y;
        public float remaining = .35f;
        Clash(float x,float y) { this.x = x; this.y = y; }
    }

    public static final class BattleEvent {
        public final int type, tile, units;
        BattleEvent(int type, int tile, int units) { this.type = type; this.tile = tile; this.units = units; }
    }

    private static final class StatefulRandom extends Random {
        private static final long MULTIPLIER = 0x5DEECE66DL, MASK = (1L << 48)-1;
        private long state;
        StatefulRandom(long seed) { super(seed); }
        @Override public synchronized void setSeed(long seed) {
            super.setSeed(seed); state = (seed ^ MULTIPLIER) & MASK;
        }
        @Override protected synchronized int next(int bits) {
            // Mirror Java's LCG without replacing its sampling or rejection behavior.
            state = (state*MULTIPLIER+0xBL) & MASK;
            return super.next(bits);
        }
        void restoreState(long value) { setSeed(value ^ MULTIPLIER); }
    }

    private static final class Collision {
        final int a, b; final float time;
        Collision(int a,int b,float time) { this.a = a; this.b = b; this.time = time; }
    }

    public final ArrayList<Territory> territories = new ArrayList<>();
    public final ArrayList<Troop> troops = new ArrayList<>();
    public final ArrayList<Clash> clashes = new ArrayList<>();
    public final int levelIndex;
    public int difficulty, outcome = PLAYING, captures, unitsLost, unitsSent;
    public float elapsed;
    public long seed;
    public boolean seedKnown = true, historyKnown = true, startingKingLost;
    public int rulesVersion = RULES_VERSION, aiVersion, intercepted, cappedReinforcements;
    public int objectiveType = CAMPAIGN, objectiveTarget = -1, deploymentBudget, challengeId = -1;
    public float objectiveSeconds, objectiveProgress;
    public String dailyDate = "";
    private final float[] aiTimers = new float[MAX_TEAMS];
    public final boolean[] resigned = new boolean[MAX_TEAMS];
    private float dominanceSeconds;
    private final StatefulRandom random;
    private final ArrayList<Collision> collisions = new ArrayList<>();
    private final ArrayList<BattleEvent> events = new ArrayList<>();
    private float heldSecondsThisTick;

    public GameModel(int levelIndex, int difficulty, long seed) {
        if (levelIndex < 0 || levelIndex >= LEVELS.length) throw new IllegalArgumentException("Level");
        if (difficulty < 0 || difficulty > 2) throw new IllegalArgumentException("Difficulty");
        this.levelIndex = levelIndex; this.difficulty = difficulty; this.seed = seed; random = new StatefulRandom(seed);
        Level level = level();
        int[] cells = new int[level.columns*level.rows];
        java.util.Arrays.fill(cells,-1);
        for (int row = 0; row < level.rows; row++) {
            for (int col = 0; col < level.columns; col++) {
                boolean hole = false;
                for (int absent : level.holes) if (absent == row * level.columns + col) hole = true;
                if (!hole) {
                    cells[row*level.columns+col] = territories.size();
                    territories.add(new Territory(territories.size(),
                    1 + col * 1.732f + (row % 2) * .866f, 1 + row * 1.5f,
                    NEUTRAL, level.neutralMinimum + random.nextInt(9)));
                }
            }
        }
        // Keep the original six layouts and spawn order compatible with existing saves.
        if (level.startingCells != null) {
            for (int owner = 0; owner <= level.opponents; owner++)
                setCapital(cells[level.startingCells[owner]],owner,owner == 0 ? level.playerTroops : level.rivalTroops);
        } else {
            setCapital(0, PLAYER, 32);
            setCapital(territories.size() - 1, 1, levelIndex == 0 ? 25 : 32);
            if (level.opponents >= 2) {
                int topRight = 0;
                for (Territory territory : territories) {
                    if (territory.y == 1) topRight = territory.id;
                }
                setCapital(topRight, 2, 30);
            }
            if (level.opponents >= 3) {
                int bottomLeft = 0;
                float lowestY = -1;
                for (Territory territory : territories) {
                    if (territory.y > lowestY) { lowestY = territory.y; bottomLeft = territory.id; }
                }
                setCapital(bottomLeft, 3, 30);
            }
        }
        for (int owner = 1; owner < aiTimers.length; owner++) aiTimers[owner] = 2.8f + owner * .5f;
    }

    private void setCapital(int index, int owner, int count) {
        Territory territory = territories.get(index);
        territory.owner = owner; territory.troops = count; territory.capital = true; territory.originalOwner = owner;
    }

    public Level level() { return LEVELS[levelIndex]; }

    public int originalKing(int owner) {
        for (Territory tile : territories) if (tile.capital && tile.originalOwner == owner && owner != NEUTRAL) return tile.id;
        return -1;
    }

    public int personality(int owner) {
        if (aiVersion == 0 || owner <= PLAYER || owner > level().opponents) return CLASSIC;
        int faction = level().faction(owner);
        return faction == 1 || faction == 3 ? PRESSURE : GUARDIAN;
    }

    public static String styleName(int style) {
        return style == PRESSURE ? "Pressure" : style == GUARDIAN ? "Guardian" : "Classic";
    }

    public ArrayList<BattleEvent> drainEvents() {
        ArrayList<BattleEvent> result = new ArrayList<>(events); events.clear(); return result;
    }

    private static int addCounter(int value,int amount) { return (int)Math.min(Integer.MAX_VALUE,(long)value+amount); }

    private void event(int type,int tile,int units) {
        for (int i = 0; i < events.size(); i++) {
            BattleEvent previous = events.get(i);
            if (previous.type == type && previous.tile == tile) {
                events.set(i,new BattleEvent(type,tile,addCounter(previous.units,units))); return;
            }
        }
        if (events.size() < MAX_EVENTS) events.add(new BattleEvent(type,tile,units));
    }

    public void configureChallenge(int type,int target,float seconds,int budget) {
        if (elapsed != 0 || outcome != PLAYING || captures != 0 || unitsLost != 0 || unitsSent != 0
            || intercepted != 0 || cappedReinforcements != 0 || startingKingLost || !troops.isEmpty()
            || objectiveType != CAMPAIGN || rulesVersion != RULES_VERSION || !historyKnown || !seedKnown)
            throw new IllegalStateException("Challenge requires a fresh battle");
        for (boolean surrendered : resigned) if (surrendered) throw new IllegalStateException("Challenge requires a fresh battle");
        if (!validObjective(type,target,seconds,budget) || type == CAMPAIGN
            || type == HOLD_KING && territories.get(target).owner <= PLAYER
            || territories.get(originalKing(PLAYER)).owner != PLAYER)
            throw new IllegalArgumentException("Invalid challenge");
        objectiveType = type; objectiveTarget = target; objectiveSeconds = seconds;
        deploymentBudget = budget; objectiveProgress = 0;
    }

    private boolean validObjective(int type,int target,float seconds,int budget) {
        if (!Float.isFinite(seconds)) return false;
        if (type == CAMPAIGN) return target == -1 && seconds == 0 && budget == 0;
        if (type == BUDGET) return (target == -1 || target == originalKing(PLAYER)) && seconds == 0 && budget > 0;
        if (type != HOLD_KING && type != KEEP_KING || seconds <= 0 || seconds > 86400 || budget != 0) return false;
        if (type == KEEP_KING) return target == -1 || target == originalKing(PLAYER);
        return target >= 0 && target < territories.size() && territories.get(target).capital
            && territories.get(target).originalOwner > PLAYER;
    }

    public int launch(int sourceId, int targetId, double fraction) {
        if (outcome != PLAYING || sourceId < 0 || targetId < 0 || sourceId == targetId
            || sourceId >= territories.size() || targetId >= territories.size()
            || !Double.isFinite(fraction) || fraction <= 0 || fraction > 1) return 0;
        Territory source = territories.get(sourceId), target = territories.get(targetId);
        if (source.owner == NEUTRAL || source.count() < 2) return 0;
        int amount = (int) Math.floor(source.count() * fraction);
        int slots = MAX_CONVOYS - troops.size();
        if (amount < 1 || slots <= 0) return 0;
        source.troops -= amount;
        float distance = distance(source, target);
        float duration = .3f + distance / 2.6f;
        // Large armies share visual packets without losing units to the particle budget.
        int packets = Math.min(amount,slots), remaining = amount;
        for (int i = 0; i < packets; i++) {
            Troop troop = new Troop(sourceId,targetId,source.owner,duration,-i*.022f);
            troop.units = (remaining+packets-i-1)/(packets-i); remaining -= troop.units;
            troops.add(troop);
        }
        if (source.owner == PLAYER) {
            boolean overBudget = objectiveType == BUDGET && (long)unitsSent+amount > deploymentBudget;
            unitsSent = addCounter(unitsSent,amount);
            if (overBudget) outcome = LOST;
        }
        return amount;
    }

    public void update(float seconds) {
        if (outcome != PLAYING || !Float.isFinite(seconds) || seconds <= 0) return;
        float dt = Math.min(seconds, .1f);
        heldSecondsThisTick = objectiveType == HOLD_KING && territories.get(objectiveTarget).owner == PLAYER ? dt : 0;
        elapsed += dt;
        for (int i = clashes.size()-1; i >= 0; i--) {
            clashes.get(i).remaining -= dt;
            if (clashes.get(i).remaining <= 0) clashes.remove(i);
        }
        for (Territory territory : territories) {
            if (territory.owner != NEUTRAL) {
                territory.troops = Math.min(troopCap(territory),territory.troops+dt*productionRate(territory));
            }
        }
        intercept(dt);
        for (int i = troops.size() - 1; i >= 0; i--) {
            Troop troop = troops.get(i);
            if (troop.units == 0) { troops.remove(i); continue; }
            troop.age += dt;
            if (troop.age >= troop.duration) {
                arrive(troop);
                troops.remove(i);
            }
        }
        checkResignation(dt);
        checkOutcome();
        if (outcome != PLAYING) return;
        for (int owner = 1; owner <= level().opponents; owner++) {
            if (resigned[owner]) continue;
            aiTimers[owner] -= dt;
            if (aiTimers[owner] <= 0) {
                playAi(owner);
                aiTimers[owner] = new float[] {2.5f, 1.4f, .8f}[difficulty] + random.nextFloat() * .6f;
            }
        }
    }

    private void arrive(Troop troop) {
        Territory target = territories.get(troop.target);
        if (target.owner == troop.owner) {
            capArrival(target,target.troops+troop.units);
        } else {
            int defenders = target.count(), lost = Math.min(defenders,troop.units);
            if (troop.owner == PLAYER || target.owner == PLAYER) unitsLost = addCounter(unitsLost,lost);
            if (troop.units <= defenders) target.troops -= troop.units;
            else {
                transfer(target,troop.owner,troop.units-defenders,Math.min(.1f,Math.max(0,troop.age-troop.duration)));
                capArrival(target,troop.units-defenders);
            }
        }
    }

    private void capArrival(Territory target,double total) {
        int discarded = (int)Math.floor(Math.max(0,total-troopCap(target))+.00001);
        target.troops = Math.min(troopCap(target),total);
        if (target.owner == PLAYER && discarded > 0) {
            cappedReinforcements = addCounter(cappedReinforcements,discarded);
            event(CAP_LOSS_EVENT,target.id,discarded);
        }
    }

    private void transfer(Territory target,int owner,int units,float heldSeconds) {
        int previous = target.owner;
        if (previous == owner) return;
        if (objectiveType == HOLD_KING && target.id == objectiveTarget) {
            objectiveProgress = 0; heldSecondsThisTick = owner == PLAYER ? heldSeconds : 0;
        }
        if (previous == PLAYER && target.capital) {
            if (target.originalOwner == PLAYER) {
                startingKingLost = true; event(HOME_KING_LOSS_EVENT,target.id,1);
            } else event(KING_LOSS_EVENT,target.id,1);
        }
        target.owner = owner;
        if (owner == PLAYER) {
            captures = addCounter(captures,1); event(CAPTURE_EVENT,target.id,units);
            if (target.capital && target.originalOwner != PLAYER) event(KING_GAIN_EVENT,target.id,1);
        }
    }

    private void intercept(float dt) {
        collisions.clear();
        for (int a = 0; a < troops.size(); a++) {
            Troop first = troops.get(a);
            if (first.age+dt <= 0 || first.age >= first.duration) continue;
            for (int b = a+1; b < troops.size(); b++) {
                Troop second = troops.get(b);
                if (first.owner == second.owner || second.age+dt <= 0 || second.age >= second.duration) continue;
                float time = collisionTime(first,second,dt);
                if (time >= 0) collisions.add(new Collision(a,b,time));
            }
        }
        collisions.sort((a,b) -> Float.compare(a.time,b.time));
        for (Collision collision : collisions) {
            Troop a = troops.get(collision.a), b = troops.get(collision.b);
            int canceled = Math.min(a.units,b.units);
            if (canceled == 0) continue;
            a.units -= canceled; b.units -= canceled;
            if (a.owner == PLAYER || b.owner == PLAYER) {
                unitsLost = addCounter(unitsLost,canceled); intercepted = addCounter(intercepted,canceled);
                event(INTERCEPT_EVENT,a.owner == PLAYER ? a.target : b.target,canceled);
            }
            if (clashes.size() < 48) {
                Territory source = territories.get(a.source), target = territories.get(a.target);
                float p = (a.age+collision.time)/a.duration;
                clashes.add(new Clash(source.x+(target.x-source.x)*p,source.y+(target.y-source.y)*p));
            }
        }
    }

    private float collisionTime(Troop a,Troop b,float dt) {
        // Swept collision tests prevent fast troops skipping each other between frames.
        float start = Math.max(0,Math.max(a.duration*.04f-a.age,b.duration*.04f-b.age));
        float end = Math.min(dt,Math.min(a.duration*.96f-a.age,b.duration*.96f-b.age));
        if (end < start) return -1;
        Territory as = territories.get(a.source), at = territories.get(a.target);
        Territory bs = territories.get(b.source), bt = territories.get(b.target);
        float avx = (at.x-as.x)/a.duration, avy = (at.y-as.y)/a.duration;
        float bvx = (bt.x-bs.x)/b.duration, bvy = (bt.y-bs.y)/b.duration;
        float rx = as.x+avx*(a.age+start)-bs.x-bvx*(b.age+start);
        float ry = as.y+avy*(a.age+start)-bs.y-bvy*(b.age+start);
        float vx = avx-bvx, vy = avy-bvy, window = end-start, radius = .14f;
        if (Math.min(rx,rx+vx*window) > radius || Math.max(rx,rx+vx*window) < -radius
            || Math.min(ry,ry+vy*window) > radius || Math.max(ry,ry+vy*window) < -radius) return -1;
        float c = rx*rx+ry*ry-radius*radius;
        if (c <= 0) return start;
        float speed = vx*vx+vy*vy;
        if (speed < .000001f) return -1;
        float dot = rx*vx+ry*vy, discriminant = dot*dot-speed*c;
        if (discriminant < 0) return -1;
        float time = (-dot-(float)Math.sqrt(discriminant))/speed;
        return time >= 0 && time <= window ? start+time : -1;
    }

    public static int troopCap(Territory territory) { return territory.capital ? MAX_TROOPS : NORMAL_CAP; }

    public int capturedKings(int owner) {
        int count = 0;
        for (Territory territory : territories)
            if (territory.capital && territory.owner == owner && territory.originalOwner != NEUTRAL && territory.originalOwner != owner) count++;
        return count;
    }

    public double teamMultiplier(int owner) {
        int kings = capturedKings(owner);
        return kings < 4 ? Math.pow(1.2,kings) : 3*Math.pow(1.2,kings-4);
    }

    private void checkResignation(float dt) {
        if (owned(PLAYER)*10 <= territories.size()*9) { dominanceSeconds = 0; return; }
        dominanceSeconds = Math.min(10,dominanceSeconds+dt);
        if (dominanceSeconds < 9.999f) return;
        for (int owner = 1; owner <= level().opponents; owner++) {
            if (resigned[owner] || army(owner) == 0 && owned(owner) == 0 || canRecapture(owner)) continue;
            resigned[owner] = true;
            for (Territory territory : territories) if (territory.owner == owner) {
                transfer(territory,PLAYER,territory.count(),0);
            }
            for (int i = troops.size()-1; i >= 0; i--) if (troops.get(i).owner == owner) troops.remove(i);
        }
    }

    private boolean canRecapture(int owner) {
        // Count every remaining unit, including convoys, as potentially usable support.
        int remaining = army(owner);
        for (Territory territory : territories) if (territory.owner == PLAYER) {
            int exposed = Math.max(0,territory.count()-incoming(territory.id,PLAYER,false));
            if (remaining > exposed) return true;
        }
        return false;
    }

    private void checkOutcome() {
        boolean playerAlive = false, enemiesAlive = false;
        for (Territory territory : territories) {
            if (territory.owner == PLAYER) playerAlive = true;
            if (territory.owner > PLAYER) enemiesAlive = true;
        }
        for (Troop troop : troops) {
            if (troop.owner == PLAYER) playerAlive = true;
            if (troop.owner > PLAYER) enemiesAlive = true;
        }
        if (objectiveType == HOLD_KING) {
            if (territories.get(objectiveTarget).owner != PLAYER) objectiveProgress = 0;
            else objectiveProgress = Math.min(objectiveSeconds,objectiveProgress+heldSecondsThisTick);
        } else if (objectiveType == KEEP_KING) {
            objectiveProgress = Math.min(objectiveSeconds,elapsed);
            if (territories.get(originalKing(PLAYER)).owner != PLAYER) startingKingLost = true;
        }
        if (!playerAlive || objectiveType == KEEP_KING && startingKingLost
            || objectiveType == BUDGET && unitsSent > deploymentBudget) outcome = LOST;
        else if (objectiveType == HOLD_KING || objectiveType == KEEP_KING) {
            if (objectiveProgress >= objectiveSeconds) outcome = WON;
        } else if (!enemiesAlive) outcome = WON;
    }

    private void playAi(int owner) {
        Territory bestSource = null, bestTarget = null;
        int bestAmount = 0;
        int[] available = new int[territories.size()];
        for (Territory source : territories) if (source.owner == owner)
            available[source.id] = Math.max(0,source.count()-defensiveReserve(source,owner));
        double bestScore = -Double.MAX_VALUE;
        for (Territory source : territories) {
            int capacity = troops.size() < MAX_CONVOYS ? available[source.id] : 0;
            if (source.owner != owner || capacity < 2) continue;
            for (Territory target : territories) {
                if (target.id == source.id) continue;
                double score;
                int amount;
                float distance = distance(source, target);
                float travel = .3f + distance/2.6f;
                if (target.owner == owner) {
                    int threat = incoming(target.id,owner,false);
                    int shortage = threat-target.count()-incomingBefore(target.id,owner,true,firstArrival(target.id,owner,false));
                    if (shortage < 0 || threat == 0 || travel > lastArrival(target.id,owner,false)) continue;
                    amount = Math.min(capacity,shortage+4);
                    score = 70 + Math.min(30,shortage) - distance*4;
                    if (personality(owner) == GUARDIAN && target.capital) score += 32;
                } else {
                    amount = captureBudget(target,owner,travel);
                    if (amount <= 0 || amount > capacity) continue;
                    score = attackScore(target,owner,amount,distance);
                }
                if (score > bestScore) { bestScore = score; bestSource = source; bestTarget = target; bestAmount = amount; }
            }
        }
        if (personality(owner) != CLASSIC && difficulty > 0 && coordinateAttack(owner,available,bestScore,true)) return;
        if (bestSource != null) {
            launch(bestSource.id,bestTarget.id,(bestAmount+.01)/bestSource.count());
        } else if (difficulty > 0) coordinateAttack(owner,available);
    }

    private int defensiveReserve(Territory source, int owner) {
        int style = personality(owner);
        int minimum = (source.capital ? 8 : 4)+difficulty*2;
        if (style == GUARDIAN && source.capital) minimum += 6;
        double fraction = style == PRESSURE ? .12 : style == GUARDIAN ? .28 : .18;
        int reserve = Math.max(minimum,(int)Math.ceil(source.count()*(fraction+difficulty*.04)));
        int hostile = incoming(source.id,owner,false);
        int timely = incomingBefore(source.id,owner,true,firstArrival(source.id,owner,false));
        reserve = Math.max(reserve,hostile-timely+3);
        for (Territory enemy : territories) {
            if (enemy.owner == NEUTRAL || enemy.owner == owner) continue;
            double pressure = enemy.count()*(.22+difficulty*.09)/(1+distance(source,enemy)*.35);
            reserve = Math.max(reserve,(int)Math.ceil(pressure));
        }
        return reserve;
    }

    private int captureBudget(Territory target, int owner, float travel) {
        double rate = target.owner == NEUTRAL ? 0 : productionRate(target);
        int margin = difficulty == 0 ? 7 : difficulty == 1 ? 5 : 4;
        double defense = Math.min(troopCap(target),target.troops+rate*travel);
        // Defenders also grow while a staggered volley is arriving, not just during travel.
        double divisor = 1-rate*.022;
        int amount = (int)Math.ceil((defense+margin)/divisor);
        float deadline = travel+Math.max(0,amount-1)*.022f;
        // Only credit armies arriving in this attack's window, not distant future support.
        for (int pass = 0; pass < 3; pass++) {
            int reinforcements = target.owner == NEUTRAL ? 0 : incomingBefore(target.id,target.owner,true,deadline);
            int arriving = incomingBefore(target.id,owner,true,deadline);
            amount = Math.max(0,(int)Math.ceil((defense+reinforcements+margin-arriving)/divisor));
            deadline = Math.max(deadline,travel+Math.max(0,amount-1)*.022f);
        }
        return amount;
    }

    private double attackScore(Territory target, int owner, int amount, float distance) {
        double preference = target.owner == NEUTRAL ? 7-difficulty*3 : difficulty*3;
        double variation = random.nextDouble()*(difficulty == 0 ? 6 : difficulty == 1 ? 3 : 1.5);
        double recovery = difficulty > 0 && target.capital && target.originalOwner == owner ? (difficulty == 1 ? 20 : 34) : 0;
        int style = personality(owner);
        double priority = 0;
        if (target.capital) {
            if (style == PRESSURE && target.originalOwner != owner) priority = 22+(target.owner == PLAYER ? 8 : 0);
            if (style == GUARDIAN && target.originalOwner == owner) priority = 40;
        }
        return 26+preference+(target.capital ? 2 : 0)+recovery-amount*.32-distance*4+variation+priority;
    }

    private void coordinateAttack(int owner, int[] available) {
        coordinateAttack(owner,available,-Double.MAX_VALUE,false);
    }

    private boolean coordinateAttack(int owner,int[] available,double minimumScore,boolean priorityOnly) {
        Territory targetChoice = null;
        double bestScore = -Double.MAX_VALUE;
        int bestAmount = 0, bestCapacity = 0;
        ArrayList<Territory> bestSources = null;
        ArrayList<Territory> attackers = new ArrayList<>();
        for (Territory source : territories) if (source.owner == owner && available[source.id] >= 5) attackers.add(source);
        if (attackers.size() < 2) return false;
        for (Territory target : territories) {
            if (target.owner == owner) continue;
            if (priorityOnly && (!target.capital || personality(owner) == GUARDIAN && target.originalOwner != owner)) continue;
            attackers.sort((a,b) -> Float.compare(distance(a,target),distance(b,target)));
            int capacity = 0, amount = 0;
            float travel = 0, totalDistance = 0;
            ArrayList<Territory> sources = new ArrayList<>();
            for (Territory source : attackers) {
                sources.add(source); capacity += available[source.id];
                float distance = distance(source,target);
                totalDistance += distance; travel = Math.max(travel,.3f+distance/2.6f);
                amount = captureBudget(target,owner,travel);
                if (amount <= 0 || capacity >= amount) break;
            }
            if (sources.size() < 2 || amount <= 0 || capacity < amount || troops.size() >= MAX_CONVOYS) continue;
            double score = attackScore(target,owner,amount,totalDistance/sources.size())-2;
            if (priorityOnly) score += personality(owner) == PRESSURE ? 16 : 26;
            if (score > bestScore) {
                bestScore = score; targetChoice = target; bestSources = sources;
                bestAmount = amount; bestCapacity = capacity;
            }
        }
        if (targetChoice == null || bestScore <= minimumScore) return false;
        for (Territory source : bestSources) {
            int capacity = available[source.id];
            int amount = Math.min(capacity,(int)Math.ceil((double)bestAmount*capacity/bestCapacity));
            if (amount > 0) bestAmount -= launch(source.id,targetChoice.id,(amount+.01)/source.count());
            bestCapacity -= capacity;
            if (bestAmount <= 0) break;
        }
        return true;
    }

    private float lastArrival(int target, int owner, boolean friendly) {
        float latest = 0;
        for (Troop troop : troops) if (troop.target == target && (troop.owner == owner) == friendly)
            latest = Math.max(latest,troop.duration-troop.age);
        return latest;
    }

    private float firstArrival(int target, int owner, boolean friendly) {
        float earliest = Float.MAX_VALUE;
        for (Troop troop : troops) if (troop.target == target && (troop.owner == owner) == friendly)
            earliest = Math.min(earliest,troop.duration-troop.age);
        return earliest;
    }

    private int incomingBefore(int target, int owner, boolean friendly, float deadline) {
        int count = 0;
        for (Troop troop : troops) if (troop.target == target && (troop.owner == owner) == friendly
            && troop.duration-troop.age <= deadline+.0001f) count += troop.units;
        return count;
    }

    private double productionRate(Territory territory) {
        return (territory.capital ? 2.2 : 1.35)*teamMultiplier(territory.owner);
    }

    private int incoming(int target, int owner, boolean friendly) {
        int result = 0;
        for (Troop troop : troops) if (troop.target == target && (troop.owner == owner) == friendly) result += troop.units;
        return result;
    }

    private static float distance(Territory a, Territory b) {
        return (float) Math.hypot(a.x - b.x, a.y - b.y);
    }

    public int owned(int owner) {
        int result = 0;
        for (Territory territory : territories) if (territory.owner == owner) result++;
        return result;
    }

    public int army(int owner) {
        int result = 0;
        for (Territory territory : territories) if (territory.owner == owner) result += territory.count();
        for (Troop troop : troops) if (troop.owner == owner) result += troop.units;
        return result;
    }

    public int score() {
        if (outcome != WON) return 0;
        return 1000 + captures * 50 + Math.max(0, 1200 - (int) elapsed * 6) + difficulty * 250;
    }

    public int stars() {
        if (outcome != WON) return 0;
        if (elapsed <= level().parSeconds) return 3;
        if (elapsed <= level().parSeconds * 1.6f) return 2;
        return 1;
    }

    public byte[] save() throws IOException {
        if (dailyDate == null) throw new IOException("Invalid challenge identity");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x464C3034); out.writeInt(levelIndex); out.writeInt(difficulty);
        out.writeFloat(elapsed); out.writeInt(outcome); out.writeInt(captures);
        out.writeInt(unitsLost); out.writeInt(unitsSent);
        out.writeInt(territories.size());
        for (Territory territory : territories) { out.writeInt(territory.owner); out.writeDouble(territory.troops); }
        out.writeInt(troops.size());
        for (Troop troop : troops) {
            out.writeInt(troop.source); out.writeInt(troop.target); out.writeInt(troop.owner);
            out.writeFloat(troop.duration); out.writeFloat(troop.age);
            out.writeInt(troop.units);
        }
        for (float timer : aiTimers) out.writeFloat(timer);
        out.writeFloat(dominanceSeconds);
        for (boolean surrendered : resigned) out.writeBoolean(surrendered);
        out.writeLong(seed); out.writeBoolean(seedKnown); out.writeBoolean(historyKnown); out.writeBoolean(startingKingLost);
        out.writeInt(rulesVersion); out.writeInt(aiVersion); out.writeInt(intercepted); out.writeInt(cappedReinforcements);
        out.writeInt(objectiveType); out.writeInt(objectiveTarget); out.writeFloat(objectiveSeconds); out.writeFloat(objectiveProgress);
        out.writeInt(deploymentBudget); out.writeInt(challengeId); out.writeUTF(dailyDate);
        out.writeLong(random.state);
        out.flush(); return bytes.toByteArray();
    }

    public static GameModel restore(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > 50000) throw new IOException("Invalid save size");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int version = in.readInt();
        if (version != 0x464C3031 && version != 0x464C3032 && version != 0x464C3033 && version != 0x464C3034) throw new IOException("Unknown save version");
        int level = in.readInt(), difficulty = in.readInt();
        if (level < 0 || level >= LEVELS.length || version < 0x464C3033 && level >= 30 || difficulty < 0 || difficulty > 2) throw new IOException("Invalid level");
        GameModel model = new GameModel(level, difficulty, 100 + level);
        model.elapsed = in.readFloat(); model.outcome = in.readInt(); model.captures = in.readInt();
        model.unitsLost = in.readInt(); model.unitsSent = in.readInt();
        if (!Float.isFinite(model.elapsed) || model.elapsed < 0 || model.elapsed > 86400
            || model.outcome < 0 || model.outcome > 2 || model.captures < 0 || model.unitsLost < 0 || model.unitsSent < 0) throw new IOException("Invalid battle");
        if (in.readInt() != model.territories.size()) throw new IOException("Invalid map");
        for (Territory territory : model.territories) {
            territory.owner = in.readInt(); territory.troops = in.readDouble();
            if (territory.owner < NEUTRAL || territory.owner > model.level().opponents
                || !Double.isFinite(territory.troops) || territory.troops < 0
                || territory.troops > (version == 0x464C3031 ? 99 : version == 0x464C3032 ? LEGACY_MAX_TROOPS : troopCap(territory))) throw new IOException("Invalid territory");
            territory.troops = Math.min(troopCap(territory),territory.troops);
        }
        int count = in.readInt();
        if (count < 0 || count > MAX_CONVOYS) throw new IOException("Invalid troop count");
        for (int i = 0; i < count; i++) {
            int source = in.readInt(), target = in.readInt(), owner = in.readInt();
            float duration = in.readFloat(), age = in.readFloat();
            int units = version == 0x464C3031 ? 1 : in.readInt();
            if (source < 0 || target < 0 || source >= model.territories.size() || target >= model.territories.size()
                || owner < PLAYER || owner > model.level().opponents || !Float.isFinite(duration) || duration < .01f || duration > 30
                || !Float.isFinite(age) || age < -30 || age > duration || units < 1 || units > LEGACY_MAX_TROOPS) throw new IOException("Invalid convoy");
            Troop troop = new Troop(source,target,owner,duration,age); troop.units = units; model.troops.add(troop);
        }
        for (int i = 0; i < (version >= 0x464C3033 ? MAX_TEAMS : 4); i++) {
            model.aiTimers[i] = in.readFloat();
            if (!Float.isFinite(model.aiTimers[i]) || model.aiTimers[i] < 0 || model.aiTimers[i] > 10) throw new IOException("Invalid AI timer");
        }
        if (version >= 0x464C3033) {
            model.dominanceSeconds = in.readFloat();
            if (!Float.isFinite(model.dominanceSeconds) || model.dominanceSeconds < 0 || model.dominanceSeconds > 10) throw new IOException("Invalid dominance timer");
            for (int i = 0; i < MAX_TEAMS; i++) {
                int flag = in.readUnsignedByte();
                if (flag > 1 || flag == 1 && (i == PLAYER || i > model.level().opponents || model.owned(i) > 0 || model.army(i) > 0)) throw new IOException("Invalid resignation");
                model.resigned[i] = flag == 1;
            }
        }
        if (version == 0x464C3034) {
            model.seed = in.readLong(); model.seedKnown = readFlag(in); model.historyKnown = readFlag(in);
            model.startingKingLost = readFlag(in); model.rulesVersion = in.readInt(); model.aiVersion = in.readInt();
            model.intercepted = in.readInt(); model.cappedReinforcements = in.readInt();
            model.objectiveType = in.readInt(); model.objectiveTarget = in.readInt();
            model.objectiveSeconds = in.readFloat(); model.objectiveProgress = in.readFloat();
            model.deploymentBudget = in.readInt(); model.challengeId = in.readInt(); model.dailyDate = in.readUTF();
            long state = in.readLong();
            if (state < 0 || state > StatefulRandom.MASK) throw new IOException("Invalid random state");
            model.random.restoreState(state);
            model.validateV10();
        } else {
            model.seed = 0; model.seedKnown = false; model.historyKnown = false;
            model.rulesVersion = 0; model.aiVersion = 0;
        }
        if (in.available() != 0) throw new IOException("Unexpected save data");
        return model;
    }

    private static boolean readFlag(DataInputStream in) throws IOException {
        int flag = in.readUnsignedByte();
        if (flag > 1) throw new IOException("Invalid boolean");
        return flag == 1;
    }

    private void validateV10() throws IOException {
        boolean timed = objectiveType == HOLD_KING || objectiveType == KEEP_KING;
        if (rulesVersion != 0 && rulesVersion != RULES_VERSION || aiVersion < 0 || aiVersion > 1
            || rulesVersion == 0 && (seedKnown || historyKnown || seed != 0 || aiVersion != 0 || objectiveType != CAMPAIGN)
            || rulesVersion == RULES_VERSION && (!seedKnown || !historyKnown)
            || intercepted < 0 || intercepted > unitsLost || cappedReinforcements < 0)
            throw new IOException("Invalid battle history");
        if (!validObjective(objectiveType,objectiveTarget,objectiveSeconds,deploymentBudget)
            || !Float.isFinite(objectiveProgress) || objectiveProgress < 0 || objectiveProgress > objectiveSeconds
            || objectiveProgress > elapsed+.0001f)
            throw new IOException("Invalid objective");
        if (objectiveType == HOLD_KING && objectiveProgress > 0 && territories.get(objectiveTarget).owner != PLAYER
            || objectiveType == KEEP_KING && (!startingKingLost && territories.get(originalKing(PLAYER)).owner != PLAYER
                || startingKingLost && outcome != LOST || objectiveProgress != Math.min(objectiveSeconds,elapsed))
            || objectiveType == BUDGET && unitsSent > deploymentBudget && outcome != LOST
            || timed && outcome == PLAYING && objectiveProgress >= objectiveSeconds
            || timed && outcome == WON && (objectiveProgress < objectiveSeconds || objectiveType == KEEP_KING && startingKingLost))
            throw new IOException("Inconsistent objective state");
        if (challengeId < -1 || challengeId >= Challenge.PRESETS.length || dailyDate == null
            || objectiveType == CAMPAIGN && (challengeId != -1 || !dailyDate.isEmpty())
            || !dailyDate.isEmpty() && (challengeId < 0 || !Challenge.validDate(dailyDate)))
            throw new IOException("Invalid challenge identity");
    }
}
