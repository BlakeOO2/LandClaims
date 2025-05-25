package org.example;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final Main plugin;
    private FileConfiguration config;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        setDefaultValues();
    }

    private void setDefaultValues() {
        // Only set defaults if they don't exist
        if (!config.contains("debug")) {
            config.set("debug", true);
        }

        // Claiming settings
        if (!config.contains("claiming.tool")) {
            config.set("claiming.tool", "GOLDEN_SHOVEL");
        }
        if (!config.contains("claiming.admin-tool")) {
            config.set("claiming.admin-tool", "GOLDEN_AXE");
        }
        if (!config.contains("claiming.default-blocks")) {
            config.set("claiming.default-blocks", 2000);
        }
        if (!config.contains("claiming.blocks-per-hour")) {
            config.set("claiming.blocks-per-hour", 500);
        }
        if (!config.contains("claiming.max-blocks")) {
            config.set("claiming.max-blocks", 1000000);
        }
        if (!config.contains("claiming.minimum-size")) {
            config.set("claiming.minimum-size", 100);
        }
        if (!config.contains("claiming.max-claim-size")) {
            config.set("claiming.max-claim-size", 1000000);
        }

        // Visualization settings
        if (!config.contains("visualization.corner-block")) {
            config.set("visualization.corner-block", "GOLD_BLOCK");
        }
        if (!config.contains("visualization.border-block")) {
            config.set("visualization.border-block", "REDSTONE_BLOCK");
        }
        if (!config.contains("visualization.admin-corner-block")) {
            config.set("visualization.admin-corner-block", "DIAMOND_BLOCK");
        }
        if (!config.contains("visualization.spacing")) {
            config.set("visualization.spacing", 10);
        }
        if (!config.contains("visualization.duration")) {
            config.set("visualization.duration", 30);
        }
        if (!config.contains("visualization.nearby-radius")) {
            config.set("visualization.nearby-radius", 50);
        }

        // Server settings
        if (!config.contains("server.name")) {
            config.set("server.name", "&3&lSmoky&b&lPeaks");
        }
        if (!config.contains("server.admin-claim-prefix")) {
            config.set("server.admin-claim-prefix", "§f[Admin] ");
        }

        // Global flags
        if (!config.contains("flags.global.PVP")) {
            config.set("flags.global.PVP", false);
        }
        if (!config.contains("flags.global.MONSTERS")) {
            config.set("flags.global.MONSTERS", false);
        }
        if (!config.contains("flags.global.EXPLOSIONS")) {
            config.set("flags.global.EXPLOSIONS", false);
        }
        if (!config.contains("flags.global.FIRE_SPREAD")) {
            config.set("flags.global.FIRE_SPREAD", false);
        }
        if (!config.contains("flags.global.MOB_GRIEFING")) {
            config.set("flags.global.MOB_GRIEFING", false);
        }
        if (!config.contains("flags.global.LEAF_DECAY")) {
            config.set("flags.global.LEAF_DECAY", true);
        }
        if (!config.contains("flags.global.VILLAGER_TRADING")) {
            config.set("flags.global.VILLAGER_TRADING", true);
        }
        if (!config.contains("flags.global.REDSTONE")) {
            config.set("flags.global.REDSTONE", true);
        }
        if (!config.contains("flags.global.PISTONS")) {
            config.set("flags.global.PISTONS", true);
        }
        if (!config.contains("flags.global.HOPPERS")) {
            config.set("flags.global.HOPPERS", true);
        }

        // World-specific settings
        if (!config.contains("flags.worlds.world.allow-claiming")) {
            config.set("flags.worlds.world.allow-claiming", true);
        }
        if (!config.contains("flags.worlds.world_nether.allow-claiming")) {
            config.set("flags.worlds.world_nether.allow-claiming", false);
        }
        if (!config.contains("flags.worlds.world_nether.EXPLOSIONS")) {
            config.set("flags.worlds.world_nether.EXPLOSIONS", true);
        }
        if (!config.contains("flags.worlds.world_nether.FIRE_SPREAD")) {
            config.set("flags.worlds.world_nether.FIRE_SPREAD", true);
        }
        if (!config.contains("flags.worlds.world_the_end.allow-claiming")) {
            config.set("flags.worlds.world_the_end.allow-claiming", false);
        }

        // Save any changes
        plugin.saveConfig();
    }


    // Getters for all config values
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    public Material getClaimingTool() {
        return Material.valueOf(config.getString("claiming.tool", "GOLDEN_SHOVEL"));
    }

    public Material getAdminClaimingTool() {
        return Material.valueOf(config.getString("claiming.admin-tool", "GOLDEN_AXE"));
    }

    public int getDefaultClaimBlocks() {
        return config.getInt("claiming.default-blocks", 1000);
    }

    public int getBlocksPerHour() {
        return config.getInt("claiming.blocks-per-hour", 100);
    }

    public int getMaxBlocks() {
        return config.getInt("claiming.max-blocks", 10000);
    }

    public int getMinimumClaimSize() {
        return config.getInt("claiming.minimum-size", 100);
    }

    public int getMaxClaimSize() {
        return config.getInt("claiming.max-claim-size", 10000);
    }

    public Material getCornerBlock() {
        return Material.valueOf(config.getString("visualization.corner-block", "GOLD_BLOCK"));
    }

    public Material getBorderBlock() {
        return Material.valueOf(config.getString("visualization.border-block", "REDSTONE_BLOCK"));
    }

    public Material getAdminCornerBlock() {
        return Material.valueOf(config.getString("visualization.admin-corner-block", "DIAMOND_BLOCK"));
    }

    public int getVisualizationSpacing() {
        return config.getInt("visualization.spacing", 10);
    }

    public int getVisualizationDuration() {
        return config.getInt("visualization.duration", 30);
    }

    public int getNearbyRadius() {
        return config.getInt("visualization.nearby-radius", 50);
    }

    public String getServerName() {
        return config.getString("server.name", "Server");
    }

    public String getAdminClaimPrefix() {
        return config.getString("server.admin-claim-prefix", "§4[Admin] ");
    }

    public boolean isClaimingAllowedInWorld(String worldName) {
        return config.getBoolean("flags.worlds." + worldName + ".allow-claiming", true);
    }

    public boolean getGlobalFlag(String flag) {
        return config.getBoolean("flags.global." + flag, false);
    }

    public boolean getWorldFlag(String worldName, String flag) {
        return config.getBoolean("flags.worlds." + worldName + "." + flag,
                getGlobalFlag(flag));
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
