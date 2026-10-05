package com.frontline.offline;

public final class Campaign {
    public static final int SECTORS_PER_CHAPTER = 6;
    public static final String[] FACTIONS = {"Frontier Alliance", "Ember Pact", "Auric League", "Vesper Order"};
    public static final String[] RULERS = {"Frontier Council", "Marshal Kael Voss", "Chancellor Mira Sol", "Warden Ilyra Veil"};
    public static final Chapter[] CHAPTERS = {
        new Chapter("Border Sparks", 1, "The Ember Pact crosses the frontier.", "Hold the border as rival banners appear."),
        new Chapter("Ember Reach", 1, "The southern foundries answer to Kael Voss.", "Break the Pact's hold on the Ember Reach."),
        new Chapter("Auric Divide", 2, "Mira Sol claims the eastern provinces.", "Reclaim the divided lands of the Auric League."),
        new Chapter("Vesper Veil", 3, "Ilyra Veil rules the northern outposts.", "Unite the scattered provinces of Vesper."),
        new Chapter("Last Accord", 0, "Three rulers contest the last stronghold.", "Defeat every banner and reunite your homeland.")
    };

    public static final class Chapter {
        public final String name, firstLine, secondLine;
        public final int rulerFaction;
        Chapter(String name, int rulerFaction, String firstLine, String secondLine) {
            this.name = name; this.rulerFaction = rulerFaction;
            this.firstLine = firstLine; this.secondLine = secondLine;
        }
    }

    public static int chapterIndex(int sector) { return sector/SECTORS_PER_CHAPTER; }
    public static Chapter chapter(int sector) { return CHAPTERS[chapterIndex(sector)]; }
    private Campaign() {}
}
