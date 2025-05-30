// SelectionManager.java
package org.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private final Main plugin;
    private final Map<UUID, Selection> selections;

    public SelectionManager(Main plugin) {
        this.plugin = plugin;
        this.selections = new HashMap<>();
    }

    public void handleFirstPoint(Player player, Location location) {
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), k -> new Selection());
        selection.setFirstPoint(location);
        selection.setAdminClaim(false);

        player.sendMessage("§6[LandClaims] §fFirst corner selected. Right-click to select the second corner.");
        showSelectionPoint(player, location);
    }

    public void handleSecondPoint(Player player, Location location) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.getFirstPoint() == null) {
            player.sendMessage("§c[LandClaims] Please select the first corner first (left-click).");
            return;
        }

        selection.setSecondPoint(location);

        // Calculate required blocks
        int requiredBlocks = calculateRequiredBlocks(selection);
        int availableBlocks = plugin.getClaimManager().getPlayerAvailableBlocks(player.getUniqueId());

        // Debug logging
        plugin.getLogger().info("[Debug] Player " + player.getName() + " attempting to create claim:");
        plugin.getLogger().info("[Debug] Required blocks: " + requiredBlocks);
        plugin.getLogger().info("[Debug] Available blocks: " + availableBlocks);

        if (requiredBlocks > availableBlocks) {
            player.sendMessage("§c[LandClaims] You need " + requiredBlocks + " blocks to create this claim, but you only have " + availableBlocks + ".");
            clearSelection(player);
            return;
        }

        // Check for overlap
        if (plugin.getClaimManager().wouldOverlap(selection.getFirstPoint(), selection.getSecondPoint(), null)) {
            player.sendMessage("§c[LandClaims] This claim would overlap with an existing claim.");
            clearSelection(player);
            return;
        }

        // Create the claim
        Claim claim = new Claim(player.getUniqueId(), selection.getFirstPoint(), selection.getSecondPoint());

        // Debug logging before save
        plugin.getLogger().info("[Debug] Creating new claim for " + player.getName() +
                " at " + claim.getCorner1().getBlockX() + "," + claim.getCorner1().getBlockZ());
        plugin.getLogger().info("[Debug] Current cache size: " +
                plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).size());

        // Save to database first
        try {
            plugin.getDatabaseManager().saveClaim(claim);
            plugin.getLogger().info("[Debug] Claim saved to database successfully");
        } catch (Exception e) {
            plugin.getLogger().severe("[Debug] Failed to save claim to database: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c[LandClaims] Error saving claim. Please contact an administrator.");
            clearSelection(player);
            return;
        }

        // Then add to cache
        plugin.getClaimManager().addClaim(claim);

        // Debug logging after save
        plugin.getLogger().info("[Debug] Cache size after adding: " +
                plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).size());

        // Remove blocks from player's balance
        plugin.getBlockAccumulator().removeBlocks(player.getUniqueId(), requiredBlocks);

        // Show visualization
        plugin.getClaimVisualizer().showClaim(player, claim);

        // Verify claim was added correctly
        if (plugin.getClaimManager().getClaimAt(claim.getCorner1()) == null) {
            plugin.getLogger().severe("[Debug] Claim was not properly added to cache! Cannot find claim at location.");
        }

        // Debug cache state
        plugin.getLogger().info("=== Cache State After Claim Creation ===");
        plugin.getLogger().info("Total claims for " + player.getName() + ": " +
                plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).size());
        plugin.getLogger().info("Claim findable at location: " +
                (plugin.getClaimManager().getClaimAt(claim.getCorner1()) != null));

        player.sendMessage("§a[LandClaims] Claim created successfully! Size: " + requiredBlocks + " blocks");
        clearSelection(player);

        // Force a cache refresh
        plugin.getClaimManager().refreshCache();
    }


    public void handleAdminFirstPoint(Player admin, Location location) {
        if (!admin.hasPermission("landclaims.admin")) {
            admin.sendMessage("§c[LandClaims] You don't have permission to use admin tools.");
            return;
        }

        // Get or create selection using the existing map
        Selection selection = selections.computeIfAbsent(admin.getUniqueId(), k -> new Selection());
        selection.setFirstPoint(location);
        selection.setAdminClaim(true);

        // Show special visualization for admin claims
        Material adminCornerMaterial = Material.valueOf(
                plugin.getConfig().getString("visualization.admin-corner-block", "DIAMOND_BLOCK")
        );
        admin.sendBlockChange(location, adminCornerMaterial.createBlockData());

        admin.sendMessage("§6[LandClaims] First corner of admin claim set at " +
                location.getBlockX() + ", " + location.getBlockZ());
    }

    public void handleAdminSecondPoint(Player player, Location location) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.getFirstPoint() == null) {
            player.sendMessage("§c[LandClaims] Please select the first corner first (left-click).");
            return;
        }

        selection.setSecondPoint(location);

        // Check for overlap (optional for admin claims)
        if (plugin.getClaimManager().wouldOverlap(selection.getFirstPoint(), selection.getSecondPoint(), null)) {
            player.sendMessage("§e[LandClaims] Warning: This admin claim overlaps with existing claims.");
        }

        // Create admin claim
        Claim claim = new Claim(UUID.fromString("00000000-0000-0000-0000-000000000000"), // Admin UUID
                selection.getFirstPoint(), selection.getSecondPoint());
        claim.setAdminClaim(true);

        plugin.getClaimManager().addClaim(claim);
        plugin.getDataManager().saveClaim(claim);

        //Runs /lc show by itself after a claim has been created
        plugin.getClaimVisualizer().showClaim(player, claim);

        player.sendMessage("§a[LandClaims] Admin claim created successfully!");
        clearSelection(player);
    }

    private int calculateRequiredBlocks(Selection selection) {
        Location first = selection.getFirstPoint();
        Location second = selection.getSecondPoint();
        int length = Math.abs(first.getBlockX() - second.getBlockX()) + 1;
        int width = Math.abs(first.getBlockZ() - second.getBlockZ()) + 1;
        return length * width;
    }

    public void clearSelection(Player player) {
        Selection selection = selections.remove(player.getUniqueId());
        if (selection != null) {
            if (selection.getFirstPoint() != null) {
                resetBlock(player, selection.getFirstPoint());
            }
            if (selection.getSecondPoint() != null) {
                resetBlock(player, selection.getSecondPoint());
            }
        }
    }

    private void showSelectionPoint(Player player, Location location) {
        Material visualMaterial = Material.valueOf(plugin.getConfig().getString("visualization.corner-block", "GOLD_BLOCK"));
        player.sendBlockChange(location, visualMaterial.createBlockData());

        // Reset the block after 30 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                resetBlock(player, location);
            }
        }.runTaskLater(plugin, 20 * 30);
    }

    private void resetBlock(Player player, Location location) {
        player.sendBlockChange(location, location.getBlock().getBlockData());
    }

    private static class Selection {
        private Location firstPoint;
        private Location secondPoint;
        private boolean isAdminClaim;

        public Location getFirstPoint() {
            return firstPoint;
        }

        public void setFirstPoint(Location firstPoint) {
            this.firstPoint = firstPoint;
        }

        public Location getSecondPoint() {
            return secondPoint;
        }

        public void setSecondPoint(Location secondPoint) {
            this.secondPoint = secondPoint;
        }

        public boolean isAdminClaim() {
            return isAdminClaim;
        }

        public void setAdminClaim(boolean adminClaim) {
            this.isAdminClaim = adminClaim;
        }
    }

}
