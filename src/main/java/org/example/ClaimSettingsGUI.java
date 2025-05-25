package org.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class ClaimSettingsGUI {
    private final Main plugin;
    private final Map<UUID, Claim> openMenus;

    public ClaimSettingsGUI(Main plugin) {
        this.plugin = plugin;
        this.openMenus = new HashMap<>();
    }

    public void openGlobalSettings(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 36, "Global Claim Settings");

        // Add global flags
        addToggleItem(inv, 10, Material.DIAMOND_SWORD, "PvP", claim.getFlag(ClaimFlag.PVP));
        addToggleItem(inv, 11, Material.TNT, "Explosions", claim.getFlag(ClaimFlag.EXPLOSIONS));
        addToggleItem(inv, 12, Material.FIRE_CHARGE, "Fire Spread", claim.getFlag(ClaimFlag.FIRE_SPREAD));
        addToggleItem(inv, 13, Material.ZOMBIE_HEAD, "Mob Griefing", claim.getFlag(ClaimFlag.MOB_GRIEFING));
        addToggleItem(inv, 14, Material.REDSTONE, "Redstone", claim.getFlag(ClaimFlag.REDSTONE));
        addToggleItem(inv, 15, Material.PISTON, "Pistons", claim.getFlag(ClaimFlag.PISTONS));
        addToggleItem(inv, 16, Material.HOPPER, "Hoppers", claim.getFlag(ClaimFlag.HOPPERS));

        // Back button
        inv.setItem(31, createItem(Material.ARROW, "§cBack to Main Menu", null));

        openMenus.put(player.getUniqueId(), claim);
        player.openInventory(inv);
    }

    public void openTrustSettings(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 36, "Trust Settings");

        // Trusted Players Section
        ItemStack trustedTitle = createItem(Material.EMERALD,
                "§aTrusted Players Settings",
                Arrays.asList(
                        "§7Configure what trusted",
                        "§7players can do in your claim"
                ));
        inv.setItem(10, trustedTitle);

        addToggleItem(inv, 11, Material.CHEST, "Containers (Trusted)", claim.getFlag(ClaimFlag.TRUSTED_CONTAINERS));
        addToggleItem(inv, 12, Material.OAK_DOOR, "Doors (Trusted)", claim.getFlag(ClaimFlag.TRUSTED_DOORS));
        addToggleItem(inv, 13, Material.DIAMOND_PICKAXE, "Build (Trusted)", claim.getFlag(ClaimFlag.TRUSTED_BUILD));

        // Untrusted Players Section
        ItemStack untrustedTitle = createItem(Material.REDSTONE,
                "§cUntrusted Players Settings",
                Arrays.asList(
                        "§7Configure what untrusted",
                        "§7players can do in your claim"
                ));
        inv.setItem(19, untrustedTitle);

        addToggleItem(inv, 20, Material.CHEST, "Containers (Untrusted)", claim.getFlag(ClaimFlag.UNTRUSTED_CONTAINERS));
        addToggleItem(inv, 21, Material.OAK_DOOR, "Doors (Untrusted)", claim.getFlag(ClaimFlag.UNTRUSTED_DOORS));
        addToggleItem(inv, 22, Material.DIAMOND_PICKAXE, "Build (Untrusted)", claim.getFlag(ClaimFlag.UNTRUSTED_BUILD));

        // Back button
        inv.setItem(31, createItem(Material.ARROW, "§cBack to Main Menu", null));

        openMenus.put(player.getUniqueId(), claim);
        player.openInventory(inv);
    }

    private void addToggleItem(Inventory inv, int slot, Material material, String name, boolean enabled) {
        List<String> lore = new ArrayList<>();
        lore.add(enabled ? "§aEnabled" : "§cDisabled");
        lore.add("§7Click to toggle");

        ItemStack item = createItem(material,
                (enabled ? "§a" : "§c") + name,
                lore);
        inv.setItem(slot, item);
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

    public void handleClick(Player player, ItemStack clicked) {
        try {
            if (clicked == null) return;

            Claim claim = openMenus.get(player.getUniqueId());
            if (claim == null) return;

            String title = player.getOpenInventory().getTitle();

            switch (title) {
                case "Global Claim Settings":
                    handleGlobalSettingsClick(player, clicked, claim);
                    break;
                case "Trust Settings":
                    handleTrustSettingsClick(player, clicked, claim);
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling settings click: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c[LandClaims] An error occurred while processing your click.");
        }
    }

    private void handleGlobalSettingsClick(Player player, ItemStack clicked, Claim claim) {
        if (clicked.getType() == Material.ARROW) {
            plugin.getMainClaimGui().openMainMenu(player);
            return;
        }

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        ClaimFlag flag = null;

        switch (name.toUpperCase()) {
            case "PVP":
                flag = ClaimFlag.PVP;
                break;
            case "EXPLOSIONS":
                flag = ClaimFlag.EXPLOSIONS;
                break;
            case "FIRE SPREAD":
                flag = ClaimFlag.FIRE_SPREAD;
                break;
            case "MOB GRIEFING":
                flag = ClaimFlag.MOB_GRIEFING;
                break;
            case "REDSTONE":
                flag = ClaimFlag.REDSTONE;
                break;
            case "PISTONS":
                flag = ClaimFlag.PISTONS;
                break;
            case "HOPPERS":
                flag = ClaimFlag.HOPPERS;
                break;
        }

        if (flag != null) {
            boolean newValue = !claim.getFlag(flag);
            claim.setFlag(flag, newValue);
            plugin.getDataManager().saveClaim(claim);
            openGlobalSettings(player, claim); // Refresh menu
            player.sendMessage("§a[LandClaims] " + name + " is now " + (newValue ? "enabled" : "disabled"));
        }
    }

    private void handleTrustSettingsClick(Player player, ItemStack clicked, Claim claim) {
        if (clicked.getType() == Material.ARROW) {
            plugin.getMainClaimGui().openMainMenu(player);
            return;
        }

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        ClaimFlag flag = null;

        if (name.contains("(Trusted)")) {
            switch (name.replace(" (Trusted)", "").toUpperCase()) {
                case "CONTAINERS":
                    flag = ClaimFlag.TRUSTED_CONTAINERS;
                    break;
                case "DOORS":
                    flag = ClaimFlag.TRUSTED_DOORS;
                    break;
                case "BUILD":
                    flag = ClaimFlag.TRUSTED_BUILD;
                    break;
            }
        } else if (name.contains("(Untrusted)")) {
            switch (name.replace(" (Untrusted)", "").toUpperCase()) {
                case "CONTAINERS":
                    flag = ClaimFlag.UNTRUSTED_CONTAINERS;
                    break;
                case "DOORS":
                    flag = ClaimFlag.UNTRUSTED_DOORS;
                    break;
                case "BUILD":
                    flag = ClaimFlag.UNTRUSTED_BUILD;
                    break;
            }
        }

        if (flag != null) {
            boolean newValue = !claim.getFlag(flag);
            claim.setFlag(flag, newValue);
            plugin.getDataManager().saveClaim(claim);
            openTrustSettings(player, claim); // Refresh menu
            player.sendMessage("§a[LandClaims] " + name + " is now " + (newValue ? "enabled" : "disabled"));
        }
    }

    public void cleanup(Player player) {
        openMenus.remove(player.getUniqueId());
    }
}


