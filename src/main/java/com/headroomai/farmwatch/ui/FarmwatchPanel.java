package com.headroomai.farmwatch.ui;

import com.headroomai.farmwatch.FarmwatchConfig;
import com.headroomai.farmwatch.FarmwatchState;
import com.headroomai.farmwatch.potion.BankTracker;
import com.headroomai.farmwatch.potion.EquipmentTracker;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar container panel. Hosts the four Farmwatch views as tabs:
 * Potions (brew planner), Dashboard (patch cards), Timeline, Run.
 */
public class FarmwatchPanel extends PluginPanel
{
    private final JTabbedPane tabs = new JTabbedPane();

    public FarmwatchPanel(BankTracker bankTracker,
                          EquipmentTracker equipmentTracker,
                          FarmwatchState farmState,
                          Client client,
                          FarmwatchConfig config)
    {
        super(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        PotionsTab potions = new PotionsTab(bankTracker, equipmentTracker, farmState, client, config);
        DashboardTab dashboard = new DashboardTab(farmState, client);
        TimelineTab timeline = new TimelineTab(farmState, client);
        RunPlannerTab run = new RunPlannerTab(farmState, client);

        tabs.addTab("Potions", wrapScroll(potions));
        tabs.addTab("Dashboard", wrapScroll(dashboard));
        tabs.addTab("Timeline", wrapScroll(timeline));
        tabs.addTab("Run", wrapScroll(run));

        add(tabs, BorderLayout.CENTER);
    }

    private static JScrollPane wrapScroll(java.awt.Component view)
    {
        JScrollPane sp = new JScrollPane(view,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(14);
        sp.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return sp;
    }
}
