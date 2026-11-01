package com.headroomai.farmwatch.ui;

import com.headroomai.farmwatch.FarmwatchState;
import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import com.headroomai.farmwatch.potion.HerbVarbitDecoder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Run planner tab: ordered list of patches that are harvestable right
 * now, with a one-line hint of the fastest known teleport method.
 * Hidden entirely when nothing is ready.
 */
public class RunPlannerTab extends JPanel
{
    private static final int REBUILD_PERIOD_MS = 10_000;

    private final FarmwatchState state;
    private final Client client;
    private final JPanel list = new JPanel();
    private final JLabel emptyHint = new JLabel("<html>No ready patches yet. Pop back when something's grown — this view auto-refreshes.</html>");

    public RunPlannerTab(FarmwatchState state, Client client)
    {
        this.state = state;
        this.client = client;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(ColorScheme.DARK_GRAY_COLOR);
        emptyHint.setFont(FontManager.getRunescapeSmallFont());
        emptyHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JLabel sub = new JLabel("<html>Harvest run order — nearest teleport, assuming you own the usual farming unlocks.</html>");
        sub.setFont(FontManager.getRunescapeSmallFont());
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.add(sub, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(list, BorderLayout.CENTER);

        Timer t = new Timer(REBUILD_PERIOD_MS, e -> rebuild());
        t.setInitialDelay(0);
        t.start();
    }

    private void rebuild()
    {
        list.removeAll();
        List<Entry> ready = collectReady();
        if (ready.isEmpty())
        {
            list.add(emptyHint);
        }
        else
        {
            int idx = 1;
            for (Entry e : ready)
            {
                list.add(buildRow(idx++, e));
                list.add(Box.createVerticalStrut(4));
            }
        }
        list.revalidate();
        list.repaint();
    }

    private List<Entry> collectReady()
    {
        List<Entry> out = new ArrayList<>();
        if (state == null || client == null) return out;
        long hash;
        try { hash = client.getAccountHash(); }
        catch (Throwable t) { return out; }
        if (hash == -1) return out;
        AccountState acc = state.get(hash);
        if (acc == null) return out;
        for (PatchState ps : acc.patches().values())
        {
            if (!HerbVarbitDecoder.isKnownHerbPatchVarbitId(ps.getVarbitId())) continue;
            HerbVarbitDecoder.Decoded d = HerbVarbitDecoder.decode(ps.getVarbitValue());
            if (d == null) continue;
            if (d.state == HerbVarbitDecoder.CropState.HARVESTABLE
                || d.state == HerbVarbitDecoder.CropState.DEAD
                || d.state == HerbVarbitDecoder.CropState.DISEASED)
            {
                out.add(new Entry(ps, d));
            }
        }
        out.sort((a, b) -> Integer.compare(regionOrder(a.patch.getVarbitId()), regionOrder(b.patch.getVarbitId())));
        return out;
    }

    private static int regionOrder(int varbitId)
    {
        // Teleport order based on common farming runs: nearest-first.
        // Numbers are arbitrary ranks, not game IDs; lower = earlier in the run.
        switch (varbitId)
        {
            case 7906:  return 1;  // Hosidius (xeric's talisman)
            case 4772:  return 2;  // Falador
            case 7904:  return 3;  // Harmony (ectophial-adjacent)
            case 4774:  return 4;  // Canifis
            case 4771:  return 5;  // Catherby (camelot tele)
            case 4773:  return 6;  // Ardougne (ardy cape / tele)
            case 7907:  return 7;  // Farming Guild (skills necklace)
            case 10981: return 8;  // Varlamore
            case 4775:  return 9;  // Trollheim (fire cape)
            case 7905:  return 10; // Weiss (icy basalt)
            default:    return 99;
        }
    }

    private JPanel buildRow(int order, Entry e)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel idx = new JLabel(String.valueOf(order));
        idx.setFont(FontManager.getRunescapeBoldFont());
        idx.setForeground(ColorScheme.BRAND_ORANGE);
        idx.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JLabel region = new JLabel(regionName(e.patch.getVarbitId()));
        region.setFont(FontManager.getRunescapeBoldFont());
        region.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        body.add(region);

        JLabel detail = new JLabel("<html><span style='color:#888'>" + stateHint(e) + " · " + teleportHint(e.patch.getVarbitId()) + "</span></html>");
        detail.setFont(FontManager.getRunescapeSmallFont());
        body.add(detail);

        row.add(idx, BorderLayout.WEST);
        row.add(body, BorderLayout.CENTER);
        return row;
    }

    private static String regionName(int varbitId)
    {
        switch (varbitId)
        {
            case 4771:  return "Catherby";
            case 4772:  return "Falador";
            case 4773:  return "Ardougne";
            case 4774:  return "Canifis";
            case 4775:  return "Trollheim";
            case 7904:  return "Harmony";
            case 7905:  return "Weiss";
            case 7906:  return "Hosidius";
            case 7907:  return "Farming Guild";
            case 10981: return "Varlamore";
            default:    return "Patch " + varbitId;
        }
    }

    private static String teleportHint(int varbitId)
    {
        switch (varbitId)
        {
            case 4771:  return "Camelot tele / cat. tele tab";
            case 4772:  return "Explorer's ring / Falador tele";
            case 4773:  return "Ardy cloak / Ardy tele";
            case 4774:  return "Canifis slayer tele / Fenkenstrain's";
            case 4775:  return "Fire cape right-click / Stony basalt";
            case 7904:  return "Ectophial / Harmony tele tab";
            case 7905:  return "Icy basalt";
            case 7906:  return "Xeric's talisman → inferno";
            case 7907:  return "Skills necklace → Farming Guild";
            case 10981: return "Civitas illa fortis tele";
            default:    return "nearest teleport";
        }
    }

    private static String stateHint(Entry e)
    {
        switch (e.decoded.state)
        {
            case HARVESTABLE: return "ready to harvest";
            case DISEASED:    return "diseased, needs cure";
            case DEAD:        return "dead, clear & replant";
            default:          return "patch";
        }
    }

    private static final class Entry
    {
        final PatchState patch;
        final HerbVarbitDecoder.Decoded decoded;
        Entry(PatchState p, HerbVarbitDecoder.Decoded d) { this.patch = p; this.decoded = d; }
    }
}
