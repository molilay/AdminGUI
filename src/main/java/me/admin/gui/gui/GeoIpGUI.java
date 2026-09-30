package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.GeoIpManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Locale;

/** In-game view of approximate GeoIP data. */
public final class GeoIpGUI extends PaginatedGUI {

    private final String ip;
    private final OfflinePlayer target;
    private volatile GeoIpManager.GeoIpResult result;
    private volatile String error;

    public GeoIpGUI(AdvancedModeratorGUI plugin, Player viewer, String ip, OfflinePlayer target) {
        super(plugin, viewer);
        this.ip = ip;
        this.target = target;
        this.result = plugin.getGeoIpManager().getCached(ip);
    }

    @Override
    public String getTitle() {
        String name = target != null && target.getName() != null ? target.getName() : ip;
        return plugin.getLocalizationManager().format("gui.geoip.title", "&8Геолокация: %target%", "target", name);
    }

    @Override public void buildContent() {}

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.geoip") || !viewer.hasPermission("amgui.viewip")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
        if (result == null && error == null) load();
    }

    private void load() {
        plugin.getGeoIpManager().lookup(ip).whenComplete((loaded, failure) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (failure == null) {
                    result = loaded;
                    plugin.getAuditManager().record(viewer, "ip.geolocation", targetName(),
                            target == null ? null : target.getUniqueId(),
                            "provider=ipwho.is; network=" + me.admin.gui.utils.IpPrivacyUtil.mask(loaded.ip()));
                    SoundUtil.success(viewer);
                } else {
                    Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    SoundUtil.error(viewer);
                }
                if (plugin.getGuiManager().getOpenGUI(viewer) == this) refresh();
            });
        });
    }

    private String targetName() {
        return target != null && target.getName() != null ? target.getName() : ip;
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());

        if (result == null && error == null) {
            inventory.setItem(22, new ItemBuilder(Material.CLOCK).name("&eПолучение данных...")
                    .lore("&7Запрос выполняется асинхронно", "&7IP не сохраняется в отдельное хранилище.").glowing().build());
        } else if (error != null) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER).name("&cНе удалось определить местоположение")
                    .lore("&7" + trim(error, 90), "", "&eКликните, чтобы повторить запрос").build());
        } else {
            String coords = Double.isNaN(result.latitude()) || Double.isNaN(result.longitude())
                    ? "неизвестно" : String.format(Locale.ROOT, "%.3f, %.3f", result.latitude(), result.longitude());
            if (!plugin.getConfig().getBoolean("ip-geolocation.show-coordinates", true)) coords = "скрыты настройкой сервера";
            inventory.setItem(20, new ItemBuilder(Material.FILLED_MAP).name("&bПримерное местоположение")
                    .lore("&7Страна: &f" + display(result.country()) + codeSuffix(result.countryCode()),
                            "&7Регион: &f" + display(result.region()), "&7Город: &f" + display(result.city()),
                            "&7Координаты центра сети: &f" + coords,
                            "", "&c⚠ GeoIP не показывает точный адрес человека.").build());
            inventory.setItem(22, new ItemBuilder(Material.COMPASS).name("&eСетевые данные")
                    .lore("&7IP: &f" + result.ip(), "&7Тип: &f" + display(result.connectionType()),
                            "&7Провайдер: &f" + display(result.isp()), "&7Организация: &f" + display(result.organization()),
                            "&7Часовой пояс: &f" + display(result.timezone())).glowing().build());
            boolean reputationKnown = result.proxy() != null || result.vpn() != null || result.tor() != null || result.hosting() != null;
            boolean suspicious = Boolean.TRUE.equals(result.proxy()) || Boolean.TRUE.equals(result.vpn())
                    || Boolean.TRUE.equals(result.tor()) || Boolean.TRUE.equals(result.hosting());
            inventory.setItem(23, new ItemBuilder(reputationKnown ? suspicious ? Material.REDSTONE : Material.EMERALD : Material.GRAY_DYE)
                    .name(reputationKnown ? suspicious ? "&cСетевая репутация: подозрительно" : "&aСетевая репутация: чисто"
                            : "&7Сетевая репутация недоступна")
                    .lore(reputationKnown ? new String[]{"&7Proxy: " + yesNo(result.proxy()), "&7VPN: " + yesNo(result.vpn()),
                                    "&7Tor: " + yesNo(result.tor()), "&7Hosting: " + yesNo(result.hosting())}
                            : new String[]{"&7Текущий тариф/API не вернул", "&7поля proxy, VPN, Tor и hosting."}).glowing(suspicious).build());
            inventory.setItem(24, new ItemBuilder(Material.KNOWLEDGE_BOOK).name("&6О точности")
                    .lore("&7Данные относятся к диапазону IP", "&7и обычно указывают город/регион провайдера.",
                            "&7VPN, прокси и мобильные сети", "&7могут показывать другое место.").build());
        }

        if (target != null) inventory.setItem(48, new ItemBuilder(Material.ARROW).name("&e« К IP-истории").build());
        if (result != null) inventory.setItem(50, new ItemBuilder(Material.RECOVERY_COMPASS).name("&bОбновить GeoIP")
                .lore("&7Очистить запись из памяти", "&7и повторить HTTPS-запрос").build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }

    private static String display(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static String yesNo(Boolean value) { return value == null ? "&7—" : value ? "&cда" : "&aнет"; }
    private static String codeSuffix(String value) { return value == null || value.isBlank() ? "" : " &8(" + value + ")"; }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }

    @Override
    public void onClick(int slot) {
        if (slot == 22 && error != null) {
            error = null;
            refresh();
            load();
        } else if (slot == 48 && target != null) {
            new IpHistoryGUI(plugin, viewer, target).open();
        } else if (slot == 50 && result != null) {
            plugin.getGeoIpManager().invalidate(ip);
            result = null;
            error = null;
            refresh();
            load();
        } else if (slot == SLOT_CLOSE) {
            close();
        }
    }
}
