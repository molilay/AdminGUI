package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.PlayerCardGUI;
import me.admin.gui.manager.StaffModeManager.StaffTool;
import me.admin.gui.manager.security.ActionReceiptManager;
import me.admin.gui.manager.security.ModerationActionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class StaffModeListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public StaffModeListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player staff = event.getPlayer();
        if (!plugin.getStaffModeManager().isStaffMode(staff)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !stillAuthorized(staff)) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        StaffTool tool = plugin.getStaffModeManager().getTool(staff.getInventory().getItemInMainHand()).orElse(null);
        if (tool == null) return;
        switch (tool) {
            case TELEPORT_TO, QUICK_TELEPORT -> {
                if (!execute(staff, target, ModerationActionService.Action.TELEPORT)) return;
                staff.teleport(target);
                audit(staff, target, "staffmode.teleport-to");
                staff.sendMessage("§a✓ Телепортирован к §f" + target.getName());
            }
            case INVESTIGATE -> {
                if (!plugin.getStaffModeManager().requireToolPermission(staff, tool,
                        "amgui.investigation.center")) return;
                new PlayerCardGUI(plugin, staff, target).open();
                audit(staff, target, "staffmode.investigate");
            }
            case TELEPORT_HERE -> {
                if (!execute(staff, target, ModerationActionService.Action.TELEPORT)) return;
                target.teleport(staff);
                audit(staff, target, "staffmode.teleport-here");
                staff.sendMessage("§a✓ Притянули §f" + target.getName());
            }
            case FREEZE -> {
                if (!execute(staff, target, ModerationActionService.Action.FREEZE)) return;
                boolean previouslyFrozen = plugin.getFreezeManager().isFrozen(target);
                if (previouslyFrozen) plugin.getFreezeManager().unfreeze(target);
                else plugin.getFreezeManager().freeze(target);
                ActionReceiptManager.forPlugin(plugin).record(staff, target,
                        ActionReceiptManager.Type.FREEZE, Boolean.toString(previouslyFrozen));
                audit(staff, target, "staffmode.freeze-toggle");
                staff.sendMessage("§b✓ Состояние заморозки изменено для §f" + target.getName());
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player staff)) return;
        if (!plugin.getStaffModeManager().isStaffMode(staff)) return;
        event.setCancelled(true);
        if (!stillAuthorized(staff) || !(event.getEntity() instanceof Player target)) return;
        StaffTool tool = plugin.getStaffModeManager().getTool(staff.getInventory().getItemInMainHand()).orElse(null);
        if (tool != StaffTool.KICK) return;
        if (!execute(staff, target, ModerationActionService.Action.KICK)) return;

        plugin.getInventoryRollbackManager().saveSnapshot(target, "staffmode_kick");
        audit(staff, target, "staffmode.kick");
        target.kick(me.admin.gui.utils.TextUtil.legacy("§cКикнуты модератором через StaffMode."));
        staff.sendMessage("§c✓ Кикнули §f" + target.getName());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player staff = event.getPlayer();
        if (!plugin.getStaffModeManager().isStaffMode(staff)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !stillAuthorized(staff)) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = staff.getInventory().getItemInMainHand();
            plugin.getStaffModeManager().handleItemClick(staff, item);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getStaffModeManager().disable(event.getPlayer());
    }

    private boolean stillAuthorized(Player staff) {
        if (staff.hasPermission("amgui.staffmode")) return true;
        plugin.getStaffModeManager().requireToolPermission(staff, StaffTool.EXIT, "amgui.staffmode");
        plugin.getStaffModeManager().disable(staff);
        return false;
    }

    private boolean execute(Player staff, Player target, ModerationActionService.Action action) {
        var decision = ModerationActionService.execute(plugin, staff, target, action);
        if (decision.allowed()) return true;
        staff.sendMessage("§cДействие запрещено: " + decision.reason());
        return false;
    }

    private void audit(Player staff, Player target, String action) {
        plugin.getAuditManager().record(staff, action, target.getName(), target.getUniqueId(), "tool=pdc");
    }
}
