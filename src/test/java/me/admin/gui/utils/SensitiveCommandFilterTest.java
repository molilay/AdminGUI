package me.admin.gui.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveCommandFilterTest {

    @Test
    void recognizesNamespacedLoginCommands() {
        assertTrue(SensitiveCommandFilter.isSensitive("/authme:login secret"));
        assertTrue(SensitiveCommandFilter.isSensitive(" /REGISTER secret secret "));
        assertTrue(SensitiveCommandFilter.isSensitive("/minecraft:login password"));
        assertTrue(SensitiveCommandFilter.isSensitive("/AUTHME:TOTP 123456"));
        assertFalse(SensitiveCommandFilter.isSensitive("/msg Steve hello"));
    }

    @Test
    void redactsEveryArgument() {
        assertEquals("/login <redacted>", SensitiveCommandFilter.redact("/login very-secret"));
        assertFalse(SensitiveCommandFilter.redact("/2fa 123456").contains("123456"));
    }
}
