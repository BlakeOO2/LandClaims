package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseManager {
    private final Main plugin;
    private Connection connection;
    private final String dbPath;
    private final PreparedStatement[] cachedStatements = new PreparedStatement[10];

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder() + "/claims.db";
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Create tables
            try (Statement stmt = connection.createStatement()) {
                // Claims table - removed Y coordinates
                stmt.execute("CREATE TABLE IF NOT EXISTS claims (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "owner TEXT NOT NULL," +
                        "world TEXT NOT NULL," +
                        "corner1_x INTEGER NOT NULL," +
                        "corner1_z INTEGER NOT NULL," +
                        "corner2_x INTEGER NOT NULL," +
                        "corner2_z INTEGER NOT NULL," +
                        "is_admin_claim BOOLEAN NOT NULL DEFAULT 0," +
                        "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "modification_count INTEGER DEFAULT 0" +
                        ")");

                // Rest of the tables remain the same
                stmt.execute("CREATE TABLE IF NOT EXISTS trusted_players (" +
                        "claim_id INTEGER," +
                        "player_uuid TEXT NOT NULL," +
                        "trust_level TEXT NOT NULL," +
                        "FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE," +
                        "PRIMARY KEY(claim_id, player_uuid)" +
                        ")");

                stmt.execute("CREATE TABLE IF NOT EXISTS claim_flags (" +
                        "claim_id INTEGER," +
                        "flag_name TEXT NOT NULL," +
                        "flag_value BOOLEAN NOT NULL," +
                        "FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE," +
                        "PRIMARY KEY(claim_id, flag_name)" +
                        ")");

                // Create indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims(owner)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_claims_world ON claims(world)");
            }

            plugin.getLogger().info("Database initialized successfully");
        } catch (Exception e) {
            plugin.getLogger().severe("Could not initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean reconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to reconnect to database: " + e.getMessage());
            return false;
        }
    }

    public void handleCorruptDatabase() {
        plugin.getLogger().severe("Database corruption detected! Attempting recovery...");

        // Create backup of corrupted database
        File dbFile = new File(dbPath);
        if (dbFile.exists()) {
            try {
                File backupFile = new File(dbPath + ".corrupted." +
                        new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
                java.nio.file.Files.copy(dbFile.toPath(), backupFile.toPath());
                plugin.getLogger().info("Created backup of corrupted database: " + backupFile.getName());
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to backup corrupted database: " + e.getMessage());
            }
        }

        // Close current connection
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing corrupted database connection: " + e.getMessage());
        }

        // Delete corrupted database
        if (dbFile.exists() && dbFile.delete()) {
            plugin.getLogger().info("Removed corrupted database file");
        }

        // Reinitialize database
        try {
            initializeDatabase();
            plugin.getLogger().info("Database reinitialized successfully");

            // Import from YAML backup if available
            List<Claim> yamlClaims = plugin.getDataManager().loadAllClaims();
            if (!yamlClaims.isEmpty()) {
                plugin.getLogger().info("Found " + yamlClaims.size() + " claims in YAML backup, importing...");
                for (Claim claim : yamlClaims) {
                    saveClaim(claim);
                }
                plugin.getLogger().info("Successfully imported claims from YAML backup");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to recover from database corruption: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private void startBackupTask() {
        long interval = plugin.getConfig().getLong("database.backup-interval", 12) * 20 * 3600; // Convert hours to ticks
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            backupDatabase();
        }, interval, interval);
    }

    public boolean isDatabaseHealthy() {
        try (Statement stmt = connection.createStatement()) {
            // Check if we can execute a simple query
            stmt.executeQuery("SELECT 1");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database health check failed: " + e.getMessage());
            return false;
        }
    }



    // In DatabaseManager.java
    public void saveClaimAsync(Claim claim) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            saveClaim(claim);
        });
    }

    // In DatabaseManager.java
    private void trackMigrationStatus() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS migration_status (" +
                    "id INTEGER PRIMARY KEY," +
                    "migration_date TIMESTAMP," +
                    "yaml_claims INTEGER," +
                    "migrated_claims INTEGER)");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create migration tracking table: " + e.getMessage());
        }
    }
    public void importFromYaml() {
        try {
            connection.setAutoCommit(false);

            List<Claim> yamlClaims = plugin.getDataManager().loadAllClaims();
            Set<String> processedSignatures = new HashSet<>();
            int imported = 0;
            int skipped = 0;

            for (Claim claim : yamlClaims) {
                String signature = createClaimSignature(claim);

                // Check if this exact claim already exists
                if (processedSignatures.contains(signature) || claimExists(claim)) {
                    skipped++;
                    continue;
                }

                // Verify claim ownership
                if (!isValidClaim(claim)) {
                    plugin.getLogger().warning("Skipping invalid claim for owner: " + claim.getOwner());
                    skipped++;
                    continue;
                }

                saveClaim(claim);
                processedSignatures.add(signature);
                imported++;
            }

            connection.commit();
            plugin.getLogger().info("Import completed: " + imported + " claims imported, " +
                    skipped + " duplicates/invalid claims skipped");

        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                plugin.getLogger().severe("Error during rollback: " + rollbackEx.getMessage());
            }
            plugin.getLogger().severe("Error during import: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error resetting auto-commit: " + e.getMessage());
            }
        }
    }

    private boolean isValidClaim(Claim claim) {
        // Check if owner exists
        OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwner());
        if (!owner.hasPlayedBefore() && !owner.isOnline()) {
            return false;
        }

        // Check if world exists
        if (Bukkit.getWorld(claim.getWorld()) == null) {
            return false;
        }

        // Check if claim size is valid
        int size = claim.getSize();
        int minSize = plugin.getConfig().getInt("claiming.minimum-size", 100);
        int maxSize = plugin.getConfig().getInt("claiming.max-claim-size", 1000000);
        if (size < minSize || size > maxSize) {
            return false;
        }

        return true;
    }



    public void loadClaimsAsync(Consumer<List<Claim>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Claim> claims = loadAllClaims();
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(claims));
        });
    }


    public boolean hasExistingClaims() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM claims")) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for existing claims: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean isDuplicateClaim(Claim claim) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM claims WHERE owner = ? AND world = ? AND " +
                        "corner1_x = ? AND corner1_z = ? AND corner2_x = ? AND corner2_z = ?")) {

            stmt.setString(1, claim.getOwner().toString());
            stmt.setString(2, claim.getWorld());
            stmt.setInt(3, claim.getCorner1().getBlockX());
            stmt.setInt(4, claim.getCorner1().getBlockZ());
            stmt.setInt(5, claim.getCorner2().getBlockX());
            stmt.setInt(6, claim.getCorner2().getBlockZ());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for duplicate claim: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }



    private String createClaimSignature(Claim claim) {
        // Create a unique signature for a claim based on its properties
        return String.format("%s_%d_%d_%d_%d_%d_%d_%s",
                claim.getWorld(),
                claim.getCorner1().getBlockX(),
                claim.getCorner1().getBlockY(),
                claim.getCorner1().getBlockZ(),
                claim.getCorner2().getBlockX(),
                claim.getCorner2().getBlockY(),
                claim.getCorner2().getBlockZ(),
                claim.getOwner().toString());
    }

    public void saveClaim(Claim claim) {
        try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
            Connection conn = transaction.getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO claims (owner, world, corner1_x, corner1_z, " +
                            "corner2_x, corner2_z, is_admin_claim) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, claim.getOwner().toString());
                stmt.setString(2, claim.getWorld());
                stmt.setInt(3, claim.getCorner1().getBlockX());
                stmt.setInt(4, claim.getCorner1().getBlockZ());
                stmt.setInt(5, claim.getCorner2().getBlockX());
                stmt.setInt(6, claim.getCorner2().getBlockZ());
                stmt.setBoolean(7, claim.isAdminClaim());

                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int claimId = rs.getInt(1);
                    saveTrustedPlayers(conn, claimId, claim.getTrustedPlayers());
                    saveFlags(conn, claimId, claim.getFlags());
                }
            }

            transaction.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving claim: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void saveTrustedPlayers(Connection conn, int claimId, Map<UUID, TrustLevel> trustedPlayers) throws SQLException {
        // First delete existing trusted players
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM trusted_players WHERE claim_id = ?")) {
            delete.setInt(1, claimId);
            delete.executeUpdate();
        }

        // Insert new trusted players
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO trusted_players (claim_id, player_uuid, trust_level) VALUES (?, ?, ?)")) {
            for (Map.Entry<UUID, TrustLevel> entry : trustedPlayers.entrySet()) {
                insert.setInt(1, claimId);
                insert.setString(2, entry.getKey().toString());
                insert.setString(3, entry.getValue().name());
                insert.executeUpdate();
            }
        }
    }

    public void updateClaim(Claim claim) {
        try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
            Connection conn = transaction.getConnection();

            // First, find the claim ID
            int claimId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id FROM claims WHERE owner = ? AND world = ? AND " +
                            "corner1_x = ? AND corner1_z = ? AND corner2_x = ? AND corner2_z = ?")) {

                stmt.setString(1, claim.getOwner().toString());
                stmt.setString(2, claim.getWorld());
                stmt.setInt(3, claim.getCorner1().getBlockX());
                stmt.setInt(4, claim.getCorner1().getBlockZ());
                stmt.setInt(5, claim.getCorner2().getBlockX());
                stmt.setInt(6, claim.getCorner2().getBlockZ());

                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    // Claim doesn't exist, create it instead
                    saveClaim(claim);
                    return;
                }
                claimId = rs.getInt("id");
            }

            // Update the claim's main data
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE claims SET owner = ?, world = ?, corner1_x = ?, corner1_z = ?, " +
                            "corner2_x = ?, corner2_z = ?, is_admin_claim = ? WHERE id = ?")) {

                stmt.setString(1, claim.getOwner().toString());
                stmt.setString(2, claim.getWorld());
                stmt.setInt(3, claim.getCorner1().getBlockX());
                stmt.setInt(4, claim.getCorner1().getBlockZ());
                stmt.setInt(5, claim.getCorner2().getBlockX());
                stmt.setInt(6, claim.getCorner2().getBlockZ());
                stmt.setBoolean(7, claim.isAdminClaim());
                stmt.setInt(8, claimId);

                stmt.executeUpdate();
            }

            // Update trusted players and flags
            saveTrustedPlayers(conn, claimId, claim.getTrustedPlayers());
            saveFlags(conn, claimId, claim.getFlags());

            transaction.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating claim: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateClaimLocation(Claim claim, Location oldCorner1, Location oldCorner2) {
        try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
            Connection conn = transaction.getConnection();

            // Find the claim using old coordinates
            int claimId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id FROM claims WHERE owner = ? AND world = ? AND " +
                            "corner1_x = ? AND corner1_z = ? AND corner2_x = ? AND corner2_z = ?")) {

                stmt.setString(1, claim.getOwner().toString());
                stmt.setString(2, oldCorner1.getWorld().getName());
                stmt.setInt(3, oldCorner1.getBlockX());
                stmt.setInt(4, oldCorner1.getBlockZ());
                stmt.setInt(5, oldCorner2.getBlockX());
                stmt.setInt(6, oldCorner2.getBlockZ());

                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    plugin.getLogger().warning("Could not find claim to update location");
                    return;
                }
                claimId = rs.getInt("id");
            }

            // Update to new coordinates
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE claims SET corner1_x = ?, corner1_z = ?, " +
                            "corner2_x = ?, corner2_z = ? WHERE id = ?")) {

                stmt.setInt(1, claim.getCorner1().getBlockX());
                stmt.setInt(2, claim.getCorner1().getBlockZ());
                stmt.setInt(3, claim.getCorner2().getBlockX());
                stmt.setInt(4, claim.getCorner2().getBlockZ());
                stmt.setInt(5, claimId);

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    plugin.getLogger().warning("Failed to update claim location");
                    return;
                }
            }

            transaction.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating claim location: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveFlags(Connection conn, int claimId, Map<ClaimFlag, Boolean> flags) throws SQLException {
        // First delete existing flags
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM claim_flags WHERE claim_id = ?")) {
            delete.setInt(1, claimId);
            delete.executeUpdate();
        }

        // Insert new flags
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO claim_flags (claim_id, flag_name, flag_value) VALUES (?, ?, ?)")) {
            for (Map.Entry<ClaimFlag, Boolean> entry : flags.entrySet()) {
                insert.setInt(1, claimId);
                insert.setString(2, entry.getKey().name());
                insert.setBoolean(3, entry.getValue());
                insert.executeUpdate();
            }
        }
    }
    // In DatabaseManager.java
    // In DatabaseManager.java
    private Connection getConnection() throws SQLException {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

                // Test connection
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeQuery("SELECT 1");
                } catch (SQLException e) {
                    if (e.getMessage().contains("malformed")) {
                        handleCorruptDatabase();
                        // Retry connection after recovery
                        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                    } else {
                        throw e;
                    }
                }
            }
            return connection;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error: " + e.getMessage());
            throw e;
        }
    }



    public void removeDuplicateClaims() {
        try (Connection conn = getConnection()) {
            // First, identify duplicates based on location and owner
            String findDuplicatesSQL = """
            SELECT c1.id, c1.owner, c1.world, c1.corner1_x, c1.corner1_z, c1.corner2_x, c1.corner2_z,
            COUNT(*) as duplicate_count
            FROM claims c1
            GROUP BY owner, world, corner1_x, corner1_z, corner2_x, corner2_z
            HAVING COUNT(*) > 1
        """;

            List<Integer> duplicateIds = new ArrayList<>();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(findDuplicatesSQL)) {

                while (rs.next()) {
                    // Keep the oldest claim (lowest ID) and mark others for deletion
                    String getDuplicatesSQL = """
                    SELECT id FROM claims 
                    WHERE owner = ? AND world = ? 
                    AND corner1_x = ? AND corner1_z = ? 
                    AND corner2_x = ? AND corner2_z = ?
                    ORDER BY id ASC
                """;

                    try (PreparedStatement pstmt = conn.prepareStatement(getDuplicatesSQL)) {
                        pstmt.setString(1, rs.getString("owner"));
                        pstmt.setString(2, rs.getString("world"));
                        pstmt.setInt(3, rs.getInt("corner1_x"));
                        pstmt.setInt(4, rs.getInt("corner1_z"));
                        pstmt.setInt(5, rs.getInt("corner2_x"));
                        pstmt.setInt(6, rs.getInt("corner2_z"));

                        ResultSet duplicatesRs = pstmt.executeQuery();
                        // Skip first result (keep oldest)
                        duplicatesRs.next();
                        // Add rest to deletion list
                        while (duplicatesRs.next()) {
                            duplicateIds.add(duplicatesRs.getInt("id"));
                        }
                    }
                }
            }

            // Delete the duplicates
            if (!duplicateIds.isEmpty()) {
                String deleteSQL = "DELETE FROM claims WHERE id IN (" +
                        String.join(",", Collections.nCopies(duplicateIds.size(), "?")) + ")";

                try (PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
                    for (int i = 0; i < duplicateIds.size(); i++) {
                        pstmt.setInt(i + 1, duplicateIds.get(i));
                    }
                    int deleted = pstmt.executeUpdate();
                    plugin.getLogger().info("Removed " + deleted + " duplicate claims");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing duplicate claims: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void transferClaim(Claim claim, UUID newOwner) {
        try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
            Connection conn = transaction.getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE claims SET owner = ? WHERE id = (SELECT id FROM claims WHERE owner = ? AND world = ? AND " +
                            "corner1_x = ? AND corner1_z = ? AND corner2_x = ? AND corner2_z = ?)")) {

                stmt.setString(1, newOwner.toString());
                stmt.setString(2, claim.getOwner().toString());
                stmt.setString(3, claim.getWorld());
                stmt.setInt(4, claim.getCorner1().getBlockX());
                stmt.setInt(5, claim.getCorner1().getBlockZ());
                stmt.setInt(6, claim.getCorner2().getBlockX());
                stmt.setInt(7, claim.getCorner2().getBlockZ());

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    plugin.getLogger().warning("Failed to transfer claim ownership");
                    return;
                }
            }

            transaction.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error transferring claim ownership: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void verifyAndRepair() {
        try {
            // Verify database integrity
            boolean needsRepair = false;
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("PRAGMA integrity_check");
                if (rs.next() && !rs.getString(1).equals("ok")) {
                    needsRepair = true;
                }
            }

            if (needsRepair) {
                plugin.getLogger().warning("Database corruption detected, attempting repair...");
                // Backup current database
                backupDatabase();
                // Rebuild from cache
                rebuildDatabase();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database verification failed: " + e.getMessage());
        }
    }

    private void rebuildDatabase() {
        try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
            Connection conn = transaction.getConnection();

            // Drop and recreate tables
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS claims");
                stmt.execute("DROP TABLE IF EXISTS trusted_players");
                stmt.execute("DROP TABLE IF EXISTS claim_flags");

                // Reinitialize database
                initializeDatabase();

                // Get all claims from the ClaimManager
                Set<Claim> claims = plugin.getClaimManager().getAllClaims();

                // Resave all claims
                for (Claim claim : claims) {
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "INSERT INTO claims (owner, world, corner1_x, corner1_z, " +
                                    "corner2_x, corner2_z, is_admin_claim) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {

                        pstmt.setString(1, claim.getOwner().toString());
                        pstmt.setString(2, claim.getWorld());
                        pstmt.setInt(3, claim.getCorner1().getBlockX());
                        pstmt.setInt(4, claim.getCorner1().getBlockZ());
                        pstmt.setInt(5, claim.getCorner2().getBlockX());
                        pstmt.setInt(6, claim.getCorner2().getBlockZ());
                        pstmt.setBoolean(7, claim.isAdminClaim());

                        pstmt.executeUpdate();

                        // Get the generated claim ID and save trusted players and flags
                        ResultSet rs = pstmt.getGeneratedKeys();
                        if (rs.next()) {
                            int claimId = rs.getInt(1);
                            saveTrustedPlayers(conn, claimId, claim.getTrustedPlayers());
                            saveFlags(conn, claimId, claim.getFlags());
                        }
                    }
                }
            }

            transaction.commit();
            plugin.getLogger().info("Database rebuilt successfully with " +
                    plugin.getClaimManager().getAllClaims().size() + " claims");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to rebuild database: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private <T> T executeWithRetry(DatabaseOperation<T> operation, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                return operation.execute();
            } catch (SQLException e) {
                attempts++;
                if (attempts == maxRetries) {
                    throw new RuntimeException("Operation failed after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(100 * attempts); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    private void prepareStatements() throws SQLException {
        cachedStatements[0] = connection.prepareStatement(
                "SELECT * FROM claims WHERE owner = ?");
        cachedStatements[1] = connection.prepareStatement(
                "INSERT INTO claims (owner, world, corner1_x, corner1_z, corner2_x, corner2_z) VALUES (?, ?, ?, ?, ?, ?)");
        // ... more prepared statements
    }

    @FunctionalInterface
    private interface DatabaseOperation<T> {
        T execute() throws SQLException;
    }

    // In DatabaseManager.java
    public boolean backupDatabase() {
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) {
            plugin.getLogger().warning("Cannot backup database: file does not exist");
            return false;
        }

        try {
            // Create backups directory if it doesn't exist
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            // Create backup file with timestamp
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File backupFile = new File(backupDir, "claims_" + timestamp + ".db");

            // Close current connection to ensure all data is written
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            // Copy database file
            java.nio.file.Files.copy(dbFile.toPath(), backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Reconnect to database
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Clean up old backups (keep last 5)
            File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("claims_") && name.endsWith(".db"));
            if (backups != null && backups.length > 5) {
                Arrays.sort(backups, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (int i = 5; i < backups.length; i++) {
                    backups[i].delete();
                }
            }

            plugin.getLogger().info("Database backup created: " + backupFile.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create database backup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }



    // Add a method to get backup information
    public List<String> getBackupInfo() {
        List<String> info = new ArrayList<>();
        File backupDir = new File(plugin.getDataFolder(), "backups");

        if (!backupDir.exists() || !backupDir.isDirectory()) {
            info.add("No backups found");
            return info;
        }

        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("claims_") && name.endsWith(".db"));
        if (backups == null || backups.length == 0) {
            info.add("No backups found");
            return info;
        }

        // Sort backups by date (newest first)
        Arrays.sort(backups, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        for (File backup : backups) {
            String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(backup.lastModified()));
            double sizeInMb = backup.length() / (1024.0 * 1024.0);
            info.add(String.format("§7%s §8- §e%.2f MB", date, sizeInMb));
        }

        return info;
    }


    public boolean claimExists(Claim claim) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM claims WHERE owner = ? AND world = ? AND " +
                        "corner1_x = ? AND corner1_z = ? AND corner2_x = ? AND corner2_z = ?")) {

            stmt.setString(1, claim.getOwner().toString());
            stmt.setString(2, claim.getWorld());
            stmt.setInt(3, claim.getCorner1().getBlockX());
            stmt.setInt(4, claim.getCorner1().getBlockZ());
            stmt.setInt(5, claim.getCorner2().getBlockX());
            stmt.setInt(6, claim.getCorner2().getBlockZ());

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking if claim exists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void deleteClaim(Claim claim) {
        try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
            Connection conn = transaction.getConnection();

            // Delete the claim (cascading will handle trusted players and flags due to FK constraints)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM claims WHERE owner = ? AND world = ? AND " +
                            "corner1_x = ? AND corner1_z = ? AND corner2_x = ? AND corner2_z = ?")) {

                stmt.setString(1, claim.getOwner().toString());
                stmt.setString(2, claim.getWorld());
                stmt.setInt(3, claim.getCorner1().getBlockX());
                stmt.setInt(4, claim.getCorner1().getBlockZ());
                stmt.setInt(5, claim.getCorner2().getBlockX());
                stmt.setInt(6, claim.getCorner2().getBlockZ());

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    plugin.getLogger().warning("No claim found to delete at location: " +
                            claim.getCorner1().getBlockX() + "," + claim.getCorner1().getBlockZ());
                }
            }

            transaction.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting claim: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Claim> loadAllClaims() {
        List<Claim> claims = new ArrayList<>();
        try {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM claims")) {

                while (rs.next()) {
                    claims.add(loadClaim(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading claims: " + e.getMessage());
            e.printStackTrace();
        }
        return claims;
    }

    private Claim loadClaim(ResultSet rs) throws SQLException {
        int claimId = rs.getInt("id");
        UUID owner = UUID.fromString(rs.getString("owner"));
        String worldName = rs.getString("world");
        World world = Bukkit.getWorld(worldName);

        // Create locations with y=0, since claims extend full height
        Location corner1 = new Location(world,
                rs.getInt("corner1_x"),
                0,
                rs.getInt("corner1_z"));

        Location corner2 = new Location(world,
                rs.getInt("corner2_x"),
                0,
                rs.getInt("corner2_z"));

        Claim claim = new Claim(owner, corner1, corner2);
        claim.setAdminClaim(rs.getBoolean("is_admin_claim"));

        loadTrustedPlayers(claimId, claim);
        loadFlags(claimId, claim);

        return claim;
    }


    private void loadTrustedPlayers(int claimId, Claim claim) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM trusted_players WHERE claim_id = ?")) {
            stmt.setInt(1, claimId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                TrustLevel level = TrustLevel.valueOf(rs.getString("trust_level"));
                claim.setTrust(playerUuid, level);
            }
        }
    }

    private void loadFlags(int claimId, Claim claim) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM claim_flags WHERE claim_id = ?")) {
            stmt.setInt(1, claimId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ClaimFlag flag = ClaimFlag.valueOf(rs.getString("flag_name"));
                boolean value = rs.getBoolean("flag_value");
                claim.setFlag(flag, value);
            }
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
