package me.admin.gui.utils;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundUtil {

    public static void click(Player p) {
        try { p.playSound(p, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f); }
        catch (Exception ignored) {}
    }

    public static void success(Player p) {
        try { p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f); }
        catch (Exception ignored) {}
    }

    public static void error(Player p) {
        try { p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f); }
        catch (Exception ignored) {}
    }
}
