package com.headroomai.farmwatch.potion;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Watches the player's inventory and worn equipment and remembers
 * whether magic secateurs have been seen on their person. The planner
 * uses this to decide whether to apply the +10% herb yield bonus to the
 * expected-harvest math.
 *
 * <p>Three states: UNKNOWN (we haven't seen either container yet),
 * HAS (observed the secateurs — flat-ID or NZ variant — in INV or WORN
 * at the last change), LACKS (observed both containers, neither
 * contained them). The resolve() method lets callers plug in a fallback
 * for the UNKNOWN case; that's typically the user's config toggle.
 *
 * <p>Thread model: {@code ItemContainerChanged} fires on the client
 * thread; the panel reads from the Swing EDT. State is held in two
 * volatile booleans so the read is cheap and lock-free.
 */
public class EquipmentTracker
{
    private volatile boolean secateursInInventory = false;
    private volatile boolean secateursInEquipment = false;
    private volatile boolean seenInventory = false;
    private volatile boolean seenEquipment = false;

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e)
    {
        int id = e.getContainerId();
        if (id == InventoryID.INV)
        {
            secateursInInventory = containsSecateurs(e.getItemContainer());
            seenInventory = true;
        }
        else if (id == InventoryID.WORN)
        {
            secateursInEquipment = containsSecateurs(e.getItemContainer());
            seenEquipment = true;
        }
    }

    private static boolean containsSecateurs(ItemContainer container)
    {
        if (container == null) return false;
        for (Item item : container.getItems())
        {
            int id = item.getId();
            if (id == ItemID.MAGIC_SECATEURS || id == ItemID.MAGIC_SECATEURS_NZ)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve to a yes/no answer for the yield math.
     *
     * @param defaultIfUnknown value to use if we have not yet observed the player's inventory or equipment
     */
    public boolean resolve(boolean defaultIfUnknown)
    {
        if (!seenInventory && !seenEquipment)
        {
            return defaultIfUnknown;
        }
        return secateursInInventory || secateursInEquipment;
    }

    /** For tests + diagnostics. */
    public boolean hasObserved() { return seenInventory || seenEquipment; }
    public boolean secateursSeenInInventory() { return secateursInInventory; }
    public boolean secateursSeenInEquipment() { return secateursInEquipment; }
}
