package dev.aurora.injector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPanelGuiTest {
    @Test
    void helpBadgeCanBeConstructedForAnExpandedSetting() {
        ControlPanelGui.HelpButton badge = assertDoesNotThrow(() ->
                new ControlPanelGui.HelpButton("Maximum target acquisition distance."));

        assertTrue(badge.getToolTipText().contains("Maximum target acquisition distance."));
    }

    @Test
    void reachWarningBadgeExposesUnsafeTooltip() {
        ControlPanelGui.WarningBadge badge = assertDoesNotThrow(() ->
                new ControlPanelGui.WarningBadge("Unsafe"));

        assertEquals("Unsafe", badge.getToolTipText());
        assertEquals("Unsafe", badge.getAccessibleContext().getAccessibleName());
    }
}
