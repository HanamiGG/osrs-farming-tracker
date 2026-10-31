package com.headroomai.farmwatch.potion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HerbYieldTest
{
    private static final double EPS = 0.01;

    @Test
    public void secondaryIngredientsHaveNoYieldModel()
    {
        for (Ingredient ing : Ingredient.values())
        {
            if (ing.type() == Ingredient.Type.SECONDARY)
            {
                assertTrue(ing + " should not have yield model", !ing.hasYieldModel());
                assertEquals(0.0, HerbYield.expectedPerPatch(ing, 70, true), 0.0);
            }
        }
    }

    @Test
    public void allHerbsHaveYieldModel()
    {
        for (Ingredient ing : Ingredient.values())
        {
            if (ing.type() == Ingredient.Type.HERB)
            {
                assertTrue(ing + " should have yield model", ing.hasYieldModel());
                assertTrue(ing + " ctsLow positive", ing.ctsLow() > 0);
                assertTrue(ing + " ctsHigh positive", ing.ctsHigh() > 0);
                assertTrue(ing + " ctsLow <= ctsHigh", ing.ctsLow() <= ing.ctsHigh());
            }
        }
    }

    @Test
    public void ranarrAtLevel99MatchesWikiCalibration()
    {
        // Ranarr at level 99 with no secateurs: cts_raw = (39*0 + 80*98)/98 + 1 = 81.
        // cts_prob = 81/256 ≈ 0.3164. yield = 3 / (1 - 0.3164) ≈ 4.39.
        double y = HerbYield.expectedPerPatch(Ingredient.RANARR, 99, false);
        assertEquals("ranarr 99 no secateurs", 4.39, y, EPS);
    }

    @Test
    public void torstolAtLevel99WithSecateursIsHigher()
    {
        // Torstol at 99 has the highest CTS (72/80). cts_raw at 99 = (72*0 + 80*98)/98 + 1 = 81,
        // same as ranarr at 99 since high=80 for both. So torstol only outyields ranarr at
        // lower levels, where its higher ctsLow helps. Spot-check: secateurs multiplies by 1.10.
        double noSec = HerbYield.expectedPerPatch(Ingredient.TORSTOL, 99, false);
        double withSec = HerbYield.expectedPerPatch(Ingredient.TORSTOL, 99, true);
        assertEquals(noSec * 1.10, withSec, 1e-6);
    }

    @Test
    public void guamAtLevel50IsLowerThanRanarrAtLevel50()
    {
        // Guam CTS low=25 vs ranarr low=39. At mid-level ranarr yields more per patch.
        double g = HerbYield.expectedPerPatch(Ingredient.GUAM, 50, false);
        double r = HerbYield.expectedPerPatch(Ingredient.RANARR, 50, false);
        assertTrue("ranarr50 (" + r + ") should exceed guam50 (" + g + ")", r > g);
    }

    @Test
    public void yieldIsMonotonicInLevel()
    {
        // Higher Farming level => higher expected yield for the same herb.
        double at1 = HerbYield.expectedPerPatch(Ingredient.RANARR, 1, false);
        double at50 = HerbYield.expectedPerPatch(Ingredient.RANARR, 50, false);
        double at99 = HerbYield.expectedPerPatch(Ingredient.RANARR, 99, false);
        assertTrue("yield should rise with level: " + at1 + " < " + at50 + " < " + at99,
            at1 < at50 && at50 < at99);
    }

    @Test
    public void levelsOutsideRangeAreClamped()
    {
        // Negative, zero, over 99 should all clamp into [1, 99] without exceptions.
        double below = HerbYield.expectedPerPatch(Ingredient.RANARR, -5, false);
        double atOne = HerbYield.expectedPerPatch(Ingredient.RANARR, 1, false);
        double over = HerbYield.expectedPerPatch(Ingredient.RANARR, 150, false);
        double atNinetyNine = HerbYield.expectedPerPatch(Ingredient.RANARR, 99, false);
        assertEquals("sub-1 clamps to 1", atOne, below, 1e-9);
        assertEquals("over-99 clamps to 99", atNinetyNine, over, 1e-9);
    }

    @Test
    public void expectedFromPatchesScalesLinearly()
    {
        double per = HerbYield.expectedPerPatch(Ingredient.RANARR, 70, false);
        assertEquals((int) Math.floor(per * 4), HerbYield.expectedFromPatches(Ingredient.RANARR, 4, 70, false));
    }

    @Test
    public void zeroOrNegativePatchesReturnZero()
    {
        assertEquals(0, HerbYield.expectedFromPatches(Ingredient.RANARR, 0, 70, false));
        assertEquals(0, HerbYield.expectedFromPatches(Ingredient.RANARR, -3, 70, false));
    }

    @Test
    public void secateursBoostYieldByTenPercent()
    {
        for (int level : new int[]{1, 30, 50, 70, 85, 99})
        {
            double no = HerbYield.expectedPerPatch(Ingredient.TORSTOL, level, false);
            double yes = HerbYield.expectedPerPatch(Ingredient.TORSTOL, level, true);
            assertEquals("secateurs flat 1.10 at level " + level, no * 1.10, yes, 1e-9);
        }
    }

    @Test
    public void expectedPerPatchHandlesNullIngredient()
    {
        assertEquals(0.0, HerbYield.expectedPerPatch(null, 70, true), 0.0);
    }

    @Test
    public void chanceToSaveMatchesHandComputedExample()
    {
        // Ranarr (39/80) at level 1: ctsRaw = (39*98 + 80*0)/98 + 1 = 40. prob = 40/256 = 0.15625.
        assertEquals(40.0 / 256.0, HerbYield.chanceToSaveProbability(39, 80, 1), 1e-9);
        // Ranarr at level 99: ctsRaw = (39*0 + 80*98)/98 + 1 = 81. prob = 81/256 ≈ 0.3164.
        assertEquals(81.0 / 256.0, HerbYield.chanceToSaveProbability(39, 80, 99), 1e-9);
    }
}
