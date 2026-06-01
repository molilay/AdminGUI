package me.admin.gui.manager;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatInputManager {

    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();

    public void awaitInput(Player player, String prompt, Consumer<String> callback) {
        synchronized (pendingInputs) {
            pendingInputs.put(player.getUniqueId(), new PendingInput(callback, System.currentTimeMillis()));
        }
        player.sendMessage(prompt);
    }

    public void awaitInput(Player player, Consumer<String> callback) {
        awaitInput(player, "§eВведите значение в чат (30 сек):", callback);
    }

    public boolean hasInput(Player player) {
        synchronized (pendingInputs) {
            PendingInput input = pendingInputs.get(player.getUniqueId());
            if (input == null) return false;
            if (System.currentTimeMillis() - input.timestamp > 30000) {
                pendingInputs.remove(player.getUniqueId());
                player.sendMessage("§cВремя ввода истекло.");
                return false;
            }
            return true;
        }
    }

    public void handleInput(Player player, String message) {
        PendingInput input;
        synchronized (pendingInputs) {
            input = pendingInputs.remove(player.getUniqueId());
        }
        if (input != null) {
            input.callback.accept(message);
        }
    }

    private record PendingInput(Consumer<String> callback, long timestamp) {}
}
