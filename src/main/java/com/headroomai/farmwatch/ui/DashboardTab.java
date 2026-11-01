package com.headroomai.farmwatch.ui;

import com.headroomai.farmwatch.FarmwatchState;
import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import com.headroomai.farmwatch.potion.FarmTicks;
import com.headroomai.farmwatch.potion.HerbVarbitDecoder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.MatteBorder;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Dashboard tab: a scrollable list of patch cards. Each card shows
 * produce, state, countdown, and a left-edge color band whose colour
 * encodes the state (growing / ready / diseased / dead / empty).
 *
 * <p>The card list rebuilds every 15 seconds via a Swing Timer. The
 * countdown labels are refreshed every second so the "ready in" line
 * ticks visibly without re-laying-out the whole list.
 */
public class DashboardTab extends JPanel
{
    private static final int REBUILD_PERIOD_MS = 15_000;
    private static final int TICK_PERIOD_MS = 1_000;
    private static final DateTimeFormatter READY_FMT =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private static final Color COLOR_GROWING    = new Color(0x45, 0x90, 0x66); // green
    private static final Color COLOR_READY      = new Color(0x3b, 0x82, 0xf6); // blue
    private static final Color COLOR_DISEASED   = new Color(0xff, 0x9f, 0x1c); // amber
    private static final Color COLOR_DEAD       = new Color(0xd0, 0x3b, 0x3b); // red
    private static final Color COLOR_EMPTY      = new Color(0x60, 0x60, 0x60); // grey

    private final FarmwatchState state;
    private final Client client;
    private final JPanel cards = new JPanel();
    private final JLabel emptyHint = new JLabel("<html>No herb patches tracked yet. Log in and visit a patch (or just let the stock Time Tracking plugin pick them up on login).</html>");
    private final List<Runnable> tickers = new ArrayList<>();
    private final Timer rebuildTimer;
    private final Timer tickTimer;

    public DashboardTab(FarmwatchState state, Client client)
    {
        this.state = state;
        this.client = client;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);
        emptyHint.setFont(FontManager.getRunescapeSmallFont());
        emptyHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        add(cards, BorderLayout.CENTER);

        rebuildTimer = new Timer(REBUILD_PERIOD_MS, e -> rebuild());
        rebuildTimer.setInitialDelay(0);
        rebuildTimer.start();

