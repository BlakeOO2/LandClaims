package org.example;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class FlightManager {
    private final Main plugin;
    private BukkitTask flightCheckTask;
    private static final long CHECK_INTERVAL_TICKS = 100; // 5 seconds (20 ticks = 1 second)

    public FlightManager(Main plugin) {
        this.plugin = plugin;
        startFlightCheckTask();
    }

    /**
     * Starts a repeating task that checks if players with flight enabled
     * are still in claims where they have flight permission
     */
    private void startFlightCheckTask() {
        flightCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Iterate through all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerUUID = player.getUniqueId();

                // Skip if player doesn't have flight toggled on in our system
                if (!plugin.getFlightState(playerUUID)) {
                    continue;
                }

                // Skip if player is in creative or spectator mode
                GameMode gameMode = player.getGameMode();
                if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
                    continue;
                }

                // Check if player is in a claim where they have flight permission
                Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
                boolean shouldHaveFlight = claim != null && plugin.canFlyInClaim(player, claim);

                // If player has flight but shouldn't, disable it
                if (!shouldHaveFlight) {
                    // Only disable flight if the player actually has it enabled
                    if (player.getAllowFlight()) {
                        disableFlightSafely(player);
                        player.sendMessage("§c[LandClaims] You've left a claim where you had flight permission.");
                    }

                    // Update the flight state in the plugin's system to match reality
                    plugin.setFlightState(playerUUID, false);
                }
            }
        }, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    /**
     * Disables a player's flight safely, preventing fall damage
     * @param player The player to disable flight for
     */
    private void disableFlightSafely(Player player) {
        // Only do this if the player is actually flying
        if (player.isFlying()) {
            // Cancel fall damage for a short period
            player.setFallDistance(0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setFallDistance(0);
            }, 60L); // 3 seconds of fall damage prevention
        }

        // Disable flight capabilities
        player.setFlying(false);
        player.setAllowFlight(false);

        // Update the plugin's flight state to match
        plugin.setFlightState(player.getUniqueId(), false);
    }

    /**
     * Cleans up resources when the plugin is disabled
     */
    public void shutdown() {
        if (flightCheckTask != null) {
            flightCheckTask.cancel();
            flightCheckTask = null;
        }
    }
}
