package dev.aurora.modules;

public interface CrystalComboController {
    boolean canStartSilentAuraCombo(String targetId);

    boolean queueSilentAuraCombo(String targetId);
}
