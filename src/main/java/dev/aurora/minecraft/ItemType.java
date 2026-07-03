package dev.aurora.minecraft;

import java.util.List;

public enum ItemType {
    END_CRYSTAL("END_CRYSTAL", "field_8301", "wf"),
    OBSIDIAN("OBSIDIAN", "field_8281", "eR"),
    WOODEN_AXE("WOODEN_AXE", "field_8406", "pu"),
    STONE_AXE("STONE_AXE", "field_8062", "pz"),
    GOLDEN_AXE("GOLDEN_AXE", "field_8825", "pE"),
    IRON_AXE("IRON_AXE", "field_8475", "pJ"),
    DIAMOND_AXE("DIAMOND_AXE", "field_8556", "pO"),
    NETHERITE_AXE("NETHERITE_AXE", "field_22025", "pT"),
    MACE("MACE", "field_49814", "va"),
    SHIELD("SHIELD", "field_8255", "ws");

    private final List<String> fieldNames;

    ItemType(String... fieldNames) {
        this.fieldNames = List.of(fieldNames);
    }

    public String fieldName() {
        return fieldNames.getFirst();
    }

    public List<String> fieldNames() {
        return fieldNames;
    }
}
