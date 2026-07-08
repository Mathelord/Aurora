package dev.aurora.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HookInstaller {
    private static final List<String> MOUSE_CLASSES = List.of(
            "net.minecraft.client.MouseHandler",
            "net.minecraft.client.Mouse",
            "net.minecraft.class_312",
            "fll"
    );
    private static final List<String> MOUSE_BUTTON_METHODS = List.of(
            "onPress",
            "onMouseButton",
            "method_1601",
            "m_91530_"
    );
    private static final List<String> MOUSE_SCROLL_METHODS = List.of(
            "onScroll",
            "onMouseScroll",
            "method_1598",
            "m_91526_"
    );
    private static final List<String> ENTITY_CLASSES = List.of(
            "net.minecraft.world.entity.Entity",
            "net.minecraft.entity.Entity",
            "net.minecraft.class_1297",
            "bum"
    );
    private static final List<String> LOOK_METHODS = List.of(
            "turn",
            "changeLookDirection",
            "method_5872",
            "m_5616_"
    );
    private static final List<String> CAMERA_CLASSES = List.of(
            "net.minecraft.client.Camera",
            "net.minecraft.client.render.Camera",
            "net.minecraft.class_4184",
            "fks"
    );
    private static final List<String> CAMERA_UPDATE_METHODS = List.of(
            "setup",
            "update",
            "method_19321",
            "m_90575_"
    );
    private static final List<String> KEYBOARD_INPUT_CLASSES = List.of(
            "net.minecraft.client.player.KeyboardInput",
            "net.minecraft.client.input.KeyboardInput",
            "net.minecraft.class_743",
            "gkw"
    );
    private static final List<String> WORLD_RENDER_CLASSES = List.of(
            "net.minecraft.client.render.WorldRenderer",
            "net.minecraft.class_761",
            "glv"
    );
    private static final List<String> WORLD_RENDER_METHODS = List.of("renderEntities", "method_62207", "a");
    private static final List<String> INPUT_TICK_METHODS = List.of(
            "tick",
            "method_3129",
            "m_108578_",
            "a"
    );
    // Minecraft 1.21.4 official mappings use flk#bo for startAttack. Keep this list in sync with
    // HookProfile's supported namespaces; missing the official name silently skips the advice.
    private static final List<String> ATTACK_METHODS = List.of("doAttack", "startAttack", "method_1536", "bo");
    private static final List<String> BLOCK_ATTACK_METHODS = List.of(
            "handleBlockBreaking", "continueAttack", "method_1581", "method_1590"
    );
    private static final List<String> PACKET_INTERFACE_TYPES = List.of(
            "net.minecraft.network.packet.Packet", "net.minecraft.class_2596", "yw", "aay"
    );
    private static final List<String> PACKET_LISTENER_TYPES = List.of(
            "net.minecraft.network.listener.PacketListener", "net.minecraft.class_2547", "vv", "xk"
    );
    private final HookProfile profile;
    private final Consumer<String> diagnosticSink;
    private final AtomicBoolean installed = new AtomicBoolean();
    private Instrumentation instrumentation;
    private ResettableClassFileTransformer byteBuddyTransformer;

    public HookInstaller() {
        this(HookProfile.minecraft1214(), message -> System.err.println("[Aurora] " + message));
    }

    public HookInstaller(Consumer<String> diagnosticSink) {
        this(HookProfile.minecraft1214(), diagnosticSink);
    }

    public HookInstaller(HookProfile profile, Consumer<String> diagnosticSink) {
        this.profile = profile == null ? HookProfile.minecraft1214() : profile;
        this.diagnosticSink = diagnosticSink == null ? ignored -> { } : diagnosticSink;
    }

    public void install(Instrumentation instrumentation) {
        if (!installed.compareAndSet(false, true)) {
            throw new IllegalStateException("hooks already installed");
        }
        this.instrumentation = instrumentation;
        byteBuddyTransformer = installByteBuddyHooks(instrumentation);
        retransformLoadedHookClasses(instrumentation);
    }

    public void uninstall() {
        if (!installed.compareAndSet(true, false)) {
            return;
        }
        if (byteBuddyTransformer != null) {
            byteBuddyTransformer.reset(instrumentation, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        }
        byteBuddyTransformer = null;
        instrumentation = null;
    }

    boolean supportsWorldRendering() {
        return profile.hasWorldRenderHook();
    }

    private ResettableClassFileTransformer installByteBuddyHooks(Instrumentation instrumentation) {
        AgentBuilder agentBuilder = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onError(String typeName, ClassLoader classLoader, JavaModule module,
                                        boolean loaded, Throwable throwable) {
                        diagnosticSink.accept("Hook transformation failed for " + typeName + ": "
                                + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                    }
                });

        return agentBuilder
                .type(namedTypeAny(profile.tickClasses()))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder
                                .visit(Advice.to(TickAdvice.class).on(namedMethodAny(profile.tickMethods())))
                                .visit(Advice.to(AttackSuppressionAdvice.class).on(
                                        namedMethodAny(profile.attackMethods()).and(ElementMatchers.takesArguments(0))))
                                .visit(Advice.to(BlockAttackSuppressionAdvice.class).on(
                                        namedMethodAny(profile.blockAttackMethods()).and(ElementMatchers.takesArguments(1))));
                    }
                })
                .type(namedTypeAny(profile.renderClasses()))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder.visit(Advice.to(RenderAdvice.class).on(
                                namedMethodAny(profile.renderMethods()).and(ElementMatchers.takesArguments(2))));
                    }
                })
                .type(namedTypeAny(profile.worldRenderClasses()))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder.visit(Advice.to(WorldRenderAdvice.class).on(worldRenderMethod()));
                    }
                })
                .type(namedTypeAny(profile.mouseClasses()))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder
                                .visit(Advice.to(MouseButtonAdvice.class).on(
                                        namedMethodAny(profile.mouseButtonMethods()).and(
                                                ElementMatchers.takesArguments(profile.mouseButtonArgumentCount()))))
                                .visit(Advice.to(MouseScrollAdvice.class).on(
                                        namedMethodAny(profile.mouseScrollMethods()).and(ElementMatchers.takesArguments(3))));
                    }
                })
                .type(namedTypeAny(profile.entityClasses()))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder.visit(Advice.to(LookAdvice.class).on(
                                namedMethodAny(profile.lookMethods()).and(ElementMatchers.takesArguments(2))));
                    }
                })
                .type(namedTypeAny(profile.cameraClasses()))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder.visit(Advice.to(CameraAdvice.class).on(
                                namedMethodAny(profile.cameraUpdateMethods()).and(ElementMatchers.takesArguments(5))));
                    }
                })
                .type(namedTypeAny(KEYBOARD_INPUT_CLASSES))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder.visit(Advice.to(MovementInputAdvice.class).on(namedMethodAny(INPUT_TICK_METHODS)));
                    }
                })
                .type(namedTypeAny(profile.packetClasses()))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder
                                .visit(Advice.to(OutboundPacketAdvice.class).on(outboundPacketMethod()))
                                .visit(Advice.to(InboundPacketAdvice.class).on(inboundPacketMethod()));
                    }
                })
                .installOn(instrumentation);
    }

    private void retransformLoadedHookClasses(Instrumentation instrumentation) {
        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            if (!instrumentation.isModifiableClass(loadedClass)) {
                continue;
            }
            if (isHookClass(loadedClass.getName())) {
                try {
                    instrumentation.retransformClasses(loadedClass);
                } catch (Exception exception) {
                    System.err.println("[Aurora] Could not retransform hook class " + loadedClass.getName() + ": " + exception.getMessage());
                }
            }
        }
    }

    private boolean isHookClass(String className) {
        return profile.tickClasses().contains(className)
                || profile.renderClasses().contains(className)
                || profile.packetClasses().contains(className)
                || profile.mouseClasses().contains(className)
                || profile.entityClasses().contains(className)
                || profile.cameraClasses().contains(className)
                || KEYBOARD_INPUT_CLASSES.contains(className)
                || profile.worldRenderClasses().contains(className);
    }

    private static ElementMatcher.Junction<? super TypeDescription> namedTypeAny(List<String> names) {
        ElementMatcher.Junction<? super TypeDescription> matcher = ElementMatchers.none();
        for (String name : names) {
            matcher = matcher.or(ElementMatchers.<TypeDescription>named(name));
        }
        return matcher;
    }

    private static ElementMatcher.Junction<? super MethodDescription> namedMethodAny(List<String> names) {
        ElementMatcher.Junction<? super MethodDescription> matcher = ElementMatchers.none();
        for (String name : names) {
            matcher = matcher.or(ElementMatchers.<MethodDescription>named(name));
        }
        return matcher;
    }

    ElementMatcher.Junction<? super MethodDescription> worldRenderMethod() {
        // Official jars overload the obfuscated name "a" heavily. The argument count separates
        // renderEntities (1.21.4-1.21.8) from renderBlockDestroyAnimation (1.21.9-1.21.11).
        return namedMethodAny(profile.worldRenderMethods())
                .and(ElementMatchers.takesArguments(profile.worldRenderArgumentCount()));
    }

    ElementMatcher.Junction<? super MethodDescription> outboundPacketMethod() {
        return namedMethodAny(profile.outboundPacketMethods())
                .and(ElementMatchers.takesArguments(1))
                .and(ElementMatchers.takesArgument(0, namedTypeAny(PACKET_INTERFACE_TYPES)));
    }

    ElementMatcher.Junction<? super MethodDescription> inboundPacketMethod() {
        // Hook the final static dispatch boundary, after ClientConnection selected and validated the
        // listener but before Packet#apply(listener). Exact parameter types disambiguate the heavily
        // overloaded official name `a`. This boundary is handlePacket in Yarn and method_10759 in
        // Fabric intermediary for both supported versions.
        return namedMethodAny(profile.inboundPacketMethods())
                .and(ElementMatchers.takesArguments(2))
                .and(ElementMatchers.isStatic())
                .and(ElementMatchers.takesArgument(0, namedTypeAny(PACKET_INTERFACE_TYPES)))
                .and(ElementMatchers.takesArgument(1, namedTypeAny(PACKET_LISTENER_TYPES)));
    }

    public static final class TickAdvice {
        // Run at tick entry, before the player ticks and flushes its movement (flying) packet.
        // On method exit the attack/use packet would land after the movement packet within the
        // same tick, which Grim flags as "Post". Firing on enter keeps interaction packets ahead
        // of movement, matching vanilla ordering.
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter() {
            Object callback = System.getProperties().get("dev.aurora.hook.tick");
            if (callback instanceof Runnable runnable) {
                runnable.run();
            }
        }
    }

    public static final class RenderAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.AllArguments Object[] arguments) {
            Object callback = System.getProperties().get("dev.aurora.hook.render");
            if (callback instanceof Consumer<?> consumer) {
                @SuppressWarnings("unchecked")
                Consumer<Object[]> renderCallback = (Consumer<Object[]>) consumer;
                renderCallback.accept(arguments);
            }
        }
    }

    public static final class AttackSuppressionAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter() {
            Object callback = System.getProperties().get(HookDispatch.ATTACK_SUPPRESSION_CALLBACK_KEY);
            return callback instanceof BooleanSupplier supplier && supplier.getAsBoolean();
        }
    }

    public static final class BlockAttackSuppressionAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter() {
            Object callback = System.getProperties().get(HookDispatch.BLOCK_ATTACK_SUPPRESSION_CALLBACK_KEY);
            return callback instanceof BooleanSupplier supplier && supplier.getAsBoolean();
        }
    }

    public static final class WorldRenderAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.AllArguments Object[] arguments) {
            if (arguments == null || arguments.length < 2) {
                return;
            }
            Object callback = System.getProperties().get(HookDispatch.WORLD_RENDER_CALLBACK_KEY);
            if (callback instanceof Consumer<?> consumer) {
                @SuppressWarnings("unchecked")
                Consumer<Object[]> renderCallback = (Consumer<Object[]>) consumer;
                renderCallback.accept(arguments);
            }
        }
    }

    public static final class MouseButtonAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter() {
            return Boolean.TRUE.equals(System.getProperties().get("dev.aurora.gui.open"));
        }
    }

    public static final class MouseScrollAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(2) double vertical) {
            Object callback = System.getProperties().get("dev.aurora.hook.scroll");
            if (callback instanceof Consumer<?> consumer) {
                @SuppressWarnings("unchecked")
                Consumer<Double> scrollCallback = (Consumer<Double>) consumer;
                scrollCallback.accept(vertical);
            }
        }
    }

    public static final class LookAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter(@Advice.This Object entity,
                                      @Advice.Argument(0) double cursorDeltaX,
                                      @Advice.Argument(1) double cursorDeltaY) {
            Object callback = System.getProperties().get("dev.aurora.hook.look");
            if (callback instanceof Predicate<?> predicate) {
                @SuppressWarnings("unchecked")
                Predicate<Object[]> lookCallback = (Predicate<Object[]>) predicate;
                return lookCallback.test(new Object[]{entity, cursorDeltaX, cursorDeltaY});
            }
            return false;
        }
    }

    public static final class CameraAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static boolean onEnter(@Advice.Argument(1) Object focusedEntity) {
            Object callback = System.getProperties().get("dev.aurora.hook.camera.begin");
            if (callback instanceof Predicate<?> predicate) {
                @SuppressWarnings("unchecked")
                Predicate<Object> cameraBegin = (Predicate<Object>) predicate;
                return cameraBegin.test(focusedEntity);
            }
            return false;
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object camera,
                                  @Advice.Argument(1) Object focusedEntity,
                                  @Advice.Argument(4) float tickDelta,
                                  @Advice.Enter boolean altered) {
            if (!altered) {
                return;
            }
            Object callback = System.getProperties().get("dev.aurora.hook.camera.end");
            if (callback instanceof Consumer<?> consumer) {
                @SuppressWarnings("unchecked")
                Consumer<Object[]> cameraEnd = (Consumer<Object[]>) consumer;
                cameraEnd.accept(new Object[]{camera, focusedEntity, tickDelta});
            }
        }
    }

    public static final class MovementInputAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object input) {
            Object callback = System.getProperties().get("dev.aurora.hook.movement");
            if (callback instanceof Consumer<?> consumer) {
                @SuppressWarnings("unchecked")
                Consumer<Object> movementCallback = (Consumer<Object>) consumer;
                movementCallback.accept(input);
            }
        }
    }

    /**
     * Outbound packet hook (the connection's {@code send}-family methods). Returning {@code true}
     * skips the original method body entirely, meaning the packet is held back by {@link
     * dev.aurora.network.PacketRelay} instead of actually reaching the network.
     */
    public static final class OutboundPacketAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter(@Advice.This Object connection, @Advice.Argument(0) Object packet) {
            Object callback = System.getProperties().get("dev.aurora.hook.packet.outbound");
            if (!(callback instanceof BiPredicate<?, ?> predicate)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            BiPredicate<Object, Object> typed = (BiPredicate<Object, Object>) predicate;
            return typed.test(connection, packet);
        }
    }

    /** Inbound hook at {@code handlePacket(Packet, PacketListener)}. */
    public static final class InboundPacketAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter(@Advice.Argument(0) Object packet,
                                      @Advice.Argument(1) Object listener) {
            Object callback = System.getProperties().get("dev.aurora.hook.packet.inbound");
            if (!(callback instanceof BiPredicate<?, ?> predicate)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            BiPredicate<Object, Object> typed = (BiPredicate<Object, Object>) predicate;
            return typed.test(listener, packet);
        }
    }

}
