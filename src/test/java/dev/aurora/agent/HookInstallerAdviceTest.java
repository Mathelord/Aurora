package dev.aurora.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.bytebuddy.description.method.MethodDescription;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookInstallerAdviceTest {
    @AfterEach
    void removeCallbacks() {
        System.getProperties().remove(HookDispatch.TICK_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.RENDER_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.WORLD_RENDER_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.CLICK_GUI_OPEN_KEY);
        System.getProperties().remove(HookDispatch.OUTBOUND_PACKET_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.INBOUND_PACKET_CALLBACK_KEY);
    }

    @Test
    void tickAdviceUsesBootstrapVisibleCallback() {
        AtomicInteger calls = new AtomicInteger();
        System.getProperties().put(HookDispatch.TICK_CALLBACK_KEY, (Runnable) calls::incrementAndGet);

        HookInstaller.TickAdvice.onEnter();

        assertEquals(1, calls.get());
    }

    @Test
    void renderAdviceForwardsArgumentsThroughBootstrapVisibleCallback() {
        AtomicReference<Object[]> received = new AtomicReference<>();
        System.getProperties().put(HookDispatch.RENDER_CALLBACK_KEY, (Consumer<Object[]>) received::set);
        Object[] arguments = {"draw context", 0.5D};

        HookInstaller.RenderAdvice.onExit(arguments);

        assertArrayEquals(arguments, received.get());
    }

    @Test
    void worldRenderAdviceForwardsMatrixAndVertexProviderArguments() {
        AtomicReference<Object[]> received = new AtomicReference<>();
        System.getProperties().put(HookDispatch.WORLD_RENDER_CALLBACK_KEY, (Consumer<Object[]>) received::set);
        Object[] arguments = {"matrices", "vertex consumers", "camera"};

        HookInstaller.WorldRenderAdvice.onExit(arguments);

        assertArrayEquals(arguments, received.get());
    }

    @Test
    void packetMatchersExcludeTheInboundCompilerBridgeAndOutboundOverloads() throws Exception {
        MethodDescription typedInbound = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("channelRead0", Object.class, FakePacket.class));
        MethodDescription bridgeInbound = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("channelRead0", Object.class, Object.class));
        MethodDescription staticHandlePacket = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_10770", FakePacket.class, Object.class));
        MethodDescription singleSend = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_10743", FakePacket.class));
        MethodDescription overloadedSend = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("send", FakePacket.class, Object.class));

        assertTrue(HookInstaller.inboundPacketMethod().matches(typedInbound));
        assertFalse(HookInstaller.inboundPacketMethod().matches(bridgeInbound));
        // method_10770 is the static handlePacket(Packet, PacketListener); it must not be hooked.
        assertFalse(HookInstaller.inboundPacketMethod().matches(staticHandlePacket));
        assertTrue(HookInstaller.outboundPacketMethod().matches(singleSend));
        assertFalse(HookInstaller.outboundPacketMethod().matches(overloadedSend));
    }

    @Test
    void worldRenderMatcherRequiresTheFiveArgumentRenderEntitiesSignature() throws Exception {
        MethodDescription correct = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_62207", Object.class, Object.class,
                        Object.class, Object.class, java.util.List.class));
        MethodDescription wrong = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_62207", Object.class));

        assertTrue(HookInstaller.worldRenderMethod().matches(correct));
        assertFalse(HookInstaller.worldRenderMethod().matches(wrong));
    }

    @Test
    void mouseButtonAdviceSkipsMinecraftInputOnlyWhileGuiIsOpen() {
        assertEquals(false, HookInstaller.MouseButtonAdvice.onEnter());

        System.getProperties().put(HookDispatch.CLICK_GUI_OPEN_KEY, Boolean.TRUE);

        assertEquals(true, HookInstaller.MouseButtonAdvice.onEnter());
    }

    private static final class FakePacket {
    }

    @SuppressWarnings("unused")
    private static final class PacketMethods {
        private void channelRead0(Object context, FakePacket packet) { }
        private void channelRead0(Object context, Object packet) { }
        private static void method_10770(FakePacket packet, Object listener) { }
        private void method_10743(FakePacket packet) { }
        private void send(FakePacket packet, Object callbacks) { }
        private void method_62207(Object matrices, Object providers, Object camera,
                                  Object tickCounter, java.util.List<?> entities) { }
        private void method_62207(Object ignored) { }
    }

    @Test
    void outboundPacketAdviceForwardsConnectionAndFirstArgumentAndCanSuppress() {
        AtomicReference<Object[]> received = new AtomicReference<>();
        System.getProperties().put(HookDispatch.OUTBOUND_PACKET_CALLBACK_KEY,
                (BiPredicate<Object, Object>) (connection, packet) -> {
                    received.set(new Object[]{connection, packet});
                    return true;
                });
        Object connection = new Object();
        Object packet = "packet";

        assertTrue(HookInstaller.OutboundPacketAdvice.onEnter(connection, new Object[]{packet, "extra"}));
        assertArrayEquals(new Object[]{connection, packet}, received.get());
    }

    @Test
    void outboundPacketAdviceDoesNotSuppressWithoutACallback() {
        assertFalse(HookInstaller.OutboundPacketAdvice.onEnter(new Object(), new Object[]{"packet"}));
    }

    @Test
    void inboundPacketAdviceForwardsConnectionAndLastArgument() {
        AtomicReference<Object[]> received = new AtomicReference<>();
        System.getProperties().put(HookDispatch.INBOUND_PACKET_CALLBACK_KEY,
                (BiPredicate<Object, Object>) (connection, packet) -> {
                    received.set(new Object[]{connection, packet});
                    return false;
                });
        Object connection = new Object();
        Object packet = "packet";

        assertFalse(HookInstaller.InboundPacketAdvice.onEnter(connection, new Object[]{"context", packet}));
        assertArrayEquals(new Object[]{connection, packet}, received.get());
    }
}
