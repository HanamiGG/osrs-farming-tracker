package com.headroomai.farmwatch;

import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

/**
 * Mirrors the stock Time Tracking plugin's config into our state.
 *
 * Stock plugin key formats (confirmed from source):
 *   timetracking.<rsprofile>.<regionID>.<varbitID>          = "<varbitValue>:<unixTime>"
 *   timetracking.<rsprofile>.<regionID>.<varbitID>.compost  = "<CompostState enum name>"
 *   timetracking.<rsprofile>.<regionID>.<varbitID>.protected= "true"  (key unset when false)
 *
 * On login we prime from the current RSProfile via
 * ConfigManager#getRSProfileConfigurationKeys so previously-seen patches
 * appear immediately. ConfigChanged events keep state live afterwards.
 */
@Slf4j
public class PatchReader
{
    private static final Pattern VARBIT_KEY     = Pattern.compile("^(\\d+)\\.(\\d+)$");
    private static final Pattern COMPOST_KEY    = Pattern.compile("^(\\d+)\\.(\\d+)\\.compost$");
    private static final Pattern PROTECTED_KEY  = Pattern.compile("^(\\d+)\\.(\\d+)\\.protected$");
    private static final Pattern VARBIT_VALUE   = Pattern.compile("^(-?\\d+):(\\d+)$");
    private static final String TICK_OFFSET_KEY           = "farmTickOffset";
    private static final String TICK_OFFSET_PRECISION_KEY = "farmTickOffsetPrecision";

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
     * Called on login. Pulls every timetracking entry stored for the current
     * RSProfile and mirrors it into our state, so the dashboard is useful
     * before the user re-visits anything.
     */
    public void primeFromStockTracker(long accountHash)
    {
        String profile = configManager.getRSProfileKey();
        if (profile == null)
        {
            log.debug("Farmwatch prime skipped: no active RSProfile");
            return;
        }
        List<String> keys;
        try
        {
            keys = configManager.getRSProfileConfigurationKeys("timetracking", profile, "");
        }
        catch (Throwable t)
        {
            log.warn("Farmwatch prime: getRSProfileConfigurationKeys failed", t);
            return;
        }
        int applied = 0;
        for (String key : keys)
        {
            String value = configManager.getRSProfileConfiguration("timetracking", key);
            if (applyKey(accountHash, key, value)) applied++;
        }
        log.debug("Farmwatch prime: {}/{} timetracking entries mirrored for account {}",
            applied, keys.size(), accountHash);
    }

    /**
     * Varbit changes themselves don't carry useful data for us — the stock
     * tracker processes them, decodes them, and persists via ConfigChanged
     * downstream, which is where we pick them up.
     */
    public boolean handleVarbit(VarbitChanged e)
    {
        return false;
    }

    public boolean handleStockConfigChange(ConfigChanged e)
    {
        if (!"timetracking".equals(e.getGroup())) return false;
        long accountHash = client.getAccountHash();
        if (accountHash == -1) return false;
        return applyKey(accountHash, e.getKey(), e.getNewValue());
    }

    private boolean applyKey(long accountHash, String key, String value)
    {
        if (key == null) return false;

        Matcher m = VARBIT_KEY.matcher(key);
        if (m.matches())
        {
            return applyVarbit(accountHash, Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), value);
        }
        m = COMPOST_KEY.matcher(key);
        if (m.matches())
        {
            return applyCompost(accountHash, Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), value);
        }
        m = PROTECTED_KEY.matcher(key);
        if (m.matches())
        {
            return applyProtected(accountHash, Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), value);
        }
        if (TICK_OFFSET_KEY.equals(key))
        {
            return applyTickOffset(accountHash, value, false);
        }
        if (TICK_OFFSET_PRECISION_KEY.equals(key))
        {
            return applyTickOffset(accountHash, value, true);
        }
        // notify.*, autoweed and other keys fall through.
        return false;
    }

    private boolean applyTickOffset(long accountHash, String value, boolean precision)
    {
        AccountState acc = state.touch(accountHash);
        Integer parsed = null;
        if (value != null && !value.isEmpty())
        {
            try { parsed = Integer.parseInt(value); }
            catch (NumberFormatException ignored) { return false; }
        }
        if (precision) acc.setFarmTickOffsetPrecision(parsed);
        else acc.setFarmTickOffset(parsed);
        return true;
    }

    private boolean applyVarbit(long accountHash, int regionId, int varbitId, String value)
    {
        if (value == null || value.isEmpty()) return false;
        Matcher vm = VARBIT_VALUE.matcher(value);
        if (!vm.matches())
        {
            log.debug("Farmwatch: {}.{} unexpected value {}", regionId, varbitId, value);
            return false;
        }
        int varbitValue = Integer.parseInt(vm.group(1));
        long unixTime = Long.parseLong(vm.group(2));

        PatchState p = upsert(accountHash, regionId, varbitId);
        p.setVarbitValue(varbitValue);
        p.setLastUpdated(Instant.ofEpochSecond(unixTime));
        return true;
    }

    private boolean applyCompost(long accountHash, int regionId, int varbitId, String value)
    {
        PatchState p = upsert(accountHash, regionId, varbitId);
        // Value is a CompostState enum name ("COMPOST", "SUPERCOMPOST", "ULTRACOMPOST")
        // or null when the key was unset (which maps to "NONE" semantically).
        p.setCompost(value);
        return true;
    }

    private boolean applyProtected(long accountHash, int regionId, int varbitId, String value)
    {
        PatchState p = upsert(accountHash, regionId, varbitId);
        p.setProtectedPaid("true".equalsIgnoreCase(value));
        return true;
    }

    private PatchState upsert(long accountHash, int regionId, int varbitId)
    {
        AccountState acc = state.touch(accountHash);
        acc.setLastSeen(Instant.now());
        String id = regionId + "." + varbitId;
        return acc.patches().computeIfAbsent(id, k -> {
            PatchState ps = new PatchState();
            ps.setId(k);
            ps.setRegionId(regionId);
            ps.setVarbitId(varbitId);
            return ps;
        });
    }
}
