// TrustGUI.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class TrustGUI {
    private final Main plugin;
    private final Map<UUID, Claim> openMenus;

    public TrustGUI(Main plugin) {
        this.plugin = plugin;
        this.openMenus = new HashMap<>();
    }

    public void openTrustMenu(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 54, "Trust Management");

        // Add trusted players
        int slot = 0;
        for (Map.Entry<UUID, TrustLevel> entry : claim.getTrustedPlayers().entrySet()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            OfflinePlayer trusted = Bukkit.getOfflinePlayer(entry.getKey());
            meta.setOwningPlayer(trusted);
            meta.setDisplayName("§6" + trusted.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7Trust Level: §e" + entry.getValue());
            lore.add("§7Left-click to change trust level");
            lore.add("§7Right-click to remove trust");
            meta.setLore(lore);

            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        // Add button
        ItemStack addButton = createItem(Material.EMERALD, "§aAdd Trusted Player",
                Collections.singletonList("§7Click to add a new trusted player"));
        inv.setItem(45, addButton);

        // Back button
        ItemStack backButton = createItem(Material.ARROW, "§fBack to Claim Menu",
                Collections.singletonList("§7Click to return to the main claim menu"));
        inv.setItem(49, backButton);

        openMenus.put(player.getUniqueId(), claim);
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

    public void handleClick(Player player, ItemStack clicked, boolean isRightClick) {
        Claim claim = openMenus.get(player.getUniqueId());
        if (claim == null) return;

        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            UUID targetUUID = meta.getOwningPlayer().getUniqueId();

            if (isRightClick) {
                // Remove trust
                claim.setTrust(targetUUID, null);
                plugin.getDatabaseManager().updateClaimTrustedPlayers(claim); // Use new method
                openTrustMenu(player, claim); // Refresh menu
                player.sendMessage("§a[LandClaims] Removed trust for " + meta.getOwningPlayer().getName());
            } else {
                // Cycle trust level
                TrustLevel current = claim.getTrustLevel(targetUUID);
                TrustLevel next = getNextTrustLevel(current);
                claim.setTrust(targetUUID, next);
                plugin.getDatabaseManager().updateClaimTrustedPlayers(claim); // Use new method

                // Update just this item
                List<String> lore = new ArrayList<>();
                lore.add("§7Trust Level: §e" + next);
                lore.add("§7Left-click to change trust level");
                lore.add("§7Right-click to remove trust");
                meta.setLore(lore);
                clicked.setItemMeta(meta);

                player.sendMessage("§a[LandClaims] Changed trust level for " +
                        meta.getOwningPlayer().getName() + " to " + next);
            }
        } else if (clicked.getType() == Material.EMERALD) {
            handleAddTrust(player);
        } else if (clicked.getType() == Material.ARROW) {
            plugin.getMainClaimGui().openMainMenu(player);
        }
    }

    private TrustLevel getNextTrustLevel(TrustLevel current) {
        TrustLevel[] levels = TrustLevel.values();
        int nextIndex = (current.ordinal() + 1) % (levels.length - 1); // Exclude OWNER level
        return levels[nextIndex];
    }

    public void cleanup(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    private void handleAddTrust(Player player) {
        Claim claim = openMenus.get(player.getUniqueId());
        if (claim == null) return;

        player.closeInventory();
        plugin.getChatInputManager().awaitChatInput(player,
                "§6[LandClaims] Type the name of the player you want to trust (or 'cancel' to cancel):",
                input -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        player.sendMessage("§c[LandClaims] Trust action cancelled.");
                        return;
                    }

                    @SuppressWarnings("deprecation")
                    OfflinePlayer target = Bukkit.getOfflinePlayer(input);

                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        player.sendMessage("§c[LandClaims] Player not found: " + input);
                        return;
                    }

                    if (target.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage("§c[LandClaims] You can't trust yourself!");
                        return;
                    }

                    claim.setTrust(target.getUniqueId(), TrustLevel.BUILD);
                    plugin.getDatabaseManager().updateClaimTrustedPlayers(claim); // Use new method
                    player.sendMessage("§a[LandClaims] Successfully trusted " + target.getName() + " with BUILD access.");

                    // Reopen the trust menu
                    Bukkit.getScheduler().runTask(plugin, () -> openTrustMenu(player, claim));
                });
    }
}
