// ProtectionManager.java
package org.example;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.Plugin;

public class ProtectionManager {
    private final Main plugin;

    public ProtectionManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean canBuild(Player player, Block block) {
        Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claim != null) {
            if (claim.isAdminClaim()) {
                boolean allowed = player.hasPermission("landclaims.admin");
                if (!allowed) {
                    player.sendMessage("§c[LandClaims] This is an admin claim - you cannot build here.");
                }
                return allowed;
            }

            // Owner always has access
            if (claim.getOwner().equals(player.getUniqueId())) return true;

            // Admin bypass
            if (player.hasPermission("landclaims.admin") &&
                    plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
                return true;
            }

            boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
            boolean allowed = isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_BUILD) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_BUILD);

            if (!allowed) {
                player.sendMessage("§c[LandClaims] You don't have permission to build in this claim.");
            }
            return allowed;
        } else {
            // Wild area protection logic
            boolean allowed = plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.WILD_BUILD);
            if (!allowed) {
                player.sendMessage("§c[LandClaims] Building is not allowed in wilderness areas.");
            }
            return allowed;
        }
    }


    public boolean shouldShowSignMessage(Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        return claim == null || !claim.getFlag(ClaimFlag.SUPPRESS_SIGN_MESSAGES);
    }


    private boolean isChestShopSign(Sign sign) {
        String[] lines = sign.getLines();
        if (lines.length < 4) return false;

        // Line 2 should contain B or S followed by numbers
        String priceLine = lines[1];
        if (priceLine == null || priceLine.isEmpty()) return false;

        // Check for ChestShop price format
        return priceLine.matches("^[BS]\\d+(?::[BS]\\d+)?$");
    }

    public boolean isChestShopContainer(Block block) {
        // Check if ChestShop plugin is present
        if (plugin.getServer().getPluginManager().getPlugin("ChestShop") == null) {
            return false;
        }

        // Only check if it's a container
        if (!(block.getState() instanceof Container)) {
            return false;
        }

        // Check for attached shop signs
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = block.getRelative(face);
            if (relative.getState() instanceof Sign) {
                Sign sign = (Sign) relative.getState();
                if (isChestShopSign(sign)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isPartOfChestShop(Block container) {
        if (!(container.getState() instanceof Container)) {
            return false;
        }

        // Check all adjacent faces for shop signs
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = container.getRelative(face);
            if (relative.getState() instanceof Sign) {
                Sign sign = (Sign) relative.getState();
                if (isChestShopSign(sign)) {
                    return true;
                }
            }
        }

        return false;
    }
    private Sign findShopSign(Block container) {
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

    private boolean isChestShopAdminBypass(Player player) {
        // Check if player has the permission and if they're in bypass mode
        if (player.hasPermission("chestshop.admin.chestbypass")) {
            Plugin chestShop = plugin.getServer().getPluginManager().getPlugin("ChestShop");
            if (chestShop != null) {
                // Store bypass state in metadata
                return player.hasMetadata("chestshop.bypass");
            }
        }
        return false;
    }

    public boolean canInteract(Player player, Block block) {
        // First check if it's a ChestShop container
        if (isChestShopContainer(block)) {
            // Find the shop sign
            Sign shopSign = findShopSign(block);
            if (shopSign != null) {
                // Get the shop owner from the sign (first line)
                String ownerName = ChatColor.stripColor(shopSign.getLine(0));

                // Only allow access if:
                // 1. Player is the shop owner, or
                // 2. Player has admin bypass permission AND is in bypass mode
                boolean isOwner = player.getName().equals(ownerName);
                boolean hasAdminBypass = player.hasPermission("chestshop.admin.chestbypass") &&
                        hasChestShopBypass(player);

                if (!isOwner && !hasAdminBypass) {
                    player.sendMessage("§c[LandClaims] This container belongs to a ChestShop. Use the shop sign to trade.");
                    return false;
                }
                return true;
            }
            // If we found a shop container but no sign, deny access to be safe
            return false;
        }

        // Regular claim container checks...
        Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claim == null) return true;

        // Owner always has access
        if (claim.getOwner().equals(player.getUniqueId())) return true;

        // Admin bypass
        if (player.hasPermission("landclaims.admin") &&
                plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
            return true;
        }

        boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
        String blockType = block.getType().name();

        // Check containers
        if (blockType.contains("CHEST") ||
                blockType.contains("FURNACE") ||
                blockType.contains("BARREL") ||
                blockType.contains("SHULKER") ||
                blockType.contains("DISPENSER") ||
                blockType.contains("DROPPER") ||
                blockType.contains("HOPPER")) {
            return isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_CONTAINERS) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_CONTAINERS);
        }

        // Check doors
        if (blockType.contains("DOOR") ||
                blockType.contains("GATE") ||
                blockType.contains("TRAPDOOR") ||
                blockType.contains("FENCE_GATE")) {
            return isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_DOORS) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_DOORS);
        }

        // For other interactions, require at least container access
        return isTrusted ?
                claim.getFlag(ClaimFlag.TRUSTED_CONTAINERS) :
                claim.getFlag(ClaimFlag.UNTRUSTED_CONTAINERS);
    }
    private boolean hasChestShopBypass(Player player) {
        try {
            Plugin chestShop = plugin.getServer().getPluginManager().getPlugin("ChestShop");
            if (chestShop != null) {
                // Check for bypass metadata
                return player.hasMetadata("chestshop.bypass");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking ChestShop bypass: " + e.getMessage());
        }
        return false;
    }

    public boolean canTradeWithVillager(Player player, Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        if (claim == null) return true;

        // Owner always has access
        if (claim.getOwner().equals(player.getUniqueId())) return true;

        // Admin bypass
        if (player.hasPermission("landclaims.admin") &&
                plugin.getClaimManager().isAdminBypassing(player.getUniqueId())) {
            return true;
        }

        boolean isTrusted = claim.getTrustLevel(player.getUniqueId()) != null;
        return isTrusted ?
                claim.getFlag(ClaimFlag.TRUSTED_VILLAGER_TRADING) :
                claim.getFlag(ClaimFlag.UNTRUSTED_VILLAGER_TRADING);
    }

    public boolean canPvP(Player attacker, Player victim) {
        Claim claim = plugin.getClaimManager().getClaimAt(victim.getLocation());
        if (claim != null) {
            return claim.getFlag(ClaimFlag.PVP);
        }
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.PVP);
    }

    public boolean canMobGrief(Entity entity, Block block) {
        Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claim != null) {
            return claim.getFlag(ClaimFlag.MOB_GRIEFING);
        }
        // Use global settings for unclaimed areas
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.MOB_GRIEFING);
    }

    public boolean canMobsSpawn(Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        if (claim != null) {
            return claim.getFlag(ClaimFlag.MONSTERS);
        }
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.MONSTERS);
    }

    public boolean canLeavesDecay(Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        if (claim != null) {
            return claim.getFlag(ClaimFlag.LEAF_DECAY);
        }
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.LEAF_DECAY);
    }

    public boolean canRedstoneWork(Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        if (claim != null) {
            return claim.getFlag(ClaimFlag.REDSTONE);
        }
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.REDSTONE);
    }

    public boolean canPistonsWork(Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        if (claim != null) {
            return claim.getFlag(ClaimFlag.PISTONS);
        }
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.PISTONS);
    }

    public boolean canHoppersWork(Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        if (claim != null) {
            return claim.getFlag(ClaimFlag.HOPPERS);
        }
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.HOPPERS);
    }

    public boolean canFireSpread(Location location) {
        Claim claim = plugin.getClaimManager().getClaimAt(location);
        if (claim != null) {
            return claim.getFlag(ClaimFlag.FIRE_SPREAD);
        }
        return plugin.getWorldSettingsManager().getGlobalFlag(ClaimFlag.FIRE_SPREAD);
    }

    private boolean isOwnerOrAdmin(Player player, Claim claim) {
        return claim.getOwner().equals(player.getUniqueId()) ||
                (player.hasPermission("landclaims.admin") &&
                        plugin.getClaimManager().isAdminBypassing(player.getUniqueId()));
    }

    private boolean isTrusted(Player player, Claim claim) {
        return claim.getTrustLevel(player.getUniqueId()) != null;
    }
}
