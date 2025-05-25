// ClaimStatistics.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimStatistics {
    private final Main plugin;

    public ClaimStatistics(Main plugin) {
        this.plugin = plugin;
    }

    public void showStatistics(Player viewer) {
        Map<UUID, PlayerStats> statsMap = new HashMap<>();

        // Collect statistics
        for (Claim claim : plugin.getClaimManager().getAllClaims()) {
            PlayerStats stats = statsMap.computeIfAbsent(claim.getOwner(),
                    k -> new PlayerStats(claim.getOwner()));
            stats.addClaim(claim);
        }

        // Sort by total claimed blocks
        List<PlayerStats> sortedStats = statsMap.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::getTotalBlocks).reversed())
                .collect(Collectors.toList());

        // Display statistics
        viewer.sendMessage("§6=== Land Claims Statistics ===");
        viewer.sendMessage("§7Total Claims: §e" + plugin.getClaimManager().getAllClaims().size());
        viewer.sendMessage("§7Total Claimed Blocks: §e" +
                sortedStats.stream().mapToInt(PlayerStats::getTotalBlocks).sum());
        viewer.sendMessage("");
        viewer.sendMessage("§6=== Top Claimers ===");

        int shown = 0;
        for (PlayerStats stats : sortedStats) {
            if (shown++ >= 10) break; // Show top 10
            OfflinePlayer player = Bukkit.getOfflinePlayer(stats.getPlayerId());
            viewer.sendMessage(String.format("§e%d. §f%s: §7%d claims, %d blocks",
                    shown, player.getName(), stats.getClaimCount(), stats.getTotalBlocks()));
        }

        // Show viewer's stats if not in top 10
        if (!sortedStats.isEmpty()) {
            PlayerStats viewerStats = statsMap.get(viewer.getUniqueId());
            if (viewerStats != null && shown <= 10) {
                int rank = sortedStats.indexOf(viewerStats) + 1;
                viewer.sendMessage("");
                viewer.sendMessage("§7Your Rank: §e#" + rank);
                viewer.sendMessage(String.format("§7Your Stats: §e%d claims, %d blocks",
                        viewerStats.getClaimCount(), viewerStats.getTotalBlocks()));
            }
        }
    }

    private static class PlayerStats {
        private final UUID playerId;
        private int claimCount;
        private int totalBlocks;

        public PlayerStats(UUID playerId) {
            this.playerId = playerId;
            this.claimCount = 0;
            this.totalBlocks = 0;
        }

        public void addClaim(Claim claim) {
            claimCount++;
            totalBlocks += claim.getSize();
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getClaimCount() {
            return claimCount;
        }

        public int getTotalBlocks() {
            return totalBlocks;
        }
    }
}
