package dev.aurora.social;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Process-wide registry of friended player names, shared by the combat modules (which must never
 * target a friend) and ESP (which paints friends in a distinct color). Names are matched
 * case-insensitively; the originally-added spelling is preserved for display.
 *
 * <p>The desktop control panel owns the canonical list and pushes it to the agent over IPC; the
 * agent applies it here via {@link #setAll(List)}. Reads happen on the render/tick threads while
 * writes happen on the IPC thread, so all access is synchronized.
 */
public final class FriendManager {
    private static final FriendManager INSTANCE = new FriendManager();

    /** Lower-cased name -> display name, preserving insertion order for a stable friends list. */
    private final Map<String, String> friends = new LinkedHashMap<>();

    private FriendManager() {
    }

    public static FriendManager get() {
        return INSTANCE;
    }

    /** Whether {@code name} (a player display name) is currently friended, ignoring case. */
    public boolean isFriend(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        synchronized (friends) {
            return friends.containsKey(normalize(name));
        }
    }

    /** Replaces the entire friends list, e.g. when the control panel pushes an updated set. */
    public void setAll(List<String> names) {
        synchronized (friends) {
            friends.clear();
            if (names != null) {
                for (String name : names) {
                    if (name != null && !name.isBlank()) {
                        friends.put(normalize(name), name.strip());
                    }
                }
            }
        }
    }

    public List<String> names() {
        synchronized (friends) {
            return new ArrayList<>(friends.values());
        }
    }

    private static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
