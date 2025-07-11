// ClaimTransferManager.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import java.util.*;
import org.bukkit.Location;

public class ClaimTransferManager {
    private final Main plugin;
    private final Map<UUID, TransferRequest> pendingTransfers;

    public ClaimTransferManager(Main plugin) {
        this.plugin = plugin;
        this.pendingTransfers = new HashMap<>();
    }

    public void initiateTransfer(Player sender, Claim claim, String targetName) {
        if (!claim.getOwner().equals(sender.getUniqueId())) {
            sender.sendMessage("§c[LandClaims] You can only transfer claims you own.");
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore()) {
            sender.sendMessage("§c[LandClaims] Player not found.");
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage("§c[LandClaims] You can't transfer a claim to yourself.");
            return;
        }

        // Check if target has enough available blocks
        int targetAvailableBlocks = plugin.getBlockAccumulator().getBlocks(target.getUniqueId());
        if (targetAvailableBlocks < claim.getSize()) {
            sender.sendMessage("§c[LandClaims] The target player doesn't have enough claim blocks available.");
            return;
        }

        // Create transfer request
        TransferRequest request = new TransferRequest(sender.getUniqueId(), target.getUniqueId(), claim);
        pendingTransfers.put(target.getUniqueId(), request);

        // Notify players
        sender.sendMessage("§a[LandClaims] Transfer request sent to " + target.getName());
        if (target.isOnline()) {
            Player targetPlayer = target.getPlayer();
            targetPlayer.sendMessage("§6[LandClaims] " + sender.getName() + " wants to transfer their claim to you.");
            targetPlayer.sendMessage("§6[LandClaims] Location: " + formatLocation(claim.getCorner1()));
            targetPlayer.sendMessage("§6[LandClaims] Size: " + claim.getSize() + " blocks");
            targetPlayer.sendMessage("§6[LandClaims] Type §a/lc transfer accept §6or §c/lc transfer deny");
        }

        // Expire request after 5 minutes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingTransfers.remove(target.getUniqueId(), request)) {
                sender.sendMessage("§c[LandClaims] Transfer request to " + target.getName() + " has expired.");
                if (target.isOnline()) {
                    target.getPlayer().sendMessage("§c[LandClaims] Transfer request from " +
                            sender.getName() + " has expired.");
                }
            }
        }, 20 * 300); // 5 minutes
    }

    public void handleTransferResponse(Player player, boolean accept) {
        TransferRequest request = pendingTransfers.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage("§c[LandClaims] You have no pending transfer requests.");
            return;
        }

        Player sender = Bukkit.getPlayer(request.getSender());
        if (accept) {
            Claim claim = request.getClaim();

            // Update block counts
            plugin.getBlockAccumulator().removeBlocks(player.getUniqueId(), claim.getSize());
            plugin.getBlockAccumulator().addBlocks(request.getSender(), claim.getSize());

            // Update claim ownership in memory
            UUID oldOwner = claim.getOwner();

            // First, remove the claim from cache to prevent duplication
            plugin.getClaimManager().removeClaim(claim);

            // Update owner
            claim.setOwner(player.getUniqueId());

            // Update in database
            plugin.getDatabaseManager().transferClaim(claim, player.getUniqueId());

            // Add the claim back to cache with new owner
            plugin.getClaimManager().addClaim(claim);

            // Notify players
            player.sendMessage("§a[LandClaims] You have accepted the claim transfer.");
            if (sender != null) {
                sender.sendMessage("§a[LandClaims] " + player.getName() + " has accepted your claim transfer.");
            }
        } else {
            // Handle rejection
            player.sendMessage("§c[LandClaims] You have denied the claim transfer.");
            if (sender != null) {
                sender.sendMessage("§c[LandClaims] " + player.getName() + " has denied your claim transfer.");
            }
        }
    }

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockZ();
    }

    private static class TransferRequest {
        private final UUID sender;
        private final UUID recipient;
        private final Claim claim;

        public TransferRequest(UUID sender, UUID recipient, Claim claim) {
            this.sender = sender;
            this.recipient = recipient;
            this.claim = claim;
        }

        public UUID getSender() {
            return sender;
        }

        public UUID getRecipient() {
            return recipient;
        }

        public Claim getClaim() {
            return claim;
        }
    }
}
