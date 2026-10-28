package com.headroomai.farmwatch;

import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

/**
 * Reads farming state by mirroring the stock Time Tracking plugin's config,
 * NOT by trying to iterate FarmingPatch.values() — that enum is
 * package-private in net.runelite.client.plugins.timetracking.farming so a
 * third-party plugin cannot reach it directly.
 *
 * Config format persisted by the stock tracker (confirmed from source):
 *
 *   group = "timetracking"
 *   key   = "<regionID>.<varbitID>"      (both ints)
 *   value = "<varbitValue>:<unixTime>"
 *   stored per-RSProfile.
 *
 * We listen for ConfigChanged on that group and mirror each entry into our
 * PatchState keyed by "<regionID>.<varbitID>". Decoding regionID/varbitID
 * into a human-readable patch name + produce happens on the web side via a
 * decoder table (web/patch-decoder.json, TODO v1.1 — v1 ships with the
 * raw numeric data and a stub decoder for a handful of common patches).
 *
 * Compost + protection are tracked by the stock plugin's CompostTracker /
 * PaymentTracker siblings under different config key patterns. Mirroring
 * those is deferred to v1.1 — see TODO below.
 */
@Slf4j
public class PatchReader
{
    /** Matches the stock tracker's key shape: "<regionID>.<varbitID>", both ints. */
    private static final Pattern KEY_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");

    /** Matches the stock tracker's value shape: "<varbitValue>:<unixTime>". */
    private static final Pattern VALUE_PATTERN = Pattern.compile("^(-?\\d+):(\\d+)$");

    private final Client client;
    private final ConfigManager configManager;
    private final FarmwatchState state;

    public PatchReader(Client client, ConfigManager configManager, FarmwatchState state)
    {
        this.client = client;
        this.configManager = configManager;
        this.state = state;
    }

    /**
     * Called on login — pull all currently-persisted stock tracker entries
     * for the active RSProfile and copy them into our state.
     *
     * TODO(prime): the exact ConfigManager API for listing keys under a
     * profile isn't bullet-proof here. In the current RuneLite master,
     * ConfigManager has getConfigurationKeys(String prefix) that returns
     * fully-qualified keys of the form "<group>.<profile>.<key>". A safer
     * initial implementation iterates those and filters for ones matching
     * KEY_PATTERN after the profile segment. Flip to that when wiring.
     *
     * For now this is a no-op; the per-event handler below will populate
     * state as the user moves around and the stock tracker emits updates.
     */
    public void primeFromStockTracker(long accountHash)
    {
        log.debug("primeFromStockTracker({}) - deferred, see TODO in PatchReader", accountHash);
    }

    /**
     * Varbit changes themselves don't carry useful data for us — the stock
     * tracker processes them, decodes them, and persists via ConfigChanged
     * downstream, which is where we pick them up. So this is a no-op.
     *
     * @return false, always.
     */
    public boolean handleVarbit(VarbitChanged e)
    {
        return false;
    }

    /**
     * Mirror the stock tracker's per-patch config entries into our state.
     *
     * @return true if we matched and copied a timetracking patch entry.
     */
    public boolean handleStockConfigChange(ConfigChanged e)
    {
        if (!"timetracking".equals(e.getGroup()))
        {
            return false;
        }

        String key = e.getKey();
        if (key == null)
        {
            return false;
        }

        Matcher km = KEY_PATTERN.matcher(key);
        if (!km.matches())
        {
            // Not a "<regionID>.<varbitID>" entry — could be compost, payment,
            // notify flags, autoweed, or FARM_TICK_OFFSET. v1 ignores those.
            // TODO(v1.1): recognize compost.* and payment.* keys and mirror
            // them into PatchState.compost / PatchState.protectedPaid.
            return false;
        }

        int regionId = Integer.parseInt(km.group(1));
        int varbitId = Integer.parseInt(km.group(2));

        String value = e.getNewValue();
        if (value == null || value.isEmpty())
        {
            return false;
        }

        Matcher vm = VALUE_PATTERN.matcher(value);
        if (!vm.matches())
        {
            log.debug("Farmwatch: timetracking key {} has unexpected value shape {}", key, value);
            return false;
        }

        int varbitValue = Integer.parseInt(vm.group(1));
        long unixTime = Long.parseLong(vm.group(2));

        long accountHash = client.getAccountHash();
        if (accountHash == -1)
        {
            return false;
        }

        AccountState acc = state.touch(accountHash);
        PatchState p = acc.patches().computeIfAbsent(key, k -> {
            PatchState ps = new PatchState();
            ps.setId(k);
            ps.setRegionId(regionId);
            ps.setVarbitId(varbitId);
            return ps;
        });
        p.setVarbitValue(varbitValue);
        p.setLastUpdated(Instant.ofEpochSecond(unixTime));
        // state/produce/compost/protection are left as decoded by the web
        // side (or empty, in which case the UI falls back to the raw view).
        acc.setLastSeen(Instant.now());

        return true;
    }
}
