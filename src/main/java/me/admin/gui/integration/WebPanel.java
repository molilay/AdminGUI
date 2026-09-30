package me.admin.gui.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class WebPanel {

    private record RateWindow(long startedAt, int requests) {}
    private record FailedAuthWindow(long startedAt, int failures, boolean reported) {}
    private record ApiToken(String id, byte[] sha256, Set<String> scopes, long expiresAt) {
        boolean allows(String requiredScope, long now) {
            return scopeAllows(scopes, expiresAt, requiredScope, now);
        }
    }
    private enum AuthResult { AUTHORIZED, INVALID, FORBIDDEN }
    @FunctionalInterface
    private interface ExchangeHandler { void handle(HttpExchange exchange) throws Exception; }

    private final AdvancedModeratorGUI plugin;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private final Map<String, FailedAuthWindow> failedAuthWindows = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService executor;
    private List<ApiToken> tokens = List.of();
    private String corsOrigin;
    private int port;

    public WebPanel(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("web-panel.enabled", false);
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public String getPublicAddress() {
        String bindAddress = safeConfig("web-panel.bind-address", "127.0.0.1");
        return "http://" + bindAddress + ":" + plugin.getConfig().getInt("web-panel.port", 8080);
    }

    public synchronized void start() {
        if (!isEnabled() || server != null) return;
        tokens = loadTokens();
        if (tokens.isEmpty()) {
            throw new IllegalStateException("WebPanel requires web-panel.tokens or a valid legacy token");
        }
        String bindAddress = safeConfig("web-panel.bind-address", "127.0.0.1");
        try {
            boolean loopback = InetAddress.getByName(bindAddress).isLoopbackAddress();
            if (!loopback && !plugin.getConfig().getBoolean("web-panel.allow-external", false)) {
                throw new IllegalStateException("A non-loopback WebPanel bind requires web-panel.allow-external: true");
            }
        } catch (Exception error) {
            if (error instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Invalid WebPanel bind address: " + error.getMessage(), error);
        }
        corsOrigin = safeConfig("web-panel.cors-origin", "").trim();
        port = plugin.getConfig().getInt("web-panel.port", 8080);
        try {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 32);
            server.createContext("/api/stats", secured("stats", this::handleStats));
            server.createContext("/api/players", secured("stats", this::handlePlayers));
            server.createContext("/api/bans", secured("moderation", this::handleBans));
            server.createContext("/api/reports", secured("moderation", this::handleReports));
            server.createContext("/api/appeals", secured("moderation", this::handleAppeals));
            server.createContext("/api/cases", secured("moderation", this::handleCases));
            server.createContext("/api/audit", secured("audit", this::handleAudit));
            server.createContext("/api/health", secured("health", this::handleHealth));
            server.createContext("/", secured("health", this::handleRoot));
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("WebPanel запущен на http://" + bindAddress + ":" + port + " (Bearer auth включён)");
            plugin.getAuditManager().record(null, "System", "web.start", bindAddress + ":" + port, null, "WebPanel started");
        } catch (IOException e) {
            server = null;
            if (executor != null) executor.close();
            executor = null;
            throw new IllegalStateException("Could not start WebPanel: " + e.getMessage(), e);
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.close();
            executor = null;
        }
        rateWindows.clear();
        failedAuthWindows.clear();
        tokens = List.of();
    }

    private HttpHandler secured(String requiredScope, ExchangeHandler delegate) {
        return exchange -> {
            applySecurityHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "method_not_allowed");
                return;
            }
            if (!allowRequest(exchange)) {
                sendError(exchange, 429, "rate_limit_exceeded");
                return;
            }
            AuthResult auth = authorize(exchange, requiredScope);
            if (auth == AuthResult.INVALID) {
                recordFailedAuthentication(exchange);
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"AdvancedModeratorGUI\"");
                sendError(exchange, 401, "unauthorized");
                return;
            }
            if (auth == AuthResult.FORBIDDEN) {
                sendError(exchange, 403, "insufficient_scope");
                return;
            }
            try {
                delegate.handle(exchange);
            } catch (Exception e) {
                plugin.getLogger().warning("WebPanel " + exchange.getRequestURI() + ": " + e.getMessage());
                if (exchange.getResponseCode() < 0) sendError(exchange, 500, "internal_error");
            }
        };
    }

    private AuthResult authorize(HttpExchange exchange, String requiredScope) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return AuthResult.INVALID;
        String supplied = authorization.substring(7);
        if (supplied.length() < 16 || supplied.length() > 512) return AuthResult.INVALID;
        byte[] suppliedHash = sha256(supplied);
        long now = System.currentTimeMillis();
        boolean matched = false;
        for (ApiToken candidate : tokens) {
            if (!MessageDigest.isEqual(candidate.sha256(), suppliedHash)) continue;
            matched = true;
            if (candidate.allows(requiredScope, now)) return AuthResult.AUTHORIZED;
        }
        return matched ? AuthResult.FORBIDDEN : AuthResult.INVALID;
    }

    private List<ApiToken> loadTokens() {
        List<ApiToken> configured = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("web-panel.tokens");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String base = id + ".";
                String encoded = section.getString(base + "sha256", "").trim().toLowerCase(Locale.ROOT);
                if (!encoded.matches("[0-9a-f]{64}")) {
                    plugin.getLogger().warning("WebPanel token '" + id + "' skipped: sha256 must contain 64 hex characters.");
                    continue;
                }
                Set<String> scopes = section.getStringList(base + "scopes").stream()
                        .map(value -> value.toLowerCase(Locale.ROOT).trim())
                        .filter(value -> Set.of("all", "health", "stats", "moderation", "audit").contains(value))
                        .collect(Collectors.toUnmodifiableSet());
                if (scopes.isEmpty()) {
                    plugin.getLogger().warning("WebPanel token '" + id + "' skipped: no valid scopes.");
                    continue;
                }
                configured.add(new ApiToken(id, java.util.HexFormat.of().parseHex(encoded), scopes,
                        section.getLong(base + "expires-at", 0L)));
            }
        }
        String legacy = safeConfig("web-panel.token", "").trim();
        if (legacy.length() >= 16) {
            configured.add(new ApiToken("legacy", sha256(legacy), Set.of("all"), 0L));
            plugin.getLogger().warning("web-panel.token uses legacy all-access mode; migrate to scoped SHA-256 tokens.");
        }
        return List.copyOf(configured);
    }

    private void recordFailedAuthentication(HttpExchange exchange) {
        String fingerprint = clientFingerprint(exchange);
        long now = System.currentTimeMillis();
        FailedAuthWindow state = failedAuthWindows.compute(fingerprint, (key, current) -> {
            if (current == null || now - current.startedAt() >= 60_000L) return new FailedAuthWindow(now, 1, false);
            return new FailedAuthWindow(current.startedAt(), current.failures() + 1, current.reported());
        });
        if (state.failures() >= 5 && !state.reported()) {
            failedAuthWindows.put(fingerprint, new FailedAuthWindow(state.startedAt(), state.failures(), true));
            plugin.getAuditManager().record(null, "System", "web.auth-failures", "client:" + fingerprint,
                    null, "count=" + state.failures() + "; window=60s");
        }
        if (failedAuthWindows.size() > 10_000) {
            failedAuthWindows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= 60_000L);
        }
    }

    private static String clientFingerprint(HttpExchange exchange) {
        String value = exchange.getRemoteAddress().getAddress().getHostAddress();
        return java.util.HexFormat.of().formatHex(sha256(value)).substring(0, 16);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static boolean scopeAllows(Set<String> scopes, long expiresAt, String requiredScope, long now) {
        return (expiresAt <= 0L || expiresAt > now)
                && (scopes.contains("all") || scopes.contains(requiredScope));
    }

    private boolean allowRequest(HttpExchange exchange) {
        int limit = Math.max(10, plugin.getConfig().getInt("web-panel.rate-limit-per-minute", 120));
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        RateWindow result = rateWindows.compute(address, (key, current) -> {
            if (current == null || now - current.startedAt() >= 60_000L) return new RateWindow(now, 1);
            return new RateWindow(current.startedAt(), current.requests() + 1);
        });
        if (rateWindows.size() > 10_000) rateWindows.entrySet().removeIf(e -> now - e.getValue().startedAt() > 60_000L);
        return result.requests() <= limit;
    }

    private void applySecurityHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
        if (!corsOrigin.isBlank()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        String html = """
                <!doctype html><html lang="ru"><head><meta charset="UTF-8"><title>AdvancedModeratorGUI API</title>
                <style>body{font:16px system-ui;background:#111827;color:#e5e7eb;padding:40px;max-width:900px;margin:auto}
                h1{color:#fb7185}code{background:#1f2937;padding:3px 7px;border-radius:5px}</style></head><body>
                <h1>AdvancedModeratorGUI API</h1><p>Все запросы защищены заголовком <code>Authorization: Bearer TOKEN</code>.</p>
                <p>Endpoints: <code>/api/stats</code>, <code>/api/players</code>, <code>/api/bans</code>,
                <code>/api/reports</code>, <code>/api/appeals</code>, <code>/api/cases</code>, <code>/api/audit</code>, <code>/api/health</code>.</p>
                </body></html>
                """;
        html = html.replace("AdvancedModeratorGUI API",
                "AdvancedModeratorGUI " + plugin.getPluginMeta().getVersion() + " API");
        send(exchange, 200, "text/html; charset=UTF-8", html);
    }

    private void handleStats(HttpExchange exchange) throws Exception {
        sendJson(exchange, sync(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("online", Bukkit.getOnlinePlayers().size());
            data.put("max", Bukkit.getMaxPlayers());
            data.put("tps", Math.round(Bukkit.getTPS()[0] * 10.0) / 10.0);
            data.put("reports", plugin.getReportManager().getActiveCount());
            data.put("appeals", plugin.getAppealManager().getPendingCount());
            data.put("openCases", plugin.getModerationCaseManager().getOpen().size());
            data.put("auditIntegrity", plugin.getAuditManager().isIntegrityValid());
            data.put("version", plugin.getPluginMeta().getVersion());
            return data;
        }));
    }

    private void handlePlayers(HttpExchange exchange) throws Exception {
        sendJson(exchange, sync(() -> Map.of("players", Bukkit.getOnlinePlayers().stream().map(player -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", player.getName());
            item.put("uuid", player.getUniqueId().toString());
            item.put("ping", player.getPing());
            item.put("world", player.getWorld().getName());
            item.put("gamemode", player.getGameMode().name());
            return item;
        }).toList())));
    }

    private void handleBans(HttpExchange exchange) throws Exception {
        sendJson(exchange, sync(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (me.admin.gui.utils.BanService.ProfileBan entry : me.admin.gui.utils.BanService.profileBans()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("target", entry.name());
                item.put("reason", entry.reason());
                item.put("source", entry.source());
                item.put("expires", entry.expiration() == null ? null : entry.expiration().toString());
                result.add(item);
            }
            return Map.of("bans", result);
        }));
    }

    private void handleReports(HttpExchange exchange) throws Exception {
        sendJson(exchange, sync(() -> Map.of("reports", plugin.getReportManager().getAll().stream().map(report -> Map.of(
                "id", report.id(), "target", report.target(), "reason", report.reason(), "reporter", report.reporter(),
                "category", report.category(), "resolved", report.resolved())).toList())));
    }

    private void handleAppeals(HttpExchange exchange) throws Exception {
        sendJson(exchange, sync(() -> Map.of("appeals", plugin.getAppealManager().getPending().stream().map(appeal -> Map.of(
                "id", appeal.id(), "target", appeal.targetName(), "submitter", appeal.submitter(), "reason", appeal.reason())).toList())));
    }

    private void handleCases(HttpExchange exchange) throws Exception {
        sendJson(exchange, sync(() -> Map.of("cases", plugin.getModerationCaseManager().getAll().stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.id());
            value.put("target", item.targetName());
            value.put("targetUuid", item.targetUuid());
            value.put("title", item.title());
            value.put("status", item.status());
            value.put("priority", item.priority());
            value.put("assignedTo", item.assignedTo());
            value.put("updatedAt", item.updatedAt());
            value.put("dueAt", item.dueAt());
            value.put("overdue", item.dueAt() > 0 && item.dueAt() < System.currentTimeMillis()
                    && (item.status() == me.admin.gui.manager.ModerationCaseManager.Status.OPEN
                    || item.status() == me.admin.gui.manager.ModerationCaseManager.Status.INVESTIGATING));
            value.put("reports", item.reportIds());
            value.put("evidence", item.evidenceIds());
            return value;
        }).toList())));
    }

    private void handleAudit(HttpExchange exchange) throws IOException {
        boolean includeSensitiveIp = plugin.getConfig().getBoolean("web-panel.include-sensitive-ip", false);
        sendJson(exchange, Map.of("integrity", plugin.getAuditManager().isIntegrityValid(),
                "entries", plugin.getAuditManager().getRecent().stream().limit(500).map(item -> Map.of(
                        "id", item.id(), "timestamp", item.timestamp(), "actor", item.actorName(),
                        "action", item.action(),
                        "target", includeSensitiveIp ? item.targetName() : me.admin.gui.utils.IpPrivacyUtil.redactText(item.targetName()),
                        "details", includeSensitiveIp ? item.details() : me.admin.gui.utils.IpPrivacyUtil.redactText(item.details()),
                        "hash", item.hash())).toList()));
    }

    private void handleHealth(HttpExchange exchange) throws Exception {
        sendJson(exchange, sync(() -> {
            me.admin.gui.manager.AuditManager.Health audit = plugin.getAuditManager().health();
            me.admin.gui.manager.YamlPersistenceService.Health yaml =
                    me.admin.gui.manager.YamlPersistenceService.forPlugin(plugin).health();
            DiscordWebhook.Status discord = plugin.getDiscordWebhook().map(DiscordWebhook::status).orElse(null);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("version", plugin.getPluginMeta().getVersion());
            data.put("healthy", plugin.getAuditManager().isAvailableForCriticalActions() && audit.failedWrites() == 0
                    && yaml.failedWrites() == 0 && yaml.rejectedWrites() == 0
                    && plugin.getEvidenceManager().invalidEvidenceCount() == 0);
            data.put("audit", Map.of("integrity", plugin.getAuditManager().isIntegrityValid(), "queued", audit.queued(),
                    "capacity", audit.capacity(), "written", audit.written(), "failures", audit.failedWrites(),
                    "recovered", audit.recoveredWrites(), "rejected", audit.rejectedEntries(),
                    "failClosed", audit.failClosed()));
            data.put("yaml", Map.of("pending", yaml.pendingFiles(), "capacity", yaml.capacity(),
                    "writes", yaml.completedWrites(), "coalesced", yaml.coalescedWrites(),
                    "failures", yaml.failedWrites(), "rejected", yaml.rejectedWrites()));
            data.put("invalidEvidence", plugin.getEvidenceManager().invalidEvidenceCount());
            data.put("unsignedEvidence", plugin.getEvidenceManager().unsignedEvidenceCount());
            me.admin.gui.manager.PluginTaskExecutor.Health runtime = plugin.getTaskExecutor().health();
            data.put("runtime", Map.of("running", runtime.running(), "active", runtime.active(),
                    "queued", runtime.queued(), "capacity", runtime.capacity(), "submitted", runtime.submitted(),
                    "completed", runtime.completed(), "rejected", runtime.rejected(), "pending", runtime.pendingFutures()));
            me.admin.gui.manager.PlayerModerationContextCache.Health contexts =
                    plugin.getPlayerModerationContextCache().health();
            data.put("playerContexts", Map.of("entries", contexts.entries(), "refreshes", contexts.refreshes(),
                    "misses", contexts.misses(), "running", contexts.running()));
            if (discord != null) data.put("discord", Map.of("enabled", discord.enabled(), "queued", discord.queued(),
                    "sent", discord.sent(), "retries", discord.retries(), "failed", discord.failed(), "dropped", discord.dropped()));
            return data;
        }));
    }

    private <T> T sync(Callable<T> callable) throws Exception {
        if (Bukkit.isPrimaryThread()) return callable.call();
        return Bukkit.getScheduler().callSyncMethod(plugin, callable).get(3, TimeUnit.SECONDS);
    }

    private void sendJson(HttpExchange exchange, Object value) throws IOException {
        send(exchange, 200, "application/json; charset=UTF-8", toJson(value));
    }

    private void sendError(HttpExchange exchange, int status, String error) throws IOException {
        send(exchange, status, "application/json; charset=UTF-8", toJson(Map.of("error", error, "status", status)));
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Character || value instanceof Enum<?> || value instanceof java.util.UUID) {
            return "\"" + escapeJson(String.valueOf(value)) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .map(entry -> toJson(String.valueOf(entry.getKey())) + ":" + toJson(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Collection<?> collection) return collection.stream().map(this::toJson)
                .collect(Collectors.joining(",", "[", "]"));
        return toJson(String.valueOf(value));
    }

    private static String escapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.toString();
    }

    private String safeConfig(String path, String fallback) {
        String value = plugin.getConfig().getString(path);
        return value == null ? fallback : value;
    }
}
