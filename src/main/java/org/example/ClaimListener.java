package org.example;

// ClaimListener.java

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventPriority;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;


import org.bukkit.block.data.type.WallSign;


import org.bukkit.entity.Player;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.PotionSplashEvent;

import java.util.*;

public class ClaimListener implements Listener {
    private final Main plugin;
    private final SelectionManager selectionManager;
    private final Map<UUID, Long> lastFlightMessage = new HashMap<>();
    private static final long FLIGHT_MESSAGE_COOLDOWN = 30000; // 3 seconds in

    public ClaimListener(Main plugin) {
        this.plugin = plugin;
        this.selectionManager = new SelectionManager(plugin);
    }





    private boolean isClaimingTool(org.bukkit.inventory.ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_SHOVEL;
    }



    private boolean canBuild(Player player, Claim claim) {
        return claim.getOwner().equals(player.getUniqueId()) ||
                claim.getTrustLevel(player.getUniqueId()).ordinal() >= TrustLevel.BUILD.ordinal() ||
                player.hasPermission("landclaims.admin.override");
    }

    private boolean isCrop(Material material) {
        return material == Material.WHEAT ||
                material == Material.CARROTS ||
                material == Material.POTATOES ||
                material == Material.BEETROOTS ||
                material == Material.MELON_STEM ||
                material == Material.PUMPKIN_STEM;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (plugin.getChatInputManager().handleChatInput(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHitEvent(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SmallFireball)) {
            return;
        }

        // Get the location where the fireball will land
        Location impactLocation = event.getEntity().getLocation();
        Claim claim = plugin.getClaimManager().getClaimAt(impactLocation);

        // Check if fire spread is allowed in this location
        if (claim != null) {
            if (!claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
                event.getEntity().remove(); // Remove the fireball
                event.setCancelled(true);
            }
        } else {
            // Check global settings for unclaimed areas
            if (!plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.FIRE_SPREAD)) {
                event.getEntity().remove();
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockIgnite(BlockIgniteEvent event) {
        // Check if it's from a blaze fireball
        if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL) {
            Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());

            if (claim != null) {
                if (!claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
                    event.setCancelled(true);
                }
            } else {
                // Check global settings for unclaimed areas
                if (!plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.FIRE_SPREAD)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check if they've changed blocks (not just looking around)
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Claim fromClaim = plugin.getClaimManager().getClaimAt(event.getFrom());
        Claim toClaim = plugin.getClaimManager().getClaimAt(event.getTo());

        // Handle flight state
        if (plugin.getFlightState(player.getUniqueId())) {
            // Only handle flight changes when crossing claim boundaries
            if (!Objects.equals(fromClaim, toClaim)) {
                if (toClaim != null && canFlyInClaim(player, toClaim)) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                } else {
                    player.setAllowFlight(false);
                    player.setFlying(false);

                    // Check cooldown before sending message
                    long now = System.currentTimeMillis();
                    Long lastMessage = lastFlightMessage.get(player.getUniqueId());
                    if (lastMessage == null || now - lastMessage >= FLIGHT_MESSAGE_COOLDOWN) {
                        player.sendMessage("§c[LandClaims] Flight disabled - left claim area.");
                        lastFlightMessage.put(player.getUniqueId(), now);
                    }
                }
            }
        }

        // Check if notifications are enabled in config
        if (!plugin.getConfig().getBoolean("messages.show-claim-enter-exit", true)) {
            return;
        }

// Check if player has notifications enabled
        PlayerPreferences prefs = plugin.getPlayerPreferences(player.getUniqueId());
        if (!prefs.isNotificationsEnabled()) {
            return;
        }

        // Check if player has notifications enabled
        if (!player.hasPermission("landclaims.notifications")) {
            return;
        }

        // If entering a new claim
        if ((fromClaim == null && toClaim != null) ||
                (fromClaim != null && toClaim != null && !fromClaim.equals(toClaim))) {
            String message;
            if (toClaim.isAdminClaim()) {
                message = plugin.getConfig().getString("messages.admin-claim-enter",
                        "§6[LandClaims] You have entered an admin protected area");
            } else {
                message = plugin.getConfig().getString("messages.claim-enter",
                                "§6[LandClaims] You have entered %owner%'s claim")
                        .replace("%owner%", Bukkit.getOfflinePlayer(toClaim.getOwner()).getName());
            }
            player.sendMessage(message);
        }
        // If leaving a claim
        else if (fromClaim != null && (toClaim == null || !fromClaim.equals(toClaim))) {
            String message;
            if (fromClaim.isAdminClaim()) {
                message = plugin.getConfig().getString("messages.admin-claim-exit",
                        "§6[LandClaims] You have left an admin protected area");
            } else {
                message = plugin.getConfig().getString("messages.claim-exit",
                                "§6[LandClaims] You have left %owner%'s claim")
                        .replace("%owner%", Bukkit.getOfflinePlayer(fromClaim.getOwner()).getName());
            }
            player.sendMessage(message);
        }
    }

    public boolean canFlyInClaim(Player player, Claim claim) {
        // Admin bypass always allows flight
        if (player.hasPermission("landclaims.admin.bypass")) {
            return true;
        }

        // Must have basic flight permission
        if (!player.hasPermission("landclaims.flight")) {
            return false;
        }

        // In admin claims
        if (claim.isAdminClaim()) {
            return player.hasPermission("landclaims.adminland.fly");
        }

        // In personal claims
        if (claim.getOwner().equals(player.getUniqueId())) {
            return true;
        }

        // Check trust level - safely handle null case
        TrustLevel trustLevel = claim.getTrustLevel(player.getUniqueId());
        return trustLevel != null && trustLevel.ordinal() >= TrustLevel.MANAGE.ordinal();
    }


    // Update the fall damage prevention to use the same permission check
    @EventHandler
    public void onPlayerFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;

        Player player = (Player) event.getEntity();
        // If they were just flying in a claim where they have permission
        if (player.hasPermission("landclaims.flight")) {
            Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
            if (claim != null && canFlyInClaim(player, claim)) {
                event.setCancelled(true);
            }
        }
    }

    // In ClaimListener.java, add this method:

    @EventHandler
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (newItem != null && isClaimingTool(newItem)) {
            int availableBlocks = plugin.getClaimManager().getPlayerAvailableBlocks(player.getUniqueId());
            player.sendMessage("§6[LandClaims] §fYou have §e" + availableBlocks + " §fblocks available to claim");
        }
    }

