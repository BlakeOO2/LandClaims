package org.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LandClaimsCommand implements CommandExecutor {
    private final Main plugin;

    public LandClaimsCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getLogger().info("Command received: /" + label + " " + String.join(" ", args));

        if (!plugin.isInitialized()) {
            sender.sendMessage("§c[LandClaims] Plugin is recovering from an error. Please wait...");
            plugin.attemptRecovery();
            return true;
        }

        try {
            // Handle admin commands first since they can be run from console
            if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
                if (!sender.hasPermission("landclaims.admin")) {
                    sender.sendMessage("§cYou don't have permission to use admin commands.");
                    return true;
                }
                return plugin.getAdminCommand().handleAdminCommand(sender, args);
            }

            // For all other commands, require player
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            plugin.getLogger().info("Processing command for player: " + player.getName());

            if (args.length == 0) {
                showHelp(player);
                return true;
            }

            plugin.getLogger().info("Processing command for player: " + player.getName());



            if (args.length == 0) {
                showHelp(player);
                return true;
            }
            plugin.getLogger().info("Executing command: " + args[0]);

            switch (args[0].toLowerCase()) {
                case "menu":
                    if (plugin.getMainClaimGui() != null) {
                        plugin.getMainClaimGui().openMainMenu(player);
                    } else {
                        player.sendMessage("§c[LandClaims] Menu system is not available right now.");
                        plugin.getLogger().severe("MainClaimGUI is null!");
                    }
                    return true;
                case "admin":
                    if (!player.hasPermission("landclaims.admin")) {
                        player.sendMessage("§cYou don't have permission to use admin commands.");
                        return true;
                    }
                    return plugin.getAdminCommand().handleAdminCommand(player, args);
                case "trust":
                    return plugin.getTrustCommand().handleTrust(player, args);
                case "untrust":
                    return plugin.getTrustCommand().handleUntrust(player, args);
                case "show":
                    if (args.length > 1 && args[1].equalsIgnoreCase("nearby")) {
                        plugin.getClaimVisualizer().showNearbyClaimBoundaries(player);
                    } else {
                        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
                        if (claim == null) {
                            player.sendMessage("§c[LandClaims] You are not standing in a claim.");
                            return true;
                        }
                        plugin.getClaimVisualizer().showClaim(player, claim);
                    }
                    return true;
                case "flight":
                    if (!player.hasPermission("landclaims.flight")) {
                        player.sendMessage("§c[LandClaims] You don't have permission to use flight.");
                        return true;
                    }
                    handleFlight(player);
                    return true;
                case "world":
                    if (!player.hasPermission("landclaims.admin")) {
                        player.sendMessage("§cYou don't have permission to manage world settings.");
                        return true;
                    }
                    return plugin.getWorldSettingsCommand().handleCommand(sender, args);
                case "unclaim":
                    handleUnclaim(player);
                    return true;
                case "info":
                    plugin.getClaimInfoCommand().handleInfo(player);
                    return true;
                case "transfer":
                    if (args.length < 2) {
                        player.sendMessage("§c[LandClaims] Usage: /lc transfer <player>");
                        return true;
                    }
                    if (args[1].equalsIgnoreCase("accept")) {
                        plugin.getTransferManager().handleTransferResponse(player, true);
                    } else if (args[1].equalsIgnoreCase("deny")) {
                        plugin.getTransferManager().handleTransferResponse(player, false);
                    } else {
                        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
                        if (claim == null) {
                            player.sendMessage("§c[LandClaims] You must be standing in your claim to transfer it.");
                            return true;
                        }
                        plugin.getTransferManager().initiateTransfer(player, claim, args[1]);
                    }
                    return true;
                case "stats":
                    plugin.getClaimStatistics().showStatistics(player);
                    return true;
                case "modify":
                    Claim currentClaim = plugin.getClaimManager().getClaimAt(player.getLocation());
                    if (currentClaim == null) {
                        player.sendMessage("§c[LandClaims] You must be standing in a claim to modify it.");
                        return true;
                    }
                    if (!currentClaim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("landclaims.admin")) {
                        player.sendMessage("§c[LandClaims] You can only modify claims you own.");
                        return true;
                    }
                    plugin.getClaimModifier().startModification(player, currentClaim);
                    return true;
                case "confirm":
                    try {
                        Runnable pendingAction = plugin.getPendingActions().remove(player.getUniqueId());
                        if (pendingAction != null) {
                            pendingAction.run();
                        } else {
                            player.sendMessage("§c[LandClaims] No action to confirm!");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error executing confirm action: " + e.getMessage());
                        e.printStackTrace();
                        player.sendMessage("§c[LandClaims] An error occurred while executing the action.");
                    }
                    return true;
                case "debug":
                    if (player.hasPermission("landclaims.admin")) {
                        player.sendMessage("§6=== Plugin Status ===");
                        player.sendMessage("§7Fully Initialized: §e" + plugin.isInitialized());
                        player.sendMessage("§7MainClaimGUI Ready: §e" + plugin.isComponentReady("mainClaimGui"));
                        player.sendMessage("§7ClaimManager Ready: §e" + plugin.isComponentReady("claimManager"));
                        player.sendMessage("§7ProtectionManager Ready: §e" + plugin.isComponentReady("protectionManager"));
                    }
                    return true;
                case "reload":
                    if (!sender.hasPermission("landclaims.admin.reload")) {
                        sender.sendMessage("§c[LandClaims] You don't have permission to reload the plugin.");
                        return true;
                    }
                    try {
                        plugin.reload();
                        sender.sendMessage("§a[LandClaims] Plugin reloaded successfully!");
                    } catch (Exception e) {
                        sender.sendMessage("§c[LandClaims] Error reloading plugin. Check console for details.");
                        plugin.getLogger().severe("Error during reload: " + e.getMessage());
                        e.printStackTrace();
                    }
                    return true;

                default:
                    showHelp(player);
                    return true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing command: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage("§c[LandClaims] An error occurred while executing the command.");

            return true;
        }
    }

    private void toggleFlight(Player player) {
        boolean newState = !player.getAllowFlight();
        player.setAllowFlight(newState);
        player.setFlying(newState);
        player.sendMessage(newState ?
                "§a[LandClaims] Flight enabled." :
                "§c[LandClaims] Flight disabled.");
    }

    private boolean handleFlight(Player player) {
        if (!player.hasPermission("landclaims.flight")) {
            player.sendMessage("§c[LandClaims] You don't have permission to use flight.");
            return true;
        }

        boolean newState = !plugin.getFlightState(player.getUniqueId());
        plugin.setFlightState(player.getUniqueId(), newState);

        if (newState) {
            // Only enable flight if in a valid claim
            Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
            if (claim != null && plugin.canFlyInClaim(player, claim)) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.sendMessage("§a[LandClaims] Flight mode enabled.");
            } else {
                player.sendMessage("§c[LandClaims] Flight is enabled but only works in claims you own or are trusted in.");
            }
        } else {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage("§c[LandClaims] Flight mode disabled.");
        }

        return true;
    }


    private void showHelp(Player player) {
        player.sendMessage("§6=== LandClaims Help ===");
        player.sendMessage("§f/lc menu §7- Open the main claims menu");
        player.sendMessage("§f/lc trust <player> [level] §7- Trust a player in your claim");
        player.sendMessage("§f/lc untrust <player> §7- Remove trust from a player");
        player.sendMessage("§f/lc show §7- Show claim boundaries");
        player.sendMessage("§f/lc unclaim §7- Unclaim your current claim");
        player.sendMessage("§f/lc info §7- View claim information");
        player.sendMessage("§f/lc transfer <player> §7- Transfer claim ownership");
        player.sendMessage("§f/lc stats §7- View claim statistics");
        player.sendMessage("§f/lc modify §7- Modify your claim's boundaries");
        if (player.hasPermission("landclaims.admin")) {
            player.sendMessage("§f/lc admin §7- View admin commands");
        }
    }

    private void handleUnclaim(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§c[LandClaims] You must be standing in a claim to unclaim it.");
            return;
        }

        if (!claim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("landclaims.admin")) {
            player.sendMessage("§c[LandClaims] You can only unclaim your own claims.");
            return;
        }

        plugin.getChatInputManager().awaitChatInput(player,
                "§c[LandClaims] Type 'confirm' to unclaim this area (or 'cancel' to cancel):",
                input -> {
                    if (input.equalsIgnoreCase("confirm")) {
                        // Get the refund amount before removing the claim
                        int refundAmount = claim.getSize();

                        // Remove from database first
                        plugin.getDatabaseManager().deleteClaim(claim);

                        // Then remove from cache
                        plugin.getClaimManager().removeClaim(claim);

                        // Give the blocks back
                        plugin.getBlockAccumulator().addBlocks(player.getUniqueId(), refundAmount);

                        player.sendMessage("§a[LandClaims] Successfully unclaimed this area.");
                        player.sendMessage("§a[LandClaims] " + refundAmount + " blocks have been refunded to you.");
                    } else {
                        player.sendMessage("§c[LandClaims] Unclaim cancelled.");
                    }
                });
    }
}
