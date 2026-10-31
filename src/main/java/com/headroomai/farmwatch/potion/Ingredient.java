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
 */
public enum Ingredient
{
    // Herbs (cleaned). Display names match the in-game item names.
    GUAM(Type.HERB, "Guam leaf", null),
    MARRENTILL(Type.HERB, "Marrentill", null),
    TARROMIN(Type.HERB, "Tarromin", null),
    HARRALANDER(Type.HERB, "Harralander", null),
    RANARR(Type.HERB, "Ranarr weed", null),
    TOADFLAX(Type.HERB, "Toadflax", null),
    IRIT(Type.HERB, "Irit leaf", null),
    AVANTOE(Type.HERB, "Avantoe", null),
    KWUARM(Type.HERB, "Kwuarm", null),
    SNAPDRAGON(Type.HERB, "Snapdragon", null),
    CADANTINE(Type.HERB, "Cadantine", null),
    LANTADYME(Type.HERB, "Lantadyme", null),
    DWARF_WEED(Type.HERB, "Dwarf weed", null),
    TORSTOL(Type.HERB, "Torstol", null),

    // Secondaries.
    EYE_OF_NEWT(Type.SECONDARY, "Eye of newt", "General store (Betty, Port Sarim)"),
    LIMPWURT_ROOT(Type.SECONDARY, "Limpwurt root", "Hobgoblins / plant limpwurt seeds in flower patch"),
    WHITE_BERRIES(Type.SECONDARY, "White berries", "White berry bush (Farming 59) / fairy ring BKR"),
    SNAPE_GRASS(Type.SECONDARY, "Snape grass", "Spawns at Waterbirth / Jiggig; plant post-Garden of Tranquility"),
    MORT_MYRE_FUNGUS(Type.SECONDARY, "Mort myre fungus", "Mort Myre swamp with silver sickle"),
    AMYLASE_CRYSTAL(Type.SECONDARY, "Amylase crystal", "Barbarian Assault (10 pts/crystal) or Mastering Mixology"),
    RED_SPIDERS_EGGS(Type.SECONDARY, "Red spiders' eggs", "Edgeville / Taverley dungeon spawns"),
    CRUSHED_NEST(Type.SECONDARY, "Crushed bird's nest", "Bird's nests from woodcutting + pestle and mortar"),
    PHOENIX_FEATHER(Type.SECONDARY, "Phoenix feather", "Phoenix slayer drop / Mount Quidamortem"),
    LAVA_SCALE_SHARD(Type.SECONDARY, "Lava scale shard", "Konar / Vorkath drop; crush lava scale with pestle"),
    DRAGON_SCALE_DUST(Type.SECONDARY, "Dragon scale dust", "Blue dragons; crush blue dragon scale with pestle"),
    WINE_OF_ZAMORAK(Type.SECONDARY, "Wine of Zamorak", "Chaos Temple (Asgarnia) altar spawn — telegrab"),
    POTATO_CACTUS(Type.SECONDARY, "Potato cactus", "Cactus patch (Farming 25) / Kalphite Lair spawns"),
    JANGERBERRIES(Type.SECONDARY, "Jangerberries", "Jangerberry bush (Farming 48) / Legends' Guild garden"),
    MAGIC_ROOT(Type.SECONDARY, "Magic root", "Dig up magic tree stump after chopping (Farming 75)"),
    ZULRAH_SCALES(Type.SECONDARY, "Zulrah's scales", "Zulrah drop (requires Regicide)");

    public enum Type { HERB, SECONDARY }

    private final Type type;
    private final String displayName;
    private final String source;

    Ingredient(Type type, String displayName, String source)
    {
        this.type = type;
        this.displayName = displayName;
        this.source = source;
    }

    public Type type() { return type; }
    public String displayName() { return displayName; }

    /** One-line note on where ironmen get this. {@code null} for herbs (the dashboard already covers those). */
    public String source() { return source; }
}
