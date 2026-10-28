package com.headroomai.farmwatch.model;

/**
 * Minimal crop-definition shim. The authoritative source is RuneLite's
 * Produce enum (growth cycle minutes, protection payment, stages). This
 * class exists only as a placeholder for the fallback path where we need
 * to compute readyAt without having the full Produce enum resolved — e.g.
 * when we read patch state from the stock plugin's config profile and
 * only know the produce by name.
 *
 * TODO: once PatchReader is wired to the stock Produce enum, this class
 * either (a) wraps it as a trivial record, or (b) gets deleted.
 */
public final class CropDef
{
    public final String produce;
    public final int cycleMinutes;

    public CropDef(String produce, int cycleMinutes)
    {
        this.produce = produce;
        this.cycleMinutes = cycleMinutes;
    }
}