        tickTimer = new Timer(TICK_PERIOD_MS, e -> {
            for (Runnable r : tickers) r.run();
        });
        tickTimer.start();
    }

    private void rebuild()
    {
        cards.removeAll();
        tickers.clear();

        List<DecodedPatch> patches = collectPatches();
        if (patches.isEmpty())
        {
            cards.add(emptyHint);
        }
        else
        {
            patches.sort(DecodedPatch.readinessOrder());
            for (DecodedPatch p : patches)
            {
                cards.add(buildCard(p));
                cards.add(Box.createVerticalStrut(6));
            }
        }
        cards.revalidate();
        cards.repaint();
    }

    private List<DecodedPatch> collectPatches()
    {
        List<DecodedPatch> out = new ArrayList<>();
        if (state == null || client == null) return out;
        long hash;
        try { hash = client.getAccountHash(); }
        catch (Throwable t) { return out; }
        if (hash == -1) return out;
        AccountState acc = state.get(hash);
        if (acc == null) return out;
        Integer offset = acc.getFarmTickOffset();
        Integer precision = acc.getFarmTickOffsetPrecision();

        for (PatchState ps : acc.patches().values())
        {
            if (!HerbVarbitDecoder.isKnownHerbPatchVarbitId(ps.getVarbitId())) continue;
            HerbVarbitDecoder.Decoded d = HerbVarbitDecoder.decode(ps.getVarbitValue());
            if (d == null) continue;
            out.add(new DecodedPatch(ps, d, offset, precision));
        }
        return out;
    }

    private JPanel buildCard(DecodedPatch patch)
    {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        Color bandColor = patch.stateColor();
        card.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 4, 0, 0, bandColor),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 2, 0);

        JLabel title = new JLabel(patch.produceLabel());
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.BRAND_ORANGE);
        card.add(title, c);

        c.gridx = 1;
        c.weightx = 0;
        c.anchor = GridBagConstraints.EAST;
        JLabel state = new JLabel(patch.stateLabel());
        state.setFont(FontManager.getRunescapeSmallFont());
        state.setForeground(bandColor);
        state.setHorizontalAlignment(SwingConstants.RIGHT);
        card.add(state, c);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 2;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        JLabel sub = new JLabel(patch.subLine(Instant.now()));
        sub.setFont(FontManager.getRunescapeSmallFont());
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        card.add(sub, c);

        tickers.add(() -> sub.setText(patch.subLine(Instant.now())));
        return card;
    }

    /** Decoded patch snapshot cached for a single rebuild. */
    private static final class DecodedPatch
    {
        final PatchState raw;
        final HerbVarbitDecoder.Decoded decoded;
        final Instant readyAt;

        DecodedPatch(PatchState raw, HerbVarbitDecoder.Decoded decoded, Integer offset, Integer precision)
        {
            this.raw = raw;
            this.decoded = decoded;
            this.readyAt = computeReadyAt(raw.getLastUpdated(), decoded, offset, precision);
        }

        private static Instant computeReadyAt(Instant last, HerbVarbitDecoder.Decoded d, Integer offset, Integer precision)
        {
            if (last == null) return null;
            if (d.state != HerbVarbitDecoder.CropState.GROWING) return null;
            int stagesLeft = 4 - d.stage;
            if (stagesLeft <= 0) return null;
            return FarmTicks.readyAt(last, stagesLeft, FarmTicks.HERB_TICK_RATE_MINUTES, offset, precision);
        }

        String produceLabel()
        {
            if (decoded.produce == HerbVarbitDecoder.Produce.WEEDS) return "Empty / weeds";
            if (decoded.produce == HerbVarbitDecoder.Produce.ANYHERB) return "Dead herb";
            String n = decoded.produce.name().toLowerCase();
            return Character.toUpperCase(n.charAt(0)) + n.substring(1).replace('_', ' ');
        }

        String stateLabel()
        {
            switch (decoded.state)
            {
                case HARVESTABLE: return "ready";
                case GROWING:     return "growing";
                case DISEASED:    return "diseased";
                case DEAD:        return "dead";
                default:          return "";
            }
        }

        Color stateColor()
        {
            switch (decoded.state)
            {
                case HARVESTABLE: return COLOR_READY;
                case GROWING:     return COLOR_GROWING;
                case DISEASED:    return COLOR_DISEASED;
                case DEAD:        return COLOR_DEAD;
                default:          return COLOR_EMPTY;
            }
        }

        String subLine(Instant now)
        {
            if (readyAt != null && decoded.state == HerbVarbitDecoder.CropState.GROWING)
            {
                if (readyAt.isBefore(now))
                {
                    return "due now · stage " + decoded.stage + "/4";
                }
                Duration d = Duration.between(now, readyAt);
                return "ready " + READY_FMT.format(readyAt) + " · in " + humanDuration(d) + " · stage " + decoded.stage + "/4";
            }
            if (decoded.state == HerbVarbitDecoder.CropState.HARVESTABLE)
            {
                return "harvest now";
            }
            if (decoded.state == HerbVarbitDecoder.CropState.DISEASED)
            {
                return "needs cure";
            }
            if (decoded.state == HerbVarbitDecoder.CropState.DEAD)
            {
                return "clear & replant";
            }
            return "patch " + raw.getRegionId() + "." + raw.getVarbitId();
        }

        private static String humanDuration(Duration d)
        {
            long mins = Math.max(0, d.toMinutes());
            if (mins < 60) return mins + "m";
            long h = mins / 60;
            long m = mins % 60;
            return m == 0 ? (h + "h") : (h + "h " + m + "m");
        }

        /** Sort growing-with-nearest-ready first, then harvestable, then diseased/dead, then empty. */
        static Comparator<DecodedPatch> readinessOrder()
        {
            return Comparator.<DecodedPatch>comparingInt(p -> stateRank(p.decoded.state))
                .thenComparing(p -> p.readyAt == null ? Instant.MAX : p.readyAt);
        }

        private static int stateRank(HerbVarbitDecoder.CropState s)
        {
            switch (s)
            {
                case HARVESTABLE: return 0;
                case GROWING:     return 1;
                case DISEASED:    return 2;
                case DEAD:        return 3;
                default:          return 9;
            }
        }
    }
}
