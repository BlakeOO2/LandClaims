// MainClaimGUI.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MainClaimGUI {
    private final Main plugin;
    private final Map<UUID, Claim> openMenus;

    public MainClaimGUI(Main plugin) {
        this.plugin = plugin;
        this.openMenus = new HashMap<>();
    }

    public void openMainMenu(Player player) {
        try {
            plugin.getLogger().info("Attempting to open main menu for " + player.getName());

            Inventory inv = Bukkit.createInventory(null, 54, "Land Claims Menu");

            // Player's claim information
            plugin.getLogger().info("Getting claim information for " + player.getName());
            int totalClaims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).size();
            int availableBlocks = plugin.getBlockAccumulator().getBlocks(player.getUniqueId());
            plugin.getLogger().info("Player has " + totalClaims + " claims and " + availableBlocks + " available blocks");

            // Info Item (Player's statistics)
            ItemStack infoItem = createItem(Material.BOOK,
                    "§6Your Claim Information",
                    Arrays.asList(
                            "§7Available Blocks: §e" + availableBlocks,
                            "§7Total Claims: §e" + totalClaims,
                            "",
                            "§7Click to view all your claims"
                    )
            );
            inv.setItem(4, infoItem);

            // Current claim management (if standing in a claim)
            plugin.getLogger().info("Checking current claim for " + player.getName());
            Claim currentClaim = plugin.getClaimManager().getClaimAt(player.getLocation());
            if (currentClaim != null && (currentClaim.getOwner().equals(player.getUniqueId()) ||
                    player.hasPermission("landclaims.admin"))) {

                plugin.getLogger().info("Player is in their claim or has admin permission");

                // Trust Management
                ItemStack trustItem = createItem(Material.PLAYER_HEAD,
                        "§6Trust Management",
                        Arrays.asList(
                                "§7Manage trusted players",
                                "§7Current trusted players: §e" + currentClaim.getTrustedPlayers().size()
                        )
                );
                inv.setItem(19, trustItem);

                // Flag Management
                ItemStack flagItem = createItem(Material.REDSTONE_TORCH,
                        "§6Claim Settings",
                        Arrays.asList(
                                "§7Configure claim settings:",
                                "§7- PvP, Explosions, Fire spread",
                                "§7- Container access, Building",
                                "§7- Monster spawning, and more",
                                "",
                                "§eClick to modify settings"
                        )
                );
                inv.setItem(21, flagItem);

                // Visualization
                ItemStack visualizeItem = createItem(Material.GLASS,
                        "§6Visualize Claim",
                        Arrays.asList(
                                "§7Click to toggle claim visualization",
                                "§7Shows the boundaries of your claim"
                        )
                );
                inv.setItem(23, visualizeItem);

                // Claim Size Info
                ItemStack sizeItem = createItem(Material.MAP,
                        "§6Claim Information",
                        Arrays.asList(
                                "§7Size: §e" + currentClaim.getSize() + " blocks",
                                "§7Location: §e" + formatLocation(currentClaim.getCorner1()),
                                "",
                                "§7Click for more details"
                        )
                );
                inv.setItem(25, sizeItem);
                // Add Modify button
                ItemStack modifyItem = createItem(Material.DIAMOND_PICKAXE,
                        "§6How to Modify Claim",
                        Arrays.asList(
                                "§7To modify your claim:",
                                "§71. Hold a golden shovel",
                                "§72. Shift-click a corner block",
                                "§73. Click new location for corner"
                        )
                );
                inv.setItem(31, modifyItem);

                // Store the current claim for the click handler
                openMenus.put(player.getUniqueId(), currentClaim);

            } else {
                plugin.getLogger().info("Player is not in a manageable claim");
                // Not in a claim or not owner
                ItemStack noClaim = createItem(Material.BARRIER,
                        "§cNo Claim Here",
                        Arrays.asList(
                                "§7You are not standing in",
                                "§7a claim you can manage"
                        )
                );
                inv.setItem(22, noClaim);

                // For admins, add world settings button when not in a claim
                if (player.hasPermission("landclaims.admin")) {
                    ItemStack worldSettingsItem = createItem(Material.COMMAND_BLOCK,
                            "§6World Settings",
                            Arrays.asList(
                                    "§7Configure global settings:",
                                    "§7- Default claim flags",
                                    "§7- World protection options",
                                    "",
                                    "§eClick to modify settings"
                            )
                    );
                    inv.setItem(31, worldSettingsItem);
                }
            }

            // Tool Information
            ItemStack toolInfo = createItem(Material.GOLDEN_SHOVEL,
                    "§6Claiming Tool",
                    Arrays.asList(
                            "§7Use a Golden Shovel to claim land:",
                            "§7- Left-click for first corner",
                            "§7- Right-click for second corner",
                            "",
                            "§7Required blocks: §e" + availableBlocks + " available"
                    )
            );
            inv.setItem(40, toolInfo);

            // Help/Information
            ItemStack helpItem = createItem(Material.PAPER,
                    "§6Help & Information",
                    Arrays.asList(
                            "§7Basic Commands:",
                            "§7/lc trust <player> - Trust a player",
                            "§7/lc untrust <player> - Remove trust",
                            "§7/lc show - Visualize claim",
                            "§7/lc info - Claim information"
                    )
            );
            inv.setItem(45, helpItem);

            plugin.getLogger().info("Opening inventory for " + player.getName());
            player.openInventory(inv);
            plugin.getLogger().info("Successfully opened main menu for " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("Error opening main menu: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c[LandClaims] An error occurred while opening the menu.");
        }
    }


    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockZ();
    }

    public void handleClick(Player player, ItemStack clicked) {
        try {
            if (clicked == null) return;

            plugin.getLogger().info("Player " + player.getName() + " clicked " + clicked.getType());

            Claim currentClaim = openMenus.get(player.getUniqueId());
            if (currentClaim == null) {
                plugin.getLogger().info("No current claim found for " + player.getName());
            }
            String title = player.getOpenInventory().getTitle();
            if (title.equals("Admin Claim Management")) {
                handleAdminClaimClick(player, clicked, currentClaim);
                return;
            }

            switch (clicked.getType()) {
                case PLAYER_HEAD: // Trust Management
                    if (currentClaim != null) {
                        plugin.getLogger().info("Opening trust menu for " + player.getName());
                        plugin.getTrustGui().openTrustMenu(player, currentClaim);
                    }
                    break;

                case REDSTONE_TORCH: // Flag Management
                    if (currentClaim != null) {
                        plugin.getLogger().info("Opening flag menu for " + player.getName());
                        plugin.getFlagGui().openFlagMenu(player, currentClaim);
                    }
                    break;

                case GLASS: // Visualization
                    if (currentClaim != null) {
                        plugin.getLogger().info("Showing claim visualization for " + player.getName());
                        plugin.getClaimVisualizer().showClaim(player, currentClaim);
                        player.closeInventory();
                    }
                    break;

                case MAP: // Claim Information
                    if (currentClaim != null) {
                        plugin.getLogger().info("Showing claim details for " + player.getName());
                        showClaimDetails(player, currentClaim);
                    }
                    break;

                case BOOK: // View All Claims
                    plugin.getLogger().info("Opening claim list for " + player.getName());
                    plugin.getClaimListGui().openClaimList(player);
                    break;

                case COMMAND_BLOCK: // World Settings (Admin only)
                    if (player.hasPermission("landclaims.admin")) {
                        plugin.getLogger().info("Opening world settings for admin " + player.getName());
                        player.closeInventory();
                        plugin.getWorldSettingsCommand().handleCommand(player, new String[]{"world", "global"});
                    }
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling menu click: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c[LandClaims] An error occurred while processing your click.");
        }
    }
    private void handleAdminClaimClick(Player admin, ItemStack clicked, Claim claim) {
        switch (clicked.getType()) {
            case PLAYER_HEAD:
                if (clicked.getItemMeta().getDisplayName().contains("Trust")) {
                    plugin.getTrustGui().openTrustMenu(admin, claim);
                }
                break;
            case REDSTONE_TORCH:
                plugin.getFlagGui().openFlagMenu(admin, claim);
                break;
            case ENDER_PEARL:
                Location teleportLoc = findSafeLocation(claim);
                if (teleportLoc != null) {
                    admin.teleport(teleportLoc);
                    admin.sendMessage("§a[LandClaims] Teleported to claim.");
                } else {
                    admin.sendMessage("§c[LandClaims] Could not find a safe location!");
                }
                admin.closeInventory();
                break;
            case BARRIER:
                if (admin.isSneaking()) {
                    plugin.getClaimManager().removeClaim(claim);
                    plugin.getDataManager().deleteClaim(claim);
                    admin.sendMessage("§a[LandClaims] Claim deleted successfully!");
                    admin.closeInventory();
                } else {
                    admin.sendMessage("§c[LandClaims] Shift-click to confirm deletion!");
                }
                break;
            case ARROW:
                admin.closeInventory();
                plugin.getAdminMenuGui().openAdminMenu(admin);
                break;
        }
    }
    private Location findSafeLocation(Claim claim) {
        World world = Bukkit.getWorld(claim.getWorld());
        int minX = Math.min(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int maxX = Math.max(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int minZ = Math.min(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());
        int maxZ = Math.max(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        int y = world.getHighestBlockYAt(centerX, centerZ);
        Location loc = new Location(world, centerX + 0.5, y + 1, centerZ + 0.5);

        return loc.getBlock().getType().isSolid() &&
                loc.add(0, 1, 0).getBlock().getType().isAir() &&
                loc.add(0, 1, 0).getBlock().getType().isAir() ? loc : null;
    }

    private void showClaimDetails(Player player, Claim claim) {
        player.closeInventory();
        player.sendMessage("§6=== Claim Details ===");
        player.sendMessage("§7Size: §e" + claim.getSize() + " blocks");
        player.sendMessage("§7Location: §eFrom " + formatLocation(claim.getCorner1()) +
                " to " + formatLocation(claim.getCorner2()));
        player.sendMessage("§7World: §e" + claim.getWorld());
        player.sendMessage("§7Trusted Players: §e" + claim.getTrustedPlayers().size());

        // Show active flags
        player.sendMessage("§7Active Flags:");
        for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
            if (entry.getValue()) {
                player.sendMessage("§a✔ " + entry.getKey().name());
            }
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void cleanup(Player player) {
        openMenus.remove(player.getUniqueId());
    }
    public void openClaimMenu(Player admin, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 54, "Admin Claim Management");

        // Owner Info
        String ownerName = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        ItemStack ownerInfo = createItem(Material.PLAYER_HEAD,
                "§6Owner: " + ownerName,
                Arrays.asList(
                        "§7Size: §e" + claim.getSize() + " blocks",
                        "§7Location: §e" + formatLocation(claim.getCorner1()),
                        "§7World: §e" + claim.getWorld()
                )
        );
        inv.setItem(4, ownerInfo);

        // Trust Management
        ItemStack trustItem = createItem(Material.PLAYER_HEAD,
                "§6Trust Management",
                Arrays.asList(
                        "§7Manage trusted players",
                        "§7Current trusted players: §e" + claim.getTrustedPlayers().size()
                )
        );
        inv.setItem(19, trustItem);

        // Flag Management
        ItemStack flagItem = createItem(Material.REDSTONE_TORCH,
                "§6Claim Settings",
                Arrays.asList(
                        "§7Configure claim settings:",
                        "§7- PvP, Explosions, Fire spread",
                        "§7- Container access, Building",
                        "§7- Monster spawning, and more"
                )
        );
        inv.setItem(21, flagItem);

        // Teleport
        ItemStack teleportItem = createItem(Material.ENDER_PEARL,
                "§6Teleport to Claim",
                Arrays.asList(
                        "§7Click to teleport to",
                        "§7the center of this claim"
                )
        );
        inv.setItem(23, teleportItem);

        // Delete Claim
        ItemStack deleteItem = createItem(Material.BARRIER,
                "§cDelete Claim",
                Arrays.asList(
                        "§7Permanently delete this claim",
                        "§7and refund blocks to the owner",
                        "",
                        "§cShift-click to delete"
                )
        );
        inv.setItem(25, deleteItem);

        // Back button
        ItemStack backButton = createItem(Material.ARROW,
                "§fBack",
                Collections.singletonList("§7Return to previous menu")
        );
        inv.setItem(49, backButton);

        // Store the claim for the click handler
        openMenus.put(admin.getUniqueId(), claim);
        admin.openInventory(inv);
    }


}
