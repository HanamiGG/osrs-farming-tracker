package com.headroomai.farmwatch.potion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static com.headroomai.farmwatch.potion.Ingredient.*;
import static com.headroomai.farmwatch.potion.PotionCatalog.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PotionPlannerTest
{
    @Test
    public void emptyGoalsProducesEmptyShoppingList()
    {
        ShoppingList out = PotionPlanner.compute(Collections.emptyMap());
        assertTrue(out.isEmpty());
        assertTrue(out.herbs().isEmpty());
        assertTrue(out.secondaries().isEmpty());
    }

    @Test
    public void nullGoalsProducesEmptyShoppingList()
    {
        ShoppingList out = PotionPlanner.compute(null);
        assertTrue(out.isEmpty());
    }

    @Test
    public void singlePotionProducesItsRecipeTimesQuantity()
    {
        Map<PotionDef, Integer> goals = Collections.singletonMap(PRAYER_POTION, 50);
        ShoppingList out = PotionPlanner.compute(goals);
        assertEquals(Integer.valueOf(50), out.herbs().get(RANARR));
        assertEquals(Integer.valueOf(50), out.secondaries().get(SNAPE_GRASS));
        assertEquals("only one herb in the list", 1, out.herbs().size());
        assertEquals("only one secondary in the list", 1, out.secondaries().size());
    }

    @Test
    public void superCombatFansOutToFourHerbsAndThreeSecondaries()
    {
        // Super combat flattens to Torstol + Irit + Kwuarm + Cadantine
        // and Eye of newt + Limpwurt root + White berries. Nothing else
        // should sneak into the shopping list.
        Map<PotionDef, Integer> goals = Collections.singletonMap(SUPER_COMBAT, 500);
        ShoppingList out = PotionPlanner.compute(goals);

        assertEquals(Integer.valueOf(500), out.herbs().get(TORSTOL));
        assertEquals(Integer.valueOf(500), out.herbs().get(IRIT));
        assertEquals(Integer.valueOf(500), out.herbs().get(KWUARM));
        assertEquals(Integer.valueOf(500), out.herbs().get(CADANTINE));
        assertEquals("exactly four herbs", 4, out.herbs().size());

        assertEquals(Integer.valueOf(500), out.secondaries().get(EYE_OF_NEWT));
        assertEquals(Integer.valueOf(500), out.secondaries().get(LIMPWURT_ROOT));
        assertEquals(Integer.valueOf(500), out.secondaries().get(WHITE_BERRIES));
        assertEquals("exactly three secondaries", 3, out.secondaries().size());
    }

    @Test
    public void overlappingGoalsSumSharedIngredients()
    {
        // Super combat contributes 300 Torstol; Divine super combat
        // contributes another 100. Should sum to 400.
        Map<PotionDef, Integer> goals = new HashMap<>();
        goals.put(SUPER_COMBAT, 300);
        goals.put(DIVINE_SUPER_COMBAT, 100);

        ShoppingList out = PotionPlanner.compute(goals);
        assertEquals(Integer.valueOf(400), out.herbs().get(TORSTOL));
        assertEquals(Integer.valueOf(400), out.herbs().get(KWUARM));
        assertEquals(Integer.valueOf(400), out.secondaries().get(EYE_OF_NEWT));
        // Only Divine super combat uses crushed nest.
        assertEquals(Integer.valueOf(100), out.secondaries().get(CRUSHED_NEST));
    }

    @Test
    public void zeroQuantityGoalIsIgnored()
    {
        Map<PotionDef, Integer> goals = new HashMap<>();
        goals.put(PRAYER_POTION, 50);
        goals.put(SUPER_RESTORE, 0);
        ShoppingList out = PotionPlanner.compute(goals);
        assertNull("super restore contributed nothing", out.herbs().get(SNAPDRAGON));
        assertEquals(Integer.valueOf(50), out.herbs().get(RANARR));
    }

    @Test
    public void negativeQuantityGoalIsIgnored()
    {
        Map<PotionDef, Integer> goals = new HashMap<>();
        goals.put(PRAYER_POTION, -5);
        ShoppingList out = PotionPlanner.compute(goals);
        assertTrue(out.isEmpty());
    }

    @Test
    public void catalogHasTwelveEntriesAndAllAreValidPotions()
    {
        assertEquals(12, PotionCatalog.all().size());
        for (PotionDef p : PotionCatalog.all())
        {
            assertFalse("every potion has a name", p.name().isEmpty());
            assertFalse("every potion has a recipe", p.recipe().isEmpty());
        }
    }

    @Test
    public void everySecondaryHasAnIronmanSource()
    {
        // Herbs don't need a source (the dashboard covers them). But if
        // a SECONDARY ships without one the UI will render a bare name
        // and the user won't know where to look.
        for (Ingredient ing : Ingredient.values())
        {
            if (ing.type() == Ingredient.Type.SECONDARY)
            {
                assertFalse(
                    "secondary " + ing + " needs a source line",
                    ing.source() == null || ing.source().isEmpty());
            }
        }
    }

    @Test
    public void potionBuilderRejectsNonPositiveQuantities()
    {
        try
        {
            PotionDef.builder("bad").add(RANARR, 0).build();
            throw new AssertionError("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
            // ok
        }
    }

    @Test
    public void superCombatCostsAcrossThreeGoalsScaleCorrectly()
    {
        // Mixed-potion sanity check matching the UI's preview case:
        // 500 Super combat + 200 Prayer + 100 Super restore + 100 Stamina.
        Map<PotionDef, Integer> goals = new HashMap<>();
        goals.put(SUPER_COMBAT, 500);
        goals.put(PRAYER_POTION, 200);
        goals.put(SUPER_RESTORE, 100);
        goals.put(STAMINA_POTION, 100);
        ShoppingList out = PotionPlanner.compute(goals);
        assertEquals(Integer.valueOf(500), out.herbs().get(TORSTOL));
        assertEquals(Integer.valueOf(200), out.herbs().get(RANARR));
        assertEquals(Integer.valueOf(100), out.herbs().get(SNAPDRAGON));
        assertEquals(Integer.valueOf(100), out.herbs().get(AVANTOE));
        assertEquals(Integer.valueOf(200), out.secondaries().get(SNAPE_GRASS));
        assertEquals(Integer.valueOf(100), out.secondaries().get(RED_SPIDERS_EGGS));
        assertEquals(Integer.valueOf(100), out.secondaries().get(MORT_MYRE_FUNGUS));
        assertEquals(Integer.valueOf(100), out.secondaries().get(AMYLASE_CRYSTAL));
    }
}
