package me.admin.gui.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private TextUtil() {}

    public static Component legacy(String value) {
        return LEGACY.deserialize(value == null ? "" : value);
    }

    public static String plain(Component value) {
        return value == null ? "" : PlainTextComponentSerializer.plainText().serialize(value);
    }
}
