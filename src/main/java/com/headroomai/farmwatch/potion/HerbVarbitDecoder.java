package com.headroomai.farmwatch.potion;

/**
 * Decodes a herb-patch varbit value into (produce, crop state, stage).
 *
 * <p>Ported from
 * {@code net.runelite.client.plugins.timetracking.farming.PatchImplementation.HERB.forVarbitValue(int)}
 * in the stock Time Tracking plugin (RuneLite client 1.13.4) — that
 * method is package-private, so we can't call it from our package.
 * Keep this in sync with upstream when a new herb lands (Huasca was
 * added in a recent desert update).
 *
 * <p>Only HERB patches are decoded. Other patch types (allotment,
 * fruit tree, tree, bush, ...) use different varbit encodings and
 * would decode to garbage here, so the planner guards callers with
 * {@link #isKnownHerbPatchVarbitId(int)}.
 *
 * <p>Varbit value ranges (inclusive):
 * <pre>
 *   0-3      WEEDS growing
 *   4-10     GUAM (4-7 growing, 8-10 harvestable)
 *   11-17    MARRENTILL
 *   18-24    TARROMIN
 *   25-31    HARRALANDER
 *   32-38    RANARR
 *   39-45    TOADFLAX
 *   46-52    IRIT
 *   53-59    AVANTOE
 *   60-66    HUASCA
 *   67       (reserved empty slot — treated as weeds)
 *   68-74    KWUARM
 *   75-81    SNAPDRAGON
 *   82-88    CADANTINE
 *   89-95    LANTADYME
 *   96-102   DWARF_WEED
 *   103-109  TORSTOL
 *   128-130  GUAM diseased
 *   131-133  MARRENTILL diseased
 *   134-136  TARROMIN diseased
 *   137-139  HARRALANDER diseased
 *   140-142  RANARR diseased
 *   143-145  TOADFLAX diseased
 *   146-148  IRIT diseased
 *   149-151  AVANTOE diseased
 *   152-154  KWUARM diseased
 *   155-157  SNAPDRAGON diseased
 *   158-160  CADANTINE diseased
 *   161-163  LANTADYME diseased
 *   164-166  DWARF_WEED diseased
 *   167-169  TORSTOL diseased
 *   170-172  ANYHERB dead (identity is lost on death)
 *   173-175  HUASCA diseased
 *   176-191  WEEDS (empty / cleared patch)
 * </pre>
 */
public final class HerbVarbitDecoder
{
    /** Varbit IDs known to belong to herb patches, per the stock plugin's FarmingTracker.
     *  Keep this list up to date when Jagex ships new herb patches. */
    private static final int[] HERB_PATCH_VARBIT_IDS = {
        4771, // Catherby
        4772, // Falador
        4773, // Ardougne
        4774, // Morytania (Canifis)
        4775, // Trollheim
        7904, // Harmony Island
        7905, // Weiss
        7906, // Hosidius
        7907, // Farming Guild (west)
        10981 // Civitas illa Fortis (Varlamore)
    };

    private HerbVarbitDecoder() {}

    public enum Produce
    {
        WEEDS, GUAM, MARRENTILL, TARROMIN, HARRALANDER, RANARR, TOADFLAX,
        IRIT, AVANTOE, HUASCA, KWUARM, SNAPDRAGON, CADANTINE, LANTADYME,
        DWARF_WEED, TORSTOL, ANYHERB
    }

    public enum CropState
    {
        GROWING, HARVESTABLE, DISEASED, DEAD
    }

    public static final class Decoded
    {
        public final Produce produce;
        public final CropState state;
        /** Index inside the (produce, state) range. For GROWING, 0 = just planted. */
        public final int stage;

        public Decoded(Produce produce, CropState state, int stage)
        {
            this.produce = produce;
            this.state = state;
            this.stage = stage;
        }

        /** True iff this decode names a specific herb (not weeds/anyherb). */
        public boolean isSpecificHerb()
        {
            return produce != Produce.WEEDS && produce != Produce.ANYHERB;
        }
    }

