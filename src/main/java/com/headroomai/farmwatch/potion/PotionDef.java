package com.headroomai.farmwatch.potion;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One potion the planner knows how to make. Recipes are flattened to raw
 * ingredients up front so the planner stays a trivial map-merge — e.g.
 * Super combat lists its four herbs and three secondaries directly, not
 * "needs 1 of each build-up potion". This keeps the planner readable
 * and the UI honest ("to brew 500 Super combats you need 500 torstol,
 * 500 irit, 500 kwuarm, …") rather than hiding ingredients behind a
 * recipe tree.
 */
public final class PotionDef
{
    private final String name;
    private final Map<Ingredient, Integer> recipe;

    private PotionDef(String name, Map<Ingredient, Integer> recipe)
    {
        this.name = name;
        this.recipe = Collections.unmodifiableMap(recipe);
    }

    public String name() { return name; }

    /** Raw ingredients (flattened) and how many of each one brewed potion needs. */
    public Map<Ingredient, Integer> recipe() { return recipe; }

    public static Builder builder(String name)
    {
        return new Builder(name);
    }

    public static final class Builder
    {
        private final String name;
        private final Map<Ingredient, Integer> recipe = new EnumMap<>(Ingredient.class);

        private Builder(String name)
        {
            this.name = name;
        }

        public Builder add(Ingredient ingredient, int qty)
        {
            if (qty <= 0)
            {
                throw new IllegalArgumentException("qty must be positive");
            }
            recipe.merge(ingredient, qty, Integer::sum);
            return this;
        }

        public PotionDef build()
        {
            if (recipe.isEmpty())
            {
                throw new IllegalStateException("potion must have at least one ingredient");
            }
            return new PotionDef(name, new EnumMap<>(recipe));
        }
    }
}
