package dev.aurora.agent;

import dev.aurora.api.events.PacketEvent;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import dev.aurora.input.ActivationClickSuppressor;

public final class HookDispatch {
    static final String TICK_CALLBACK_KEY = "dev.aurora.hook.tick";
    static final String RENDER_CALLBACK_KEY = "dev.aurora.hook.render";
    static final String WORLD_RENDER_CALLBACK_KEY = "dev.aurora.hook.render.world";
    static final String INBOUND_PACKET_CALLBACK_KEY = "dev.aurora.hook.packet.inbound";
    static final String OUTBOUND_PACKET_CALLBACK_KEY = "dev.aurora.hook.packet.outbound";
    static final String CLICK_GUI_OPEN_KEY = "dev.aurora.gui.open";
    static final String LOOK_CALLBACK_KEY = "dev.aurora.hook.look";
    static final String CAMERA_BEGIN_CALLBACK_KEY = "dev.aurora.hook.camera.begin";
    static final String CAMERA_END_CALLBACK_KEY = "dev.aurora.hook.camera.end";
    static final String SCROLL_CALLBACK_KEY = "dev.aurora.hook.scroll";
    static final String MOVEMENT_CALLBACK_KEY = "dev.aurora.hook.movement";
    static final String ATTACK_SUPPRESSION_CALLBACK_KEY = "dev.aurora.hook.attack.suppress";
    static final String BLOCK_ATTACK_SUPPRESSION_CALLBACK_KEY = "dev.aurora.hook.block-attack.suppress";

    private HookDispatch() {
    }

    static void bind(RuntimeController controller) {
        System.getProperties().put(TICK_CALLBACK_KEY, (Runnable) controller::onTick);
        System.getProperties().put(RENDER_CALLBACK_KEY, (Consumer<Object[]>) arguments -> {
            Object context = arguments == null || arguments.length == 0 ? null : arguments[0];
            controller.onRender(context);
        });
        System.getProperties().put(WORLD_RENDER_CALLBACK_KEY, (Consumer<Object[]>) arguments ->
                controller.onWorldRender(arguments[0], arguments[1]));
        // Packet callbacks return true to suppress the original send/receive call (the packet is
        // being held by PacketRelay for later replay); false lets it proceed normally.
        System.getProperties().put(INBOUND_PACKET_CALLBACK_KEY, (BiPredicate<Object, Object>) (connection, packet) ->
                controller.onPacket(PacketEvent.Direction.INBOUND, connection, packet));
        System.getProperties().put(OUTBOUND_PACKET_CALLBACK_KEY, (BiPredicate<Object, Object>) (connection, packet) ->
                controller.onPacket(PacketEvent.Direction.OUTBOUND, connection, packet));
        System.getProperties().put(LOOK_CALLBACK_KEY, (Predicate<Object[]>) arguments -> controller.onLook(
                arguments[0], ((Number) arguments[1]).doubleValue(), ((Number) arguments[2]).doubleValue()));
        System.getProperties().put(CAMERA_BEGIN_CALLBACK_KEY, (Predicate<Object>) controller::onCameraBegin);
        System.getProperties().put(CAMERA_END_CALLBACK_KEY, (Consumer<Object[]>) arguments ->
                controller.onCameraEnd(arguments[0], arguments[1]));
        System.getProperties().put(SCROLL_CALLBACK_KEY, (Consumer<Double>) controller::onMouseScroll);
        System.getProperties().put(MOVEMENT_CALLBACK_KEY, (Consumer<Object>) controller::onMovementInput);
        System.getProperties().put(ATTACK_SUPPRESSION_CALLBACK_KEY,
                (BooleanSupplier) controller::onAttackAttempt);
        System.getProperties().put(BLOCK_ATTACK_SUPPRESSION_CALLBACK_KEY,
                (BooleanSupplier) ActivationClickSuppressor::shouldSuppressAttack);
    }

    static void unbind() {
        System.getProperties().remove(TICK_CALLBACK_KEY);
        System.getProperties().remove(RENDER_CALLBACK_KEY);
        System.getProperties().remove(WORLD_RENDER_CALLBACK_KEY);
        System.getProperties().remove(INBOUND_PACKET_CALLBACK_KEY);
        System.getProperties().remove(OUTBOUND_PACKET_CALLBACK_KEY);
        System.getProperties().remove(CLICK_GUI_OPEN_KEY);
        System.getProperties().remove(LOOK_CALLBACK_KEY);
        System.getProperties().remove(CAMERA_BEGIN_CALLBACK_KEY);
        System.getProperties().remove(CAMERA_END_CALLBACK_KEY);
        System.getProperties().remove(SCROLL_CALLBACK_KEY);
        System.getProperties().remove(MOVEMENT_CALLBACK_KEY);
        System.getProperties().remove(ATTACK_SUPPRESSION_CALLBACK_KEY);
        System.getProperties().remove(BLOCK_ATTACK_SUPPRESSION_CALLBACK_KEY);
    }
}
