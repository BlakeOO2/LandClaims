// ClaimModifier.java
package org.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimModifier {
    private final Main plugin;
    private final Map<UUID, ModificationSession> activeSessions;

    public ClaimModifier(Main plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
    }

    private Location getNearestCorner(Location playerLoc, Claim claim) {
        Location corner1 = claim.getCorner1();
        Location corner2 = claim.getCorner2();

        double dist1 = playerLoc.distanceSquared(corner1);
        double dist2 = playerLoc.distanceSquared(corner2);

        return dist1 < dist2 ? corner1 : corner2;
    }

    public void startModification(Player player, Claim claim) {
        // Show all corners and let player select which one to modify
        showAllCorners(player, claim);
        player.sendMessage("§6[LandClaims] §fShift-click the corner you want to modify.");
    }

    // In ClaimModifier.java
    public void startModification(Player player, Location cornerLoc) {
        Claim claim = plugin.getClaimManager().getClaimAt(cornerLoc);
        if (claim == null) return;

        // Check if this is actually a corner
        if (!isCornerLocation(cornerLoc, claim)) {
            player.sendMessage("§c[LandClaims] You must click on a corner block to modify the claim.");
            return;
        }

        ModificationSession session = new ModificationSession(claim);
        session.setSelectedCorner(cornerLoc);
        activeSessions.put(player.getUniqueId(), session);

        // Show diamond block for selected corner and gold blocks for other corners
        showAllCorners(player, claim);
        Material cornerMaterial = Material.valueOf(plugin.getConfig().getString("visualization.modification-corner-block", "DIAMOND_BLOCK"));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeSessions.containsKey(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                // Keep selected corner as diamond
                player.sendBlockChange(cornerLoc, cornerMaterial.createBlockData());
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second

        player.sendMessage("§6[LandClaims] §fCorner selected. §eLeft-click§f a new location with your golden shovel to move this corner.");
    }

    private void showAllCorners(Player player, Claim claim) {
        Material normalCornerMaterial = Material.valueOf(plugin.getConfig().getString("visualization.corner-block", "GOLD_BLOCK"));

        for (Location corner : claim.getAllCorners()) {
            player.sendBlockChange(corner, normalCornerMaterial.createBlockData());
        }
    }


    public ModificationSession getActiveSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    // In ClaimModifier.java, modify handleClick:
    public void handleClick(Player player, Location clickedLoc) {
        ModificationSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        Claim claim = session.getClaim();
        Location oldCorner = session.getSelectedCorner();

        if (!isValidModification(player, claim, oldCorner, clickedLoc)) {
            return;
        }

        // Store old corners for database update
        Location oldCorner1 = claim.getCorner1().clone();
        Location oldCorner2 = claim.getCorner2().clone();

        // Update claim corners
        claim.setCorner(oldCorner.equals(claim.getCorner1()), clickedLoc);

        // Update in database
        plugin.getDatabaseManager().updateClaimLocation(claim, oldCorner1, oldCorner2);

        // Show updated visualization
        plugin.getClaimVisualizer().showClaim(player, claim);
        player.sendMessage("§a[LandClaims] Claim modified successfully!");
        endModification(player);
    }

    private boolean isValidModification(Player player, Claim claim, Location oldCorner, Location newLocation) {
        // Check if locations are in the same world
        if (!oldCorner.getWorld().equals(newLocation.getWorld())) {
            player.sendMessage("§c[LandClaims] You cannot modify a claim across different worlds.");
            return false;
        }

        // Get the opposite corner that won't be moved
        Location oppositeCorner = getOppositeCorner(claim, oldCorner);

        // Calculate new claim size
        int newSize = calculateSize(newLocation, oppositeCorner);
        int minSize = plugin.getConfig().getInt("claiming.minimum-size", 100);

        // Check minimum size
        if (newSize < minSize) {
            player.sendMessage("§c[LandClaims] Claims must be at least " + minSize + " blocks in size.");
            return false;
        }

        // Calculate block difference
        int oldSize = claim.getSize();
        int blockDifference = newSize - oldSize;

        // Check if player has enough blocks for expansion
        if (blockDifference > 0) {
            int availableBlocks = plugin.getClaimManager().getPlayerAvailableBlocks(player.getUniqueId());
            if (availableBlocks < blockDifference) {
                player.sendMessage("§c[LandClaims] You need " + blockDifference +
                        " more blocks to expand this claim. You only have " + availableBlocks + " available.");
                return false;
            }
        }

        // Check for overlap with other claims
        if (wouldOverlapWithOtherClaims(newLocation, oppositeCorner, claim)) {
            player.sendMessage("§c[LandClaims] This modification would overlap with another claim.");
            return false;
        }

        // Check maximum dimensions if configured
        int maxClaimSize = plugin.getConfig().getInt("claiming.max-claim-size", 10000);
        if (newSize > maxClaimSize) {
            player.sendMessage("§c[LandClaims] Claims cannot be larger than " + maxClaimSize + " blocks.");
            return false;
        }

        return true;
    }

    private Location getOppositeCorner(Claim claim, Location corner) {
        if (corner.equals(claim.getCorner1())) {
            return claim.getCorner2();
        }
        return claim.getCorner1();
    }

    private boolean wouldOverlapWithOtherClaims(Location corner1, Location corner2, Claim excludeClaim) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        // Check all claims in the world
        for (Claim otherClaim : plugin.getClaimManager().getAllClaims()) {
            // Skip the claim being modified
            if (otherClaim.equals(excludeClaim)) continue;

            // Get other claim boundaries
            int otherMinX = Math.min(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
            int otherMaxX = Math.max(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
            int otherMinZ = Math.min(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());
            int otherMaxZ = Math.max(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());

            // Check for overlap
            if (!(maxX < otherMinX || minX > otherMaxX || maxZ < otherMinZ || minZ > otherMaxZ)) {
                return true; // Overlap found
            }
        }
        return false;
    }

    private void handleBlockBalance(Player player, Claim claim, int blockDifference) {
        if (blockDifference > 0) {
            // Remove blocks for expansion
            plugin.getBlockAccumulator().removeBlocks(player.getUniqueId(), blockDifference);
            player.sendMessage("§a[LandClaims] Used " + blockDifference + " blocks to expand the claim.");
        } else if (blockDifference < 0) {
            // Refund blocks for shrinking
            plugin.getBlockAccumulator().addBlocks(player.getUniqueId(), -blockDifference);
            player.sendMessage("§a[LandClaims] Refunded " + (-blockDifference) + " blocks from shrinking the claim.");
        }
    }

    private void applyModification(Player player, Claim claim, Location oldCorner, Location newLocation) {
        // Store old corners for visualization
        Location oldCorner1 = claim.getCorner1().clone();
        Location oldCorner2 = claim.getCorner2().clone();

        // Update the claim
        claim.setCorner(oldCorner.equals(claim.getCorner1()), newLocation);
        plugin.getDataManager().saveClaim(claim);

        // Show the new boundaries
        plugin.getClaimVisualizer().showClaim(player, claim);

        // Reset old corner blocks
        player.sendBlockChange(oldCorner1, oldCorner1.getBlock().getBlockData());
        player.sendBlockChange(oldCorner2, oldCorner2.getBlock().getBlockData());

        player.sendMessage("§a[LandClaims] Claim modified successfully!");

        // End the modification session
        endModification(player);
    }





    private boolean wouldOverlapWith(Location corner1, Location corner2, Claim otherClaim) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        int otherMinX = Math.min(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
        int otherMaxX = Math.max(otherClaim.getCorner1().getBlockX(), otherClaim.getCorner2().getBlockX());
        int otherMinZ = Math.min(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());
        int otherMaxZ = Math.max(otherClaim.getCorner1().getBlockZ(), otherClaim.getCorner2().getBlockZ());

        return !(maxX < otherMinX || minX > otherMaxX || maxZ < otherMinZ || minZ > otherMaxZ);
    }

    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }


    private boolean isCornerLocation(Location location, Claim claim) {
        return claim.getAllCorners().stream()
                .anyMatch(corner ->
                        corner.getBlockX() == location.getBlockX() &&
                                corner.getBlockZ() == location.getBlockZ());
    }

    private int calculateSize(Location corner1, Location corner2) {
        int length = Math.abs(corner1.getBlockX() - corner2.getBlockX()) + 1;
        int width = Math.abs(corner1.getBlockZ() - corner2.getBlockZ()) + 1;
        return length * width;
    }

    public void endModification(Player player) {
        ModificationSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            Claim claim = session.getClaim();
            // Reset all corner blocks
            for (Location corner : claim.getAllCorners()) {
                player.sendBlockChange(corner, corner.getBlock().getBlockData());
            }
        }
    }


    private static class ModificationSession {
        private final Claim claim;
        private Location selectedCorner;

        public ModificationSession(Claim claim) {
            this.claim = claim;
        }

        public Claim getClaim() {
            return claim;
        }

        public Location getSelectedCorner() {
            return selectedCorner;
        }

        public void setSelectedCorner(Location corner) {
            this.selectedCorner = corner;
        }
    }
}