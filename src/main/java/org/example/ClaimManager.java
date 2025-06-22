package org.example;
// ClaimManager.java

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class ClaimManager {
    private final Main plugin;
    private final Map<UUID, Set<Claim>> playerClaims;
    private final Map<String, Set<Claim>> worldClaims;
    private final Set<UUID> adminsBypass;
    private boolean loaded = false;


    public boolean isAdminBypassing(UUID adminUUID) {
        return adminsBypass.contains(adminUUID);
    }

    public void setAdminBypass(UUID adminUUID, boolean bypass) {
        if (bypass) {
            adminsBypass.add(adminUUID);
        } else {
            adminsBypass.remove(adminUUID);
        }
    }

    public ClaimManager(Main plugin) {
        this.plugin = plugin;
        this.playerClaims = new HashMap<>();
        this.worldClaims = new HashMap<>();
        this.adminsBypass = new HashSet<>();
        loadClaims();
        startCacheRefreshTask();
    }


//    private void rebuildDatabase() {
//        try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
//            Connection conn = transaction.getConnection();
//
//            // Drop and recreate tables
//            try (Statement stmt = conn.createStatement()) {
//                stmt.execute("DROP TABLE IF EXISTS claims");
//                stmt.execute("DROP TABLE IF EXISTS trusted_players");
//                stmt.execute("DROP TABLE IF EXISTS claim_flags");
//
//                // Reinitialize database
//                initializeDatabase();
//
//                // Resave all claims from cache
//                for (Set<Claim> claims : plugin.getClaimManager().getAllClaims()) {
//                    for (Claim claim : claims) {
//                        saveClaim(claim);
//                    }
//                }
//            }
//
//            transaction.commit();
//        } catch (SQLException e) {
//            plugin.getLogger().severe("Failed to rebuild database: " + e.getMessage());
//        }
//    } //might not need this, no usage and has several errors
private void startCacheRefreshTask() {
    long refreshInterval = plugin.getConfig().getLong("database.cache-refresh-interval", 5) * 1200L; // Convert minutes to ticks (20 ticks per second * 60 seconds)

    Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
        plugin.getLogger().info("Refreshing claim cache...");
        try {
            refreshCache();
            plugin.getLogger().info("Claim cache refresh complete");
        } catch (Exception e) {
            plugin.getLogger().severe("Error refreshing claim cache: " + e.getMessage());
            e.printStackTrace();
        }
    }, refreshInterval, refreshInterval);
}

    public void refreshCache() {
        // Create temporary maps to avoid concurrent modification
        Map<UUID, Set<Claim>> newPlayerClaims = new HashMap<>();
        Map<String, Set<Claim>> newWorldClaims = new HashMap<>();

        // Load claims from database
        List<Claim> claims = plugin.getDatabaseManager().loadAllClaims();

        // Populate temporary maps
        for (Claim claim : claims) {
            // Add to player claims
            newPlayerClaims
                    .computeIfAbsent(claim.getOwner(), k -> new HashSet<>())
                    .add(claim);

            // Add to world claims
            newWorldClaims
                    .computeIfAbsent(claim.getWorld(), k -> new HashSet<>())
                    .add(claim);
        }

        // Synchronize the update of the main maps
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Instead of clearing and replacing, merge the new data
            // This preserves any in-memory changes that might not be in the database yet
            mergeClaimMaps(playerClaims, newPlayerClaims);
            mergeClaimMaps(worldClaims, newWorldClaims);
            plugin.getLogger().info("Claim cache refreshed and merged with existing data");
        });
    }

    private <K> void mergeClaimMaps(Map<K, Set<Claim>> existingMap, Map<K, Set<Claim>> newMap) {
        // First, remove claims that no longer exist
        for (K key : new HashSet<>(existingMap.keySet())) {
            if (!newMap.containsKey(key)) {
                existingMap.remove(key);
                continue;
            }

            // Remove claims that aren't in the new map
            Set<Claim> existingClaims = existingMap.get(key);
            Set<Claim> newClaims = newMap.get(key);

            existingClaims.removeIf(existingClaim ->
                    !containsMatchingClaim(newClaims, existingClaim));
        }

        // Then add new claims
        for (Map.Entry<K, Set<Claim>> entry : newMap.entrySet()) {
            K key = entry.getKey();
            Set<Claim> newClaims = entry.getValue();

            if (!existingMap.containsKey(key)) {
                existingMap.put(key, new HashSet<>(newClaims));
                continue;
            }

            // Add claims that don't exist yet
            Set<Claim> existingClaims = existingMap.get(key);
            for (Claim newClaim : newClaims) {
                if (!containsMatchingClaim(existingClaims, newClaim)) {
                    existingClaims.add(newClaim);
                }
            }
        }
    }

    private boolean containsMatchingClaim(Set<Claim> claims, Claim targetClaim) {
        for (Claim claim : claims) {
            if (claim.getOwner().equals(targetClaim.getOwner()) &&
                    claim.getWorld().equals(targetClaim.getWorld()) &&
                    claim.getCorner1().getBlockX() == targetClaim.getCorner1().getBlockX() &&
                    claim.getCorner1().getBlockZ() == targetClaim.getCorner1().getBlockZ() &&
                    claim.getCorner2().getBlockX() == targetClaim.getCorner2().getBlockX() &&
                    claim.getCorner2().getBlockZ() == targetClaim.getCorner2().getBlockZ()) {
                return true;
            }
        }
        return false;
    }


    public void loadClaims() {
        loadClaimsFromDatabase(); // Just call the database-only method
    }

    public void loadClaimsFromDatabase() {
        if (loaded) {
            plugin.getLogger().warning("Attempted to load claims when already loaded!");
            return;
        }

        plugin.getLogger().info("Loading claims from database...");

        // Clear existing cache
        playerClaims.clear();
        worldClaims.clear();

        // Load from database only
        List<Claim> claims = plugin.getDatabaseManager().loadAllClaims();

        // Add claims to cache
        int count = 0;
        for (Claim claim : claims) {
            addClaimToCache(claim);
            count++;
        }

        loaded = true;
        plugin.getLogger().info("Loaded " + count + " claims from database");
    }


    private void addClaimToCache(Claim claim) {
        // Add to player claims cache
        playerClaims.computeIfAbsent(claim.getOwner(), k -> new HashSet<>()).add(claim);

        // Add to world claims cache
        worldClaims.computeIfAbsent(claim.getWorld(), k -> new HashSet<>()).add(claim);
    }

    private void removeClaimFromCache(Claim claim) {
        // Remove from player claims cache
        Set<Claim> playerClaimSet = playerClaims.get(claim.getOwner());
        if (playerClaimSet != null) {
            playerClaimSet.remove(claim);
            if (playerClaimSet.isEmpty()) {
                playerClaims.remove(claim.getOwner());
            }
        }

        // Remove from world claims cache
        Set<Claim> worldClaimSet = worldClaims.get(claim.getWorld());
        if (worldClaimSet != null) {
            worldClaimSet.remove(claim);
            if (worldClaimSet.isEmpty()) {
                worldClaims.remove(claim.getWorld());
            }
        }
    }
    // In ClaimManager.java
    public void debugCache() {
        plugin.getLogger().info("=== Cache Debug ===");
        plugin.getLogger().info("Total player claims cached: " + playerClaims.size());
        plugin.getLogger().info("Total world claims cached: " + worldClaims.size());

        for (Map.Entry<UUID, Set<Claim>> entry : playerClaims.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            plugin.getLogger().info(player.getName() + " has " + entry.getValue().size() + " claims");
        }

        for (Map.Entry<String, Set<Claim>> entry : worldClaims.entrySet()) {
            plugin.getLogger().info("World " + entry.getKey() + " has " + entry.getValue().size() + " claims");
        }
    }



    // Add periodic cache refresh
    public void addClaim(Claim claim) {
        plugin.getLogger().info("[Debug] Adding claim to cache for " +
                Bukkit.getOfflinePlayer(claim.getOwner()).getName());

        // Save to database first
        plugin.getDatabaseManager().saveClaim(claim);

        // Add to cache
        playerClaims.computeIfAbsent(claim.getOwner(), k -> new HashSet<>()).add(claim);
        worldClaims.computeIfAbsent(claim.getWorld(), k -> new HashSet<>()).add(claim);

        plugin.getLogger().info("[Debug] Cache updated. Player now has " +
                getPlayerClaims(claim.getOwner()).size() + " claims");

        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().updateClaim(claim);
        }
    }



    public void removeClaim(Claim claim) {
        // Remove from database first
        plugin.getDatabaseManager().deleteClaim(claim);

        // Remove from cache
        removeClaimFromCache(claim);

        plugin.getLogger().info("Removed claim for " + claim.getOwner() + " at " +
                claim.getCorner1().getBlockX() + "," + claim.getCorner1().getBlockZ());

        //Blue map logic for removal of a claim
        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().removeClaim(claim);
        }

    }

    public void saveAllClaims() {
        if (!loaded) {
            plugin.getLogger().warning("Attempted to save claims when not loaded!");
            return;
        }

        plugin.getLogger().info("Saving all claims to database...");

        int count = 0;
        for (Set<Claim> claims : playerClaims.values()) {
            for (Claim claim : claims) {
                plugin.getDatabaseManager().saveClaim(claim);
                count++;
            }
        }

        plugin.getLogger().info("Saved " + count + " claims to database");
    }

    // In ClaimManager.java, add:
    public boolean wouldOverlap(Location corner1, Location corner2, Claim excludeClaim) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        Set<Claim> worldClaimSet = worldClaims.get(corner1.getWorld().getName());
        if (worldClaimSet == null) return false;

        for (Claim otherClaim : worldClaimSet) {
            // Skip checking against the claim being modified
            if (otherClaim == excludeClaim) continue;

            // Get the other claim's corners
            int otherMinX = Math.min(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
            int otherMaxX = Math.max(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
            int otherMinZ = Math.min(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());
            int otherMaxZ = Math.max(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());

            // Check for overlap
            if (!(maxX < otherMinX || minX > otherMaxX || maxZ < otherMinZ || minZ > otherMaxZ)) {
                return true; // Claims overlap
            }
        }

        return false; // No overlap found
    }


    public boolean createClaim(Player player, Location corner1, Location corner2) {
        if (!canCreateClaim(player, corner1, corner2)) {
            return false;
        }

        Claim claim = new Claim(player.getUniqueId(), corner1, corner2);
        addClaim(claim);
        return true;
    }
    public Set<Claim> getNearbyClaims(Location location, int radius) {
        Set<Claim> nearbyClaims = new HashSet<>();
        Set<Claim> worldClaims = this.worldClaims.get(location.getWorld().getName());

        if (worldClaims == null) return nearbyClaims;

        int playerX = location.getBlockX();
        int playerZ = location.getBlockZ();

        for (Claim claim : worldClaims) {
            // Check if any corner of the claim is within radius
            if (isWithinRadius(playerX, playerZ, claim.getCorner1().getBlockX(),
                    claim.getCorner1().getBlockZ(), radius) ||
                    isWithinRadius(playerX, playerZ, claim.getCorner2().getBlockX(),
                            claim.getCorner2().getBlockZ(), radius)) {
                nearbyClaims.add(claim);
            }
        }

        return nearbyClaims;
    }

    private boolean isWithinRadius(int x1, int z1, int x2, int z2, int radius) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        return (dx * dx + dz * dz) <= radius * radius;
    }

    public Set<Claim> getPlayerClaims(UUID playerUUID) {
        return playerClaims.getOrDefault(playerUUID, new HashSet<>());
    }

    private boolean canCreateClaim(Player player, Location corner1, Location corner2) {
        int requiredBlocks = calculateRequiredBlocks(corner1, corner2);
        int availableBlocks = plugin.getBlockAccumulator().getBlocks(player.getUniqueId());
        int maxClaimSize = plugin.getConfig().getInt("claiming.max-claim-size", 1000000);
        int maxTotalBlocks = plugin.getBlockAccumulator().getMaxBlocks();

        // Check if player has enough blocks
        if (availableBlocks < requiredBlocks) {
            player.sendMessage("§c[LandClaims] You need " + requiredBlocks +
                    " blocks to create this claim, but you only have " + availableBlocks + ".");
            return false;
        }

        // Check for overlap
        if (overlapsWithExistingClaim(corner1, corner2)) {
            player.sendMessage("§c[LandClaims] This area overlaps with an existing claim.");
            return false;
        }

        // Check minimum size
        int minSize = plugin.getConfig().getInt("claiming.minimum-size", 100);
        if (requiredBlocks < minSize) {
            player.sendMessage("§c[LandClaims] Claims must be at least " + minSize + " blocks in size.");
            return false;
        }

        // Check maximum size
        if (requiredBlocks > maxClaimSize) {
            player.sendMessage("§c[LandClaims] Claims cannot be larger than " + maxClaimSize + " blocks.");
            return false;
        }

        // Check total blocks limit
        Set<Claim> playerClaims = getPlayerClaims(player.getUniqueId());
        int currentTotalBlocks = playerClaims.stream()
                .mapToInt(Claim::getSize)
                .sum();

        if (currentTotalBlocks + requiredBlocks > maxTotalBlocks) {
            player.sendMessage("§c[LandClaims] This claim would exceed your maximum total blocks of " + maxTotalBlocks + ".");
            return false;
        }

        return true;
    }
    public void handleDatabaseError() {
        plugin.getLogger().warning("Attempting to recover from database error...");

        // Save current cache to backup
        backupCurrentCache();

        // Attempt to reconnect to database
        if (plugin.getDatabaseManager().reconnect()) {
            // Reload claims from database
            loadClaims();
        } else {
            plugin.getLogger().severe("Could not recover from database error!");
        }
    }

    private void backupCurrentCache() {
        try {
            File backupFile = new File(plugin.getDataFolder(), "claims_cache_backup.yml");
            YamlConfiguration backup = new YamlConfiguration();

            // Save current cache to YAML
            int count = 0;
            for (Claim claim : getAllClaims()) {  // Changed this line - now directly iterating over claims
                backup.set("claims." + count + ".owner", claim.getOwner().toString());
                backup.set("claims." + count + ".world", claim.getWorld());
                backup.set("claims." + count + ".corner1.x", claim.getCorner1().getBlockX());
                backup.set("claims." + count + ".corner1.z", claim.getCorner1().getBlockZ());
                backup.set("claims." + count + ".corner2.x", claim.getCorner2().getBlockX());
                backup.set("claims." + count + ".corner2.z", claim.getCorner2().getBlockZ());
                backup.set("claims." + count + ".isAdminClaim", claim.isAdminClaim());

                // Save trusted players
                int trustCount = 0;
                for (Map.Entry<UUID, TrustLevel> entry : claim.getTrustedPlayers().entrySet()) {
                    backup.set("claims." + count + ".trusted." + trustCount + ".uuid", entry.getKey().toString());
                    backup.set("claims." + count + ".trusted." + trustCount + ".level", entry.getValue().name());
                    trustCount++;
                }

                // Save flags
                for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
                    backup.set("claims." + count + ".flags." + entry.getKey().name(), entry.getValue());
                }

                count++;
            }

            backup.save(backupFile);
            plugin.getLogger().info("Saved " + count + " claims to backup file");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to backup claim cache: " + e.getMessage());
        }
    }



    private void debug(String message) {
        plugin.debug("[ClassName] " + message);
    }

    private int calculateRequiredBlocks(Location corner1, Location corner2) {
        // Calculate area between corners
        int length = Math.abs(corner1.getBlockX() - corner2.getBlockX()) + 1;
        int width = Math.abs(corner1.getBlockZ() - corner2.getBlockZ()) + 1;
        return length * width;
    }




    public Claim getClaimAt(Location location) {
        Set<Claim> worldClaimSet = worldClaims.get(location.getWorld().getName());
        if (worldClaimSet == null) return null;

        return worldClaimSet.stream()
                .filter(claim -> claim.contains(location))
                .findFirst()
                .orElse(null);
    }





    public void clearClaims() {
        playerClaims.clear();
        worldClaims.clear();
        loaded = false;
    }

    public int getPlayerAvailableBlocks(UUID playerUUID) {
        return plugin.getBlockAccumulator().getBlocks(playerUUID);
    }

    private boolean overlapsWithExistingClaim(Location corner1, Location corner2) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        Set<Claim> worldClaimSet = worldClaims.get(corner1.getWorld().getName());
        if (worldClaimSet == null) return false;

        for (Claim otherClaim : worldClaimSet) {
            int otherMinX = Math.min(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
            int otherMaxX = Math.max(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
            int otherMinZ = Math.min(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());
            int otherMaxZ = Math.max(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());

            // Check for overlap
            if (!(maxX < otherMinX || minX > otherMaxX || maxZ < otherMinZ || minZ > otherMaxZ)) {
                return true; // Claims overlap
            }
        }

        return false;
    }

    public Set<Claim> getAllClaims() {
        Set<Claim> allClaims = new HashSet<>();
        for (Set<Claim> claims : worldClaims.values()) {
            allClaims.addAll(claims);
        }
        return allClaims;
    }
}
