package dev.aurora.api;

import dev.aurora.api.events.TickEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventBusTest {
    @Test
    void publishesToRegisteredListeners() {
        EventBus bus = new EventBus();
        AtomicInteger calls = new AtomicInteger();

        bus.subscribe(TickEvent.class, ignored -> calls.incrementAndGet());
        bus.publish(TickEvent.now());

        assertEquals(1, calls.get());
    }

    @Test
    void subscriptionCanBeClosed() {
        EventBus bus = new EventBus();
        AtomicInteger calls = new AtomicInteger();

        Subscription subscription = bus.subscribe(TickEvent.class, ignored -> calls.incrementAndGet());
        subscription.close();
        bus.publish(TickEvent.now());

        assertEquals(0, calls.get());
    }
}
