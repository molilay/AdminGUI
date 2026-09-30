package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.manager.FreezeManager;
import me.admin.gui.manager.InventoryRollbackManager;
import me.admin.gui.manager.MuteManager;
import me.admin.gui.manager.WarnManager;
import me.admin.gui.manager.security.ModerationActionService;
import me.admin.gui.manager.security.ActionReceiptManager;
import me.admin.gui.integration.LuckPermsIntegration;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerCardGUI extends PaginatedGUI {

    private final OfflinePlayer target;
    private Player onlineTarget;

    private static final int SLOT_INFO = 4;
    private static final int SLOT_KICK = 10;
    private static final int SLOT_BAN = 11;
    private static final int SLOT_IP_BAN = 12;
    private static final int SLOT_FREEZE = 13;
    private static final int SLOT_TP_TO = 14;
    private static final int SLOT_TP_HERE = 15;
    private static final int SLOT_GIVE_ITEM = 16;
    private static final int SLOT_CHANGE_GROUP = 19;
    private static final int SLOT_CLEAR_INV = 20;
    private static final int SLOT_VIEW_INV = 21;
    private static final int SLOT_VIEW_EC = 22;
    private static final int SLOT_WARN = 23;
    private static final int SLOT_MUTE = 24;
    private static final int SLOT_ALTS = 25;
    private static final int SLOT_HISTORY = 28;
    private static final int SLOT_ROLLBACK = 29;
    private static final int SLOT_WARN_LIST = 30;
    private static final int SLOT_NOTES = 31;
    private static final int SLOT_IP_INFO = 32;
    private static final int SLOT_SNAPSHOTS = 33;
    private static final int SLOT_UNBAN = 34;
    private static final int SLOT_AUTHME = 35;
    private static final int SLOT_PERMISSIONS = 37;
    private static final int SLOT_IP_HISTORY = 38;
    private static final int SLOT_CLIENT_INFO = 39;
    private static final int SLOT_CHAT_HISTORY = 27;
    private static final int SLOT_FLAG = 36;
    private static final int SLOT_COREPROTECT = 40;
    private static final int SLOT_UNDO = 41;

    public PlayerCardGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
        if (target.isOnline()) {
            this.onlineTarget = target.getPlayer();
        }
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-player-card", "player", target.getName());
    }

    @Override
    public void buildContent() {}

    @Override
    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));

        inv.setItem(SLOT_INFO, buildInfoItem());
        setIf(inv, SLOT_KICK, "amgui.kick", buildKickItem());
        setIf(inv, SLOT_BAN, "amgui.ban", buildBanItem());
        setIf(inv, SLOT_IP_BAN, "amgui.ban", buildIpBanItem());
        setIf(inv, SLOT_UNBAN, "amgui.ban", buildUnbanItem());
        setIf(inv, SLOT_FREEZE, "amgui.freeze", buildFreezeItem());
        setIf(inv, SLOT_TP_TO, "amgui.teleport", buildTpToItem());
        setIf(inv, SLOT_TP_HERE, "amgui.teleport", buildTpHereItem());
        setIf(inv, SLOT_GIVE_ITEM, "amgui.player", buildGiveItem());
        setIf(inv, SLOT_CHANGE_GROUP, "amgui.groups.assign", buildChangeGroupItem());
        setIf(inv, SLOT_CLEAR_INV, "amgui.inventory.edit", buildClearInvItem());
        setIf(inv, SLOT_VIEW_INV, "amgui.inventory.view", buildViewInvItem());
        setIf(inv, SLOT_VIEW_EC, "amgui.inventory.view", buildViewEcItem());
        setIf(inv, SLOT_WARN, "amgui.warn", buildWarnItem());
        setIf(inv, SLOT_MUTE, "amgui.mute", buildMuteItem());
        setIf(inv, SLOT_ALTS, "amgui.alts", buildAltsItem());
        setIf(inv, SLOT_HISTORY, "amgui.logs", buildHistoryItem());
        setIf(inv, SLOT_ROLLBACK, "amgui.rollback", buildRollbackItem());
        setIf(inv, SLOT_WARN_LIST, "amgui.warn", buildWarnListItem());
        setIf(inv, SLOT_NOTES, "amgui.notes", buildNotesItem());
        setIf(inv, SLOT_IP_INFO, "amgui.viewip", buildIpInfoItem());
        setIf(inv, SLOT_SNAPSHOTS, "amgui.rollback", buildSnapshotsItem());
        setIf(inv, SLOT_AUTHME, "amgui.authme.manage", buildAuthMeItem());
        setIf(inv, SLOT_PERMISSIONS, "amgui.permissions", buildPermissionsItem());
        setIf(inv, SLOT_IP_HISTORY, "amgui.viewip", buildIpHistoryItem());
        setIf(inv, SLOT_CLIENT_INFO, "amgui.player", buildClientInfoItem());
        setIf(inv, SLOT_CHAT_HISTORY, "amgui.logs", buildChatHistoryItem());
        setIf(inv, SLOT_FLAG, "amgui.notes", buildFlagItem());
        setIf(inv, SLOT_COREPROTECT, "amgui.coreprotect", buildCoreProtectItem());
        if (viewer.hasPermission("amgui.action.undo")) {
            receipts().latestForTarget(target.getUniqueId())
                    .filter(receipt -> receipts().canUndo(viewer, receipt))
                    .ifPresent(receipt -> inv.setItem(SLOT_UNDO, buildUndoItem(receipt)));
        }

        for (int i = 0; i < SIZE; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemBuilder.createFiller());
        }

        inv.setItem(SLOT_MAIN_MENU, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inv;
    }

    private void setIf(Inventory inventory, int slot, String permission, ItemStack item) {
        if (viewer.hasPermission(permission)) inventory.setItem(slot, item);
    }

    @Override
    public void open() {
        super.open();
    }

    @Override
    public void onClick(int slot) {
        onClick(slot, false);
    }

    @Override
    public void onClick(int slot, boolean shift) {
        SoundUtil.click(viewer);
        if (shift) {
            handleShiftClick(slot);
            return;
        }
        switch (slot) {
            case SLOT_INFO -> {
                if (viewer.hasPermission("amgui.investigation.center")) new InvestigationCenterGUI(plugin, viewer, target).open();
                else viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
            case SLOT_KICK -> handleKick();
            case SLOT_BAN -> handleBan();
            case SLOT_IP_BAN -> handleIpBan();
            case SLOT_UNBAN -> handleUnban();
            case SLOT_FREEZE -> handleFreeze();
            case SLOT_TP_TO -> handleTpTo();
            case SLOT_TP_HERE -> handleTpHere();
            case SLOT_GIVE_ITEM -> handleGiveItem();
            case SLOT_CHANGE_GROUP -> handleChangeGroup();
            case SLOT_CLEAR_INV -> handleClearInv();
            case SLOT_VIEW_INV -> handleViewInv();
            case SLOT_VIEW_EC -> handleViewEc();
            case SLOT_WARN -> handleWarn();
            case SLOT_MUTE -> handleMute();
            case SLOT_ALTS -> handleAlts();
            case SLOT_HISTORY -> handleHistory();
            case SLOT_ROLLBACK -> handleRollback();
            case SLOT_WARN_LIST -> handleWarnList();
            case SLOT_NOTES -> handleNotes();
            case SLOT_IP_INFO -> handleIpInfo();
            case SLOT_SNAPSHOTS -> handleSnapshots();
            case SLOT_AUTHME -> handleAuthMe();
            case SLOT_PERMISSIONS -> handlePermissions();
            case SLOT_IP_HISTORY -> handleIpHistory();
            case SLOT_CLIENT_INFO -> handleClientInfo();
            case SLOT_CHAT_HISTORY -> handleChatHistory();
            case SLOT_FLAG -> handleFlag();
            case SLOT_COREPROTECT -> handleCoreProtect();
            case SLOT_UNDO -> handleUndo();
            case SLOT_MAIN_MENU -> {
                openHome();
            }
            case SLOT_CLOSE -> close();
        }
    }

    private ItemStack buildInfoItem() {
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD);
        if (target.getName() != null) {
            ItemStack head = plugin.getHeadCacheManager().getHead(target);
            builder = new ItemBuilder(head);
        }

        builder.name("&e" + target.getName());
        builder.lore(
                "&7UUID: &f" + target.getUniqueId().toString().substring(0, 8) + "...",
                "&7Статус: " + (target.isOnline() ? "&a✔ Онлайн" : "&c✘ Оффлайн"),
                "", "&eКлик — центр расследования"
        );

        Player fresh = target.getPlayer();
        if (target.isOnline() && fresh != null) {
            builder.lore(
                    "",
                    "&7Мир: &f" + fresh.getWorld().getName(),
                    "&7Здоровье: &f" + (int) fresh.getHealth() + " ❤",
                    "&7Голод: &f" + fresh.getFoodLevel() + " 🍗",
                    "&7Режим: &f" + fresh.getGameMode().name(),
                    "&7Пинг: &f" + fresh.getPing() + " ms"
            );

            if (viewer.hasPermission("amgui.viewip")) {
                String ip = fresh.getAddress() != null ?
                        fresh.getAddress().getAddress().getHostAddress() : "unknown";
                builder.lore("&7IP: &f" + ip);
            }

            LuckPermsIntegration lp = plugin.getLuckPermsIntegration();
            String group = lp.getPrimaryGroup(target.getUniqueId());
            builder.lore("&7Группа: &f" + group);

            if (viewer.hasPermission("amgui.warn")) {
                int warns = plugin.getWarnManager().getWarnCount(target.getUniqueId());
                builder.lore("&7Варнов: &e" + warns + "/" + plugin.getWarnManager().getMaxWarns());
            }
            if (viewer.hasPermission("amgui.mute")) {
                boolean muted = plugin.getMuteManager().isMuted(target.getUniqueId());
                builder.lore("&7Мут: " + (muted ? "&cДа" : "&aНет"));
            }
            String flag = plugin.getFlagManager().getFlag(target.getUniqueId());
            if (flag != null) {
                builder.lore("&7Флаг: " + plugin.getFlagManager().formatFlag(flag));
            }
        } else {
            String flag = plugin.getFlagManager().getFlag(target.getUniqueId());
            if (flag != null) {
                builder.lore("", "&7Флаг: " + plugin.getFlagManager().formatFlag(flag));
            }
            String lastIp = plugin.getAltDetector().getLastIp(target.getUniqueId());
            if (!lastIp.isEmpty() && viewer.hasPermission("amgui.viewip")) {
                builder.lore("", "&7Последний IP: &f" + lastIp);
            }
        }

        return builder.build();
    }

    private ItemStack buildKickItem() {
        boolean can = target.isOnline();
        return new ItemBuilder(can ? Material.IRON_DOOR : Material.BARRIER)
                .name(can ? "&cКикнуть" : "&7Кикнуть")
                .lore(can ? "&7Выгнать игрока с сервера" : "&cИгрок оффлайн")
                .build();
    }

    private ItemStack buildBanItem() {
        return new ItemBuilder(Material.ANVIL)
                .name("&cЗабанить")
                .lore("&7Перманентный или временный бан")
                .build();
    }

    private ItemStack buildIpBanItem() {
        return new ItemBuilder(Material.REDSTONE_BLOCK)
                .name("&cЗабанить по IP")
                .lore("&7Бан IP-адреса игрока", "&7(все аккаунты с этого IP)")
                .build();
    }

    private ItemStack buildUnbanItem() {
        String name = target.getName();
        if (name == null) {
            return new ItemBuilder(Material.GRAY_WOOL)
                    .name("&7Неизвестный игрок")
                    .build();
        }
        boolean isBanned = me.admin.gui.utils.BanService.isProfileBanned(target);
        return new ItemBuilder(isBanned ? Material.GREEN_WOOL : Material.GRAY_WOOL)
                .name(isBanned ? "&aРазбанить" : "&7Не забанен")
                .lore(isBanned ? "&7Нажмите для разбана" : "&7Игрок не в бане")
                .build();
    }

    private ItemStack buildFreezeItem() {
        Player freshTarget = target.getPlayer();
        boolean frozen = freshTarget != null && plugin.getFreezeManager().isFrozen(freshTarget);
        boolean online = target.isOnline();
        return new ItemBuilder(online ? (frozen ? Material.ICE : Material.PACKED_ICE) : Material.BARRIER)
                .name(online ? (frozen ? "&aРазморозить" : "&bЗаморозить") : "&7Заморозить")
                .lore(online ? (frozen ? "&7Снять заморозку" : "&7Заморозить игрока") : "&cИгрок оффлайн")
                .build();
    }

    private ItemStack buildTpToItem() {
        boolean online = target.isOnline();
        return new ItemBuilder(online ? Material.ENDER_PEARL : Material.BARRIER)
                .name(online ? "&dТП к игроку" : "&7ТП к игроку")
                .lore(online ? "&7Переместиться к игроку" : "&cИгрок оффлайн")
                .build();
    }

    private ItemStack buildTpHereItem() {
        boolean online = target.isOnline();
        return new ItemBuilder(online ? Material.ENDER_EYE : Material.BARRIER)
                .name(online ? "&dТП игрока к себе" : "&7ТП игрока к себе")
                .lore(online ? "&7Притянуть игрока" : "&cИгрок оффлайн")
                .build();
    }

    private ItemStack buildGiveItem() {
        boolean online = target.isOnline();
        return new ItemBuilder(online ? Material.HOPPER : Material.BARRIER)
                .name(online ? "&6Передать предмет" : "&7Передать предмет")
                .lore(online ? "&7Предмет из руки → игроку" : "&cИгрок оффлайн")
                .build();
    }

    private ItemStack buildChangeGroupItem() {
        return new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&bИзменить группу")
                .lore("&7Назначить LuckPerms группу")
                .build();
    }

    private ItemStack buildClearInvItem() {
        return new ItemBuilder(Material.BARRIER)
                .name("&cОчистить инвентарь")
                .lore("&7Всё содержимое + броня", "&7будет удалено")
                .build();
    }

    private ItemStack buildViewInvItem() {
        return new ItemBuilder(Material.LEATHER_CHESTPLATE)
                .name("&eИнвентарь")
                .lore("&7Просмотр инвентаря", "&7(редактирование с perm)")
                .build();
    }

    private ItemStack buildViewEcItem() {
        return new ItemBuilder(Material.ENDER_CHEST)
                .name("&eЭндер-сундук")
                .lore("&7Просмотр эндер-сундука", "&7(редактирование с perm)")
                .build();
    }

    private ItemStack buildWarnItem() {
        int warns = 0;
        Player fresh = target.getPlayer();
        if (fresh != null && fresh.isOnline()) warns = plugin.getWarnManager().getWarnCount(fresh.getUniqueId());
        boolean online = target.isOnline();
        return new ItemBuilder(online ? Material.PAPER : Material.BARRIER)
                .name(online ? "&eВыдать варн" : "&7Выдать варн")
                .lore(online ? "&7Предупреждение (" + warns + "/3)" : "&cИгрок оффлайн")
                .build();
    }

    private ItemStack buildMuteItem() {
        boolean online = target.isOnline();
        boolean muted = plugin.getMuteManager().isMuted(target.getUniqueId());
        if (online && muted) {
            MuteManager.MuteEntry entry = plugin.getMuteManager().getMuteEntry(target.getUniqueId());
            long remaining = entry != null ? (entry.expires() - System.currentTimeMillis()) / 1000 : 0;
            return new ItemBuilder(Material.NOTE_BLOCK)
                    .name("&aРазмьютить")
                    .lore("&7Активен: &c" + plugin.getMuteManager().formatRemaining(entry))
                    .build();
        }
        return new ItemBuilder(online ? Material.JUKEBOX : Material.BARRIER)
                .name(online ? "&cЗаглушить" : "&7Заглушить")
                .lore(online ? "&7Запретить чат" : "&cИгрок оффлайн")
                .build();
    }

    private ItemStack buildAltsItem() {
        return new ItemBuilder(Material.FILLED_MAP)
                .name("&dАльт-аккаунты")
                .lore("&7Проверить по IP")
                .build();
    }

    private ItemStack buildHistoryItem() {
        return new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&6История наказаний")
                .lore("&7Логи банов, киков,", "&7варнов и мутов")
                .build();
    }

    private ItemStack buildRollbackItem() {
        return new ItemBuilder(Material.CLOCK)
                .name("&aОткат инвентаря")
                .lore("&7Восстановить последний", "&7снапшот инвентаря")
                .build();
    }

    private ItemStack buildWarnListItem() {
        return new ItemBuilder(Material.BOOK)
                .name("&eВсе варны игрока")
                .lore("&7Список активных", "&7предупреждений")
                .build();
    }

    private ItemStack buildNotesItem() {
        return new ItemBuilder(Material.OAK_SIGN)
                .name("&6Заметки")
                .lore("&7Заметки модераторов", "&7об игроке")
                .build();
    }

    private ItemStack buildIpInfoItem() {
        boolean enabled = plugin.getGeoIpManager().isEnabled();
        return new ItemBuilder(enabled ? Material.COMPASS : Material.BARRIER)
                .name(enabled ? "&bГеолокация IP" : "&7Геолокация IP")
                .lore(enabled ? new String[]{"&7Страна, регион, город и провайдер", "&7Результат является приблизительным", "", "&eКликните для просмотра"}
                        : new String[]{"&cОтключено в config.yml"})
                .build();
    }

    private ItemStack buildSnapshotsItem() {
        return new ItemBuilder(Material.CHEST_MINECART)
                .name("&6Снапшоты инвентаря")
                .lore("&7Список сохранённых", "&7копий инвентаря")
                .build();
    }

    private ItemStack buildAuthMeItem() {
        boolean hasAuth = plugin.getAuthMeIntegration().isPresent()
                && plugin.getAuthMeIntegration().get().isEnabled();
        String name = target.getName();
        boolean registered = hasAuth && name != null
                && plugin.getAuthMeIntegration().get().isRegistered(name);
        return new ItemBuilder(Material.BOOK)
                .name("&5AuthMe")
                .lore(
                        hasAuth ? (registered
                                ? "&aЗарегистрирован"
                                : "&cНе зарегистрирован")
                                : "&7AuthMe не найден",
                        "&7Кликните для управления"
                )
                .build();
    }

    private ItemStack buildPermissionsItem() {
        return new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&bПрава игрока")
                .lore("&7Просмотр и управление", "&7разрешениями игрока")
                .build();
    }

    private ItemStack buildIpHistoryItem() {
        return new ItemBuilder(Material.REDSTONE_TORCH)
                .name("&cИстория IP")
                .lore("&7Все IP адреса и даты входов")
                .build();
    }

    private ItemStack buildClientInfoItem() {
        Player online = target.isOnline() ? target.getPlayer() : null;
        boolean canShowClient = online != null;
        ItemBuilder builder = new ItemBuilder(Material.COMPARATOR)
                .name("&6Моды и клиент")
                .lore("&7Кликните для полной информации");
        if (canShowClient) {
            String modLoader = me.admin.gui.integration.ViaVersionIntegration.detectServerModLoader();
            if (modLoader != null) {
                builder.lore("&7Загрузчик модов: " + modLoader);
            }
            try {
                String brand = online.getClientBrandName();
                if (brand != null) {
                    builder.lore("&7Клиент: &f" + brand);
                }
            } catch (Exception ignored) {}
            var via = plugin.getViaVersionIntegration();
            if (via.isPresent() && via.get().isEnabled()) {
                int proto = via.get().getProtocolVersion(online);
                if (proto > 0) {
                    builder.lore("&7Версия: &f" + via.get().getMinecraftVersion(proto));
                }
            }
        } else {
            builder.lore("&cИгрок оффлайн");
        }
        return builder.build();
    }

    private ItemStack buildChatHistoryItem() {
        return new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&eИстория чата")
                .lore("&7Просмотр последних сообщений", "&7и команд игрока")
                .build();
    }

    private ItemStack buildFlagItem() {
        String flag = plugin.getFlagManager().getFlag(target.getUniqueId());
        if (flag != null) {
            return new ItemBuilder(Material.ENDER_CHEST)
                    .name(plugin.getFlagManager().formatFlag(flag))
                    .lore("&7Модератор: &f" + plugin.getFlagManager().getFlagModerator(target.getUniqueId()),
                            "&7Шифт+клик — снять флаг")
                    .glowing()
                    .build();
        }
        return new ItemBuilder(Material.ENDER_CHEST)
                .name("&7Поставить флаг")
                .lore("&7Нажмите чтобы поставить флаг", "&7(например: hacker, scammer, trusted)")
                .build();
    }

    private ItemStack buildCoreProtectItem() {
        boolean cp = plugin.getCoreProtectIntegration().isEnabled();
        return new ItemBuilder(cp ? Material.OBSIDIAN : Material.BARRIER)
                .name(cp ? "&5CoreProtect" : "&7CoreProtect")
                .lore(cp
                        ? "&7Просмотр истории блоков игрока"
                        : "&cCoreProtect не найден на сервере")
                .build();
    }

    private ItemStack buildUndoItem(ActionReceiptManager.Receipt receipt) {
        long remaining = Math.max(0L, (receipt.expiresAt() - System.currentTimeMillis()) / 1000L);
        return new ItemBuilder(Material.RECOVERY_COMPASS)
                .name("&aОтменить последнее действие")
                .lore("&7Тип: &f" + receipt.type(), "&7Автор: &f" + receipt.actorName(),
                        "&7Осталось: &f" + remaining + " сек.", "", "&eНажмите для безопасной отмены")
                .glowing().build();
    }

    private void handleCoreProtect() {
        if (!viewer.hasPermission("amgui.coreprotect")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        plugin.getCoreProtectIntegration().sendBlockHistory(viewer, target);
    }

    private void handleUndo() {
        var receipt = receipts().latestForTarget(target.getUniqueId()).orElse(null);
        if (receipt == null || !receipts().canUndo(viewer, receipt)) {
            viewer.sendMessage("§cНет доступного обратимого действия.");
            return;
        }
        new ConfirmGUI(plugin, viewer, "§eОтменить " + receipt.type() + " для " + target.getName() + "?", () -> {
            var result = receipts().undo(viewer, receipt.id());
            viewer.sendMessage((result.success() ? "§a✓ " : "§c") + result.message());
            if (result.success()) SoundUtil.success(viewer); else SoundUtil.error(viewer);
            refresh();
        }).open();
    }

    private void handleAuthMe() {
        if (!viewer.hasPermission("amgui.authme.manage")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        boolean hasAuth = plugin.getAuthMeIntegration().isPresent()
                && plugin.getAuthMeIntegration().get().isEnabled();
        if (!hasAuth) {
            viewer.sendMessage("§cAuthMe не подключён на сервере.");
            return;
        }
        new AuthMeGUI(plugin, viewer, target).open();
    }

    private void handlePermissions() {
        if (!viewer.hasPermission("amgui.permissions")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        new PermissionListGUI(plugin, viewer, target).open();
    }

    private void handleIpHistory() {
        if (!viewer.hasPermission("amgui.viewip")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        new IpHistoryGUI(plugin, viewer, target).open();
    }

    private void handleClientInfo() {
        Player online = target.isOnline() ? target.getPlayer() : null;
        if (online == null) {
            viewer.sendMessage("§cИгрок оффлайн.");
            return;
        }
        viewer.sendMessage("§8┌─ §6Моды и клиент §8— §f" + target.getName());
        viewer.sendMessage("§8│");

        try {
            String brand = online.getClientBrandName();
            viewer.sendMessage("§8│ §7§lБренд (client brand):");
            viewer.sendMessage("§8│ §f" + (brand != null ? brand : "§7Неизвестно"));
            viewer.sendMessage("§8│");
        } catch (Exception ignored) {}

        String modLoader = me.admin.gui.integration.ViaVersionIntegration.detectServerModLoader();
        if (modLoader != null) {
            viewer.sendMessage("§8│ §7§lЗагрузчик модов (сервер):");
            viewer.sendMessage("§8│ " + modLoader + " §7(на сервере есть моды)");
            viewer.sendMessage("§8│");
        }

        var via = plugin.getViaVersionIntegration();
        me.admin.gui.integration.ViaVersionIntegration.ClientInfoResult rich = via.isPresent() && via.get().isEnabled()
                ? via.get().getRichClientInfo(online)
                : me.admin.gui.integration.ViaVersionIntegration.ClientInfoResult.fromPlayer(online);

        viewer.sendMessage("§8│ §7§lОсновное:");
        viewer.sendMessage("§8│ §7Locale: §f" + rich.locale + "  §7Пинг: §f" + rich.ping + "ms");
        viewer.sendMessage("§8│");

        if (rich.protocolVersion > 0) {
            viewer.sendMessage("§8│ §7§lПротокол (через ViaVersion):");
            viewer.sendMessage("§8│ §7Версия клиента: §f" + rich.minecraftVersion);
            if (rich.serverProtocol > 0 && rich.protocolVersion != rich.serverProtocol) {
                viewer.sendMessage("§8│ §7Серверный протокол: §f" + rich.serverProtocol + " (Via переводит)");
            }
            viewer.sendMessage("§8│");
        }

        if (rich.clientType != null) {
            viewer.sendMessage("§8│ §7§lТип клиента (ViaVersion):");
            viewer.sendMessage("§8│ §f" + rich.clientType);
            viewer.sendMessage("§8│");
        }

        if (rich.clientModLoader != null) {
            viewer.sendMessage("§8│ §7§lЗагрузчик/мод (ViaVersion):");
            viewer.sendMessage("§8│ §f" + rich.clientModLoader);
            viewer.sendMessage("§8│");
        }

        String brandFamily = me.admin.gui.integration.ViaVersionIntegration.detectBrandFamily(online);
        viewer.sendMessage("§8│ §7§lСемейство бренда:");
        viewer.sendMessage("§8│ " + brandFamily);
        viewer.sendMessage("§8│");

        viewer.sendMessage("§8│ §7§lЗаметка:");
        viewer.sendMessage("§8│ §7Полный список модов игрока виден только");
        viewer.sendMessage("§8│ §7на Forge/Fabric сервере. На Paper виден только");
        viewer.sendMessage("§8│ §7бренд клиента и версия протокола.");
        viewer.sendMessage("§8└─");

        plugin.getLogger().info("Клиентская информация для " + target.getName() + ": brand=" + rich.brandRaw
                + ", protocol=" + rich.protocolVersion + ", type=" + rich.clientType + ", modLoader=" + rich.clientModLoader);
    }

    private void handleChatHistory() {
        if (!viewer.hasPermission("amgui.logs")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        new ChatHistoryGUI(plugin, viewer, target).open();
    }

    private void handleFlag() {
        if (!viewer.hasPermission("amgui.notes")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        String existing = plugin.getFlagManager().getFlag(target.getUniqueId());
        if (existing != null) {
            new ConfirmGUI(plugin, viewer, "§cСнять флаг с " + target.getName() + "?", () -> {
                plugin.getFlagManager().removeFlag(target.getUniqueId());
                SoundUtil.success(viewer);
                viewer.sendMessage("§a✓ Флаг снят.");
                refresh();
            }).open();
        } else {
            viewer.closeInventory();
            plugin.getChatInputManager().awaitInput(viewer, "§eВведите текст флага (hacker, scammer, trusted, vip...):", input -> {
                String tag = input.trim().toLowerCase();
                if (tag.isEmpty()) {
                    viewer.sendMessage("§cФлаг не может быть пустым.");
                    new PlayerCardGUI(plugin, viewer, target).open();
                    return;
                }
                plugin.getFlagManager().setFlag(target.getUniqueId(), target.getName(), viewer.getName(), tag);
                SoundUtil.success(viewer);
                viewer.sendMessage("§a✓ Флаг " + plugin.getFlagManager().formatFlag(tag) + " §aустановлен.");
                new PlayerCardGUI(plugin, viewer, target).open();
            });
        }
    }

    private void handleShiftClick(int slot) {
        if (slot == SLOT_KICK) {
            if (!viewer.hasPermission("amgui.kick")) return;
            if (!canAct("kick")) return;
            if (!requireOnline()) return;
            Player fresh = target.getPlayer();
            if (fresh != null && fresh.hasPermission("amgui.kick.exempt")) {
                viewer.sendMessage("§cИммунитет."); return;
            }
            new ConfirmGUI(plugin, viewer, "§cПодтвердить быстрый кик " + target.getName() + "?", () -> {
                Player online = target.getPlayer();
                if (online == null || !online.isOnline()) { viewer.sendMessage("§cИгрок уже оффлайн."); return; }
                if (!executePreflight(ModerationActionService.Action.KICK)) return;
                plugin.getInventoryRollbackManager().saveSnapshot(online, "quick_kick");
                online.kick(me.admin.gui.utils.TextUtil.legacy("§cБыстрый кик от модератора."));
                plugin.getDatabaseManager().logPunishment("quick_kick", viewer.getName(), target.getName(), "Shift+кик", -1);
                plugin.getAuditManager().record(viewer, "punishment.quick-kick", target.getName(), target.getUniqueId(), "confirmed=true");
                SoundUtil.success(viewer);
                viewer.sendMessage("§a✓ Быстрый кик: " + target.getName());
            }).open();
            return;
        }
        if (slot == SLOT_BAN && viewer.hasPermission("amgui.ban")) {
            if (!canAct("ban")) return;
            String name = target.getName();
            if (name == null) return;
            Player online = target.getPlayer();
            if (online != null && online.isOnline() && online.hasPermission("amgui.ban.exempt")) {
                viewer.sendMessage("§cИммунитет."); return;
            }
            new ConfirmGUI(plugin, viewer, "§4Подтвердить быстрый бан " + name + "?", () -> {
                if (!executePreflight(ModerationActionService.Action.BAN)) return;
                String reason = "Shift+бан";
                me.admin.gui.utils.BanService.banProfile(target, reason, null, viewer.getName());
                receipts().record(viewer, target, ActionReceiptManager.Type.BAN, "");
                Player player = target.getPlayer();
                if (player != null && player.isOnline()) player.kick(me.admin.gui.utils.TextUtil.legacy("§cВы забанены.\n§7Причина: " + reason));
                plugin.getDatabaseManager().logPunishment("quick_ban", viewer.getName(), name, reason, -1);
                plugin.getAuditManager().record(viewer, "punishment.quick-ban", name, target.getUniqueId(), "confirmed=true");
                SoundUtil.success(viewer);
                viewer.sendMessage("§c✓ Быстрый бан: " + name);
            }).open();
            return;
        }
        if (slot == SLOT_FREEZE && viewer.hasPermission("amgui.freeze")) {
            if (!canAct("freeze")) return;
            if (!requireOnline()) return;
            Player fresh = target.getPlayer();
            if (fresh != null && fresh.hasPermission("amgui.freeze.exempt")) {
                viewer.sendMessage("§cИммунитет."); return;
            }
            FreezeManager fm = plugin.getFreezeManager();
            if (!executePreflight(ModerationActionService.Action.FREEZE)) return;
            boolean previouslyFrozen = fm.isFrozen(fresh);
            if (previouslyFrozen) {
                fm.unfreeze(fresh);
                viewer.sendMessage("§a✓ Быстрая разморозка: " + target.getName());
            } else {
                fm.freeze(fresh);
                viewer.sendMessage("§c✓ Быстрая заморозка: " + target.getName());
            }
            receipts().record(viewer, target, ActionReceiptManager.Type.FREEZE,
                    Boolean.toString(previouslyFrozen));
            SoundUtil.success(viewer);
        }
    }

    private void sendDiscord(String type, String player, String reason, String duration) {
        plugin.getDiscordWebhook().ifPresent(w -> w.send(type, player, viewer.getName(), reason, duration));
    }

    private boolean requireOnline() {
        Player fresh = target.getPlayer();
        if (fresh == null || !fresh.isOnline()) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return false;
        }
        return true;
    }

    private void handleKick() {
        if (!viewer.hasPermission("amgui.kick") || !viewer.hasPermission("amgui.use")) return;
        if (!canAct("kick")) return;
        Player fresh = target.getPlayer();
        if (fresh == null || !fresh.isOnline()) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return;
        }
        if (fresh.hasPermission("amgui.kick.exempt")) {
            viewer.sendMessage("§cЭтот игрок имеет иммунитет к кику.");
            return;
        }
        Player captured = fresh;
        new ReasonSelectGUI(plugin, viewer, "kick", reason -> {
            new ConfirmGUI(plugin, viewer, "§cКикнуть " + target.getName() + "?",
                () -> {
                    Player now = target.getPlayer();
                    if (now == null || !now.isOnline()) {
                        viewer.sendMessage("§cИгрок уже оффлайн.");
                        return;
                    }
                    if (!executePreflight(ModerationActionService.Action.KICK)) return;
                    plugin.getInventoryRollbackManager().saveSnapshot(now, "kick: " + reason);
                    now.kick(me.admin.gui.utils.TextUtil.legacy(plugin.getConfigManager().getFormattedMessage("kicked", "reason", reason)));
                    plugin.getDatabaseManager().logPunishment("kick", viewer.getName(), target.getName(), reason, -1);
                    plugin.getConfigManager().broadcastPunishment("kick", target.getName(), reason);
                    plugin.getConfigManager().sendPunishmentTitle(viewer, "kick", target.getName());
                    plugin.getConfigManager().playPunishmentSound(viewer, "kick");
                    sendDiscord("kick", target.getName(), reason, "—");
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§a✓ Игрок " + target.getName() + " кикнут.");
                },
                () -> new PlayerCardGUI(plugin, viewer, target).open()
            ).open();
        }).open();
    }

    private void handleBan() {
        if (!viewer.hasPermission("amgui.ban") || !viewer.hasPermission("amgui.use")) return;
        if (!canAct("ban")) return;
        String banName = target.getName();
        if (banName == null) { viewer.sendMessage("§cНеизвестный игрок."); return; }
        Player online = target.getPlayer();
        if (online != null && online.isOnline() && online.hasPermission("amgui.ban.exempt")) {
            viewer.sendMessage("§cЭтот игрок имеет иммунитет к бану.");
            return;
        }
        new BanDurationGUI(plugin, viewer, "ban", duration -> {
            if (duration <= 0) {
                new ReasonSelectGUI(plugin, viewer, "ban", reason -> {
                    new ConfirmGUI(plugin, viewer, "§cЗабанить " + banName + " навсегда?",
                        () -> {
                            if (!executePreflight(ModerationActionService.Action.BAN)) return;
                            Player now = target.getPlayer();
                            if (now != null && now.isOnline())
                                plugin.getInventoryRollbackManager().saveSnapshot(now, "ban: " + reason);
                            me.admin.gui.utils.BanService.banProfile(target, reason, null, viewer.getName());
                            receipts().record(viewer, target, ActionReceiptManager.Type.BAN, "");
                            if (now != null && now.isOnline())
                                now.kick(me.admin.gui.utils.TextUtil.legacy("§cВы забанены навсегда.\n§7Причина: " + reason));
                            plugin.getDatabaseManager().logPunishment("ban", viewer.getName(), banName, reason, -1);
                            plugin.getConfigManager().broadcastPunishment("ban", banName, reason);
                            plugin.getConfigManager().sendPunishmentTitle(viewer, "ban", banName);
                            plugin.getConfigManager().playPunishmentSound(viewer, "ban");
                            sendDiscord("ban", banName, reason, "Навсегда");
                            SoundUtil.success(viewer);
                            viewer.sendMessage("§c✓ Игрок " + banName + " забанен навсегда.");
                        },
                        () -> new PlayerCardGUI(plugin, viewer, target).open()
                    ).open();
                }).open();
            } else {
                new ReasonSelectGUI(plugin, viewer, "ban", reason -> {
                    new ConfirmGUI(plugin, viewer, "§cЗабанить " + banName + " на " + TimeUtils.formatDuration(duration) + "?",
                        () -> {
                            if (!executePreflight(ModerationActionService.Action.TEMPBAN)) return;
                            Player now = target.getPlayer();
                            if (now != null && now.isOnline())
                                plugin.getInventoryRollbackManager().saveSnapshot(now, "tempban: " + reason);
                            long expires = System.currentTimeMillis() + (duration * 1000);
                            me.admin.gui.utils.BanService.banProfile(target, reason,
                                    java.time.Instant.ofEpochMilli(expires), viewer.getName());
                            receipts().record(viewer, target, ActionReceiptManager.Type.TEMPBAN, "");
                            if (now != null && now.isOnline())
                                now.kick(me.admin.gui.utils.TextUtil.legacy("§cВы забанены на " + TimeUtils.formatDuration(duration) + ".\n§7Причина: " + reason));
                            plugin.getDatabaseManager().logPunishment("tempban", viewer.getName(), banName, reason, duration);
                            plugin.getConfigManager().broadcastPunishment("tempban", banName, reason);
                            plugin.getConfigManager().sendPunishmentTitle(viewer, "ban", banName);
                            plugin.getConfigManager().playPunishmentSound(viewer, "ban");
                            sendDiscord("tempban", banName, reason, TimeUtils.formatDuration(duration));
                            SoundUtil.success(viewer);
                            viewer.sendMessage("§c✓ Игрок " + banName + " забанен на " + TimeUtils.formatDuration(duration) + ".");
                        },
                        () -> new PlayerCardGUI(plugin, viewer, target).open()
                    ).open();
                }).open();
            }
        }).open();
    }

    private void handleIpBan() {
        if (!viewer.hasPermission("amgui.ban")) return;
        if (!canAct("ipban")) return;
        String name = target.getName();
        if (name == null) { viewer.sendMessage("§cНеизвестный игрок."); return; }
        String ip = plugin.getAltDetector().getLastIp(target.getUniqueId());
        if (ip.isEmpty()) {
            viewer.sendMessage("§cНет IP для этого игрока.");
            return;
        }
        new ReasonSelectGUI(plugin, viewer, "ban", reason -> {
            new ConfirmGUI(plugin, viewer, "§cЗабанить по IP " + name + "?",
                () -> {
                    if (!executePreflight(ModerationActionService.Action.IP_BAN)) return;
                    me.admin.gui.utils.BanService.banIp(ip, reason, null, viewer.getName());
                    me.admin.gui.utils.BanService.banProfile(target, reason, null, viewer.getName());
                    Player fresh = target.getPlayer();
                    if (fresh != null && fresh.isOnline()) {
                        plugin.getInventoryRollbackManager().saveSnapshot(fresh, "ipban: " + reason);
                        fresh.kick(me.admin.gui.utils.TextUtil.legacy("§cВы забанены по IP.\n§7Причина: " + reason));
                    }
                    plugin.getDatabaseManager().logPunishment("ban", viewer.getName(), name, "IP-BAN: " + reason, -1);
                    plugin.getConfigManager().sendPunishmentTitle(viewer, "ban", name);
                    plugin.getConfigManager().playPunishmentSound(viewer, "ban");
                    sendDiscord("ban", name, "IP-BAN: " + reason, "Навсегда");
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§c✓ IP-бан: " + name + " (" + ip + ")");
                    // Ban all alts
                    Map<String, UUID> alts = plugin.getAltDetector().findAltUuids(target.getUniqueId());
                    for (Map.Entry<String, UUID> e : alts.entrySet()) {
                        if (!e.getKey().equalsIgnoreCase(name)) {
                            me.admin.gui.utils.BanService.banProfile(e.getKey(), "IP-бан " + name, null, viewer.getName());
                            plugin.getDatabaseManager().logPunishment("ban", viewer.getName(), e.getKey(), "IP-BAN alt: " + reason, -1);
                            Player altP = Bukkit.getPlayer(e.getValue());
                            if (altP != null) altP.kick(me.admin.gui.utils.TextUtil.legacy("§cIP-бан.\n§7Причина: " + reason));
                        }
                    }
                    if (alts.size() > 1) viewer.sendMessage("§c✓ Также забанено " + (alts.size() - 1) + " альтов.");
                },
                () -> new PlayerCardGUI(plugin, viewer, target).open()
            ).open();
        }).open();
    }

    private void handleUnban() {
        if (!viewer.hasPermission("amgui.ban")) return;
        String name = target.getName();
        if (name == null) { viewer.sendMessage("§cНеизвестный игрок."); return; }
        if (me.admin.gui.utils.BanService.isProfileBanned(target)) {
            new ConfirmGUI(plugin, viewer, "§cРазбанить " + name + "?", () -> {
                me.admin.gui.utils.BanService.pardonProfile(target);
                plugin.getDatabaseManager().logPunishment("unban", viewer.getName(), name, "Разбан", -1);
                sendDiscord("unban", name, "Разбан", "—");
                SoundUtil.success(viewer);
                viewer.sendMessage("§a✓ Игрок " + name + " разбанен.");
                refresh();
            }).open();
        } else {
            viewer.sendMessage("§cИгрок не забанен.");
        }
    }

    private void handleFreeze() {
        if (!viewer.hasPermission("amgui.freeze")) return;
        if (!canAct("freeze")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        if (fresh != null && fresh.hasPermission("amgui.freeze.exempt")) {
            viewer.sendMessage("§cЭтот игрок имеет иммунитет к заморозке.");
            return;
        }
        FreezeManager fm = plugin.getFreezeManager();
        boolean frozen = fm.isFrozen(fresh);
        new ConfirmGUI(plugin, viewer, (frozen ? "§aРазморозить " : "§bЗаморозить ") + target.getName() + "?",
            () -> {
                Player current = target.getPlayer();
                if (current == null || !current.isOnline()) {
                    viewer.sendMessage("§cИгрок уже оффлайн.");
                    return;
                }
                if (!executePreflight(ModerationActionService.Action.FREEZE)) return;
                boolean currentlyFrozen = fm.isFrozen(current);
                if (currentlyFrozen) {
                    fm.unfreeze(current);
                    plugin.getDatabaseManager().logPunishment("unfreeze", viewer.getName(), target.getName(), "Разморозка", -1);
                    plugin.getConfigManager().sendPunishmentTitle(viewer, "unfreeze", target.getName());
                    plugin.getConfigManager().playPunishmentSound(viewer, "unfreeze");
                    sendDiscord("unfreeze", target.getName(), "Разморозка", "—");
                } else {
                    fm.freeze(current);
                    plugin.getDatabaseManager().logPunishment("freeze", viewer.getName(), target.getName(), "Заморозка", -1);
                    plugin.getConfigManager().sendPunishmentTitle(viewer, "freeze", target.getName());
                    plugin.getConfigManager().playPunishmentSound(viewer, "freeze");
                    sendDiscord("freeze", target.getName(), "Заморозка", "—");
                }
                receipts().record(viewer, target, ActionReceiptManager.Type.FREEZE,
                        Boolean.toString(currentlyFrozen));
                SoundUtil.success(viewer);
                refresh();
            },
            () -> new PlayerCardGUI(plugin, viewer, target).open()
        ).open();
    }

    private void handleTpTo() {
        if (!viewer.hasPermission("amgui.teleport")) return;
        if (!executePreflight(ModerationActionService.Action.TELEPORT)) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        viewer.teleport(fresh);
        SoundUtil.success(viewer);
        viewer.sendMessage("§a✓ Телепортирован к " + target.getName());
        plugin.getAuditManager().record(viewer, "teleport.to", target.getName(), target.getUniqueId(), "");
    }

    private void handleTpHere() {
        if (!viewer.hasPermission("amgui.teleport")) return;
        if (!executePreflight(ModerationActionService.Action.TELEPORT)) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        fresh.teleport(viewer);
        SoundUtil.success(viewer);
        viewer.sendMessage("§a✓ " + target.getName() + " телепортирован к вам");
        plugin.getAuditManager().record(viewer, "teleport.here", target.getName(), target.getUniqueId(), "");
    }

    private void handleGiveItem() {
        if (!viewer.hasPermission("amgui.inventory.edit")) return;
        if (!executePreflight(ModerationActionService.Action.INVENTORY_EDIT)) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        ItemStack hand = viewer.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            viewer.sendMessage("§cВозьмите предмет в руку.");
            return;
        }
        ItemStack toGive = hand.clone();
        java.util.Map<Integer, ItemStack> leftover = fresh.getInventory().addItem(toGive);
        if (!leftover.isEmpty()) {
            viewer.sendMessage("§cИнвентарь игрока полон.");
            return;
        }
        viewer.getInventory().setItemInMainHand(null);
        SoundUtil.success(viewer);
        viewer.sendMessage("§a✓ Предмет выдан " + target.getName());
        plugin.getAuditManager().record(viewer, "inventory.give", target.getName(), target.getUniqueId(),
                toGive.getType() + " x" + toGive.getAmount());
    }

    private void handleChangeGroup() {
        if (!viewer.hasPermission("amgui.groups.assign")) return;
        if (!canAct("group")) return;
        new AssignGroupGUI(plugin, viewer, target).open();
    }

    private void handleClearInv() {
        if (!viewer.hasPermission("amgui.inventory.edit")) return;
        if (!canAct("inventory-clear")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        new ConfirmGUI(plugin, viewer, "§cОчистить инвентарь " + target.getName() + "?", () -> {
            Player current = target.getPlayer();
            if (current == null || !current.isOnline()) {
                viewer.sendMessage("§cИгрок уже оффлайн.");
                return;
            }
            if (!executePreflight(ModerationActionService.Action.INVENTORY_EDIT)) return;
            current.getInventory().clear();
            current.getInventory().setArmorContents(new ItemStack[4]);
            current.getInventory().setExtraContents(new ItemStack[1]);
            SoundUtil.success(viewer);
            viewer.sendMessage(plugin.getConfigManager().getFormattedMessage("inventory-cleared", "player", target.getName()));
            plugin.getAuditManager().record(viewer, "inventory.clear", target.getName(), target.getUniqueId(), "");
        }).open();
    }

    private void handleViewInv() {
        if (!viewer.hasPermission("amgui.inventory.view")) return;
        if (!canAct("inventory-view")) return;
        plugin.getAuditManager().record(viewer, "inventory.view", target.getName(), target.getUniqueId(), "inventory");
        Player fresh = target.getPlayer();
        if (fresh != null && fresh.isOnline()) {
            if (viewer.hasPermission("amgui.inventory.edit")) {
                plugin.getGuiManager().openEditableExternal(viewer, fresh.getInventory());
                SoundUtil.success(viewer);
                viewer.sendMessage("§eРедактирование инвентаря: " + (target.getName() != null ? target.getName() : "?"));
            } else {
                Inventory frozen = ReadOnlyInventoryHolder.snapshot(45,
                        "§8Инвентарь: " + (target.getName() != null ? target.getName() : "?"),
                        fresh.getInventory().getContents());
                registerReadOnly(frozen);
                viewer.sendMessage("§eПросмотр инвентаря: " + (target.getName() != null ? target.getName() : "?") + " (только чтение)");
            }
        } else {
            ItemStack[] contents = plugin.getPlayerInventoryCache().getInventory(target.getUniqueId());
            if (contents == null) {
                viewer.sendMessage("§cНет кэшированного инвентаря для этого игрока (игрок должен хотя бы раз зайти на сервер).");
                return;
            }
            Inventory frozen = ReadOnlyInventoryHolder.snapshot(45,
                    "§8Инвентарь: " + (target.getName() != null ? target.getName() : "?") + " (кэш)", contents);
            registerReadOnly(frozen);
            viewer.sendMessage("§eПросмотр кэшированного инвентаря: " + (target.getName() != null ? target.getName() : "?"));
        }
    }

    private void registerReadOnly(Inventory inv) {
        PaginatedGUI menu = new PaginatedGUI(plugin, viewer) {
            @Override public String getTitle() { return ""; }
            @Override public void buildContent() {}
            @Override public void onClick(int slot) {}
            @Override protected Inventory buildInventory() { return inv; }
        };
        plugin.getGuiManager().open(viewer, menu, inv, true);
    }

    private void handleViewEc() {
        if (!viewer.hasPermission("amgui.inventory.view")) return;
        if (!canAct("inventory-view")) return;
        plugin.getAuditManager().record(viewer, "inventory.view", target.getName(), target.getUniqueId(), "ender-chest");
        Player fresh = target.getPlayer();
        if (fresh != null && fresh.isOnline()) {
            if (viewer.hasPermission("amgui.inventory.edit")) {
                plugin.getGuiManager().openEditableExternal(viewer, fresh.getEnderChest());
                viewer.sendMessage("§eРедактирование эндер-сундука: " + (target.getName() != null ? target.getName() : "?"));
            } else {
                Inventory frozen = ReadOnlyInventoryHolder.snapshot(fresh.getEnderChest().getSize(),
                        "§8Эндер-сундук: " + (target.getName() != null ? target.getName() : "?"),
                        fresh.getEnderChest().getContents());
                registerReadOnly(frozen);
                viewer.sendMessage("§eПросмотр эндер-сундука: " + (target.getName() != null ? target.getName() : "?")
                        + " (только чтение)");
            }
        } else {
            ItemStack[] contents = plugin.getPlayerInventoryCache().getEnderChest(target.getUniqueId());
            if (contents == null) {
                viewer.sendMessage("§cНет кэшированного эндер-сундука для этого игрока.");
                return;
            }
            Inventory frozen = ReadOnlyInventoryHolder.snapshot(27,
                    "§8Эндер-сундук: " + (target.getName() != null ? target.getName() : "?") + " (кэш)", contents);
            registerReadOnly(frozen);
            viewer.sendMessage("§eПросмотр кэшированного эндер-сундука: " + (target.getName() != null ? target.getName() : "?"));
        }
    }

    private void handleWarn() {
        if (!viewer.hasPermission("amgui.warn")) return;
        if (!canAct("warn")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        new ReasonSelectGUI(plugin, viewer, "warn", reason -> {
            Player current = target.getPlayer();
            if (current == null || !current.isOnline()) {
                viewer.sendMessage("§cИгрок уже оффлайн.");
                return;
            }
            if (!executePreflight(ModerationActionService.Action.WARN)) return;
            UUID warnId = plugin.getWarnManager().warn(current, reason, viewer.getName());
            if (warnId != null) receipts().record(viewer, target, ActionReceiptManager.Type.WARN, warnId.toString());
            int warnCount = plugin.getWarnManager().getWarnCount(current.getUniqueId());
            sendDiscord("warn", target.getName(), reason, warnCount + "/" + plugin.getWarnManager().getMaxWarns());
            SoundUtil.success(viewer);
            viewer.sendMessage(plugin.getConfigManager().getFormattedMessage("warned",
                    "player", target.getName(),
                    "reason", reason,
                    "count", String.valueOf(warnCount),
                    "max", String.valueOf(plugin.getWarnManager().getMaxWarns())));
            refresh();
        }).open();
    }

    private void handleMute() {
        if (!viewer.hasPermission("amgui.mute")) return;
        if (!canAct("mute")) return;
        if (plugin.getMuteManager().isMuted(target.getUniqueId())) {
            new ConfirmGUI(plugin, viewer, "§aРазмьютить " + target.getName() + "?", () -> {
                if (!executePreflight(ModerationActionService.Action.MUTE)) return;
                plugin.getMuteManager().unmute(target.getUniqueId());
                plugin.getDatabaseManager().logPunishment("unmute", viewer.getName(), target.getName(), "Размьючен", -1);
                sendDiscord("unmute", target.getName(), "Размьючен", "—");
                SoundUtil.success(viewer);
                viewer.sendMessage(plugin.getConfigManager().getMessage("unmuted"));
                refresh();
            }).open();
            return;
        }
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        new BanDurationGUI(plugin, viewer, "mute", duration -> {
            new ReasonSelectGUI(plugin, viewer, "mute", reason -> {
                Player current = target.getPlayer();
                if (current == null || !current.isOnline()) {
                    viewer.sendMessage("§cИгрок уже оффлайн.");
                    return;
                }
                if (!executePreflight(ModerationActionService.Action.MUTE)) return;
                plugin.getMuteManager().mute(current, reason, viewer.getName(), duration);
                receipts().record(viewer, target, ActionReceiptManager.Type.MUTE, "");
                sendDiscord("mute", target.getName(), reason, TimeUtils.formatDuration(duration));
                SoundUtil.success(viewer);
                viewer.sendMessage("§c✓ Игрок " + target.getName() + " замьючен.");
                refresh();
            }).open();
        }).open();
    }

    private void handleAlts() {
        if (!viewer.hasPermission("amgui.alts")) return;
        if (!canAct("alts")) return;
        new AltConnectionsGUI(plugin, viewer, target).open();
    }

    private void handleHistory() {
        if (!viewer.hasPermission("amgui.logs")) return;
        new PlayerHistoryGUI(plugin, viewer, target).open();
    }

    private void handleRollback() {
        if (!viewer.hasPermission("amgui.rollback")) return;
        if (!canAct("rollback")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        var snapshots = plugin.getInventoryRollbackManager().getSnapshots(target.getUniqueId());
        if (snapshots.isEmpty()) {
            viewer.sendMessage("§cСнапшотов для восстановления не найдено.");
            return;
        }
        new ConfirmGUI(plugin, viewer, "§6Восстановить последний снапшот?", () -> {
            plugin.getInventoryRollbackManager().restoreSnapshot(fresh, snapshots.get(0).timestamp());
            SoundUtil.success(viewer);
            viewer.sendMessage("§a✓ Инвентарь игрока " + target.getName() + " восстановлен.");
        }).open();
    }

    private boolean canAct(String action) {
        ModerationActionService.Action mapped = ModerationActionService.Action.fromSecurityAction(action);
        if (mapped != null) {
            var preview = ModerationActionService.preview(plugin, viewer, target, mapped);
            if (preview.allowed()) return true;
            viewer.sendMessage(plugin.getLocalizationManager().format("messages.action-denied",
                    "&cДействие запрещено: %reason%", "reason", preview.reason()));
            return false;
        }
        var decision = plugin.getPunishmentSecurityManager().validate(viewer, target, action);
        if (decision.allowed()) return true;
        viewer.sendMessage(plugin.getLocalizationManager().format("messages.action-denied",
                "&cДействие запрещено: %reason%", "reason", decision.reason()));
        return false;
    }

    private boolean executePreflight(ModerationActionService.Action action) {
        var decision = ModerationActionService.execute(plugin, viewer, target, action);
        if (decision.allowed()) return true;
        viewer.sendMessage(plugin.getLocalizationManager().format("messages.action-denied",
                "&cДействие запрещено: %reason%", "reason", decision.reason()));
        return false;
    }

    private ActionReceiptManager receipts() {
        return ActionReceiptManager.forPlugin(plugin);
    }

    private void handleWarnList() {
        if (!viewer.hasPermission("amgui.warn")) return;
        new WarnListGUI(plugin, viewer, target).open();
    }

    private void handleNotes() {
        if (!viewer.hasPermission("amgui.notes")) return;
        List<String> notes = plugin.getPlayerNoteManager().getNotes(target.getUniqueId());
        if (notes.isEmpty()) { promptAddNote(); return; }
        viewer.sendMessage("§8[§cAM§8] §7Заметки о §f" + target.getName() + ":");
        for (String n : notes) viewer.sendMessage(" §8- " + n);
        viewer.sendMessage("");
        new ConfirmGUI(plugin, viewer, "§aДобавить заметку?", () -> promptAddNote()).open();
    }

    private void promptAddNote() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите текст заметки:", input -> {
            String text = input.trim();
            if (text.isEmpty()) { viewer.sendMessage("§cТекст заметки не может быть пустым."); return; }
            plugin.getPlayerNoteManager().addNote(target.getUniqueId(), target.getName() != null ? target.getName() : "?", viewer.getName(), text);
            plugin.getAuditManager().record(viewer, "note.add", target.getName(), target.getUniqueId(), text);
            SoundUtil.success(viewer);
            viewer.sendMessage("§a✓ Заметка добавлена.");
            new PlayerCardGUI(plugin, viewer, target).open();
        });
    }

    private void handleIpInfo() {
        if (!viewer.hasPermission("amgui.geoip") || !viewer.hasPermission("amgui.viewip")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (!canAct("view-ip")) return;
        String ip = "";
        Player online = target.isOnline() ? target.getPlayer() : null;
        if (online != null && online.getAddress() != null) ip = online.getAddress().getAddress().getHostAddress();
        if (ip.isBlank()) ip = plugin.getAltDetector().getLastIp(target.getUniqueId());
        if (ip.isBlank()) {
            viewer.sendMessage("§eНет IP-данных для " + target.getName());
            return;
        }
        new GeoIpGUI(plugin, viewer, ip, target).open();
    }

    private void handleSnapshots() {
        if (!viewer.hasPermission("amgui.rollback")) return;
        List<InventoryRollbackManager.SnapshotInfo> snaps = plugin.getInventoryRollbackManager().getSnapshots(target.getUniqueId());
        if (snaps.isEmpty()) {
            viewer.sendMessage("§cНет снапшотов для этого игрока.");
            return;
        }
        viewer.sendMessage("§8[§cAM§8] §7Снапшоты §f" + target.getName() + ":");
        for (int i = 0; i < snaps.size(); i++) {
            InventoryRollbackManager.SnapshotInfo s = snaps.get(i);
            viewer.sendMessage(" §e#" + (i + 1) + " §7" + s.reason() + " §8(" + TimeUtils.formatLogTime(s.timestamp()) + ")");
        }
        if (target.isOnline()) {
            new ConfirmGUI(plugin, viewer, "§6Восстановить последний снапшот?", () -> {
                Player fresh = target.getPlayer();
                if (fresh != null && fresh.isOnline()) {
                    plugin.getInventoryRollbackManager().restoreSnapshot(fresh, snaps.get(0).timestamp());
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§a✓ Инвентарь восстановлен.");
                }
            }).open();
        }
    }

    @Override
    public void refresh() {
        super.refresh();
    }
}
