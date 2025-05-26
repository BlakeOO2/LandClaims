// ClaimVisualizer.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ClaimVisualizer {
    private final Main plugin;
    private final Map<UUID, Set<Location>> activeVisualizations;
    private final Material cornerMaterial;
    private final Material borderMaterial;
    private final int spacing;

    public ClaimVisualizer(Main plugin) {
        this.plugin = plugin;
        this.activeVisualizations = new HashMap<>();
        this.cornerMaterial = Material.valueOf(plugin.getConfig().getString("visualization.corner-block", "GOLD_BLOCK"));
        this.borderMaterial = Material.valueOf(plugin.getConfig().getString("visualization.border-block", "REDSTONE_BLOCK"));
        this.spacing = plugin.getConfig().getInt("visualization.spacing", 10);
    }

    public void showClaim(Player player, Claim claim) {
        plugin.getLogger().info("[Debug] Showing claim visualization for " + player.getName());

        // Clear any existing visualization
        hideVisualization(player);

        Set<Location> visualBlocks = new HashSet<>();
        Location corner1 = claim.getCorner1();
        Location corner2 = claim.getCorner2();

        if (corner1 == null || corner2 == null) {
            plugin.getLogger().warning("[Debug] Invalid claim corners for player " + player.getName());
            return;
        }

        if (corner1.getWorld() == null || corner2.getWorld() == null) {
            plugin.getLogger().warning("[Debug] Invalid world in claim corners for player " + player.getName());
            return;
        }

        plugin.getLogger().info("[Debug] Claim corners: " +
                corner1.getBlockX() + "," + corner1.getBlockZ() + " to " +
                corner2.getBlockX() + "," + corner2.getBlockZ());

        // Get min and max coordinates
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        // Use player's Y level or highest block
        int y = Math.min(player.getLocation().getBlockY(),
                player.getWorld().getHighestBlockYAt(player.getLocation()));
        plugin.getLogger().info("[Debug] Using Y level: " + y);

        // Show visualization with delays to ensure proper packet handling
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Show corners first
            showCornerBlock(player, new Location(corner1.getWorld(), minX, y, minZ), visualBlocks);
            showCornerBlock(player, new Location(corner1.getWorld(), minX, y, maxZ), visualBlocks);
            showCornerBlock(player, new Location(corner1.getWorld(), maxX, y, minZ), visualBlocks);
            showCornerBlock(player, new Location(corner1.getWorld(), maxX, y, maxZ), visualBlocks);
            plugin.getLogger().info("[Debug] Showed corner blocks");

            // Show borders after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                showBorders(player, corner1.getWorld(), minX, maxX, minZ, maxZ, y, visualBlocks);
                plugin.getLogger().info("[Debug] Showed border blocks");
            }, 5L);
        }, 2L);

        activeVisualizations.put(player.getUniqueId(), visualBlocks);

        // Schedule cleanup
        int duration = plugin.getConfig().getInt("visualization.duration", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            hideVisualization(player);
        }, duration * 20L);
    }


    private String formatLocation(Location loc) {
        if (loc == null) return "null";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }


    private void showCornerBlock(Player player, Location location, Set<Location> visualBlocks) {
        try {
            // Schedule the block change on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Material cornerMaterial = Material.valueOf(plugin.getConfig().getString("visualization.corner-block", "GLOWSTONE"));
                // Store the original block data
                BlockData originalData = location.getBlock().getBlockData();
                // Send the fake block change
                player.sendBlockChange(location, cornerMaterial.createBlockData());
                visualBlocks.add(location.clone());
                plugin.getLogger().info("[Debug] Sent block change at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

                // Schedule a check to ensure the block change was applied
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (visualBlocks.contains(location)) {
                        player.sendBlockChange(location, cornerMaterial.createBlockData());
                    }
                }, 5L);
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[Debug] Error showing corner block: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private void showBorders(Player player, World world, int minX, int maxX, int minZ, int maxZ, int y, Set<Location> visualBlocks) {
        // North and South borders
        for (int x = minX + spacing; x < maxX; x += spacing) {
            showBorderBlock(player, new Location(world, x, y, minZ), visualBlocks);
            showBorderBlock(player, new Location(world, x, y, maxZ), visualBlocks);
        }

        // East and West borders
        for (int z = minZ + spacing; z < maxZ; z += spacing) {
            showBorderBlock(player, new Location(world, minX, y, z), visualBlocks);
            showBorderBlock(player, new Location(world, maxX, y, z), visualBlocks);
        }
    }

    private void showBorderBlock(Player player, Location location, Set<Location> visualBlocks) {
        player.sendBlockChange(location, borderMaterial.createBlockData());
        visualBlocks.add(location);
    }

    public void hideVisualization(Player player) {
        Set<Location> blocks = activeVisualizations.remove(player.getUniqueId());
        if (blocks != null) {
            plugin.getLogger().info("[Debug] Cleaning up visualization for " + player.getName() + " (" + blocks.size() + " blocks)");
            for (Location loc : blocks) {
                try {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                } catch (Exception e) {
                    plugin.getLogger().warning("[Debug] Error resetting block at " +
                            loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                }
            }
        }
    }


    public void showNearbyClaimBoundaries(Player player) {
        Location playerLoc = player.getLocation();
        int radius = plugin.getConfig().getInt("visualization.nearby-radius", 50);

        Set<Claim> nearbyClaims = plugin.getClaimManager().getNearbyClaims(playerLoc, radius);
        if (nearbyClaims.isEmpty()) {
            player.sendMessage("§c[LandClaims] No claims found within " + radius + " blocks.");
            return;
        }

        // Show all nearby claims
        Set<Location> visualBlocks = new HashSet<>();
        for (Claim claim : nearbyClaims) {
            visualizeClaim(player, claim, visualBlocks);
        }

        activeVisualizations.put(player.getUniqueId(), visualBlocks);

        // Schedule removal
        int duration = plugin.getConfig().getInt("visualization.duration", 30);
        new BukkitRunnable() {
            @Override
            public void run() {
                hideVisualization(player);
            }
        }.runTaskLater(plugin, 20L * duration);

        player.sendMessage("§a[LandClaims] Showing nearby claim boundaries for " + duration + " seconds.");
        player.sendMessage("§7Found §e" + nearbyClaims.size() + "§7 claims within " + radius + " blocks.");
    }

    private void visualizeClaim(Player player, Claim claim, Set<Location> visualBlocks) {
        Location corner1 = claim.getCorner1();
        Location corner2 = claim.getCorner2();

        // Get min and max coordinates
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        // Use player's Y level for visualization
        int y = Math.min(player.getLocation().getBlockY(),
                Math.max(corner1.getBlockY(), corner2.getBlockY()));

        // Show corners
        showCornerBlock(player, new Location(corner1.getWorld(), minX, y, minZ), visualBlocks);
        showCornerBlock(player, new Location(corner1.getWorld(), minX, y, maxZ), visualBlocks);
        showCornerBlock(player, new Location(corner1.getWorld(), maxX, y, minZ), visualBlocks);
        showCornerBlock(player, new Location(corner1.getWorld(), maxX, y, maxZ), visualBlocks);

        // Show borders
        showBorders(player, corner1.getWorld(), minX, maxX, minZ, maxZ, y, visualBlocks);
    }
}
