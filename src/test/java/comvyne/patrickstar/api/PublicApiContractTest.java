package comvyne.patrickstar.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class PublicApiContractTest {
    @Test
    void patricExposesCreationPassiveOneShotAndCancellationMethods() {
        Set<String> serviceNames = List.of(Patrick.class.getDeclaredMethods()).stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("create", "register", "oneShot", "unregister"), serviceNames);
    }

    @Test
    void registryExposesPriorityLockAndDeferBuilders() {
        Set<String> builderNames = List.of(PatrickPathRegistry.class.getDeclaredMethods()).stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("priority", "lock", "deferOneShot"), builderNames);
    }

    @Test
    void waypointBuilderRetainsOrderAndOptionalReachability() {
        BlockPos destination = new BlockPos(30, 70, 30);
        Vec3 first = new Vec3(10.5, 70, 10.5);
        Vec3 second = new Vec3(20.5, 70, 20.5);
        PatrickPath path = PatrickPath.builder(destination)
                .waypoints(List.of(first, second))
                .speed(1.25)
                .build();
        assertEquals(List.of(first, second), path.waypoints());
        assertEquals(destination, path.destination());
        assertEquals(1.25, path.speed());
        assertTrue(path.reachableOverride().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> path.waypoints().add(new Vec3(0, 0, 0)));
        assertEquals(false, PatrickPath.builder(destination).reachable(false)
                .build().reachableOverride().orElseThrow());
        assertEquals(true, PatrickPath.builder(destination).reachable(true)
                .build().reachableOverride().orElseThrow());
    }

    @Test
    void invalidWaypointDataAndSpeedAreRejected() {
        PatrickPath.Builder builder = PatrickPath.builder(BlockPos.ZERO);
        assertThrows(NullPointerException.class, () -> builder.waypoint(null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.waypoint(new Vec3(Double.NaN, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> builder.waypoint(new Vec3(1, Double.POSITIVE_INFINITY, 0)));
        assertThrows(IllegalArgumentException.class, () -> builder.speed(0));
        assertThrows(IllegalArgumentException.class, () -> builder.speed(-5));
        assertThrows(IllegalArgumentException.class, () -> builder.speed(Double.NaN));
        assertThrows(NullPointerException.class, () -> PatrickPath.builder(null));
    }

    @Test
    void registryRejectsOutOfRangePriorityAndNegativeLockAndFreezesAfterRegistration() {
        PatrickPathRegistry registration = new PatrickPathRegistry(null, mob -> null);
        assertSame(registration, registration.priority(0).lock(0).deferOneShot(false));
        assertFalse(registration.deferOneShot());
        assertSame(registration, registration.deferOneShot(true));
        assertTrue(registration.deferOneShot());
        assertSame(registration, registration.priority(65535).lock(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> registration.priority(-1));
        assertThrows(IllegalArgumentException.class, () -> registration.priority(65536));
        assertThrows(IllegalArgumentException.class, () -> registration.lock(-1));
        registration.markRegistered();
        assertThrows(IllegalStateException.class, () -> registration.priority(1));
        assertThrows(IllegalStateException.class, () -> registration.lock(1));
        assertThrows(IllegalStateException.class, () -> registration.deferOneShot(false));
    }

    @Test
    void oneShotAndUnregisterKeepTheExactRegisterHandleSignature() throws NoSuchMethodException {
        assertEquals(boolean.class,
                Patrick.class.getMethod("register", PatrickPathRegistry.class).getReturnType());
        assertEquals(boolean.class,
                Patrick.class.getMethod("oneShot", PatrickPathRegistry.class).getReturnType());
        assertEquals(boolean.class,
                Patrick.class.getMethod("unregister", PatrickPathRegistry.class).getReturnType());
        assertFalse(Patrick.oneShot(null));
        assertFalse(Patrick.unregister(null));
        assertFalse(Patrick.unregister(new PatrickPathRegistry(null, mob -> null)));
    }

    @Test
    void waypointCountIsBoundedToPreventUnboundedWork() {
        PatrickPath.Builder builder = PatrickPath.builder(BlockPos.ZERO);
        for (int i = 0; i < 1024; i++) {
            builder.waypoint(new Vec3(i + 0.5, 64, 0.5));
        }
        assertEquals(1024, builder.build().waypoints().size());
        assertThrows(IllegalArgumentException.class,
                () -> builder.waypoint(new Vec3(1024.5, 64, 0.5)));
    }
}
