package comvyne.patrickstar.api;

import comvyne.patrickstar.internal.PathDirector;
import net.minecraft.world.entity.Mob;

import java.util.function.Function;

/**
 * Single-use registration handle. Only priority and lock are publicly configurable.
 */
public final class PatricPathRegistry {
    private final Mob target;
    private final PathDirector.Request request;
    private int priority;
    private int lockMillis;
    private boolean registered;

    PatricPathRegistry(Mob target, Function<Mob, PatricPath> factory) {
        this(target, new PathDirector.Request(
                entity -> Patric.toPlan(factory.apply(entity)), false));
    }

    PatricPathRegistry(Mob target, PathDirector.Request request) {
        this.target = target;
        this.request = request;
    }

    public PatricPathRegistry priority(int value) {
        assertMutable();
        if (value < 0 || value > 65_535) {
            throw new IllegalArgumentException("priority must be in [0, 65535]");
        }
        priority = value;
        return this;
    }

    public PatricPathRegistry lock(int millis) {
        assertMutable();
        if (millis < 0) {
            throw new IllegalArgumentException("lock milliseconds must be >= 0");
        }
        lockMillis = millis;
        return this;
    }

    private void assertMutable() {
        if (registered) {
            throw new IllegalStateException("registration has already been accepted");
        }
    }

    Mob target() {
        return target;
    }

    PathDirector.Request request() {
        return request;
    }

    int getPriority() {
        return priority;
    }

    int getLockMillis() {
        return lockMillis;
    }

    boolean registered() {
        return registered;
    }

    void markRegistered() {
        registered = true;
    }
}
