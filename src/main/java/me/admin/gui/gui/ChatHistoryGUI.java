package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.ChatHistoryManager;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ChatHistoryGUI extends PaginatedGUI {

    private final OfflinePlayer target;
    private List<ChatHistoryManager.ChatMessage> messages;

    public ChatHistoryGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public String getTitle() {
        String name = target.getName() != null ? target.getName() : "?";
        return "§8История чата: " + name;
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        messages = plugin.getChatHistoryManager().getHistory(target.getUniqueId());

        if (messages.isEmpty()) {
            contentItems.add(new ItemBuilder(Material.BARRIER)
                    .name("&7Нет сообщений")
                    .lore("&7У игрока пока нет сохранённой", "&7истории чата")
                    .build());
            return;
        }

        for (ChatHistoryManager.ChatMessage msg : messages) {
            String formatted = msg.getFormatted();
            boolean isCommand = msg.isCommand();

            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7" + formatted);
            if (isCommand) {
                lore.add("§7[команда]");
            }

            contentItems.add(new ItemBuilder(isCommand ? Material.COMMAND_BLOCK : Material.PAPER)
                    .name((isCommand ? "&e/" : "&f") + truncate(msg.message(), 40))
                    .lore(lore.toArray(new String[0]))
                    .build());
        }
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = super.buildInventory();
        if (messages != null) {
            inv.setItem(49, new ItemBuilder(Material.BOOK)
                    .name("&7Сообщений: &f" + messages.size())
                    .lore("&7Показаны последние сообщения")
                    .build());
        }
        return inv;
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            if (slot == PaginatedGUI.SLOT_CLOSE) { close(); return; }
            if (slot == PaginatedGUI.SLOT_MAIN_MENU) {
                openHome();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index >= 0 && index < (messages != null ? messages.size() : 0)) {
            ChatHistoryManager.ChatMessage msg = messages.get(index);
            viewer.sendMessage("§8[§cAM§8] §7" + msg.getFormatted());
        }
    }

    @Override
    public void open() {
        super.open();
    }
}
