package org.example;
// Claim.java
import org.bukkit.Location;
import java.util.*;
import java.util.ArrayList;
import java.util.List;

public class Claim {
    private UUID owner;
    private String world;
    private Location corner1;
    private Location corner2;
    private Map<UUID, TrustLevel> trustedPlayers;
    private Map<ClaimFlag, Boolean> flags;
    private boolean adminClaim;

    public Map<UUID, TrustLevel> getTrustedPlayers() {
        return new HashMap<>(trustedPlayers);
    }
    public Map<ClaimFlag, Boolean> getFlags() {
        return new HashMap<>(flags);
    }
    public Claim(UUID owner, Location corner1, Location corner2) {
        this.owner = owner;
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.world = corner1.getWorld().getName();
        this.trustedPlayers = new HashMap<>();
        this.flags = new EnumMap<>(ClaimFlag.class);
        initializeDefaultFlags();
    }

    private void initializeDefaultFlags() {
        for (ClaimFlag flag : ClaimFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }
    public int getSize() {
        int length = Math.abs(corner1.getBlockX() - corner2.getBlockX()) + 1;
        int width = Math.abs(corner1.getBlockZ() - corner2.getBlockZ()) + 1;
        return length * width;
    }

    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(world)) return false;

        int x = location.getBlockX();
        int z = location.getBlockZ();

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public void setFlag(ClaimFlag flag, boolean value) {
        flags.put(flag, value);
    }

    public boolean getFlag(ClaimFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultValue());
    }

    public void setTrust(UUID player, TrustLevel level) {
        if (level == null) {
            trustedPlayers.remove(player);
        } else {
            trustedPlayers.put(player, level);
        }
    }

    public TrustLevel getTrustLevel(UUID player) {
        // Changed from getOrDefault to just get - this ensures unspecified players are NONE
        return trustedPlayers.get(player);
    }

    // Getters and setters
    public UUID getOwner() {
        return owner;
    }

    public String getWorld() {
        return world;
    }

    public Location getCorner1() {
        return corner1;
    }

    public Location getCorner2() {
        return corner2;
    }

    public void setCorner(boolean isFirstCorner, Location newLocation) {
        if (isFirstCorner) {
            this.corner1 = newLocation;
        } else {
            this.corner2 = newLocation;
        }
        // Ensure corners are properly aligned
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        corner1 = new Location(corner1.getWorld(), minX, corner1.getY(), minZ);
        corner2 = new Location(corner2.getWorld(), maxX, corner2.getY(), maxZ);
    }
    public void setOwner(UUID newOwner) {
        this.owner = newOwner;
    }
    public List<Location> getAllCorners() {
        List<Location> corners = new ArrayList<>();
        corners.add(corner1);
        corners.add(corner2);
        corners.add(new Location(corner1.getWorld(), corner1.getX(), corner1.getY(), corner2.getZ()));
        corners.add(new Location(corner1.getWorld(), corner2.getX(), corner1.getY(), corner1.getZ()));
        return corners;
    }
    public boolean isAdminClaim() {
        return adminClaim;
    }

    public void setAdminClaim(boolean adminClaim) {
        this.adminClaim = adminClaim;
    }

}
