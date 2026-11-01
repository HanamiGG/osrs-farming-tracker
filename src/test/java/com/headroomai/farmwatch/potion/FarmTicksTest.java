package com.headroomai.farmwatch.potion;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FarmTicksTest
{
    private static final int RATE = 20;
    private static final Instant NOON = Instant.parse("2026-10-31T12:00:00Z");

    @Test
    public void nullReferenceReturnsNull()
    {
        assertNull(FarmTicks.readyAt(null, 4, RATE, 0, 20));
    }

    @Test
    public void withoutOffsetFallsBackToNaiveProjectionOnGrid()
    {
        // Reference on a 20-min boundary + 4 ticks = 80 min later.
        assertEquals(NOON.plusSeconds(4 * 20 * 60),
            FarmTicks.readyAt(NOON, 4, RATE, null, null));
    }

    @Test
    public void withoutOffsetReferenceOffGridSnapsDownward()
    {
        // 12:05 observed, 2 ticks remain. Grid is :00/:20/:40 (zero offset),
        // so floor(12:05) = 12:00, and ready is 12:00 + 2*20 = 12:40.
        // This is intentional: without a learned offset we assume the grid
        // is wall-clock-aligned.
        Instant t = Instant.parse("2026-10-31T12:05:00Z");
        assertEquals(Instant.parse("2026-10-31T12:40:00Z"),
            FarmTicks.readyAt(t, 2, RATE, null, null));
    }

    @Test
    public void precisionBelowTickRateIsIgnored()
    {
        // precision 10 min < tickRate 20 and < 40 -> offset must not apply.
        // Should match the no-offset result.
        Instant actual = FarmTicks.readyAt(NOON, 3, RATE, 7, 10);
        Instant expected = FarmTicks.readyAt(NOON, 3, RATE, null, null);
        assertEquals(expected, actual);
    }

    @Test
    public void precisionBelowTickRateButAtLeast40IsHonored()
    {
        // Edge case from the stock plugin: precision >= 40 unlocks offset
        // use even when precision < tickRate. Only relevant for crops with
        // tickRate > 40 (e.g. trees); herbs never hit this path, but we
        // match the stock behavior for forward-compat.
        int longTick = 80;
        long result = FarmTicks.offsetSeconds(longTick, 50, 40);
        assertEquals(50L * 60L, result);
    }

    @Test
    public void offsetShiftsGrid()
    {
        // offset=5 min -> grid is :05/:25/:45. Patch observed at 12:00 UTC,
        // 1 tick to go. Next grid line after (12:00 + 5min offset) floored
        // and then +20min - offset is 12:25.
        //   adjusted = 12:00 + 300s = 12:05
        //   floored  = 12:00 (12:05 mod 20min = 5min)
        //   result   = 12:00 + 1200 - 300 = 12:15... wait, let me recompute.
        // adjusted = epoch(12:00) + 300 = epoch(12:05)
        // floored  = epoch(12:05) - (epoch(12:05) % 1200)
        //          = epoch(12:05) - 300 = epoch(12:00)   [since 12:05 = N*1200+300]
        // result   = epoch(12:00) + 1200 - 300 = epoch(12:00) + 900 = epoch(12:15)
        //
        // That IS the next farm-tick on a :15/:35/:55 grid? no — offset=5
        // means grid is at times where (epoch+300) % 1200 == 0, i.e. epoch
        // mod 1200 == 900, i.e. :15/:35/:55 past the hour. So from 12:00
        // the next grid line is 12:15. ✓
        assertEquals(Instant.parse("2026-10-31T12:15:00Z"),
            FarmTicks.readyAt(NOON, 1, RATE, 5, 20));
    }

    @Test
    public void offsetBeyondTickRateIsModded()
    {
        // offset=125 min % tickRate 20 = 5 min. Same answer as offset=5.
        assertEquals(FarmTicks.readyAt(NOON, 1, RATE, 5, 20),
            FarmTicks.readyAt(NOON, 1, RATE, 125, 20));
    }

    @Test
    public void negativeTicksClampToZero()
    {
        // "Ready in 0 more ticks" -> the floored boundary itself (sans offset).
        assertEquals(FarmTicks.readyAt(NOON, 0, RATE, null, null),
            FarmTicks.readyAt(NOON, -3, RATE, null, null));
    }

    @Test
    public void zeroTickRateDegradesToReference()
    {
        assertEquals(NOON, FarmTicks.readyAt(NOON, 4, 0, null, null));
    }

    @Test
    public void stageProgressionMatchesStockPluginFormula()
    {
        // Direct transcription of the stock code applied by hand with
        // tickRate=20, ticks=4, offset=3, precision=20:
        //   offsetSec = 3 * 60 = 180
        //   epoch(12:00) = 43200
        //   adjusted = 43380
        //   floored  = 43380 - (43380 % 1200) = 43380 - 180 = 43200
        //   result   = 43200 + 4800 - 180 = 47820  == 13:17:00Z
        assertEquals(Instant.parse("2026-10-31T13:17:00Z"),
            FarmTicks.readyAt(NOON, 4, RATE, 3, 20));
    }
}
