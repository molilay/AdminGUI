package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Read-only health audit. Database diagnostics are deliberately out of scope. */
public final class DoctorManager {

    public enum Severity { OK, WARN, ERROR }
    public record Check(String id, Severity severity, String title, String details) {}
    public record Report(List<Check> checks) {
        public long errors() { return checks.stream().filter(value -> value.severity() == Severity.ERROR).count(); }
        public long warnings() { return checks.stream().filter(value -> value.severity() == Severity.WARN).count(); }
        public boolean healthy() { return errors() == 0; }
    }

    private final AdvancedModeratorGUI plugin;

    public DoctorManager(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public Report run() {
        List<Check> checks = new ArrayList<>();
        checks.add(check("luckperms", plugin.getLuckPermsIntegration() != null, "LuckPerms API",
                plugin.getLuckPermsIntegration() != null ? "подключён" : "недоступен"));
        checks.add(new Check("audit", plugin.getAuditManager().isIntegrityValid() ? Severity.OK : Severity.ERROR,
                "Целостность аудита", plugin.getAuditManager().isIntegrityValid() ? "SHA-256 цепочка цела" : "audit.log повреждён"));

        AuditManager.Health auditHealth = plugin.getAuditManager().health();
        Severity auditQueueSeverity = auditHealth.failClosed() || auditHealth.failedWrites() > 0 ? Severity.ERROR
                : auditHealth.queued() > auditHealth.capacity() * 3 / 4 ? Severity.WARN : Severity.OK;
        checks.add(new Check("audit-writer", auditQueueSeverity, "Audit writer",
                "queue=" + auditHealth.queued() + "/" + auditHealth.capacity() + ", written="
                        + auditHealth.written() + ", failures=" + auditHealth.failedWrites()
                        + ", rejected=" + auditHealth.rejectedEntries() + ", failClosed=" + auditHealth.failClosed()
                        + (auditHealth.lastError().isBlank() ? "" : ", last=" + auditHealth.lastError())));

        PluginTaskExecutor.Health runtime = plugin.getTaskExecutor().health();
        Severity runtimeSeverity = !runtime.running() || runtime.rejected() > 0 ? Severity.ERROR
                : runtime.queued() > runtime.capacity() * 3 / 4 ? Severity.WARN : Severity.OK;
        checks.add(new Check("task-runtime", runtimeSeverity, "Bounded task runtime",
                "active=" + runtime.active() + ", queued=" + runtime.queued() + "/" + runtime.capacity()
                        + ", submitted=" + runtime.submitted() + ", completed=" + runtime.completed()
                        + ", rejected=" + runtime.rejected() + ", futures=" + runtime.pendingFutures()));

        PlayerModerationContextCache.Health contexts = plugin.getPlayerModerationContextCache().health();
        ChatHistoryManager.Health chat = plugin.getChatHistoryManager().health();
        checks.add(new Check("async-context", contexts.running() ? Severity.OK : Severity.ERROR,
                "Async moderation snapshots", "entries=" + contexts.entries() + ", refreshes="
                + contexts.refreshes() + ", misses=" + contexts.misses()));
        checks.add(new Check("chat-history", Severity.OK, "Bounded chat history", "players=" + chat.players()
                + ", messages=" + chat.messages() + ", commands=" + chat.commands() + ", sensitive-discarded="
                + chat.sensitiveCommandsDiscarded() + ", max-per-player=" + chat.maxPerPlayer()));

        YamlPersistenceService.Health persistence = YamlPersistenceService.forPlugin(plugin).health();
        Severity persistenceSeverity = persistence.failedWrites() > 0 || persistence.rejectedWrites() > 0
                ? Severity.ERROR : persistence.pendingFiles() > persistence.capacity() * 3 / 4 ? Severity.WARN : Severity.OK;
        checks.add(new Check("yaml-writer", persistenceSeverity, "Atomic YAML writer",
                "pending=" + persistence.pendingFiles() + "/" + persistence.capacity() + ", writes="
                        + persistence.completedWrites() + ", coalesced=" + persistence.coalescedWrites()
                        + ", failures=" + persistence.failedWrites() + ", last=" + persistence.lastWriteDurationMillis() + "ms"));

        if (plugin.getLifecycleCoordinator() != null) {
            List<LifecycleCoordinator.ComponentState> components = plugin.getLifecycleCoordinator().snapshot();
            long failed = components.stream().filter(component -> component.state() == LifecycleCoordinator.State.FAILED).count();
            checks.add(new Check("lifecycle", failed == 0 ? Severity.OK : Severity.ERROR, "Lifecycle компонентов",
                    "running=" + components.stream().filter(component -> component.state() == LifecycleCoordinator.State.RUNNING).count()
                            + ", failed=" + failed));
        }

        checks.add(new Check("memory-caches", Severity.OK, "Ограниченные кэши",
                "heads=" + plugin.getHeadCacheManager().cachedHeads() + ", profiles=" + plugin.getHeadCacheManager().cachedProfiles()
                        + ", head-requests=" + plugin.getHeadCacheManager().pendingRequests() + ", geoip="
                        + plugin.getGeoIpManager().cacheSize() + ", geoip-requests=" + plugin.getGeoIpManager().inFlightSize()));

        long invalidEvidence = plugin.getEvidenceManager().invalidEvidenceCount();
        long unsignedEvidence = plugin.getEvidenceManager().unsignedEvidenceCount();
        checks.add(new Check("evidence-integrity", invalidEvidence > 0 ? Severity.ERROR
                : unsignedEvidence > 0 ? Severity.WARN : Severity.OK,
                "Evidence authenticity", invalidEvidence > 0 ? "invalid entries: " + invalidEvidence
                : unsignedEvidence > 0 ? "legacy unsigned entries: " + unsignedEvidence : "all evidence records are HMAC-signed"));

        Path dataRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        checks.add(check("data-folder", Files.isDirectory(dataRoot) && Files.isWritable(dataRoot), "Каталог данных",
                dataRoot + (Files.isWritable(dataRoot) ? " доступен для записи" : " недоступен для записи")));
        validateYaml(dataRoot, checks);

        int rules = plugin.getAutoModManager().getRules().size();
        checks.add(new Check("automod", rules > 0 ? Severity.OK : Severity.WARN, "AutoMod", "загружено правил: " + rules));
        int overdue = plugin.getModerationCaseManager().getOverdue().size();
        checks.add(new Check("cases", overdue > 0 ? Severity.WARN : Severity.OK, "Дела модерации",
                overdue > 0 ? "просрочено: " + overdue : "просроченных дел нет"));
        checks.add(new Check("antiraid", plugin.getAntiRaidManager().isLockdown() ? Severity.WARN : Severity.OK,
                "AntiRaid", plugin.getAntiRaidManager().isLockdown() ? "LOCKDOWN, осталось " + plugin.getAntiRaidManager().remainingSeconds() + "с" : "норма"));

        String geoEndpoint = plugin.getConfig().getString("ip-geolocation.endpoint", "");
        boolean geoSecure;
        try { geoSecure = "https".equalsIgnoreCase(URI.create(geoEndpoint.replace("{ip}", "8.8.8.8").replace("{language}", "ru")).getScheme()); }
        catch (Exception e) { geoSecure = false; }
        checks.add(new Check("geoip", !plugin.getGeoIpManager().isEnabled() ? Severity.WARN : geoSecure ? Severity.OK : Severity.ERROR,
                "GeoIP", !plugin.getGeoIpManager().isEnabled() ? "отключён" : geoSecure ? "HTTPS endpoint настроен" : "endpoint некорректен или не HTTPS"));

        List<Path> backups = plugin.getYamlBackupManager().listBackups();
        YamlBackupManager.BackupVerification backupVerification = plugin.getYamlBackupManager().getLastVerification();
        if (backupVerification != null) checks.add(new Check("backup-integrity",
                backupVerification.valid() ? Severity.OK : Severity.ERROR, "Last backup verification",
                backupVerification.valid() ? "verified entries: " + backupVerification.entries()
                        : String.join("; ", backupVerification.errors())));
        plugin.getDiscordWebhook().ifPresent(webhook -> {
            me.admin.gui.integration.DiscordWebhook.Status status = webhook.status();
            Severity severity = status.failed() > 0 || status.dropped() > 0 ? Severity.WARN : Severity.OK;
            checks.add(new Check("discord-queue", severity, "Discord webhook",
                    "queued=" + status.queued() + ", sent=" + status.sent() + ", retries=" + status.retries()
                            + ", failed=" + status.failed() + ", dropped=" + status.dropped()));
        });
        checks.add(new Check("backups", backups.isEmpty() ? Severity.WARN : Severity.OK, "YAML-бэкапы",
                backups.isEmpty() ? "резервных копий ещё нет" : "найдено: " + backups.size()));

        boolean webEnabled = plugin.getConfig().getBoolean("web-panel.enabled", false);
        String bind = plugin.getConfig().getString("web-panel.bind-address", "127.0.0.1");
        String token = plugin.getConfig().getString("web-panel.token", "");
        var scopedTokens = plugin.getConfig().getConfigurationSection("web-panel.tokens");
        boolean hasScopedTokens = scopedTokens != null && !scopedTokens.getKeys(false).isEmpty();
        boolean localBind = "127.0.0.1".equals(bind) || "localhost".equalsIgnoreCase(bind) || "::1".equals(bind);
        boolean externalAllowed = plugin.getConfig().getBoolean("web-panel.allow-external", false);
        Severity webSeverity = !webEnabled ? Severity.OK : (token == null || token.length() < 16) && !hasScopedTokens ? Severity.ERROR
                : localBind ? Severity.OK : externalAllowed ? Severity.WARN : Severity.ERROR;
        checks.add(new Check("web", webSeverity, "Web API", !webEnabled ? "выключен" : "bind=" + bind + ", token="
                + (hasScopedTokens ? "scoped" : token != null && token.length() >= 16 ? "legacy all-access" : "не настроен")));

        me.admin.gui.config.ConfigurationValidator.Result configuration = me.admin.gui.config.ConfigurationValidator
                .validate(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(plugin.getDataFolder(), "config.yml")));
        checks.add(new Check("config-validation", configuration.valid() ? configuration.warnings().isEmpty() ? Severity.OK : Severity.WARN
                : Severity.ERROR, "Валидация конфигурации", configuration.valid()
                ? configuration.warnings().isEmpty() ? "ошибок нет" : String.join("; ", configuration.warnings())
                : String.join("; ", configuration.errors())));

        int maxItems = plugin.getConfig().getInt("gui.items-per-page", 45);
        checks.add(new Check("gui", maxItems >= 9 && maxItems <= 45 ? Severity.OK : Severity.WARN,
                "GUI", "items-per-page=" + maxItems));

        me.admin.gui.compat.ServerCompat.Version serverVersion = me.admin.gui.compat.ServerCompat.version();
        checks.add(new Check("server-version", me.admin.gui.compat.ServerCompat.isSupportedVersion() ? Severity.OK : Severity.WARN,
                "Версия сервера", "Minecraft " + serverVersion + ", минимальная " + me.admin.gui.compat.ServerCompat.MINIMUM_MAJOR
                        + "." + me.admin.gui.compat.ServerCompat.MINIMUM_MINOR + "." + me.admin.gui.compat.ServerCompat.MINIMUM_PATCH));

        List<String> compatWarnings = me.admin.gui.compat.ServerCompat.compatibilityWarnings();
        me.admin.gui.compat.ServerCompat.Platform serverPlatform = me.admin.gui.compat.ServerCompat.platform();
        checks.add(new Check("server-platform", me.admin.gui.compat.ServerCompat.isSupported() ? Severity.OK : Severity.WARN,
                "Платформа", serverPlatform + (compatWarnings.isEmpty() ? ", замечаний нет" : ": " + String.join("; ", compatWarnings))));

        return new Report(List.copyOf(checks));
    }

    private void validateYaml(Path root, List<Check> checks) {
        int valid = 0;
        List<String> broken = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 2)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return (name.endsWith(".yml") || name.endsWith(".yaml")) && !path.toString().contains("backups");
            }).toList()) {
                try {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(file.toFile());
                    valid++;
                } catch (Exception e) {
                    broken.add(root.relativize(file) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            broken.add("ошибка сканирования: " + e.getMessage());
        }
        checks.add(new Check("yaml", broken.isEmpty() ? Severity.OK : Severity.ERROR, "YAML-файлы",
                broken.isEmpty() ? "проверено: " + valid : String.join("; ", broken).substring(0, Math.min(240, String.join("; ", broken).length()))));
    }

    private static Check check(String id, boolean success, String title, String details) {
        return new Check(id, success ? Severity.OK : Severity.ERROR, title, details);
    }
}
