package dev.aurora.modules;

import dev.aurora.api.events.TickEvent;
import dev.aurora.network.PacketRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkDelayModuleTest {
    private final PacketRelay relay = PacketRelay.get();
    private final NetworkDelayModule module = new NetworkDelayModule(relay);

    @AfterEach
    void resetRelay() {
        module.setEnabled(false);
        relay.reset();
    }

    @Test
    void enablingAndDisablingAcquiresAndReleasesDelayLease() {
        module.setEnabled(true);
        assertTrue(relay.isLagging());

        module.setEnabled(false);
        assertFalse(relay.isLagging());
    }

    @Test
    void tickRefreshesLeaseWhileEnabled() {
        module.setEnabled(true);
        module.onTick(TickEvent.now());

        assertTrue(relay.isLagging());
    }
}
