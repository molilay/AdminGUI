package me.admin.gui.database;

import me.admin.gui.AdvancedModeratorGUI;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final AdvancedModeratorGUI plugin;
    private Connection connection;
    private final String dbType;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DatabaseManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dbType = plugin.getConfig().getString("database.type", "sqlite");
        init();
    }

    private void init() {
        try {
            if (dbType.equalsIgnoreCase("mysql")) {
                setupMySQL();
            } else {
                setupSQLite();
            }
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            connection = null;
        }
    }

    private void setupSQLite() throws Exception {
        File dbFile = new File(plugin.getDataFolder(), "logs.db");
        plugin.getDataFolder().mkdirs();
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    private void setupMySQL() throws Exception {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "adminmoderator");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");

        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&charset=utf8mb4",
                username, password);
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            if (dbType.equalsIgnoreCase("mysql")) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS punishment_logs (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "type VARCHAR(32) NOT NULL," +
                    "moderator VARCHAR(64) NOT NULL," +
                    "target VARCHAR(64) NOT NULL," +
                    "reason TEXT," +
                    "date DATETIME NOT NULL," +
                    "duration BIGINT DEFAULT -1" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                );
            } else {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS punishment_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "type TEXT NOT NULL," +
                    "moderator TEXT NOT NULL," +
                    "target TEXT NOT NULL," +
                    "reason TEXT," +
                    "date TEXT NOT NULL," +
                    "duration INTEGER DEFAULT -1" +
                    ")"
                );
            }
        }
    }

    private boolean isConnected() {
        if (connection == null) return false;
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void logPunishment(String type, String moderator, String target, String reason, long duration) {
        if (!isConnected()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO punishment_logs (type, moderator, target, reason, date, duration) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, type);
            ps.setString(2, moderator);
            ps.setString(3, target);
            ps.setString(4, reason);
            ps.setString(5, LocalDateTime.now().format(FORMATTER));
            ps.setLong(6, duration);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log punishment: " + e.getMessage());
        }
    }

    public List<LogEntry> getAllLogs() {
        if (!isConnected()) return List.of();
        List<LogEntry> logs = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM punishment_logs ORDER BY date DESC")) {
            while (rs.next()) {
                logs.add(mapLogEntry(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to fetch logs: " + e.getMessage());
        }
        return logs;
    }

    public List<LogEntry> getLogsByTarget(String targetName) {
        if (!isConnected()) return List.of();
        List<LogEntry> logs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishment_logs WHERE target LIKE ? ORDER BY date DESC")) {
            ps.setString(1, "%" + targetName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapLogEntry(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to fetch logs: " + e.getMessage());
        }
        return logs;
    }

    public CompletableFuture<List<LogEntry>> getAllLogsAsync() {
        return CompletableFuture.supplyAsync(this::getAllLogs);
    }

    public CompletableFuture<List<LogEntry>> getLogsByTargetAsync(String targetName) {
        return CompletableFuture.supplyAsync(() -> getLogsByTarget(targetName));
    }

    public void clearLogs() {
        if (!isConnected()) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM punishment_logs");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear logs: " + e.getMessage());
        }
    }

    private LogEntry mapLogEntry(ResultSet rs) throws SQLException {
        if (dbType.equalsIgnoreCase("mysql")) {
            return new LogEntry(
                    rs.getInt("id"),
                    rs.getString("type"),
                    rs.getString("moderator"),
                    rs.getString("target"),
                    rs.getString("reason"),
                    rs.getTimestamp("date").toLocalDateTime(),
                    rs.getLong("duration")
            );
        } else {
            return new LogEntry(
                    rs.getInt("id"),
                    rs.getString("type"),
                    rs.getString("moderator"),
                    rs.getString("target"),
                    rs.getString("reason"),
                    LocalDateTime.parse(rs.getString("date"), FORMATTER),
                    rs.getLong("duration")
            );
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database: " + e.getMessage());
        }
    }
}
