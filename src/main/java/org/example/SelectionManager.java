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

        // Create and save the claim
        Claim claim = new Claim(player.getUniqueId(), selection.getFirstPoint(), selection.getSecondPoint());

        // Save to database first
        plugin.getDatabaseManager().saveClaim(claim);

        // Then add to cache
        plugin.getClaimManager().addClaim(claim);

        // Remove blocks from player's balance
        plugin.getBlockAccumulator().removeBlocks(player.getUniqueId(), requiredBlocks);

        // Show visualization
        plugin.getClaimVisualizer().showClaim(player, claim);

        player.sendMessage("§a[LandClaims] Claim created successfully! Size: " + requiredBlocks + " blocks");
        clearSelection(player);
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
