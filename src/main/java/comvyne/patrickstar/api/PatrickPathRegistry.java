package comvyne.patrickstar.api;

import comvyne.patrickstar.internal.PathDirector;
import net.minecraft.world.entity.Mob;

import java.util.function.Function;

/**
 * Single-use navigation handle shared by passive and one-shot requests.
 * Configure it before submission. The defaults are priority {@code 0},
 * lock {@code 0} milliseconds, and deferred one-shots disabled.
 * After acceptance by {@link Patrick#register(PatrickPathRegistry)} or
 * {@link Patrick#oneShot(PatrickPathRegistry)}, configuration is frozen
 * and the handle cannot be submitted again, even after cancellation.
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

    /**
     * Sets the priority used to rank requests; higher values rank first.
     * Equal-priority requests are ordered by reachability, estimated distance,
     * and registration order. The default is {@code 0}.
     *
     * @param value priority from 0 through 65535, inclusive
     * @return this handle for chaining
     * @throws IllegalArgumentException if the value is outside the supported range
     * @throws IllegalStateException if this handle has already been accepted
     */
    public PatrickPathRegistry priority(int value) {
        assertMutable();
        if (value < 0 || value > 65_535) {
            throw new IllegalArgumentException("priority must be in [0, 65535]");
        }
        priority = value;
        return this;
    }

    /**
     * Sets the minimum protection period against preemption after a request
     * starts or resumes. It does not prevent completion, failure or explicit
     * cancellation. The default is {@code 0} milliseconds (no timed protection).
     *
     * @param millis non-negative duration in milliseconds
     * @return this handle for chaining
     * @throws IllegalArgumentException if the duration is negative
     * @throws IllegalStateException if this handle has already been accepted
     */
    public PatrickPathRegistry lock(int millis) {
        assertMutable();
        if (millis < 0) {
            throw new IllegalArgumentException("lock milliseconds must be >= 0");
        }
        lockMillis = millis;
        return this;
    }

    /**
     * Controls whether a persistent route defers queued one-shots.
     * By default ({@code false}), a one-shot can preempt the passive route
     * after its lock expires, regardless of relative priority. With {@code true},
     * queued one-shots wait until the current passive route yields instead of
     * interrupting it; they are not discarded. Higher-priority persistent
     * requests are still subject to the normal priority and lock rules.
     * This setting has no effect on a handle submitted via
     * {@link Patrick#oneShot(PatrickPathRegistry)}.
     *
     * @param value {@code true} to defer one-shots; {@code false} to permit preemption
     * @return this handle for chaining
     * @throws IllegalStateException if this handle has already been accepted
     * @see #lock(int)
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
