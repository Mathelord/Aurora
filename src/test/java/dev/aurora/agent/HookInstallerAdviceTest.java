package dev.aurora.agent;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import dev.aurora.input.GameplayInputGate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookInstallerAdviceTest {
    private final HookInstaller installer = new HookInstaller(ignored -> { });

    @AfterEach
    void removeCallbacks() {
        GameplayInputGate.clear();
        System.getProperties().remove(HookDispatch.TICK_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.RENDER_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.WORLD_RENDER_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.CLICK_GUI_OPEN_KEY);
        System.getProperties().remove(HookDispatch.OUTBOUND_PACKET_CALLBACK_KEY);
        System.getProperties().remove(HookDispatch.INBOUND_PACKET_CALLBACK_KEY);
    }

    @Test
    void inputAdvicesSuppressKeyboardMouseAndScrollWhileTheGateIsHeld() {
        assertTrue(GameplayInputGate.acquire("test"));

        assertTrue(HookInstaller.MouseButtonAdvice.onEnter());
        assertTrue(HookInstaller.MouseScrollAdvice.onEnter(1.0D));
        assertTrue(HookInstaller.KeyboardAdvice.onEnter());
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
    void packetMatchersSelectStaticIntermediaryDispatchAndExcludeWrongShapes() throws Exception {
        MethodDescription intermediaryDispatch = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_10759",
                        net.minecraft.class_2596.class, net.minecraft.class_2547.class));
        MethodDescription instanceDispatch = new MethodDescription.ForLoadedMethod(
                InstancePacketMethods.class.getDeclaredMethod("method_10759",
                        net.minecraft.class_2596.class, net.minecraft.class_2547.class));
        MethodDescription wrongTypes = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_10759", FakePacket.class, Object.class));
        MethodDescription singleSend = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_10743", net.minecraft.class_2596.class));
        MethodDescription overloadedSend = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("send", FakePacket.class, Object.class));
        MethodDescription nonPacketOfficialOverload = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("a", java.util.function.Consumer.class));

        assertTrue(installer.inboundPacketMethod().matches(intermediaryDispatch));
        assertFalse(installer.inboundPacketMethod().matches(instanceDispatch));
        assertFalse(installer.inboundPacketMethod().matches(wrongTypes));
        assertTrue(installer.outboundPacketMethod().matches(singleSend));
        assertFalse(installer.outboundPacketMethod().matches(overloadedSend));
        assertFalse(installer.outboundPacketMethod().matches(nonPacketOfficialOverload));
    }

    @Test
    void inlinedInboundAdviceCanSuppressARealStaticDispatch() throws Exception {
        AtomicReference<Object[]> received = new AtomicReference<>();
        System.getProperties().put(HookDispatch.INBOUND_PACKET_CALLBACK_KEY,
                (BiPredicate<Object, Object>) (listener, packet) -> {
                    received.set(new Object[]{listener, packet});
                    return true;
                });
        Class<?> instrumented = new ByteBuddy()
                .redefine(IntermediaryConnectionFixture.class)
                .visit(Advice.to(HookInstaller.InboundPacketAdvice.class).on(installer.inboundPacketMethod()))
                .make()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
                .getLoaded();
        Object packet = new IntermediaryPacketFixture();
        Object listener = new IntermediaryListenerFixture();
        var dispatch = instrumented.getDeclaredMethod("method_10759",
                net.minecraft.class_2596.class, net.minecraft.class_2547.class);
        dispatch.setAccessible(true);

        dispatch.invoke(null, packet, listener);

        assertArrayEquals(new Object[]{listener, packet}, received.get());
        var calls = instrumented.getDeclaredMethod("calls");
        calls.setAccessible(true);
        assertEquals(0, calls.invoke(null));
    }

    @Test
    void worldRenderMatcherRequiresTheFiveArgumentRenderEntitiesSignature() throws Exception {
        MethodDescription correct = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_62207", Object.class, Object.class,
                        Object.class, Object.class, java.util.List.class));
        MethodDescription wrong = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_62207", Object.class));

        assertTrue(installer.worldRenderMethod().matches(correct));
        assertFalse(installer.worldRenderMethod().matches(wrong));
    }

    @Test
    void modernWorldRenderMatcherUsesThreeArgumentBlockDamagePass() throws Exception {
        HookInstaller modern = new HookInstaller(HookProfile.forVersion("1.21.11"), ignored -> { });
        MethodDescription correct = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_62206", Object.class, Object.class, Object.class));
        MethodDescription wrong = new MethodDescription.ForLoadedMethod(
                PacketMethods.class.getDeclaredMethod("method_62207", Object.class, Object.class,
                        Object.class, Object.class, java.util.List.class));

        assertTrue(modern.worldRenderMethod().matches(correct));
        assertFalse(modern.worldRenderMethod().matches(wrong));
    }

    @Test
    void mouseButtonAdviceSkipsMinecraftInputOnlyWhileGuiIsOpen() {
        assertEquals(false, HookInstaller.MouseButtonAdvice.onEnter());

        System.getProperties().put(HookDispatch.CLICK_GUI_OPEN_KEY, Boolean.TRUE);

        assertEquals(true, HookInstaller.MouseButtonAdvice.onEnter());
    }

    private static final class FakePacket implements net.minecraft.class_2596 {
    }

    @SuppressWarnings("unused")
    private static final class PacketMethods {
        private static void method_10759(net.minecraft.class_2596 packet, net.minecraft.class_2547 listener) { }
        private static void method_10759(FakePacket packet, Object listener) { }
        private void method_10743(net.minecraft.class_2596 packet) { }
        private void send(FakePacket packet, Object callbacks) { }
        private void a(java.util.function.Consumer<?> task) { }
        private void method_62207(Object matrices, Object providers, Object camera,
                                  Object tickCounter, java.util.List<?> entities) { }
        private void method_62207(Object ignored) { }
        private void method_62206(Object matrices, Object providers, Object renderState) { }
    }

    @SuppressWarnings("unused")
    private static final class InstancePacketMethods {
        private void method_10759(net.minecraft.class_2596 packet, net.minecraft.class_2547 listener) { }
    }

    @SuppressWarnings("unused")
    private static final class IntermediaryConnectionFixture {
        private static int calls;

        private static void method_10759(net.minecraft.class_2596 packet, net.minecraft.class_2547 listener) {
            calls++;
        }

        private static int calls() {
            return calls;
        }
    }

    private static final class IntermediaryPacketFixture implements net.minecraft.class_2596 {
    }

    private static final class IntermediaryListenerFixture implements net.minecraft.class_2547 {
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

        assertTrue(HookInstaller.OutboundPacketAdvice.onEnter(connection, packet));
        assertArrayEquals(new Object[]{connection, packet}, received.get());
    }

    @Test
    void outboundPacketAdviceDoesNotSuppressWithoutACallback() {
        assertFalse(HookInstaller.OutboundPacketAdvice.onEnter(new Object(), "packet"));
    }

    @Test
    void inboundPacketAdviceForwardsPacketAndSelectedListener() {
        AtomicReference<Object[]> received = new AtomicReference<>();
        System.getProperties().put(HookDispatch.INBOUND_PACKET_CALLBACK_KEY,
                (BiPredicate<Object, Object>) (listener, packet) -> {
                    received.set(new Object[]{listener, packet});
                    return false;
                });
        Object listener = new Object();
        Object packet = "packet";

        assertFalse(HookInstaller.InboundPacketAdvice.onEnter(packet, listener));
        assertArrayEquals(new Object[]{listener, packet}, received.get());
    }
}
