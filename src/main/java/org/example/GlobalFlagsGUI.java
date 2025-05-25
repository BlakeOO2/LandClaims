package org.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GlobalFlagsGUI {
    private final Main plugin;

    public GlobalFlagsGUI(Main plugin) {
        this.plugin = plugin;
    }

    public void openGlobalFlagsMenu(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, "Global Flags");

        int slot = 0;
        for (ClaimFlag flag : ClaimFlag.values()) {
            boolean isEnabled = plugin.getWorldSettingsManager().getGlobalFlag(flag);
            ItemStack flagItem = createFlagItem(flag, isEnabled);
            inv.setItem(slot++, flagItem);
        }

        // Back button
        inv.setItem(49, createItem(Material.ARROW, "§cBack to Admin Menu",
                Collections.singletonList("§7Click to return")));

        admin.openInventory(inv);
    }

    private ItemStack createFlagItem(ClaimFlag flag, boolean enabled) {
        return createItem(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "§6" + formatFlagName(flag),
                Arrays.asList(
                        enabled ? "§aEnabled" : "§cDisabled",
                        "§7Click to toggle"
                )
        );
    }

    private String formatFlagName(ClaimFlag flag) {
        return formatFlagName(flag.name()); // Call the existing string formatter with the flag name
    }

    private String formatFlagName(String name) {
        String[] words = name.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1).toLowerCase());
        }
        return formatted.toString();
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

    public void handleClick(Player admin, ItemStack clicked) {
        if (clicked.getType() == Material.ARROW) {
            plugin.getAdminMenuGui().openAdminMenu(admin);
            return;
        }

        if (!admin.hasPermission("landclaims.admin")) {
            admin.sendMessage("§c[LandClaims] You don't have permission to modify global flags.");
            return;
        }

        String flagName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                .toUpperCase().replace(" ", "_");
        try {
            ClaimFlag flag = ClaimFlag.valueOf(flagName);
            boolean newValue = !plugin.getWorldSettingsManager().getGlobalFlag(flag);
            plugin.getWorldSettingsManager().setGlobalFlag(flag, newValue);

            // Refresh the menu
            openGlobalFlagsMenu(admin);

            // Notify the admin
            admin.sendMessage("§a[LandClaims] Global flag " + flagName + " is now " +
                    (newValue ? "enabled" : "disabled") + ".");

            // Log the change
            plugin.getLogger().info("Global flag " + flagName + " was set to " + newValue +
                    " by " + admin.getName());
        } catch (IllegalArgumentException e) {
            admin.sendMessage("§c[LandClaims] Invalid flag name: " + flagName);
        }
    }
}
