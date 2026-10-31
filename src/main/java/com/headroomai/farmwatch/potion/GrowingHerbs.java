package com.headroomai.farmwatch.potion;

import com.headroomai.farmwatch.FarmwatchState;
import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Scans the current account's herb patches in {@link FarmwatchState} and
 * aggregates them by produce: how many of each herb are growing or
 * harvestable, and when the earliest one will be ready.
 *
 * <p>Each herb has 4 growing stages × 20 min = 80 min total. We don't
 * try to align to the game's farm-tick clock (the stock Time Tracking
 * plugin stores a per-account offset for that); the planner just needs
 * an approximate "~HH:MM" so the user knows roughly when to log in.
 */
public final class GrowingHerbs
{
    /** Minutes per growth stage for herbs (all herbs are 20-min tick, 4 stages). */
    public static final int HERB_STAGE_MINUTES = 20;
    public static final int HERB_TOTAL_STAGES = 4;

    public static final class Aggregate
    {
        public final HerbVarbitDecoder.Produce produce;
        public final int harvestablePatches;
        public final int growingPatches;
        public final int diseasedPatches;
        /** Earliest moment any still-growing patch of this herb becomes harvestable;
         *  null if none are growing (already ready, or none at all). */
        public final Instant earliestReadyAt;

        public Aggregate(HerbVarbitDecoder.Produce produce,
                         int harvestablePatches,
                         int growingPatches,
                         int diseasedPatches,
                         Instant earliestReadyAt)
        {
            this.produce = produce;
            this.harvestablePatches = harvestablePatches;
            this.growingPatches = growingPatches;
            this.diseasedPatches = diseasedPatches;
            this.earliestReadyAt = earliestReadyAt;
        }

        /** Patches that would yield herbs if harvested now + patches that will yield once grown. */
        public int yieldingPatches()
        {
            return harvestablePatches + growingPatches;
        }
    }

    private GrowingHerbs() {}

    /**
     * Returns an aggregate per Produce for the given account. Returns an
     * empty map if the account is unknown or has no herb patches on
     * record. Called from the Swing EDT; FarmwatchState's inner maps are
     * {@code ConcurrentHashMap} so a race with the client thread's writes
     * is safe (we may see a slightly stale snapshot, which is fine for
     * a planner).
     */
    public static Map<HerbVarbitDecoder.Produce, Aggregate> aggregate(FarmwatchState state, long accountHash)
    {
        EnumMap<HerbVarbitDecoder.Produce, int[]> counts = new EnumMap<>(HerbVarbitDecoder.Produce.class);
        EnumMap<HerbVarbitDecoder.Produce, Instant> earliestReady = new EnumMap<>(HerbVarbitDecoder.Produce.class);

        if (state == null || accountHash == -1)
        {
            return new EnumMap<>(HerbVarbitDecoder.Produce.class);
        }
        AccountState acc = state.get(accountHash);
        if (acc == null)
        {
            return new EnumMap<>(HerbVarbitDecoder.Produce.class);
        }

        for (PatchState p : acc.patches().values())
        {
            if (!HerbVarbitDecoder.isKnownHerbPatchVarbitId(p.getVarbitId())) continue;
            HerbVarbitDecoder.Decoded d = HerbVarbitDecoder.decode(p.getVarbitValue());
            if (d == null || !d.isSpecificHerb()) continue;

            int[] slot = counts.computeIfAbsent(d.produce, k -> new int[3]); // [harvestable, growing, diseased]
            switch (d.state)
            {
                case HARVESTABLE:
                    slot[0]++;
                    break;
                case GROWING:
                    slot[1]++;
                    Instant last = p.getLastUpdated();
                    if (last != null)
                    {
                        int stagesLeft = HERB_TOTAL_STAGES - d.stage;
                        Instant readyAt = last.plus(Duration.ofMinutes((long) stagesLeft * HERB_STAGE_MINUTES));
                        earliestReady.merge(d.produce, readyAt, (a, b) -> a.isBefore(b) ? a : b);
                    }
                    break;
                case DISEASED:
                    slot[2]++;
                    break;
                case DEAD:
                default:
                    break;
            }
        }

        EnumMap<HerbVarbitDecoder.Produce, Aggregate> out = new EnumMap<>(HerbVarbitDecoder.Produce.class);
        for (Map.Entry<HerbVarbitDecoder.Produce, int[]> e : counts.entrySet())
        {
            int[] c = e.getValue();
            out.put(e.getKey(), new Aggregate(e.getKey(), c[0], c[1], c[2], earliestReady.get(e.getKey())));
        }
        return out;
    }
}
