package org.example;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GUIListener implements Listener {
    private final Main plugin;

    public GUIListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Cancel all item movement in GUIs
        if (title.contains("Claims") ||
                title.contains("Menu") ||
                title.contains("Trust") ||
                title.contains("Flag") ||
                title.contains("Admin") ||
                title.contains("Players")) {

            event.setCancelled(true);

            if (event.getCurrentItem() != null && event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                handleGuiClick(player, event.getCurrentItem(), event);
            }
        }
    }

    private void handleGuiClick(Player player, ItemStack clicked, InventoryClickEvent event) {
        try {
            String title = event.getView().getTitle();

            // Debug log
            plugin.getLogger().info("GUI Click in " + title + " by " + player.getName());



            if (title.startsWith("Your Claims")) {
                plugin.getClaimListGui().handleClick(player, clicked,
                        event.isRightClick(), event.isShiftClick());
                return;
            }

            if (title.equals("Online Players") || title.equals("Offline Players")) {
                plugin.getAdminMenuGui().handlePlayerSelectionClick(player, clicked);
                return;
            }

            if (title.startsWith("All Claims")) {
                plugin.getAdminMenuGui().handleAllClaimsClick(player, clicked,
                        event.isRightClick(), event.isShiftClick());
                return;
            }

            switch (title) {
                case "Flag Management":
                    plugin.getFlagGui().handleClick(player, clicked, event);
                    break;
                case "Trust Management":
                    plugin.getTrustGui().handleClick(player, clicked, event.isRightClick());
                    break;
                case "Land Claims Menu":
                    plugin.getMainClaimGui().handleClick(player, clicked);
                    break;
                case "Global Flags":
                    plugin.getGlobalFlagsGui().handleClick(player, clicked);
                    break;
                case "Global Claim Settings":
                case "Trust Settings":
                    plugin.getClaimSettingsGui().handleClick(player, clicked);
                    break;
                case "Admin Menu":
                    plugin.getAdminMenuGui().handleAdminMenuClick(player, clicked);
                    break;
                default:
                    if (title.startsWith("Claims of")) {
                        plugin.getAdminMenuGui().handlePlayerClaimsClick(player, clicked,
                                event.isRightClick(), event.isShiftClick());
                    } else {
                        plugin.getLogger().warning("Unhandled GUI click in: " + title);
                    }
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling GUI click: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c[LandClaims] An error occurred while processing your click.");
        }
    }

    private void preventItemRemoval(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) {
                ItemMeta meta = item.getItemMeta();
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        try {
            if (!(event.getPlayer() instanceof Player)) return;
            Player player = (Player) event.getPlayer();
            String title = event.getView().getTitle();

            // Only debug log if enabled in config
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Inventory closed: " + title + " by " + player.getName());
            }

            // Only handle our custom GUIs
            if (title.contains("Claims") ||
                    title.contains("Menu") ||
                    title.contains("Trust") ||
                    title.contains("Flag") ||
                    title.contains("Admin")) {

                switch (title) {
                    case "Your Claims":
                        plugin.getClaimListGui().cleanup(player);
                        break;
                    case "Flag Management":
                        plugin.getFlagGui().cleanup(player);
                        break;
                    case "Trust Management":
                        plugin.getTrustGui().cleanup(player);
                        break;
                    case "Land Claims Menu":
                        plugin.getMainClaimGui().cleanup(player);
                        break;
                    case "Global Flags":
                        // No cleanup needed
                        break;
                    case "Global Claim Settings":
                    case "Trust Settings":
                        plugin.getClaimSettingsGui().cleanup(player);
                        break;
                    default:
                        if (title.startsWith("Admin Menu") ||
                                title.startsWith("All Claims") ||
                                title.startsWith("Claims of")) {
                            plugin.getAdminMenuGui().cleanup(player);
                        }
                        // Don't log warnings for non-plugin inventories
                        break;
                }
            }
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().severe("Error handling inventory close: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
