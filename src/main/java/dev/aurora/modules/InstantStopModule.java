package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;

/** Applies a one-tick opposite directional input when a movement key is released. */
public final class InstantStopModule extends AbstractModule {
    private static final int W = 87, A = 65, S = 83, D = 68;
    private final MinecraftBridge minecraft;
    private boolean wTap, sTap, aTap, dTap;
    private boolean previousW, previousS, previousA, previousD;

    public InstantStopModule(MinecraftBridge minecraft) {
        super("instant-stop", "Instant Stop", "Movement", "Presses oposite key to counter strafe the movement");
        this.minecraft = minecraft;
    }
    @Override public void onEnable() { clear(); }
    @Override public void onDisable() { release(); previousW = previousS = previousA = previousD = false; }
    @Override public void onTick(TickEvent event) {
        release();
        boolean w = minecraft.isKeyDown(W), s = minecraft.isKeyDown(S);
        boolean a = minecraft.isKeyDown(A), d = minecraft.isKeyDown(D);
        tap(W, S, previousW && !w, s); tap(S, W, previousS && !s, w);
        tap(A, D, previousA && !a, d); tap(D, A, previousD && !d, a);
        previousW = w; previousS = s; previousA = a; previousD = d;
    }
    private void tap(int key, int opposite, boolean held, boolean oppositeHeld) {
        if (!held || oppositeHeld || tapped(key)) return;
        set(opposite, true); mark(key);
    }
    private boolean tapped(int key) { return key == W ? wTap : key == S ? sTap : key == A ? aTap : dTap; }
    private void mark(int key) { if (key == W) wTap = true; else if (key == S) sTap = true; else if (key == A) aTap = true; else dTap = true; }
    private void set(int key, boolean held) { if (key == W) minecraft.setForwardKeyHeld(held); else if (key == S) minecraft.setBackwardKeyHeld(held); else if (key == A) minecraft.setLeftKeyHeld(held); else minecraft.setRightKeyHeld(held); }
    private void release() { if (wTap) minecraft.setBackwardKeyHeld(false); if (sTap) minecraft.setForwardKeyHeld(false); if (aTap) minecraft.setRightKeyHeld(false); if (dTap) minecraft.setLeftKeyHeld(false); clear(); }
    private void clear() { wTap = sTap = aTap = dTap = false; }
}
