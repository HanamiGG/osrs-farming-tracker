package com.headroomai.farmwatch.potion;

import static com.headroomai.farmwatch.potion.Ingredient.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * MVP catalog of ironman-staple potions. Recipes are expressed as
 * flattened raw-ingredient costs: Super combat lists its four herbs
 * directly rather than referencing Super attack / strength / defence,
 * so the planner is a one-pass sum.
 *
 * <p>Quantity is per potion brewed, not per dose — "500 Super combat"
 * means 500 potions (dose count determined by the recipe used in-game;
 * 1 standard brew makes 1 potion regardless of final dose count).
 *
 * <p>Order here is also the order potions appear in the sidebar.
 */
public final class PotionCatalog
{
    private PotionCatalog() {}

    public static final PotionDef SUPER_ATTACK = PotionDef.builder("Super attack")
        .add(IRIT, 1).add(EYE_OF_NEWT, 1).build();

    public static final PotionDef SUPER_STRENGTH = PotionDef.builder("Super strength")
        .add(KWUARM, 1).add(LIMPWURT_ROOT, 1).build();

    public static final PotionDef SUPER_DEFENCE = PotionDef.builder("Super defence")
        .add(CADANTINE, 1).add(WHITE_BERRIES, 1).build();

    public static final PotionDef SUPER_COMBAT = PotionDef.builder("Super combat")
        .add(TORSTOL, 1)
        .add(IRIT, 1).add(EYE_OF_NEWT, 1)
        .add(KWUARM, 1).add(LIMPWURT_ROOT, 1)
        .add(CADANTINE, 1).add(WHITE_BERRIES, 1)
        .build();

    public static final PotionDef DIVINE_SUPER_COMBAT = PotionDef.builder("Divine super combat")
        .add(TORSTOL, 1)
        .add(IRIT, 1).add(EYE_OF_NEWT, 1)
        .add(KWUARM, 1).add(LIMPWURT_ROOT, 1)
        .add(CADANTINE, 1).add(WHITE_BERRIES, 1)
        .add(CRUSHED_NEST, 1)
        .build();

    public static final PotionDef DIVINE_RANGING = PotionDef.builder("Divine ranging")
        .add(DWARF_WEED, 1).add(WINE_OF_ZAMORAK, 1).add(CRUSHED_NEST, 1).build();

    public static final PotionDef DIVINE_MAGIC = PotionDef.builder("Divine magic")
        .add(LANTADYME, 1).add(POTATO_CACTUS, 1).add(CRUSHED_NEST, 1).build();

    public static final PotionDef PRAYER_POTION = PotionDef.builder("Prayer potion")
        .add(RANARR, 1).add(SNAPE_GRASS, 1).build();

    public static final PotionDef SUPER_RESTORE = PotionDef.builder("Super restore")
        .add(SNAPDRAGON, 1).add(RED_SPIDERS_EGGS, 1).build();

    public static final PotionDef STAMINA_POTION = PotionDef.builder("Stamina potion")
        .add(AVANTOE, 1).add(MORT_MYRE_FUNGUS, 1).add(AMYLASE_CRYSTAL, 1).build();

    public static final PotionDef SARADOMIN_BREW = PotionDef.builder("Saradomin brew")
        .add(TOADFLAX, 1).add(CRUSHED_NEST, 1).build();

    public static final PotionDef SUPER_ANTIFIRE = PotionDef.builder("Super antifire")
        .add(LANTADYME, 1).add(DRAGON_SCALE_DUST, 1)
        .add(PHOENIX_FEATHER, 1).add(LAVA_SCALE_SHARD, 1)
        .build();

    private static final List<PotionDef> ALL = Collections.unmodifiableList(Arrays.asList(
        SUPER_COMBAT,
        DIVINE_SUPER_COMBAT,
        PRAYER_POTION,
        SUPER_RESTORE,
        STAMINA_POTION,
        SARADOMIN_BREW,
        SUPER_ANTIFIRE,
        DIVINE_RANGING,
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
