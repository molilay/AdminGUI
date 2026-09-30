package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.DoctorManager;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class DoctorGUI extends PaginatedGUI {

    private DoctorManager.Report report;
    public DoctorGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }
    @Override public String getTitle() { return plugin.getLocalizationManager().get("gui.doctor.title", "&8AMGUI Doctor"); }

    @Override
    public void buildContent() {
        contentItems.clear();
        report = plugin.getDoctorManager().run();
        for (DoctorManager.Check check : report.checks()) {
            Material material = switch (check.severity()) {
                case OK -> Material.LIME_DYE;
                case WARN -> Material.YELLOW_DYE;
                case ERROR -> Material.RED_DYE;
            };
            String color = switch (check.severity()) { case OK -> "&a"; case WARN -> "&e"; case ERROR -> "&c"; };
            contentItems.add(new ItemBuilder(material).name(color + check.severity() + " &f" + check.title())
                    .lore("&7" + check.details(), "", "&8ID: " + check.id()).glowing(check.severity() == DoctorManager.Severity.ERROR).build());
        }
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) { handlePaginatedClick(slot); return; }
        if (slot < 0 || slot >= contentItems.size()) return;
        DoctorManager.Check check = report.checks().get(slot);
        viewer.sendMessage("§8[§cDoctor§8] §f" + check.title() + ": §7" + check.details());
    }
}
