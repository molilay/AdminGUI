package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import me.admin.gui.manager.GeoIpManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class IpHistoryGUI extends PaginatedGUI {

    private final OfflinePlayer target;
    private List<String> displayedIps = List.of();

    public IpHistoryGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.viewip")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
        plugin.getAuditManager().record(viewer, "ip.history-view", target.getName(), target.getUniqueId(),
                "entries=" + displayedIps.size());
    }

    @Override
    public String getTitle() {
        return "&8IP история: " + (target.getName() != null ? target.getName() : "?");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        Map<String, long[]> ipData = plugin.getAltDetector().getIpData(target.getUniqueId());
        displayedIps = List.copyOf(ipData.keySet());
        for (Map.Entry<String, long[]> e : ipData.entrySet()) {
            if (e.getValue() != null && e.getValue().length >= 2) {
                long[] times = e.getValue();
                String firstStr = times[0] > 0 ? TimeUtils.formatLogTime(times[0]) : "неизвестно";
                String lastStr = times[1] > 0 ? TimeUtils.formatLogTime(times[1]) : "неизвестно";
                GeoIpManager.GeoIpResult cached = plugin.getGeoIpManager().getCached(e.getKey());
                ItemBuilder item = new ItemBuilder(cached == null ? Material.REDSTONE_TORCH : Material.COMPASS)
                        .name("&c" + e.getKey()).lore("&7Первый вход: &f" + firstStr, "&7Последний вход: &f" + lastStr);
                if (cached != null) item.lore("&7GeoIP: &f" + cached.locationLine(), "&7Провайдер: &f" + display(cached.isp()));
                item.lore("", viewer.hasPermission("amgui.geoip") ? "&eЛКМ — примерное местоположение" : "&8Нет права на GeoIP",
                        "&bПКМ — показать IP в чате");
                contentItems.add(item.build());
            } else {
                contentItems.add(new ItemBuilder(Material.REDSTONE_TORCH)
                        .name("&c" + e.getKey())
                        .lore("&7Нет данных о времени")
                        .build());
            }
        }
        if (contentItems.isEmpty()) {
            contentItems.add(new ItemBuilder(Material.BARRIER)
                    .name("&7Нет данных")
                    .lore("&7IP адреса не записаны")
                    .build());
        }
    }

    @Override
    public void onClick(int slot) {
        onClick(slot, false, false);
    }

    @Override
    public void onClick(int slot, boolean shift, boolean right) {
        if (slot >= 45) {
            if (slot == 48) {
                if (!plugin.getGuiManager().goBack(viewer)) new InvestigationCenterGUI(plugin, viewer, target).open();
                return;
            }
            if (slot == 50) { scanAll(); return; }
            handlePaginatedClick(slot);
            return;
        }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= displayedIps.size()) return;
        String ip = displayedIps.get(index);
        if (right) {
            if (!viewer.hasPermission("amgui.viewip")) { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return; }
            viewer.sendMessage("§8[§cAM§8] §7IP показан в чате: §f" + ip);
            viewer.sendMessage("§8▸ §7" + ip);
            plugin.getAuditManager().record(viewer, "ip.reveal-chat", target.getName(), target.getUniqueId(), "history-entry");
        } else if (viewer.hasPermission("amgui.geoip") && viewer.hasPermission("amgui.viewip")) {
            new GeoIpGUI(plugin, viewer, ip, target).open();
        } else {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
        }
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        int start = page * MAX_ITEMS_PER_PAGE;
        int end = Math.min(start + MAX_ITEMS_PER_PAGE, contentItems.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, contentItems.get(i));
        }
        for (int i = 45; i < SIZE; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemBuilder.createFiller());
        }
        int totalPages = Math.max(1, (int) Math.ceil((double) contentItems.size() / MAX_ITEMS_PER_PAGE));
        if (page > 0) inv.setItem(45, ItemBuilder.createPreviousButton());
        inv.setItem(49, ItemBuilder.createPageInfo(page, totalPages));
        if (end < contentItems.size()) inv.setItem(53, ItemBuilder.createNextButton());
        inv.setItem(48, new ItemBuilder(Material.SPYGLASS).name("&e« В центр расследования").build());
        if (viewer.hasPermission("amgui.geoip") && viewer.hasPermission("amgui.viewip") && !displayedIps.isEmpty()) {
            long cached = displayedIps.stream().filter(ip -> plugin.getGeoIpManager().getCached(ip) != null).count();
            inv.setItem(50, new ItemBuilder(Material.RECOVERY_COMPASS).name("&bПроверить все IP")
                    .lore("&7Кэшировано: &f" + cached + "&7/&f" + displayedIps.size(),
                            "&7Покажет смены стран и", "&7максимальное расстояние").glowing(cached == displayedIps.size()).build());
        }
        inv.setItem(52, ItemBuilder.createCloseButton());
        return inv;
    }

    private void scanAll() {
        if (!viewer.hasPermission("amgui.geoip") || !viewer.hasPermission("amgui.viewip")) { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return; }
        int max = Math.clamp(plugin.getConfig().getInt("ip-geolocation.max-batch-lookups", 20), 1, 50);
        List<String> ips = displayedIps.stream().limit(max).toList();
        viewer.sendMessage("§eGeoIP: проверка §f" + ips.size() + " §eадресов...");
        List<CompletableFuture<GeoIpManager.GeoIpResult>> futures = ips.stream()
                .map(ip -> plugin.getGeoIpManager().lookup(ip).exceptionally(ignored -> null)).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((unused, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) return;
                    List<GeoIpManager.GeoIpResult> results = futures.stream().map(CompletableFuture::join)
                            .filter(java.util.Objects::nonNull).toList();
                    Set<String> countries = new HashSet<>();
                    results.stream().map(GeoIpManager.GeoIpResult::countryCode).filter(value -> !value.isBlank()).forEach(countries::add);
                    double maxDistance = 0;
                    for (int i = 0; i < results.size(); i++) for (int j = i + 1; j < results.size(); j++) {
                        double distance = GeoIpManager.distanceKm(results.get(i), results.get(j));
                        if (!Double.isNaN(distance)) maxDistance = Math.max(maxDistance, distance);
                    }
                    viewer.sendMessage("§a✓ GeoIP получен: §f" + results.size() + "§7/§f" + ips.size()
                            + " §8| §7стран: §f" + countries.size() + " §8| §7макс. расстояние: §f"
                            + String.format(Locale.ROOT, "%.0f км", maxDistance));
                    if (countries.size() > 1) viewer.sendMessage("§e⚠ IP игрока определялись в разных странах: §f" + String.join(", ", countries));
                    plugin.getAuditManager().record(viewer, "ip.geolocation-batch", target.getName(), target.getUniqueId(),
                            "requested=" + ips.size() + "; success=" + results.size() + "; countries=" + countries.size());
                    refresh();
                }));
    }

    private static String display(String value) { return value == null || value.isBlank() ? "—" : value; }
}
