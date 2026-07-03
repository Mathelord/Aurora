package dev.aurora.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <T extends Event> Subscription subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.getOrDefault(eventType, new CopyOnWriteArrayList<>()).remove(listener);
    }

    public void publish(Event event) {
        dispatch(event.getClass(), event);
        for (Class<?> eventInterface : event.getClass().getInterfaces()) {
            dispatch(eventInterface, event);
        }
    }

    private void dispatch(Class<?> eventType, Event event) {
        List<Consumer<?>> consumers = listeners.get(eventType);
        if (consumers == null) {
            return;
        }
        for (Consumer<?> consumer : consumers) {
            @SuppressWarnings("unchecked")
            Consumer<Event> typedConsumer = (Consumer<Event>) consumer;
            typedConsumer.accept(event);
        }
    }
}
