// Main.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.HashMap;

public class Main extends JavaPlugin {
    private static Main instance;
    private ClaimManager claimManager;
    private ConfigManager configManager;
    private AdminCommand adminCommand;
    private ClaimModifier claimModifier;
    private DataManager dataManager;
    private SelectionManager selectionManager;
    private BlockAccumulator blockAccumulator;
    private ProtectionManager protectionManager;
    private GUI gui;
    private TrustCommand trustCommand;
    private TrustGUI trustGui;
    private FlagGUI flagGui;
    private MainClaimGUI mainClaimGui;
    private ClaimListGUI claimListGui;
    private Map<UUID, Runnable> pendingActions;
    private ClaimInfoCommand claimInfoCommand;
    private WorldSettingsManager worldSettingsManager;
    private WorldSettingsCommand worldSettingsCommand;
    private ChatInputManager chatInputManager;
    private ClaimTransferManager transferManager;
    private ClaimStatistics claimStatistics;
    private ClaimVisualizer claimVisualizer;
    private ClaimSettingsGUI claimSettingsGui;
    private AdminMenuGUI adminMenuGui;
    private GlobalFlagsGUI globalFlagsGui;
    private boolean isInitialized = false;
    private boolean managersInitialized = false;
    private boolean eventsRegistered = false;
    private boolean commandsRegistered = false;
    private DatabaseManager databaseManager;
    private boolean databaseMigrated = false;
    private BlueMapIntegration blueMapIntegration;
    private final Map<UUID, Boolean> flightStates = new HashMap<>();
    private final Map<UUID, Long> lastFlightMessage = new HashMap<>();
    private static final long FLIGHT_MESSAGE_COOLDOWN = 20000;


    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Initializing LandClaims plugin...");

