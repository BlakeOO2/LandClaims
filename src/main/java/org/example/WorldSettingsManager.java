// WorldSettingsManager.java
package org.example;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumMap;

public class WorldSettingsManager {
    private final Main plugin;
    private final Map<String, EnumMap<ClaimFlag, Boolean>> worldFlags;
    private EnumMap<ClaimFlag, Boolean> globalFlags;

    public WorldSettingsManager(Main plugin) {
        this.plugin = plugin;
        this.worldFlags = new HashMap<>();
        this.globalFlags = new EnumMap<>(ClaimFlag.class);
        loadSettings();
    }

    private void loadSettings() {
        ConfigurationSection config = plugin.getConfig();

        // Load global flags
        ConfigurationSection globalSection = config.getConfigurationSection("flags.global");
        if (globalSection == null) {
            globalSection = config.createSection("flags.global");
        }
        loadGlobalFlags(globalSection);

        // Load world-specific flags
        ConfigurationSection worldsSection = config.getConfigurationSection("flags.worlds");
        if (worldsSection == null) {
            worldsSection = config.createSection("flags.worlds");
        }

        // Load settings for each world
        for (World world : plugin.getServer().getWorlds()) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(world.getName());
            if (worldSection == null) {
                worldSection = worldsSection.createSection(world.getName());
            }
            loadWorldFlags(world.getName(), worldSection);
        }

        plugin.saveConfig();
    }


    private void loadGlobalFlags(ConfigurationSection section) {
        globalFlags.clear();
        for (ClaimFlag flag : ClaimFlag.values()) {
            boolean value = section.getBoolean(flag.name(), flag.getDefaultValue());
            globalFlags.put(flag, value);
            section.set(flag.name(), value);
        }
    }

    private void loadWorldFlags(String worldName, ConfigurationSection section) {
        EnumMap<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);
        for (ClaimFlag flag : ClaimFlag.values()) {
            boolean value = section.getBoolean(flag.name(), globalFlags.get(flag));
            flags.put(flag, value);
            section.set(flag.name(), value);
        }
        worldFlags.put(worldName, flags);
    }

    public void saveSettings() {
        ConfigurationSection config = plugin.getConfig();

        // Save global flags
        ConfigurationSection globalSection = config.getConfigurationSection("flags.global");
        for (Map.Entry<ClaimFlag, Boolean> entry : globalFlags.entrySet()) {
            globalSection.set(entry.getKey().name(), entry.getValue());
        }

        // Save world flags
        ConfigurationSection worldsSection = config.getConfigurationSection("flags.worlds");
        for (Map.Entry<String, EnumMap<ClaimFlag, Boolean>> worldEntry : worldFlags.entrySet()) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldEntry.getKey());
            if (worldSection == null) {
                worldSection = worldsSection.createSection(worldEntry.getKey());
            }

            for (Map.Entry<ClaimFlag, Boolean> flagEntry : worldEntry.getValue().entrySet()) {
                worldSection.set(flagEntry.getKey().name(), flagEntry.getValue());
            }
        }

        plugin.saveConfig();
    }

    public boolean getGlobalFlag(ClaimFlag flag) {
        return globalFlags.getOrDefault(flag, flag.getDefaultValue());
    }

    public void setGlobalFlag(ClaimFlag flag, boolean value) {
        globalFlags.put(flag, value);
        saveSettings();
    }

    public boolean getWorldFlag(String worldName, ClaimFlag flag) {
        EnumMap<ClaimFlag, Boolean> flags = worldFlags.get(worldName);
        if (flags != null && flags.containsKey(flag)) {
            return flags.get(flag);
        }
        return getGlobalFlag(flag);
    }

    public void setWorldFlag(String worldName, ClaimFlag flag, boolean value) {
        worldFlags.computeIfAbsent(worldName, k -> new EnumMap<>(ClaimFlag.class))
                .put(flag, value);
        saveSettings();
    }

    public boolean isClaimingAllowed(World world) {
        return plugin.getConfig().getBoolean("flags.worlds." + world.getName() + ".allow-claiming", true);
    }

    public void setClaimingAllowed(World world, boolean allowed) {
        plugin.getConfig().set("flags.worlds." + world.getName() + ".allow-claiming", allowed);
        plugin.saveConfig();
    }
}
