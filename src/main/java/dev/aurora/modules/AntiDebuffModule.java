package dev.aurora.modules;

import dev.aurora.api.AbstractModule;

/** Hides blindness and nausea visuals while leaving their gameplay effects unchanged. */
public final class AntiDebuffModule extends AbstractModule {
    public AntiDebuffModule() {
        super("anti-debuff", "Anti Debuff", "Render",
                "Hides blindness darkness and nausea screen warping without changing the effects themselves.");
    }
}
