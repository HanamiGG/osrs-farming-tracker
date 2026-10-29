package com.headroomai.farmwatch.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;

@Data
public class AccountState
{
    private long hash;
    private String displayName;
    private Instant lastSeen;
    /** From stock tracker's FARM_TICK_OFFSET, in minutes. Null when not yet observed. */
    private Integer farmTickOffset;
    /** From stock tracker's FARM_TICK_OFFSET_PRECISION, in minutes. */
    private Integer farmTickOffsetPrecision;
    private final Map<String, PatchState> patches = new ConcurrentHashMap<>();

    public Map<String, PatchState> patches()
    {
        return patches;
    }
}