        try {
            // Create plugin directory if it doesn't exist
            if (!getDataFolder().exists()) {
                getLogger().info("Creating plugin directory...");
                getDataFolder().mkdirs();
            }

            // Initialize database first - this should be the only data source
            try {
                getLogger().info("Initializing database...");
                this.databaseManager = new DatabaseManager(this);
            } catch (Exception e) {
                getLogger().severe("Failed to initialize database: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }

            // Initialize managers - but don't load from YAML
            initializeManagers();

            // Load claims from database only
            claimManager.loadClaimsFromDatabase();

            // Register events with error handling
            try {
                getLogger().info("Registering events...");
                ClaimListener claimListener = new ClaimListener(this);
                GUIListener guiListener = new GUIListener(this);

                getServer().getPluginManager().registerEvents(claimListener, this);
                getServer().getPluginManager().registerEvents(guiListener, this);

                // Register additional events
                getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                    @EventHandler(priority = EventPriority.HIGH)
                    public void onPlayerJoin(PlayerJoinEvent event) {
                        // Restore flight state if needed
                        UUID playerUUID = event.getPlayer().getUniqueId();
                        if (getFlightState(playerUUID)) {
                            Claim claim = getClaimManager().getClaimAt(event.getPlayer().getLocation());
                            if (claim != null && canFlyInClaim(event.getPlayer(), claim)) {
                                event.getPlayer().setAllowFlight(true);
                                event.getPlayer().setFlying(true);
                            }
                        }
                    }
                }, this);

                eventsRegistered = true;
                getLogger().info("Events registered successfully.");
            } catch (Exception e) {
                getLogger().severe("Failed to register events: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }

            // Register commands with error handling
            try {
                getLogger().info("Registering commands...");
                LandClaimsCommand landClaimsCommand = new LandClaimsCommand(this);
                if (getCommand("landclaims") != null) {
                    getCommand("landclaims").setExecutor(landClaimsCommand);
                    getCommand("lc").setExecutor(landClaimsCommand);
                    commandsRegistered = true;
                    getLogger().info("Commands registered successfully.");
                } else {
                    throw new IllegalStateException("landclaims command not found in plugin.yml!");
                }
            } catch (Exception e) {
                getLogger().severe("Failed to register commands: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }


            if (getServer().getPluginManager().getPlugin("BlueMap") != null) {
                getLogger().info("[LandClaims] BlueMap detected, initializing integration...");
                try {
                    this.blueMapIntegration = new BlueMapIntegration(this);
                    if (this.blueMapIntegration.isEnabled()) {
                        getLogger().info("[LandClaims] BlueMap integration enabled successfully!");
                    } else {
                        getLogger().warning("[LandClaims] BlueMap integration failed to initialize.");
                    }
                } catch (Exception e) {
                    getLogger().severe("[LandClaims] Error initializing BlueMap integration: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            getLogger().severe("Critical error during plugin initialization: " + e.getMessage());
            e.printStackTrace();
            throw e; // Let Bukkit handle the plugin disable
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
            return player.hasPermission("landclaims.admin");
        }

        // In personal claims
        return claim.getOwner().equals(player.getUniqueId()) ||
                claim.getTrustLevel(player.getUniqueId()) != null;
    }
    public boolean areCriticalComponentsReady() {
        boolean ready = true;
        if (configManager == null) {
            getLogger().severe("ConfigManager is null!");
            ready = false;
        }
        if (dataManager == null) {
            getLogger().severe("DataManager is null!");
            ready = false;
        }
        if (claimManager == null) {
            getLogger().severe("ClaimManager is null!");
            ready = false;
        }
        if (protectionManager == null) {
            getLogger().severe("ProtectionManager is null!");
            ready = false;
        }
        if (mainClaimGui == null) {
            getLogger().severe("MainClaimGui is null!");
            ready = false;
        }
        return ready;
    }
    public boolean isDatabaseMigrated() {
        return databaseMigrated;
    }

    public void setDatabaseMigrated(boolean migrated) {
        this.databaseMigrated = migrated;
    }

    private void initializeManagers() {
        try {
            // First, initialize configuration and data storage
            getLogger().info("Initializing ConfigManager...");
            this.configManager = new ConfigManager(this);
            if (configManager == null) throw new IllegalStateException("ConfigManager failed to initialize");

            getLogger().info("Initializing DataManager...");
            this.dataManager = new DataManager(this);
            if (dataManager == null) throw new IllegalStateException("DataManager failed to initialize");

            // Initialize core systems
            getLogger().info("Initializing BlockAccumulator...");
            this.blockAccumulator = new BlockAccumulator(this);
            if (blockAccumulator == null) throw new IllegalStateException("BlockAccumulator failed to initialize");

            getLogger().info("Initializing ClaimManager...");
            this.claimManager = new ClaimManager(this);
            if (claimManager == null) throw new IllegalStateException("ClaimManager failed to initialize");

            // Load claims after ClaimManager is initialized
            getLogger().info("Loading claims...");
            this.claimManager.loadClaimsFromDatabase();

            getLogger().info("Initializing WorldSettingsManager...");
            this.worldSettingsManager = new WorldSettingsManager(this);
            if (worldSettingsManager == null) throw new IllegalStateException("WorldSettingsManager failed to initialize");

            getLogger().info("Initializing ProtectionManager...");
            this.protectionManager = new ProtectionManager(this);
            if (protectionManager == null) throw new IllegalStateException("ProtectionManager failed to initialize");

            // Initialize GUIs
            getLogger().info("Initializing GUI components...");
            this.mainClaimGui = new MainClaimGUI(this);
            this.trustGui = new TrustGUI(this);
            this.flagGui = new FlagGUI(this);
            this.claimListGui = new ClaimListGUI(this);
            this.claimSettingsGui = new ClaimSettingsGUI(this);
            this.adminMenuGui = new AdminMenuGUI(this);
            this.globalFlagsGui = new GlobalFlagsGUI(this);
            this.gui = new GUI(this);

            // Initialize commands
            getLogger().info("Initializing commands...");
            this.adminCommand = new AdminCommand(this);
            this.trustCommand = new TrustCommand(this);
            this.worldSettingsCommand = new WorldSettingsCommand(this);
            this.claimInfoCommand = new ClaimInfoCommand(this);

            // Initialize utilities
            getLogger().info("Initializing utilities...");
            this.pendingActions = new HashMap<>();
            this.chatInputManager = new ChatInputManager(this);
            this.claimModifier = new ClaimModifier(this);
            this.selectionManager = new SelectionManager(this);
            this.transferManager = new ClaimTransferManager(this);
            this.claimStatistics = new ClaimStatistics(this);
            this.claimVisualizer = new ClaimVisualizer(this);

            // Verify all GUIs are initialized
            if (mainClaimGui == null) throw new IllegalStateException("MainClaimGUI failed to initialize");
            if (trustGui == null) throw new IllegalStateException("TrustGUI failed to initialize");
            if (flagGui == null) throw new IllegalStateException("FlagGUI failed to initialize");
            if (claimListGui == null) throw new IllegalStateException("ClaimListGUI failed to initialize");
            if (claimSettingsGui == null) throw new IllegalStateException("ClaimSettingsGUI failed to initialize");
            if (adminMenuGui == null) throw new IllegalStateException("AdminMenuGUI failed to initialize");
            if (globalFlagsGui == null) throw new IllegalStateException("GlobalFlagsGUI failed to initialize");
            if (gui == null) throw new IllegalStateException("GUI failed to initialize");

            // Now verify critical components
            verifyInitialization();

            // Set the flag only after everything is properly initialized
            managersInitialized = true;

            getLogger().info("All managers initialized successfully.");
        } catch (Exception e) {
            managersInitialized = false;
            getLogger().severe("Error during manager initialization: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize managers", e);
        }
    }



    public boolean shouldSendFlightMessage(UUID playerUUID) {
        long now = System.currentTimeMillis();
        Long lastMessage = lastFlightMessage.get(playerUUID);
        if (lastMessage == null || now - lastMessage >= FLIGHT_MESSAGE_COOLDOWN) {
            lastFlightMessage.put(playerUUID, now);
            return true;
        }
        return false;
    }

    // In Main.java
    public Map<UUID, Long> getLastFlightMessage() {
        return lastFlightMessage;
    }


    private void verifyInitialization() {
        StringBuilder errors = new StringBuilder();

        if (configManager == null) errors.append("ConfigManager is null\n");
        if (dataManager == null) errors.append("DataManager is null\n");
        if (claimManager == null) errors.append("ClaimManager is null\n");
        if (protectionManager == null) errors.append("ProtectionManager is null\n");
        if (mainClaimGui == null) errors.append("MainClaimGUI is null\n");
        if (blockAccumulator == null) errors.append("BlockAccumulator is null\n");
        if (worldSettingsManager == null) errors.append("WorldSettingsManager is null\n");

        if (errors.length() > 0) {
            getLogger().severe("Critical components failed to initialize:");
            getLogger().severe(errors.toString());
            throw new IllegalStateException("Critical components failed to initialize");
        }
    }

    public void migrateToDatabase() {
        try {
            getLogger().info("Starting data migration to database...");
            List<Claim> oldClaims = dataManager.loadAllClaims();

            if (oldClaims.isEmpty()) {
                getLogger().info("No claims found in data.yml to migrate.");
                return;
            }

            int migrated = 0;
            for (Claim claim : oldClaims) {
                databaseManager.saveClaim(claim);
                migrated++;
            }

            getLogger().info("Successfully migrated " + migrated + " claims to database.");

            // Optionally backup and rename old data file
            File dataFile = new File(getDataFolder(), "data.yml");
            if (dataFile.exists()) {
                File backupFile = new File(getDataFolder(), "data.yml.backup");
                dataFile.renameTo(backupFile);
                getLogger().info("Original data.yml has been backed up to data.yml.backup");
            }
        } catch (Exception e) {
            getLogger().severe("Error during data migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public BlueMapIntegration getBlueMapIntegration() {
        return blueMapIntegration;
    }




    @Override
    public void onDisable() {
        try {
            if (databaseManager != null) {
                getLogger().info("Closing database connection...");
                databaseManager.close();
            }

            if (blueMapIntegration != null) {
                blueMapIntegration.disable();
            }
            if (blockAccumulator != null) {
                getLogger().info("Saving block accumulator data...");
                blockAccumulator.saveData();
            }



            ConfigurationSection flightSection = getConfig().createSection("flight-states");
            for (Map.Entry<UUID, Boolean> entry : flightStates.entrySet()) {
                flightSection.set(entry.getKey().toString(), entry.getValue());
            }
            saveConfig();

            getLogger().info("LandClaims plugin has been disabled!");
        } catch (Exception e) {
            getLogger().severe("Error during plugin shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }



    public static Main getInstance() {
        return instance;
    }

    public int getFlightStatesCount() {
        return flightStates.size();
    }

    public int cleanupFlightStates() {
        int count = 0;
        Iterator<Map.Entry<UUID, Boolean>> it = flightStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Boolean> entry = it.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                it.remove();
                count++;
            }
        }
        return count;
    }

    public int cleanupPendingActions() {
        int count = 0;
        Iterator<Map.Entry<UUID, Runnable>> it = pendingActions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Runnable> entry = it.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                it.remove();
                count++;
            }
        }
        return count;
    }


    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AdminCommand getAdminCommand() {
        return adminCommand;
    }
    public ClaimModifier getClaimModifier() {
        return claimModifier;
    }
    public DataManager getDataManager() {
        return dataManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public void setBlueMapIntegration(BlueMapIntegration integration) {
        this.blueMapIntegration = integration;
    }


    public BlockAccumulator getBlockAccumulator() {
        return blockAccumulator;
    }
    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }
    public GUI getGui() {
        return gui;
    }
    public TrustCommand getTrustCommand() {
        return trustCommand;
    }

    public TrustGUI getTrustGui() {
        return trustGui;
    }
    public FlagGUI getFlagGui() {
        return flagGui;
    }
    public MainClaimGUI getMainClaimGui() {
        return mainClaimGui;
    }
    public ClaimListGUI getClaimListGui() {
        return claimListGui;
    }

    public Map<UUID, Runnable> getPendingActions() {
        return pendingActions;
    }
    public ClaimInfoCommand getClaimInfoCommand() {
        return claimInfoCommand;
    }
    public WorldSettingsManager getWorldSettingsManager() {
        return worldSettingsManager;
    }

    public WorldSettingsCommand getWorldSettingsCommand() {
        return worldSettingsCommand;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public ClaimTransferManager getTransferManager() {
        return transferManager;
    }

    public ClaimStatistics getClaimStatistics() {
        return claimStatistics;
    }
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ClaimVisualizer getClaimVisualizer() {
        return claimVisualizer;
    }
    // In Main.java
    public void reload() {
        getLogger().info("Reloading LandClaims...");

        // Save current data
        if (claimManager != null) {
            claimManager.saveAllClaims();
        }

        // Close and reinitialize database connection
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = new DatabaseManager(this);
        }

        // Reload config
        reloadConfig();
        if (configManager != null) {
            configManager.loadConfig();
        }

        // Reload managers
        if (claimManager != null) {
            claimManager.clearClaims();
            claimManager.loadClaims();
        }

        getLogger().info("LandClaims configuration and data reloaded.");
    }


    public boolean hasPendingAction(UUID playerUUID) {
        return pendingActions.containsKey(playerUUID);
    }
    public ClaimSettingsGUI getClaimSettingsGui() {
        return claimSettingsGui;
    }
    public AdminMenuGUI getAdminMenuGui() {
        return adminMenuGui;
    }

    public GlobalFlagsGUI getGlobalFlagsGui() {
        return globalFlagsGui;
    }

    public void debug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[Debug] " + message);
        }
    }
    public boolean isInitialized() {
        if (!managersInitialized) {
            return false;
        }
        return areCriticalComponentsReady();
    }

    private void initializeDatabase() {
        try {
            getLogger().info("Initializing database...");
            this.databaseManager = new DatabaseManager(this);

            // Optionally migrate data if database is empty
            if (!databaseManager.hasExistingClaims()) {
                getLogger().info("New database detected, checking for data to migrate...");
                migrateToDatabase();
            }
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean getFlightState(UUID playerUUID) {
        return flightStates.getOrDefault(playerUUID, false);
    }

    public void setFlightState(UUID playerUUID, boolean state) {
        flightStates.put(playerUUID, state);
    }


    public boolean isComponentReady(String componentName) {
        switch (componentName) {
            case "mainClaimGui":
                return mainClaimGui != null;
            case "claimManager":
                return claimManager != null;
            case "protectionManager":
                return protectionManager != null;
            default:
                return false;
        }
    }
    private void loadFlightStates() {
        ConfigurationSection flightSection = getConfig().getConfigurationSection("flight-states");
        if (flightSection != null) {
            for (String uuidString : flightSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    boolean state = flightSection.getBoolean(uuidString);
                    flightStates.put(uuid, state);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid UUID in flight states: " + uuidString);
                }
            }
        }
    }

    public void attemptRecovery() {
        getLogger().info("Attempting plugin recovery...");

        try {
            // Clear existing managers
            clearManagers();

            // Reinitialize everything
            initializeManagers();
            registerEvents();
            registerCommands();

            isInitialized = true;
            getLogger().info("Recovery attempt completed successfully.");
        } catch (Exception e) {
            isInitialized = false;
            getLogger().severe("Recovery attempt failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void registerEvents() {
        try {
            getLogger().info("Registering events...");
            ClaimListener claimListener = new ClaimListener(this);
            GUIListener guiListener = new GUIListener(this);

            getServer().getPluginManager().registerEvents(claimListener, this);
            getServer().getPluginManager().registerEvents(guiListener, this);

            eventsRegistered = true;
            getLogger().info("Events registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register events: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void registerCommands() {
        try {
            getLogger().info("Registering commands...");
            LandClaimsCommand landClaimsCommand = new LandClaimsCommand(this);
            if (getCommand("landclaims") != null) {
                getCommand("landclaims").setExecutor(landClaimsCommand);
                getCommand("lc").setExecutor(landClaimsCommand);
                commandsRegistered = true;
                getLogger().info("Commands registered successfully.");
            } else {
                throw new IllegalStateException("landclaims command not found in plugin.yml!");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void clearManagers() {
        if (claimManager != null) {
            claimManager.clearClaims();
        }
        // Clear other managers as needed
    }

}
