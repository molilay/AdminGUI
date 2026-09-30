package me.admin.gui.utils;

import java.util.Locale;
import java.util.Set;

public final class SensitiveCommandFilter {

    private static final Set<String> SENSITIVE = Set.of(
            "login", "l", "log", "register", "reg", "changepassword", "changepass",
            "password", "passwd", "2fa", "totp", "authme"
    );

    private SensitiveCommandFilter() {}

    public static String commandName(String command) {
        if (command == null) return "";
        String value = command.strip();
        if (value.startsWith("/")) value = value.substring(1);
        int space = value.indexOf(' ');
        String name = (space < 0 ? value : value.substring(0, space)).toLowerCase(Locale.ROOT);
        int namespace = name.indexOf(':');
        return namespace < 0 ? name : name.substring(namespace + 1);
    }

    public static boolean isSensitive(String command) {
        return SENSITIVE.contains(commandName(command));
    }

    public static String redact(String command) {
        String name = commandName(command);
        return name.isBlank() ? "/<redacted>" : "/" + name + " <redacted>";
    }
}