    public static boolean isKnownHerbPatchVarbitId(int varbitId)
    {
        for (int id : HERB_PATCH_VARBIT_IDS)
        {
            if (id == varbitId) return true;
        }
        return false;
    }

    /**
     * Map an Ingredient to the Produce the decoder emits for that herb.
     * Returns null for non-herb ingredients.
     */
    public static Produce produceFor(Ingredient ingredient)
    {
        if (ingredient == null || ingredient.type() != Ingredient.Type.HERB) return null;
        switch (ingredient)
        {
            case GUAM:        return Produce.GUAM;
            case MARRENTILL:  return Produce.MARRENTILL;
            case TARROMIN:    return Produce.TARROMIN;
            case HARRALANDER: return Produce.HARRALANDER;
            case RANARR:      return Produce.RANARR;
            case TOADFLAX:    return Produce.TOADFLAX;
            case IRIT:        return Produce.IRIT;
            case AVANTOE:     return Produce.AVANTOE;
            case KWUARM:      return Produce.KWUARM;
            case SNAPDRAGON:  return Produce.SNAPDRAGON;
            case CADANTINE:   return Produce.CADANTINE;
            case LANTADYME:   return Produce.LANTADYME;
            case DWARF_WEED:  return Produce.DWARF_WEED;
            case TORSTOL:     return Produce.TORSTOL;
            default:          return null;
        }
    }

