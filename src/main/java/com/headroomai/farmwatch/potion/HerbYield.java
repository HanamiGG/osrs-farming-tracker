package com.headroomai.farmwatch.potion;

/**
 * OSRS herb-patch yield math. Encapsulates the "chance to save a life"
 * formula so the planner can turn "4 ranarr patches growing" into "~16
 * ranarr herbs on the way at your Farming level".
 *
 * <p>Formula (per OSRS Wiki, "Chance to save a life"):
 * <pre>
 *   cts_raw = floor( (CTS_low * (99 - level) + CTS_high * (level - 1)) / 98 ) + 1
 *   cts_prob = cts_raw / 256
 *   expected_yield = lives / (1 - cts_prob)
 * </pre>
 *
 * <p>For herbs, lives=3 (compost does not affect yield, only disease
 * resistance). Magic secateurs give a flat +10% to the expected yield.
 * We ignore Attas plants (negligible +0.5%) and disease loss (ironmen
 * who protect their herbs — the planner's audience — don't take disease
 * hits often enough to be worth modelling).
 */
public final class HerbYield
{
    private static final int LIVES = 3;
    private static final double MAGIC_SECATEURS_MULT = 1.10;

    private HerbYield() {}

    /**
     * Expected herbs harvested from a single HARVESTABLE herb patch.
     *
     * @param ingredient herb ingredient — must satisfy {@link Ingredient#hasYieldModel()}
     * @param farmingLevel the player's current (or boosted) Farming level, 1-99 clamped
     * @param magicSecateurs true if the player has magic secateurs equipped
     */
    public static double expectedPerPatch(Ingredient ingredient, int farmingLevel, boolean magicSecateurs)
    {
        if (ingredient == null || !ingredient.hasYieldModel())
        {
            return 0.0;
        }
        int level = clampLevel(farmingLevel);
        double prob = chanceToSaveProbability(ingredient.ctsLow(), ingredient.ctsHigh(), level);
        double yield = LIVES / (1.0 - prob);
        if (magicSecateurs)
        {
            yield *= MAGIC_SECATEURS_MULT;
        }
        return yield;
    }

    /**
     * Round-down expected yield from a count of harvestable patches. The
     * UI shows an integer so the number doesn't look misleadingly precise.
     */
    public static int expectedFromPatches(Ingredient ingredient, int patches, int farmingLevel, boolean magicSecateurs)
    {
        if (patches <= 0) return 0;
        double per = expectedPerPatch(ingredient, farmingLevel, magicSecateurs);
        return (int) Math.floor(per * patches);
    }

    /** CTS probability in [0, 1) at the given level. Package-visible for tests. */
    static double chanceToSaveProbability(int ctsLow, int ctsHigh, int level)
    {
        int ctsRaw = (ctsLow * (99 - level) + ctsHigh * (level - 1)) / 98 + 1;
        return ctsRaw / 256.0;
    }

    private static int clampLevel(int level)
    {
        if (level < 1) return 1;
        if (level > 99) return 99;
        return level;
    }
}
