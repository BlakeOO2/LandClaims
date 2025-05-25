package org.example;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class AdminMenuGUI {
    private final Main plugin;
    private final Map<UUID, Integer> pageNumbers;
    private final Map<UUID, UUID> selectedPlayer;
    private final Map<UUID, Claim> openMenus;
    private static final int ITEMS_PER_PAGE = 45;

    public AdminMenuGUI(Main plugin) {
        this.plugin = plugin;
        this.pageNumbers = new HashMap<>();
        this.selectedPlayer = new HashMap<>();
        this.openMenus = new HashMap<>(); // Initialize it
    }

    public void openAdminMenu(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, "Admin Menu");

        // All Claims button
        ItemStack allClaims = createItem(Material.BOOK,
                "§6All Claims",
                Arrays.asList(
                        "§7View and manage all claims",
                        "§7on the server",
                        "§7Total Claims: §e" + plugin.getClaimManager().getAllClaims().size()
                )
        );
        inv.setItem(13, allClaims);

        // Online Players button
        ItemStack onlinePlayers = createItem(Material.EMERALD,
                "§6Online Players",
                Arrays.asList(
                        "§7View claims of",
                        "§7currently online players",
                        "§7Online: §e" + Bukkit.getOnlinePlayers().size()
                )
        );
        inv.setItem(11, onlinePlayers);

        // Offline Players button
        ItemStack offlinePlayers = createItem(Material.REDSTONE,
                "§6Offline Players",
                Arrays.asList(
                        "§7View claims of",
                        "§7offline players"
                )
        );
        inv.setItem(15, offlinePlayers);
        ItemStack adminLand = createItem(Material.BEDROCK,
                "§6Create Admin Land",
                Arrays.asList(
                        "§7Create an admin-owned claim",
                        "§7that cannot be modified by players",
                        "",
                        "§eClick to get claiming tool"
                )
        );
        inv.setItem(31, adminLand); // Or any other slot you prefer

        // Global Flags button
        ItemStack globalFlags = createItem(Material.REDSTONE_TORCH,
                "§6Global Flags",
                Arrays.asList(
                        "§7Configure server-wide",
                        "§7default claim settings"
                )
        );
        inv.setItem(40, globalFlags);

        admin.openInventory(inv);
    }

    public void openAllClaimsList(Player admin, int page) {
        List<Claim> allClaims = new ArrayList<>(plugin.getClaimManager().getAllClaims());
        int totalPages = (int) Math.ceil(allClaims.size() / (double) ITEMS_PER_PAGE);

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Inventory inv = Bukkit.createInventory(null, 54, "All Claims (Page " + page + "/" + totalPages + ")");

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allClaims.size());

        for (int i = startIndex; i < endIndex; i++) {
            Claim claim = allClaims.get(i);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwner());

            ItemStack claimItem = createItem(Material.MAP,
                    "§6" + owner.getName() + "'s Claim",
                    Arrays.asList(
                            "§7Location: §e" + formatLocation(claim.getCorner1()),
                            "§7Size: §e" + claim.getSize() + " blocks",
                            "§7World: §e" + claim.getWorld(),
                            "",
                            "§eClick to manage"
                    )
            );
            inv.setItem(i - startIndex, claimItem);
        }

        // Navigation buttons
        if (page > 1) {
            inv.setItem(45, createItem(Material.ARROW, "§6Previous Page", null));
        }
        if (page < totalPages) {
            inv.setItem(53, createItem(Material.ARROW, "§6Next Page", null));
        }

        // Back button
        inv.setItem(49, createItem(Material.BARRIER, "§cBack to Admin Menu", null));

        pageNumbers.put(admin.getUniqueId(), page);
        admin.openInventory(inv);
    }

    public void openPlayerSelection(Player admin, boolean onlineOnly) {
        String title = onlineOnly ? "Online Players" : "Offline Players";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Collection<? extends OfflinePlayer> players = onlineOnly ?
                Bukkit.getOnlinePlayers() :
                Arrays.asList(Bukkit.getOfflinePlayers());

        int slot = 0;
        for (OfflinePlayer player : players) {
            if (onlineOnly || plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).size() > 0) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(player);
                meta.setDisplayName("§6" + player.getName());

                List<String> lore = new ArrayList<>();
                int claimCount = plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).size();
                lore.add("§7Claims: §e" + claimCount);
                lore.add("§7Last Seen: §e" + formatLastSeen(player.getLastPlayed()));
                lore.add("");
                lore.add("§eClick to view claims");

                meta.setLore(lore);
                head.setItemMeta(meta);
                inv.setItem(slot++, head);
            }
        }

        // Back button
        inv.setItem(49, createItem(Material.BARRIER, "§cBack to Admin Menu", null));

        admin.openInventory(inv);
    }

    public void openPlayerClaims(Player admin, UUID targetPlayer, int page) {
        Set<Claim> claims = plugin.getClaimManager().getPlayerClaims(targetPlayer);
        String playerName = Bukkit.getOfflinePlayer(targetPlayer).getName();
        Inventory inv = Bukkit.createInventory(null, 54, "Claims of " + playerName);

        int slot = 0;
        for (Claim claim : claims) {
            ItemStack claimItem = createItem(Material.MAP,
                    "§6Claim at " + formatLocation(claim.getCorner1()),
                    Arrays.asList(
                            "§7Size: §e" + claim.getSize() + " blocks",
                            "§7World: §e" + claim.getWorld(),
                            "",
                            "§eLeft-Click §7to teleport",
                            "§eRight-Click §7to manage",
                            "§eShift-Right-Click §7to delete"
                    )
            );
            inv.setItem(slot++, claimItem);
        }

        // Back button
        ItemStack backButton = createItem(Material.BARRIER, "§cBack to Player Selection",
                Collections.singletonList("§7Click to return"));
        inv.setItem(49, backButton);

        admin.openInventory(inv);
    }

    private ItemStack createClaimItem(Claim claim) {
        return createItem(Material.MAP,
                "§6Claim at " + formatLocation(claim.getCorner1()),
                Arrays.asList(
                        "§7Size: §e" + claim.getSize() + " blocks",
                        "§7World: §e" + claim.getWorld(),
                        "",
                        "§eLeft-Click §7to teleport",
                        "§eRight-Click §7to manage",
                        "§eShift-Right-Click §7to delete"
                )
        );
    }


    // In AdminMenuGUI.java, modify handleClick:
    public void handleClick(Player admin, ItemStack clicked, boolean isRightClick, boolean isShiftClick) {
        if (clicked == null) return;

        String title = admin.getOpenInventory().getTitle();

        if (title.equals("Admin Menu")) {
            switch (clicked.getType()) {
                case BOOK: // All Claims
                    openAllClaimsList(admin, 1);
                    break;
                case EMERALD: // Online Players
                    openPlayerSelection(admin, true);
                    break;
                case REDSTONE: // Offline Players
                    openPlayerSelection(admin, false);
                    break;
                case BEDROCK: // Admin Land
                    giveAdminClaimingTool(admin);
                    admin.closeInventory();
                    break;
                case REDSTONE_TORCH: // Global Flags
                    plugin.getGlobalFlagsGui().openGlobalFlagsMenu(admin);
                    break;
            }
        } else if (title.startsWith("All Claims")) {
            handleAllClaimsClick(admin, clicked, isRightClick, isShiftClick);
        } else if (title.equals("Online Players") || title.equals("Offline Players")) {
            handlePlayerSelectionClick(admin, clicked);
        }
    }


    public void handleAdminMenuClick(Player admin, ItemStack clicked) {
        switch (clicked.getType()) {
            case BOOK: // All Claims
                plugin.getLogger().info("Opening all claims list for " + admin.getName());
                openAllClaimsList(admin, 1);
                break;
            case EMERALD: // Online Players
                plugin.getLogger().info("Opening online players list for " + admin.getName());
                openPlayerSelection(admin, true);
                break;
            case REDSTONE: // Offline Players
                plugin.getLogger().info("Opening offline players list for " + admin.getName());
                openPlayerSelection(admin, false);
                break;
            case REDSTONE_TORCH: // Global Flags
                plugin.getGlobalFlagsGui().openGlobalFlagsMenu(admin);
                break;
            case BEDROCK: // Admin Land
                giveAdminClaimingTool(admin);
                admin.closeInventory();
                break;
        }
    }
    private void giveAdminClaimingTool(Player admin) {
        ItemStack tool = new ItemStack(Material.GOLDEN_AXE); // Using axe to differentiate from normal claiming
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§6Admin Claiming Tool");
        meta.setLore(Arrays.asList(
                "§7Left-click: Select first corner",
                "§7Right-click: Select second corner",
                "§7for admin claim"
        ));
        tool.setItemMeta(meta);

        admin.getInventory().addItem(tool);
        admin.sendMessage("§a[LandClaims] You have received the admin claiming tool.");
        admin.sendMessage("§a[LandClaims] Use left and right clicks to select corners.");
    }

    // In AdminMenuGUI.java
    public void handleAllClaimsClick(Player admin, ItemStack clicked, boolean isRightClick, boolean isShiftClick) {
        if (clicked.getType() == Material.BARRIER) {
            openAdminMenu(admin);
            return;
        }

        if (clicked.getType() == Material.MAP) {
            String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            String[] parts = displayName.split("at ");
            if (parts.length < 2) return;

            String[] coords = parts[1].split(", ");
            if (coords.length < 2) return;

            try {
                int x = Integer.parseInt(coords[0]);
                int z = Integer.parseInt(coords[1]);

                // Find claim at these coordinates
                for (Claim claim : plugin.getClaimManager().getAllClaims()) {
                    if (isLocationInClaim(x, z, claim)) {
                        if (isRightClick) {
                            if (isShiftClick) {
                                // Delete claim
                                handleClaimDeletion(admin, claim);
                            } else {
                                // Manage claim
                                openClaimManagement(admin, claim);
                            }
                        } else {
                            // Teleport
                            handleClaimTeleport(admin, claim);
                        }
                        return;
                    }
                }
                admin.sendMessage("§c[LandClaims] Could not find the selected claim.");
            } catch (NumberFormatException e) {
                admin.sendMessage("§c[LandClaims] Error processing claim coordinates.");
            }
        }
    }

    private boolean isLocationInClaim(int x, int z, Claim claim) {
        int minX = Math.min(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int maxX = Math.max(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int minZ = Math.min(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());
        int maxZ = Math.max(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private void handleClaimDeletion(Player admin, Claim claim) {
        admin.closeInventory();
        admin.sendMessage("§c[LandClaims] Are you sure you want to delete this claim?");
        admin.sendMessage("§c[LandClaims] Type §f/lc confirm §cto confirm deletion.");
        plugin.getPendingActions().put(admin.getUniqueId(), () -> {
            plugin.getClaimManager().removeClaim(claim);
            plugin.getDataManager().deleteClaim(claim);
            admin.sendMessage("§a[LandClaims] Claim deleted successfully!");
            openAllClaimsList(admin, 1);
        });
    }

    private void handleClaimTeleport(Player admin, Claim claim) {
        Location safeLoc = findSafeLocation(claim);
        if (safeLoc != null) {
            admin.teleport(safeLoc);
            admin.sendMessage("§a[LandClaims] Teleported to claim.");
        } else {
            admin.sendMessage("§c[LandClaims] Could not find a safe location!");
        }
    }



    private Claim findClaim(UUID ownerId, ItemStack clicked) {
        if (clicked.getItemMeta() == null) return null;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (displayName.contains("at ")) {
            String[] locationParts = displayName.split("at ")[1].split(", ");
            if (locationParts.length == 2) {
                try {
                    int x = Integer.parseInt(locationParts[0]);
                    int z = Integer.parseInt(locationParts[1]);

                    // Find the claim at this location
                    for (Claim claim : plugin.getClaimManager().getPlayerClaims(ownerId)) {
                        if (claim.contains(new Location(Bukkit.getWorlds().get(0), x, 0, z))) {
                            return claim;
                        }
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Could not parse claim coordinates: " + displayName);
                }
            }
        }
        return null;
    }


    public void handlePlayerSelectionClick(Player admin, ItemStack clicked) {
        if (clicked.getType() == Material.BARRIER) {
            openAdminMenu(admin);
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta.getOwningPlayer() != null) {
                UUID targetUUID = meta.getOwningPlayer().getUniqueId();
                openPlayerClaims(admin, targetUUID, 1);
            }
        }
    }


    public void handlePlayerClaimsClick(Player admin, ItemStack clicked, boolean isRightClick, boolean isShiftClick) {
        int currentPage = pageNumbers.getOrDefault(admin.getUniqueId(), 1);
        UUID targetPlayer = selectedPlayer.get(admin.getUniqueId());

        switch (clicked.getType()) {
            case MAP:
                if (clicked.getItemMeta() != null) {
                    String[] locationParts = ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                            .replace("Claim at ", "").split(", ");
                    if (locationParts.length == 2) {
                        try {
                            int x = Integer.parseInt(locationParts[0]);
                            int z = Integer.parseInt(locationParts[1]);
                            Location loc = new Location(admin.getWorld(), x, 0, z);
                            Claim claim = plugin.getClaimManager().getClaimAt(loc);

                            if (claim != null) {
                                // Open the main claim menu for this claim
                                plugin.getMainClaimGui().openClaimMenu(admin, claim);
                            } else {
                                admin.sendMessage("§c[LandClaims] Could not find the selected claim.");
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Could not parse claim coordinates: " +
                                    String.join(", ", locationParts));
                        }
                    }
                }
                break;
            case ARROW:
                if (clicked.getItemMeta().getDisplayName().contains("Previous")) {
                    openPlayerClaims(admin, targetPlayer, currentPage - 1);
                } else {
                    openPlayerClaims(admin, targetPlayer, currentPage + 1);
                }
                break;
            case BARRIER:
                openPlayerSelection(admin, Bukkit.getPlayer(targetPlayer) != null);
                break;
        }
    }

    private void handleClaimClick(Player admin, ItemStack clicked, boolean isRightClick, boolean isShiftClick) {
        String[] locationParts = ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                .replace("Claim at ", "").split(", ");
        int x = Integer.parseInt(locationParts[0]);
        int z = Integer.parseInt(locationParts[1]);

        Location loc = new Location(admin.getWorld(), x, 0, z);
        Claim claim = plugin.getClaimManager().getClaimAt(loc);

        if (claim == null) {
            admin.sendMessage("§c[LandClaims] Error: Could not find claim!");
            return;
        }

        if (isShiftClick && isRightClick) {
            // Delete claim
            plugin.getClaimManager().removeClaim(claim);
            plugin.getDataManager().deleteClaim(claim);
            admin.sendMessage("§a[LandClaims] Claim deleted successfully!");
            openAllClaimsList(admin, pageNumbers.getOrDefault(admin.getUniqueId(), 1));
        }
        else if (isRightClick) {
            // Manage claim
            plugin.getFlagGui().openFlagMenu(admin, claim);
        }
        else {
            // Teleport to claim
            Location teleportLoc = findSafeLocation(claim);
            if (teleportLoc != null) {
                admin.teleport(teleportLoc);
                admin.sendMessage("§a[LandClaims] Teleported to claim.");
            } else {
                admin.sendMessage("§c[LandClaims] Could not find a safe location!");
            }
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

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockZ();
    }

    private String formatLastSeen(long lastPlayed) {
        if (lastPlayed == 0) return "Never";

        long now = System.currentTimeMillis();
        long diff = now - lastPlayed;
        long days = diff / (1000 * 60 * 60 * 24);

        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";
        return days + " days ago";
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public void cleanup(Player player) {
        pageNumbers.remove(player.getUniqueId());
        selectedPlayer.remove(player.getUniqueId());
    }
    private void openClaimManagement(Player admin, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 54, "Claim Management");

        // Owner Info
        String ownerName = claim.isAdminClaim() ?
                plugin.getConfigManager().getServerName() :
                Bukkit.getOfflinePlayer(claim.getOwner()).getName();

        ItemStack ownerInfo = createItem(Material.PLAYER_HEAD,
                "§6Owner: " + ownerName,
                Arrays.asList(
                        "§7Size: §e" + claim.getSize() + " blocks",
                        "§7Location: §e" + formatLocation(claim.getCorner1()),
                        "§7World: §e" + claim.getWorld()
                ));
        inv.setItem(4, ownerInfo);

        // Trust Management
        ItemStack trustItem = createItem(Material.PLAYER_HEAD,
                "§6Trust Management",
                Arrays.asList(
                        "§7Manage trusted players",
                        "§7Current trusted players: §e" + claim.getTrustedPlayers().size()
                ));
        inv.setItem(19, trustItem);

        // Flag Management
        ItemStack flagItem = createItem(Material.REDSTONE_TORCH,
                "§6Claim Settings",
                Arrays.asList(
                        "§7Configure claim settings:",
                        "§7- PvP, Explosions, Fire spread",
                        "§7- Container access, Building",
                        "§7- Monster spawning, and more"
                ));
        inv.setItem(21, flagItem);

        // Teleport
        ItemStack teleportItem = createItem(Material.ENDER_PEARL,
                "§6Teleport to Claim",
                Arrays.asList(
                        "§7Click to teleport to",
                        "§7the center of this claim"
                ));
        inv.setItem(23, teleportItem);

        // Delete Claim
        ItemStack deleteItem = createItem(Material.BARRIER,
                "§cDelete Claim",
                Arrays.asList(
                        "§7Permanently delete this claim",
                        claim.isAdminClaim() ? "§7This is an admin claim" : "§7and refund blocks to the owner",
                        "",
                        "§cShift-click to delete"
                ));
        inv.setItem(25, deleteItem);

        // Back button
        ItemStack backButton = createItem(Material.ARROW,
                "§fBack",
                Collections.singletonList("§7Return to previous menu"));
        inv.setItem(49, backButton);

        // Store the claim for the click handler
        openMenus.put(admin.getUniqueId(), claim);
        admin.openInventory(inv);
    }

    // Also make sure you have this helper method if not already present:


}