    /** Returns null for varbit values that don't correspond to a known HERB state. */
    public static Decoded decode(int v)
    {
        if (v >= 0 && v <= 3)   return new Decoded(Produce.WEEDS,       CropState.GROWING,     3 - v);
        if (v >= 4 && v <= 7)   return new Decoded(Produce.GUAM,        CropState.GROWING,     v - 4);
        if (v >= 8 && v <= 10)  return new Decoded(Produce.GUAM,        CropState.HARVESTABLE, 10 - v);
        if (v >= 11 && v <= 14) return new Decoded(Produce.MARRENTILL,  CropState.GROWING,     v - 11);
        if (v >= 15 && v <= 17) return new Decoded(Produce.MARRENTILL,  CropState.HARVESTABLE, 17 - v);
        if (v >= 18 && v <= 21) return new Decoded(Produce.TARROMIN,    CropState.GROWING,     v - 18);
        if (v >= 22 && v <= 24) return new Decoded(Produce.TARROMIN,    CropState.HARVESTABLE, 24 - v);
        if (v >= 25 && v <= 28) return new Decoded(Produce.HARRALANDER, CropState.GROWING,     v - 25);
        if (v >= 29 && v <= 31) return new Decoded(Produce.HARRALANDER, CropState.HARVESTABLE, 31 - v);
        if (v >= 32 && v <= 35) return new Decoded(Produce.RANARR,      CropState.GROWING,     v - 32);
        if (v >= 36 && v <= 38) return new Decoded(Produce.RANARR,      CropState.HARVESTABLE, 38 - v);
        if (v >= 39 && v <= 42) return new Decoded(Produce.TOADFLAX,    CropState.GROWING,     v - 39);
        if (v >= 43 && v <= 45) return new Decoded(Produce.TOADFLAX,    CropState.HARVESTABLE, 45 - v);
        if (v >= 46 && v <= 49) return new Decoded(Produce.IRIT,        CropState.GROWING,     v - 46);
        if (v >= 50 && v <= 52) return new Decoded(Produce.IRIT,        CropState.HARVESTABLE, 52 - v);
        if (v >= 53 && v <= 56) return new Decoded(Produce.AVANTOE,     CropState.GROWING,     v - 53);
        if (v >= 57 && v <= 59) return new Decoded(Produce.AVANTOE,     CropState.HARVESTABLE, 59 - v);
        if (v >= 60 && v <= 63) return new Decoded(Produce.HUASCA,      CropState.GROWING,     v - 60);
        if (v >= 64 && v <= 66) return new Decoded(Produce.HUASCA,      CropState.HARVESTABLE, 66 - v);
        if (v == 67)            return new Decoded(Produce.WEEDS,       CropState.GROWING,     3);
        if (v >= 68 && v <= 71) return new Decoded(Produce.KWUARM,      CropState.GROWING,     v - 68);
        if (v >= 72 && v <= 74) return new Decoded(Produce.KWUARM,      CropState.HARVESTABLE, 74 - v);
        if (v >= 75 && v <= 78) return new Decoded(Produce.SNAPDRAGON,  CropState.GROWING,     v - 75);
        if (v >= 79 && v <= 81) return new Decoded(Produce.SNAPDRAGON,  CropState.HARVESTABLE, 81 - v);
        if (v >= 82 && v <= 85) return new Decoded(Produce.CADANTINE,   CropState.GROWING,     v - 82);
        if (v >= 86 && v <= 88) return new Decoded(Produce.CADANTINE,   CropState.HARVESTABLE, 88 - v);
        if (v >= 89 && v <= 92) return new Decoded(Produce.LANTADYME,   CropState.GROWING,     v - 89);
        if (v >= 93 && v <= 95) return new Decoded(Produce.LANTADYME,   CropState.HARVESTABLE, 95 - v);
        if (v >= 96 && v <= 99) return new Decoded(Produce.DWARF_WEED,  CropState.GROWING,     v - 96);
        if (v >= 100 && v <= 102) return new Decoded(Produce.DWARF_WEED, CropState.HARVESTABLE, 102 - v);
        if (v >= 103 && v <= 106) return new Decoded(Produce.TORSTOL,   CropState.GROWING,     v - 103);
        if (v >= 107 && v <= 109) return new Decoded(Produce.TORSTOL,   CropState.HARVESTABLE, 109 - v);
        if (v >= 128 && v <= 130) return new Decoded(Produce.GUAM,        CropState.DISEASED, v - 127);
        if (v >= 131 && v <= 133) return new Decoded(Produce.MARRENTILL,  CropState.DISEASED, v - 130);
        if (v >= 134 && v <= 136) return new Decoded(Produce.TARROMIN,    CropState.DISEASED, v - 133);
        if (v >= 137 && v <= 139) return new Decoded(Produce.HARRALANDER, CropState.DISEASED, v - 136);
        if (v >= 140 && v <= 142) return new Decoded(Produce.RANARR,      CropState.DISEASED, v - 139);
        if (v >= 143 && v <= 145) return new Decoded(Produce.TOADFLAX,    CropState.DISEASED, v - 142);
        if (v >= 146 && v <= 148) return new Decoded(Produce.IRIT,        CropState.DISEASED, v - 145);
        if (v >= 149 && v <= 151) return new Decoded(Produce.AVANTOE,     CropState.DISEASED, v - 148);
        if (v >= 152 && v <= 154) return new Decoded(Produce.KWUARM,      CropState.DISEASED, v - 151);
        if (v >= 155 && v <= 157) return new Decoded(Produce.SNAPDRAGON,  CropState.DISEASED, v - 154);
        if (v >= 158 && v <= 160) return new Decoded(Produce.CADANTINE,   CropState.DISEASED, v - 157);
        if (v >= 161 && v <= 163) return new Decoded(Produce.LANTADYME,   CropState.DISEASED, v - 160);
        if (v >= 164 && v <= 166) return new Decoded(Produce.DWARF_WEED,  CropState.DISEASED, v - 163);
        if (v >= 167 && v <= 169) return new Decoded(Produce.TORSTOL,     CropState.DISEASED, v - 166);
        if (v >= 170 && v <= 172) return new Decoded(Produce.ANYHERB,     CropState.DEAD,     v - 169);
        if (v >= 173 && v <= 175) return new Decoded(Produce.HUASCA,      CropState.DISEASED, v - 172);
        if (v >= 176 && v <= 191) return new Decoded(Produce.WEEDS,       CropState.GROWING,  3);
        return null;
    }
}
