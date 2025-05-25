package org.example;
// GUI.java

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GUI {
    private final Main plugin;

    public GUI(Main plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "Land Claims Menu");

        // Get player's claim information
        Set<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        int availableBlocks = plugin.getClaimManager().getPlayerAvailableBlocks(player.getUniqueId());

        // Info item
        ItemStack info = createItem(Material.BOOK,
                "§6Claim Information",
                Arrays.asList(
                        "§7Available Blocks: §e" + availableBlocks,
                        "§7Total Claims: §e" + claims.size()
                )
        );
        inv.setItem(4, info);

        // Claims list
        int slot = 9;
        for (Claim claim : claims) {
            ItemStack claimItem = createItem(Material.MAP,
                    "§6Claim at " + claim.getCorner1().getBlockX() + ", " + claim.getCorner1().getBlockZ(),
                    Arrays.asList(
                            "§7Size: §e" + claim.getSize() + " blocks",
                            "§7World: §e" + claim.getWorld(),
                            "§7Click to manage this claim"
                    )
            );
            inv.setItem(slot++, claimItem);
        }

        player.openInventory(inv);
    }

    public void openClaimMenu(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 54, "Claim Management");

        // Trusted players section
        ItemStack trustedPlayers = createItem(Material.PLAYER_HEAD,
                "§6Trusted Players",
                Collections.singletonList("§7Click to manage trusted players")
        );
        inv.setItem(11, trustedPlayers);

        // Flags section
        ItemStack flags = createItem(Material.REDSTONE_TORCH,
                "§6Claim Flags",
                Collections.singletonList("§7Click to manage claim flags")
        );
        inv.setItem(13, flags);

        // Visualization toggle
        ItemStack visualize = createItem(Material.GLASS,
                "§6Toggle Visualization",
                Collections.singletonList("§7Click to show/hide claim borders")
        );
        inv.setItem(15, visualize);

        player.openInventory(inv);
    }

    public void openTrustedPlayersMenu(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 54, "Trusted Players");

        int slot = 0;
        for (Map.Entry<UUID, TrustLevel> entry : claim.getTrustedPlayers().entrySet()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getKey()));
            meta.setDisplayName("§6" + playerName);
            meta.setLore(Arrays.asList(
                    "§7Trust Level: §e" + entry.getValue().name(),
                    "§7Click to modify trust level",
                    "§7Right-click to remove trust"
            ));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        player.openInventory(inv);
    }

    public void openFlagsMenu(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 27, "Claim Flags");

        for (ClaimFlag flag : ClaimFlag.values()) {
            boolean enabled = claim.getFlag(flag);
            ItemStack flagItem = createItem(
                    enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    "§6" + flag.name(),
                    Arrays.asList(
                            "§7Status: " + (enabled ? "§aEnabled" : "§cDisabled"),
                            "§7Click to toggle"
                    )
            );
            inv.setItem(flag.ordinal(), flagItem);
        }

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
