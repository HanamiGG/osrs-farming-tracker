package com.headroomai.farmwatch.potion;

import java.time.Instant;

/**
 * Projects a growing patch forward to the moment it becomes harvestable,
 * snapped to the game's global farm-tick clock when the stock Time
 * Tracking plugin has learned a per-account offset.
 *
 * <p>Formula ported from
 * {@code net.runelite.client.plugins.timetracking.farming.FarmingTracker#getTickTime(int,int,long,String)}
 * (RuneLite client 1.13.4). The stock plugin only applies the offset
 * when precision is at least tickRate or 40 minutes; below that the
 * learned offset is too noisy, so it falls through to zero. We follow
 * the same rule for behavioural parity.
 *
 * <p>Null offset / precision collapse to the naive "reference + ticks ×
 * rate" projection, which is also what the panel showed before
 * alignment was wired in.
 */
public final class FarmTicks
{
    public static final int HERB_TICK_RATE_MINUTES = 20;

    private FarmTicks() {}

    /**
     * @param reference moment at which the patch was observed at its current stage
     * @param ticks     number of farm-ticks until harvest (≥ 0)
     * @param tickRate  minutes per farm-tick for this crop (20 for herbs)
     * @param offset    stock plugin's farmTickOffset for this account (minutes), or null
     * @param precision stock plugin's farmTickOffsetPrecision for this account (minutes), or null
     */
    public static Instant readyAt(Instant reference, int ticks, int tickRate, Integer offset, Integer precision)
    {
        if (reference == null) return null;
        if (ticks < 0) ticks = 0;
        if (tickRate <= 0) return reference;

        long epochSec = reference.getEpochSecond();
        long tickRateSec = (long) tickRate * 60L;
        long offsetSec = offsetSeconds(tickRate, offset, precision);
        long adjusted = epochSec + offsetSec;
        long floored = adjusted - Math.floorMod(adjusted, tickRateSec);
        long result = floored + (long) ticks * tickRateSec - offsetSec;
        return Instant.ofEpochSecond(result);
    }

    static long offsetSeconds(int tickRate, Integer offset, Integer precision)
    {
        if (offset == null || precision == null) return 0L;
        if (precision < tickRate && precision < 40) return 0L;
        return (long) Math.floorMod(offset, tickRate) * 60L;
    }
}
