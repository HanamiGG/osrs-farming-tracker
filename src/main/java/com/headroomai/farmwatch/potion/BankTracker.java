package com.headroomai.farmwatch.potion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Keeps a snapshot of the player's bank contents so the potion planner
 * can show "have / need" counts. Updated whenever the bank container
 * fires {@link ItemContainerChanged} (opening the bank, deposits,
 * withdrawals). Nothing is persisted — a fresh RuneLite session starts
 * with {@link #hasSeenBank()} false until the user opens their bank.
 *
 * <p>Thread model: {@code ItemContainerChanged} fires on the client
 * thread; panel reads happen on the Swing EDT. The counts map is only
 * ever touched inside a synchronized block.
 */
public class BankTracker
{
    private final Map<Integer, Integer> counts = new HashMap<>();
    private volatile boolean seenBank = false;

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e)
    {
        if (e.getContainerId() != InventoryID.BANK)
        {
            return;
        }
        ItemContainer container = e.getItemContainer();
        if (container == null)
        {
            return;
        }
        Item[] items = container.getItems();
        synchronized (counts)
        {
            counts.clear();
            for (Item item : items)
            {
                int id = item.getId();
                int qty = item.getQuantity();
                if (id <= 0 || qty <= 0)
                {
                    continue;
                }
                counts.merge(id, qty, Integer::sum);
            }
        }
        seenBank = true;
    }

    /** Returns 0 if the item isn't in the snapshot or the bank hasn't been seen yet. */
    public int getCount(int itemId)
    {
        synchronized (counts)
        {
            return counts.getOrDefault(itemId, 0);
        }
    }

    /** False until the player opens their bank for the first time this session. */
    public boolean hasSeenBank()
    {
        return seenBank;
    }

    /** Defensive copy for tests. */
    Map<Integer, Integer> snapshot()
    {
        synchronized (counts)
        {
            return Collections.unmodifiableMap(new HashMap<>(counts));
        }
    }
}
