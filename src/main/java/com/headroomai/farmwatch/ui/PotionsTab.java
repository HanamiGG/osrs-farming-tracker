package com.headroomai.farmwatch.ui;

import com.headroomai.farmwatch.FarmwatchConfig;
import com.headroomai.farmwatch.FarmwatchState;
import com.headroomai.farmwatch.potion.BankTracker;
import com.headroomai.farmwatch.potion.EquipmentTracker;
import com.headroomai.farmwatch.potion.GrowingHerbs;
import com.headroomai.farmwatch.potion.HerbVarbitDecoder;
import com.headroomai.farmwatch.potion.HerbYield;
import com.headroomai.farmwatch.potion.Ingredient;
import com.headroomai.farmwatch.potion.PotionCatalog;
import com.headroomai.farmwatch.potion.PotionDef;
import com.headroomai.farmwatch.potion.PotionPlanner;
import com.headroomai.farmwatch.potion.ShoppingList;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The "Potions" tab of {@link FarmwatchPanel}: user sets how many of
 * each potion they want to brew, we show the raw herb + secondary
 * shopping list cross-referenced against their bank snapshot and
 * currently-growing herb patches.
 */
public class PotionsTab extends JPanel
{
    private static final String FILTER_ALL = "All";
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final BankTracker bankTracker;
    private final EquipmentTracker equipmentTracker;
    private final FarmwatchState farmState;
    private final Client client;
    private final FarmwatchConfig config;
    private final Map<PotionDef, JSpinner> inputs = new HashMap<>();
    private final Map<PotionDef, JPanel> inputRows = new HashMap<>();
    private final JPanel outputPanel = new JPanel();
    private final JLabel emptyHint = new JLabel("Set quantities above and press Compute.");
    private final JComboBox<String> categoryFilter = new JComboBox<>();

    public PotionsTab(BankTracker bankTracker,
                      EquipmentTracker equipmentTracker,
                      FarmwatchState farmState,
                      Client client,
                      FarmwatchConfig config)
    {
        super();
        this.bankTracker = bankTracker;
        this.equipmentTracker = equipmentTracker;
        this.farmState = farmState;
        this.client = client;
        this.config = config;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel sub = new JLabel("<html>Enter how many of each potion you want to brew. Open your bank and visit your herb patches so we can show what you have, what's growing, and what's short.</html>");
        sub.setFont(FontManager.getRunescapeSmallFont());
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.add(sub, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildBody()
    {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(ColorScheme.DARK_GRAY_COLOR);
        body.add(buildFilterRow());
        body.add(Box.createVerticalStrut(6));
        body.add(buildInputList());
        body.add(Box.createVerticalStrut(8));
        body.add(buildButtonRow());
        body.add(Box.createVerticalStrut(10));
        body.add(buildOutputSection());
        return body;
    }

    private JPanel buildFilterRow()
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Show:");
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        categoryFilter.addItem(FILTER_ALL);
        for (PotionDef.Category c : PotionDef.Category.values())
        {
            categoryFilter.addItem(prettyName(c));
        }
        categoryFilter.setSelectedItem(FILTER_ALL);
        categoryFilter.addActionListener(e -> applyFilter());

        row.add(label, BorderLayout.WEST);
        row.add(categoryFilter, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildInputList()
    {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (PotionDef potion : PotionCatalog.all())
        {
            JPanel rowPanel = buildInputRow(potion);
            inputRows.put(potion, rowPanel);
            list.add(rowPanel);
        }
        return list;
    }

    private JPanel buildInputRow(PotionDef potion)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel name = new JLabel(potion.name());
        name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        name.setFont(FontManager.getRunescapeSmallFont());

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
        spinner.setPreferredSize(new Dimension(70, 22));
        inputs.put(potion, spinner);

        row.add(name, BorderLayout.CENTER);
        row.add(spinner, BorderLayout.EAST);
        return row;
    }

    private void applyFilter()
    {
        Object selected = categoryFilter.getSelectedItem();
        if (selected == null)
        {
            return;
        }
        for (Map.Entry<PotionDef, JPanel> e : inputRows.entrySet())
        {
            boolean visible = FILTER_ALL.equals(selected)
                || prettyName(e.getKey().category()).equals(selected);
            e.getValue().setVisible(visible);
        }
        revalidate();
        repaint();
    }

    private JPanel buildButtonRow()
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton compute = new JButton("Compute");
        compute.setFocusPainted(false);
        compute.addActionListener(e -> recompute());

        JButton reset = new JButton("Reset");
        reset.setFocusPainted(false);
        reset.addActionListener(e -> resetInputs());

        row.add(compute, BorderLayout.CENTER);
        row.add(reset, BorderLayout.EAST);
        return row;
    }

    private JPanel buildOutputSection()
    {
        outputPanel.setLayout(new BoxLayout(outputPanel, BoxLayout.Y_AXIS));
        outputPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        outputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        emptyHint.setFont(FontManager.getRunescapeSmallFont());
        emptyHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        outputPanel.add(emptyHint);
        return outputPanel;
    }

    private void resetInputs()
    {
        for (JSpinner s : inputs.values())
        {
            s.setValue(0);
        }
        outputPanel.removeAll();
        outputPanel.add(emptyHint);
        outputPanel.revalidate();
        outputPanel.repaint();
    }

    private void recompute()
    {
        Map<PotionDef, Integer> goals = new HashMap<>();
        for (Map.Entry<PotionDef, JSpinner> e : inputs.entrySet())
        {
            Object v = e.getValue().getValue();
            if (v instanceof Number)
            {
                int n = ((Number) v).intValue();
                if (n > 0)
                {
                    goals.put(e.getKey(), n);
                }
            }
        }
        renderOutput(PotionPlanner.compute(goals));
    }

    private void renderOutput(ShoppingList out)
    {
        outputPanel.removeAll();
        if (out.isEmpty())
        {
            outputPanel.add(emptyHint);
        }
        else
        {
            if (bankTracker != null && !bankTracker.hasSeenBank())
            {
                outputPanel.add(bankBanner());
                outputPanel.add(Box.createVerticalStrut(6));
            }
            Map<HerbVarbitDecoder.Produce, GrowingHerbs.Aggregate> growing = currentGrowingHerbs();
            if (!out.herbs().isEmpty())
            {
                outputPanel.add(sectionHeader("Herbs"));
                for (Map.Entry<Ingredient, Integer> row : out.herbs().entrySet())
                {
                    outputPanel.add(ingredientLine(row.getKey(), row.getValue(), growing));
                }
            }
            if (!out.secondaries().isEmpty())
            {
                outputPanel.add(Box.createVerticalStrut(6));
                outputPanel.add(sectionHeader("Secondaries"));
                for (Map.Entry<Ingredient, Integer> row : out.secondaries().entrySet())
                {
                    outputPanel.add(ingredientLine(row.getKey(), row.getValue(), growing));
                }
            }
        }
        outputPanel.revalidate();
        outputPanel.repaint();
    }

    private Map<HerbVarbitDecoder.Produce, GrowingHerbs.Aggregate> currentGrowingHerbs()
    {
        if (client == null || farmState == null)
        {
            return java.util.Collections.emptyMap();
        }
        long hash;
        try
        {
            hash = client.getAccountHash();
        }
        catch (Throwable t)
        {
            return java.util.Collections.emptyMap();
        }
        return GrowingHerbs.aggregate(farmState, hash);
    }

    private int farmingLevel()
    {
        if (client == null) return 1;
        try
        {
            int lv = client.getRealSkillLevel(Skill.FARMING);
            return lv > 0 ? lv : 1;
        }
        catch (Throwable t)
        {
            return 1;
        }
    }

    private JLabel bankBanner()
    {
        JLabel banner = new JLabel("<html>Open your bank to see what you already have.</html>");
        banner.setFont(FontManager.getRunescapeSmallFont());
        banner.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
        banner.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        return banner;
    }

    private JLabel sectionHeader(String text)
    {
        JLabel header = new JLabel(text);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setForeground(ColorScheme.BRAND_ORANGE);
        header.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        return header;
    }

    private JPanel ingredientLine(Ingredient ing, int qty, Map<HerbVarbitDecoder.Produce, GrowingHerbs.Aggregate> growing)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean bankReady = bankTracker != null && bankTracker.hasSeenBank();
        int have = bankReady ? bankTracker.getCount(ing.itemId()) : 0;
        int deficit = Math.max(0, qty - have);

        JLabel main = new JLabel(qty + " × " + ing.displayName());
        main.setFont(FontManager.getRunescapeSmallFont());
        if (bankReady)
        {
            main.setForeground(deficit > 0
                ? ColorScheme.PROGRESS_ERROR_COLOR
                : ColorScheme.PROGRESS_COMPLETE_COLOR);
        }
        else
        {
            main.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }
        main.setHorizontalAlignment(SwingConstants.LEFT);
        row.add(main);

        if (bankReady)
        {
            String sub = deficit > 0
                ? ("have " + have + " · short " + deficit)
                : ("have " + have + " ✓");
            row.add(subLineLabel(sub));
        }

        String growingLine = growingSubline(ing, growing);
        if (growingLine != null)
        {
            row.add(subLineLabel(growingLine));
        }

        if (ing.source() != null)
        {
            row.add(subLineLabel(ing.source()));
        }
        return row;
    }

