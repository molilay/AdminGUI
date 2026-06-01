package me.admin.gui.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GUIManager {

    private final Map<UUID, PaginatedGUI> openGUIs = new HashMap<>();

    public void register(UUID playerId, PaginatedGUI gui) {
        openGUIs.put(playerId, gui);
    }

    public void unregister(UUID playerId) {
        openGUIs.remove(playerId);
    }

    public PaginatedGUI getOpenGUI(org.bukkit.entity.Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    public boolean hasGUI(org.bukkit.entity.Player player) {
        return openGUIs.containsKey(player.getUniqueId());
    }
}
