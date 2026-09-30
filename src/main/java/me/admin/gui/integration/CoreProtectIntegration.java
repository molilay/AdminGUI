package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class CoreProtectIntegration {

    private final AdvancedModeratorGUI plugin;
    private boolean enabled = false;
    private Object coreProtectAPI;
    private Method performLookup;
    private Method parseResult;

    public CoreProtectIntegration(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        try {
            Class<?> apiClass = Class.forName("net.coreprotect.CoreProtectAPI");
            Class<?> coreProtectClass = Class.forName("net.coreprotect.CoreProtect");

            Object instance = coreProtectClass.getMethod("getInstance").invoke(null);
            Method getApi = instance.getClass().getMethod("getAPI");
            coreProtectAPI = getApi.invoke(instance);

            performLookup = apiClass.getMethod("performLookup", List.class, int.class, int.class, int.class, int.class, List.class, List.class, List.class);

            parseResult = apiClass.getMethod("parseResult", Object.class);

            enabled = true;
            plugin.getLogger().info("CoreProtect подключён.");
        } catch (Exception e) {
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    public List<String> getBlockHistory(OfflinePlayer target) {
        if (!enabled) return List.of("§cCoreProtect не найден на сервере.");

        List<String> result = new ArrayList<>();
        try {
            List<Object> lookup = new ArrayList<>();
            int time = 86400;
            List<String> users = new ArrayList<>();
            if (target.getName() != null) users.add(target.getName());
            List<Object> restrict = new ArrayList<>();
            List<String> actionList = new ArrayList<>();
            actionList.add("block");

            Object rawResult = performLookup.invoke(coreProtectAPI, lookup, time, 0, 0, 0, users, restrict, actionList);

            if (rawResult instanceof List<?> entries) {
                int limit = Math.min(entries.size(), 50);
                for (int i = 0; i < limit; i++) {
                    Object entry = entries.get(i);
                    Object parsed = parseResult.invoke(coreProtectAPI, entry);
                    if (parsed instanceof String) {
                        result.add("§7" + (String) parsed);
                    } else {
                        result.add("§7" + entry.toString());
                    }
                }
                if (entries.isEmpty()) {
                    result.add("§7Блоков не найдено (последние 24ч).");
                } else if (entries.size() > limit) {
                    result.add("§8... и ещё " + (entries.size() - limit) + " записей");
                }
            }
        } catch (Exception e) {
            result.add("§cОшибка CoreProtect: " + e.getMessage());
        }
        return result;
    }

    public void sendBlockHistory(Player viewer, OfflinePlayer target) {
        viewer.sendMessage("§8[§cCoreProtect§8] §7История блоков: §f" + (target.getName() != null ? target.getName() : "?"));
        viewer.sendMessage("§8┌───────────────────────────────────");
        List<String> history = getBlockHistory(target);
        for (String line : history) {
            viewer.sendMessage("§8│ " + line);
        }
        viewer.sendMessage("§8└───────────────────────────────────");
    }
}
