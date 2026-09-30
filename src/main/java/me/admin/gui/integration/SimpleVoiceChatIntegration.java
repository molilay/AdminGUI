package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleVoiceChatIntegration {

    private final AdvancedModeratorGUI plugin;
    private boolean enabled = false;
    private Object voicechatApi;
    private final ConcurrentHashMap<UUID, Boolean> voiceMuted = new ConcurrentHashMap<>();

    public SimpleVoiceChatIntegration(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        check();
    }

    private void check() {
        try {
            if (Bukkit.getPluginManager().getPlugin("VoiceChat") == null) {
                enabled = false;
                return;
            }
            Class<?> serviceClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            Object service = serviceClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method registerMethod = service.getClass().getMethod("register");
            voicechatApi = registerMethod.invoke(service);
            enabled = true;
            plugin.getLogger().info("SimpleVoiceChat подключён.");
        } catch (Exception e) {
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    public void mute(Player player) {
        if (!enabled) return;
        voiceMuted.put(player.getUniqueId(), true);
        if (voicechatApi == null) return;
        try {
            java.lang.reflect.Method serverApiMethod = voicechatApi.getClass().getMethod("getServerApi");
            Object serverApi = serverApiMethod.invoke(voicechatApi);
            if (serverApi != null) {
                java.lang.reflect.Method muteMethod = serverApi.getClass().getMethod("mutePlayer", UUID.class);
                muteMethod.invoke(serverApi, player.getUniqueId());
            }
        } catch (Exception ignored) {}
    }

    public void unmute(Player player) {
        if (!enabled) return;
        voiceMuted.remove(player.getUniqueId());
        if (voicechatApi == null) return;
        try {
            java.lang.reflect.Method serverApiMethod = voicechatApi.getClass().getMethod("getServerApi");
            Object serverApi = serverApiMethod.invoke(voicechatApi);
            if (serverApi != null) {
                java.lang.reflect.Method unmuteMethod = serverApi.getClass().getMethod("unmutePlayer", UUID.class);
                unmuteMethod.invoke(serverApi, player.getUniqueId());
            }
        } catch (Exception ignored) {}
    }

    public boolean isVoiceMuted(Player player) {
        return voiceMuted.containsKey(player.getUniqueId());
    }
}
