package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AutoModManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Locale;

/** Searchable/filterable rule browser; mutations remain permission-gated and confirmed. */
public final class AutoModRulesGUI extends PaginatedGUI {
    private enum Filter { ALL, ENABLED, DISABLED }
    private Filter filter = Filter.ALL;
    private String search = "";
    private List<AutoModManager.Rule> visible = List.of();

    public AutoModRulesGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }
    @Override public String getTitle() { return plugin.getLocalizationManager().get("gui.automod-rules.title", "&8AutoMod: правила"); }
    @Override public void open() { if (!viewer.hasPermission("amgui.automod.view")) { deny(); return; } super.open(); }

    @Override public void buildContent() {
        String needle = search.toLowerCase(Locale.ROOT);
        visible = plugin.getAutoModManager().getRules().values().stream().filter(rule -> switch (filter) {
            case ALL -> true; case ENABLED -> rule.isEnabled(); case DISABLED -> !rule.isEnabled();
        }).filter(rule -> needle.isBlank() || rule.getId().toLowerCase(Locale.ROOT).contains(needle)
                || rule.getName().toLowerCase(Locale.ROOT).contains(needle)
                || rule.getCategory().toLowerCase(Locale.ROOT).contains(needle)).toList();
        contentItems.clear();
        boolean compact = plugin.getWorkflowAssignmentManager().isCompact(viewer.getUniqueId());
        for (AutoModManager.Rule rule : visible) contentItems.add(new ItemBuilder(rule.isEnabled() ? Material.ENCHANTED_BOOK : Material.BOOK)
                .name((rule.isEnabled() ? "&a" : "&c") + rule.getName())
                .lore(compact ? List.of("&7" + rule.getId() + " • " + rule.getAction()) : List.of(
                        "&7ID: &f" + rule.getId(), "&7Статус: " + (rule.isEnabled() ? "&aВКЛ" : "&cВЫКЛ"),
                        "&7Категория: &f" + rule.getCategory() + " &8/ &7тяжесть: &f" + rule.getSeverity(),
                        "&7Действие: &f" + rule.getAction(), "&7Длительность: &f" + (rule.getDuration() > 0 ? TimeUtils.formatDuration(rule.getDuration()) : "—"),
                        rule.getAutoDisabledReason().isBlank() ? "&7Circuit breaker: &aOK" : "&c" + rule.getAutoDisabledReason(),
                        "", viewer.hasPermission("amgui.automod.edit") ? "&eКлик — переключить (с подтверждением)" : "&8Только просмотр"))
                .glowing(rule.isEnabled()).build());
        if (page * MAX_ITEMS_PER_PAGE >= visible.size()) page = 0;
    }

    @Override protected Inventory buildInventory() {
        Inventory inventory = super.buildInventory();
        inventory.setItem(46, new ItemBuilder(Material.HOPPER).name("&eФильтр: &f" + filter).lore("&eКлик — переключить").build());
        inventory.setItem(47, new ItemBuilder(Material.OAK_SIGN).name("&bПоиск")
                .lore(search.isBlank() ? "&7Поиск не задан" : "&7Запрос: &f" + search, "&eКлик — ввести").build());
        inventory.setItem(48, new ItemBuilder(Material.REPEATER).name("&e« AutoMod").build());
        inventory.setItem(50, new ItemBuilder(Material.KNOWLEDGE_BOOK).name(plugin.getLocalizationManager().get("gui.inbox.legend", "&bЛегенда"))
                .lore("&eЛКМ &7— переключить правило", "&eПоиск/фильтр &7— над списком", "&7Свечение — правило включено").build());
        return inventory;
    }

    @Override public void onClick(int slot) {
        if (slot == 46) { filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length]; page = 0; refresh(); return; }
        if (slot == 47) { viewer.closeInventory(); plugin.getChatInputManager().awaitInput(viewer, "§eПоиск правила (cancel — сбросить):", value -> {
            search = value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена") ? "" : value.trim(); page = 0; open(); }); return; }
        if (slot == 48) { new AutoModGUI(plugin, viewer).open(); return; }
        if (slot >= 45) { handlePaginatedClick(slot); return; }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= visible.size()) return;
        AutoModManager.Rule captured = visible.get(index);
        boolean expectedEnabled = captured.isEnabled();
        if (!viewer.hasPermission("amgui.automod.edit")) { deny(); return; }
        new ConfirmGUI(plugin, viewer, (expectedEnabled ? "§cВыключить " : "§aВключить ") + captured.getName() + "?", () -> {
            if (!viewer.hasPermission("amgui.automod.edit")) { deny(); return; }
            AutoModManager.Rule current = plugin.getAutoModManager().getRules().get(captured.getId());
            if (current == null || current.isEnabled() != expectedEnabled) viewer.sendMessage("§cПравило уже изменено. Список обновлён.");
            else plugin.getAutoModManager().toggleRule(captured.getId());
            open();
        }, this::open).open();
    }
    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
}
