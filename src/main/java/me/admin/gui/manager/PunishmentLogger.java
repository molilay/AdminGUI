package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PunishmentLogger {

    private final AdvancedModeratorGUI plugin;
    private final File logFile;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PunishmentLogger(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "logs");
        dir.mkdirs();
        this.logFile = new File(dir, "punishments.log");
    }

    public void log(String type, String target, String moderator, String reason, String duration) {
        String line = String.format("[%s] [%s] %s -> %s | %s | %s",
                LocalDateTime.now().format(FMT), type.toUpperCase(), moderator, target, reason, duration);
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write punishment log: " + e.getMessage());
        }
    }
}
