package org.example;

// BlockAccumulator.java

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import java.io.IOException;
import java.util.UUID;


import java.util.HashMap;
import java.util.Map;

public class BlockAccumulator {
    private final Main plugin;
    private final File timeFile;
    private FileConfiguration timeData;
    private final Map<UUID, Integer> playerBlocks;
    private  int blocksPerHour;
    private  int maxBlocks;


    public BlockAccumulator(Main plugin) {
        this.plugin = plugin;
        this.timeFile = new File(plugin.getDataFolder(), "playtime.yml");
        this.playerBlocks = new HashMap<>();

        // Load config values directly from config
        FileConfiguration config = plugin.getConfig();
        this.blocksPerHour = config.getInt("claiming.blocks-per-hour", 500);
        this.maxBlocks = config.getInt("claiming.max-blocks", 1000000);

        plugin.getLogger().info("Initialized BlockAccumulator with maxBlocks=" + maxBlocks +
                ", blocksPerHour=" + blocksPerHour);

        loadData();
        startAccumulationTask();
    }

    public void reloadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.blocksPerHour = config.getInt("claiming.blocks-per-hour", 100);
        this.maxBlocks = config.getInt("claiming.max-blocks", 10000);
        plugin.getLogger().info("Loaded block accumulator config: maxBlocks=" + maxBlocks + ", blocksPerHour=" + blocksPerHour);
    }

    public void reloadData() {
        if (!timeFile.exists()) {
            try {
                timeFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create playtime.yml");
                e.printStackTrace();
            }
        }
        timeData = YamlConfiguration.loadConfiguration(timeFile);

        // Load existing block counts
        ConfigurationSection blocksSection = timeData.getConfigurationSection("blocks");
        if (blocksSection != null) {
            playerBlocks.clear();
            for (String uuid : blocksSection.getKeys(false)) {
                playerBlocks.put(UUID.fromString(uuid), blocksSection.getInt(uuid));
            }
        }
    }

    private void loadData() {
        if (!timeFile.exists()) {
            try {
                timeFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create playtime.yml");
                e.printStackTrace();
            }
        }
        timeData = YamlConfiguration.loadConfiguration(timeFile);

        // Load existing block counts
        ConfigurationSection blocksSection = timeData.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String uuid : blocksSection.getKeys(false)) {
                playerBlocks.put(UUID.fromString(uuid), blocksSection.getInt(uuid));
            }
        }
    }

    public void saveData() {
        try {
            // Ensure timeData is initialized
            if (timeData == null) {
                timeData = YamlConfiguration.loadConfiguration(timeFile);
            }

            // Clear existing blocks section and create a new one
            timeData.set("blocks", null);
            timeData.createSection("blocks");

            // Save block counts
            for (Map.Entry<UUID, Integer> entry : playerBlocks.entrySet()) {
                timeData.set("blocks." + entry.getKey().toString(), entry.getValue());
            }

            // Save to file
            timeData.save(timeFile);
            plugin.debug("Saved block accumulator data");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save block accumulator data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startAccumulationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    addBlocks(player.getUniqueId(), blocksPerHour / 60); // Add blocks per minute
                }
                saveData();
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Run every minute (20 ticks * 60)
    }

    public void addBlocks(UUID playerUUID, int blocks) {
        int currentBlocks = getBlocks(playerUUID);
        int maxAllowed = plugin.getConfig().getInt("claiming.max-blocks", 1000000); // Get fresh from config
        setBlocks(playerUUID, Math.min(currentBlocks + blocks, maxAllowed));
    }

    public void setBlocks(UUID playerUUID, int blocks) {
        int maxAllowed = plugin.getConfig().getInt("claiming.max-blocks", 1000000); // Get fresh from config
        int finalAmount = Math.min(blocks, maxAllowed);
        playerBlocks.put(playerUUID, finalAmount);
        plugin.debug("Set blocks for " + playerUUID + " to " + finalAmount + " (max: " + maxAllowed + ")");
    }

    public int getBlocks(UUID playerUUID) {
        return playerBlocks.getOrDefault(playerUUID, plugin.getConfig().getInt("claiming.default-blocks", 2000));
    }

    public void removeBlocks(UUID playerUUID, int blocks) {
        int currentBlocks = getBlocks(playerUUID);
        setBlocks(playerUUID, Math.max(0, currentBlocks - blocks));
    }

    public int getMaxBlocks() {
        // Always get fresh from config
        int configMax = plugin.getConfig().getInt("claiming.max-blocks", 1000000);
        if (this.maxBlocks != configMax) {
            plugin.getLogger().info("Updating max blocks from " + this.maxBlocks + " to " + configMax);
            this.maxBlocks = configMax;
        }
        plugin.debug("Current max blocks: " + this.maxBlocks + ", Config max: " + configMax);
        return configMax; // Return the config value directly instead of the field
    }

    public int getBlocksPerHour() {
        return plugin.getConfig().getInt("claiming.blocks-per-hour", 500);
    }
    private void debug(String message) {
        plugin.debug("[ClassName] " + message);
    }

    public void updateConfig() {
        this.blocksPerHour = plugin.getConfig().getInt("claiming.blocks-per-hour", 500);
        this.maxBlocks = plugin.getConfig().getInt("claiming.max-blocks", 1000000);
        plugin.getLogger().info("Updated block accumulator config: maxBlocks=" + maxBlocks + ", blocksPerHour=" + blocksPerHour);
    }
}
