// ClaimListGUI.java
package org.example;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.CompassMeta;


import java.util.*;

public class ClaimListGUI {
    private final Main plugin;
    private final Map<UUID, Integer> pageNumbers;
    private final int CLAIMS_PER_PAGE = 45; // Leave room for navigation buttons

    public ClaimListGUI(Main plugin) {
        this.plugin = plugin;
        this.pageNumbers = new HashMap<>();
    }

    public void openClaimList(Player player) {
        openClaimList(player, 1);
    }

    public void openClaimList(Player player, int page) {
        Set<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        List<Claim> claimList = new ArrayList<>(claims);

        int totalPages = (int) Math.ceil(claimList.size() / (double) CLAIMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Inventory inv = Bukkit.createInventory(null, 54, "Your Claims (Page " + page + "/" + totalPages + ")");
        pageNumbers.put(player.getUniqueId(), page);

        // Calculate start and end indices for current page
        int start = (page - 1) * CLAIMS_PER_PAGE;
        int end = Math.min(start + CLAIMS_PER_PAGE, claimList.size());

        // Add claims for current page
        int slot = 0;
        for (Claim claim : claims) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Size: §e" + claim.getSize() + " blocks");
            lore.add("§7World: §e" + claim.getWorld());
            lore.add("§7Trusted Players: §e" + claim.getTrustedPlayers().size());
            lore.add("");
            if (player.hasPermission("landclaims.teleport")) {
                lore.add("§eLeft-Click §7to teleport");
            }
            lore.add("§eRight-Click §7to manage");
            lore.add("§eShift-Right-Click §7to delete");

            ItemStack claimItem = createItem(Material.COMPASS,
                    "§6Claim at " + formatLocation(claim.getCorner1()),
                    lore);

            // Prevent item from being moved
            ItemMeta meta = claimItem.getItemMeta();
            meta.setUnbreakable(true);
            claimItem.setItemMeta(meta);

            inv.setItem(slot++, claimItem);
        }

        // Navigation buttons
        if (page > 1) {
            ItemStack prevPage = createItem(Material.ARROW, "§6Previous Page",
                    Collections.singletonList("§7Click to go to page " + (page - 1)));
            inv.setItem(45, prevPage);
        }

        if (page < totalPages) {
            ItemStack nextPage = createItem(Material.ARROW, "§6Next Page",
                    Collections.singletonList("§7Click to go to page " + (page + 1)));
            inv.setItem(53, nextPage);
        }

        // Back button
        ItemStack backButton = createItem(Material.BARRIER, "§cBack to Main Menu",
                Collections.singletonList("§7Click to return to the main menu"));
        inv.setItem(49, backButton);

        // Statistics
        ItemStack statsItem = createItem(Material.BOOK, "§6Claim Statistics",
                Arrays.asList(
                        "§7Total Claims: §e" + claims.size(),
                        "§7Available Blocks: §e" + plugin.getBlockAccumulator().getBlocks(player.getUniqueId()),
                        "§7Total Claimed Blocks: §e" + calculateTotalClaimedBlocks(claims)
                ));
        inv.setItem(48, statsItem);

        player.openInventory(inv);
    }

