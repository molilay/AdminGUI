package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class StaffModeManager {

    public enum StaffTool {
        TELEPORT_TO, PLAYER_LIST, INVESTIGATE, QUICK_TELEPORT, MOD_MENU,
        KICK, TELEPORT_HERE, FREEZE, EXIT
    }

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> staffMode = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, GameMode> savedGamemode = new HashMap<>();
    private final Map<UUID, Boolean> savedFly = new HashMap<>();
    private final Map<UUID, Boolean> savedFlying = new HashMap<>();
    private final Map<UUID, Boolean> savedVanish = new HashMap<>();
    private final NamespacedKey toolKey;

    public StaffModeManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "staff-tool");
    }

    public void toggle(Player staff) {
        if (staffMode.contains(staff.getUniqueId())) disable(staff);
        else enable(staff);
    }

    public void enable(Player staff) {
        if (!staff.hasPermission("amgui.staffmode")) {
            staff.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            plugin.getAuditManager().record(staff, "staffmode.enable-denied", staff.getName(),
                    staff.getUniqueId(), "missing=amgui.staffmode");
            return;
        }
        UUID uid = staff.getUniqueId();
        if (staffMode.contains(uid)) return;
        savedInventories.put(uid, cloneItems(staff.getInventory().getContents()));
        savedArmor.put(uid, cloneItems(staff.getInventory().getArmorContents()));
        savedGamemode.put(uid, staff.getGameMode());
        savedFly.put(uid, staff.getAllowFlight());
        savedFlying.put(uid, staff.isFlying());
        boolean alreadyVanished = plugin.getVanishManager().isVanished(staff);
        savedVanish.put(uid, alreadyVanished);

        staff.getInventory().clear();
        staff.setGameMode(GameMode.CREATIVE);
        staff.setAllowFlight(true);
        staff.setFlying(true);
        if (!alreadyVanished) plugin.getVanishManager().vanish(staff);
        staffMode.add(uid);
        giveItems(staff);
        staff.sendMessage("§a✓ Режим персонала включён.");
        plugin.getAuditManager().record(staff, "staffmode.enable", staff.getName(), uid, "");
        plugin.getStaffActivityManager().ifPresent(s -> s.log(staff, "StaffMode", null, "Включён", 0));
    }

    public void disable(Player staff) {
        UUID uid = staff.getUniqueId();
        if (!staffMode.contains(uid)) return;

        staff.getInventory().clear();
        ItemStack[] inventory = savedInventories.get(uid);
        ItemStack[] armor = savedArmor.get(uid);
        if (inventory != null) staff.getInventory().setContents(inventory);
        if (armor != null) staff.getInventory().setArmorContents(armor);
        staff.setGameMode(savedGamemode.getOrDefault(uid, GameMode.SURVIVAL));
        staff.setAllowFlight(savedFly.getOrDefault(uid, false));
        staff.setFlying(staff.getAllowFlight() && savedFlying.getOrDefault(uid, false));

        if (!savedVanish.getOrDefault(uid, false)) plugin.getVanishManager().unvanish(staff);
        savedInventories.remove(uid);
        savedArmor.remove(uid);
        savedGamemode.remove(uid);
        savedFly.remove(uid);
        savedFlying.remove(uid);
        savedVanish.remove(uid);
        staffMode.remove(uid);
        staff.sendMessage("§c✓ Режим персонала выключен.");
        plugin.getAuditManager().record(staff, "staffmode.disable", staff.getName(), uid, "");
        plugin.getStaffActivityManager().ifPresent(s -> s.log(staff, "StaffMode", null, "Выключен", 0));
    }

    public boolean isStaffMode(Player staff) {
        return staffMode.contains(staff.getUniqueId());
    }

    public void giveItems(Player staff) {
        if (!staffMode.contains(staff.getUniqueId())) return;
        PlayerInventory inv = staff.getInventory();
        inv.setItem(0, tool(new ItemBuilder(Material.COMPASS).name("&aТелепортация")
                .lore("&7Кликните по игроку").build(), StaffTool.TELEPORT_TO));
        inv.setItem(1, tool(new ItemBuilder(Material.BOOK).name("&eСписок игроков")
                .lore("&7Открыть меню игроков").build(), StaffTool.PLAYER_LIST));
        inv.setItem(2, tool(new ItemBuilder(Material.BLAZE_ROD).name("&6Палочка расследования")
                .lore("&7Клик по игроку → карточка").build(), StaffTool.INVESTIGATE));
        inv.setItem(3, tool(new ItemBuilder(Material.CLOCK).name("&bБыстрый телепорт")
                .lore("&7Клик по игроку → ТП").build(), StaffTool.QUICK_TELEPORT));
        inv.setItem(4, tool(new ItemBuilder(Material.NETHER_STAR).name("&cМеню модератора")
                .lore("&7Открыть /mod").build(), StaffTool.MOD_MENU));
        inv.setItem(5, tool(new ItemBuilder(Material.DIAMOND_SWORD).name("&cУдар-кик")
                .lore("&7Ударьте игрока, чтобы кикнуть").build(), StaffTool.KICK));
        inv.setItem(6, tool(new ItemBuilder(Material.ENDER_EYE).name("&5Притянуть игрока")
                .lore("&7Клик по игроку → сюда").build(), StaffTool.TELEPORT_HERE));
        inv.setItem(7, tool(new ItemBuilder(Material.ICE).name("&bЗаморозка")
                .lore("&7Клик по игроку → переключить").build(), StaffTool.FREEZE));
        inv.setItem(8, tool(new ItemBuilder(Material.BARRIER).name("&cВыйти из StaffMode")
                .lore("&7Нажмите для выхода").build(), StaffTool.EXIT));
    }

    public void handleItemClick(Player staff, ItemStack item) {
        StaffTool tool = getTool(item).orElse(null);
        if (tool == StaffTool.EXIT) {
            disable(staff);
        } else if (tool == StaffTool.MOD_MENU) {
            if (!requireToolPermission(staff, tool, "amgui.use")) return;
            staff.performCommand("mod");
        } else if (tool == StaffTool.PLAYER_LIST) {
            if (!requireToolPermission(staff, tool, "amgui.player")) return;
            staff.performCommand("mod online");
        }
    }

    public Optional<StaffTool> getTool(ItemStack item) {
        if (item == null || item.getType().isAir()) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        String id = meta.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (id == null) return Optional.empty();
        try { return Optional.of(StaffTool.valueOf(id)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    public boolean requireToolPermission(Player staff, StaffTool tool, String permission) {
        if (staff.hasPermission(permission)) return true;
        staff.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
        plugin.getAuditManager().record(staff, "staffmode.tool-denied", tool.name(), null,
                "missing=" + permission);
        return false;
    }

    private ItemStack tool(ItemStack item, StaffTool tool) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    public void disableAll() {
        new ArrayList<>(staffMode).forEach(uid -> {
            Player player = Bukkit.getPlayer(uid);
            if (player != null && player.isOnline()) disable(player);
        });
    }
}
