package dev.aurora.input;

import java.util.concurrent.atomic.AtomicReference;

/** A small exclusive gate used for short, state-sensitive client transactions. */
public final class GameplayInputGate {
    private static final AtomicReference<String> OWNER = new AtomicReference<>();

    private GameplayInputGate() {
    }

    public static boolean acquire(String owner) {
        if (owner == null || owner.isBlank()) return false;
        String active = OWNER.get();
        return owner.equals(active) || OWNER.compareAndSet(null, owner);
    }

    public static void release(String owner) {
        if (owner != null) OWNER.compareAndSet(owner, null);
    }

    public static boolean isActive() {
        return OWNER.get() != null;
    }

    static String ownerForTest() {
        return OWNER.get();
    }

    public static void clear() {
        OWNER.set(null);
    }
}
