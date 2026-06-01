package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.manager.FreezeManager;
import me.admin.gui.manager.InventoryRollbackManager;
import me.admin.gui.manager.MuteManager;
import me.admin.gui.manager.WarnManager;
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
import org.bukkit.inventory.meta.SkullMeta;

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
        Inventory inv = Bukkit.createInventory(null, SIZE, getTitle());

        inv.setItem(SLOT_INFO, buildInfoItem());
        inv.setItem(SLOT_KICK, buildKickItem());
        inv.setItem(SLOT_BAN, buildBanItem());
        inv.setItem(SLOT_IP_BAN, buildIpBanItem());
        inv.setItem(SLOT_UNBAN, buildUnbanItem());
        inv.setItem(SLOT_FREEZE, buildFreezeItem());
        inv.setItem(SLOT_TP_TO, buildTpToItem());
        inv.setItem(SLOT_TP_HERE, buildTpHereItem());
        inv.setItem(SLOT_GIVE_ITEM, buildGiveItem());
        inv.setItem(SLOT_CHANGE_GROUP, buildChangeGroupItem());
        inv.setItem(SLOT_CLEAR_INV, buildClearInvItem());
        inv.setItem(SLOT_VIEW_INV, buildViewInvItem());
        inv.setItem(SLOT_VIEW_EC, buildViewEcItem());
        inv.setItem(SLOT_WARN, buildWarnItem());
        inv.setItem(SLOT_MUTE, buildMuteItem());
        inv.setItem(SLOT_ALTS, buildAltsItem());
        inv.setItem(SLOT_HISTORY, buildHistoryItem());
        inv.setItem(SLOT_ROLLBACK, buildRollbackItem());
        inv.setItem(SLOT_WARN_LIST, buildWarnListItem());
        inv.setItem(SLOT_NOTES, buildNotesItem());
        inv.setItem(SLOT_IP_INFO, buildIpInfoItem());
        inv.setItem(SLOT_SNAPSHOTS, buildSnapshotsItem());

        for (int i = 0; i < SIZE; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemBuilder.createFiller());
        }

        inv.setItem(SLOT_MAIN_MENU, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inv;
    }

    @Override
    public void open() {
        viewer.openInventory(buildInventory());
        plugin.getGuiManager().register(viewer.getUniqueId(), this);
    }

    @Override
    public void onClick(int slot) {
        SoundUtil.click(viewer);
        switch (slot) {
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
            case SLOT_MAIN_MENU -> {
                plugin.getGuiManager().unregister(viewer.getUniqueId());
                new MainMenu(plugin, viewer).open();
            }
            case SLOT_CLOSE -> close();
        }
    }

    private ItemStack buildInfoItem() {
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD);
        if (target.getName() != null) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                if (target.isOnline()) meta.setOwningPlayer(target.getPlayer());
                else meta.setOwningPlayer(target);
                head.setItemMeta(meta);
            }
            builder = new ItemBuilder(head);
        }

        builder.name("&e" + target.getName());
        builder.lore(
                "&7UUID: &f" + target.getUniqueId().toString().substring(0, 8) + "...",
                "&7Статус: " + (target.isOnline() ? "&a✔ Онлайн" : "&c✘ Оффлайн")
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
        } else {
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
        boolean isBanned = Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(name);
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
        return new ItemBuilder(Material.REDSTONE_TORCH)
                .name("&cIP информация")
                .lore("&7Все IP адреса игрока", "&7и даты входов")
                .build();
    }

    private ItemStack buildSnapshotsItem() {
        return new ItemBuilder(Material.CHEST_MINECART)
                .name("&6Снапшоты инвентаря")
                .lore("&7Список сохранённых", "&7копий инвентаря")
                .build();
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
        if (!viewer.hasPermission("amgui.player") || !viewer.hasPermission("amgui.use")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        new ReasonSelectGUI(plugin, viewer, "kick", reason -> {
            plugin.getInventoryRollbackManager().saveSnapshot(fresh, "kick: " + reason);
            fresh.kickPlayer(plugin.getConfigManager().getFormattedMessage("kicked", "reason", reason));
            plugin.getDatabaseManager().logPunishment("kick", viewer.getName(), target.getName(), reason, -1);
            plugin.getConfigManager().broadcastPunishment("kick", target.getName(), reason);
            sendDiscord("kick", target.getName(), reason, "—");
            SoundUtil.success(viewer);
            viewer.sendMessage("§a✓ Игрок " + target.getName() + " кикнут.");
        }).open();
    }

    private void handleBan() {
        if (!viewer.hasPermission("amgui.ban") || !viewer.hasPermission("amgui.use")) return;
        String banName = target.getName();
        if (banName == null) { viewer.sendMessage("§cНеизвестный игрок."); return; }
        new BanDurationGUI(plugin, viewer, "ban", duration -> {
            Player fresh = target.getPlayer();
            if (duration <= 0) {
                new ReasonSelectGUI(plugin, viewer, "ban", reason -> {
                    if (fresh != null && fresh.isOnline())
                        plugin.getInventoryRollbackManager().saveSnapshot(fresh, "ban: " + reason);
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(banName, reason, null, viewer.getName());
                    if (fresh != null && fresh.isOnline())
                        fresh.kickPlayer("§cВы забанены навсегда.\n§7Причина: " + reason);
                    plugin.getDatabaseManager().logPunishment("ban", viewer.getName(), banName, reason, -1);
                    plugin.getConfigManager().broadcastPunishment("ban", banName, reason);
                    sendDiscord("ban", banName, reason, "Навсегда");
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§c✓ Игрок " + banName + " забанен навсегда.");
                }).open();
            } else {
                new ReasonSelectGUI(plugin, viewer, "ban", reason -> {
                    if (fresh != null && fresh.isOnline())
                        plugin.getInventoryRollbackManager().saveSnapshot(fresh, "tempban: " + reason);
                    long expires = System.currentTimeMillis() + (duration * 1000);
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(banName, reason, new java.util.Date(expires), viewer.getName());
                    if (fresh != null && fresh.isOnline())
                        fresh.kickPlayer("§cВы забанены на " + TimeUtils.formatDuration(duration) + ".\n§7Причина: " + reason);
                    plugin.getDatabaseManager().logPunishment("tempban", viewer.getName(), banName, reason, duration);
                    plugin.getConfigManager().broadcastPunishment("tempban", banName, reason);
                    sendDiscord("tempban", banName, reason, TimeUtils.formatDuration(duration));
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§c✓ Игрок " + banName + " забанен на " + TimeUtils.formatDuration(duration) + ".");
                }).open();
            }
        }).open();
    }

    private void handleIpBan() {
        if (!viewer.hasPermission("amgui.ban")) return;
        String name = target.getName();
        if (name == null) { viewer.sendMessage("§cНеизвестный игрок."); return; }
        String ip = plugin.getAltDetector().getLastIp(target.getUniqueId());
        if (ip.isEmpty()) {
            viewer.sendMessage("§cНет IP для этого игрока.");
            return;
        }
        new ReasonSelectGUI(plugin, viewer, "ban", reason -> {
            Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(ip, reason, null, viewer.getName());
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(name, reason, null, viewer.getName());
            Player fresh = target.getPlayer();
            if (fresh != null && fresh.isOnline()) {
                plugin.getInventoryRollbackManager().saveSnapshot(fresh, "ipban: " + reason);
                fresh.kickPlayer("§cВы забанены по IP.\n§7Причина: " + reason);
            }
            plugin.getDatabaseManager().logPunishment("ban", viewer.getName(), name, "IP-BAN: " + reason, -1);
            sendDiscord("ban", name, "IP-BAN: " + reason, "Навсегда");
            SoundUtil.success(viewer);
            viewer.sendMessage("§c✓ IP-бан: " + name + " (" + ip + ")");
            // Ban all alts
            Map<String, UUID> alts = plugin.getAltDetector().findAltUuids(target.getUniqueId());
            for (Map.Entry<String, UUID> e : alts.entrySet()) {
                if (!e.getKey().equalsIgnoreCase(name)) {
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(e.getKey(), "IP-бан " + name, null, viewer.getName());
                    plugin.getDatabaseManager().logPunishment("ban", viewer.getName(), e.getKey(), "IP-BAN alt: " + reason, -1);
                    Player altP = Bukkit.getPlayer(e.getValue());
                    if (altP != null) altP.kickPlayer("§cIP-бан.\n§7Причина: " + reason);
                }
            }
            if (alts.size() > 1) viewer.sendMessage("§c✓ Также забанено " + (alts.size() - 1) + " альтов.");
        }).open();
    }

    private void handleUnban() {
        if (!viewer.hasPermission("amgui.ban")) return;
        String name = target.getName();
        if (name == null) { viewer.sendMessage("§cНеизвестный игрок."); return; }
        if (Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(name)) {
            new ConfirmGUI(plugin, viewer, "§cРазбанить " + name + "?", () -> {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(name);
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
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        FreezeManager fm = plugin.getFreezeManager();
        if (fm.isFrozen(fresh)) {
            fm.unfreeze(fresh);
            plugin.getDatabaseManager().logPunishment("unfreeze", viewer.getName(), target.getName(), "Разморозка", -1);
            sendDiscord("unfreeze", target.getName(), "Разморозка", "—");
            SoundUtil.success(viewer);
        } else {
            fm.freeze(fresh);
            plugin.getDatabaseManager().logPunishment("freeze", viewer.getName(), target.getName(), "Заморозка", -1);
            sendDiscord("freeze", target.getName(), "Заморозка", "—");
            SoundUtil.success(viewer);
        }
        refresh();
    }

    private void handleTpTo() {
        if (!viewer.hasPermission("amgui.teleport")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        viewer.teleport(fresh);
        SoundUtil.success(viewer);
        viewer.sendMessage("§a✓ Телепортирован к " + target.getName());
    }

    private void handleTpHere() {
        if (!viewer.hasPermission("amgui.teleport")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        fresh.teleport(viewer);
        SoundUtil.success(viewer);
        viewer.sendMessage("§a✓ " + target.getName() + " телепортирован к вам");
    }

    private void handleGiveItem() {
        if (!viewer.hasPermission("amgui.inventory.edit")) return;
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
    }

    private void handleChangeGroup() {
        if (!viewer.hasPermission("amgui.groups.assign")) return;
        new AssignGroupGUI(plugin, viewer, target).open();
    }

    private void handleClearInv() {
        if (!viewer.hasPermission("amgui.inventory.edit")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        new ConfirmGUI(plugin, viewer, "§cОчистить инвентарь " + target.getName() + "?", () -> {
            fresh.getInventory().clear();
            fresh.getInventory().setArmorContents(new ItemStack[4]);
            fresh.getInventory().setExtraContents(new ItemStack[1]);
            SoundUtil.success(viewer);
            viewer.sendMessage(plugin.getConfigManager().getFormattedMessage("inventory-cleared", "player", target.getName()));
        }).open();
    }

    private void handleViewInv() {
        if (!viewer.hasPermission("amgui.inventory.view")) return;
        Player fresh = target.getPlayer();
        if (fresh != null && fresh.isOnline()) {
            if (viewer.hasPermission("amgui.inventory.edit")) {
                viewer.openInventory(fresh.getInventory());
                SoundUtil.success(viewer);
                viewer.sendMessage("§eРедактирование инвентаря: " + (target.getName() != null ? target.getName() : "?"));
            } else {
                Inventory frozen = Bukkit.createInventory(null, 45, "§8Инвентарь: " + (target.getName() != null ? target.getName() : "?"));
                frozen.setContents(fresh.getInventory().getContents().clone());
                viewer.openInventory(frozen);
                registerReadOnly(frozen);
                viewer.sendMessage("§eПросмотр инвентаря: " + (target.getName() != null ? target.getName() : "?") + " (только чтение)");
            }
        } else {
            ItemStack[] contents = plugin.getPlayerInventoryCache().getInventory(target.getUniqueId());
            if (contents == null) {
                viewer.sendMessage("§cНет кэшированного инвентаря для этого игрока (игрок должен хотя бы раз зайти на сервер).");
                return;
            }
            Inventory frozen = Bukkit.createInventory(null, 45, "§8Инвентарь: " + (target.getName() != null ? target.getName() : "?") + " (кэш)");
            frozen.setContents(contents);
            viewer.openInventory(frozen);
            registerReadOnly(frozen);
            viewer.sendMessage("§eПросмотр кэшированного инвентаря: " + (target.getName() != null ? target.getName() : "?"));
        }
    }

    private void registerReadOnly(Inventory inv) {
        plugin.getGuiManager().register(viewer.getUniqueId(), new PaginatedGUI(plugin, viewer) {
            @Override public String getTitle() { return ""; }
            @Override public void buildContent() {}
            @Override public void onClick(int slot) {}
            @Override protected Inventory buildInventory() { return inv; }
        });
    }

    private void handleViewEc() {
        if (!viewer.hasPermission("amgui.inventory.view")) return;
        Player fresh = target.getPlayer();
        if (fresh != null && fresh.isOnline()) {
            if (viewer.hasPermission("amgui.inventory.edit")) {
                viewer.openInventory(fresh.getEnderChest());
                viewer.sendMessage("§eРедактирование эндер-сундука: " + (target.getName() != null ? target.getName() : "?"));
            } else {
                viewer.openInventory(fresh.getEnderChest());
                viewer.sendMessage("§eПросмотр эндер-сундука: " + (target.getName() != null ? target.getName() : "?"));
            }
        } else {
            ItemStack[] contents = plugin.getPlayerInventoryCache().getEnderChest(target.getUniqueId());
            if (contents == null) {
                viewer.sendMessage("§cНет кэшированного эндер-сундука для этого игрока.");
                return;
            }
            Inventory frozen = Bukkit.createInventory(null, 27, "§8Эндер-сундук: " + (target.getName() != null ? target.getName() : "?") + " (кэш)");
            frozen.setContents(contents);
            viewer.openInventory(frozen);
            registerReadOnly(frozen);
            viewer.sendMessage("§eПросмотр кэшированного эндер-сундука: " + (target.getName() != null ? target.getName() : "?"));
        }
    }

    private void handleWarn() {
        if (!viewer.hasPermission("amgui.warn")) return;
        if (!requireOnline()) return;
        Player fresh = target.getPlayer();
        new ReasonSelectGUI(plugin, viewer, "warn", reason -> {
            plugin.getWarnManager().warn(fresh, reason, viewer.getName());
            int warnCount = plugin.getWarnManager().getWarnCount(fresh.getUniqueId());
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
        if (plugin.getMuteManager().isMuted(target.getUniqueId())) {
            new ConfirmGUI(plugin, viewer, "§aРазмьютить " + target.getName() + "?", () -> {
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
                plugin.getMuteManager().mute(fresh, reason, viewer.getName(), duration);
                sendDiscord("mute", target.getName(), reason, TimeUtils.formatDuration(duration));
                SoundUtil.success(viewer);
                viewer.sendMessage("§c✓ Игрок " + target.getName() + " замьючен.");
                refresh();
            }).open();
        }).open();
    }

    private void handleAlts() {
        if (!viewer.hasPermission("amgui.alts")) return;
        Map<String, UUID> altsMap = plugin.getAltDetector().findAltUuids(target.getUniqueId());
        if (altsMap.isEmpty()) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-alts"));
            return;
        }
        String alts = String.join("§7, §f", altsMap.keySet());
        viewer.sendMessage(plugin.getConfigManager().getFormattedMessage("alts-found", "player", target.getName(), "alts", alts));

        if (viewer.hasPermission("amgui.ban")) {
            new ConfirmGUI(plugin, viewer, "§cЗабанить все алиасы " + target.getName() + "?", () -> {
                String reason = "Альт-аккаунт " + target.getName();
                for (Map.Entry<String, UUID> e : altsMap.entrySet()) {
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(e.getKey(), reason, null, viewer.getName());
                    Player online = Bukkit.getPlayer(e.getValue());
                    if (online != null) online.kickPlayer("§cВы забанены.\n§7Причина: " + reason);
                    plugin.getDatabaseManager().logPunishment("ban", viewer.getName(), e.getKey(), reason, -1);
                    sendDiscord("ban", e.getKey(), reason, "Альт-аккаунт");
                }
                SoundUtil.success(viewer);
                viewer.sendMessage("§c✓ Забанено " + altsMap.size() + " алиасов.");
            }).open();
        }
    }

    private void handleHistory() {
        if (!viewer.hasPermission("amgui.logs")) return;
        new PlayerHistoryGUI(plugin, viewer, target).open();
    }

    private void handleRollback() {
        if (!viewer.hasPermission("amgui.rollback")) return;
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

    private void handleWarnList() {
        if (!viewer.hasPermission("amgui.warn")) return;
        List<WarnManager.WarnEntry> warns = plugin.getWarnManager().getWarns(target.getUniqueId());
        if (warns.isEmpty()) {
            viewer.sendMessage("§eУ игрока " + target.getName() + " нет варнов.");
            return;
        }
        viewer.sendMessage("§8[§cAM§8] §7Варны игрока §f" + target.getName() + ":");
        for (int i = 0; i < warns.size(); i++) {
            WarnManager.WarnEntry w = warns.get(i);
            viewer.sendMessage(" §c#" + (i + 1) + " §7" + w.reason() + " §8(§7" + w.moderator() + "§8) §8" + TimeUtils.formatDuration((System.currentTimeMillis() - w.timestamp()) / 1000) + " назад");
        }
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
            SoundUtil.success(viewer);
            viewer.sendMessage("§a✓ Заметка добавлена.");
            new PlayerCardGUI(plugin, viewer, target).open();
        });
    }

    private void handleIpInfo() {
        if (!viewer.hasPermission("amgui.viewip")) return;
        Map<String, long[]> ipData = plugin.getAltDetector().getIpData(target.getUniqueId());
        if (ipData.isEmpty()) {
            viewer.sendMessage("§eНет данных по IP для " + target.getName());
            return;
        }
        viewer.sendMessage("§8[§cAM§8] §7IP адреса §f" + target.getName() + ":");
        for (Map.Entry<String, long[]> e : ipData.entrySet()) {
            String ip = e.getKey();
            long[] times = e.getValue();
            viewer.sendMessage(" §8- §c" + ip + " §8(" + TimeUtils.formatLogTime(times[0]) + " — " + TimeUtils.formatLogTime(times[1]) + ")");
        }
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
        viewer.openInventory(buildInventory());
        plugin.getGuiManager().register(viewer.getUniqueId(), this);
    }
}
