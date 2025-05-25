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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlueMapIntegration {
    private final Main plugin;
    private BlueMapAPI blueMap;
    private MarkerSet markerSet;
    private WebApp webapp;
    private BlueMapMap map;
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
        plugin.getLogger().info("[BlueMap] Initializing BlueMap integration...");

        // Force enable in config if not set
        if (!plugin.getConfig().contains("bluemap.enabled")) {
            plugin.getConfig().set("bluemap.enabled", true);
            plugin.saveConfig();
        }

        if (Bukkit.getPluginManager().getPlugin("BlueMap") != null) {
            plugin.getLogger().info("[BlueMap] BlueMap plugin found, enabling integration...");
            BlueMapAPI.onEnable(this::onBlueMapReady);
            enabled = true;
        } else {
            plugin.getLogger().warning("[BlueMap] BlueMap plugin not found!");
        }



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


    }

    // In BlueMapIntegration.java
    private void onBlueMapReady(BlueMapAPI api) {
        try {
            plugin.getLogger().info("[BlueMap] API received, initializing...");
            this.blueMap = api;

            // Get the webapp
            this.webapp = api.getWebApp();
            if (webapp == null) {
                plugin.getLogger().warning("[BlueMap] Failed to get WebApp instance");
                return;
            }
            plugin.getLogger().info("[BlueMap] WebApp acquired successfully");

            // Get available maps
            if (api.getMaps().isEmpty()) {
                plugin.getLogger().warning("[BlueMap] No maps found in BlueMap!");
                return;
            }

            // Get first map
            this.map = api.getMaps().iterator().next();
            plugin.getLogger().info("[BlueMap] Using map: " + map.getId());

            // Create or get marker set
            Map<String, MarkerSet> markerSets = map.getMarkerSets();
            this.markerSet = markerSets.get("claims");

            if (this.markerSet == null) {
                plugin.getLogger().info("[BlueMap] Creating new marker set...");
                MarkerSet.Builder builder = MarkerSet.builder();
                builder.label("Land Claims");
                builder.defaultHidden(false);
                builder.toggleable(true);
                this.markerSet = builder.build();
                markerSets.put("claims", this.markerSet);
            }

            // Initial update
            updateAllClaims();
            plugin.getLogger().info("[BlueMap] Initial claim update completed");

            // Start periodic updates
            startUpdateTask();
            plugin.getLogger().info("[BlueMap] BlueMap integration initialized successfully!");

        } catch (Exception e) {
            plugin.getLogger().severe("[BlueMap] Error during initialization: " + e.getMessage());
            e.printStackTrace();
            enabled = false;
        }
    }



    // In BlueMapIntegration.java
    public void reload() {
        plugin.getLogger().info("[BlueMap] Reloading BlueMap integration...");

        // Clear existing markers
        if (markerSet != null) {
            plugin.getLogger().info("[BlueMap] Clearing existing markers...");
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
        if (!enabled || blueMap == null || markerSet == null || map == null) {
            plugin.getLogger().warning("[LandClaims][BlueMap] Cannot update claims - integration not properly initialized");
            plugin.getLogger().warning("[LandClaims][BlueMap] Enabled: " + enabled);
            plugin.getLogger().warning("[LandClaims][BlueMap] API: " + (blueMap != null));
            plugin.getLogger().warning("[LandClaims][BlueMap] MarkerSet: " + (markerSet != null));
            plugin.getLogger().warning("[LandClaims][BlueMap] Map: " + (map != null));
            return;
        }

        try {
            // Clear existing markers
            markerSet.getMarkers().clear();
            int count = 0;

            // Add markers for all claims
            for (Claim claim : plugin.getClaimManager().getAllClaims()) {
                addClaimMarker(claim);
                count++;
            }

            plugin.getLogger().info("[BlueMap] Updated " + count + " claim markers");
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

    private void addClaimMarker(Claim claim) {
        if (!enabled || blueMap == null || markerSet == null || map == null) return;

        try {
            // Get claim corners
            Location corner1 = claim.getCorner1();
            Location corner2 = claim.getCorner2();

            String markerId = "claim_" + claim.getOwner().toString() + "_" +
                    corner1.getBlockX() + "_" + corner1.getBlockZ();

            // Create shape - using float values
            Shape shape = Shape.createRect(
                    (float)corner1.getBlockX(), (float)corner1.getBlockZ(),
                    (float)corner2.getBlockX(), (float)corner2.getBlockZ()
            );

            // Create marker with both fill and line colors
            ShapeMarker marker = ShapeMarker.builder()
                    .label(showOwner ? Bukkit.getOfflinePlayer(claim.getOwner()).getName() + "'s Claim" : "")
                    .shape(shape, 0.0f)
                    .fillColor(claim.isAdminClaim() ? adminFillColor : normalFillColor)  // Fill color for the area
                    .lineColor(claim.isAdminClaim() ? adminBorderColor : normalBorderColor)  // Color for the border
                    .lineWidth(2)  // Width of the border line
                    .depthTestEnabled(true)  // Make it visible through terrain
                    .build();

            // Add detail popup
            StringBuilder detail = new StringBuilder();
            if (showSize) {
                detail.append("Size: ").append(claim.getSize()).append(" blocks\n");
            }
            if (showTrusted) {
                detail.append("Trusted Players: ").append(claim.getTrustedPlayers().size());
            }
            marker.setDetail(detail.toString());

            // Add marker to set
            markerSet.getMarkers().put(markerId, marker);

        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("Error adding claim marker: " + e.getMessage());
            }
        }
    }




    public void updateClaim(Claim claim) {
        if (!enabled || blueMap == null || markerSet == null || map == null) return;

        String markerId = "claim_" + claim.getOwner().toString() + "_" +
                claim.getCorner1().getBlockX() + "_" +
                claim.getCorner1().getBlockZ();

        markerSet.getMarkers().remove(markerId);
        addClaimMarker(claim);
    }

    public void removeClaim(Claim claim) {
        if (!enabled || blueMap == null || markerSet == null || map == null) return;

        String markerId = "claim_" + claim.getOwner().toString() + "_" +
                claim.getCorner1().getBlockX() + "_" +
                claim.getCorner1().getBlockZ();

        markerSet.getMarkers().remove(markerId);
    }

    public void disable() {
        if (markerSet != null) {
            markerSet.getMarkers().clear();
        }
        enabled = false;
        plugin.getLogger().info("[BlueMap] Integration disabled");
    }

    public boolean isEnabled() {
        return enabled && blueMap != null && markerSet != null && map != null;
    }
}
