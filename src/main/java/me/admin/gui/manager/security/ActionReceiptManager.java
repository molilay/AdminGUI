package me.admin.gui.manager.security;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.YamlPersistenceService;
import me.admin.gui.utils.BanService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Bounded YAML-backed receipts for narrowly defined, reversible actions. */
public final class ActionReceiptManager {

    public enum Type { BAN, TEMPBAN, MUTE, FREEZE, WARN }
    public record Receipt(UUID id, Type type, UUID targetId, String targetName,
                          UUID actorId, String actorName, long createdAt, long expiresAt,
                          String payload) { }
    public record UndoResult(boolean success, String message, Receipt receipt) { }

    private static final Map<AdvancedModeratorGUI, ActionReceiptManager> INSTANCES = new WeakHashMap<>();

    public static synchronized ActionReceiptManager forPlugin(AdvancedModeratorGUI plugin) {
        return INSTANCES.computeIfAbsent(plugin, ActionReceiptManager::new);
    }

    private final AdvancedModeratorGUI plugin;
    private final File file;
    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();

    private ActionReceiptManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "action-receipts.yml");
        load();
    }

    public synchronized Optional<Receipt> record(Player actor, OfflinePlayer target, Type type, String payload) {
        if (actor == null || target == null || type == null) return Optional.empty();
        long now = System.currentTimeMillis();
        purge(now, false);
        String safePayload = payload == null ? "" : payload;
        if (type == Type.BAN || type == Type.TEMPBAN) {
            safePayload = currentBanFingerprint(target).orElse("");
            if (safePayload.isEmpty()) return Optional.empty();
        } else if (type == Type.MUTE) {
            safePayload = currentMuteFingerprint(target.getUniqueId()).orElse("");
            if (safePayload.isEmpty()) return Optional.empty();
        }
        long ttl = Math.clamp(plugin.getConfig().getLong("action-receipts.ttl-seconds", 600L),
                30L, 86_400L) * 1000L;
        // A newer receipt of the same type supersedes an older expectation.
        receipts.values().removeIf(receipt -> receipt.targetId().equals(target.getUniqueId())
                && receipt.type() == type);
        Receipt receipt = new Receipt(UUID.randomUUID(), type, target.getUniqueId(),
                target.getName() == null ? target.getUniqueId().toString() : target.getName(),
                actor.getUniqueId(), actor.getName(), now, now + ttl, safePayload);
        receipts.put(receipt.id(), receipt);
        enforceBounds();
        try {
            save();
        } catch (RuntimeException error) {
            receipts.remove(receipt.id());
            plugin.getAuditManager().record(actor, "action.receipt-save-failed", receipt.targetName(),
                    receipt.targetId(), "type=" + type + "; error=" + error.getMessage());
            return Optional.empty();
        }
        plugin.getAuditManager().record(actor, "action.receipt-created", receipt.targetName(),
                receipt.targetId(), "id=" + receipt.id() + "; type=" + type);
        return Optional.of(receipt);
    }

    public synchronized Optional<Receipt> latestForTarget(UUID targetId) {
        purge(System.currentTimeMillis(), true);
        return receipts.values().stream().filter(receipt -> receipt.targetId().equals(targetId))
                .max(Comparator.comparingLong(Receipt::createdAt));
    }

    public boolean canUndo(Player actor, Receipt receipt) {
        if (actor == null || receipt == null || !actor.hasPermission("amgui.action.undo")) return false;
        return actor.getUniqueId().equals(receipt.actorId()) || actor.hasPermission("amgui.action.undo.others");
    }

    public synchronized UndoResult undo(Player actor, UUID receiptId) {
        purge(System.currentTimeMillis(), true);
        Receipt receipt = receipts.get(receiptId);
        if (receipt == null) return new UndoResult(false, "Квитанция не найдена или истекла.", null);
        Optional<Receipt> latest = latestForTarget(receipt.targetId());
        if (latest.isEmpty() || !latest.get().id().equals(receipt.id())) {
            return new UndoResult(false, "Можно отменить только последнее обратимое действие над игроком.", receipt);
        }
        if (!actor.hasPermission("amgui.action.undo")) {
            return denied(actor, receipt, "отсутствует amgui.action.undo");
        }
        if (!actor.getUniqueId().equals(receipt.actorId())
                && !actor.hasPermission("amgui.action.undo.others")) {
            return denied(actor, receipt, "чужое действие требует amgui.action.undo.others");
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(receipt.targetId());
        var preflight = ModerationActionService.execute(plugin, actor, target, ModerationActionService.Action.UNDO);
        if (!preflight.allowed()) return denied(actor, receipt, preflight.reason());
        if (!matchesExpectedState(receipt, target)) {
            return denied(actor, receipt, "состояние цели изменилось после действия; отмена небезопасна");
        }

        receipts.remove(receipt.id());
        try {
            save(); // durable claim before mutation => at-most-once undo
        } catch (RuntimeException error) {
            receipts.put(receipt.id(), receipt);
            return denied(actor, receipt, "не удалось надёжно зафиксировать отмену");
        }
        try {
            applyUndo(receipt, target);
        } catch (RuntimeException error) {
            receipts.put(receipt.id(), receipt);
            try { save(); } catch (RuntimeException ignored) { }
            return denied(actor, receipt, "ошибка отмены: " + error.getMessage());
        }
        plugin.getAuditManager().record(actor, "action.undo", receipt.targetName(), receipt.targetId(),
                "receipt=" + receipt.id() + "; type=" + receipt.type() + "; original-actor=" + receipt.actorName());
        return new UndoResult(true, "Действие " + receipt.type() + " отменено.", receipt);
    }

    private boolean matchesExpectedState(Receipt receipt, OfflinePlayer target) {
        return switch (receipt.type()) {
            case BAN, TEMPBAN -> BanService.isProfileBanned(target)
                    && currentBanFingerprint(target).map(receipt.payload()::equals).orElse(false);
            case MUTE -> currentMuteFingerprint(receipt.targetId()).map(receipt.payload()::equals).orElse(false);
            case FREEZE -> {
                Player online = target.getPlayer();
                if (online == null || !online.isOnline()) yield false;
                boolean previous = Boolean.parseBoolean(receipt.payload());
                yield plugin.getFreezeManager().isFrozen(online) != previous;
            }
            case WARN -> {
                try { yield plugin.getWarnManager().hasWarn(receipt.targetId(), UUID.fromString(receipt.payload())); }
                catch (IllegalArgumentException error) { yield false; }
            }
        };
    }

    private void applyUndo(Receipt receipt, OfflinePlayer target) {
        switch (receipt.type()) {
            case BAN, TEMPBAN -> BanService.pardonProfile(target);
            case MUTE -> plugin.getMuteManager().unmute(receipt.targetId());
            case FREEZE -> {
                Player online = target.getPlayer();
                if (online == null || !online.isOnline()) throw new IllegalStateException("игрок оффлайн");
                if (Boolean.parseBoolean(receipt.payload())) plugin.getFreezeManager().freeze(online);
                else plugin.getFreezeManager().unfreeze(online);
            }
            case WARN -> plugin.getWarnManager().removeWarn(receipt.targetId(), UUID.fromString(receipt.payload()));
        }
    }

    private UndoResult denied(Player actor, Receipt receipt, String reason) {
        plugin.getAuditManager().record(actor, "action.undo-denied", receipt.targetName(), receipt.targetId(),
                "receipt=" + receipt.id() + "; reason=" + reason);
        return new UndoResult(false, reason, receipt);
    }

    private void enforceBounds() {
        int maxPerPlayer = Math.clamp(plugin.getConfig().getInt("action-receipts.max-per-player", 10), 1, 100);
        int maxTotal = Math.clamp(plugin.getConfig().getInt("action-receipts.max-total", 500), maxPerPlayer, 10_000);
        List<Receipt> newest = receipts.values().stream()
                .sorted(Comparator.comparingLong(Receipt::createdAt).reversed()).toList();
        Map<UUID, Integer> perPlayer = new LinkedHashMap<>();
        int kept = 0;
        for (Receipt receipt : newest) {
            int targetCount = perPlayer.getOrDefault(receipt.targetId(), 0);
            if (targetCount >= maxPerPlayer || kept >= maxTotal) receipts.remove(receipt.id());
            else {
                perPlayer.put(receipt.targetId(), targetCount + 1);
                kept++;
            }
        }
    }

    private void purge(long now, boolean persist) {
        boolean changed = receipts.values().removeIf(receipt -> receipt.expiresAt() <= now);
        if (changed && persist) save();
    }

    private Optional<String> currentBanFingerprint(OfflinePlayer target) {
        String targetName = target.getName();
        if (targetName == null) return Optional.empty();
        return BanService.profileBans().stream()
                .filter(ban -> ban.name().equalsIgnoreCase(targetName))
                .findFirst().map(ban -> digest(ban.name() + "\n" + ban.reason() + "\n" + ban.source()
                        + "\n" + (ban.expiration() == null ? "permanent" : ban.expiration().toEpochMilli())));
    }

    private Optional<String> currentMuteFingerprint(UUID targetId) {
        var mute = plugin.getMuteManager().getMuteEntry(targetId);
        if (mute == null) return Optional.empty();
        return Optional.of(digest(mute.reason() + "\n" + mute.moderator() + "\n"
                + mute.expires() + "\n" + mute.targetName()));
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("receipts");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                String path = "receipts." + key + ".";
                Receipt receipt = new Receipt(UUID.fromString(key), Type.valueOf(yaml.getString(path + "type", "")),
                        UUID.fromString(yaml.getString(path + "target-id", "")),
                        yaml.getString(path + "target-name", "?"),
                        UUID.fromString(yaml.getString(path + "actor-id", "")),
                        yaml.getString(path + "actor-name", "?"), yaml.getLong(path + "created-at"),
                        yaml.getLong(path + "expires-at"), yaml.getString(path + "payload", ""));
                receipts.put(receipt.id(), receipt);
            } catch (Exception ignored) { }
        }
        purge(System.currentTimeMillis(), false);
        enforceBounds();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Receipt receipt : receipts.values()) {
            String path = "receipts." + receipt.id() + ".";
            yaml.set(path + "type", receipt.type().name());
            yaml.set(path + "target-id", receipt.targetId().toString());
            yaml.set(path + "target-name", receipt.targetName());
            yaml.set(path + "actor-id", receipt.actorId().toString());
            yaml.set(path + "actor-name", receipt.actorName());
            yaml.set(path + "created-at", receipt.createdAt());
            yaml.set(path + "expires-at", receipt.expiresAt());
            yaml.set(path + "payload", receipt.payload());
        }
        YamlPersistenceService.saveYamlNow(plugin, file, yaml, "action receipts");
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
