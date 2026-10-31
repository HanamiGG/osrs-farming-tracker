package com.headroomai.farmwatch.ui;

import com.headroomai.farmwatch.potion.Ingredient;
import com.headroomai.farmwatch.potion.PotionCatalog;
import com.headroomai.farmwatch.potion.PotionDef;
import com.headroomai.farmwatch.potion.PotionPlanner;
import com.headroomai.farmwatch.potion.ShoppingList;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel: user enters how many of each potion they want to brew,
 * clicks Compute, sees the raw herb + secondary shopping list. No bank
 * reading — users mentally subtract what they already have. See
 * {@link PotionPlanner} for the math; the panel is a thin shell.
 */
public class PotionPlannerPanel extends PluginPanel
{
    private static final String FILTER_ALL = "All";

    private final Map<PotionDef, JSpinner> inputs = new HashMap<>();
    private final Map<PotionDef, JPanel> inputRows = new HashMap<>();
    private final JPanel outputPanel = new JPanel();
    private final JLabel emptyHint = new JLabel("Set quantities above and press Compute.");
    private final JComboBox<String> categoryFilter = new JComboBox<>();

    public PotionPlannerPanel()
    {
        super();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel title = new JLabel("Potion Planner");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.BRAND_ORANGE);
        header.add(title, BorderLayout.NORTH);

        JLabel sub = new JLabel("<html>Enter how many of each potion you want to brew. The list below is raw ingredient totals &mdash; subtract what you already have.</html>");
        sub.setFont(FontManager.getRunescapeSmallFont());
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sub.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
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
            if (!out.herbs().isEmpty())
            {
                outputPanel.add(sectionHeader("Herbs"));
                for (Map.Entry<Ingredient, Integer> row : out.herbs().entrySet())
                {
                    outputPanel.add(ingredientLine(row.getKey(), row.getValue()));
                }
            }
            if (!out.secondaries().isEmpty())
            {
                outputPanel.add(Box.createVerticalStrut(6));
                outputPanel.add(sectionHeader("Secondaries"));
                for (Map.Entry<Ingredient, Integer> row : out.secondaries().entrySet())
                {
                    outputPanel.add(ingredientLine(row.getKey(), row.getValue()));
                }
            }
        }
        outputPanel.revalidate();
        outputPanel.repaint();
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

    private JPanel ingredientLine(Ingredient ing, int qty)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel main = new JLabel(qty + " × " + ing.displayName());
        main.setFont(FontManager.getRunescapeSmallFont());
        main.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        main.setHorizontalAlignment(SwingConstants.LEFT);
        row.add(main);

        if (ing.source() != null)
        {
            JLabel source = new JLabel("<html><span style='color:#888'>&nbsp;&nbsp;" + ing.source() + "</span></html>");
            source.setFont(FontManager.getRunescapeSmallFont());
            source.setHorizontalAlignment(SwingConstants.LEFT);
            row.add(source);
        }
        return row;
    }

    private static String prettyName(PotionDef.Category c)
    {
        String s = c.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
