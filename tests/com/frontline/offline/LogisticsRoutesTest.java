package com.frontline.offline;

import java.util.Arrays;
import java.util.Collections;

/** Headless factory and route-preview checks; no mode activation or convoy integration. */
public final class LogisticsRoutesTest {
    private static int checks;
    private interface Action { void run(); }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " logistics factory/routing checks.");
    }

    public static int run() throws Exception {
        checks = 0;
        presets(); geometry(); friendlyAndHostile(); holes(); deterministic();
        invalid(); arrivalEstimates(); ownershipChanges(); exhaustive(); largePreview(); pure();
        return checks;
    }

    private static void presets() throws Exception {
        check(Logistics.PRESETS.length == 3 && Logistics.MODE_LOGISTICS == 2, "Three separate prototype maps");
        int[] columns = {4, 5, 6}, rows = {3, 4, 4};
        for (int id = 0; id < Logistics.PRESETS.length; id++) {
            Logistics preset = Logistics.PRESETS[id];
            check(preset.id == id && !preset.name.isEmpty() && !preset.description.isEmpty(), "Stable preset metadata");
            check(preset.level.columns == columns[id] && preset.level.rows == rows[id]
                && preset.level.opponents == id + 1 && preset.level.holes.length > 0, "Purpose-built sizes, holes, and opponent counts");
            for (int difficulty = 0; difficulty < 3; difficulty++) for (long seed : new long[] {0, 19, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                GameModel model = Logistics.create(id, difficulty, seed);
                check(model.levelIndex == 0 && model.battleMode == 2 && model.difficulty == difficulty
                    && model.seed == seed && Logistics.getIndex(model) == id, "Frozen factory identity and settings");
                check(model.level() != preset.level && model.level().name.equals("Logistics: " + preset.name), "English frozen identity, independent definition");
                check(model.territories.size() == columns[id] * rows[id] - preset.level.holes.length, "Only non-hole vertices exist");
                check(model.runPerks == 0 && model.runId.isEmpty() && model.runNode == -1
                    && model.challengeId == -1 && model.dailyDate.isEmpty() && model.objectiveType == Challenge.CAMPAIGN,
                    "No campaign mission, Daily, or Run association");
                for (int owner = 0; owner <= model.level().opponents; owner++)
                    check(model.owned(owner) == 1 && model.army(owner) == preset.level.playerTroops
                        && model.territories.get(model.originalKing(owner)).capital, "Every faction starts with one equal army at a king");
                check(preset.level.playerTroops == preset.level.rivalTroops, "Equal base armies in definition");
                check(Arrays.equals(model.save(), Logistics.create(id, difficulty, seed).save()), "Factory deterministic for matched seed");
                GameModel restored = GameModel.restore(model.save());
                check(Logistics.getIndex(restored) == id && Arrays.equals(model.save(), restored.save()), "Frozen map identity roundtrip");
                GameModel.Level snapshot = model.snapshotLevel();
                snapshot.holes[0] = -1; snapshot.startingCells[0] = -1; snapshot.factions[0] = -1;
                check(model.level().holes[0] >= 0 && model.level().startingCells[0] >= 0
                    && model.level().factions[0] > 0, "Level snapshots cannot mutate active topology");
            }
            GameModel connected = Logistics.create(id, 1, 42);
            ownAll(connected, 0);
            for (GameModel.Territory source : connected.territories)
                check(LogisticsRoutes.legalTargets(connected, source.id, 0).length == connected.territories.size() - 1,
                    "Each map stays connected around its holes");
            for (GameModel.Level campaign : GameModel.LEVELS)
                check(!campaign.name.equals(preset.level.name), "Prototype not inserted in campaign");
        }
        check(Logistics.getIndex(null) == -1 && Logistics.getIndex(new GameModel(0, 1, 42)) == -1, "Non-logistics identity refused");
        GameModel unknown = fixture(2, 1, new int[0]); unknown.battleMode = 2;
        check(Logistics.getIndex(unknown) == -1, "Unknown frozen name not guessed from index zero");
        reject(() -> Logistics.create(-1, 1, 1)); reject(() -> Logistics.create(3, 1, 1));
        reject(() -> Logistics.create(0, -1, 1)); reject(() -> Logistics.create(0, 3, 1));
    }

    private static void geometry() {
        GameModel model = fixture(3, 3, new int[0]);
        check(Arrays.equals(LogisticsRoutes.neighbors(model, 4), new int[] {1, 2, 3, 5, 7, 8}), "Six hex neighbors on an odd row");
        check(Arrays.equals(LogisticsRoutes.neighbors(model, 1), new int[] {0, 2, 3, 4}), "Top edge has four neighbors");
        check(Arrays.equals(LogisticsRoutes.neighbors(model, 7), new int[] {3, 4, 6, 8}), "Even row uses the opposite stagger");
        check(!LogisticsRoutes.adjacent(model, 0, 2) && !LogisticsRoutes.adjacent(model, 0, 6)
            && !LogisticsRoutes.adjacent(model, 0, 0), "No long, vertical-gap, or self edges");
        for (GameModel.Territory tile : model.territories) {
            int previous = -1;
            for (int neighbor : LogisticsRoutes.neighbors(model, tile.id)) {
                check(neighbor > previous && LogisticsRoutes.adjacent(model, neighbor, tile.id), "Sorted, symmetric adjacency");
                previous = neighbor;
            }
        }
    }

    private static void friendlyAndHostile() {
        GameModel model = fixture(3, 2, new int[0]); ownAll(model, 0);
        expectRoute(model, 0, 4, 0, new int[] {0, 1, 4});
        model.territories.get(4).owner = 1;
        expectRoute(model, 0, 4, 0, new int[] {0, 1, 4});
        model.territories.get(4).owner = GameModel.NEUTRAL;
        expectRoute(model, 0, 4, 0, new int[] {0, 1, 4});
        model.territories.get(1).owner = 1;
        expectRoute(model, 0, 4, 0, new int[] {0, 3, 4});
        model.territories.get(3).owner = GameModel.NEUTRAL;
        check(LogisticsRoutes.getRoute(model, 0, 4, 0) == null, "Cannot cross enemy or neutral interior");
        check(Arrays.equals(LogisticsRoutes.legalTargets(model, 0, 0), new int[] {1, 3}), "Only adjacent hostile or neutral endpoints legal");
        model.territories.get(4).owner = 0;
        check(LogisticsRoutes.getRoute(model, 0, 4, 0) == null, "Disconnected friendly target cannot be reinforced");
        model.territories.get(4).owner = 1; model.territories.get(2).owner = 1;
        expectRoute(model, 2, 0, 1, new int[] {2, 1, 0});
        check(LogisticsRoutes.getRoute(model, 2, 5, 0) == null, "AI and player must own their source");
    }

    private static void holes() {
        GameModel gap = fixture(3, 1, new int[] {1}); ownAll(gap, 0);
        check(gap.territories.size() == 2 && !LogisticsRoutes.adjacent(gap, 0, 1), "Dense neighboring IDs do not bridge a hole");
        check(LogisticsRoutes.getRoute(gap, 0, 1, 0) == null && LogisticsRoutes.legalTargets(gap, 0, 0).length == 0,
            "Hole makes reinforcement impossible");
        gap.territories.get(1).owner = 1;
        check(LogisticsRoutes.getRoute(gap, 0, 1, 0) == null && Float.isInfinite(LogisticsRoutes.eta(gap, 0, 1, 0)),
            "Attack and ETA cannot bridge a missing tile");
        GameModel detour = fixture(3, 2, new int[] {1}); ownAll(detour, 0);
        expectRoute(detour, 0, 1, 0, new int[] {0, 2, 3, 1});
        detour.territories.get(3).owner = 1;
        check(LogisticsRoutes.getRoute(detour, 0, 1, 0) == null, "Enemy on the only corridor blocks distant friendly reinforcement");
    }

    private static void deterministic() {
        GameModel model = fixture(3, 2, new int[0]); ownAll(model, 0);
        int[] expected = {0, 1, 4};
        for (int i = 0; i < 30; i++) expectRoute(model, 0, 4, 0, expected);
        model.territories.get(4).owner = 1;
        Collections.reverse(model.territories);
        expectRoute(model, 0, 4, 0, expected);
        check(Arrays.equals(LogisticsRoutes.neighbors(model, 0), new int[] {1, 3}), "ID order is independent of list order");
        check(Arrays.equals(LogisticsRoutes.legalTargets(model, 0, 0), new int[] {1, 2, 3, 4, 5}), "Endpoint IDs remain sorted after list reordering");
        Collections.reverse(model.territories);
        int[] route = LogisticsRoutes.getRoute(model, 0, 4, 0); route[1] = 99;
        int[] neighbors = LogisticsRoutes.neighbors(model, 0); neighbors[0] = 99;
        int[] targets = LogisticsRoutes.legalTargets(model, 0, 0); targets[0] = 99;
        expectRoute(model, 0, 4, 0, expected);
        check(LogisticsRoutes.neighbors(model, 0)[0] == 1 && LogisticsRoutes.legalTargets(model, 0, 0)[0] == 1,
            "Returned arrays are not shared mutable caches");
    }

    private static void invalid() {
        GameModel model = fixture(3, 2, new int[0]); ownAll(model, 0);
        for (int[] input : new int[][] {{-1, 1, 0}, {0, -1, 0}, {6, 1, 0}, {0, 6, 0},
            {0, 0, 0}, {0, 1, -1}, {0, 1, 2}, {0, 1, 1}}) {
            check(LogisticsRoutes.getRoute(model, input[0], input[1], input[2]) == null, "Invalid route refused");
            check(Float.isInfinite(LogisticsRoutes.eta(model, input[0], input[1], input[2])), "Invalid ETA unavailable");
        }
        check(LogisticsRoutes.getRoute(null, 0, 1, 0) == null && LogisticsRoutes.legalTargets(null, 0, 0).length == 0
            && LogisticsRoutes.neighbors(null, 0).length == 0 && !LogisticsRoutes.adjacent(null, 0, 1), "Null model handled without mutation or exception");
        check(LogisticsRoutes.neighbors(model, -1).length == 0 && LogisticsRoutes.neighbors(model, 6).length == 0
            && !LogisticsRoutes.adjacent(model, -1, 0) && !LogisticsRoutes.adjacent(model, 0, 6), "Invalid geometry IDs rejected");
        for (int owner : new int[] {-1, 1, 2})
            check(LogisticsRoutes.legalTargets(model, 0, owner).length == 0, "Neutral, foreign, and unknown owners cannot dispatch");
        model.territories.get(0).owner = GameModel.NEUTRAL;
        check(LogisticsRoutes.getRoute(model, 0, 1, -1) == null, "Neutral source cannot dispatch");
        model.territories.get(0).owner = 0; model.territories.get(1).owner = 7;
        check(LogisticsRoutes.getRoute(model, 0, 1, 0) == null
            && !contains(LogisticsRoutes.legalTargets(model, 0, 0), 1), "Malformed endpoint ownership rejected");
    }

    private static void arrivalEstimates() {
        GameModel model = fixture(3, 2, new int[0]); ownAll(model, 0);
        int[] route = LogisticsRoutes.getRoute(model, 0, 4, 0);
        float expected = hop(model, 0, 1) + hop(model, 1, 4);
        check(LogisticsRoutes.eta(model, route) == expected && LogisticsRoutes.eta(model, 0, 4, 0) == expected,
            "ETA sums the actual hop durations");
        check(LogisticsRoutes.eta(model, 0, 4, 0) > hop(model, 0, 4), "Per-hop delay included, not straight-line travel");
        model.territories.get(0).troops = 125;
        GameModel.Troop delayed = new GameModel.Troop(0, 1, 0, hop(model, 0, 1), -10);
        model.troops.add(delayed); model.unitsSent = 1;
        check(LogisticsRoutes.eta(model, route) == expected, "Packet count and launch delays excluded from travel ETA");
        for (int[] illegal : new int[][] {new int[0], {0}, {0, 0}, {0, 4}, {0, 99}, {0, 1, 0}})
            check(Float.isInfinite(LogisticsRoutes.eta(model, illegal)), "Invalid ETA route refused");
        check(Float.isInfinite(LogisticsRoutes.eta(model, null)) && Float.isInfinite(LogisticsRoutes.eta(null, route)), "Absent ETA unavailable");
        model.territories.get(1).owner = 1;
        check(Float.isInfinite(LogisticsRoutes.eta(model, route)), "Stale preview ETA refuses enemy interior");
        model.territories.get(1).owner = GameModel.NEUTRAL;
        check(Float.isInfinite(LogisticsRoutes.eta(model, route)), "Stale preview ETA refuses neutral interior");
        model.territories.get(1).owner = 0; model.territories.get(4).owner = GameModel.NEUTRAL;
        check(LogisticsRoutes.eta(model, route) == expected, "Neutral final hop remains a legal attack ETA");
        model.territories.get(4).owner = 1;
        check(LogisticsRoutes.eta(model, route) == expected, "Enemy final hop remains a legal attack ETA");
    }

    private static void ownershipChanges() {
        GameModel model = fixture(3, 2, new int[0]); ownAll(model, 0);
        expectRoute(model, 0, 4, 0, new int[] {0, 1, 4});
        model.territories.get(1).owner = 1;
        expectRoute(model, 0, 4, 0, new int[] {0, 3, 4});
        model.territories.get(3).owner = 1;
        check(LogisticsRoutes.getRoute(model, 0, 4, 0) == null, "Preview reacts immediately to loss of transit ownership");
        model.territories.get(1).owner = 0;
        expectRoute(model, 0, 4, 0, new int[] {0, 1, 4});
    }

    private static void exhaustive() {
        GameModel model = fixture(3, 2, new int[0]);
        int states = 1;
        for (int i = 0; i < model.territories.size(); i++) states *= 3;
        for (int state = 0; state < states; state++) {
            int value = state;
            for (GameModel.Territory tile : model.territories) { tile.owner = value % 3 - 1; value /= 3; }
            for (int owner = 0; owner <= 1; owner++) for (GameModel.Territory source : model.territories) {
                int[] targets = LogisticsRoutes.legalTargets(model, source.id, owner);
                int previous = -1;
                for (int target : targets) { check(target > previous, "Exhaustive legal target ordering"); previous = target; }
                for (GameModel.Territory target : model.territories) {
                    int[] route = LogisticsRoutes.getRoute(model, source.id, target.id, owner);
                    int distance = oracleDistance(model, source.id, target.id, owner);
                    check((route == null) == (distance < 0), "Exhaustive route legality matches independent graph oracle");
                    check(contains(targets, target.id) == (distance >= 0), "Legal target set matches route query");
                    if (route != null) {
                        check(route.length == distance + 1, "Exhaustive route uses minimum hop count");
                        validRoute(model, route, owner);
                    }
                }
            }
        }
    }

    private static void largePreview() {
        for (GameModel model : new GameModel[] {Logistics.create(2, 2, 73), new GameModel(59, 2, 73)}) {
            ownAll(model, 0);
            int last = model.territories.size() - 1;
            model.territories.get(last).owner = 1;
            int[] preview = LogisticsRoutes.getRoute(model, 0, last, 0);
            check(preview != null && preview.length > 4, "Large map preview spans multiple hex hops");
            validRoute(model, preview, 0);
            check(preview.length == oracleDistance(model, 0, last, 0) + 1, "Large map preview is shortest around holes");
            check(LogisticsRoutes.legalTargets(model, 0, 0).length == model.territories.size() - 1,
                "Large preview exposes connected reinforcement and attack endpoints");
            float expected = 0;
            for (int i = 1; i < preview.length; i++) expected += hop(model, preview[i - 1], preview[i]);
            check(LogisticsRoutes.eta(model, preview) == expected, "Large preview ETA uses every actual edge");
            check(model.battleMode == (model.levelIndex == 59 ? GameModel.MODE_CAMPAIGN : 2), "Pure preview never activates another mode");
        }
    }

    private static void pure() throws Exception {
        for (int id = 0; id < 3; id++) {
            GameModel model = Logistics.create(id, 1, 99), control = Logistics.create(id, 1, 99);
            byte[] before = model.save();
            for (int repeat = 0; repeat < 20; repeat++) for (GameModel.Territory source : model.territories) {
                LogisticsRoutes.neighbors(model, source.id);
                for (int owner = -1; owner <= model.level().opponents + 1; owner++) {
                    LogisticsRoutes.legalTargets(model, source.id, owner);
                    for (GameModel.Territory target : model.territories) {
                        LogisticsRoutes.adjacent(model, source.id, target.id);
                        int[] route = LogisticsRoutes.getRoute(model, source.id, target.id, owner);
                        LogisticsRoutes.eta(model, route);
                        LogisticsRoutes.eta(model, source.id, target.id, owner);
                    }
                }
            }
            check(Arrays.equals(before, model.save()), "Previews preserve counters, armies, clocks, convoys, and RNG state");
            check(model.troops.isEmpty() && model.clashes.isEmpty() && model.drainEvents().isEmpty(), "Previews create no convoy, clash, or battle event");
            model.update(.05f); control.update(.05f);
            check(Arrays.equals(model.save(), control.save()), "Queries do not affect subsequent simulation");
        }
        GameModel model = fixture(3, 1, new int[] {1});
        model.troops.add(new GameModel.Troop(0, 1, 0, 2, -.2f)); model.unitsSent = 1;
        byte[] before = model.save();
        check(LogisticsRoutes.getRoute(model, 0, 1, 0) == null, "Impossible preview refused with existing convoy");
        LogisticsRoutes.eta(model, 0, 1, 0); LogisticsRoutes.legalTargets(model, 0, 0);
        check(Arrays.equals(before, model.save()), "Refusal does not deduct troops or change existing convoys");
    }

    private static int oracleDistance(GameModel model, int source, int target, int owner) {
        if (source == target || model.territories.get(source).owner != owner) return -1;
        int size = model.territories.size(), infinity = 10000;
        int[][] distances = new int[size][size];
        for (int i = 0; i < size; i++) {
            Arrays.fill(distances[i], infinity); distances[i][i] = 0;
            for (int j = 0; j < size; j++) {
                GameModel.Territory a = model.territories.get(i), b = model.territories.get(j);
                double distance = Math.hypot(a.x - b.x, a.y - b.y);
                if (distance > 1.72 && distance < 1.74) distances[i][j] = 1;
            }
        }
        // Floyd-Warshall allows only friendly vertices as interiors, not neutral/enemy waypoints.
        for (int k = 0; k < size; k++) if (model.territories.get(k).owner == owner)
            for (int i = 0; i < size; i++) for (int j = 0; j < size; j++)
                distances[i][j] = Math.min(distances[i][j], distances[i][k] + distances[k][j]);
        return distances[source][target] >= infinity ? -1 : distances[source][target];
    }

    private static void validRoute(GameModel model, int[] route, int owner) {
        boolean[] seen = new boolean[model.territories.size()];
        for (int i = 0; i < route.length; i++) {
            int id = route[i];
            check(id >= 0 && id < seen.length && !seen[id], "Route IDs valid, unique, and cycle-free");
            seen[id] = true;
            if (i < route.length - 1) check(model.territories.get(id).owner == owner, "Every interior tile friendly");
            if (i > 0) check(LogisticsRoutes.adjacent(model, route[i - 1], id), "Every route hop hex-adjacent");
        }
        check(Float.isFinite(LogisticsRoutes.eta(model, route)), "Legal route has a finite preview ETA");
    }

    private static float hop(GameModel model, int a, int b) {
        GameModel.Territory source = model.territories.get(a), target = model.territories.get(b);
        return .3f + (float) Math.hypot(source.x - target.x, source.y - target.y) / 2.6f;
    }

    private static GameModel fixture(int columns, int rows, int[] holes) {
        return new GameModel(0, 1, 51, new GameModel.Level("Routing fixture", columns, rows, 1, holes, 100));
    }

    private static void ownAll(GameModel model, int owner) {
        for (GameModel.Territory tile : model.territories) tile.owner = owner;
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) if (value == target) return true;
        return false;
    }

    private static void expectRoute(GameModel model, int source, int target, int owner, int[] expected) {
        check(Arrays.equals(LogisticsRoutes.getRoute(model, source, target, owner), expected), "Deterministic expected route");
    }

    private static void reject(Action action) {
        try { action.run(); throw new AssertionError("Invalid factory configuration accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
