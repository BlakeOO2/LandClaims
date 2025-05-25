// ClaimInfoCommand.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;

public class ClaimInfoCommand {
    private final Main plugin;

    public ClaimInfoCommand(Main plugin) {
        this.plugin = plugin;
    }

    public void handleInfo(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§c[LandClaims] You are not standing in a claim.");
            return;
        }

        // Get owner info
        OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwner());

        player.sendMessage("§6=== Claim Information ===");
        player.sendMessage("§7Owner: §e" + owner.getName());
        player.sendMessage("§7Size: §e" + claim.getSize() + " blocks");
        player.sendMessage("§7Location: §eFrom " + formatLocation(claim.getCorner1()) +
                " to " + formatLocation(claim.getCorner2()));
        player.sendMessage("§7World: §e" + claim.getWorld());

        // Show trusted players
        player.sendMessage("§6=== Trusted Players ===");
        Map<UUID, TrustLevel> trusted = claim.getTrustedPlayers();
        if (trusted.isEmpty()) {
            player.sendMessage("§7No trusted players");
        } else {
            for (Map.Entry<UUID, TrustLevel> entry : trusted.entrySet()) {
                OfflinePlayer trustedPlayer = Bukkit.getOfflinePlayer(entry.getKey());
                player.sendMessage("§e" + trustedPlayer.getName() + " §7- " + entry.getValue());
            }
        }

        // Show active flags
        player.sendMessage("§6=== Active Flags ===");
        for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
            String status = entry.getValue() ? "§a✔" : "§c✘";
            player.sendMessage(status + " §7" + formatFlagName(entry.getKey()));
        }
    }

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockZ();
    }

    private String formatFlagName(ClaimFlag flag) {
        return flag.name().replace("_", " ").toLowerCase();
    }
}
