package comvyne.patrickstar.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-time coverage for the public navigation overloads, without starting a game server.
 * A real NavigationSession is provided by the API only while its callback executes.
 */
final class NavigationOverloadTest {
    @Test
    void createOverloadsArePublicStaticAndReturnRegistrationHandles() throws NoSuchMethodException {
        assertCreateOverload(BlockPos.class);
        assertCreateOverload(Vec3.class);
        assertCreateOverload(Path.class);
        assertCreateOverload(Supplier.class);
        assertCreateOverload(BiConsumer.class);
        assertCreateOverload(Function.class);
    }

    private static void assertCreateOverload(Class<?> secondArgument) throws NoSuchMethodException {
        Method create = Patrick.class.getMethod("create", Mob.class, secondArgument);
        assertTrue(Modifier.isPublic(create.getModifiers()));
        assertTrue(Modifier.isStatic(create.getModifiers()));
        assertEquals(PatrickPathRegistry.class, create.getReturnType());
    }

    @Test
    void typedMethodReferencesSelectAllSixOverloads() {
        // Method references must resolve without casts at their invocation sites.
        BiFunction<Mob, BlockPos, PatrickPathRegistry> block = Patrick::create;
        BiFunction<Mob, Vec3, PatrickPathRegistry> vector = Patrick::create;
        BiFunction<Mob, Path, PatrickPathRegistry> nativePath = Patrick::create;
        BiFunction<Mob, Supplier<Path>, PatrickPathRegistry> suppliedPath = Patrick::create;
        BiFunction<Mob, BiConsumer<Mob, NavigationSession>, PatrickPathRegistry> session = Patrick::create;
        BiFunction<Mob, Function<Mob, PatrickPath>, PatrickPathRegistry> legacy = Patrick::create;

        assertNotNull(block);
        assertNotNull(vector);
        assertNotNull(nativePath);
        assertNotNull(suppliedPath);
        assertNotNull(session);
        assertNotNull(legacy);
    }

    @Test
    void lambdaArityAndSessionMoveToTypesCompileWithoutAmbiguity() {
        // The null entity is intentionally rejected before any callback can run.
        // This tests Java overload resolution without mocking Minecraft game objects.
        Mob missing = null;
        assertThrows(NullPointerException.class, () -> Patrick.create(missing, BlockPos.ZERO));
        assertThrows(NullPointerException.class,
                () -> Patrick.create(missing, new Vec3(0.5, 64, 0.5)));
        assertThrows(NullPointerException.class, () -> Patrick.create(missing, (Path) null));
        assertThrows(NullPointerException.class,
                () -> Patrick.create(missing, () -> (Path) null));
        assertThrows(NullPointerException.class,
                () -> Patrick.create(missing, mob -> PatrickPath.builder(BlockPos.ZERO).build()));
        assertThrows(NullPointerException.class,
                () -> Patrick.create(missing, (mob, session) -> {
                    session.follow((Path) null, 1.0);
                    session.moveTo(BlockPos.ZERO, 1.0);
                    session.moveTo(new Vec3(0.5, 64, 0.5), 1.0);
                }));
    }

    @Test
    void navigationSessionExposesThreePublicMovementMethods() throws NoSuchMethodException {
        assertPublicSessionMethod("follow", Path.class, double.class);
        assertPublicSessionMethod("moveTo", BlockPos.class, double.class);
        assertPublicSessionMethod("moveTo", Vec3.class, double.class);
    }

    private static void assertPublicSessionMethod(String name, Class<?> positionType,
                                                  Class<?> speedType) throws NoSuchMethodException {
        Method method = NavigationSession.class.getMethod(name, positionType, speedType);
        assertTrue(Modifier.isPublic(method.getModifiers()));
    }
}
