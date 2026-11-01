package com.headroomai.farmwatch.ui;

import com.headroomai.farmwatch.FarmwatchState;
import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.model.PatchState;
import com.headroomai.farmwatch.potion.FarmTicks;
import com.headroomai.farmwatch.potion.HerbVarbitDecoder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Timeline tab: a custom-painted 6-hour scrubber showing when each
 * patch becomes ready. "Now" sits one tick in from the left so recent
 * past readyAts stay visible for a few minutes after they fire.
 *
 * <p>Narrow sidebar constraint: ~215px drawing width. We trade off vs.
 * the webpage version by using a 6-hour window instead of 24h — short
 * enough that a herb's 80-minute run fits comfortably across it, and
 * patches that are hours out get a "+N h" badge on the right instead
 * of being plotted off-screen.
 */
public class TimelineTab extends JPanel
{
    private static final int REPAINT_PERIOD_MS = 15_000;
    private static final Duration WINDOW = Duration.ofHours(6);
    private static final Duration PAST_WINDOW = Duration.ofMinutes(20);
    private static final DateTimeFormatter TICK_FMT =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 4;
    private static final int LEFT_LABEL_W = 70;
    private static final int RIGHT_PAD = 24;
    private static final int TOP_PAD = 16;

    private final FarmwatchState state;
    private final Client client;
    private final TimelineCanvas canvas;

    public TimelineTab(FarmwatchState state, Client client)
    {
        this.state = state;
        this.client = client;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel header = new JLabel("<html>Next 6 hours. Each row is a patch; the dot is when it becomes harvestable.</html>");
        header.setFont(FontManager.getRunescapeSmallFont());
        header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        canvas = new TimelineCanvas();
        add(header, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        Timer t = new Timer(REPAINT_PERIOD_MS, e -> canvas.repaint());
        t.setInitialDelay(0);
        t.start();
    }

    private List<Row> collectRows()
    {
        List<Row> out = new ArrayList<>();
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
            if (d.produce == HerbVarbitDecoder.Produce.WEEDS) continue;
            Instant readyAt = null;
            if (d.state == HerbVarbitDecoder.CropState.GROWING && ps.getLastUpdated() != null)
            {
                int stagesLeft = 4 - d.stage;
                if (stagesLeft > 0)
                {
                    readyAt = FarmTicks.readyAt(ps.getLastUpdated(), stagesLeft,
                        FarmTicks.HERB_TICK_RATE_MINUTES, offset, precision);
                }
            }
            else if (d.state == HerbVarbitDecoder.CropState.HARVESTABLE)
            {
                readyAt = Instant.now().minusSeconds(1); // "already ready"
            }
            out.add(new Row(ps, d, readyAt));
        }
        out.sort((a, b) -> {
            Instant ai = a.readyAt == null ? Instant.MAX : a.readyAt;
            Instant bi = b.readyAt == null ? Instant.MAX : b.readyAt;
            return ai.compareTo(bi);
        });
        return out;
    }

    private final class TimelineCanvas extends JPanel
    {
        TimelineCanvas()
        {
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setPreferredSize(new Dimension(0, 160));
        }

        @Override
        public Dimension getPreferredSize()
        {
            int rows = Math.max(1, collectRows().size());
            int h = TOP_PAD + rows * (ROW_HEIGHT + ROW_GAP) + 24;
            return new Dimension(0, h);
        }

