package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.PaginatedGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PunishmentTemplateManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final List<Template> templates = new ArrayList<>();

    public record Template(String name, String type, String reason, long duration, String displayName, Material icon, String severity) {
        public Template(String name, String type, String reason, long duration, String displayName, Material icon) {
            this(name, type, reason, duration, displayName, icon, "medium");
        }
    }

    public PunishmentTemplateManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "templates.yml");
        load();
    }

    public void addTemplate(String name, String type, String reason, long duration, String displayName, Material icon) {
        addTemplate(name, type, reason, duration, displayName, icon, "medium");
    }

    public void addTemplate(String name, String type, String reason, long duration, String displayName, Material icon, String severity) {
        templates.removeIf(t -> t.name().equalsIgnoreCase(name));
        templates.add(new Template(name, type, reason, duration, displayName, icon, severity));
        save();
    }

    public void removeTemplate(String name) {
        templates.removeIf(t -> t.name().equalsIgnoreCase(name));
        save();
    }

    public Template getTemplate(String name) {
        return templates.stream().filter(t -> t.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public List<Template> getTemplates() { return Collections.unmodifiableList(templates); }

    public void applyTemplate(Player staff, String templateName, String targetName) {
        Template t = getTemplate(templateName);
        if (t == null) { staff.sendMessage("§cШаблон не найден."); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { staff.sendMessage("§cИгрок не найден."); return; }

        PunishmentSecurityManager.Decision decision = plugin.getPunishmentSecurityManager()
                .validate(staff, target, t.type());
        if (!decision.allowed()) {
            staff.sendMessage("§cДействие запрещено: " + decision.reason());
            return;
        }

        String action = t.type();
        switch (action) {
            case "kick" -> {
                target.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§cВы кикнуты.\n§7Причина: " + t.reason()));
                plugin.getDatabaseManager().logPunishment("kick", staff.getName(), targetName, t.reason(), -1);
                plugin.getConfigManager().sendPunishmentTitle(staff, "kick", targetName);
                plugin.getConfigManager().playPunishmentSound(staff, "kick");
            }
            case "ban" -> {
                me.admin.gui.utils.BanService.banProfile(targetName, t.reason(), t.duration() > 0
                        ? java.time.Instant.ofEpochMilli(System.currentTimeMillis() + t.duration() * 1000)
                        : null, staff.getName());
                target.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§cВы забанены.\n§7Причина: " + t.reason()));
                plugin.getDatabaseManager().logPunishment(t.duration() > 0 ? "tempban" : "ban", staff.getName(), targetName, t.reason(), t.duration());
                plugin.getConfigManager().sendPunishmentTitle(staff, "ban", targetName);
                plugin.getConfigManager().playPunishmentSound(staff, "ban");
            }
            case "mute" -> {
                plugin.getMuteManager().mute(target, t.reason(), staff.getName(), t.duration());
                plugin.getConfigManager().sendPunishmentTitle(staff, "mute", targetName);
                plugin.getConfigManager().playPunishmentSound(staff, "mute");
            }
            case "warn" -> {
                plugin.getWarnManager().warn(target, t.reason(), staff.getName());
                plugin.getConfigManager().sendPunishmentTitle(staff, "warn", targetName);
                plugin.getConfigManager().playPunishmentSound(staff, "warn");
            }
        }

        if (target.hasPermission("amgui.staffchat")) {
            String msg = "§8[§c⚠§8] §f" + staff.getName() + " §cнаказал сотрудника §f" + targetName;
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("amgui.admin"))
                .forEach(p -> p.sendMessage(msg));
            plugin.getDatabaseManager().logPunishment("staffpunish-" + action, staff.getName(), targetName, t.reason(), t.duration());
        }

        staff.sendMessage("§a✓ Шаблон §f" + t.displayName() + " §aприменён к §f" + targetName);
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Template t : templates) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            m.put("type", t.type());
            m.put("reason", t.reason());
            m.put("duration", t.duration());
            m.put("display", t.displayName());
            m.put("icon", t.icon().name());
            m.put("severity", t.severity());
            list.add(m);
        }
        config.set("templates", list);
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "punishment templates");
    }

    private void load() {
        if (!dataFile.exists()) {
            addDefault("grief", "ban", "Гриферство (разрушение построек)", 604800L, "&cГриферство", Material.GRASS_BLOCK, "high");
            addDefault("cheat", "ban", "Использование читов / запрещённых модов", 0, "&cЧитерство", Material.DIAMOND_SWORD, "high");
            addDefault("spam", "mute", "Спам в чате", 3600L, "&eСпам (1ч)", Material.PAPER, "low");
            addDefault("caps", "warn", "Капс в чате", 0, "&6Капс", Material.OAK_SIGN, "low");
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<?, ?>> raw = yml.getMapList("templates");
        for (Map<?, ?> m : raw) {
            try {
                String severity = m.containsKey("severity") ? (String) m.get("severity") : "medium";
                templates.add(new Template(
                        (String) m.get("name"),
                        (String) m.get("type"),
                        (String) m.get("reason"),
                        ((Number) m.get("duration")).longValue(),
                        (String) m.get("display"),
                        Material.valueOf((String) m.get("icon")),
                        severity
                ));
            } catch (Exception ignored) {}
        }
    }

    private void addDefault(String name, String type, String reason, long duration, String display, Material icon, String severity) {
        templates.add(new Template(name, type, reason, duration, display, icon, severity));
    }

    public long calculateDuration(String severity, int priorOffenses) {
        return switch (severity) {
            case "low" -> 3600L;
            case "medium" -> 86400L;
            case "high" -> 604800L;
            default -> 86400L;
        };
    }

    public static class PunishmentTemplateGUI extends PaginatedGUI {

        public PunishmentTemplateGUI(AdvancedModeratorGUI plugin, Player viewer) {
            super(plugin, viewer);
        }

        @Override
        public String getTitle() { return "§8Шаблоны наказаний"; }

        @Override
        public void buildContent() {
            contentItems.clear();
            for (PunishmentTemplateManager.Template t : plugin.getPunishmentTemplateManager().getTemplates()) {
                String severityColor = switch (t.severity().toLowerCase()) {
                    case "low" -> "&a";
                    case "medium" -> "&e";
                    case "high" -> "&c";
                    default -> "&7";
                };
                contentItems.add(new ItemBuilder(t.icon())
                        .name(t.displayName())
                        .lore(
                                "&7Тип: &f" + t.type(),
                                "&7Причина: &f" + t.reason(),
                                t.duration() > 0 ? "&7Срок: &f" + TimeUtils.formatDuration(t.duration()) : "&7Срок: &cНавсегда",
                                "&7Серьёзность: " + severityColor + t.severity(),
                                "",
                                "&aЛКМ — выбрать игрока",
                                "&cПКМ — удалить шаблон"
                        ).build());
            }
        }

        @Override
        public void onClick(int slot) {
            onClick(slot, false);
        }

        @Override
        public void onClick(int slot, boolean shift) {
            if (slot >= 45) {
                if (slot == SLOT_MAIN_MENU) {
                    plugin.getGuiManager().unregister(viewer.getUniqueId());
                    new me.admin.gui.gui.MainMenu(plugin, viewer).open();
                    return;
                }
                handlePaginatedClick(slot);
                return;
            }
            int index = page * MAX_ITEMS_PER_PAGE + slot;
            List<PunishmentTemplateManager.Template> list = plugin.getPunishmentTemplateManager().getTemplates();
            if (index < 0 || index >= list.size()) return;

            PunishmentTemplateManager.Template t = list.get(index);
            if (shift) {
                new me.admin.gui.gui.ConfirmGUI(plugin, viewer, "§cУдалить шаблон §f" + t.displayName() + "§c?",
                    () -> {
                        plugin.getPunishmentTemplateManager().removeTemplate(t.name());
                        viewer.sendMessage("§c✓ Шаблон §f" + t.displayName() + " §cудалён.");
                        refresh();
                    },
                    () -> refresh()
                ).open();
                return;
            }

            viewer.closeInventory();
            plugin.getChatInputManager().awaitInput(viewer, "§eВведите ник игрока для применения §f" + t.displayName() + "§e:", input -> {
                String target = input.trim();
                if (target.isEmpty()) { new PunishmentTemplateGUI(plugin, viewer).open(); return; }
                plugin.getPunishmentTemplateManager().applyTemplate(viewer, t.name(), target);
                new PunishmentTemplateGUI(plugin, viewer).open();
            });
        }
    }
}
