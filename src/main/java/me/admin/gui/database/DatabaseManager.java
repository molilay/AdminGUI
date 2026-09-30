package me.admin.gui.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.admin.gui.AdvancedModeratorGUI;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseManager {

    public record PunishmentSummary(long total, long todayBans, long totalBans, long totalMutes, long totalWarns) {
        public static PunishmentSummary empty() { return new PunishmentSummary(0, 0, 0, 0, 0); }
    }
    public record TypeCounts(long total, long today, long week, long month) {
        public TypeCounts plus(TypeCounts other) {
            return new TypeCounts(total + other.total, today + other.today, week + other.week, month + other.month);
        }
    }
    public record StaffCount(String name, long actions) {}
    public record DetailedStats(java.util.Map<String, TypeCounts> types, List<StaffCount> topStaff) {}

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdvancedModeratorGUI plugin;
    private final String dbType;
    private final ExecutorService executor;
    private HikariDataSource dataSource;

    public DatabaseManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        this.executor = Executors.newFixedThreadPool(2,
                Thread.ofPlatform().daemon().name("amgui-db-", 0).factory());
        initialize();
    }

    private void initialize() {
        try {
            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("AMGUI-Database");
            hikari.setConnectionTimeout(Math.max(1000L,
                    plugin.getConfig().getLong("database.pool.connection-timeout-ms", 5000L)));
            if (dbType.equals("mysql")) {
                configureMySql(hikari);
            } else {
                configureSqlite(hikari);
            }

            dataSource = new HikariDataSource(hikari);
            runMigrations();
            int retentionDays = plugin.getConfig().getInt("database.retention-days", 365);
            if (retentionDays > 0) purgeOlderThan(retentionDays);
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось инициализировать базу данных: " + e.getMessage());
            if (dataSource != null) dataSource.close();
            dataSource = null;
        }
    }

    private void configureSqlite(HikariConfig hikari) throws ClassNotFoundException, SQLException {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) throw new SQLException("Не удалось создать папку плагина");
        File database = new File(folder, "logs.db");
        Class.forName("org.sqlite.JDBC");
        hikari.setDriverClassName("org.sqlite.JDBC");
        hikari.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
        hikari.setMaximumPoolSize(1);
        hikari.setMinimumIdle(1);
        hikari.setConnectionInitSql("PRAGMA foreign_keys=ON");
    }

    private void configureMySql(HikariConfig hikari) throws ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "adminmoderator");
        boolean ssl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl + "&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci"
                + "&serverTimezone=UTC");
        hikari.setUsername(plugin.getConfig().getString("database.mysql.username", "root"));
        hikari.setPassword(plugin.getConfig().getString("database.mysql.password", ""));
        int maximumSize = Math.max(2, plugin.getConfig().getInt("database.pool.maximum-size", 10));
        int minimumIdle = Math.clamp(plugin.getConfig().getInt("database.pool.minimum-idle", 1), 0, maximumSize);
        hikari.setMaximumPoolSize(maximumSize);
        hikari.setMinimumIdle(minimumIdle);
    }

    private void runMigrations() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (!dbType.equals("mysql")) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
            }
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at VARCHAR(32) NOT NULL)");
        }
        int version = getSchemaVersion();
        if (version < 1) {
            migrationCreatePunishmentLogs();
            recordMigration(1);
        }
        if (version < 2) {
            migrationCreateIndexes();
            recordMigration(2);
        }
    }

    private int getSchemaVersion() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void migrationCreatePunishmentLogs() throws SQLException {
        String sql = dbType.equals("mysql")
                ? "CREATE TABLE IF NOT EXISTS punishment_logs (id INT PRIMARY KEY AUTO_INCREMENT,type VARCHAR(32) NOT NULL,moderator VARCHAR(64) NOT NULL,target VARCHAR(64) NOT NULL,reason TEXT,date DATETIME NOT NULL,duration BIGINT DEFAULT -1) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                : "CREATE TABLE IF NOT EXISTS punishment_logs (id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL,moderator TEXT NOT NULL,target TEXT NOT NULL,reason TEXT,date TEXT NOT NULL,duration INTEGER DEFAULT -1)";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void migrationCreateIndexes() throws SQLException {
        createIndex("idx_punishment_target", "target");
        createIndex("idx_punishment_date", "date");
        createIndex("idx_punishment_type", "type");
    }

    private void createIndex(String name, String column) throws SQLException {
        String sql = "CREATE INDEX " + (dbType.equals("mysql") ? "" : "IF NOT EXISTS ") + name
                + " ON punishment_logs(" + column + ")";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            if (!isDuplicateIndex(e)) throw e;
        }
    }

    private boolean isDuplicateIndex(SQLException e) {
        String state = e.getSQLState();
        return "42000".equals(state) || e.getErrorCode() == 1061;
    }

    private void recordMigration(int version) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
            statement.setInt(1, version);
            statement.setString(2, LocalDateTime.now().format(FORMATTER));
            statement.executeUpdate();
        }
    }

    public boolean isConnected() {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    public void logPunishment(String type, String moderator, String target, String reason, long duration) {
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO punishment_logs (type, moderator, target, reason, date, duration) VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, type);
                statement.setString(2, moderator);
                statement.setString(3, target);
                statement.setString(4, reason);
                statement.setString(5, LocalDateTime.now().format(FORMATTER));
                statement.setLong(6, duration);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Не удалось записать наказание: " + e.getMessage());
            }
        }
        if (plugin.getAuditManager() != null) {
            plugin.getAuditManager().record(null, moderator, "punishment." + type, target, null,
                    reason + "; duration=" + duration);
        }
        if (plugin.getModerationCaseManager() != null) {
            plugin.getModerationCaseManager().recordPunishment(target, moderator, type, reason, duration);
        }
    }

    public List<LogEntry> getAllLogs() {
        return getLogs(0, 0, null);
    }

    public PunishmentSummary getPunishmentSummary() {
        if (dataSource == null) return PunishmentSummary.empty();
        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN type IN ('ban','tempban') AND date >= ? THEN 1 ELSE 0 END) AS today_bans, "
                + "SUM(CASE WHEN type IN ('ban','tempban') THEN 1 ELSE 0 END) AS total_bans, "
                + "SUM(CASE WHEN type = 'mute' THEN 1 ELSE 0 END) AS total_mutes, "
                + "SUM(CASE WHEN type = 'warn' THEN 1 ELSE 0 END) AS total_warns FROM punishment_logs";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, java.time.LocalDate.now().atStartOfDay().format(FORMATTER));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return new PunishmentSummary(result.getLong("total"), result.getLong("today_bans"),
                        result.getLong("total_bans"), result.getLong("total_mutes"), result.getLong("total_warns"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Не удалось получить сводку наказаний: " + e.getMessage());
        }
        return PunishmentSummary.empty();
    }

    public DetailedStats getDetailedStats() {
        if (dataSource == null) return new DetailedStats(java.util.Map.of(), List.of());
        java.util.Map<String, TypeCounts> types = new java.util.HashMap<>();
        List<StaffCount> staff = new ArrayList<>();
        String grouped = "SELECT type, COUNT(*) AS total, "
                + "SUM(CASE WHEN date >= ? THEN 1 ELSE 0 END) AS today_count, "
                + "SUM(CASE WHEN date >= ? THEN 1 ELSE 0 END) AS week_count, "
                + "SUM(CASE WHEN date >= ? THEN 1 ELSE 0 END) AS month_count "
                + "FROM punishment_logs GROUP BY type";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(grouped)) {
            java.time.LocalDate today = java.time.LocalDate.now();
            statement.setString(1, today.atStartOfDay().format(FORMATTER));
            statement.setString(2, today.minusDays(7).atStartOfDay().format(FORMATTER));
            statement.setString(3, today.minusDays(30).atStartOfDay().format(FORMATTER));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) types.put(result.getString("type"), new TypeCounts(result.getLong("total"),
                        result.getLong("today_count"), result.getLong("week_count"), result.getLong("month_count")));
            }
            try (PreparedStatement top = connection.prepareStatement("SELECT moderator, COUNT(*) AS actions "
                    + "FROM punishment_logs WHERE moderator IS NOT NULL AND moderator <> '' "
                    + "GROUP BY moderator ORDER BY actions DESC LIMIT 5");
                 ResultSet result = top.executeQuery()) {
                while (result.next()) staff.add(new StaffCount(result.getString("moderator"), result.getLong("actions")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Не удалось получить подробную статистику: " + e.getMessage());
        }
        return new DetailedStats(java.util.Map.copyOf(types), List.copyOf(staff));
    }

    public List<LogEntry> getLogsByTarget(String targetName) {
        return getLogs(0, 0, targetName);
    }

    public List<LogEntry> getLogs(int limit, int offset, String targetName) {
        if (dataSource == null) return List.of();
        List<LogEntry> logs = new ArrayList<>();
        boolean filtered = targetName != null && !targetName.isBlank();
        StringBuilder sql = new StringBuilder("SELECT * FROM punishment_logs");
        if (filtered) sql.append(" WHERE target LIKE ?");
        sql.append(" ORDER BY date DESC");
        if (limit > 0) sql.append(" LIMIT ? OFFSET ?");
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (filtered) statement.setString(index++, "%" + targetName + "%");
            if (limit > 0) {
                statement.setInt(index++, limit);
                statement.setInt(index, Math.max(0, offset));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) logs.add(mapLogEntry(result));
            }
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().severe("Не удалось получить логи: " + e.getMessage());
        }
        return logs;
    }

    public CompletableFuture<List<LogEntry>> getAllLogsAsync() {
        return CompletableFuture.supplyAsync(this::getAllLogs, executor);
    }

    public CompletableFuture<List<LogEntry>> getLogsByTargetAsync(String targetName) {
        return CompletableFuture.supplyAsync(() -> getLogsByTarget(targetName), executor);
    }

    public void clearLogs() {
        executeUpdate("DELETE FROM punishment_logs");
    }

    public int purgeOlderThan(int days) {
        if (days <= 0 || dataSource == null) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM punishment_logs WHERE date < ?")) {
            statement.setString(1, cutoff.format(FORMATTER));
            return statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Не удалось очистить старые логи: " + e.getMessage());
            return 0;
        }
    }

    private void executeUpdate(String sql) {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка базы данных: " + e.getMessage());
        }
    }

    private LogEntry mapLogEntry(ResultSet result) throws SQLException {
        LocalDateTime date = dbType.equals("mysql")
                ? result.getTimestamp("date").toLocalDateTime()
                : LocalDateTime.parse(result.getString("date"), FORMATTER);
        return new LogEntry(result.getInt("id"), result.getString("type"), result.getString("moderator"),
                result.getString("target"), result.getString("reason"), date, result.getLong("duration"));
    }

    public void close() {
        executor.shutdownNow();
        if (dataSource != null) dataSource.close();
    }
}
