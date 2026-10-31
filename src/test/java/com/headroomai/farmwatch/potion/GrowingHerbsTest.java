package com.headroomai.farmwatch.potion;

import com.headroomai.farmwatch.FarmwatchState;
import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GrowingHerbsTest
{
    private FarmwatchState state;
    private static final long ACCT = 0xDEADBEEFL;

    @Before
    public void setUp()
    {
        state = new FarmwatchState();
    }

    private PatchState addPatch(int regionId, int varbitId, int varbitValue, Instant lastUpdated)
    {
        AccountState acc = state.touch(ACCT);
        PatchState p = new PatchState();
        p.setId(regionId + "." + varbitId);
        p.setRegionId(regionId);
        p.setVarbitId(varbitId);
        p.setVarbitValue(varbitValue);
        p.setLastUpdated(lastUpdated);
        acc.patches().put(p.getId(), p);
        return p;
    }

    @Test
    public void emptyStateProducesEmptyMap()
    {
        assertTrue(GrowingHerbs.aggregate(state, ACCT).isEmpty());
    }

    @Test
    public void unknownAccountReturnsEmpty()
    {
        addPatch(11062, 4771, 32, Instant.now()); // Ranarr growing in Catherby
        assertTrue(GrowingHerbs.aggregate(state, 999L).isEmpty());
    }

    @Test
    public void nullStateReturnsEmpty()
    {
        assertTrue(GrowingHerbs.aggregate(null, ACCT).isEmpty());
    }

    @Test
    public void sentinelAccountHashIsSkipped()
    {
        addPatch(11062, 4771, 32, Instant.now());
        // -1 is the stock plugin's "not logged in" sentinel; we must not
        // try to look it up in state.
        assertTrue(GrowingHerbs.aggregate(state, -1L).isEmpty());
    }

    @Test
    public void nonHerbPatchVarbitIsIgnored()
    {
        // Not in the HERB_PATCH_VARBIT_IDS whitelist — allotment, fruit
        // tree, etc. We should decline to decode it even if the varbit
        // value happens to overlap a herb's growing band.
        addPatch(11062, 9999, 32, Instant.now());
        assertTrue(GrowingHerbs.aggregate(state, ACCT).isEmpty());
    }

    @Test
    public void growingRanarrIsAggregated()
    {
        addPatch(11062, 4771, 32, Instant.parse("2026-10-31T12:00:00Z")); // just planted (stage 0)
        Map<HerbVarbitDecoder.Produce, GrowingHerbs.Aggregate> agg = GrowingHerbs.aggregate(state, ACCT);
        GrowingHerbs.Aggregate ran = agg.get(HerbVarbitDecoder.Produce.RANARR);
        assertEquals(1, ran.growingPatches);
        assertEquals(0, ran.harvestablePatches);
        // Stage 0 + 4 stages × 20 min = 80 min later.
        assertEquals(Instant.parse("2026-10-31T13:20:00Z"), ran.earliestReadyAt);
    }

    @Test
    public void twoRanarrPatchesPickTheEarliestReadyTime()
    {
        addPatch(11062, 4771, 32, Instant.parse("2026-10-31T12:00:00Z"));
        addPatch(12083, 4772, 34, Instant.parse("2026-10-31T12:30:00Z")); // stage 2 (40 min left)
        GrowingHerbs.Aggregate ran = GrowingHerbs.aggregate(state, ACCT).get(HerbVarbitDecoder.Produce.RANARR);
        assertEquals(2, ran.growingPatches);
        // 12:00 + 80 = 13:20; 12:30 + 40 = 13:10. Earliest: 13:10.
        assertEquals(Instant.parse("2026-10-31T13:10:00Z"), ran.earliestReadyAt);
    }

    @Test
    public void harvestableRanarrCountsSeparatelyWithNoReadyTime()
    {
        addPatch(11062, 4771, 37, Instant.parse("2026-10-31T12:00:00Z")); // harvestable
        GrowingHerbs.Aggregate ran = GrowingHerbs.aggregate(state, ACCT).get(HerbVarbitDecoder.Produce.RANARR);
        assertEquals(1, ran.harvestablePatches);
        assertEquals(0, ran.growingPatches);
        assertNull("earliestReadyAt only tracks growing patches", ran.earliestReadyAt);
    }

    @Test
    public void deadPatchDoesNotCreditAnyHerb()
    {
        addPatch(11062, 4771, 170, Instant.parse("2026-10-31T12:00:00Z")); // ANYHERB dead
        assertTrue("dead patches lose produce identity",
            GrowingHerbs.aggregate(state, ACCT).isEmpty());
    }

    @Test
    public void diseasedRanarrIsRecordedButNotYielding()
    {
        addPatch(11062, 4771, 141, Instant.parse("2026-10-31T12:00:00Z"));
        GrowingHerbs.Aggregate ran = GrowingHerbs.aggregate(state, ACCT).get(HerbVarbitDecoder.Produce.RANARR);
        assertEquals(1, ran.diseasedPatches);
        assertEquals(0, ran.yieldingPatches());
    }

    @Test
    public void weedsAndEmptyPatchesAreIgnored()
    {
        addPatch(11062, 4771, 0, Instant.now());    // weeds
        addPatch(12083, 4772, 180, Instant.now()); // empty filler band
        assertTrue(GrowingHerbs.aggregate(state, ACCT).isEmpty());
    }

    @Test
    public void mixedHerbsAggregateIndependently()
    {
        addPatch(11062, 4771, 32, Instant.parse("2026-10-31T12:00:00Z")); // ranarr growing
        addPatch(12083, 4772, 105, Instant.parse("2026-10-31T12:00:00Z")); // torstol growing stage 2
        addPatch(10290, 4773, 108, Instant.parse("2026-10-31T12:00:00Z")); // torstol harvestable
        Map<HerbVarbitDecoder.Produce, GrowingHerbs.Aggregate> agg = GrowingHerbs.aggregate(state, ACCT);
        assertEquals(1, agg.get(HerbVarbitDecoder.Produce.RANARR).growingPatches);
        assertEquals(1, agg.get(HerbVarbitDecoder.Produce.TORSTOL).growingPatches);
        assertEquals(1, agg.get(HerbVarbitDecoder.Produce.TORSTOL).harvestablePatches);
    }

    @Test
    public void growingPatchWithoutLastUpdatedIsCountedButSkipsReadyTime()
    {
        addPatch(11062, 4771, 32, null);
        GrowingHerbs.Aggregate ran = GrowingHerbs.aggregate(state, ACCT).get(HerbVarbitDecoder.Produce.RANARR);
        assertEquals(1, ran.growingPatches);
        assertNull("null lastUpdated must not contribute a ready-at",
            ran.earliestReadyAt);
    }

    @Test
    public void stagesLeftRespectsGrowthStageIndex()
    {
        Instant t = Instant.parse("2026-10-31T12:00:00Z");
        addPatch(11062, 4771, 35, t); // ranarr growing stage 3 (one tick from ready)
        GrowingHerbs.Aggregate ran = GrowingHerbs.aggregate(state, ACCT).get(HerbVarbitDecoder.Produce.RANARR);
        assertEquals(t.plus(Duration.ofMinutes(20)), ran.earliestReadyAt);
    }
}
