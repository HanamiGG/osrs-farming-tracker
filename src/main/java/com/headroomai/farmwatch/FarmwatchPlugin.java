package com.headroomai.farmwatch;

import com.google.inject.Provides;
import com.headroomai.farmwatch.model.AccountState;
import com.headroomai.farmwatch.potion.BankTracker;
import com.headroomai.farmwatch.ui.PotionPlannerPanel;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
    name = "Farmwatch",
    description = "Potion planner for ironmen (sidebar) with a farming-patch mirror behind it",
    tags = {"potion", "planner", "ironman", "herblore", "farming", "timers", "tracker", "web", "dashboard"}
)
public class FarmwatchPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private FarmwatchConfig config;
    @Inject private ClientToolbar clientToolbar;
    @Inject private EventBus eventBus;

    private FarmwatchState state;
    private PatchReader patchReader;
    private StateExporter exporter;
    private LocalServer server;
    private NavigationButton potionNav;
    private BankTracker bankTracker;

    private boolean dirty = false;

    @Provides
    FarmwatchConfig provideConfig(ConfigManager cm)
    {
        return cm.getConfig(FarmwatchConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        Path exportDir = resolveExportDir();
        state = new FarmwatchState();
        patchReader = new PatchReader(client, configManager, state);
        exporter = new StateExporter(exportDir, config);

        if (config.enableServer())
        {
            server = new LocalServer(config, state);
            try
            {
                int actualPort = server.start();
                log.info("Farmwatch HTTP server bound to 127.0.0.1:{}", actualPort);
            }
            catch (IOException e)
            {
                log.error("Farmwatch HTTP server failed to start", e);
                server = null;
            }
        }

        if (config.showPotionPanel())
        {
            bankTracker = new BankTracker();
            eventBus.register(bankTracker);
            BufferedImage icon = ImageUtil.loadImageResource(FarmwatchPlugin.class, "/com/headroomai/farmwatch/panel-icon.png");
            potionNav = NavigationButton.builder()
                .tooltip("Potion Planner")
                .icon(icon)
                .priority(5)
                .panel(new PotionPlannerPanel(bankTracker))
                .build();
            clientToolbar.addNavigation(potionNav);
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        if (potionNav != null)
        {
            clientToolbar.removeNavigation(potionNav);
            potionNav = null;
        }
        if (bankTracker != null)
        {
            eventBus.unregister(bankTracker);
            bankTracker = null;
        }
        if (server != null)
        {
            server.stop();
            server = null;
        }
        if (exporter != null && state != null)
        {
            exporter.flush(state);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        if (e.getGameState() == GameState.LOGGED_IN)
        {
            long hash = client.getAccountHash();
            if (hash != -1)
            {
                AccountState acc = state.touch(hash);
                acc.setDisplayName(client.getLocalPlayer() != null
                    ? client.getLocalPlayer().getName()
                    : null);
                patchReader.primeFromStockTracker(hash);
                dirty = true;
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged e)
    {
        if (patchReader.handleVarbit(e))
        {
            dirty = true;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e)
    {
        if ("timetracking".equals(e.getGroup()))
        {
            if (patchReader.handleStockConfigChange(e))
            {
                dirty = true;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick e)
    {
        if (!dirty)
        {
            return;
        }
        dirty = false;
        state.touchGeneratedAt();
        if (config.enableFileExport())
        {
            exporter.flushAsync(state);
        }
        if (server != null)
        {
            server.refreshSnapshot(state);
        }
    }

    private Path resolveExportDir()
    {
        String configured = config.exportDir();
        if (configured != null && !configured.isBlank())
        {
            return Paths.get(configured);
        }
        return Paths.get(System.getProperty("user.home"), ".runelite", "osrs-farming-tracker");
    }
}
