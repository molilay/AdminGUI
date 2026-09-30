package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.integration.AuthMeIntegration;
import me.admin.gui.manager.security.AuthMeActionApprovalQueue;
import me.admin.gui.manager.security.ModerationActionService;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class AuthMeGUI extends PaginatedGUI {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final Map<AdvancedModeratorGUI, AuthMeActionApprovalQueue> APPROVALS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final OfflinePlayer target;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_FORCE_LOGIN = 29;
    private static final int SLOT_FORCE_LOGOUT = 31;
    private static final int SLOT_RECOVERY_TOKEN = 33;
    private static final int SLOT_BACK = 49;

    public AuthMeGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public String getTitle() {
        return "&8AuthMe: " + (target.getName() != null ? target.getName() : "?");
    }

    @Override public void buildContent() { }

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.authme.manage")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
        plugin.getAuditManager().record(viewer, "authme.view", target.getName(), target.getUniqueId(),
                "ip-visible=" + viewer.hasPermission("amgui.viewip") + "; password-hash-exposed=false");
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, 54,
                me.admin.gui.utils.TextUtil.legacy(getTitle()));
        fillBorder(inventory);
        AuthMeIntegration auth = plugin.getAuthMeIntegration().orElse(null);
        String name = target.getName();
        boolean available = auth != null && auth.isEnabled() && name != null;

        ItemBuilder information = new ItemBuilder(Material.PAPER).name("&6Информация AuthMe");
        if (!available) {
            information.lore("&cAuthMe не подключён");
        } else {
            boolean registered = auth.isRegistered(name);
            information.lore("&7Статус: " + (registered ? "&aзарегистрирован" : "&cне зарегистрирован"));
            Player online = target.getPlayer();
            if (online != null && online.isOnline()) {
                information.lore("&7Авторизация: " + (auth.isAuthenticated(online) ? "&aвыполнена" : "&cне выполнена"));
            }
            String lastIp = auth.getLastIp(name);
            if (lastIp != null && !lastIp.isBlank()) {
                information.lore("&7Последний IP: &f" + (viewer.hasPermission("amgui.viewip")
                        ? lastIp : me.admin.gui.utils.IpPrivacyUtil.mask(lastIp)));
            }
            information.lore("", "&8Хеш пароля никогда не отображается");

            if (registered && viewer.hasPermission("amgui.authme.sensitive")) {
                if (target.isOnline()) {
                    inventory.setItem(SLOT_FORCE_LOGIN, new ItemBuilder(Material.GREEN_WOOL)
                            .name("&aЗапросить принудительный вход")
                            .lore("&7Требует второго сотрудника", "&7Повторный клик другого сотрудника подтверждает")
                            .build());
                    inventory.setItem(SLOT_FORCE_LOGOUT, new ItemBuilder(Material.RED_WOOL)
                            .name("&cЗапросить принудительный выход")
                            .lore("&7Требует второго сотрудника", "&7Повторный клик другого сотрудника подтверждает")
                            .build());
                }
                inventory.setItem(SLOT_RECOVERY_TOKEN, new ItemBuilder(Material.ENDER_EYE)
                        .name("&dСоздать recovery-токен")
                        .lore("&7Не требует ввода пароля в чат", "&7Требует второго сотрудника",
                                "&7Токен показывается один раз в защищённом GUI")
                        .glowing().build());
            }
        }

        inventory.setItem(SLOT_INFO, information.build());
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name("&7← Назад к карточке").build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }

    @Override
    public void onClick(int slot) {
        SoundUtil.click(viewer);
        if (slot == SLOT_BACK) {
            new PlayerCardGUI(plugin, viewer, target).open();
            return;
        }
        if (slot == SLOT_CLOSE) {
            close();
            return;
        }
        if (slot == SLOT_INFO) {
            viewer.sendMessage("§8[§5AuthMe§8] §7Хеши и пароли не отображаются. Используйте recovery-токен.");
            return;
        }

        AuthMeIntegration auth = plugin.getAuthMeIntegration().orElse(null);
        String name = target.getName();
        if (auth == null || !auth.isEnabled() || name == null) return;
        AuthMeActionApprovalQueue.Action action = switch (slot) {
            case SLOT_FORCE_LOGIN -> AuthMeActionApprovalQueue.Action.FORCE_LOGIN;
            case SLOT_FORCE_LOGOUT -> AuthMeActionApprovalQueue.Action.FORCE_LOGOUT;
            case SLOT_RECOVERY_TOKEN -> AuthMeActionApprovalQueue.Action.RECOVERY_TOKEN;
            default -> null;
        };
        if (action == null) return;
        requestOrApprove(auth, name, action);
    }

    private void requestOrApprove(AuthMeIntegration auth, String name, AuthMeActionApprovalQueue.Action action) {
        if (!viewer.hasPermission("amgui.authme.manage") || !viewer.hasPermission("amgui.authme.sensitive")) {
            deny("отозвано право AuthMe");
            return;
        }
        var preview = ModerationActionService.preview(plugin, viewer, target, ModerationActionService.Action.AUTHME);
        if (!preview.allowed()) {
            deny(preview.reason());
            return;
        }

        long ttl = Math.clamp(plugin.getConfig().getLong("security.dual-approval.expire-seconds", 600),
                30L, 3600L) * 1000L;
        var decision = approvals().requestOrApprove(viewer.getUniqueId(), target.getUniqueId(), action,
                System.currentTimeMillis(), ttl);
        if (decision.status() == AuthMeActionApprovalQueue.Status.REQUESTED) {
            plugin.getAuditManager().record(viewer, "authme.approval-request", name, target.getUniqueId(),
                    "action=" + action + "; credential-stored=false");
            viewer.sendMessage("§eЗапрос создан. Другой сотрудник должен открыть AuthMe игрока и нажать то же действие.");
            Bukkit.getOnlinePlayers().stream()
                    .filter(staff -> !staff.getUniqueId().equals(viewer.getUniqueId()))
                    .filter(staff -> staff.hasPermission("amgui.authme.manage")
                            && staff.hasPermission("amgui.authme.sensitive"))
                    .forEach(staff -> staff.sendMessage("§8[§5AuthMe§8] §e" + viewer.getName()
                            + " запросил " + label(action) + " для §f" + name + "§e. Подтвердите в AuthMe GUI игрока."));
            return;
        }
        if (decision.status() == AuthMeActionApprovalQueue.Status.OWN_REQUEST) {
            viewer.sendMessage("§cНельзя подтвердить собственный запрос.");
            return;
        }
        if (decision.status() == AuthMeActionApprovalQueue.Status.FULL) {
            deny("очередь подтверждений заполнена; повторите после истечения старых запросов");
            return;
        }

        Player requester = Bukkit.getPlayer(decision.requester());
        if (requester == null || !requester.isOnline()
                || !requester.hasPermission("amgui.authme.manage")
                || !requester.hasPermission("amgui.authme.sensitive")) {
            deny("инициатор вышел или потерял права; создайте новый запрос");
            return;
        }
        var preflight = ModerationActionService.execute(plugin, viewer, target, ModerationActionService.Action.AUTHME);
        if (!preflight.allowed()) {
            deny(preflight.reason());
            return;
        }
        executeApproved(auth, name, action, requester);
    }

    private void executeApproved(AuthMeIntegration auth, String name,
                                 AuthMeActionApprovalQueue.Action action, Player requester) {
        Player online = target.getPlayer();
        boolean success;
        switch (action) {
            case FORCE_LOGIN -> {
                if (online == null || !online.isOnline()) { deny("игрок уже оффлайн"); return; }
                auth.forceLogin(online);
                success = true;
                viewer.sendMessage("§a✓ Игрок " + name + " авторизован после двойного подтверждения.");
            }
            case FORCE_LOGOUT -> {
                if (online == null || !online.isOnline()) { deny("игрок уже оффлайн"); return; }
                auth.forceLogout(online);
                success = true;
                viewer.sendMessage("§a✓ Игрок " + name + " разлогинен после двойного подтверждения.");
            }
            case RECOVERY_TOKEN -> {
                String token = generateToken();
                success = auth.setPassword(name, token);
                if (success) {
                    if (online != null && online.isOnline()) auth.forceLogout(online);
                    new RecoveryTokenGUI(plugin, viewer, target, token).open();
                }
            }
            default -> success = false;
        }
        if (!success) {
            SoundUtil.error(viewer);
            viewer.sendMessage("§cНе удалось выполнить действие AuthMe.");
            return;
        }
        SoundUtil.success(viewer);
        plugin.getAuditManager().record(viewer, "authme.approval-execute", name, target.getUniqueId(),
                "action=" + action + "; requester=" + requester.getName() + "; credential-redacted=true");
    }

    private AuthMeActionApprovalQueue approvals() {
        synchronized (APPROVALS) {
            return APPROVALS.computeIfAbsent(plugin, ignored -> new AuthMeActionApprovalQueue());
        }
    }

    private void deny(String reason) {
        viewer.sendMessage("§cДействие AuthMe запрещено: " + reason);
        plugin.getAuditManager().record(viewer, "authme.action-denied", target.getName(),
                target.getUniqueId(), reason);
    }

    private static String label(AuthMeActionApprovalQueue.Action action) {
        return switch (action) {
            case FORCE_LOGIN -> "принудительный вход";
            case FORCE_LOGOUT -> "принудительный выход";
            case RECOVERY_TOKEN -> "recovery-токен";
        };
    }

    private static String generateToken() {
        StringBuilder token = new StringBuilder(24);
        for (int i = 0; i < 24; i++) token.append(TOKEN_ALPHABET.charAt(SECURE_RANDOM.nextInt(TOKEN_ALPHABET.length())));
        return token.toString();
    }

    protected void fillBorder(Inventory inventory) {
        for (int i = 0; i < 54; i++) if (inventory.getItem(i) == null) inventory.setItem(i, ItemBuilder.createFiller());
    }

    /** Token lives only in this GUI instance and is never sent through chat or audit. */
    private static final class RecoveryTokenGUI extends PaginatedGUI {
        private final OfflinePlayer target;
        private final String token;

        private RecoveryTokenGUI(AdvancedModeratorGUI plugin, Player viewer,
                                 OfflinePlayer target, String token) {
            super(plugin, viewer);
            this.target = target;
            this.token = token;
        }

        @Override public String getTitle() { return "&8Одноразовый recovery-токен"; }
        @Override public void buildContent() { }

        @Override
        protected Inventory buildInventory() {
            Inventory inventory = Bukkit.createInventory(null, 27,
                    me.admin.gui.utils.TextUtil.legacy(getTitle()));
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, ItemBuilder.createFiller());
            inventory.setItem(13, new ItemBuilder(Material.PAPER).name("&dRecovery-токен для " + target.getName())
                    .lore("&f" + token, "", "&cПоказывается только сейчас",
                            "&7Передайте игроку по защищённому каналу", "&7После входа игрок должен сменить пароль")
                    .glowing().build());
            inventory.setItem(22, ItemBuilder.createCloseButton());
            return inventory;
        }

        @Override public void onClick(int slot) { if (slot == 22) close(); }
    }
}
