package dev.aurora.api;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}
