package com.headroomai.farmwatch;

import com.headroomai.farmwatch.model.AccountState;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class FarmwatchState
{
    public static final int SCHEMA_VERSION = 1;

    private final ConcurrentHashMap<Long, AccountState> accounts = new ConcurrentHashMap<>();
    private volatile Instant generatedAt = Instant.now();

    public AccountState touch(long accountHash)
    {
        return accounts.computeIfAbsent(accountHash, h -> {
            AccountState a = new AccountState();
            a.setHash(h);
            a.setLastSeen(Instant.now());
            return a;
        });
    }

    public AccountState get(long accountHash)
    {
        return accounts.get(accountHash);
    }

    public Collection<AccountState> all()
    {
        return accounts.values();
    }

    public Instant generatedAt()
    {
        return generatedAt;
    }

    public void touchGeneratedAt()
    {
        generatedAt = Instant.now();
    }

    public int accountCount()
    {
        return accounts.size();
    }
}