    private ItemStack createClaimItem(Claim claim) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        String name = "§6Claim at " + formatLocation(claim.getCorner1());
        List<String> lore = new ArrayList<>();
        lore.add("§7Size: §e" + claim.getSize() + " blocks");
        lore.add("§7World: §e" + claim.getWorld());
        lore.add("§7Trusted Players: §e" + claim.getTrustedPlayers().size());
        lore.add("");
        lore.add("§eLeft-Click §7to teleport");
        lore.add("§eRight-Click §7to manage");
        lore.add("§eShift-Right-Click §7to delete");

        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockZ();
    }

    private int calculateTotalClaimedBlocks(Set<Claim> claims) {
        return claims.stream().mapToInt(Claim::getSize).sum();
    }

    public void handleClick(Player player, ItemStack clicked, boolean isRightClick, boolean isShiftClick) {
        if (clicked == null) return;

        String title = player.getOpenInventory().getTitle();
        if (!title.startsWith("Your Claims")) return;

        int currentPage = pageNumbers.getOrDefault(player.getUniqueId(), 1);

        switch (clicked.getType()) {
            case COMPASS:
                handleClaimClick(player, clicked, isRightClick, isShiftClick);
                break;

            case ARROW:
                if (clicked.getItemMeta().getDisplayName().contains("Previous")) {
                    openClaimList(player, currentPage - 1);
                } else {
                    openClaimList(player, currentPage + 1);
                }
                break;

            case BARRIER:
                plugin.getMainClaimGui().openMainMenu(player);
                break;
        }
    }

    private void handleClaimClick(Player player, ItemStack clicked, boolean isRightClick, boolean isShiftClick) {
        // Extract location from item name
        if (!isRightClick && !player.hasPermission("landclaims.teleport")) {
            player.sendMessage("§c[LandClaims] You don't have permission to teleport to claims.");
            return;
        }
        String[] parts = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).split(" ");
        int x = Integer.parseInt(parts[2].replace(",", ""));
        int z = Integer.parseInt(parts[3]);

        // Find the corresponding claim
        Claim targetClaim = plugin.getClaimManager().getClaimAt(
                new Location(player.getWorld(), x, 0, z));

        if (targetClaim == null) {
            player.sendMessage("§c[LandClaims] Error: Could not find this claim!");
            return;
        }

        if (isShiftClick && isRightClick) {
            // Delete claim
            if (!player.hasPermission("landclaims.admin") &&
                    !targetClaim.getOwner().equals(player.getUniqueId())) {
                player.sendMessage("§c[LandClaims] You don't have permission to delete this claim!");
                return;
            }

            // Confirm deletion
            player.closeInventory();
            player.sendMessage("§c[LandClaims] Are you sure you want to delete this claim?");
            player.sendMessage("§c[LandClaims] Type §f/lc confirm §cto confirm deletion.");
            plugin.getPendingActions().put(player.getUniqueId(),
                    () -> deleteClaim(player, targetClaim));

        } else if (isRightClick) {
            // Manage claim
            plugin.getMainClaimGui().openMainMenu(player);

        } else {
            // Teleport to claim
            Location safeLoc = findSafeLocation(targetClaim); // Changed from claim to targetClaim
            if (safeLoc != null) {
                player.teleport(safeLoc);
                player.sendMessage("§a[LandClaims] Teleported to claim.");
            } else {
                player.sendMessage("§c[LandClaims] Could not find a safe location in this claim!");
            }
        }
    }




    // Add to ClaimListGUI.java or wherever you handle teleports

    private Location findSafeLocation(Claim claim) {
        World world = Bukkit.getWorld(claim.getWorld());
        int minX = Math.min(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int maxX = Math.max(claim.getCorner1().getBlockX(), claim.getCorner2().getBlockX());
        int minZ = Math.min(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());
        int maxZ = Math.max(claim.getCorner1().getBlockZ(), claim.getCorner2().getBlockZ());

        // Try center first
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        // Try center location first
        Location safeLoc = findSafeY(world, centerX, centerZ);
        if (safeLoc != null) {
            return safeLoc;
        }

        // If center isn't safe, spiral outward from center
        int radius = 1;
        int maxRadius = Math.min((maxX - minX), (maxZ - minZ)) / 2; // Don't search outside claim

        while (radius <= maxRadius) {
            // Try locations in a spiral pattern
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // Only check the outer ring
                    if (Math.abs(x) == radius || Math.abs(z) == radius) {
                        int testX = centerX + x;
                        int testZ = centerZ + z;

                        // Make sure we're still in the claim
                        if (testX >= minX && testX <= maxX && testZ >= minZ && testZ <= maxZ) {
                            safeLoc = findSafeY(world, testX, testZ);
                            if (safeLoc != null) {
                                return safeLoc;
                            }
                        }
                    }
                }
            }
            radius++;
        }

        return null;
    }

    private Location findSafeY(World world, int x, int z) {
        // Start from the highest block and work down
        int maxY = world.getMaxHeight();
        int minY = world.getMinHeight();

        for (int y = maxY; y >= minY; y--) {
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            Location below = loc.clone().subtract(0, 1, 0);
            Location above = loc.clone().add(0, 1, 0);

            // Check if we have a solid block below and air above
            if (below.getBlock().getType().isSolid() &&
                    !below.getBlock().getType().name().contains("LAVA") &&
                    !below.getBlock().getType().name().contains("WATER") &&
                    !below.getBlock().getType().name().contains("FIRE") &&
                    !below.getBlock().getType().name().contains("CACTUS") &&
                    loc.getBlock().getType().isAir() &&
                    above.getBlock().getType().isAir()) {

                // Additional safety checks
                if (!isHazardous(below.getBlock().getType())) {
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


    private void deleteClaim(Player player, Claim claim) {
        // Refund blocks
        plugin.getBlockAccumulator().addBlocks(player.getUniqueId(), claim.getSize());

        // Remove claim
        plugin.getClaimManager().removeClaim(claim);
        plugin.getDataManager().deleteClaim(claim);

        player.sendMessage("§a[LandClaims] Claim deleted successfully!");
        player.sendMessage("§a[LandClaims] " + claim.getSize() + " blocks have been refunded.");

        // Reopen the GUI
        openClaimList(player);
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
        pageNumbers.remove(player.getUniqueId());
    }
}
