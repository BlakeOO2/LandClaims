package org.example;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.WebApp;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BlueMapIntegration {
    private final Main plugin;
    private BlueMapAPI blueMap;
    private Map<String, MarkerSet> markerSets;
    private Map<String, BlueMapMap> worldMaps;
    private WebApp webapp;
    private boolean enabled = false;
    private BukkitTask updateTask;

    // Configuration values
    private final int updateInterval;
    private final Color normalFillColor;
    private final Color normalBorderColor;
    private final Color adminFillColor;
    private final Color adminBorderColor;
    private final boolean showOwner;
    private final boolean showSize;
    private final boolean showTrusted;

    public BlueMapIntegration(Main plugin) {
        this.plugin = plugin;
        this.markerSets = new HashMap<>();
        this.worldMaps = new HashMap<>();

        // Load configuration
        FileConfiguration config = plugin.getConfig();
        this.updateInterval = config.getInt("bluemap.update-interval", 300);
        this.normalFillColor = parseColor(config.getString("bluemap.markers.normal-claims.fill-color", "#00FF0033"));
        this.normalBorderColor = parseColor(config.getString("bluemap.markers.normal-claims.border-color", "#00FF00FF"));
        this.adminFillColor = parseColor(config.getString("bluemap.markers.admin-claims.fill-color", "#FF000033"));
        this.adminBorderColor = parseColor(config.getString("bluemap.markers.admin-claims.border-color", "#FF0000FF"));
        this.showOwner = config.getBoolean("bluemap.markers.label.show-owner", true);
        this.showSize = config.getBoolean("bluemap.markers.label.show-size", true);
        this.showTrusted = config.getBoolean("bluemap.markers.label.show-trusted", true);

        if (Bukkit.getPluginManager().getPlugin("BlueMap") != null) {
            plugin.getLogger().info("[BlueMap] BlueMap plugin found, enabling integration...");
            BlueMapAPI.onEnable(this::onBlueMapReady);
            enabled = true;
        } else {
            plugin.getLogger().warning("[BlueMap] BlueMap plugin not found!");
        }
    }

    // In BlueMapIntegration.java
    private void onBlueMapReady(BlueMapAPI api) {
        try {
            plugin.getLogger().info("[BlueMap] API received, initializing...");
            this.blueMap = api;
            this.webapp = api.getWebApp();

            // Initialize maps for each world
            for (BlueMapMap map : api.getMaps()) {
                // Get world name from the map ID instead
                String worldName = map.getId().split(":")[0]; // Usually map IDs are in format "world:surface"
                worldMaps.put(worldName, map);

                // Create marker set for this world
                MarkerSet markerSet = MarkerSet.builder()
                        .label("Land Claims - " + worldName)
                        .defaultHidden(false)
                        .toggleable(true)
                        .build();

                map.getMarkerSets().put("claims_" + worldName, markerSet);
                markerSets.put(worldName, map.getMarkerSets().get("claims_" + worldName));

                plugin.getLogger().info("[BlueMap] Initialized map for world: " + worldName);
            }

            // Start update task
            startUpdateTask();

            // Initial update
            updateAllClaims();

        } catch (Exception e) {
            plugin.getLogger().severe("[BlueMap] Error during initialization: " + e.getMessage());
            e.printStackTrace();
            enabled = false;
        }
    }


    private void addClaimMarker(Claim claim) {
        String worldName = claim.getWorld();
        BlueMapMap map = worldMaps.get(worldName);
        MarkerSet markerSet = markerSets.get(worldName);

        if (map == null || markerSet == null) {
            plugin.getLogger().warning("[BlueMap] No map/markerset found for world: " + worldName);
            return;
        }

        try {
            Location corner1 = claim.getCorner1();
            Location corner2 = claim.getCorner2();

            String markerId = "claim_" + claim.getOwner().toString() + "_" +
                    corner1.getBlockX() + "_" + corner1.getBlockZ();

            Shape shape = Shape.createRect(
                    (float)corner1.getBlockX(), (float)corner1.getBlockZ(),
                    (float)corner2.getBlockX(), (float)corner2.getBlockZ()
            );

            ShapeMarker marker = ShapeMarker.builder()
                    .label(showOwner ? Bukkit.getOfflinePlayer(claim.getOwner()).getName() + "'s Claim" : "")
                    .shape(shape, 0.0f)
                    .fillColor(claim.isAdminClaim() ? adminFillColor : normalFillColor)
                    .lineColor(claim.isAdminClaim() ? adminBorderColor : normalBorderColor)
                    .lineWidth(2)
                    .depthTestEnabled(true)
                    .build();

            markerSet.getMarkers().put(markerId, marker);

        } catch (Exception e) {
            plugin.getLogger().warning("[BlueMap] Error adding claim marker: " + e.getMessage());
        }
    }



    // In BlueMapIntegration.java

    public void reload() {
        plugin.getLogger().info("[BlueMap] Reloading BlueMap integration...");

        // Clear existing markers
        for (MarkerSet markerSet : markerSets.values()) {
            markerSet.getMarkers().clear();
        }

        if (!enabled) {
            plugin.getLogger().info("[BlueMap] Integration was disabled, attempting to re-enable...");
            if (Bukkit.getPluginManager().getPlugin("BlueMap") != null) {
                BlueMapAPI.onEnable(this::onBlueMapReady);
                enabled = true;
            }
        } else {
            plugin.getLogger().info("[BlueMap] Updating all claims...");
            updateAllClaims();
        }
    }



    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }


        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::updateAllClaims,
                updateInterval * 20L,
                updateInterval * 20L
        );
    }

    public void updateAllClaims() {
        if (!enabled || blueMap == null || markerSets.isEmpty()) {
            plugin.getLogger().warning("[BlueMap] Cannot update claims - integration not properly initialized");
            return;
        }

        try {
            // Clear existing markers from all marker sets
            for (MarkerSet markerSet : markerSets.values()) {
                markerSet.getMarkers().clear();
            }

            // Add markers for all claims
            Set<Claim> claims = plugin.getClaimManager().getAllClaims();
            plugin.getLogger().info("[BlueMap] Found " + claims.size() + " claims to display");

            for (Claim claim : claims) {
                addClaimMarker(claim);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("[BlueMap] Error updating claims: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Color parseColor(String colorStr) {
        try {
            // The Color constructor can directly accept CSS-style hex colors
            return new Color(colorStr);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid color format: " + colorStr + ". Using default.");
            return new Color("#FFFFFF"); // White color
        }
    }






    public void updateClaim(Claim claim) {
        if (!enabled || blueMap == null) return;

        String worldName = claim.getWorld();
        MarkerSet markerSet = markerSets.get(worldName);

        if (markerSet != null) {
            String markerId = "claim_" + claim.getOwner().toString() + "_" +
                    claim.getCorner1().getBlockX() + "_" +
                    claim.getCorner1().getBlockZ();

            markerSet.getMarkers().remove(markerId);
            addClaimMarker(claim);
        }
    }

    public void removeClaim(Claim claim) {
        if (!enabled || blueMap == null) return;

        String worldName = claim.getWorld();
        MarkerSet markerSet = markerSets.get(worldName);

        if (markerSet != null) {
            String markerId = "claim_" + claim.getOwner().toString() + "_" +
                    claim.getCorner1().getBlockX() + "_" +
                    claim.getCorner1().getBlockZ();

            markerSet.getMarkers().remove(markerId);
        }
    }

    public void disable() {
        for (MarkerSet markerSet : markerSets.values()) {
            markerSet.getMarkers().clear();
        }
        markerSets.clear();
        worldMaps.clear();
        enabled = false;
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        plugin.getLogger().info("[BlueMap] Integration disabled");
    }

    public boolean isEnabled() {
        return enabled && blueMap != null && !markerSets.isEmpty();
    }



}
