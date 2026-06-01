package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public class VaultIntegration {

    private final AdvancedModeratorGUI plugin;
    private Economy economy;

    public VaultIntegration(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            plugin.getLogger().info("Vault подключён.");
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public void fine(OfflinePlayer player, double amount) {
        if (economy != null && economy.hasAccount(player)) {
            economy.withdrawPlayer(player, amount);
        }
    }

    public void reward(OfflinePlayer player, double amount) {
        if (economy != null && economy.hasAccount(player)) {
            economy.depositPlayer(player, amount);
        }
    }
}