    // Also add this to catch when players pick up or move items in inventory
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if they're moving the claiming tool to their hand
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.QUICKBAR &&
                event.getCurrentItem() != null && isClaimingTool(event.getCurrentItem())) {
            int availableBlocks = plugin.getClaimManager().getPlayerAvailableBlocks(player.getUniqueId());
            player.sendMessage("§6[LandClaims] §fYou have §e" + availableBlocks + " §fblocks available to claim");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastFlightMessage.remove(event.getPlayer().getUniqueId());

        plugin.getSelectionManager().clearSelection(event.getPlayer());
    }
    // In ClaimListener.java, add:
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getProtectionManager().canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[LandClaims] You don't have permission to build here.");
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getProtectionManager().canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[LandClaims] You don't have permission to build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        // Check for leads/leashes
        if (entity instanceof LeashHitch) {
            Claim claim = plugin.getClaimManager().getClaimAt(entity.getLocation());
            if (claim == null) return; // No claim here, default behavior

            // Owner always has access
            if (claim.getOwner().equals(player.getUniqueId())) return;

            // Admin bypass
            if (player.hasPermission("landclaims.admin") &&
                    plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
                return;
            }

            // Check trust level
            boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
            boolean allowed = isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_BUILD) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_BUILD);

            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage("§c[LandClaims] You don't have permission to interact with leads here.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent event) {
        // This handles leads, item frames, paintings, etc.
        if (!(event.getRemover() instanceof Player)) return;

        Player player = (Player) event.getRemover();
        Entity entity = event.getEntity();

        Claim claim = plugin.getClaimManager().getClaimAt(entity.getLocation());
        if (claim == null) return; // No claim here, default behavior

        // Owner always has access
        if (claim.getOwner().equals(player.getUniqueId())) return;

        // Admin bypass
        if (player.hasPermission("landclaims.admin") &&
                plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
            return;
        }

        // Check trust level
        boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
        boolean allowed = isTrusted ?
                claim.getFlag(ClaimFlag.TRUSTED_BUILD) :
                claim.getFlag(ClaimFlag.UNTRUSTED_BUILD);

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage("§c[LandClaims] You don't have permission to break this here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        // This is needed for armor stands and some other specific entities
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        // Skip if using claiming tool
        if (player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_SHOVEL) {
            return;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(entity.getLocation());
        if (claim == null) return; // No claim here, default behavior

        // Owner always has access
        if (claim.getOwner().equals(player.getUniqueId())) return;

        // Admin bypass
        if (player.hasPermission("landclaims.admin") &&
                plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
            return;
        }

        // Check trust level and entity type
        boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
        boolean allowed;

        if (entity instanceof ArmorStand) {
            // Armor stands are considered more like containers/interactive blocks
            allowed = isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_INTERACTIVE) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_INTERACTIVE);
        } else {
            // Other entities follow the build permission
            allowed = isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_BUILD) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_BUILD);
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage("§c[LandClaims] You don't have permission to interact with this here.");
        }
    }

    // In ClaimListener.java
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractSign(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if it's any type of sign
        if (block.getState() instanceof Sign) {
            Player player = event.getPlayer();
            Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());

            if (claim != null && claim.getFlag(ClaimFlag.SUPPRESS_SIGN_MESSAGES)) {
                // If player has bypass permission, don't suppress
                if (!player.hasPermission("landclaims.signs.bypass")) {
                    // Check if it's a chest shop sign
                    if (isChestShopSign((Sign) block.getState())) {
                        // Allow the interaction without message
                        event.setCancelled(false);
                        return; // Exit early to prevent any messages
                    }
                }
            }
        }
    }
    private boolean isChestShopSign(Sign sign) {
        String[] lines = sign.getLines();

        // First line should be a player name
        if (lines[0] == null || lines[0].isEmpty()) return false;

        // Second line should contain B or S followed by numbers
        if (lines[1] != null) {
            String line = lines[1].trim();
            if (!line.startsWith("B") && !line.startsWith("S")) return false;

            // Remove B and S and check if remaining characters are numbers
            String priceStr = line.replaceAll("[BS:]", "");
            try {
                Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }

        // Third line should be a number
        if (lines[2] == null || lines[2].isEmpty()) return false;
        try {
            Integer.parseInt(lines[2].trim());
        } catch (NumberFormatException e) {
            return false;
        }

        // Fourth line should be item name
        return lines[3] != null && !lines[3].isEmpty();
    }


    private boolean isShopSign(Block block) {
        if (!(block.getState() instanceof Sign)) return false;

        Sign sign = (Sign) block.getState();
        String[] lines = sign.getLines();

        // Check if line 1 is a valid player name
        if (lines[0] == null || lines[0].isEmpty()) return false;

        // Check line 2 for valid shop format
        if (lines[1] != null) {
            String priceLine = lines[1].trim();
            // Check for valid formats: "B14", "B15:S16", "S1"
            if (priceLine.startsWith("B") || priceLine.startsWith("S") || priceLine.contains(":S")) {
                // Check line 3 for quantity (should be a number)
                if (lines[2] != null && !lines[2].isEmpty()) {
                    try {
                        Integer.parseInt(lines[2]);
                        // Check line 4 for item name
                        return lines[3] != null && !lines[3].isEmpty();
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOtherSpecialSign(Block block) {
        if (!(block.getState() instanceof Sign)) return false;

        Sign sign = (Sign) block.getState();
        String[] lines = sign.getLines();

        // Example checks for different types of special signs
        return isElevatorSign(lines) ||
                isTeleportSign(lines) ||
                isCommandSign(lines);
    }

    private boolean isElevatorSign(String[] lines) {
        return lines[0] != null && (
                lines[0].equalsIgnoreCase("[Elevator]") ||
                        lines[0].equalsIgnoreCase("[Up]") ||
                        lines[0].equalsIgnoreCase("[Down]")
        );
    }

    private boolean isTeleportSign(String[] lines) {
        return lines[0] != null && lines[0].equalsIgnoreCase("[Teleport]");
    }

    private boolean isCommandSign(String[] lines) {
        return lines[0] != null && lines[0].equalsIgnoreCase("[Command]");
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        try {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();
            Block block = event.getClickedBlock();


            // Check for admin claiming tool
            if (item.getType() == Material.GOLDEN_AXE &&
                    item.hasItemMeta() &&
                    item.getItemMeta().getDisplayName().equals("§6Admin Claiming Tool")) {

                if (!player.hasPermission("landclaims.admin")) {
                    player.sendMessage("§c[LandClaims] You don't have permission to use this tool.");
                    return;
                }

                event.setCancelled(true);
                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock == null) return;

                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    plugin.getSelectionManager().handleAdminFirstPoint(player, clickedBlock.getLocation());
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    plugin.getSelectionManager().handleAdminSecondPoint(player, clickedBlock.getLocation());
                }
                return;
            }

            // Normal claiming tool
            if (item.getType() == Material.GOLDEN_SHOVEL) {
                event.setCancelled(true);

                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock == null) return;

                // Check if player is in modification mode
                if (plugin.getClaimModifier().getActiveSession(player) != null) {
                    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                        plugin.getClaimModifier().handleClick(player, clickedBlock.getLocation());
                    }
                    return;
                }

                // Check for shift-click to start modification
                if (player.isSneaking()) {
                    Claim claim = plugin.getClaimManager().getClaimAt(clickedBlock.getLocation());
                    if (claim != null &&
                            (claim.getOwner().equals(player.getUniqueId()) ||
                                    player.hasPermission("landclaims.admin"))) {
                        plugin.getClaimModifier().startModification(player, clickedBlock.getLocation());
                    }
                    return;
                }

                // Normal claim creation handling
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    plugin.getSelectionManager().handleFirstPoint(player, clickedBlock.getLocation());
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    plugin.getSelectionManager().handleSecondPoint(player, clickedBlock.getLocation());
                }
            } else if (block != null && (block.getType() == Material.FARMLAND || isCrop(block.getType()))) {
                Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());

                if (claim != null) {
                    boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;

                    // Use the build flags to determine permission
                    if (isTrusted && !claim.getFlag(ClaimFlag.TRUSTED_BUILD)) {
                        event.setCancelled(true);
                    } else if (!isTrusted && !claim.getFlag(ClaimFlag.UNTRUSTED_BUILD)) {
                        event.setCancelled(true);
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error in claim interaction: " + e.getMessage());
            e.printStackTrace();
        }
    }






    @EventHandler(priority = EventPriority.HIGH)
    public void onVillagerTrade(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof org.bukkit.entity.Villager) {
            Player player = event.getPlayer();
            if (!plugin.getProtectionManager().canTradeWithVillager(player, event.getRightClicked().getLocation())) {
                event.setCancelled(true);
                player.sendMessage("§c[LandClaims] You don't have permission to trade with villagers here.");
            }
        }
    }
    private String getClaimOwnerDisplay(Claim claim) {
        if (claim.isAdminClaim()) {
            return plugin.getConfig().getString("server.admin-claim-prefix", "§4[Admin] ") +
                    plugin.getConfig().getString("server.name", "Server");
        }
        return Bukkit.getOfflinePlayer(claim.getOwner()).getName();
    }


    @EventHandler(priority = EventPriority.LOWEST) // Change to LOWEST to run before other plugins
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        // Check if it's a ChestShop container first
        if (plugin.getProtectionManager().isChestShopContainer(block)) {
            // If player can't interact with the shop container, cancel the event
            if (!plugin.getProtectionManager().canInteract(player, block)) {
                event.setCancelled(true);
                return;
            }
            // If they can interact (they're the owner or admin), let the event continue
            return;
        }

        // Handle regular claim protection
        if (!plugin.getProtectionManager().canInteract(player, block)) {
            event.setCancelled(true);
            player.sendMessage("§c[LandClaims] You don't have permission to interact with this block.");
        }
    }
    private boolean isShopSignInteraction(Block block, Player player) {
        // Check if the player is clicking a sign
        if (!(block.getState() instanceof Sign)) {
            return false;
        }

        Sign sign = (Sign) block.getState();
        String[] lines = sign.getLines();

        // Check if it's a valid shop sign
        if (lines.length < 4) {
            return false;
        }

        // Check if the second line contains B or S with numbers (ChestShop format)
        String priceLine = lines[1];
        return priceLine != null && priceLine.matches("^[BS]\\d+(?::[BS]\\d+)?$");
    }





    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // Skip if using claiming tool
        if (player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_SHOVEL) {
            return;
        }

        // Check for item frames, armor stands, and other interactable entities
        if (entity instanceof ItemFrame || entity instanceof ArmorStand) {
            Claim claim = plugin.getClaimManager().getClaimAt(entity.getLocation());
            if (claim == null) return;

            // Owner always has access
            if (claim.getOwner().equals(player.getUniqueId())) return;

            // Admin bypass
            if (player.hasPermission("landclaims.admin") &&
                    plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
                return;
            }

            boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
            boolean allowed = isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_INTERACTIVE) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_INTERACTIVE);

            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage("§c[LandClaims] You don't have permission to interact with this.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        // Skip if the damaged entity is a player (PvP is handled elsewhere)
        if (event.getEntity() instanceof Player) {
            return;
        }

        // Get the attacker
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null) {
            return; // Not caused by a player
        }

        // Check claim permissions
        Claim claim = plugin.getClaimManager().getClaimAt(event.getEntity().getLocation());
        if (claim == null) return;

        // Owner always has access
        if (claim.getOwner().equals(attacker.getUniqueId())) return;

        // Admin bypass
        if (attacker.hasPermission("landclaims.admin") &&
                plugin.getClaimManager().isAdminBypassing(attacker.getUniqueId())) {
            return;
        }

        boolean isTrusted = claim.getTrustLevel(attacker.getUniqueId()) != null;
        boolean allowed = isTrusted ?
                claim.getFlag(ClaimFlag.TRUSTED_INTERACTIVE) :
                claim.getFlag(ClaimFlag.UNTRUSTED_INTERACTIVE);

        if (!allowed) {
            event.setCancelled(true);
            attacker.sendMessage("§c[LandClaims] You don't have permission to damage entities here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonEggInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        if (event.getClickedBlock().getType() == Material.DRAGON_EGG) {
            Player player = event.getPlayer();
            Claim claim = plugin.getClaimManager().getClaimAt(event.getClickedBlock().getLocation());

            if (claim == null) return;

            // Owner always has access
            if (claim.getOwner().equals(player.getUniqueId())) return;

            // Admin bypass
            if (player.hasPermission("landclaims.admin") &&
                    plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
                return;
            }

            boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
            boolean allowed = isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_INTERACTIVE) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_INTERACTIVE);

            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage("§c[LandClaims] You don't have permission to interact with the dragon egg here.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = null;

        // Check for projectile damage (bows, crossbows, tridents, etc.)
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }
        // Direct player damage
        else if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        }

        // If we found an attacker (either direct or through projectile)
        if (attacker != null) {
            Claim claim = plugin.getClaimManager().getClaimAt(victim.getLocation());
            if (claim != null) {
                if (!claim.getFlag(ClaimFlag.PVP)) {
                    event.setCancelled(true);
                    attacker.sendMessage("§c[LandClaims] PvP is disabled in this claim.");
                    return;
                }
            } else {
                // Check world settings for unclaimed areas
                if (!plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.PVP)) {
                    event.setCancelled(true);
                    attacker.sendMessage("§c[LandClaims] PvP is disabled in this area.");
                    return;
                }
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerAccess(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Container)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if it's a ChestShop container
        if (plugin.getProtectionManager().isChestShopContainer(block)) {
            // Find the shop sign
            Sign shopSign = findAttachedShopSign(block);
            if (shopSign != null) {
                String ownerName = ChatColor.stripColor(shopSign.getLine(0));
                // Only allow access if player is the owner or has admin bypass
                if (!player.getName().equals(ownerName)) {
                    event.setCancelled(true);
                    player.sendMessage("§c[LandClaims] This container belongs to a ChestShop. Use the shop sign to trade.");
                    return;
                }
            }
        }
    }

    private Sign findAttachedShopSign(Block container) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = container.getRelative(face);
            if (relative.getState() instanceof Sign) {
                Sign sign = (Sign) relative.getState();
                if (isChestShopSign(sign)) {
                    return sign;
                }
            }
        }
        return null;
    }



    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getEntity().getShooter();
        boolean isPvPPotion = false;

        // Check if the potion has harmful effects
        for (PotionEffect effect : event.getPotion().getEffects()) {
            if (isHarmfulEffect(effect.getType())) {
                isPvPPotion = true;
                break;
            }
        }

        if (isPvPPotion) {
            for (LivingEntity entity : event.getAffectedEntities()) {
                if (entity instanceof Player && entity != attacker) {
                    Player victim = (Player) entity;
                    Claim claim = plugin.getClaimManager().getClaimAt(victim.getLocation());

                    if (claim != null && !claim.getFlag(ClaimFlag.PVP)) {
                        event.setIntensity(entity, 0);
                        attacker.sendMessage("§c[LandClaims] PvP is disabled in this claim.");
                    } else if (claim == null && !plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.PVP)) {
                        event.setIntensity(entity, 0);
                        attacker.sendMessage("§c[LandClaims] PvP is disabled in this area.");
                    }
                }
            }
        }
    }


    private boolean isHarmfulEffect(PotionEffectType type) {
        return Arrays.asList(
                PotionEffectType.NAUSEA,
                PotionEffectType.POISON,
                PotionEffectType.WEAKNESS,
                PotionEffectType.SLOWNESS,
                PotionEffectType.BLINDNESS,
                PotionEffectType.WITHER
        ).contains(type);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplodeDamage(org.bukkit.event.entity.EntityExplodeEvent event) {
        // Handle ghast fireballs and other projectiles from hostile mobs
        if (event.getEntity() instanceof org.bukkit.entity.Fireball) {
            org.bukkit.entity.Fireball fireball = (org.bukkit.entity.Fireball) event.getEntity();

            // Check if the projectile is from a mob we want to control (Ghast or Blaze)
            if (fireball.getShooter() instanceof Ghast || fireball.getShooter() instanceof Blaze) {
                Claim claim = plugin.getClaimManager().getClaimAt(event.getLocation());

                // Check claim settings
                if (claim != null && !claim.getFlag(ClaimFlag.MOB_GRIEFING)) {
                    event.setCancelled(true);
                    return;
                }

                // Check global settings for unclaimed areas
                if (claim == null && !plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.MOB_GRIEFING)) {
                    event.setCancelled(true);
                    return;
                }

                // If explosion is allowed, still filter blocks by claim boundaries
                event.blockList().removeIf(block -> {
                    Claim blockClaim = plugin.getClaimManager().getClaimAt(block.getLocation());
                    if (blockClaim != null) {
                        return !blockClaim.getFlag(ClaimFlag.MOB_GRIEFING);
                    }
                    return !plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.MOB_GRIEFING);
                });
            }
        }
    }




    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        // Handle blaze fireballs that might set blocks on fire
        if (event.getEntity() instanceof org.bukkit.entity.SmallFireball &&
                event.getEntity().getShooter() instanceof Blaze) {

            // Get the location where the projectile hit
            Location hitLocation = event.getEntity().getLocation();
            Claim claim = plugin.getClaimManager().getClaimAt(hitLocation);

            // Check claim settings
            if (claim != null && !claim.getFlag(ClaimFlag.MOB_GRIEFING)) {
                event.setCancelled(true);

                // Also extinguish any fire that might have been created
                if (event.getHitBlock() != null) {
                    Block hitBlock = event.getHitBlock();
                    // Check adjacent blocks for fire
                    for (BlockFace face : BlockFace.values()) {
                        Block adjacent = hitBlock.getRelative(face);
                        if (adjacent.getType() == Material.FIRE) {
                            adjacent.setType(Material.AIR);
                        }
                    }
                }
            }
            // Check global settings for unclaimed areas
            else if (claim == null && !plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.MOB_GRIEFING)) {
                event.setCancelled(true);

                // Also extinguish any fire that might have been created
                if (event.getHitBlock() != null) {
                    Block hitBlock = event.getHitBlock();
                    // Check adjacent blocks for fire
                    for (BlockFace face : BlockFace.values()) {
                        Block adjacent = hitBlock.getRelative(face);
                        if (adjacent.getType() == Material.FIRE) {
                            adjacent.setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }




    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (event.getEntity() == null) return;

        Claim claim = plugin.getClaimManager().getClaimAt(event.getLocation());

        // Handle different explosion types
        if (event.getEntity() instanceof Creeper) {
            if (claim != null && !claim.getFlag(ClaimFlag.CREEPER_DAMAGE)) {
                event.setCancelled(true);
                return;
            }
            if (claim == null && !plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.CREEPER_DAMAGE)) {
                event.setCancelled(true);
                return;
            }
        } else if (event.getEntity() instanceof TNTPrimed || event.getEntity() instanceof ExplosiveMinecart) {
            if (claim != null && !claim.getFlag(ClaimFlag.EXPLOSIONS)) {
                event.setCancelled(true);
                return;
            }
            if (claim == null && !plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.EXPLOSIONS)) {
                event.setCancelled(true);
                return;
            }
        }
    }


    // Add these new event handlers to ClaimListener.java:



    @EventHandler(priority = EventPriority.HIGH)
    public void onEndermanBlockChange(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Enderman)) {
            return;
        }

        // Check if this is a pickup (block -> AIR) or placement (AIR -> block)
        Block block = event.getBlock();

        // Get claim at the block location
        Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());

        // Check mob griefing permission
        if (claim != null) {
            // If in a claim, use claim's MOB_GRIEFING flag
            if (!claim.getFlag(ClaimFlag.MOB_GRIEFING)) {
                event.setCancelled(true);
                return;
            }
        } else {
            // If in wilderness, use global MOB_GRIEFING setting
            if (!plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.MOB_GRIEFING)) {
                event.setCancelled(true);
                return;
            }
        }

        // If it's a pickup event, track it (for any other mechanics that might need this info)
        if (event.getTo() == Material.AIR) {
            event.setCancelled(true);
            plugin.trackEndermanPickup(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFireSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE) {
            if (!plugin.getProtectionManager().canFireSpread(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (!plugin.getProtectionManager().canPistonsWork(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!event.isSticky()) return;

        Claim pistonClaim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());

        for (Block block : event.getBlocks()) {
            Claim movingBlockClaim = plugin.getClaimManager().getClaimAt(block.getLocation());

            // Cancel if piston or blocks cross claim boundaries
            if ((pistonClaim == null && movingBlockClaim != null) ||
                    (pistonClaim != null && movingBlockClaim != null && !pistonClaim.equals(movingBlockClaim))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        if (!plugin.getProtectionManager().canPistonsWork(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        Claim pistonClaim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());

        for (Block block : event.getBlocks()) {
            Claim movingBlockClaim = plugin.getClaimManager().getClaimAt(block.getLocation());
            Claim destinationClaim = plugin.getClaimManager().getClaimAt(block.getRelative(event.getDirection()).getLocation());

            // Cancel if piston or blocks cross claim boundaries
            if ((pistonClaim == null && movingBlockClaim != null) ||
                    (pistonClaim != null && movingBlockClaim != null && !pistonClaim.equals(movingBlockClaim)) ||
                    (pistonClaim != null && destinationClaim != null && !pistonClaim.equals(destinationClaim))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryMoveItem(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Container &&
                event.getDestination().getHolder() instanceof Container) {

            Container sourceContainer = (Container) event.getSource().getHolder();
            if (!plugin.getProtectionManager().canHoppersWork(sourceContainer.getLocation())) {
                event.setCancelled(true);
            }
        }
    }



    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockRedstone(org.bukkit.event.block.BlockRedstoneEvent event) {
        if (!plugin.getProtectionManager().canRedstoneWork(event.getBlock().getLocation())) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTNTExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.TNTPrimed ||
                event.getEntity() instanceof org.bukkit.entity.minecart.ExplosiveMinecart) {

            event.blockList().removeIf(block -> {
                Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());
                if (claim != null) {
                    return !claim.getFlag(ClaimFlag.EXPLOSIONS);
                }
                // Use global settings for unclaimed areas
                return !plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.EXPLOSIONS);
            });
        }
    }

    // For monster spawning control
    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster || event.getEntity() instanceof Phantom) {
            if (!plugin.getProtectionManager().canMobsSpawn(event.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    // For leaf decay control
    @EventHandler(priority = EventPriority.HIGH)
    public void onLeavesDecay(org.bukkit.event.block.LeavesDecayEvent event) {
        if (!plugin.getProtectionManager().canLeavesDecay(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }





}
