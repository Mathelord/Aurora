package dev.aurora.social;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendManagerTest {
    private final FriendManager friends = FriendManager.get();

    @AfterEach
    void clear() {
        friends.setAll(List.of());
    }

    @Test
    void matchesFriendsCaseInsensitively() {
        friends.setAll(List.of("Notch"));

        assertTrue(friends.isFriend("Notch"));
        assertTrue(friends.isFriend("notch"));
        assertTrue(friends.isFriend("NOTCH"));
        assertFalse(friends.isFriend("Herobrine"));
    }

    @Test
    void setAllReplacesPreviousList() {
        friends.setAll(List.of("Alice", "Bob"));
        friends.setAll(List.of("Carol"));

        assertFalse(friends.isFriend("Alice"));
        assertTrue(friends.isFriend("Carol"));
        assertEquals(List.of("Carol"), friends.names());
    }

    @Test
    void ignoresBlankAndNullEntries() {
        friends.setAll(java.util.Arrays.asList("  ", null, "Dave"));

        assertEquals(List.of("Dave"), friends.names());
        assertFalse(friends.isFriend(""));
        assertFalse(friends.isFriend("   "));
    }
}
