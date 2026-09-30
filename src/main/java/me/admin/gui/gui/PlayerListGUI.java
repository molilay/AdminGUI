package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.security.ModerationActionService;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.IpPrivacyUtil;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerListGUI extends PaginatedGUI {

    private final boolean online;
    private List<OfflinePlayer> players = new ArrayList<>();
    private boolean batchMode = false;
    private final Set<UUID> selectedPlayers = new HashSet<>();
    private static final int MAX_OFFLINE_PLAYERS = 1000;
    private static final int SLOT_SEARCH_BY_NAME = 46;
    private static final int SLOT_SEARCH_BY_IP = 47;
    private static final int SLOT_RECENT_PLAYERS = 50;
    private static final int SLOT_BATCH_TOGGLE = 51;

    public PlayerListGUI(AdvancedModeratorGUI plugin, Player viewer, boolean online) {
        super(plugin, viewer);
        this.online = online;
    }

    @Override
    public void open() {
        super.open();
        if (online && viewer.hasPermission("amgui.viewip")) {
            plugin.getAuditManager().record(viewer, "ip.online-list-view", null, null,
                    "players=" + Bukkit.getOnlinePlayers().size());
        }
    }

    @Override
    public String getTitle() {
        if (batchMode) {
            String status = online ? "онлайн" : "оффлайн";
            return "&8" + status.substring(0, 1).toUpperCase() + status.substring(1) + " | Выбрано: " + selectedPlayers.size();
        }
        if (online) return plugin.getConfigManager().getGuiTitle("title-players-online");
        return plugin.getConfigManager().getGuiTitle("title-players-offline");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        players.clear();

        if (online) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p);
                contentItems.add(buildPlayerHead(p));
            }
        } else {
            OfflinePlayer[] all = Bukkit.getOfflinePlayers();
            int limit = Math.min(all.length, MAX_OFFLINE_PLAYERS);
            for (int i = 0; i < limit; i++) {
                players.add(all[i]);
                contentItems.add(buildOfflineHead(all[i]));
            }
        }
    }

    private ItemStack buildPlayerHead(Player player) {
        ItemStack head = plugin.getHeadCacheManager().getHead(player);
        ItemBuilder builder = new ItemBuilder(head);
        boolean selected = selectedPlayers.contains(player.getUniqueId());
        try {
            String rawIp = player.getAddress().getAddress().getHostAddress();
            String ip = viewer.hasPermission("amgui.viewip") ? rawIp : IpPrivacyUtil.mask(rawIp);
            builder.lore(
                    (batchMode ? "&eЛКМ — выбрать/отменить" : "&7Кликните для управления"),
                    "",
                    "&7IP: &f" + ip,
                    "&a✓ Онлайн"
            );
        } catch (Exception e) {
            builder.lore(
                    (batchMode ? "&eЛКМ — выбрать/отменить" : "&7Кликните для управления"),
                    "",
                    "&a✓ Онлайн"
            );
        }
        if (selected) {
            builder.lore("", "&a✓ Выбран");
        }
        builder.glowing(selected);
        return builder.build();
    }

    private ItemStack buildOfflineHead(OfflinePlayer player) {
        ItemStack head = plugin.getHeadCacheManager().getHead(player);
        ItemBuilder builder = new ItemBuilder(head);
        boolean selected = selectedPlayers.contains(player.getUniqueId());
        builder.lore(
                (batchMode ? "&eЛКМ — выбрать/отменить" : "&7Кликните для управления"),
                "",
                "&7✗ Оффлайн"
        );
        if (selected) {
            builder.lore("", "&a✓ Выбран");
        }
        builder.glowing(selected);
        return builder.build();
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = super.buildInventory();

        if (batchMode) {
            inv.setItem(45, new ItemBuilder(Material.LIME_WOOL)
                    .name("&aВыбрать всех")
                    .lore("&7Выбрать всех игроков на странице")
                    .glowing()
                    .build());
            inv.setItem(46, new ItemBuilder(Material.RED_WOOL)
                    .name("&cСнять выделение")
                    .lore("&7Очистить выбор")
                    .build());
            if (viewer.hasPermission("amgui.kick")) inv.setItem(47, new ItemBuilder(Material.IRON_DOOR)
                    .name("&eМассовый кик")
                    .lore("&7Кикнуть выбранных игроков")
                    .build());
            if (viewer.hasPermission("amgui.ban")) inv.setItem(48, new ItemBuilder(Material.ANVIL)
                    .name("&cМассовый бан")
                    .lore("&7Забанить выбранных игроков")
                    .build());
            if (viewer.hasPermission("amgui.warn")) inv.setItem(49, new ItemBuilder(Material.PAPER)
                    .name("&6Массовый варн")
                    .lore("&7Выдать варн выбранным игрокам")
                    .build());
            if (viewer.hasPermission("amgui.mute")) inv.setItem(50, new ItemBuilder(Material.JUKEBOX)
                    .name("&bМассовый мьют")
                    .lore("&7Заглушить выбранных игроков (1ч)")
                    .build());
            inv.setItem(SLOT_BATCH_TOGGLE, new ItemBuilder(Material.ENDER_PEARL)
                    .name("&cРежим: &eПакетный")
                    .lore("&7Нажмите для выхода из пакетного режима")
                    .glowing()
                    .build());
            inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        } else {
            if (SLOT_SEARCH_BY_NAME >= 45 && SLOT_SEARCH_BY_NAME < 54) {
                inv.setItem(SLOT_SEARCH_BY_NAME, new ItemBuilder(Material.COMPASS)
                        .name("&aПоиск по нику")
                        .lore("&7Частичное совпадение", "&7Нажмите для ввода")
                        .build());
            }
            if (viewer.hasPermission("amgui.viewip") && SLOT_SEARCH_BY_IP >= 45 && SLOT_SEARCH_BY_IP < 54) {
                inv.setItem(SLOT_SEARCH_BY_IP, new ItemBuilder(Material.REDSTONE_TORCH)
                        .name("&cПоиск по IP")
                        .lore("&7Найти всех игроков с IP", "&7Нажмите для ввода")
                        .build());
            }
            if (SLOT_RECENT_PLAYERS >= 45 && SLOT_RECENT_PLAYERS < 54) {
                inv.setItem(SLOT_RECENT_PLAYERS, new ItemBuilder(Material.CLOCK)
                        .name("&eНедавние игроки")
                        .lore("&7Онлайн-игроки в порядке входа", "&7Сейчас: &f" + Bukkit.getOnlinePlayers().size() + " онлайн")
                        .build());
            }
            if (hasAnyBatchCapability()) {
                inv.setItem(SLOT_BATCH_TOGGLE, new ItemBuilder(Material.ENDER_PEARL)
                        .name("&aРежим: &7Обычный")
                        .lore("&7Нажмите для входа в пакетный режим")
                        .build());
            }
        }

        return inv;
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            if (slot == PaginatedGUI.SLOT_CLOSE) {
                close();
                return;
            }
            if (slot == SLOT_BATCH_TOGGLE) {
                if (!batchMode && !hasAnyBatchCapability()) return;
                batchMode = !batchMode;
                if (!batchMode) selectedPlayers.clear();
                page = 0;
                refresh();
                return;
            }
            if (batchMode) {
                handleBatchClick(slot);
                return;
            }
            if (slot == SLOT_SEARCH_BY_NAME) {
                startSearch();
                return;
            }
            if (slot == SLOT_SEARCH_BY_IP) {
                startIpSearch();
                return;
            }
            if (slot == SLOT_RECENT_PLAYERS) {
                showRecent();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index >= 0 && index < players.size()) {
            OfflinePlayer target = players.get(index);
            if (batchMode) {
                UUID uuid = target.getUniqueId();
                if (selectedPlayers.contains(uuid)) {
                    selectedPlayers.remove(uuid);
                } else {
                    selectedPlayers.add(uuid);
                }
                SoundUtil.click(viewer);
                refresh();
            } else {
                new PlayerCardGUI(plugin, viewer, target).open();
            }
        }
    }

    private void handleBatchClick(int slot) {
        if (slot == 45) {
            for (OfflinePlayer p : players) {
                selectedPlayers.add(p.getUniqueId());
            }
            SoundUtil.click(viewer);
            refresh();
            return;
        }
        if (slot == 46) {
            selectedPlayers.clear();
            SoundUtil.click(viewer);
            refresh();
            return;
        }
        if (slot == 47) {
            if (!viewer.hasPermission("amgui.kick")) { deny(); return; }
            if (selectedPlayers.isEmpty()) {
                viewer.sendMessage("§cНе выбрано ни одного игрока.");
                return;
            }
            if (!validateBatch("kick")) return;
            new ConfirmGUI(plugin, viewer, "§eМассовый кик " + selectedPlayers.size() + " игроков?", () -> {
                executeBatchKick();
            }).open();
            return;
        }
        if (slot == 48) {
            if (!viewer.hasPermission("amgui.ban")) { deny(); return; }
            if (selectedPlayers.isEmpty()) {
                viewer.sendMessage("§cНе выбрано ни одного игрока.");
                return;
            }
            if (!validateBatch("ban")) return;
            new ConfirmGUI(plugin, viewer, "§cМассовый бан " + selectedPlayers.size() + " игроков?", () -> {
                new BanDurationGUI(plugin, viewer, "ban", duration -> {
                    executeBatchBan(duration);
                }).open();
            }).open();
            return;
        }
        if (slot == 49) {
            if (!viewer.hasPermission("amgui.warn")) { deny(); return; }
            if (selectedPlayers.isEmpty()) {
                viewer.sendMessage("§cНе выбрано ни одного игрока.");
                return;
            }
            if (!validateBatch("warn")) return;
            new ConfirmGUI(plugin, viewer, "§6Массовый варн " + selectedPlayers.size() + " игроков?", () -> {
                executeBatchWarn();
            }).open();
            return;
        }
        if (slot == 50) {
            if (!viewer.hasPermission("amgui.mute")) { deny(); return; }
            if (selectedPlayers.isEmpty()) {
                viewer.sendMessage("§cНе выбрано ни одного игрока.");
                return;
            }
            if (!validateBatch("mute")) return;
            new ConfirmGUI(plugin, viewer, "§bМассовый мьют " + selectedPlayers.size() + " игроков (1ч)?", () -> {
                executeBatchMute();
            }).open();
            return;
        }
    }

    private boolean validateBatch(String action) {
        int limit = plugin.getPunishmentSecurityManager().getMassActionLimit(viewer);
        if (selectedPlayers.size() > limit) {
            viewer.sendMessage("§cВыбрано слишком много игроков. Ваш лимит: " + limit);
            return false;
        }
        for (UUID uuid : selectedPlayers) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            ModerationActionService.Action mapped = ModerationActionService.Action.fromSecurityAction(action);
            var decision = mapped == null
                    ? null : ModerationActionService.preview(plugin, viewer, target, mapped);
            if (decision == null) {
                viewer.sendMessage("§cНеизвестное массовое действие: " + action);
                return false;
            }
            if (!decision.allowed()) {
                viewer.sendMessage("§cДействие отменено для " + target.getName() + ": " + decision.reason());
                return false;
            }
        }
        plugin.getAuditManager().record(viewer, "mass." + action, Integer.toString(selectedPlayers.size()), null,
                "targets=" + selectedPlayers);
        return true;
    }

    private void executeBatchKick() {
        if (!viewer.hasPermission("amgui.kick")) { deny(); return; }
        int count = 0;
        for (UUID uuid : selectedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (!executePreflight(p, ModerationActionService.Action.KICK)) continue;
                String name = p.getName();
                plugin.getInventoryRollbackManager().saveSnapshot(p, "batch_kick");
                p.kick(me.admin.gui.utils.TextUtil.legacy("§cМассовый кик от модератора."));
                plugin.getDatabaseManager().logPunishment("kick", viewer.getName(), name, "Массовый кик", -1);
                count++;
            }
        }
        viewer.sendMessage("§a✓ Массовый кик: " + count + " из " + selectedPlayers.size() + " игроков.");
        selectedPlayers.clear();
        refresh();
    }

    private void executeBatchBan(long duration) {
        if (!viewer.hasPermission("amgui.ban")) { deny(); return; }
        int count = 0;
        for (UUID uuid : selectedPlayers) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName();
            if (name == null) continue;
            ModerationActionService.Action action = duration > 0
                    ? ModerationActionService.Action.TEMPBAN : ModerationActionService.Action.BAN;
            if (!executePreflight(op, action)) continue;
            String reason = duration > 0 ? "Массовый бан (" + me.admin.gui.utils.TimeUtils.formatDuration(duration) + ")" : "Массовый бан (навсегда)";
            if (duration > 0) {
                long expires = System.currentTimeMillis() + (duration * 1000);
                me.admin.gui.utils.BanService.banProfile(op, reason,
                        java.time.Instant.ofEpochMilli(expires), viewer.getName());
            } else {
                me.admin.gui.utils.BanService.banProfile(op, reason, null, viewer.getName());
            }
            Player online = op.getPlayer();
            if (online != null && online.isOnline()) {
                online.kick(me.admin.gui.utils.TextUtil.legacy("§cВы забанены.\n§7Причина: " + reason));
            }
            plugin.getDatabaseManager().logPunishment(duration > 0 ? "tempban" : "ban", viewer.getName(), name, reason, duration);
            count++;
        }
        viewer.sendMessage("§c✓ Массовый бан: " + count + " из " + selectedPlayers.size() + " игроков.");
        selectedPlayers.clear();
        refresh();
    }

    private void executeBatchWarn() {
        if (!viewer.hasPermission("amgui.warn")) { deny(); return; }
        int count = 0;
        for (UUID uuid : selectedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (!executePreflight(p, ModerationActionService.Action.WARN)) continue;
                plugin.getWarnManager().warn(p, "Массовый варн", viewer.getName());
                count++;
            }
        }
        viewer.sendMessage("§e✓ Массовый варн: " + count + " из " + selectedPlayers.size() + " игроков.");
        selectedPlayers.clear();
        refresh();
    }

    private void executeBatchMute() {
        if (!viewer.hasPermission("amgui.mute")) { deny(); return; }
        int count = 0;
        for (UUID uuid : selectedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (!executePreflight(p, ModerationActionService.Action.MUTE)) continue;
                plugin.getMuteManager().mute(p, "Массовый мьют (1ч)", viewer.getName(), 3600L);
                count++;
            }
        }
        viewer.sendMessage("§b✓ Массовый мьют: " + count + " из " + selectedPlayers.size() + " игроков.");
        selectedPlayers.clear();
        refresh();
    }

    private boolean executePreflight(OfflinePlayer target, ModerationActionService.Action action) {
        var decision = ModerationActionService.execute(plugin, viewer, target, action);
        if (decision.allowed()) return true;
        viewer.sendMessage("§cПропущен " + (target.getName() == null ? target.getUniqueId() : target.getName())
                + ": " + decision.reason());
        return false;
    }

    public void startSearch() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите ник (можно частично):", input -> {
            if (isCancel(input)) { open(); return; }
            List<OfflinePlayer> matches = findPlayers(input);
            if (matches.isEmpty()) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                open();
            } else if (matches.size() == 1) {
                new PlayerCardGUI(plugin, viewer, matches.get(0)).open();
            } else {
                viewer.sendMessage("§eНайдено несколько игроков:");
                for (OfflinePlayer p : matches) {
                    viewer.sendMessage(" §7- §f" + p.getName());
                }
                open();
            }
        });
    }

    public void startIpSearch() {
        if (!viewer.hasPermission("amgui.viewip")) { deny(); return; }
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите IP адрес:", input -> {
            if (!viewer.hasPermission("amgui.viewip")) { deny(); return; }
            if (isCancel(input)) { open(); return; }
            String ip = input.trim();
            plugin.getAuditManager().record(viewer, "ip.search", null, null, "query=" + ip);
            List<OfflinePlayer> found = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    String playerIp = p.getAddress().getAddress().getHostAddress();
                    if (playerIp.equals(ip)) {
                        found.add(p);
                    }
                } catch (Exception ignored) {}
            }
            if (!ip.contains(".")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        String playerIp = p.getAddress().getAddress().getHostAddress();
                        if (playerIp.startsWith(ip)) {
                            found.add(p);
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (found.isEmpty()) {
                viewer.sendMessage("§cИгроков с IP §f" + ip + " §cне найдено (онлайн).");
                open();
            } else if (found.size() == 1) {
                new PlayerCardGUI(plugin, viewer, found.get(0)).open();
            } else {
                viewer.sendMessage("§eИгроки с IP §f" + ip + "§e:");
                for (OfflinePlayer p : found) {
                    viewer.sendMessage(" §7- §f" + p.getName());
                }
                open();
            }
        });
    }

    public void showRecent() {
        List<Player> ordered = Bukkit.getOnlinePlayers().stream().collect(Collectors.toList());
        if (ordered.isEmpty()) {
            viewer.sendMessage("§cНет онлайн-игроков.");
            open();
            return;
        }
        if (ordered.size() == 1) {
            new PlayerCardGUI(plugin, viewer, ordered.get(0)).open();
            return;
        }
        viewer.sendMessage("§eОнлайн игроки (" + ordered.size() + "):");
        for (Player p : ordered) {
            try {
                String rawIp = p.getAddress().getAddress().getHostAddress();
                String ip = viewer.hasPermission("amgui.viewip") ? rawIp : IpPrivacyUtil.mask(rawIp);
                viewer.sendMessage(" §7- §f" + p.getName() + " §8(§7" + ip + "§8)");
            } catch (Exception ignored) {
                viewer.sendMessage(" §7- §f" + p.getName());
            }
        }
        open();
    }

    private List<OfflinePlayer> findPlayers(String query) {
        String lower = query.toLowerCase();
        List<OfflinePlayer> exact = new ArrayList<>();
        List<OfflinePlayer> partial = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (name == null) continue;
            if (name.equalsIgnoreCase(query)) {
                exact.add(p);
                return exact;
            }
            if (name.toLowerCase().contains(lower)) {
                partial.add(p);
            }
        }
        if (!partial.isEmpty()) return partial;

        for (OfflinePlayer p : players) {
            String name = p.getName();
            if (name == null) continue;
            if (name.equalsIgnoreCase(query)) {
                exact.add(p);
                return exact;
            }
            if (name.toLowerCase().contains(lower)) {
                partial.add(p);
            }
        }
        return partial;
    }

    private boolean hasAnyBatchCapability() {
        return viewer.hasPermission("amgui.kick") || viewer.hasPermission("amgui.ban")
                || viewer.hasPermission("amgui.warn") || viewer.hasPermission("amgui.mute");
    }

    private void deny() {
        viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
    }

    private static boolean isCancel(String value) {
        return value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена");
    }

}
