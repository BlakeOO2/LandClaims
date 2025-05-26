package org.example;
// DataManager.java

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataManager {
    private final Main plugin;
    private final File dataFile;
    private FileConfiguration data;
    private boolean loaded = false;

    public DataManager(Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        // Only create empty data file if it doesn't exist
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
                data = YamlConfiguration.loadConfiguration(dataFile);
                data.createSection("claims");
                saveData();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml");
                e.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
                data = YamlConfiguration.loadConfiguration(dataFile);
                data.createSection("claims");
                saveData();
                plugin.getLogger().info("Created new data file");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml");
                e.printStackTrace();
                return;
            }
        } else {
            data = YamlConfiguration.loadConfiguration(dataFile);
            validateDataFile();
            plugin.getLogger().info("Loaded existing data file");
        }
        loaded = true;
    }



    public void saveData() {
        if (!loaded) return;
        try {
            data.save(dataFile);
            plugin.getLogger().info("Saved claims data");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data to " + dataFile);
            e.printStackTrace();
        }
    }
    public void saveAllClaims(Set<Claim> claims) {
        // Clear existing claims section
        data.set("claims", null);
        data.createSection("claims");

        // Save each claim with a unique ID
        for (Claim claim : claims) {
            String claimId = UUID.randomUUID().toString();
            saveClaim(claim, claimId);
        }

        // Save to file
        saveData();
        plugin.getLogger().info("Saved " + claims.size() + " claims to data file");
    }

    // This method is for saving a single claim
    public void saveClaim(Claim claim) {
        try {
            // Generate a unique ID for this claim
            String claimId = UUID.randomUUID().toString();
            saveClaim(claim, claimId);
        } catch (Exception e) {
            plugin.getLogger().severe("Error saving claim: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // This is a private helper method used by both saveClaim and saveAllClaims
    private void saveClaim(Claim claim, String claimId) {
        try {
            ConfigurationSection claimSection = data.createSection("claims." + claimId);

            // Save basic claim data
            claimSection.set("owner", claim.getOwner().toString());
            claimSection.set("world", claim.getWorld());
            claimSection.set("isAdminClaim", claim.isAdminClaim());

            // Save corners
            claimSection.set("corner1.x", claim.getCorner1().getBlockX());
            claimSection.set("corner1.y", claim.getCorner1().getBlockY());
            claimSection.set("corner1.z", claim.getCorner1().getBlockZ());

            claimSection.set("corner2.x", claim.getCorner2().getBlockX());
            claimSection.set("corner2.y", claim.getCorner2().getBlockY());
            claimSection.set("corner2.z", claim.getCorner2().getBlockZ());

            // Save trusted players
            ConfigurationSection trustedSection = claimSection.createSection("trusted");
            for (Map.Entry<UUID, TrustLevel> entry : claim.getTrustedPlayers().entrySet()) {
                trustedSection.set(entry.getKey().toString(), entry.getValue().name());
            }

            // Save flags
            ConfigurationSection flagsSection = claimSection.createSection("flags");
            for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
                flagsSection.set(entry.getKey().name(), entry.getValue());
            }

            plugin.getLogger().info("Saved claim " + claimId + " for owner " + claim.getOwner());
        } catch (Exception e) {
            plugin.getLogger().severe("Error saving claim " + claimId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Claim> loadAllClaims() {
        List<Claim> claims = new ArrayList<>();
        ConfigurationSection claimsSection = data.getConfigurationSection("claims");

        if (claimsSection == null) {
            plugin.getLogger().info("No claims section found in data file.");
            return claims;
        }

        for (String claimId : claimsSection.getKeys(false)) {
            try {
                ConfigurationSection claimSection = claimsSection.getConfigurationSection(claimId);
                if (claimSection == null) continue;

                // Get owner UUID
                String ownerString = claimSection.getString("owner");
                if (ownerString == null || ownerString.isEmpty()) {
                    plugin.getLogger().warning("Invalid owner UUID for claim: " + claimId);
                    continue;
                }

                UUID owner;
                try {
                    owner = UUID.fromString(ownerString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID format for claim " + claimId + ": " + ownerString);
                    continue;
                }

                // Get world
                String worldName = claimSection.getString("world");
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World not found for claim " + claimId + ": " + worldName);
                    continue;
                }

                // Create locations
                Location corner1 = new Location(world,
                        claimSection.getInt("corner1.x"),
                        claimSection.getInt("corner1.y"),
                        claimSection.getInt("corner1.z"));

                Location corner2 = new Location(world,
                        claimSection.getInt("corner2.x"),
                        claimSection.getInt("corner2.y"),
                        claimSection.getInt("corner2.z"));

                // Create claim
                Claim claim = new Claim(owner, corner1, corner2);
                claim.setAdminClaim(claimSection.getBoolean("isAdminClaim", false));

                // Load trusted players
                ConfigurationSection trustedSection = claimSection.getConfigurationSection("trusted");
                if (trustedSection != null) {
                    for (String trusteeId : trustedSection.getKeys(false)) {
                        try {
                            UUID trusteeUUID = UUID.fromString(trusteeId);
                            TrustLevel level = TrustLevel.valueOf(trustedSection.getString(trusteeId));
                            claim.setTrust(trusteeUUID, level);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid trust data in claim " + claimId);
                        }
                    }
                }

                // Load flags
                ConfigurationSection flagsSection = claimSection.getConfigurationSection("flags");
                if (flagsSection != null) {
                    for (String flagName : flagsSection.getKeys(false)) {
                        try {
                            ClaimFlag flag = ClaimFlag.valueOf(flagName);
                            claim.setFlag(flag, flagsSection.getBoolean(flagName));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid flag in claim " + claimId + ": " + flagName);
                        }
                    }
                }

                claims.add(claim);
                plugin.getLogger().info("Loaded claim " + claimId + " for " + owner);

            } catch (Exception e) {
                plugin.getLogger().severe("Error loading claim " + claimId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + claims.size() + " claims");
        return claims;
    }
    public void debugDataFile() {
        plugin.getLogger().info("=== Data File Debug ===");
        plugin.getLogger().info("File exists: " + dataFile.exists());
        plugin.getLogger().info("File size: " + dataFile.length() + " bytes");

        if (data != null) {
            ConfigurationSection claimsSection = data.getConfigurationSection("claims");
            if (claimsSection != null) {
                Set<String> claimIds = claimsSection.getKeys(false);
                plugin.getLogger().info("Number of claims in file: " + claimIds.size());

                for (String claimId : claimIds) {
                    ConfigurationSection claim = claimsSection.getConfigurationSection(claimId);
                    if (claim != null) {
                        plugin.getLogger().info("Claim " + claimId + ":");
                        plugin.getLogger().info("  Owner: " + claim.getString("owner"));
                        plugin.getLogger().info("  World: " + claim.getString("world"));
                    }
                }
            } else {
                plugin.getLogger().info("No claims section found in data");
            }
        } else {
            plugin.getLogger().info("Data is null!");
        }
        plugin.getLogger().info("=====================");
    }


    private void validateDataFile() {
        if (data == null) {
            data = new YamlConfiguration();
        }

        if (!data.contains("claims")) {
            data.createSection("claims");
            try {
                data.save(dataFile);
                plugin.getLogger().info("Created empty claims section in data file");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save empty claims section: " + e.getMessage());
            }
        }
    }

    public void deleteClaim(Claim claim) {
        String claimId = findClaimId(claim);
        if (claimId != null) {
            data.set("claims." + claimId, null);
            saveData();
        }
    }


    private String findClaimId(Claim claim) {
        ConfigurationSection claims = data.getConfigurationSection("claims");
        if (claims == null) return null;

        for (String claimId : claims.getKeys(false)) {
            ConfigurationSection claimSection = claims.getConfigurationSection(claimId);
            if (claimSection.getString("owner").equals(claim.getOwner().toString())) {
                // Check coordinates match
                if (matchesLocation(claimSection, claim)) {
                    return claimId;
                }
            }
        }
        return null;
    }

    private boolean matchesLocation(ConfigurationSection claimSection, Claim claim) {
        return claim.getCorner1().getBlockX() == claimSection.getInt("corner1.x") &&
                claim.getCorner1().getBlockZ() == claimSection.getInt("corner1.z") &&
                claim.getCorner2().getBlockX() == claimSection.getInt("corner2.x") &&
                claim.getCorner2().getBlockZ() == claimSection.getInt("corner2.z");
    }

    public void reloadData() {
        loadData();
    }
}

