package dev.aurora.minecraft;

/** Enchantments {@link MinecraftBridge#hotbarEnchantmentLevel} can look up, identified by their
 * vanilla registry path (e.g. {@code minecraft:breach} -> {@code "breach"}) rather than a mapped
 * class/field name, since that identifier is content data and stable across mappings. */
public enum EnchantmentType {
    BREACH("breach"),
    DENSITY("density");

    private final String path;

    EnchantmentType(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
