package comvyne.patrickstar.api;

import comvyne.patrickstar.internal.PathDirector;
import net.minecraft.world.entity.Mob;

import java.util.function.Function;

/**
 * Single-use navigation handle shared by passive and one-shot requests.
 */
public final class PatrickPathRegistry {
    private final Mob target;
    private final PathDirector.Request request;
    private int priority;
    private int lockMillis;
    private boolean deferOneShot;
    private boolean registered;

    PatrickPathRegistry(Mob target, Function<Mob, PatrickPath> factory) {
        this(target, new PathDirector.Request(
                entity -> Patrick.toPlan(factory.apply(entity)), false));
    }

    PatrickPathRegistry(Mob target, PathDirector.Request request) {
        this.target = target;
        this.request = request;
    }

    public PatrickPathRegistry priority(int value) {
        assertMutable();
        if (value < 0 || value > 65_535) {
            throw new IllegalArgumentException("priority must be in [0, 65535]");
        }
        priority = value;
        return this;
    }

    public PatrickPathRegistry lock(int millis) {
        assertMutable();
        if (millis < 0) {
            throw new IllegalArgumentException("lock milliseconds must be >= 0");
        }
        lockMillis = millis;
        return this;
    }

    /**
     * For a persistent registration, let its current route finish before
     * queued one-shot requests may run. Defaults to false. This setting has
     * no effect when the handle is submitted through Patrick.oneShot().
     */
    public PatrickPathRegistry deferOneShot(boolean value) {
        assertMutable();
        deferOneShot = value;
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

    boolean deferOneShot() {
        return deferOneShot;
    }

    boolean registered() {
        return registered;
    }

    void markRegistered() {
        registered = true;
    }
}
