package com.headroomai.farmwatch.potion;

import java.util.EnumMap;
import java.util.Map;

/**
 * Takes a goal map (how many of each potion to brew) and sums raw
 * ingredient costs into a {@link ShoppingList}, splitting output into
 * herbs vs secondaries so the sidebar panel can render them in two
 * sections. Pure, stateless, no I/O — the UI calls this on the Swing
 * thread without worrying about blocking.
 */
public final class PotionPlanner
{
    private PotionPlanner() {}

    public static ShoppingList compute(Map<PotionDef, Integer> goals)
    {
        if (goals == null || goals.isEmpty())
        {
            return ShoppingList.empty();
        }

        Map<Ingredient, Integer> herbs = new EnumMap<>(Ingredient.class);
        Map<Ingredient, Integer> secondaries = new EnumMap<>(Ingredient.class);

        for (Map.Entry<PotionDef, Integer> goal : goals.entrySet())
        {
            PotionDef potion = goal.getKey();
            Integer qty = goal.getValue();
            if (potion == null || qty == null || qty <= 0)
            {
                continue;
            }
            for (Map.Entry<Ingredient, Integer> step : potion.recipe().entrySet())
            {
                Ingredient ing = step.getKey();
                int cost = Math.multiplyExact(step.getValue(), qty);
                Map<Ingredient, Integer> bucket = ing.type() == Ingredient.Type.HERB ? herbs : secondaries;
                bucket.merge(ing, cost, Integer::sum);
            }
        }

        return new ShoppingList(herbs, secondaries);
    }
}
