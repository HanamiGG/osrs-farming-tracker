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
    private final Map<String, PatchState> patches = new ConcurrentHashMap<>();

    public Map<String, PatchState> patches()
    {
        return patches;
    }
}
