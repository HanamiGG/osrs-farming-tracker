package com.headroomai.farmwatch.potion;

/**
 * Catalog of ingredients that show up in the MVP potion recipes.
 *
 * <p>The {@link #type} splits the output panel into two sections — herbs
 * (farmable, feeds the dashboard directly) and secondaries (dropped,
 * shopped, or mini-game grinded, which is the harder half for ironmen).
 * {@link #source} is a one-liner shown next to each secondary; the real
 * guide is still the wiki, but this is enough to point users at the
 * right content without them having to tab out mid-plan.
 *
 * <p>{@link #itemId} is the brewable-form OSRS item id used by the bank
 * reader. Grimy herbs are intentionally NOT counted — the planner's
 * "need N torstol" means N doses of brewable torstol, and if the user
 * has 300 grimy they still need to clean them before they count. Keeps
 * the semantic clean rather than silently inflating counts.
 *
 * <p>{@link #ctsLow} / {@link #ctsHigh} are the "chance to save a life"
 * low/high pair used by {@link HerbYield} to compute expected herbs per
 * patch at the player's current Farming level. Only populated for herbs
 * (secondaries don't have a patch yield model). Values come from the
 * OSRS Wiki "Chance to save a life" table; adjust here if Jagex rebalances.
 */
public enum Ingredient
{
    // ----- Herbs (clean form). ids are OSRS item ids (stable across versions). -----
    GUAM(Type.HERB,       "Guam leaf",      null, 249,  25, 80),
    MARRENTILL(Type.HERB, "Marrentill",     null, 251,  28, 80),
    TARROMIN(Type.HERB,   "Tarromin",       null, 253,  31, 80),
    HARRALANDER(Type.HERB,"Harralander",    null, 255,  36, 80),
    RANARR(Type.HERB,     "Ranarr weed",    null, 257,  39, 80),
    TOADFLAX(Type.HERB,   "Toadflax",       null, 2998, 43, 80),
    IRIT(Type.HERB,       "Irit leaf",      null, 259,  46, 80),
    AVANTOE(Type.HERB,    "Avantoe",        null, 261,  50, 80),
    KWUARM(Type.HERB,     "Kwuarm",         null, 263,  54, 80),
    SNAPDRAGON(Type.HERB, "Snapdragon",     null, 3000, 57, 80),
    CADANTINE(Type.HERB,  "Cadantine",      null, 265,  60, 80),
    LANTADYME(Type.HERB,  "Lantadyme",      null, 2481, 64, 80),
    DWARF_WEED(Type.HERB, "Dwarf weed",     null, 267,  68, 80),
    TORSTOL(Type.HERB,    "Torstol",        null, 269,  72, 80),

    // ----- Secondaries. -----
    EYE_OF_NEWT(Type.SECONDARY, "Eye of newt",
        "General store (Betty, Port Sarim)", 221),
    LIMPWURT_ROOT(Type.SECONDARY, "Limpwurt root",
        "Hobgoblins / plant limpwurt seeds in flower patch", 225),
    WHITE_BERRIES(Type.SECONDARY, "White berries",
        "White berry bush (Farming 59) / fairy ring BKR", 239),
    SNAPE_GRASS(Type.SECONDARY, "Snape grass",
        "Spawns at Waterbirth / Jiggig; plant post-Garden of Tranquility", 231),
    MORT_MYRE_FUNGUS(Type.SECONDARY, "Mort myre fungus",
        "Mort Myre swamp with silver sickle", 2970),
    AMYLASE_CRYSTAL(Type.SECONDARY, "Amylase crystal",
        "Barbarian Assault (10 pts/crystal) or Mastering Mixology", 12640),
    RED_SPIDERS_EGGS(Type.SECONDARY, "Red spiders' eggs",
        "Edgeville / Taverley dungeon spawns", 223),
    CRUSHED_NEST(Type.SECONDARY, "Crushed bird's nest",
        "Bird's nests from woodcutting + pestle and mortar", 6693),
    PHOENIX_FEATHER(Type.SECONDARY, "Phoenix feather",
        "Phoenix slayer drop / Mount Quidamortem", 24268),
    LAVA_SCALE_SHARD(Type.SECONDARY, "Lava scale shard",
        "Konar / Vorkath drop; crush lava scale with pestle", 21623),
    DRAGON_SCALE_DUST(Type.SECONDARY, "Dragon scale dust",
        "Blue dragons; crush blue dragon scale with pestle", 241),
    WINE_OF_ZAMORAK(Type.SECONDARY, "Wine of Zamorak",
        "Chaos Temple (Asgarnia) altar spawn — telegrab", 245),
    POTATO_CACTUS(Type.SECONDARY, "Potato cactus",
        "Cactus patch (Farming 25) / Kalphite Lair spawns", 3138),
    JANGERBERRIES(Type.SECONDARY, "Jangerberries",
        "Jangerberry bush (Farming 48) / Legends' Guild garden", 247),
    MAGIC_ROOT(Type.SECONDARY, "Magic root",
        "Dig up magic tree stump after chopping (Farming 75)", 6043),
    ZULRAH_SCALES(Type.SECONDARY, "Zulrah's scales",
        "Zulrah drop (requires Regicide)", 12934);

    public enum Type { HERB, SECONDARY }

    private final Type type;
    private final String displayName;
    private final String source;
    private final int itemId;
    private final Integer ctsLow;
    private final Integer ctsHigh;

    Ingredient(Type type, String displayName, String source, int itemId)
    {
        this(type, displayName, source, itemId, null, null);
    }

    Ingredient(Type type, String displayName, String source, int itemId, int ctsLow, int ctsHigh)
    {
        this(type, displayName, source, itemId, (Integer) ctsLow, (Integer) ctsHigh);
    }

    Ingredient(Type type, String displayName, String source, int itemId, Integer ctsLow, Integer ctsHigh)
    {
        this.type = type;
        this.displayName = displayName;
        this.source = source;
        this.itemId = itemId;
        this.ctsLow = ctsLow;
        this.ctsHigh = ctsHigh;
    }

    public Type type() { return type; }
    public String displayName() { return displayName; }

    /** One-line note on where ironmen get this. {@code null} for herbs (the dashboard already covers those). */
    public String source() { return source; }

    /** OSRS item id of the brewable form (grimy herbs are not matched). */
    public int itemId() { return itemId; }

    /** CTS low value from the OSRS "chance to save a life" table. Null for secondaries. */
    public Integer ctsLow() { return ctsLow; }

    /** CTS high value from the OSRS "chance to save a life" table. Null for secondaries. */
    public Integer ctsHigh() { return ctsHigh; }

    /** True iff this ingredient has a herb-patch yield model (herbs do, secondaries don't). */
    public boolean hasYieldModel() { return ctsLow != null && ctsHigh != null; }
}
