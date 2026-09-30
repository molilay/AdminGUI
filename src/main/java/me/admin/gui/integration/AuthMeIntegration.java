package me.admin.gui.integration;

import fr.xephi.authme.api.v3.AuthMeApi;
import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AuthMeIntegration {

    private final AdvancedModeratorGUI plugin;
    private AuthMeApi authMeApi;
    private boolean enabled = false;

    public AuthMeIntegration(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("AuthMe") == null) return false;
        try {
            authMeApi = AuthMeApi.getInstance();
            enabled = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAuthenticated(Player player) {
        if (!enabled) return true;
        if (player == null) return true;
        try {
            return authMeApi.isAuthenticated(player);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isRegistered(String name) {
        if (!enabled) return false;
        try {
            return authMeApi.isRegistered(name);
        } catch (Exception e) {
            return false;
        }
    }

    public String getPasswordHash(String name) {
        if (!enabled || name == null) return null;
        try {
            File authMeFolder = Bukkit.getPluginManager().getPlugin("AuthMe").getDataFolder();
            File authMeConfig = new File(authMeFolder, "config.yml");
            if (!authMeConfig.exists()) return null;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(authMeConfig);
            String backend = config.getString("DataSource.backend", "sqlite");

            if ("mysql".equalsIgnoreCase(backend)) {
                return queryPasswordMySQL(config, name);
            } else {
                return queryPasswordSQLite(authMeFolder, name);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("AuthMe getPasswordHash error: " + e.getMessage());
            return null;
        }
    }

    private String queryPasswordSQLite(File authMeFolder, String name) {
        File dbFile = new File(authMeFolder, "authme.db");
        if (!dbFile.exists()) return null;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password FROM authme WHERE LOWER(username) = LOWER(?) OR LOWER(realname) = LOWER(?)")) {
            ps.setString(1, name);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("AuthMe SQLite query error: " + e.getMessage());
        }
        return null;
    }

    private String queryPasswordMySQL(YamlConfiguration authMeConfig, String name) {
        String host = authMeConfig.getString("DataSource.mySQLHost", "localhost");
        int port = authMeConfig.getInt("DataSource.mySQLPort", 3306);
        String database = authMeConfig.getString("DataSource.mySQLDatabase", "authme");
        String user = authMeConfig.getString("DataSource.mySQLUser", "root");
        String pass = authMeConfig.getString("DataSource.mySQLPass", "");
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false", user, pass);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password FROM authme WHERE LOWER(username) = LOWER(?) OR LOWER(realname) = LOWER(?)")) {
            ps.setString(1, name);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("AuthMe MySQL query error: " + e.getMessage());
        }
        return null;
    }

    public String getLastIp(String name) {
        if (!enabled) return null;
        try {
            return authMeApi.getLastIp(name);
        } catch (Exception e) {
            return queryLastIpFromDB(name);
        }
    }

    private String queryLastIpFromDB(String name) {
        try {
            File authMeFolder = Bukkit.getPluginManager().getPlugin("AuthMe").getDataFolder();
            File authMeConfig = new File(authMeFolder, "config.yml");
            if (!authMeConfig.exists()) return null;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(authMeConfig);
            String backend = config.getString("DataSource.backend", "sqlite");

            String ip = null;
            if ("mysql".equalsIgnoreCase(backend)) {
                ip = queryStringMySQL(config, "SELECT ip FROM authme WHERE LOWER(username)=LOWER(?)", name);
            } else {
                File dbFile = new File(authMeFolder, "authme.db");
                if (dbFile.exists()) {
                    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                         PreparedStatement ps = conn.prepareStatement(
                                 "SELECT ip FROM authme WHERE LOWER(username)=LOWER(?) OR LOWER(realname)=LOWER(?)")) {
                        ps.setString(1, name);
                        ps.setString(2, name);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) ip = rs.getString("ip");
                        }
                    }
                }
            }
            return (ip != null && !ip.isEmpty()) ? ip : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getEmail(String name) {
        if (!enabled) return null;
        return queryEmailFromDB(name);
    }

    private String queryEmailFromDB(String name) {
        try {
            File authMeFolder = Bukkit.getPluginManager().getPlugin("AuthMe").getDataFolder();
            File authMeConfig = new File(authMeFolder, "config.yml");
            if (!authMeConfig.exists()) return null;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(authMeConfig);
            String backend = config.getString("DataSource.backend", "sqlite");

            if ("mysql".equalsIgnoreCase(backend)) {
                return queryStringMySQL(config, "SELECT email FROM authme WHERE LOWER(username)=LOWER(?)", name);
            } else {
                File dbFile = new File(authMeFolder, "authme.db");
                if (!dbFile.exists()) return null;
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT email FROM authme WHERE LOWER(username)=LOWER(?) OR LOWER(realname)=LOWER(?)")) {
                    ps.setString(1, name);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String email = rs.getString("email");
                            return (email != null && !email.isEmpty()) ? email : null;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String queryStringMySQL(YamlConfiguration authMeConfig, String query, String param) {
        String host = authMeConfig.getString("DataSource.mySQLHost", "localhost");
        int port = authMeConfig.getInt("DataSource.mySQLPort", 3306);
        String database = authMeConfig.getString("DataSource.mySQLDatabase", "authme");
        String user = authMeConfig.getString("DataSource.mySQLUser", "root");
        String pass = authMeConfig.getString("DataSource.mySQLPass", "");
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false", user, pass);
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString(1);
                    return (val != null && !val.isEmpty()) ? val : null;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("AuthMe MySQL query error: " + e.getMessage());
        }
        return null;
    }

    public boolean setPassword(String name, String newPassword) {
        if (!enabled) return false;
        try {
            authMeApi.changePassword(name, newPassword);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean unregister(String name) {
        if (!enabled) return false;
        try {
            authMeApi.forceUnregister(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void forceLogin(Player player) {
        if (!enabled) return;
        try {
            authMeApi.forceLogin(player);
        } catch (Exception ignored) {}
    }

    public void forceLogout(Player player) {
        if (!enabled) return;
        try {
            authMeApi.forceLogout(player);
        } catch (Exception ignored) {}
    }
}
