// FlagGUI.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.ChatColor;


import java.util.*;

public class FlagGUI {
    private final Main plugin;
    private final Map<UUID, Claim> openMenus;

    public FlagGUI(Main plugin) {
        this.plugin = plugin;
        this.openMenus = new HashMap<>();
    }

    public void openFlagMenu(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 54, "Flag Management");

        // Add all flags
        int slot = 0;
        for (ClaimFlag flag : ClaimFlag.values()) {
            // Skip WILD flags completely as they're only for world settings
            if (flag == ClaimFlag.WILD_BUILD || flag == ClaimFlag.WILD_INTERACT) {
                continue;
            }

            boolean isEnabled = claim.getFlag(flag);
            ItemStack flagItem = createFlagItem(flag, isEnabled);
            inv.setItem(slot++, flagItem);
        }

        // Back button
        ItemStack backButton = createItem(Material.ARROW, "§fBack to Claim Menu",
                Collections.singletonList("§7Click to return to the main claim menu"));
        inv.setItem(49, backButton);

        openMenus.put(player.getUniqueId(), claim);
        player.openInventory(inv);
    }



    private ItemStack createFlagItem(ClaimFlag flag, boolean enabled) {
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        String name = "§6" + formatFlagName(flag);

        List<String> lore = new ArrayList<>();
        lore.add(enabled ? "§aEnabled" : "§cDisabled");
        lore.add("§7Click to toggle");
        lore.addAll(getFlagDescription(flag));

        return createItem(material, name, lore);
    }

    private String formatFlagName(ClaimFlag flag) {
        String name = flag.name().replace("_", " ");
        StringBuilder formatted = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formatted.append(Character.toLowerCase(c));
            }
            if (c == ' ') {
                capitalizeNext = true;
            }
        }

        return formatted.toString();
    }

    private List<String> getFlagDescription(ClaimFlag flag) {
        List<String> description = new ArrayList<>();

        switch (flag) {
            case PVP:
                description.add("§7Allows or prevents PvP combat");
                description.add("§7within the claim");
                break;
            case CREEPER_DAMAGE:
                description.add("§7Controls whether creepers");
                description.add("§7can damage blocks in the claim");
                break;
            case EXPLOSIONS:
                description.add("§7Controls whether TNT and other");
                description.add("§7explosives can damage blocks");
                break;
            case FIRE_SPREAD:
                description.add("§7Controls whether fire can");
                description.add("§7spread within the claim");
                break;
            case MOB_GRIEFING:
                description.add("§7Controls whether mobs like");
                description.add("§7Endermen can modify blocks");
                break;
            case MONSTERS:
                description.add("§7Controls whether hostile mobs");
                description.add("§7can spawn in the claim");
                break;
            case LEAF_DECAY:
                description.add("§7Controls whether leaves decay");
                description.add("§7naturally when trees are cut");
                break;
            case REDSTONE:
                description.add("§7Controls whether redstone");
                description.add("§7devices can function");
                break;
            case PISTONS:
                description.add("§7Allows or prevents pistons");
                description.add("§7from moving blocks");
                break;
            case HOPPERS:
                description.add("§7Controls whether hoppers can");
                description.add("§7transfer items");
                break;
            case TRUSTED_CONTAINERS:
                description.add("§7Allows trusted players to");
                description.add("§7access chests and containers");
                description.add("§7in this claim");
                break;
            case TRUSTED_DOORS:
                description.add("§7Allows trusted players to");
                description.add("§7use doors and gates");
                description.add("§7in this claim");
                break;
            case TRUSTED_BUILD:
                description.add("§7Allows trusted players to");
                description.add("§7build and break blocks");
                description.add("§7in this claim");
                break;
            case TRUSTED_VILLAGER_TRADING:
                description.add("§7Allows trusted players to");
                description.add("§7trade with villagers");
                description.add("§7in this claim");
                break;
            case UNTRUSTED_CONTAINERS:
                description.add("§7Allows untrusted players to");
                description.add("§7access chests and containers");
                description.add("§7in this claim");
                break;
            case UNTRUSTED_DOORS:
                description.add("§7Allows untrusted players to");
                description.add("§7use doors and gates");
                description.add("§7in this claim");
                break;
            case UNTRUSTED_BUILD:
                description.add("§7Allows untrusted players to");
                description.add("§7build and break blocks");
                description.add("§7in this claim");
                break;
            case TRUSTED_INTERACTIVE:
                description.add("§7Allows trusted players to use");
                description.add("§7pressure plates, buttons,");
                description.add("§7levers, and other interactive");
                description.add("§7blocks in this claim");
                break;
            case SUPPRESS_SIGN_MESSAGES:
                description.add("§7Suppress the message");
                description.add("§7that shows up when you,");
                description.add("§7hit a sign, used for claims");
                description.add("§7with chest shops.");
                break;

            case UNTRUSTED_INTERACTIVE:
                description.add("§7Allows untrusted players to use");
                description.add("§7pressure plates, buttons,");
                description.add("§7levers, and other interactive");
                description.add("§7blocks in this claim");
                break;
            case UNTRUSTED_VILLAGER_TRADING:
                description.add("§7Allows untrusted players to");
                description.add("§7trade with villagers");
                description.add("§7in this claim");
                break;
        }

        return description;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // In FlagGUI.java
    public void handleClick(Player player, ItemStack clicked, InventoryClickEvent event) {
        Claim claim = openMenus.get(player.getUniqueId());
        if (claim == null) {
            plugin.getLogger().warning("[Debug] No claim found in open menus for " + player.getName());
            return;
        }

        plugin.getLogger().info("[Debug] Handling flag click for claim at: " +
                claim.getWorld() + " [" +
                claim.getCorner1().getBlockX() + "," + claim.getCorner1().getBlockZ() + "] to [" +
                claim.getCorner2().getBlockX() + "," + claim.getCorner2().getBlockZ() + "]");

        // Check if player can modify flags
        if (!claim.getOwner().equals(player.getUniqueId()) &&
                !player.hasPermission("landclaims.admin")) {
            player.sendMessage("§c[LandClaims] You don't have permission to modify flags in this claim.");
            return;
        }

        if (clicked.getType() == Material.ARROW) {
            plugin.getMainClaimGui().openMainMenu(player);
            return;
        }

        // Handle flag toggle
        String flagName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                .toUpperCase().replace(" ", "_");
        try {
            ClaimFlag flag = ClaimFlag.valueOf(flagName);
            boolean newValue = !claim.getFlag(flag);

            plugin.getLogger().info("[Debug] Updating flag " + flag + " to " + newValue +
                    " for claim at " + claim.getCorner1().getBlockX() + "," + claim.getCorner1().getBlockZ());

            claim.setFlag(flag, newValue);
            plugin.getDatabaseManager().updateClaimFlags(claim);

            // Update just this item instead of refreshing the whole menu
            int slot = event.getRawSlot();
            ItemStack updatedItem = createFlagItem(flag, newValue);
            player.getOpenInventory().setItem(slot, updatedItem);

            player.sendMessage("§a[LandClaims] " + formatFlagName(flag) +
                    " is now " + (newValue ? "enabled" : "disabled"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Debug] Invalid flag name: " + flagName);
        }
    }


    public int getOpenMenusCount() {
        return openMenus.size();
    }

    public int cleanupOfflineMenus() {
        int count = 0;
        Iterator<Map.Entry<UUID, Claim>> it = openMenus.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Claim> entry = it.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                it.remove();
                count++;
            }
        }
        return count;
    }



    public void cleanup(Player player) {
        openMenus.remove(player.getUniqueId());
    }
}
