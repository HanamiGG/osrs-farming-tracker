package com.headroomai.farmwatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Centralized JSON encoding. Instant -> ISO-8601 UTC string. Account hash as
 * decimal string (JS doesn't preserve full long precision as a number).
 */
public final class JsonCodec
{
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>()
        {
            @Override
            public void write(JsonWriter out, Instant value) throws IOException
            {
                if (value == null) { out.nullValue(); return; }
                out.value(DateTimeFormatter.ISO_INSTANT.format(value));
            }

            @Override
            public Instant read(JsonReader in) throws IOException
            {
                return Instant.parse(in.nextString());
            }
        })
        .create();

    private JsonCodec() {}

    public static byte[] encodeAll(FarmwatchState state)
    {
        Envelope env = new Envelope();
        env.version = FarmwatchState.SCHEMA_VERSION;
        env.generatedAt = state.generatedAt();
        env.pluginVersion = "0.1.0";
        env.varbitMap = loadFarmingVarbitMap();
        for (AccountState acc : state.all())
        {
            env.accounts.add(toDto(acc));
        }
        return GSON.toJson(env).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] encodeOne(FarmwatchState state, AccountState acc)
    {
        Envelope env = new Envelope();
        env.version = FarmwatchState.SCHEMA_VERSION;
        env.generatedAt = state.generatedAt();
        env.pluginVersion = "0.1.0";
        env.varbitMap = loadFarmingVarbitMap();
        env.accounts.add(toDto(acc));
        return GSON.toJson(env).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Reflects net.runelite.api.gameval.VarbitID for FARMING_TRANSMIT_* fields so
     * the web decoder can resolve FarmingWorld's symbolic patch references to the
     * numeric varbit IDs that appear as keys in the exported state. Cached after
     * the first call. If VarbitID is absent (shouldn't happen in a RuneLite plugin
     * runtime), returns null and the web app falls back to raw-numeric mode.
     */
    private static volatile Map<String, Integer> varbitMapCache;

    private static Map<String, Integer> loadFarmingVarbitMap()
    {
        Map<String, Integer> cached = varbitMapCache;
        if (cached != null) return cached;
        try
        {
            Class<?> cls = Class.forName("net.runelite.api.gameval.VarbitID");
            Map<String, Integer> m = new TreeMap<>();
            for (Field f : cls.getDeclaredFields())
            {
                if (!f.getName().startsWith("FARMING_TRANSMIT_")) continue;
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!int.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                m.put(f.getName(), f.getInt(null));
            }
            varbitMapCache = m;
            return m;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    public static byte[] encodeAccountList(FarmwatchState state)
    {
        List<AccountSummary> out = new ArrayList<>();
        for (AccountState acc : state.all())
        {
            AccountSummary s = new AccountSummary();
            s.hash = Long.toUnsignedString(acc.getHash());
            s.displayName = acc.getDisplayName();
            s.lastSeen = acc.getLastSeen();
            out.add(s);
        }
        return GSON.toJson(out).getBytes(StandardCharsets.UTF_8);
    }

    private static AccountDto toDto(AccountState acc)
    {
        AccountDto a = new AccountDto();
        a.hash = Long.toUnsignedString(acc.getHash());
        a.displayName = acc.getDisplayName();
        a.lastSeen = acc.getLastSeen();
        a.farmTickOffset = acc.getFarmTickOffset();
        a.farmTickOffsetPrecision = acc.getFarmTickOffsetPrecision();
        a.patches = new ArrayList<>(acc.patches().values());
        return a;
    }

    // ---- DTOs ----

    private static final class Envelope
    {
        int version;
        Instant generatedAt;
        String pluginVersion;
        Map<String, Integer> varbitMap;
        List<AccountDto> accounts = new ArrayList<>();
    }

    private static final class AccountDto
    {
        String hash;
        String displayName;
        Instant lastSeen;
        Integer farmTickOffset;
        Integer farmTickOffsetPrecision;
        List<PatchState> patches;
    }

    private static final class AccountSummary
    {
        String hash;
        String displayName;
        Instant lastSeen;
    }
}
