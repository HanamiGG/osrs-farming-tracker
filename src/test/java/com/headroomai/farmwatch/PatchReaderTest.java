package com.headroomai.farmwatch;

import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import java.time.Instant;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Golden tests for the config-mirror parsing. These are the lines of code
 * most likely to break if the stock Time Tracking plugin ever changes its
 * on-disk key format, so each supported key shape has its own test.
 */
public class PatchReaderTest
{
    private static final long ACCT = 0xDEADBEEFL;
    private static final String GROUP = "timetracking";

    private Client client;
    private ConfigManager cm;
    private FarmwatchState state;
    private PatchReader reader;

    @Before
    public void setUp()
    {
        client = Mockito.mock(Client.class);
        cm = Mockito.mock(ConfigManager.class);
        Mockito.when(client.getAccountHash()).thenReturn(ACCT);
        state = new FarmwatchState();
        reader = new PatchReader(client, cm, state);
    }

    private boolean apply(String key, String value)
    {
        return reader.handleStockConfigChange(cfg(key, value));
    }

    private static ConfigChanged cfg(String key, String value)
    {
        ConfigChanged e = new ConfigChanged();
        e.setGroup(GROUP);
        e.setKey(key);
        e.setOldValue(null);
        e.setNewValue(value);
        return e;
    }

    private PatchState patch(int regionId, int varbitId)
    {
        AccountState acc = state.touch(ACCT);
        return acc.patches().get(regionId + "." + varbitId);
    }

    @Test
    public void varbit_key_populates_value_and_lastUpdated()
    {
        long when = 1_700_000_000L;
        boolean dirty = apply("13106.4771", "3:" + when);
        assertTrue("varbit key should be accepted", dirty);
        PatchState p = patch(13106, 4771);
        assertNotNull("patch created", p);
        assertEquals(3, p.getVarbitValue());
        assertEquals(Instant.ofEpochSecond(when), p.getLastUpdated());
        assertEquals(13106, p.getRegionId());
        assertEquals(4771, p.getVarbitId());
    }

    @Test
    public void varbit_key_with_negative_value_parses()
    {
        // Diseased/dead varbits are sometimes encoded as negative deltas off
        // the base growing varbit; the regex has to accept the sign.
        assertTrue(apply("13106.4771", "-7:1700000000"));
        assertEquals(-7, patch(13106, 4771).getVarbitValue());
    }

    @Test
    public void varbit_key_with_malformed_value_is_rejected()
    {
        assertFalse(apply("13106.4771", "not-a-tuple"));
        assertNull("no patch materialized", patch(13106, 4771));
    }

    @Test
    public void varbit_key_with_null_or_empty_value_is_rejected()
    {
        assertFalse(apply("13106.4771", null));
        assertFalse(apply("13106.4771", ""));
    }

    @Test
    public void compost_key_sets_compost_tier()
    {
        apply("13106.4771", "3:1700000000");
        assertTrue(apply("13106.4771.compost", "ULTRACOMPOST"));
        assertEquals("ULTRACOMPOST", patch(13106, 4771).getCompost());
    }

    @Test
    public void protected_key_true_sets_flag()
    {
        apply("13106.4771", "3:1700000000");
        assertTrue(apply("13106.4771.protected", "true"));
        assertTrue(patch(13106, 4771).isProtectedPaid());
    }

    @Test
    public void protected_key_false_clears_flag()
    {
        apply("13106.4771", "3:1700000000");
        apply("13106.4771.protected", "true");
        apply("13106.4771.protected", "false");
        assertFalse(patch(13106, 4771).isProtectedPaid());
    }

    @Test
    public void protected_key_missing_value_clears_flag()
    {
        // The stock plugin UNSETS the key when the player stops paying; we
        // see that as a ConfigChanged with newValue=null.
        apply("13106.4771", "3:1700000000");
        apply("13106.4771.protected", "true");
        apply("13106.4771.protected", null);
        assertFalse(patch(13106, 4771).isProtectedPaid());
    }

    @Test
    public void tick_offset_keys_populate_account_fields()
    {
        assertTrue(apply("farmTickOffset", "17"));
        AccountState acc = state.touch(ACCT);
        assertEquals(Integer.valueOf(17), acc.getFarmTickOffset());

        assertTrue(apply("farmTickOffsetPrecision", "60"));
        assertEquals(Integer.valueOf(60), acc.getFarmTickOffsetPrecision());
    }

    @Test
    public void tick_offset_null_value_clears_field()
    {
        apply("farmTickOffset", "17");
        apply("farmTickOffset", null);
        AccountState acc = state.touch(ACCT);
        assertNull(acc.getFarmTickOffset());
    }

    @Test
    public void unrelated_key_is_ignored()
    {
        assertFalse(apply("notify.farming", "true"));
        assertFalse(apply("autoweed", "true"));
    }

    @Test
    public void non_timetracking_group_is_ignored()
    {
        ConfigChanged other = new ConfigChanged();
        other.setGroup("someOtherPlugin");
        other.setKey("13106.4771");
        other.setNewValue("3:1700000000");
        assertFalse(reader.handleStockConfigChange(other));
    }

    @Test
    public void varbit_key_updates_existing_patch_in_place()
    {
        apply("13106.4771", "0:1700000000");
        apply("13106.4771", "3:1700003600");
        PatchState p = patch(13106, 4771);
        assertEquals("value overwritten", 3, p.getVarbitValue());
        assertEquals("timestamp overwritten", Instant.ofEpochSecond(1700003600L), p.getLastUpdated());
    }

    @Test
    public void applying_compost_before_varbit_still_creates_the_patch()
    {
        // If the stock plugin emits the compost key first (or if we miss
        // the varbit event somehow), we should still remember the compost
        // so a later varbit event finds the record waiting.
        assertTrue(apply("13106.4771.compost", "SUPERCOMPOST"));
        PatchState p = patch(13106, 4771);
        assertNotNull(p);
        assertEquals("SUPERCOMPOST", p.getCompost());
    }
}
