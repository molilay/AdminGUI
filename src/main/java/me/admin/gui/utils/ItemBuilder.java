package me.admin.gui.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(name.replace("&", "§"));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (meta == null) return this;
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(line.replace("&", "§"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta == null) return this;
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(line.replace("&", "§"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setLore(String... lines) {
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            for (String line : lines) {
                lore.add(line.replace("&", "§"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder glowing() {
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder glowing(boolean value) {
        if (value) glowing();
        return this;
    }

    public ItemStack build() {
        return item;
    }

    public static ItemStack createFiller() {
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
    }

    public static ItemStack createBackButton() {
        return new ItemBuilder(Material.ARROW).name("&7← Назад").build();
    }

    public static ItemStack createCloseButton() {
        return new ItemBuilder(Material.BARRIER).name("&c✕ Закрыть").build();
    }

    public static ItemStack createNextButton() {
        return new ItemBuilder(Material.ARROW).name("&7Вперёд →").build();
    }

    public static ItemStack createPreviousButton() {
        return new ItemBuilder(Material.ARROW).name("&7← Назад").build();
    }

    public static ItemStack createPageInfo(int currentPage, int totalPages) {
        return new ItemBuilder(Material.PAPER)
                .name("&eСтраница &6" + (currentPage + 1) + " &eиз &6" + totalPages)
                .build();
    }

    public static ItemStack createConfirmButton() {
        return new ItemBuilder(Material.GREEN_WOOL).name("&a✓ Подтвердить").glowing().build();
    }

    public static ItemStack createCancelButton() {
        return new ItemBuilder(Material.RED_WOOL).name("&c✕ Отмена").build();
    }

    public static ItemStack createPlayerHead() {
        return new ItemBuilder(Material.PLAYER_HEAD).build();
    }
}
