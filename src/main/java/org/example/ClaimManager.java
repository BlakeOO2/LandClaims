package org.example;
// ClaimManager.java

import org.bukkit.Bukkit;
import org.bukkit.Location;
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


    public void loadClaims() {
        if (loaded) {
            plugin.getLogger().warning("Attempted to load claims when already loaded!");
            return;
        }

        plugin.getLogger().info("Loading claims...");

        // Clear existing cache
        playerClaims.clear();
        worldClaims.clear();

        // Try loading from database first
        List<Claim> claims = plugin.getDatabaseManager().loadAllClaims();

        // If database is empty, try loading from data.yml
        if (claims.isEmpty()) {
            plugin.getLogger().info("No claims found in database, checking data.yml...");
            claims = plugin.getDataManager().loadAllClaims();

            // If claims were found in data.yml, save them to database
            if (!claims.isEmpty()) {
                plugin.getLogger().info("Found claims in data.yml, migrating to database...");
                for (Claim claim : claims) {
                    plugin.getDatabaseManager().saveClaim(claim);
                }
            }
        }

        // Add claims to cache
        int count = 0;
        for (Claim claim : claims) {
            addClaimToCache(claim);
            count++;
        }

        loaded = true;
        plugin.getLogger().info("Loaded " + count + " claims");
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
    public void refreshCache() {
        playerClaims.clear();
        worldClaims.clear();

        List<Claim> claims = plugin.getDatabaseManager().loadAllClaims();
        for (Claim claim : claims) {
            addClaimToCache(claim);
        }
    }

    // Add periodic cache refresh
    private void startCacheRefreshTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            refreshCache();
        }, 6000L, 6000L); // Refresh every 5 minutes
    }

    public void addClaim(Claim claim) {
        // Save to database first
        plugin.getDatabaseManager().saveClaim(claim);

        // Add to cache
        addClaimToCache(claim);

        plugin.getLogger().info("Added claim for " + claim.getOwner() + " at " +
                claim.getCorner1().getBlockX() + "," + claim.getCorner1().getBlockZ());


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
