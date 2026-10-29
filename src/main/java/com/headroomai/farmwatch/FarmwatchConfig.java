package com.headroomai.farmwatch;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("farmwatch")
public interface FarmwatchConfig extends Config
{
    @ConfigItem(
        keyName = "enableServer",
        name = "Enable HTTP server",
        description = "Serve farming state on 127.0.0.1 so the companion webpage can read it live. Loopback only, CORS-allowlisted. Toggle off if you only want the file snapshots.",
        position = 1
    )
    default boolean enableServer() { return true; }

    @ConfigItem(
        keyName = "httpPort",
        name = "HTTP port",
        description = "Port for the loopback server. On bind failure the plugin tries +1 .. +9.",
        position = 2
    )
    default int httpPort() { return 17060; }

    @ConfigItem(
        keyName = "allowedOrigins",
        name = "Allowed origins",
        description = "Comma-separated CORS allowlist. Use <username>.github.io for Pages. Supports http://localhost:* and http://127.0.0.1:*.",
        position = 3
    )
    default String allowedOrigins()
    {
        return "https://hanamigg.github.io,http://localhost:*,http://127.0.0.1:*,null";
    }

    @ConfigItem(
        keyName = "enableFileExport",
        name = "Enable JSON snapshot",
        description = "Write state-<accthash>.json for the webpage file-upload fallback.",
        position = 4
    )
    default boolean enableFileExport() { return true; }

    @ConfigItem(
        keyName = "exportDir",
        name = "Export directory",
        description = "Where to write snapshot JSON. Leave blank for ~/.runelite/osrs-farming-tracker/.",
        position = 5
    )
    default String exportDir() { return ""; }
}
