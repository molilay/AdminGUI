package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.scheduler.BukkitTask;

public class DiscordWebhook {

    private final AdvancedModeratorGUI plugin;
    public record Status(boolean enabled, boolean workerRunning, boolean sending, int queued, long accepted,
                         long sent, long retries, long failed, long dropped, long lastSuccessAt,
                         long lastFailureAt, String lastError) {}

    private volatile String webhookUrl;
    private final ConcurrentLinkedQueue<PendingMessage> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean sending = new AtomicBoolean();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile long lastSuccessAt;
    private volatile long lastFailureAt;
    private volatile String lastError = "";
    private BukkitTask worker;
    private record PendingMessage(String json, int attempt, long notBefore) {}

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

    public void start() {
        if (worker == null) worker = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::processQueue, 20L, 20L);
    }

    public void shutdown() {
        if (worker != null) worker.cancel();
        worker = null;
        dropped.addAndGet(queue.size());
        queue.clear();
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

        accepted.incrementAndGet();
        enqueue(new PendingMessage(json, 0, System.currentTimeMillis()));
    }

    private void processQueue() {
        if (!isEnabled() || !sending.compareAndSet(false, true)) return;
        PendingMessage pending = null;
        try {
            pending = queue.peek();
            if (pending == null || pending.notBefore() > System.currentTimeMillis()) return;
            queue.poll();
            int status = post(pending.json());
            if (status >= 200 && status < 300) {
                sent.incrementAndGet();
                lastSuccessAt = System.currentTimeMillis();
                lastError = "";
                return;
            }
            retry(pending, "HTTP " + status);
        } catch (Exception e) {
            if (pending != null) retry(pending, e.getMessage());
            else plugin.getLogger().warning("Discord webhook error: " + e.getMessage());
        } finally {
            sending.set(false);
        }
    }

    private int post(String json) throws Exception {
        URI uri = new URI(webhookUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Discord webhook должен использовать HTTPS");
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", plugin.getName() + "/" + plugin.getPluginMeta().getVersion());
            conn.setConnectTimeout(Math.clamp(plugin.getConfig().getInt("discord.timeout-ms", 5000), 1000, 15000));
            conn.setReadTimeout(Math.clamp(plugin.getConfig().getInt("discord.timeout-ms", 5000), 1000, 15000));
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int status = conn.getResponseCode();
            java.io.InputStream response = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (response != null) response.close();
            return status;
        } finally {
            conn.disconnect();
        }
    }

    private void retry(PendingMessage pending, String reason) {
        int maxAttempts = Math.clamp(plugin.getConfig().getInt("discord.retry-attempts", 4), 0, 10);
        if (pending.attempt() >= maxAttempts) {
            failed.incrementAndGet();
            lastFailureAt = System.currentTimeMillis();
            lastError = reason == null ? "unknown error" : reason;
            plugin.getLogger().warning("Discord webhook: сообщение отброшено после " + pending.attempt() + " попыток: " + reason);
            return;
        }
        long delay = Math.min(60_000L, (1L << pending.attempt()) * 2000L);
        retries.incrementAndGet();
        lastFailureAt = System.currentTimeMillis();
        lastError = reason == null ? "unknown error" : reason;
        enqueue(new PendingMessage(pending.json(), pending.attempt() + 1, System.currentTimeMillis() + delay));
    }

    private void enqueue(PendingMessage message) {
        int maxQueue = Math.clamp(plugin.getConfig().getInt("discord.queue-size", 200), 10, 2000);
        while (queue.size() >= maxQueue) {
            if (queue.poll() != null) dropped.incrementAndGet();
            else break;
        }
        queue.offer(message);
    }

    public Status status() {
        return new Status(isEnabled(), worker != null, sending.get(), queue.size(), accepted.get(), sent.get(),
                retries.get(), failed.get(), dropped.get(), lastSuccessAt, lastFailureAt, lastError);
    }
}
