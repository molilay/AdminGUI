package me.admin.gui.gui;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import me.admin.gui.utils.TextUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GUIManager {

    private record Session(UUID id, PaginatedGUI gui) {}
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Deque<PaginatedGUI>> histories = new HashMap<>();
    private static final int MAX_HISTORY = 12;

    public void register(UUID playerId, PaginatedGUI gui) {
        sessions.put(playerId, new Session(UUID.randomUUID(), gui));
    }

    public void unregister(UUID playerId) {
        sessions.remove(playerId);
    }

    /**
     * Opens a managed menu without losing the previous screen. Bukkit fires an
     * InventoryCloseEvent synchronously while switching inventories, therefore
     * the new menu is registered after the switch has completed.
     */
    public void open(Player player, PaginatedGUI gui, Inventory inventory, boolean rememberCurrent) {
        UUID playerId = player.getUniqueId();
        Session currentSession = sessions.get(playerId);
        PaginatedGUI current = currentSession == null ? null : currentSession.gui();
        if (rememberCurrent && current != null && current != gui) {
            Deque<PaginatedGUI> history = histories.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
            if (history.peekLast() != current) history.addLast(current);
            while (history.size() > MAX_HISTORY) history.removeFirst();
        }
        UUID sessionId = UUID.randomUUID();
        Inventory managed = wrapManaged(player, gui, inventory, sessionId);
        player.openInventory(managed);
        sessions.put(playerId, new Session(sessionId, gui));
    }

    public boolean goBack(Player player) {
        UUID playerId = player.getUniqueId();
        Deque<PaginatedGUI> history = histories.get(playerId);
        if (history == null || history.isEmpty()) return false;
        PaginatedGUI previous = history.removeLast();
        sessions.remove(playerId);
        previous.openFromHistory();
        if (history.isEmpty()) histories.remove(playerId);
        return true;
    }

    public void clearContext(UUID playerId) {
        sessions.remove(playerId);
        histories.remove(playerId);
    }

    /** Clears all retained GUI state for a disconnecting player. */
    public void cleanup(UUID playerId) {
        clearContext(playerId);
    }

    public PaginatedGUI getOpenGUI(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session == null ? null : session.gui();
    }

    public boolean hasGUI(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public PaginatedGUI resolve(Player player, Inventory top) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return null;
        InventoryHolder holder = top.getHolder();
        if (holder instanceof AMGuiHolder managed) {
            if (!managed.ownerId().equals(player.getUniqueId()) || !managed.sessionId().equals(session.id())) return null;
            return managed.menu() == session.gui() ? session.gui() : null;
        }
        // Compatibility for real editable player inventories, immutable snapshot
        // holders and the few legacy adapters not yet converted to a typed holder.
        return session.gui();
    }

    public void closed(Player player, Inventory top) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (top.getHolder() instanceof AMGuiHolder holder && !holder.sessionId().equals(session.id())) return;
        sessions.remove(player.getUniqueId());
    }

    public void home(Player player, PaginatedGUI dashboard) {
        histories.remove(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        dashboard.buildContent();
        open(player, dashboard, dashboard.buildInventory(), false);
    }

    /** Opens Bukkit-owned editable inventories without pretending they are immutable AMGUI menus. */
    public void openEditableExternal(Player player, Inventory inventory) {
        clearContext(player.getUniqueId());
        player.openInventory(inventory);
    }

    private static Inventory wrapManaged(Player player, PaginatedGUI gui, Inventory source, UUID sessionId) {
        if (source.getHolder() != null) return source;
        AMGuiHolder holder = new AMGuiHolder(sessionId, player.getUniqueId(), gui, AMGuiHolder.Access.MANAGED_READ_ONLY);
        Inventory result = Bukkit.createInventory(holder, source.getSize(), TextUtil.legacy(gui.getTitle()));
        holder.attach(result);
        result.setContents(source.getContents());
        return result;
    }
}
