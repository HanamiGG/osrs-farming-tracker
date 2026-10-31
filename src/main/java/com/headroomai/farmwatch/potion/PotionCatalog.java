package com.headroomai.farmwatch.potion;

import static com.headroomai.farmwatch.potion.Ingredient.*;
import static com.headroomai.farmwatch.potion.PotionDef.Category.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Catalog of ironman-staple potions. Recipes are expressed as flattened
 * raw-ingredient costs: Super combat lists its four herbs directly
 * rather than referencing Super attack / strength / defence, so the
 * planner is a one-pass sum.
 *
 * <p>Quantity is per potion brewed, not per dose — "500 Super combat"
 * means 500 potions (dose count determined by the recipe used in-game;
 * one standard brew makes one potion regardless of final dose count).
 *
 * <p>Order here is the order potions appear in the sidebar; grouping by
 * category is handled by the filter dropdown.
 */
public final class PotionCatalog
{
    private PotionCatalog() {}

    // ----- Combat -----

    public static final PotionDef SUPER_COMBAT = PotionDef.builder("Super combat", COMBAT)
        .add(TORSTOL, 1)
        .add(IRIT, 1).add(EYE_OF_NEWT, 1)
        .add(KWUARM, 1).add(LIMPWURT_ROOT, 1)
        .add(CADANTINE, 1).add(WHITE_BERRIES, 1)
        .build();

    public static final PotionDef DIVINE_SUPER_COMBAT = PotionDef.builder("Divine super combat", COMBAT)
        .add(TORSTOL, 1)
        .add(IRIT, 1).add(EYE_OF_NEWT, 1)
        .add(KWUARM, 1).add(LIMPWURT_ROOT, 1)
        .add(CADANTINE, 1).add(WHITE_BERRIES, 1)
        .add(CRUSHED_NEST, 1)
        .build();

    public static final PotionDef SUPER_ANTIFIRE = PotionDef.builder("Super antifire", COMBAT)
        .add(LANTADYME, 1).add(DRAGON_SCALE_DUST, 1)
        .add(PHOENIX_FEATHER, 1).add(LAVA_SCALE_SHARD, 1)
        .build();

    public static final PotionDef ANTIVENOM_PLUS = PotionDef.builder("Antivenom+", COMBAT)
        .add(IRIT, 1).add(MAGIC_ROOT, 1)   // via Antidote++
        .add(ZULRAH_SCALES, 1)              // via Antivenom
        .add(TORSTOL, 1)                    // top-up to Antivenom+
        .build();

    public static final PotionDef ZAMORAK_BREW = PotionDef.builder("Zamorak brew", COMBAT)
        .add(TORSTOL, 1).add(JANGERBERRIES, 1).build();

    public static final PotionDef SUPER_ATTACK = PotionDef.builder("Super attack", COMBAT)
        .add(IRIT, 1).add(EYE_OF_NEWT, 1).build();

    public static final PotionDef SUPER_STRENGTH = PotionDef.builder("Super strength", COMBAT)
        .add(KWUARM, 1).add(LIMPWURT_ROOT, 1).build();

    public static final PotionDef SUPER_DEFENCE = PotionDef.builder("Super defence", COMBAT)
        .add(CADANTINE, 1).add(WHITE_BERRIES, 1).build();

    // ----- Ranged -----

    public static final PotionDef RANGING_POTION = PotionDef.builder("Ranging potion", RANGED)
        .add(DWARF_WEED, 1).add(WINE_OF_ZAMORAK, 1).build();

    public static final PotionDef DIVINE_RANGING = PotionDef.builder("Divine ranging", RANGED)
        .add(DWARF_WEED, 1).add(WINE_OF_ZAMORAK, 1).add(CRUSHED_NEST, 1).build();

    // ----- Magic -----

    public static final PotionDef MAGIC_POTION = PotionDef.builder("Magic potion", MAGIC)
        .add(LANTADYME, 1).add(POTATO_CACTUS, 1).build();

    public static final PotionDef DIVINE_MAGIC = PotionDef.builder("Divine magic", MAGIC)
        .add(LANTADYME, 1).add(POTATO_CACTUS, 1).add(CRUSHED_NEST, 1).build();

    // ----- Utility -----

    public static final PotionDef PRAYER_POTION = PotionDef.builder("Prayer potion", UTILITY)
        .add(RANARR, 1).add(SNAPE_GRASS, 1).build();

    public static final PotionDef SUPER_RESTORE = PotionDef.builder("Super restore", UTILITY)
        .add(SNAPDRAGON, 1).add(RED_SPIDERS_EGGS, 1).build();

    public static final PotionDef STAMINA_POTION = PotionDef.builder("Stamina potion", UTILITY)
        .add(AVANTOE, 1).add(MORT_MYRE_FUNGUS, 1).add(AMYLASE_CRYSTAL, 1).build();

    public static final PotionDef SARADOMIN_BREW = PotionDef.builder("Saradomin brew", UTILITY)
        .add(TOADFLAX, 1).add(CRUSHED_NEST, 1).build();

    private static final List<PotionDef> ALL = Collections.unmodifiableList(Arrays.asList(
        SUPER_COMBAT,
        DIVINE_SUPER_COMBAT,
        PRAYER_POTION,
        SUPER_RESTORE,
        STAMINA_POTION,
        SARADOMIN_BREW,
        SUPER_ANTIFIRE,
        ANTIVENOM_PLUS,
        ZAMORAK_BREW,
        RANGING_POTION,
        DIVINE_RANGING,
        MAGIC_POTION,
        DIVINE_MAGIC,
        SUPER_ATTACK,
        SUPER_STRENGTH,
        SUPER_DEFENCE
    ));

    public static List<PotionDef> all()
    {
        return ALL;
    }
}
