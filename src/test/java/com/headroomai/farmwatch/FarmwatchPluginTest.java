package com.headroomai.farmwatch;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher. Side-loads FarmwatchPlugin into a fresh RuneLite client
 * so you can test without going through the Plugin Hub review cycle.
 *
 * Run via: {@code ./gradlew runClient}
 */
public class FarmwatchPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(FarmwatchPlugin.class);
        RuneLite.main(args);
    }
}
