package com.frontline.offline;

import java.util.Arrays;
import java.util.Comparator;

/** Stateless route previews; launching, convoy limits, and transit combat belong to GameModel. */
public final class LogisticsRoutes {
    private static final double MIN_EDGE = 1.72, MAX_EDGE = 1.74;

    private LogisticsRoutes() {}

    public static boolean adjacent(GameModel model, int source, int target) {
        GameModel.Territory[] tiles = orderedTiles(model);
        int a = indexOf(tiles, source), b = indexOf(tiles, target);
        return a >= 0 && b >= 0 && adjacent(tiles[a], tiles[b]);
    }

    /** Existing adjacent territory IDs, in ascending order; holes have no vertex. */
    public static int[] neighbors(GameModel model, int source) {
        GameModel.Territory[] tiles = orderedTiles(model);
        int index = indexOf(tiles, source);
        if (index < 0) return new int[0];
        int[] result = new int[tiles.length];
        int size = 0;
        for (GameModel.Territory tile : tiles)
            if (adjacent(tiles[index], tile)) result[size++] = tile.id;
        return Arrays.copyOf(result, size);
    }

    /** Shortest legal route, including both endpoints, or null. Neutral targets can be attacked. */
    public static int[] getRoute(GameModel model, int source, int target, int owner) {
        if (source == target) return null;
        Search search = friendlySearch(model, source, owner);
        if (search == null) return null;
        int endpoint = indexOf(search.tiles, target);
        if (endpoint < 0 || !validTargetOwner(model, search.tiles[endpoint].owner)) return null;
        int lastFriendly = lastFriendly(search, endpoint);
        if (lastFriendly < 0) return null;
        int size = 1;
        for (int i = lastFriendly; search.parents[i] != i; i = search.parents[i]) size++;
        int[] route = new int[size + (lastFriendly == endpoint ? 0 : 1)];
        for (int i = lastFriendly, position = size - 1; position >= 0; position--) {
            route[position] = search.tiles[i].id;
            i = search.parents[i];
        }
        if (lastFriendly != endpoint) route[size] = target;
        return route;
    }

    /** Routeable endpoints, sorted by ID. Counts, battle outcome, and queue capacity are not checked. */
    public static int[] legalTargets(GameModel model, int source, int owner) {
        Search search = friendlySearch(model, source, owner);
        if (search == null) return new int[0];
        int[] result = new int[search.tiles.length];
        int size = 0;
        for (int i = 0; i < search.tiles.length; i++)
            if (search.tiles[i].id != source && validTargetOwner(model, search.tiles[i].owner)
                && lastFriendly(search, i) >= 0) result[size++] = search.tiles[i].id;
        return Arrays.copyOf(result, size);
    }

    /** Travel seconds only, excluding packet launch delays. Illegal routes return positive infinity. */
    public static float eta(GameModel model, int source, int target, int owner) {
        return eta(model, getRoute(model, source, target, owner));
    }

    /** Revalidates geometry and friendly interiors against current ownership without changing the route. */
    public static float eta(GameModel model, int[] route) {
        if (model == null || route == null || route.length < 2) return Float.POSITIVE_INFINITY;
        GameModel.Territory[] tiles = orderedTiles(model);
        int previous = indexOf(tiles, route[0]);
        if (previous < 0 || !validOwner(model, tiles[previous].owner)) return Float.POSITIVE_INFINITY;
        int owner = tiles[previous].owner;
        boolean[] visited = new boolean[tiles.length];
        visited[previous] = true;
        float seconds = 0;
        for (int i = 1; i < route.length; i++) {
            int current = indexOf(tiles, route[i]);
            if (current < 0 || visited[current] || !adjacent(tiles[previous], tiles[current])
                || !validTargetOwner(model, tiles[current].owner)
                || i < route.length - 1 && tiles[current].owner != owner) return Float.POSITIVE_INFINITY;
            float distance = (float) Math.hypot(tiles[previous].x - tiles[current].x,
                tiles[previous].y - tiles[current].y);
            seconds += .3f + distance / 2.6f;
            visited[current] = true;
            previous = current;
        }
        return seconds;
    }

    private static Search friendlySearch(GameModel model, int source, int owner) {
        if (!validOwner(model, owner)) return null;
        GameModel.Territory[] tiles = orderedTiles(model);
        int start = indexOf(tiles, source);
        if (start < 0 || tiles[start].owner != owner) return null;
        Search search = new Search(tiles, owner);
        search.parents[start] = start;
        search.order[search.size++] = start;
        // ID-ordered expansion makes equal-hop routes independent of territory list order.
        for (int head = 0; head < search.size; head++) {
            int current = search.order[head];
            for (int next = 0; next < tiles.length; next++)
                if (search.parents[next] < 0 && tiles[next].owner == owner && adjacent(tiles[current], tiles[next])) {
                    search.parents[next] = current;
                    search.order[search.size++] = next;
                }
        }
        return search;
    }

    private static int lastFriendly(Search search, int target) {
        if (search.tiles[target].owner == search.owner) return search.parents[target] >= 0 ? target : -1;
        for (int i = 0; i < search.size; i++) {
            int candidate = search.order[i];
            if (adjacent(search.tiles[candidate], search.tiles[target])) return candidate;
        }
        return -1;
    }

    private static boolean validOwner(GameModel model, int owner) {
        return model != null && owner >= GameModel.PLAYER && owner <= model.level().opponents;
    }

    private static boolean validTargetOwner(GameModel model, int owner) {
        return owner == GameModel.NEUTRAL || validOwner(model, owner);
    }

    private static GameModel.Territory[] orderedTiles(GameModel model) {
        if (model == null) return new GameModel.Territory[0];
        GameModel.Territory[] tiles = model.territories.toArray(new GameModel.Territory[0]);
        Arrays.sort(tiles, Comparator.comparingInt(tile -> tile.id));
        return tiles;
    }

    private static int indexOf(GameModel.Territory[] tiles, int id) {
        for (int i = 0; i < tiles.length; i++) if (tiles[i].id == id) return i;
        return -1;
    }

    private static boolean adjacent(GameModel.Territory source, GameModel.Territory target) {
        if (source.id == target.id) return false;
        double dx = source.x - target.x, dy = source.y - target.y;
        double squared = dx * dx + dy * dy;
        return squared > MIN_EDGE * MIN_EDGE && squared < MAX_EDGE * MAX_EDGE;
    }

    private static final class Search {
        final GameModel.Territory[] tiles;
        final int[] parents, order;
        final int owner;
        int size;

        Search(GameModel.Territory[] tiles, int owner) {
            this.tiles = tiles; this.owner = owner;
            parents = new int[tiles.length]; order = new int[tiles.length];
            Arrays.fill(parents, -1);
        }
    }
}
