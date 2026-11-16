package comvyne.patrickstar.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PathPolicyTest {
    private record Request(String owner, PathPolicy.Score score) {
    }

    private static Request request(String name, int priority, boolean reachable,
                                   double distance, long ticket) {
        return new Request(name, new PathPolicy.Score(priority, reachable, distance, ticket));
    }

    private static Request pick(List<Request> pool) {
        return PathPolicy.choose(pool, Request::score);
    }

    @Test
    void threeDifferentModRequestsRunOneByOneWithHighestPriorityFirst() {
        List<Request> pool = new ArrayList<>(List.of(
                request("mod-a", 1, true, 4, 0),
                request("mod-b", 65535, false, 30, 1),
                request("mod-c", 500, true, 2, 2)
        ));
        assertEquals("mod-b", pick(pool).owner());
        pool.remove(pick(pool));
        assertEquals("mod-c", pick(pool).owner());
        pool.remove(pick(pool));
        assertEquals("mod-a", pick(pool).owner());
        pool.remove(pick(pool));
        assertNull(pick(pool));
    }

    @Test
    void tieConsidersReachabilityThenLengthThenRegistrationOrder() {
        List<Request> pool = List.of(
                request("unreachable", 100, false, 1, 0),
                request("long", 100, true, 100, 1),
                request("later", 100, true, 10, 3),
                request("first", 100, true, 10, 2)
        );
        assertEquals("first", pick(pool).owner());
    }

    @Test
    void lockDeniesPreemptionUntilExpiredAndOnlyForHigherPriority() {
        long start = 1_000_000_000L;
        long deadline = start + 1_500_000_000L;
        assertFalse(PathPolicy.canPreempt(100, deadline, 101, start + 1_000_000_000L));
        assertTrue(PathPolicy.canPreempt(100, deadline, 101, deadline));
        assertTrue(PathPolicy.canPreempt(100, deadline, 101, deadline + 1L));
        assertFalse(PathPolicy.canPreempt(100, deadline, 100, deadline + 1L));
        assertFalse(PathPolicy.canPreempt(100, deadline, 99, deadline + 1L));
    }

    @Test
    void priorityBoundsAndBadDistancesAreNotValidScores() {
        assertDoesNotThrow(() -> new PathPolicy.Score(0, true, 0, 0));
        assertDoesNotThrow(() -> new PathPolicy.Score(65535, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PathPolicy.Score(-1, true, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PathPolicy.Score(65536, true, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PathPolicy.Score(1, true, Double.POSITIVE_INFINITY, 0));
    }
}
