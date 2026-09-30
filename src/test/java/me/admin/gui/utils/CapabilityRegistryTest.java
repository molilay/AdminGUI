package me.admin.gui.utils;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRegistryTest {
    @Test void granularBackupPermissionDoesNotRequireLegacyParent() {
        CommandSender verifier = sender(Set.of("amgui.backup.verify"));
        assertTrue(CapabilityRegistry.allows(verifier, CapabilityRegistry.Capability.BACKUP_ANY));
        assertTrue(CapabilityRegistry.visibleAdminCommands(verifier).contains("backup"));
        assertTrue(CapabilityRegistry.visibleBackupCommands(verifier).contains("verify"));
        assertFalse(CapabilityRegistry.visibleBackupCommands(verifier).contains("restore"));
    }

    @Test void categoryUsesAnyButSensitiveToolCanRequireAll() {
        CommandSender caseWorker = sender(Set.of("amgui.cases"));
        assertTrue(CapabilityRegistry.allows(caseWorker, CapabilityRegistry.Capability.DASH_INVESTIGATIONS));
        CommandSender banOnly = sender(Set.of("amgui.ban"));
        assertFalse(CapabilityRegistry.allows(banOnly, CapabilityRegistry.Capability.IP_BANS));
    }

    private static CommandSender sender(Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> permissions.contains(String.valueOf(args[0]));
                    case "isPermissionSet" -> permissions.contains(String.valueOf(args[0]));
                    case "getName" -> "tester";
                    case "isOp" -> false;
                    case "spigot" -> new CommandSender.Spigot();
                    default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
                });
    }
}