    private String growingSubline(Ingredient ing, Map<HerbVarbitDecoder.Produce, GrowingHerbs.Aggregate> growing)
    {
        if (!ing.hasYieldModel()) return null;
        HerbVarbitDecoder.Produce p = HerbVarbitDecoder.produceFor(ing);
        if (p == null) return null;
        GrowingHerbs.Aggregate agg = growing.get(p);
        if (agg == null || agg.yieldingPatches() == 0) return null;

        boolean configDefault = config == null || config.assumeMagicSecateurs();
        boolean useSecateurs = equipmentTracker != null
            ? equipmentTracker.resolve(configDefault)
            : configDefault;
        int expected = HerbYield.expectedFromPatches(ing, agg.yieldingPatches(), farmingLevel(), useSecateurs);
        if (expected <= 0) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("+").append(expected).append(" from ").append(agg.yieldingPatches()).append(" patch");
        if (agg.yieldingPatches() != 1) sb.append("es");
        if (agg.harvestablePatches > 0 && agg.growingPatches == 0)
        {
            sb.append(" (ready now)");
        }
        else if (agg.earliestReadyAt != null)
        {
            if (agg.earliestReadyAt.isBefore(Instant.now()))
            {
                sb.append(" (ready now)");
            }
            else
            {
                sb.append(" (ready ~").append(TIME_FMT.format(agg.earliestReadyAt)).append(")");
            }
        }
        if (agg.diseasedPatches > 0)
        {
            sb.append(" · ").append(agg.diseasedPatches).append(" diseased");
        }
        return sb.toString();
    }

    private JLabel subLineLabel(String text)
    {
        JLabel label = new JLabel("<html><span style='color:#888'>&nbsp;&nbsp;" + text + "</span></html>");
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    private static String prettyName(PotionDef.Category c)
    {
        String s = c.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
