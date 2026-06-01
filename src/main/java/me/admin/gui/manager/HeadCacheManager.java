package me.admin.gui.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HeadCacheManager {

    private final Map<UUID, ItemStack> headCache = new HashMap<>();

    public ItemStack getHead(OfflinePlayer player) {
        if (headCache.containsKey(player.getUniqueId())) {
            return headCache.get(player.getUniqueId()).clone();
        }

        ItemStack head = createHead(player);
        headCache.put(player.getUniqueId(), head.clone());
        return head;
    }

    public CompletableFuture<ItemStack> getHeadAsync(OfflinePlayer player) {
        if (headCache.containsKey(player.getUniqueId())) {
            return CompletableFuture.completedFuture(headCache.get(player.getUniqueId()).clone());
        }

        return CompletableFuture.supplyAsync(() -> {
            ItemStack head = createHead(player);
            headCache.put(player.getUniqueId(), head.clone());
            return head;
        });
    }

    private ItemStack createHead(OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + player.getName());
            if (player.isOnline()) {
                PlayerProfile profile = ((org.bukkit.entity.Player) player).getPlayerProfile();
                meta.setPlayerProfile(profile);
            } else {
                meta.setOwningPlayer(player);
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    public void preCache(OfflinePlayer player) {
        getHeadAsync(player);
    }

    public void clearCache() {
        headCache.clear();
    }
}
