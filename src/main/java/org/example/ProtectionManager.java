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

            // Check trust level first to determine building permission
            TrustLevel trustLevel = claim.getTrustLevel(player.getUniqueId());
            if (trustLevel != null && trustLevel.ordinal() >= TrustLevel.BUILD.ordinal()) {
                // BUILD or higher trust level allows building
                return claim.getFlag(ClaimFlag.TRUSTED_BUILD);
            } else if (trustLevel != null) {
                // Has trust but not BUILD level
                boolean allowed = claim.getFlag(ClaimFlag.TRUSTED_BUILD);
                if (!allowed) {
                    player.sendMessage("§c[LandClaims] You don't have permission to build in this claim.");
                }
                return allowed;
            } else {
                // Not trusted at all
                boolean allowed = claim.getFlag(ClaimFlag.UNTRUSTED_BUILD);
                if (!allowed) {
                    player.sendMessage("§c[LandClaims] You don't have permission to build in this claim.");
                }
                return allowed;
            }
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
    private Sign findShopSign(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = block.getRelative(face);
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
    private boolean isInteractiveBlock(Material type) {
        return type.name().contains("PRESSURE_PLATE") ||
                type.name().contains("BUTTON") ||
                type == Material.LEVER ||
                type == Material.TRIPWIRE ||
                type == Material.TRIPWIRE_HOOK ||
                type == Material.DAYLIGHT_DETECTOR ||
                type == Material.TARGET;
    }

    public boolean canInteractWithEntity(Player player, Entity entity) {
        Claim claim = plugin.getClaimManager().getClaimAt(entity.getLocation());
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
                claim.getFlag(ClaimFlag.TRUSTED_INTERACTIVE) :
                claim.getFlag(ClaimFlag.UNTRUSTED_INTERACTIVE);
    }

    public boolean canDamageEntity(Player player, Entity entity) {
        // For most entities, use the same logic as interaction
        return canInteractWithEntity(player, entity);
    }

    public boolean canInteract(Player player, Block block) {
        // Skip if using claiming tool
        if (player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_SHOVEL) {
            return true;
        }

        // Always allow access to these blocks
        if (block.getType() == Material.ENDER_CHEST) {
            return true;
        }
        if (block.getType() == Material.CRAFTING_TABLE) {
            return true;
        }

        // First check if it's a ChestShop container
        if (isChestShopContainer(block)) {
            // Find the shop sign
            Sign shopSign = findShopSign(block);
            if (shopSign != null) {
                String ownerName = ChatColor.stripColor(shopSign.getLine(0));
                // Only allow access if player is the shop owner or has admin bypass
                return player.getName().equals(ownerName) ||
                        (player.hasPermission("chestshop.admin.chestbypass") &&
                                hasChestShopBypass(player));
            }
            return false; // If we can't find the sign, deny access to be safe
        }

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

        // Check for interactive blocks
        Material type = block.getType();
        if (isInteractiveBlock(type)) {
            return isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_INTERACTIVE) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_INTERACTIVE);
        }

        // Check containers
        if (block.getState() instanceof Container) {
            return isTrusted ?
                    claim.getFlag(ClaimFlag.TRUSTED_CONTAINERS) :
                    claim.getFlag(ClaimFlag.UNTRUSTED_CONTAINERS);
        }

        // Check doors
        if (type.name().contains("DOOR") ||
                type.name().contains("GATE") ||
                type.name().contains("TRAPDOOR") ||
                type.name().contains("FENCE_GATE")) {
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
