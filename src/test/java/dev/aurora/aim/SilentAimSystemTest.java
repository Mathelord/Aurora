package dev.aurora.aim;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentAimSystemTest {
    private final SilentAimSystem system = SilentAimSystem.get();

    @AfterEach
    void clearSystem() {
        system.clear();
    }

    @Test
    void appliesInstantRequestToRuntimeSink() {
        CapturingSink sink = new CapturingSink();

        AimAngles applied = system.apply(
                new SilentAimSystem.AimRuntime(true, new AimAngles(0.0F, 0.0F), 0.5D, sink),
                SilentAimRequest.builder("test", new AimAngles(40.0F, -12.0F))
                        .smoothingProfile(AimSmoothingProfile.instant())
                        .build()
        );

        assertEquals(40.0F, applied.yaw(), 0.001F);
        assertEquals(-12.0F, applied.pitch(), 0.001F);
        assertEquals(40.0F, sink.last.yaw(), 0.001F);
        assertEquals(-12.0F, system.lastSilentPitch(), 0.001F);
    }

    @Test
    void rejectsLowerPriorityDifferentOwner() {
        CapturingSink sink = new CapturingSink();
        SilentAimSystem.AimRuntime runtime = new SilentAimSystem.AimRuntime(true, new AimAngles(0.0F, 0.0F), 0.5D, sink);

        system.apply(runtime, SilentAimRequest.builder("high", new AimAngles(90.0F, 0.0F))
                .priority(10)
                .smoothingProfile(AimSmoothingProfile.instant())
                .build());

        AimAngles rejected = system.apply(runtime, SilentAimRequest.builder("low", new AimAngles(-90.0F, 0.0F))
                .priority(9)
                .smoothingProfile(AimSmoothingProfile.instant())
                .build());

        assertNull(rejected);
        assertEquals(90.0F, sink.last.yaw(), 0.001F);
    }

    @Test
    void smoothedProfileDoesNotSnapWhenAlreadyNearTarget() {
        CapturingSink sink = new CapturingSink();

        AimAngles applied = system.apply(
                new SilentAimSystem.AimRuntime(true, new AimAngles(0.0F, 0.0F), 0.5D, sink),
                SilentAimRequest.builder("test", new AimAngles(1.0F, 0.0F))
                        .smoothingProfile(new AimSmoothingProfile(
                                SmoothingMode.EaseOut, 0.58D, 64.0D, 48.0D, 0.0D, 0.0D))
                        .build()
        );

        assertTrue(applied.yaw() >= 0.0F);
        assertTrue(applied.yaw() < 0.5F);
    }

    @Test
    void decoupledRequestRestoresLatestVisualAnglesWhenCleared() {
        CapturingSink sink = new CapturingSink();

        system.apply(
                new SilentAimSystem.AimRuntime(true, new AimAngles(10.0F, 5.0F), 0.5D, sink),
                SilentAimRequest.builder("decoupled", new AimAngles(45.0F, -10.0F))
                        .smoothingProfile(AimSmoothingProfile.instant())
                        .decoupled(true)
                        .build()
        );
        DecoupledAimState.get().applyMouseDelta(20.0D, 0.0D);
        system.clearOwner("decoupled");

        assertEquals(13.0F, sink.last.yaw(), 0.001F);
        assertEquals(5.0F, sink.last.pitch(), 0.001F);
        assertFalse(DecoupledAimState.get().isActive());
    }

    private static final class CapturingSink implements SilentAimSystem.RotationSink {
        private AimAngles last;

        @Override
        public boolean apply(AimAngles angles) {
            last = angles;
            return true;
        }
    }
}
