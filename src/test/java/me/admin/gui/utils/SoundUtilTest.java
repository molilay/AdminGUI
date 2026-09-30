package me.admin.gui.utils;

import org.bukkit.Sound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that GUI sounds degrade safely: a configured or built-in name resolves through the
 * compatibility layer, an unknown name never throws.
 */
class SoundUtilTest {

    @BeforeEach
    void resetCache() {
        SoundUtil.reload();
    }

    @Test
    void resolvesBuiltInFallbacks() {
        assertEquals(Sound.UI_BUTTON_CLICK, SoundUtil.resolve("click", "UI_BUTTON_CLICK"));
        assertEquals(Sound.ENTITY_PLAYER_LEVELUP, SoundUtil.resolve("success", "ENTITY_PLAYER_LEVELUP"));
        assertEquals(Sound.ENTITY_VILLAGER_NO, SoundUtil.resolve("error", "ENTITY_VILLAGER_NO"));
    }

    @Test
    void configuredNameWinsOverFallback() {
        assertNotNull(SoundUtil.resolve("click", "UI_BUTTON_CLICK"));
        assertEquals(Sound.ENTITY_VILLAGER_NO, SoundUtil.resolve("custom", "ENTITY_VILLAGER_NO"));
    }

    @Test
    void unknownSoundNamesResolveToNullInsteadOfThrowing() {
        assertNull(SoundUtil.resolve("missing", "SOUND_THAT_DOES_NOT_EXIST"));
        assertNull(SoundUtil.resolve("unknown"));
    }

    @Test
    void blankKeysAreIgnored() {
        assertNull(SoundUtil.resolve(null));
        assertNull(SoundUtil.resolve(""));
        assertNull(SoundUtil.resolve("   "));
    }

    @Test
    void playingForAMissingPlayerIsANoOp() {
        SoundUtil.click(null);
        SoundUtil.success(null);
        SoundUtil.error(null);
    }
}
