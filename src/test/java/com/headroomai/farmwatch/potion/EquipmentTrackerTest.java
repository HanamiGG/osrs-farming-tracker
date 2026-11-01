package com.headroomai.farmwatch.potion;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EquipmentTrackerTest
{
    private EquipmentTracker tracker;

    @Before
    public void setUp()
    {
        tracker = new EquipmentTracker();
    }

    private static ItemContainerChanged event(int containerId, int... itemIds)
    {
        ItemContainer container = mock(ItemContainer.class);
        Item[] items = new Item[itemIds.length];
        for (int i = 0; i < itemIds.length; i++)
        {
            Item it = mock(Item.class);
            when(it.getId()).thenReturn(itemIds[i]);
            when(it.getQuantity()).thenReturn(1);
            items[i] = it;
        }
        when(container.getItems()).thenReturn(items);
        return new ItemContainerChanged(containerId, container);
    }

    @Test
    public void unknownStateReturnsFallback()
    {
        assertTrue(tracker.resolve(true));
        assertFalse(tracker.resolve(false));
        assertFalse(tracker.hasObserved());
    }

    @Test
    public void secateursInInventoryResolveTrue()
    {
        tracker.onItemContainerChanged(event(InventoryID.INV, ItemID.MAGIC_SECATEURS));
        assertTrue(tracker.hasObserved());
        assertTrue(tracker.resolve(false));
    }

    @Test
    public void nzVariantAlsoCountsAsSecateurs()
    {
        tracker.onItemContainerChanged(event(InventoryID.INV, ItemID.MAGIC_SECATEURS_NZ));
        assertTrue(tracker.resolve(false));
    }

    @Test
    public void secateursInEquipmentResolveTrue()
    {
        tracker.onItemContainerChanged(event(InventoryID.WORN, ItemID.MAGIC_SECATEURS));
        assertTrue(tracker.resolve(false));
    }

    @Test
    public void emptyInventoryOnlyMarksObserved()
    {
        tracker.onItemContainerChanged(event(InventoryID.INV));
        assertTrue(tracker.hasObserved());
        // Only inventory observed, equipment still unknown. Current logic treats
        // "any container observed" as enough to leave the UNKNOWN state. This
        // matches "I just emptied my pack and have no secateurs" without needing
        // the player to also open their equipment screen.
        assertFalse(tracker.resolve(true));
    }

    @Test
    public void removingSecateursFromInventoryClearsIt()
    {
        tracker.onItemContainerChanged(event(InventoryID.INV, ItemID.MAGIC_SECATEURS));
        assertTrue(tracker.resolve(false));
        tracker.onItemContainerChanged(event(InventoryID.INV, ItemID.RANARR_SEED));
        assertFalse(tracker.resolve(true));
    }

    @Test
    public void secateursInEquipmentOverridesEmptyInventory()
    {
        tracker.onItemContainerChanged(event(InventoryID.INV, ItemID.RANARR_SEED));
        tracker.onItemContainerChanged(event(InventoryID.WORN, ItemID.MAGIC_SECATEURS));
        assertTrue(tracker.resolve(false));
    }

    @Test
    public void unrelatedContainersAreIgnored()
    {
        tracker.onItemContainerChanged(event(InventoryID.BANK, ItemID.MAGIC_SECATEURS));
        assertFalse(tracker.hasObserved());
        assertTrue(tracker.resolve(true));
    }

    @Test
    public void nullContainerIsSafe()
    {
        ItemContainerChanged e = new ItemContainerChanged(InventoryID.INV, null);
        tracker.onItemContainerChanged(e);
        // Null container still marks seen (we did see a change event), and
        // reports no secateurs — safer than ignoring the change entirely.
        assertTrue(tracker.hasObserved());
        assertFalse(tracker.resolve(true));
    }
}