        @Override
        protected void paintComponent(Graphics g0)
        {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics fm = g.getFontMetrics();

            int w = getWidth();
            int h = getHeight();
            int plotLeft = LEFT_LABEL_W;
            int plotRight = w - RIGHT_PAD;
            int plotWidth = Math.max(10, plotRight - plotLeft);

            Instant now = Instant.now();
            Instant windowStart = now.minus(PAST_WINDOW);
            Instant windowEnd = now.plus(WINDOW);
            long windowDurSec = Duration.between(windowStart, windowEnd).getSeconds();

            // Hourly gridlines + labels
            g.setColor(new Color(255, 255, 255, 24));
            g.setStroke(new BasicStroke(1f));
            Instant tick = windowStart.plusSeconds(3600 - (windowStart.getEpochSecond() % 3600));
            while (!tick.isAfter(windowEnd))
            {
                int x = plotLeft + (int) ((Duration.between(windowStart, tick).getSeconds() * (long) plotWidth) / windowDurSec);
                g.drawLine(x, TOP_PAD - 4, x, h - 8);
                String label = TICK_FMT.format(tick);
                g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
                g.drawString(label, x - fm.stringWidth(label) / 2, TOP_PAD - 6);
                g.setColor(new Color(255, 255, 255, 24));
                tick = tick.plusSeconds(3600);
            }

            // "Now" line
            int nowX = plotLeft + (int) ((Duration.between(windowStart, now).getSeconds() * (long) plotWidth) / windowDurSec);
            g.setColor(ColorScheme.BRAND_ORANGE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(nowX, TOP_PAD - 2, nowX, h - 8);

            // Rows
            List<Row> rows = collectRows();
            int y = TOP_PAD + 8;
            for (Row row : rows)
            {
                drawRow(g, fm, row, plotLeft, plotRight, plotWidth, windowStart, windowEnd, windowDurSec, now, y);
                y += ROW_HEIGHT + ROW_GAP;
            }

            if (rows.isEmpty())
            {
                g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
                String msg = "No herb patches tracked yet.";
                g.drawString(msg, plotLeft, TOP_PAD + 20);
            }

            g.dispose();
        }

        private void drawRow(Graphics2D g, FontMetrics fm, Row row,
                             int plotLeft, int plotRight, int plotWidth,
                             Instant windowStart, Instant windowEnd, long windowDurSec,
                             Instant now, int y)
        {
            // Label on the left
            g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            String label = shortProduceName(row.decoded);
            g.drawString(label, 0, y + fm.getAscent());

            // Baseline track
            g.setColor(new Color(255, 255, 255, 36));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(plotLeft, y + 8, plotRight, y + 8);

            if (row.readyAt == null)
            {
                g.setColor(new Color(0xd0, 0x3b, 0x3b));
                g.drawString(diseasedOrDead(row.decoded), plotRight - fm.stringWidth(diseasedOrDead(row.decoded)), y + fm.getAscent());
                return;
            }

            // Dot: where readyAt lands in the window
            Color dotColor = row.decoded.state == HerbVarbitDecoder.CropState.HARVESTABLE
                ? new Color(0x3b, 0x82, 0xf6)      // blue
                : new Color(0x45, 0x90, 0x66);     // green
            if (row.readyAt.isAfter(windowEnd))
            {
                long overHours = Duration.between(now, row.readyAt).toHours();
                g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
                String overLabel = "+" + overHours + "h";
                g.drawString(overLabel, plotRight + 2, y + fm.getAscent());
            }
            else
            {
                long sec = Math.max(0, Duration.between(windowStart, row.readyAt).getSeconds());
                int dotX = plotLeft + (int) ((sec * (long) plotWidth) / windowDurSec);
                g.setColor(dotColor);
                g.fillOval(dotX - 4, y + 4, 8, 8);
            }
        }

        private String shortProduceName(HerbVarbitDecoder.Decoded d)
        {
            if (d.produce == HerbVarbitDecoder.Produce.ANYHERB) return "Herb";
            String n = d.produce.name().toLowerCase();
            return Character.toUpperCase(n.charAt(0)) + n.substring(1).replace('_', ' ');
        }

        private String diseasedOrDead(HerbVarbitDecoder.Decoded d)
        {
            if (d.state == HerbVarbitDecoder.CropState.DISEASED) return "diseased";
            if (d.state == HerbVarbitDecoder.CropState.DEAD) return "dead";
            return "";
        }
    }

    private static final class Row
    {
        final PatchState patch;
        final HerbVarbitDecoder.Decoded decoded;
        final Instant readyAt;
        Row(PatchState p, HerbVarbitDecoder.Decoded d, Instant r) { this.patch = p; this.decoded = d; this.readyAt = r; }
    }
}
