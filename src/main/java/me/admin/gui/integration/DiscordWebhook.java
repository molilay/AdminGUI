package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    private final AdvancedModeratorGUI plugin;
    private String webhookUrl;

    public DiscordWebhook(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
    }

    public boolean isEnabled() {
        return !webhookUrl.isEmpty();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public void reload() {
        this.webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
    }

    public void send(String type, String player, String moderator, String reason, String duration) {
        if (!isEnabled()) return;

        String color = switch (type) {
            case "ban" -> "15158332";
            case "kick" -> "15105570";
            case "freeze" -> "1752220";
            case "warn" -> "16776960";
            case "unban" -> "3066993";
            default -> "9807270";
        };

        String title = switch (type) {
            case "ban" -> "🔨 Бан";
            case "kick" -> "👢 Кик";
            case "freeze" -> "❄ Заморозка";
            case "unfreeze" -> "✅ Разморозка";
            case "unban" -> "✅ Разбан";
            case "warn" -> "⚠ Предупреждение";
            default -> type;
        };
        String timestamp = java.time.Instant.now().toString();

        String json = String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"color\":%s,\"fields\":[" +
                "{\"name\":\"Игрок\",\"value\":\"%s\",\"inline\":true}," +
                "{\"name\":\"Модератор\",\"value\":\"%s\",\"inline\":true}," +
                "{\"name\":\"Причина\",\"value\":\"%s\",\"inline\":false}," +
                "{\"name\":\"Длительность\",\"value\":\"%s\",\"inline\":true}" +
                "],\"timestamp\":\"%s\"}]}",
                escape(title), color,
                escape(player), escape(moderator), escape(reason), escape(duration),
                timestamp
        );

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URI(webhookUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook error: " + e.getMessage());
            }
        });
    }
}
