package com.headroomai.farmwatch.potion;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Output of {@link PotionPlanner#compute(Map)}: the raw-ingredient totals
 * needed to brew every goal, split into herbs (farmable) and secondaries
 * (grinded / shopped). Both maps are immutable and iterate in enum order.
 */
public final class ShoppingList
{
    private final Map<Ingredient, Integer> herbs;
    private final Map<Ingredient, Integer> secondaries;

    ShoppingList(Map<Ingredient, Integer> herbs, Map<Ingredient, Integer> secondaries)
    {
        this.herbs = Collections.unmodifiableMap(herbs);
        this.secondaries = Collections.unmodifiableMap(secondaries);
    }

    public Map<Ingredient, Integer> herbs() { return herbs; }
    public Map<Ingredient, Integer> secondaries() { return secondaries; }

    public boolean isEmpty()
    {
        return herbs.isEmpty() && secondaries.isEmpty();
    }

    static ShoppingList empty()
    {
        return new ShoppingList(new EnumMap<>(Ingredient.class), new EnumMap<>(Ingredient.class));
    }
}
