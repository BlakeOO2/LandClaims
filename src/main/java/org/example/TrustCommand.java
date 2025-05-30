// TrustCommand.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class TrustCommand {
    private final Main plugin;

    public TrustCommand(Main plugin) {
        this.plugin = plugin;
    }

    private boolean handleTrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c[LandClaims] Usage: /lc trust <player> [level]");
            player.sendMessage("§c[LandClaims] Trust levels: ACCESS, BUILD, MANAGE");
            return true;
        }

        // Get the claim they're standing in
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§c[LandClaims] You must be standing in your claim to trust players.");
            return true;
        }

        // Check if they own this claim
        if (!claim.getOwner().equals(player.getUniqueId()) &&
                !player.hasPermission("landclaims.admin")) {
            player.sendMessage("§c[LandClaims] You can only modify trust in claims you own.");
            return true;
        }

        // Get the target player
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage("§c[LandClaims] Player not found.");
            return true;
        }

        // Don't allow trusting themselves
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§c[LandClaims] You can't modify your own trust level.");
            return true;
        }

        // Determine trust level
        TrustLevel trustLevel = TrustLevel.BUILD; // Default to BUILD if not specified
        if (args.length >= 3) {
            try {
                trustLevel = TrustLevel.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c[LandClaims] Invalid trust level. Use: ACCESS, BUILD, or MANAGE");
                return true;
            }
        }

        // Set the trust level
        claim.setTrust(target.getUniqueId(), trustLevel);

        // Use the new specific update method
        plugin.getDatabaseManager().updateClaimTrustedPlayers(claim);

        player.sendMessage("§a[LandClaims] Granted " + trustLevel + " access to " + target.getName());

        // Notify the trusted player if they're online
        Player targetPlayer = target.getPlayer();
        if (targetPlayer != null) {
            targetPlayer.sendMessage("§a[LandClaims] You have been granted " + trustLevel +
                    " access to " + player.getName() + "'s claim.");
        }

        return true;
    }

    private boolean handleUntrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c[LandClaims] Usage: /lc untrust <player>");
            return true;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§c[LandClaims] You must be standing in your claim to untrust players.");
            return true;
        }

        if (!claim.getOwner().equals(player.getUniqueId()) &&
                !player.hasPermission("landclaims.admin")) {
            player.sendMessage("§c[LandClaims] You can only modify trust in claims you own.");
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage("§c[LandClaims] Player not found.");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§c[LandClaims] You can't untrust yourself.");
            return true;
        }

        claim.setTrust(target.getUniqueId(), null); // Remove trust

        // Use the new specific update method
        plugin.getDatabaseManager().updateClaimTrustedPlayers(claim);

        player.sendMessage("§a[LandClaims] Removed trust for " + target.getName());

        Player targetPlayer = target.getPlayer();
        if (targetPlayer != null) {
            targetPlayer.sendMessage("§c[LandClaims] Your access to " +
                    player.getName() + "'s claim has been removed.");
        }

        return true;
    }

}
