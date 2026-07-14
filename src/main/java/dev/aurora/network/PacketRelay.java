package dev.aurora.network;

import dev.aurora.aim.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Shared "hold packets back, replay them later" engine for modules that need a controlled view of
 * network state. Aurora has no compile-time Minecraft dependency, so packet replay is resolved from
 * the active runtime namespace rather than through mapped packet classes.
 *
 * <p>Inbound holding ({@link #request}) is time-based and shared by every caller. The effective
 * delay is the largest active request, allowing independent modules to coexist. Outbound holding
 * ({@link #holdOutbound}/{@link #releaseOutbound}) is flushed explicitly rather than by a timer.
 */
public final class PacketRelay {
    private static final PacketRelay INSTANCE = new PacketRelay();
    // Yarn, intermediary and official-obfuscated names for the supported Minecraft versions.
    private static final List<String> OUTBOUND_SEND_METHOD_NAMES = List.of("send", "method_10743", "a");
    private static final List<String> PACKET_APPLY_METHOD_NAMES = List.of("apply", "method_65081", "a");
    private static final Set<String> ENTITY_MOVE_PACKET_CLASSES = Set.of(
            "net.minecraft.network.packet.s2c.play.EntityS2CPacket",
            "net.minecraft.class_2684"
    );
    private static final double DELTA_PER_BLOCK = 4096.0D;
    private static final int MAX_HELD_INBOUND = 8192;
    private static final int MAX_HELD_OUTBOUND = 512;

    private final Map<Object, Request> inboundRequests = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<InboundPacket> heldInbound = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<OutboundPacket> heldOutbound = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean replayFailureLogged = new AtomicBoolean();
    private final AtomicLong heldInboundTotal = new AtomicLong();
    private final AtomicLong replayedInboundTotal = new AtomicLong();
    private final AtomicLong heldOutboundTotal = new AtomicLong();
    private final AtomicLong replayedOutboundTotal = new AtomicLong();
    private final AtomicLong replayFailures = new AtomicLong();
    private volatile Consumer<String> diagnosticSink = message -> System.err.println("[Aurora] " + message);
    private volatile String lastError = "";
    private volatile int effectiveInboundLatencyMs;
    private volatile boolean forceFlushInbound;
    private volatile boolean outboundHeld;
    private final ThreadLocal<Boolean> replayingOutbound = ThreadLocal.withInitial(() -> false);

    private PacketRelay() {
    }

    public static PacketRelay get() {
        return INSTANCE;
    }

    /** Asks for {@code latencyMs} of inbound lag for at most {@code ttlMs}. A latency of 0 or less
     * releases. The packet that triggered the call (if any) is captured automatically — the caller's
     * {@code onPacket} handler runs before the suppress decision is made for that same packet. */
    public void request(Object owner, int latencyMs, long ttlMs) {
        if (owner == null) {
            return;
        }
        if (latencyMs <= 0 || ttlMs <= 0) {
            release(owner);
            return;
        }
        inboundRequests.put(owner, new Request(latencyMs, System.currentTimeMillis() + ttlMs));
        recomputeInboundLatency();
    }

    public void release(Object owner) {
        if (owner != null && inboundRequests.remove(owner) != null) {
            recomputeInboundLatency();
        }
    }

    public boolean isLagging() {
        return effectiveInboundLatencyMs > 0;
    }

    public void setDiagnosticSink(Consumer<String> diagnosticSink) {
        this.diagnosticSink = diagnosticSink == null
                ? message -> System.err.println("[Aurora] " + message)
                : diagnosticSink;
    }

    public void holdOutbound() {
        if (!outboundHeld) {
            heldOutbound.clear();
        }
        outboundHeld = true;
    }

    public void releaseOutbound() {
        outboundHeld = false;
    }

    public void discardOutbound() {
        outboundHeld = false;
        heldOutbound.clear();
    }

    public int heldOutboundCount() {
        return heldOutbound.size();
    }

    public int heldInboundCount() {
        return heldInbound.size();
    }

    public Vec3 heldDisplacement(int entityId) {
        if (entityId < 0) {
            return Vec3.ZERO;
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (InboundPacket held : heldInbound) {
            if (held.entityId == entityId) {
                x += held.deltaX;
                y += held.deltaY;
                z += held.deltaZ;
            }
        }
        return new Vec3(x, y, z);
    }

    public Metrics metrics() {
        return new Metrics(heldInbound.size(), heldOutbound.size(), effectiveInboundLatencyMs,
                heldInboundTotal.get(), replayedInboundTotal.get(), heldOutboundTotal.get(),
                replayedOutboundTotal.get(), replayFailures.get(), lastError);
    }

    /** Sends every held outbound packet immediately, in order, then turns outbound holding off. */
    public void flushOutbound() {
        outboundHeld = false;
        OutboundPacket held;
        while ((held = heldOutbound.pollFirst()) != null) {
            resend(held.connection, held.packet);
        }
    }

    /** Called at Minecraft's static packet-dispatch boundary. The listener is supplied directly by
     * Minecraft, avoiding a reflective lookup through {@code ClientConnection} during replay. */
    public boolean captureInbound(Object listener, Object packet) {
        if (listener == null || packet == null) {
            return false;
        }
        pruneExpiredInboundRequests();
        int delay = effectiveInboundLatencyMs;
        if (delay <= 0) {
            return false;
        }
        MoveDelta move = moveDelta(packet);
        heldInbound.addLast(new InboundPacket(listener, packet, System.currentTimeMillis() + delay,
                move.entityId, move.x, move.y, move.z));
        heldInboundTotal.incrementAndGet();
        if (heldInbound.size() > MAX_HELD_INBOUND) {
            forceFlushInbound = true;
        }
        return true;
    }

    /** Called from the connection's one-argument send hook. */
    public boolean captureOutbound(Object connection, Object packet) {
        if (replayingOutbound.get() || connection == null || packet == null || !outboundHeld) {
            return false;
        }
        if (isProtocolTransition(packet)) {
            // A terminal packet changes the channel codec/state. Everything encoded under the old
            // state must leave first, and the transition itself must execute through vanilla.
            flushOutbound();
            return false;
        }
        heldOutbound.addLast(new OutboundPacket(connection, packet));
        heldOutboundTotal.incrementAndGet();
        return true;
    }

    public static boolean isProtocolTransition(Object packet) {
        return packet != null && readBoolean(packet,
                List.of("transitionsNetworkState", "method_55943", "d"));
    }

    /** Drains everything due for release. Call once per client tick. */
    public void onTick() {
        pruneExpiredInboundRequests();
        boolean releaseEverything = effectiveInboundLatencyMs <= 0 || forceFlushInbound;
        forceFlushInbound = false;
        long now = System.currentTimeMillis();
        InboundPacket held;
        while ((held = heldInbound.peekFirst()) != null && (releaseEverything || held.releaseAtMs <= now)) {
            heldInbound.pollFirst();
            replayInbound(held.listener, held.packet);
        }
        if (heldOutbound.size() > MAX_HELD_OUTBOUND) {
            flushOutbound();
        }
    }

    private void pruneExpiredInboundRequests() {
        if (inboundRequests.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (inboundRequests.values().removeIf(request -> request.expiresAtMs <= now)) {
            recomputeInboundLatency();
        }
    }

    private void recomputeInboundLatency() {
        int max = 0;
        for (Request request : inboundRequests.values()) {
            max = Math.max(max, request.latencyMs);
        }
        effectiveInboundLatencyMs = max;
    }

    private void replayInbound(Object listener, Object packet) {
        try {
            if (!(invokeCompatible(packet, PACKET_APPLY_METHOD_NAMES, listener)
                    || invokeSingleArgAccepting(packet, listener))) {
                logReplayFailureOnce("Could not replay a held inbound packet (" + packet.getClass().getName() + ")");
                return;
            }
            replayedInboundTotal.incrementAndGet();
        } catch (RuntimeException exception) {
            logReplayFailureOnce("Inbound packet replay failed: " + exception.getMessage());
        }
    }

    private void resend(Object connection, Object packet) {
        replayingOutbound.set(true);
        try {
            // Known send(...) names first, then match by shape (the single-argument method that
            // accepts this packet) so a renamed send survives across mappings.
            if (!(invokeCompatible(connection, OUTBOUND_SEND_METHOD_NAMES, packet)
                    || invokeSingleArgAccepting(connection, packet))) {
                logReplayFailureOnce("Could not resend a held outbound packet (" + packet.getClass().getName() + ")");
                return;
            }
            replayedOutboundTotal.incrementAndGet();
        } catch (RuntimeException exception) {
            logReplayFailureOnce("Outbound packet resend failed: " + exception.getMessage());
        } finally {
            replayingOutbound.remove();
        }
    }

    private void logReplayFailureOnce(String message) {
        lastError = message;
        replayFailures.incrementAndGet();
        if (replayFailureLogged.compareAndSet(false, true)) {
            diagnosticSink.accept(message);
        }
    }

    public void reset() {
        inboundRequests.clear();
        heldInbound.clear();
        heldOutbound.clear();
        effectiveInboundLatencyMs = 0;
        forceFlushInbound = false;
        outboundHeld = false;
        replayingOutbound.remove();
        replayFailureLogged.set(false);
        lastError = "";
    }

    private static Object invokeNoArgs(Object target, List<String> methodNames) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!methodNames.contains(method.getName()) || method.getParameterCount() != 0
                        || method.getReturnType() == void.class) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Object findField(Object target, List<String> fieldNames) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (fieldNames.contains(field.getName())) {
                    try {
                        field.setAccessible(true);
                        return field.get(target);
                    } catch (ReflectiveOperationException ignored) {
                        return null;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /** Finds a single-argument method on {@code target} whose argument type accepts {@code
     * argument}, walking the class hierarchy to reach package-private/protected declarations (e.g.
     * Netty's {@code channelRead0}-adjacent dispatch methods). */
    private static boolean invokeCompatible(Object target, List<String> methodNames, Object argument) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                if (!method.getParameterTypes()[0].isInstance(argument)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(target, argument);
                    return true;
                } catch (ReflectiveOperationException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    /** Name-agnostic fallback for {@link #invokeCompatible}: invokes the first {@code void}
     * single-argument method (walking the class hierarchy) whose parameter type accepts {@code
     * argument}. Used to replay a held packet when the obfuscated {@code apply}/{@code send} name is
     * not in our lookup list — those are the only single-arg sinks for a listener/packet respectively,
     * so matching by shape avoids depending on a single mapping version. */
    private static boolean invokeSingleArgAccepting(Object target, Object argument) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() != 1 || method.getReturnType() != void.class
                        || !method.getParameterTypes()[0].isInstance(argument)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(target, argument);
                    return true;
                } catch (ReflectiveOperationException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static MoveDelta moveDelta(Object packet) {
        if (packet == null || !hasTypeName(packet, ENTITY_MOVE_PACKET_CLASSES)) {
            return MoveDelta.NONE;
        }
        if (!readBoolean(packet, List.of("isPositionChanged", "method_22826"))) {
            return MoveDelta.NONE;
        }
        int entityId = readIntField(packet, List.of("id", "entityId", "field_12310"), -1);
        double x = readNumber(packet, List.of("getDeltaX", "method_36150"), 0.0D) / DELTA_PER_BLOCK;
        double y = readNumber(packet, List.of("getDeltaY", "method_36151"), 0.0D) / DELTA_PER_BLOCK;
        double z = readNumber(packet, List.of("getDeltaZ", "method_36152"), 0.0D) / DELTA_PER_BLOCK;
        return new MoveDelta(entityId, x, y, z);
    }

    private static boolean hasTypeName(Object value, Set<String> names) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (names.contains(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean readBoolean(Object target, List<String> names) {
        Object value = invokeNoArgs(target, names);
        return value instanceof Boolean bool && bool;
    }

    private static double readNumber(Object target, List<String> names, double fallback) {
        Object value = invokeNoArgs(target, names);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int readIntField(Object target, List<String> names, int fallback) {
        Object value = findField(target, names);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public record Metrics(int heldInbound, int heldOutbound, int latencyMs,
                          long heldInboundTotal, long replayedInboundTotal,
                          long heldOutboundTotal, long replayedOutboundTotal,
                          long replayFailures, String lastError) {
    }

    private record Request(int latencyMs, long expiresAtMs) {
    }

    private record InboundPacket(Object listener, Object packet, long releaseAtMs,
                                 int entityId, double deltaX, double deltaY, double deltaZ) {
    }

    private record OutboundPacket(Object connection, Object packet) {
    }

    private record MoveDelta(int entityId, double x, double y, double z) {
        private static final MoveDelta NONE = new MoveDelta(-1, 0.0D, 0.0D, 0.0D);
    }
}
