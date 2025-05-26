// AdminCommand.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

import java.util.UUID;

public class AdminCommand {
    private final Main plugin;

    public AdminCommand(Main plugin) {
        this.plugin = plugin;
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("landclaims.admin")) {
            sender.sendMessage("§cYou don't have permission to use admin commands.");
            return true;
        }

        if (args.length < 2) {
            if (sender instanceof Player) {
                sendAdminHelp((Player) sender);
            } else {
                sendConsoleAdminHelp(sender);
            }
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "giveblock":
            case "giveblocks":
            case "addblocks":
                return handleGiveBlock(sender, args);
            case "setblock":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleSetBlock((Player) sender, args);
            case "menu":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleAdminMenu((Player) sender);
            case "delete":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleDeleteClaim((Player) sender);
            case "bypass":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleBypass((Player) sender);
            case "reload":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleReload((Player) sender);
            case "seenhere":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleSeenHere((Player) sender);
            case "database":
                return handleDatabaseCommand(sender, args);
            case "unclaimuser":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleUnclaim((Player) sender);
            case "importdata":
                return handleImportData(sender);
            case "unclaimadmin":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                return handleAdminUnclaim((Player) sender);
            // In AdminCommand.java
            case "bluemap":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /lc admin bluemap <reload|toggle|status|enable|disable>");
                    return true;
                }
                switch (args[2].toLowerCase()) {
                    case "enable":
                        plugin.getConfig().set("bluemap.enabled", true);
                        plugin.saveConfig();
                        if (plugin.getBlueMapIntegration() != null) {
                            plugin.getBlueMapIntegration().reload();
                        } else {
                            plugin.setBlueMapIntegration(new BlueMapIntegration(plugin));
                        }
                        sender.sendMessage("§a[LandClaims] BlueMap integration enabled and reloaded.");
                        return true;
                    case "reload":
                        if (plugin.getBlueMapIntegration() != null) {
                            sender.sendMessage("§6[LandClaims] Reloading BlueMap markers...");
                            plugin.getBlueMapIntegration().reload();
                            sender.sendMessage("§a[LandClaims] BlueMap markers reloaded.");
                        } else {
                            sender.sendMessage("§c[LandClaims] BlueMap integration is not available.");
                        }
                        return true;
                    case "toggle":
                        if (plugin.getBlueMapIntegration() != null) {
                            if (plugin.getBlueMapIntegration().isEnabled()) {
                                plugin.getBlueMapIntegration().disable();
                                plugin.getConfig().set("bluemap.enabled", false);
                                sender.sendMessage("§a[LandClaims] BlueMap integration disabled.");
                            } else {
                                plugin.getConfig().set("bluemap.enabled", true);
                                plugin.getBlueMapIntegration().reload();
                                sender.sendMessage("§a[LandClaims] BlueMap integration enabled.");
                            }
                            plugin.saveConfig();
                        } else {
                            sender.sendMessage("§c[LandClaims] BlueMap plugin is not installed.");
                        }
                        return true;
                    case "status":
                        if (plugin.getBlueMapIntegration() != null) {
                            sender.sendMessage("§6=== BlueMap Integration Status ===");
                            sender.sendMessage("§7Enabled: §e" + plugin.getBlueMapIntegration().isEnabled());
                            sender.sendMessage("§7Config Enabled: §e" + plugin.getConfig().getBoolean("bluemap.enabled", true));
                            sender.sendMessage("§7Plugin Found: §e" + (Bukkit.getPluginManager().getPlugin("BlueMap") != null));
                        } else {
                            sender.sendMessage("§c[LandClaims] BlueMap integration is not available.");
                        }
                        return true;
                    default:
                        sender.sendMessage("§cUnknown BlueMap command. Use: reload, toggle, status");
                        return true;
                }

