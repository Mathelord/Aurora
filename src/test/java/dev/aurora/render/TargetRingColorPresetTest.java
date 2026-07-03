package dev.aurora.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TargetRingColorPresetTest {
    @Test
    void packsTheRequestedAlphaIntoTheHighByte() {
        int color = TargetRingColorPreset.Aqua.colorAt(0.0D, 128);
        assertEquals(128, (color >>> 24) & 0xFF);
    }

    @Test
    void clampsAlphaIntoTheValidRange() {
        assertEquals(255, (TargetRingColorPreset.Fire.colorAt(0.0D, 999) >>> 24) & 0xFF);
        assertEquals(0, (TargetRingColorPreset.Fire.colorAt(0.0D, -20) >>> 24) & 0xFF);
    }

    @Test
    void byIndexClampsOutOfRangeIndexes() {
        assertSame(TargetRingColorPreset.values()[0], TargetRingColorPreset.byIndex(-5));
        TargetRingColorPreset[] values = TargetRingColorPreset.values();
        assertSame(values[values.length - 1], TargetRingColorPreset.byIndex(999));
    }

    @Test
    void exposesOneTitlePerPreset() {
        assertEquals(TargetRingColorPreset.values().length, TargetRingColorPreset.titles().size());
    }
}
