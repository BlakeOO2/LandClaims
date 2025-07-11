package org.example;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LandClaimsCommand implements CommandExecutor {
    private final Main plugin;
    private final Map<UUID, Long> trapCooldowns = new HashMap<>();
    private static final long TRAP_COOLDOWN_SECONDS = 300; // 5 minutes

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
                // In LandClaimsCommand.java, in the menu case:
                case "menu":
                    plugin.getClaimManager().debugCache(); // Add this line
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
                    return handleTrust(player, args);
                case "untrust":
                    return handleUntrust(player, args);
                case "show":
                    plugin.getLogger().info("Show command received from " + player.getName());
                    if (args.length > 1 && args[1].equalsIgnoreCase("nearby")) {
                        plugin.getLogger().info("Showing nearby claims for " + player.getName());
                        plugin.getClaimVisualizer().showNearbyClaimBoundaries(player);
                    } else {
                        plugin.getLogger().info("Checking claim at location: " + player.getLocation());
                        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
                        if (claim == null) {
                            plugin.getLogger().info("No claim found at location for " + player.getName());
                            player.sendMessage("§c[LandClaims] You are not standing in a claim.");
                            return true;
                        }
                        plugin.getLogger().info("Found claim at location. Corners: " +
                                claim.getCorner1().getBlockX() + "," + claim.getCorner1().getBlockZ() + " to " +
                                claim.getCorner2().getBlockX() + "," + claim.getCorner2().getBlockZ());
                        plugin.getClaimVisualizer().showClaim(player, claim);
                        player.sendMessage("§a[LandClaims] Showing claim boundaries for " +
                                plugin.getConfig().getInt("visualization.duration") + " seconds.");
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
                case "trapped":
                case "trap":
                    handleTrappedCommand(player);
                    return true;
                case "notifications":
                case "notify":
                    toggleNotifications(player);
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

    private void handleTrappedCommand(Player player) {
        // Check if player is in a claim
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§c[LandClaims] You are not inside a claim.");
            return;
        }

        // Check if player is in their own claim
        if (claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c[LandClaims] You cannot use this command in your own claim.");
            return;
        }

        // Check cooldown (optional)
        if (isOnCooldown(player)) {
            player.sendMessage("§c[LandClaims] You must wait before using this command again.");
            return;
        }

        // Find safe location outside the claim
        Location safeLoc = findSafeLocationOutsideClaim(player.getLocation(), claim);
        if (safeLoc == null) {
            player.sendMessage("§c[LandClaims] Could not find a safe location outside the claim. Please try again or contact an admin.");
            return;
        }

        // Teleport player
        player.teleport(safeLoc);
        player.sendMessage("§a[LandClaims] You have been teleported outside the claim.");

        // Set cooldown (optional)
        setCooldown(player);
    }

    private boolean isOnCooldown(Player player) {
        if (!trapCooldowns.containsKey(player.getUniqueId())) {
            return false;
        }

        long lastUsed = trapCooldowns.get(player.getUniqueId());
        long cooldownTime = TRAP_COOLDOWN_SECONDS * 1000; // Convert to milliseconds
        long timeRemaining = (lastUsed + cooldownTime) - System.currentTimeMillis();

        if (timeRemaining <= 0) {
            return false;
        } else {
            long secondsRemaining = timeRemaining / 1000;
            player.sendMessage("§c[LandClaims] You must wait " + secondsRemaining + " seconds before using this command again.");
            return true;
        }
    }

    private void setCooldown(Player player) {
        trapCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private Location findSafeLocationOutsideClaim(Location playerLoc, Claim claim) {
        World world = playerLoc.getWorld();

        // Get claim boundaries
        int minX = Math.min(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int maxX = Math.max(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int minZ = Math.min(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());
        int maxZ = Math.max(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());

        // Player's current position
        int playerX = playerLoc.getBlockX();
        int playerZ = playerLoc.getBlockZ();

        // Determine which edge is closest
        int distToWest = playerX - minX;
        int distToEast = maxX - playerX;
        int distToNorth = playerZ - minZ;
        int distToSouth = maxZ - playerZ;

        // Find the minimum distance
        int minDist = Math.min(Math.min(distToWest, distToEast), Math.min(distToNorth, distToSouth));

        // Try the closest edge first
        Location safeLoc = null;
        if (minDist == distToWest) {
            safeLoc = findSafeY(world, minX - 3, playerZ); // 3 blocks outside west edge
        } else if (minDist == distToEast) {
            safeLoc = findSafeY(world, maxX + 3, playerZ); // 3 blocks outside east edge
        } else if (minDist == distToNorth) {
            safeLoc = findSafeY(world, playerX, minZ - 3); // 3 blocks outside north edge
        } else {
            safeLoc = findSafeY(world, playerX, maxZ + 3); // 3 blocks outside south edge
        }

        // If we couldn't find a safe location at the closest edge, try other edges
        if (safeLoc == null) {
            if (minDist != distToWest) safeLoc = findSafeY(world, minX - 3, playerZ);
            if (safeLoc == null && minDist != distToEast) safeLoc = findSafeY(world, maxX + 3, playerZ);
            if (safeLoc == null && minDist != distToNorth) safeLoc = findSafeY(world, playerX, minZ - 3);
            if (safeLoc == null && minDist != distToSouth) safeLoc = findSafeY(world, playerX, maxZ + 3);
        }

        // If still no safe location, try corners
        if (safeLoc == null) {
            safeLoc = findSafeY(world, minX - 3, minZ - 3); // Northwest corner
            if (safeLoc == null) safeLoc = findSafeY(world, maxX + 3, minZ - 3); // Northeast corner
            if (safeLoc == null) safeLoc = findSafeY(world, minX - 3, maxZ + 3); // Southwest corner
            if (safeLoc == null) safeLoc = findSafeY(world, maxX + 3, maxZ + 3); // Southeast corner
        }

        return safeLoc;
    }

    private Location findSafeY(World world, int x, int z) {
        // Start from the player's Y level and work down
        int startY = Math.min(world.getMaxHeight() - 10, Math.max(world.getMinHeight() + 10, 100));

        // Check from startY down to bedrock
        for (int y = startY; y > world.getMinHeight() + 1; y--) {
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            Location below = loc.clone().subtract(0, 1, 0);
            Location above = loc.clone().add(0, 1, 0);

            // Check if we have a solid block below and air above
            if (below.getBlock().getType().isSolid() &&
                    !isHazardous(below.getBlock().getType()) &&
                    loc.getBlock().getType().isAir() &&
                    above.getBlock().getType().isAir()) {

                // Make sure this location is actually outside the claim
                if (plugin.getClaimManager().getClaimAt(loc) == null) {
                    return loc;
                }
            }
        }

        return null;
    }

    private boolean isHazardous(Material material) {
        return material.name().contains("LAVA") ||
                material.name().contains("FIRE") ||
                material.name().contains("CACTUS") ||
                material.name().contains("MAGMA") ||
                material.name().contains("POWDER_SNOW") ||
                material.name().contains("CAMPFIRE") ||
                material.name().contains("SWEET_BERRY_BUSH");
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
            // Set player's flight to false but don't disable flight permission state immediately
            // This prevents fall damage when toggling flight off
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
        player.sendMessage("§f/lc notifications §7- Toggle claim entry/exit messages");
        player.sendMessage("§f/lc stats §7- View claim statistics");
        player.sendMessage("§f/lc trapped §7- Teleport out if you're stuck in someone's claim");
        //player.sendMessage("§f/lc modify §7- Modify your claim's boundaries");

        if (player.hasPermission("landclaims.admin")) {
            player.sendMessage("§f/lc admin §7- View admin commands");
        }
    }

    private void toggleNotifications(Player player) {
        PlayerPreferences prefs = plugin.getPlayerPreferences(player.getUniqueId());
        prefs.toggleNotifications();

        if (prefs.isNotificationsEnabled()) {
            player.sendMessage("§a[LandClaims] Claim notifications enabled.");
        } else {
            player.sendMessage("§c[LandClaims] Claim notifications disabled.");
        }

        // Save preferences
        plugin.savePlayerPreferences();
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

                        handleFlightUnclaim(player);
                        player.sendMessage("§a[LandClaims] Successfully unclaimed this area.");
                        player.sendMessage("§a[LandClaims] " + refundAmount + " blocks have been refunded to you.");
                    } else {
                        player.sendMessage("§c[LandClaims] Unclaim cancelled.");
                    }
                });
    }

    private void handleFlightUnclaim(Player player) {
        if (plugin.getFlightState(player.getUniqueId())) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage("§c[LandClaims] Flight mode disabled.");
        }
    }
}
