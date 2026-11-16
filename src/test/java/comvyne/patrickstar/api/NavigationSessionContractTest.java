package comvyne.patrickstar.api;

import comvyne.patrickstar.internal.PathDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NavigationSessionContractTest {
    @Test
    void commandsAreCapturedInOriginalOrderAndSessionExpiresAfterCallback() {
        NavigationSession session = new NavigationSession();
        NavigationSession returned = session.moveTo(new BlockPos(11, 64, 13), 0.8)
                .moveTo(new Vec3(15.5, 64, 19.5), 1.5);
        assertSame(session, returned);

        List<PathDirector.Step> steps = session.closeAndSnapshot();
        assertEquals(2, steps.size());
        assertEquals(Vec3.atBottomCenterOf(new BlockPos(11, 64, 13)),
                ((PathDirector.Point) steps.get(0)).point());
        assertEquals(0.8, ((PathDirector.Point) steps.get(0)).speed());
        assertEquals(new Vec3(15.5, 64, 19.5),
                ((PathDirector.Point) steps.get(1)).point());
        assertEquals(1.5, ((PathDirector.Point) steps.get(1)).speed());
        assertThrows(IllegalStateException.class, () -> session.moveTo(BlockPos.ZERO, 1.0));
        assertThrows(IllegalStateException.class, session::closeAndSnapshot);
        assertThrows(UnsupportedOperationException.class, () -> steps.clear());
    }

    @Test
    void invalidInputCannotLeakIntoNavigation() {
        NavigationSession session = new NavigationSession();
        assertThrows(NullPointerException.class, () -> session.moveTo((BlockPos) null, 1.0));
        assertThrows(NullPointerException.class, () -> session.moveTo((Vec3) null, 1.0));
        assertThrows(NullPointerException.class, () -> session.follow(null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> session.moveTo(BlockPos.ZERO, 0));
        assertThrows(IllegalArgumentException.class, () -> session.moveTo(BlockPos.ZERO, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> session.moveTo(BlockPos.ZERO, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> session.moveTo(new Vec3(Double.NaN, 1, 1), 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> session.moveTo(new Vec3(1, Double.NEGATIVE_INFINITY, 1), 1.0));
        assertTrue(session.closeAndSnapshot().isEmpty());
    }

    @Test
    void legacyBuilderTranslatesIntoEquivalentOrderedStepsAndReachability() {
        PatricPath legacy = PatricPath.builder(new BlockPos(30, 64, 30))
                .waypoint(new Vec3(20.5, 64, 20.5))
                .speed(1.25)
                .reachable(false)
                .build();
        PathDirector.Plan plan = Patric.toPlan(legacy);
        assertNotNull(plan);
        assertEquals(false, plan.reachableOverride());
        assertEquals(2, plan.steps().size());
        assertEquals(new Vec3(20.5, 64, 20.5),
                ((PathDirector.Point) plan.steps().get(0)).point());
        assertEquals(Vec3.atBottomCenterOf(new BlockPos(30, 64, 30)),
                ((PathDirector.Point) plan.steps().get(1)).point());
        assertEquals(1.25, ((PathDirector.Point) plan.steps().get(1)).speed());
        assertNull(Patric.toPlan(null));
    }
}
