// ProtectionManager.java
package org.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;

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

    public boolean canInteract(Player player, Block block) {
        // Check if it's a sign first
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());

            // If in a claim with sign messages suppressed and it's a chest shop sign
            if (claim != null &&
                    claim.getFlag(ClaimFlag.SUPPRESS_SIGN_MESSAGES) &&
                    isChestShopSign(sign)) {
                return true; // Allow interaction without message
            }
        }

        // Rest of your existing canInteract logic...
        if (block.getType() == Material.ENDER_CHEST) {
            return true;
        }
        if (block.getType() == Material.CRAFTING_TABLE) {
            return true;
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
