package com.headroomai.farmwatch.potion;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BankTrackerTest
{
    private BankTracker tracker;

    @Before
    public void setUp()
    {
        tracker = new BankTracker();
    }

    private Item item(int id, int qty)
    {
        Item m = Mockito.mock(Item.class);
        Mockito.when(m.getId()).thenReturn(id);
        Mockito.when(m.getQuantity()).thenReturn(qty);
        return m;
    }

    private void fireBankUpdate(Item... items)
    {
        ItemContainer container = Mockito.mock(ItemContainer.class);
        Mockito.when(container.getItems()).thenReturn(items);
        ItemContainerChanged e = Mockito.mock(ItemContainerChanged.class);
        Mockito.when(e.getContainerId()).thenReturn(InventoryID.BANK);
        Mockito.when(e.getItemContainer()).thenReturn(container);
        tracker.onItemContainerChanged(e);
    }

    @Test
    public void freshTrackerHasNotSeenBank()
    {
        assertFalse(tracker.hasSeenBank());
        assertEquals("no counts yet", 0, tracker.getCount(257));
    }

    @Test
    public void firstBankUpdateFlipsSeenBank()
    {
        fireBankUpdate(item(257, 42));
        assertTrue(tracker.hasSeenBank());
        assertEquals(42, tracker.getCount(257));
    }

    @Test
    public void multipleItemsAreAllCounted()
    {
        fireBankUpdate(
            item(Ingredient.RANARR.itemId(), 300),
            item(Ingredient.TORSTOL.itemId(), 68),
            item(Ingredient.SNAPE_GRASS.itemId(), 500));
        assertEquals(300, tracker.getCount(Ingredient.RANARR.itemId()));
        assertEquals(68, tracker.getCount(Ingredient.TORSTOL.itemId()));
        assertEquals(500, tracker.getCount(Ingredient.SNAPE_GRASS.itemId()));
    }

    @Test
    public void subsequentBankUpdateReplacesSnapshot()
    {
        // The second update must completely overwrite the first; a
        // withdraw that empties a slot shouldn't leave a stale count.
        fireBankUpdate(item(257, 100));
        fireBankUpdate(item(269, 50));
        assertEquals("ranarr fully gone after second update", 0, tracker.getCount(257));
        assertEquals(50, tracker.getCount(269));
    }

    @Test
    public void emptySlotsAreSkipped()
    {
        // Bank arrays commonly include empty slots encoded as id=-1 or
        // id=0 with qty=0; they must not pollute the counts.
        fireBankUpdate(
            item(-1, 0),
            item(0, 0),
            item(257, 100),
            item(269, 0));
        assertEquals(100, tracker.getCount(257));
        assertEquals("qty 0 ignored", 0, tracker.getCount(269));
    }

    @Test
    public void nonBankContainerIsIgnored()
    {
        // Build the items array before opening the outer stubbing — a
        // nested Mockito.when() inside a .thenReturn() arg trips the
        // UnfinishedStubbingException checker.
        Item[] items = new Item[]{item(257, 100)};
        ItemContainerChanged e = Mockito.mock(ItemContainerChanged.class);
        Mockito.when(e.getContainerId()).thenReturn(93);  // some arbitrary non-bank id
        ItemContainer container = Mockito.mock(ItemContainer.class);
        Mockito.when(container.getItems()).thenReturn(items);
        Mockito.when(e.getItemContainer()).thenReturn(container);
        tracker.onItemContainerChanged(e);
        assertFalse(tracker.hasSeenBank());
        assertEquals(0, tracker.getCount(257));
    }

    @Test
    public void nullContainerDoesNotCrashOrFlipSeenBank()
    {
        ItemContainerChanged e = Mockito.mock(ItemContainerChanged.class);
        Mockito.when(e.getContainerId()).thenReturn(InventoryID.BANK);
        Mockito.when(e.getItemContainer()).thenReturn(null);
        tracker.onItemContainerChanged(e);
        assertFalse("null container shouldn't count as a bank sighting", tracker.hasSeenBank());
    }

    @Test
    public void snapshotIsADefensiveCopy()
    {
        fireBankUpdate(item(257, 42));
        assertEquals(Integer.valueOf(42), tracker.snapshot().get(257));
        try
        {
            tracker.snapshot().put(999, 1);
            throw new AssertionError("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
            // ok
        }
    }
}
