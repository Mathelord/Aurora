package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.network.PacketRelay;

import java.util.Objects;

/**
 * Delays the complete inbound packet stream by a configurable amount. The module refreshes a
 * short-lived lease every client tick so a failed or detached module cannot leave packet capture
 * enabled indefinitely.
 */
public final class NetworkDelayModule extends AbstractModule {
    static final long REQUEST_TTL_MS = 250L;

    private final PacketRelay relay;
    private final ModuleSetting latencyMs;

    public NetworkDelayModule(PacketRelay relay) {
        super("network-delay", "Network Delay", "Network",
                "Delays the complete inbound stream so the server observes the added latency.");
        this.relay = Objects.requireNonNull(relay, "relay");
        latencyMs = setting("latency-ms", "Delay ms", 150.0D, 25.0D, 2_000.0D, 25.0D)
                .description("Time all inbound packets are held, including keep-alive and transaction pings.");
    }

    @Override
    public void onEnable() {
        refreshLease();
    }

    @Override
    public void onDisable() {
        relay.release(this);
    }

    @Override
    public void onTick(TickEvent event) {
        refreshLease();
    }

    private void refreshLease() {
        relay.request(this, (int) Math.round(latencyMs.value()), REQUEST_TTL_MS);
    }
}