            default:
                if (sender instanceof Player) {
                    sendAdminHelp((Player) sender);
                } else {
                    sendConsoleAdminHelp(sender);
                }
                return true;
        }
    }
    // In AdminCommand.java
    private boolean handleDatabaseCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /lc admin database <import|verify|status|repair>");
            return true;
        }

        switch (args[2].toLowerCase()) {
            case "import":
                sender.sendMessage("§6[LandClaims] Starting YAML to database import...");
                plugin.getDatabaseManager().importFromYaml();
                return true;

            case "repair":
                sender.sendMessage("§6[LandClaims] Attempting database repair...");
                plugin.getDatabaseManager().handleCorruptDatabase();
                return true;

            case "verify":
                verifyDatabase(sender);
                return true;

            case "cleanduplicates":
                if (!sender.hasPermission("landclaims.admin.cleanup")) {
                    sender.sendMessage("§cYou don't have permission to clean up duplicate claims.");
                    return true;
                }
                sender.sendMessage("§6[LandClaims] Starting duplicate claim cleanup...");

                // Run async to prevent server lag
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // Get current claim count
                        int beforeCount = plugin.getDatabaseManager().loadAllClaims().size();

                        // Remove duplicates
                        plugin.getDatabaseManager().removeDuplicateClaims();

                        // Get new claim count
                        int afterCount = plugin.getDatabaseManager().loadAllClaims().size();

                        // Refresh cache
                        plugin.getClaimManager().refreshCache();

                        // Send completion message
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage("§a[LandClaims] Duplicate claim cleanup completed.");
                            sender.sendMessage("§7Claims before: §e" + beforeCount);
                            sender.sendMessage("§7Claims after: §e" + afterCount);
                            sender.sendMessage("§7Removed: §e" + (beforeCount - afterCount) + " duplicate claims");
                        });
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error during duplicate cleanup: " + e.getMessage());
                        e.printStackTrace();
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage("§c[LandClaims] Error during cleanup. Check console for details."));
                    }
                });
                return true;

            case "backup":
                sender.sendMessage("§6[LandClaims] Creating database backup...");
                boolean success = plugin.getDatabaseManager().backupDatabase();
                if (success) {
                    sender.sendMessage("§a[LandClaims] Database backup created successfully!");
                    // Show existing backups
                    sender.sendMessage("§6Existing backups:");
                    for (String backupInfo : plugin.getDatabaseManager().getBackupInfo()) {
                        sender.sendMessage(backupInfo);
                    }
                } else {
                    sender.sendMessage("§c[LandClaims] Failed to create backup. Check console for details.");
                }
                return true;

            case "listbackups":
                sender.sendMessage("§6=== Database Backups ===");
                List<String> backups = plugin.getDatabaseManager().getBackupInfo();
                for (String backup : backups) {
                    sender.sendMessage(backup);
                }
                return true;

            case "status":
                showDatabaseStatus(sender);
                return true;

            default:
                sendDatabaseHelp(sender);
                return true;
        }
    }

    private void sendDatabaseHelp(CommandSender sender) {
        sender.sendMessage("§6=== LandClaims Database Commands ===");
        sender.sendMessage("§f/lc admin database import §7- Import claims from YAML to database");
        sender.sendMessage("§f/lc admin database verify §7- Verify database integrity");
        sender.sendMessage("§f/lc admin database cleanduplicates §7- Remove duplicate claims");
        sender.sendMessage("§f/lc admin database backup §7- Create a database backup");
        sender.sendMessage("§f/lc admin database listbackups §7- List all database backups");
        sender.sendMessage("§f/lc admin database status §7- Show database statistics");
        sender.sendMessage("§f/lc admin database repair §7- Attempt to repair corrupted database");
        sender.sendMessage("§7Note: These commands may take time with large databases");
    }

    private void showDatabaseStatus(CommandSender sender) {
        sender.sendMessage("§6=== Database Status ===");

        // Get claim counts
        List<Claim> dbClaims = plugin.getDatabaseManager().loadAllClaims();
        List<Claim> yamlClaims = plugin.getDataManager().loadAllClaims();

        sender.sendMessage("§7Total Claims in Database: §e" + dbClaims.size());
        sender.sendMessage("§7Total Claims in YAML: §e" + yamlClaims.size());

        // Count admin claims
        long adminClaims = dbClaims.stream().filter(Claim::isAdminClaim).count();
        sender.sendMessage("§7Admin Claims: §e" + adminClaims);

        // Get unique owners
        long uniqueOwners = dbClaims.stream()
                .map(Claim::getOwner)
                .distinct()
                .count();
        sender.sendMessage("§7Unique Claim Owners: §e" + uniqueOwners);

        // Database file info
        File dbFile = new File(plugin.getDataFolder(), "claims.db");
        if (dbFile.exists()) {
            double sizeInMb = dbFile.length() / (1024.0 * 1024.0);
            sender.sendMessage("§7Database Size: §e" + String.format("%.2f MB", sizeInMb));
        }

        // Check database health
        boolean isHealthy = plugin.getDatabaseManager().isDatabaseHealthy();
        sender.sendMessage("§7Database Health: " + (isHealthy ? "§aHealthy" : "§cNeeds Attention"));
    }


    private void verifyDatabase(CommandSender sender) {
        sender.sendMessage("§6Starting database verification...");

        // Compare YAML and database claims
        List<Claim> yamlClaims = plugin.getDataManager().loadAllClaims();
        List<Claim> dbClaims = plugin.getDatabaseManager().loadAllClaims();

        sender.sendMessage("§7YAML claims: " + yamlClaims.size());
        sender.sendMessage("§7Database claims: " + dbClaims.size());

        // Add more detailed verification as needed
    }

    private boolean handleGiveBlock(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("§cUsage: /lc admin giveblock <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[3]);
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive.");
                return true;
            }

            plugin.getBlockAccumulator().addBlocks(target.getUniqueId(), amount);
            sender.sendMessage("§aGave " + amount + " blocks to " + target.getName());
            target.sendMessage("§aYou received " + amount + " claim blocks from an admin.");
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number format.");
            return true;
        }
    }


    private boolean handleSetBlock(Player admin, String[] args) {
        if (args.length != 4) {
            admin.sendMessage("§cUsage: /lc admin setblock <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            admin.sendMessage("§cPlayer not found.");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[3]);
            if (amount < 0) {
                admin.sendMessage("§cAmount cannot be negative.");
                return true;
            }

            plugin.getBlockAccumulator().setBlocks(target.getUniqueId(), amount);
            admin.sendMessage("§aSet " + target.getName() + "'s blocks to " + amount);
            target.sendMessage("§aYour claim blocks have been set to " + amount + " by an admin.");
            return true;

        } catch (NumberFormatException e) {
            admin.sendMessage("§cInvalid number format.");
            return true;
        }
    }
    public boolean handleSetServerName(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /lc admin setservername <name>");
            return true;
        }

        String serverName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getConfig().set("server-name", serverName);
        plugin.saveConfig();
        sender.sendMessage("§aServer name set to: " + serverName);
        return true;
    }

    private boolean handleAdminMenu(Player admin) {
        Claim claim = plugin.getClaimManager().getClaimAt(admin.getLocation());

        if (claim != null) {
            // If in a claim, open claim management
            plugin.getFlagGui().openFlagMenu(admin, claim);
        } else {
            // If not in a claim, open world settings
            plugin.getAdminMenuGui().openAdminMenu(admin);
        }

        return true;
    }

    private boolean handleDeleteClaim(Player admin) {
        Claim claim = plugin.getClaimManager().getClaimAt(admin.getLocation());
        if (claim == null) {
            admin.sendMessage("§c[LandClaims] You must be standing in a claim to delete it.");
            return true;
        }

        // Remove from database first
        plugin.getDatabaseManager().deleteClaim(claim);

        // Then remove from cache
        plugin.getClaimManager().removeClaim(claim);

        admin.sendMessage("§aClaim deleted successfully.");

        // Notify the owner if they're online
        Player owner = Bukkit.getPlayer(claim.getOwner());
        if (owner != null) {
            owner.sendMessage("§cYour claim has been deleted by an admin.");
        }
        return true;
    }

    private boolean handleBypass(Player admin) {
        UUID adminUUID = admin.getUniqueId();
        boolean newState = !plugin.getClaimManager().isAdminBypassing(adminUUID);
        plugin.getClaimManager().setAdminBypass(adminUUID, newState);

        admin.sendMessage(newState ?
                "§aAdmin bypass mode enabled. You can now modify all claims." :
                "§cAdmin bypass mode disabled. Normal claim restrictions apply.");
        return true;
    }

    private boolean handleReload(Player admin) {
        try {
            admin.sendMessage("§6[LandClaims] Starting plugin reload...");

            // Call the main reload method
            plugin.reload();

            admin.sendMessage("§a[LandClaims] Configuration and data reloaded successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            admin.sendMessage("§c[LandClaims] Error reloading configuration. Check console for details.");
            e.printStackTrace();
            return true;
        }
    }

    private void sendAdminHelp(Player admin) {
        admin.sendMessage("§6=== LandClaims Admin Commands ===");
        admin.sendMessage("§f/lc admin giveblock <player> <amount> §7- Give claim blocks");
        admin.sendMessage("§f/lc admin setblock <player> <amount> §7- Set claim blocks");
        admin.sendMessage("§f/lc admin menu §7- Open claim management for current claim");
        admin.sendMessage("§f/lc admin delete §7- Delete the claim you're standing in");
        admin.sendMessage("§f/lc admin unclaimuser §7- Remove a user's claim");
        admin.sendMessage("§f/lc admin unclaimadmin §7- Remove an admin claim");
        admin.sendMessage("§f/lc admin bypass §7- Toggle admin bypass mode");
        admin.sendMessage("§f/lc admin reload §7- Reload configuration");
        admin.sendMessage("§f/lc admin database §7- Database management commands");
        admin.sendMessage("§f/lc admin bluemap reload §7- Reload BlueMap markers");
        admin.sendMessage("§f/lc admin bluemap toggle §7- Toggle BlueMap integration");
    }




    private void sendConsoleAdminHelp(CommandSender sender) {
        sender.sendMessage("=== LandClaims Admin Commands ===");
        sender.sendMessage("§f/lc admin giveblock <player> <amount> §7- Give claim blocks");
        sender.sendMessage("§f/lc admin setblock <player> <amount> §7- Set claim blocks");
        sender.sendMessage("§f/lc admin menu §7- Open claim management for current claim");
        sender.sendMessage("§f/lc admin delete §7- Delete the claim you're standing in");
        sender.sendMessage("§f/lc admin unclaimuser §7- Remove a useres claim");
        sender.sendMessage("§f/lc admin unclaimadmin §7- Remove an admin claim");
        sender.sendMessage("§f/lc admin bypass §7- Toggle admin bypass mode");
        sender.sendMessage("§f/lc admin reload §7- Reload configuration");
    }

    private boolean handleSeenHere(Player admin) {
        Claim claim = plugin.getClaimManager().getClaimAt(admin.getLocation());
        if (claim == null) {
            admin.sendMessage("§c[LandClaims] You must be standing in a claim to use this command.");
            return true;
        }

        admin.sendMessage("§6=== Claim Activity Information ===");

        // Get owner info
        String ownerName = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        long ownerLastSeen = Bukkit.getOfflinePlayer(claim.getOwner()).getLastPlayed();
        String ownerLastSeenStr = formatLastSeen(ownerLastSeen);

        admin.sendMessage("§eOwner: §f" + ownerName);
        admin.sendMessage("§eLast Seen: §f" + ownerLastSeenStr);

        // Get trusted players info
        admin.sendMessage("§6=== Trusted Players ===");
        for (Map.Entry<UUID, TrustLevel> entry : claim.getTrustedPlayers().entrySet()) {
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            long lastSeen = Bukkit.getOfflinePlayer(entry.getKey()).getLastPlayed();
            String lastSeenStr = formatLastSeen(lastSeen);
            admin.sendMessage("§e" + playerName + " §f(" + entry.getValue() + ")");
            admin.sendMessage("§7Last Seen: " + lastSeenStr);
        }

        return true;
    }

    private boolean handleImportData(CommandSender sender) {
        if (!sender.hasPermission("landclaims.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        sender.sendMessage("§6[LandClaims] Starting data import...");

        // Run import async to prevent server lag
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().importFromYaml();
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§a[LandClaims] Data import completed successfully!"));
            } catch (Exception e) {
                plugin.getLogger().severe("Error during data import: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§c[LandClaims] Error during data import. Check console for details."));
            }
        });

        return true;
    }

    private String formatLastSeen(long lastPlayed) {
        if (lastPlayed == 0) {
            return "Never";
        }

        long now = System.currentTimeMillis();
        long diff = now - lastPlayed;

        // Convert to days
        long days = diff / (1000 * 60 * 60 * 24);

        if (days == 0) {
            return "Today";
        } else if (days == 1) {
            return "Yesterday";
        } else {
            return days + " days ago";
        }
    }

    private boolean handleUnclaim(Player admin) {
        Claim claim = plugin.getClaimManager().getClaimAt(admin.getLocation());
        if (claim == null) {
            admin.sendMessage("§c[LandClaims] You must be standing in a claim to use this command.");
            return true;
        }

        // Get the owner's name for the message
        String ownerName = Bukkit.getOfflinePlayer(claim.getOwner()).getName();

        // Refund blocks to the owner
        int refundAmount = claim.getSize();
        plugin.getBlockAccumulator().addBlocks(claim.getOwner(), refundAmount);

        // Remove the claim
        plugin.getClaimManager().removeClaim(claim);
        plugin.getDataManager().deleteClaim(claim);

        admin.sendMessage("§a[LandClaims] Successfully unclaimed this area.");
        admin.sendMessage("§a[LandClaims] " + refundAmount + " blocks have been refunded to " + ownerName);

        // Notify the owner if they're online
        Player owner = Bukkit.getPlayer(claim.getOwner());
        if (owner != null) {
            owner.sendMessage("§c[LandClaims] Your claim has been unclaimed by an admin.");
            owner.sendMessage("§a[LandClaims] " + refundAmount + " blocks have been refunded to you.");
        }

        return true;
    }
    private void giveAdminClaimingTool(Player admin) {
        ItemStack tool = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§6Admin Claiming Tool");
        meta.setLore(Arrays.asList(
                "§7Left-click: Select first corner",
                "§7Right-click: Select second corner",
                "§7for admin claim",
                "",
                "§cThis is an admin-only tool"
        ));
        // Use LUCK instead of DURABILITY for the glow effect
        meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        tool.setItemMeta(meta);

        // Remove any existing admin claiming tools
        for (ItemStack item : admin.getInventory().getContents()) {
            if (item != null && item.getType() == Material.GOLDEN_AXE &&
                    item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§6Admin Claiming Tool")) {
                item.setAmount(0);
            }
        }

        admin.getInventory().addItem(tool);
        admin.sendMessage("§a[LandClaims] You have received the admin claiming tool.");
        admin.sendMessage("§a[LandClaims] Use left and right clicks to select corners.");
    }
    private boolean handleAdminUnclaim(Player admin) {
        Claim claim = plugin.getClaimManager().getClaimAt(admin.getLocation());
        if (claim == null) {
            admin.sendMessage("§c[LandClaims] You must be standing in a claim to unclaim it.");
            return true;
        }

        if (!claim.isAdminClaim()) {
            admin.sendMessage("§c[LandClaims] This is not an admin claim.");
            return true;
        }

        plugin.getClaimManager().removeClaim(claim);
        plugin.getDataManager().deleteClaim(claim);
        admin.sendMessage("§a[LandClaims] Admin claim successfully removed.");
        return true;
    }

    // In SelectionManager.java, add:

}
