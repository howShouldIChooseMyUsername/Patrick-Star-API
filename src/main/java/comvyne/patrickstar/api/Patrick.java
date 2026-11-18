package comvyne.patrickstar.api;

import comvyne.patrickstar.internal.PathDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Entry point for coordinating navigation requests from participating mods.
 * Create a request with one of the {@code create} overloads, configure its
 * {@linkplain PatrickPathRegistry#priority(int) priority} and optional lock,
 * then submit it with {@link #register(PatrickPathRegistry)} or
 * {@link #oneShot(PatrickPathRegistry)}.
 * Operations must take place on the owning Minecraft server thread. Patrick
 * coordinates only requests submitted through this API.
 */
public final class Patrick {
    private Patrick() {
    }

    /**
     * Creates an unsubmitted request backed by a waypoint route factory.
     * The factory runs lazily during arbitration and can provide a new route
     * for each cycle of a persistent registration. A null route or a failed
     * factory is handled as an unsuccessful evaluation.
     *
     * @param targetEntity mob whose navigation will be coordinated
     * @param pathFactory function producing a route for the mob
     * @return a new, configurable, single-use handle; no movement is scheduled yet
     * @throws NullPointerException if either argument is null
     * @see PatrickPath#builder(BlockPos)
     * @see #register(PatrickPathRegistry)
     */
    public static PatrickPathRegistry create(Mob targetEntity, Function<Mob, PatrickPath> pathFactory) {
        return new PatrickPathRegistry(
                Objects.requireNonNull(targetEntity, "targetEntity"),
                Objects.requireNonNull(pathFactory, "pathFactory")
        );
    }

    /**
     * Creates an unsubmitted request to navigate to the bottom center of a block
     * using vanilla pathfinding at speed {@code 1.0}. The position is captured
     * at creation time.
     *
     * @param targetEntity mob whose navigation will be coordinated
     * @param destination fixed block destination
     * @return a new, configurable, single-use handle
     * @throws NullPointerException if either argument is null
     * @see #create(Mob, Vec3)
     */
    public static PatrickPathRegistry create(Mob targetEntity, BlockPos destination) {
        Objects.requireNonNull(targetEntity, "targetEntity");
        BlockPos pos = Objects.requireNonNull(destination, "destination").immutable();
        return new PatrickPathRegistry(targetEntity,
                new PathDirector.Request(mob -> new PathDirector.Plan(
                        List.of(new PathDirector.Point(Vec3.atBottomCenterOf(pos), 1.0)), null), false, false));
    }

    /**
     * Creates an unsubmitted request to navigate to a fixed world-space point
     * using vanilla pathfinding at speed {@code 1.0}.
     *
     * @param targetEntity mob whose navigation will be coordinated
     * @param destination precise destination in world coordinates
     * @return a new, configurable, single-use handle
     * @throws NullPointerException if either argument is null
     * @see #create(Mob, BlockPos)
     */
    public static PatrickPathRegistry create(Mob targetEntity, Vec3 destination) {
        Objects.requireNonNull(targetEntity, "targetEntity");
        Vec3 point = Objects.requireNonNull(destination, "destination");
        return new PatrickPathRegistry(targetEntity,
                new PathDirector.Request(mob -> new PathDirector.Plan(
                        List.of(new PathDirector.Point(point, 1.0)), null), false, false));
    }

    /**
     * Creates an unsubmitted request from a precomputed vanilla path at speed
     * {@code 1.0}. The path's nodes and cursor are copied at creation time;
     * execution does not advance the caller's original path.
     * Prefer the supplier overload if the mob may move before activation.
     *
     * @param targetEntity mob whose navigation will be coordinated
     * @param path precomputed vanilla path, with at most 4096 nodes
     * @return a new, configurable, single-use handle
     * @throws NullPointerException if an argument, path node or path target is null
     * @throws IllegalArgumentException if the path exceeds the supported node limit
     * @see #create(Mob, Supplier)
     */
    public static PatrickPathRegistry create(Mob targetEntity, Path path) {
        Objects.requireNonNull(targetEntity, "targetEntity");
        Path given = Objects.requireNonNull(path, "path");
        // Capture a defensive native snapshot at create time: callers may
        // continue manipulating their original Path before arbitration.
        PathDirector.Native snapshot = new PathDirector.Native(given, 1.0);
        return new PatrickPathRegistry(targetEntity,
                new PathDirector.Request(mob -> new PathDirector.Plan(
                        List.of(snapshot), snapshot.path().canReach()), false, false));
    }

    /**
     * Creates an unsubmitted request that lazily obtains a vanilla path at
     * speed {@code 1.0}. The supplier may be called again after preemption
     * or during subsequent persistent route cycles. A null path is treated
     * as an unsuccessful evaluation.
     *
     * @param targetEntity mob whose navigation will be coordinated
     * @param pathFactory supplier of the native path to copy and execute
     * @return a new, configurable, single-use handle
     * @throws NullPointerException if either argument is null
     * @see #create(Mob, Path)
     */
    public static PatrickPathRegistry create(Mob targetEntity, Supplier<Path> pathFactory) {
        Objects.requireNonNull(targetEntity, "targetEntity");
        Supplier<Path> factory = Objects.requireNonNull(pathFactory, "pathFactory");
        return new PatrickPathRegistry(targetEntity, new PathDirector.Request(mob -> {
            Path path = factory.get();
            return path == null ? null : new PathDirector.Plan(
                    List.of(new PathDirector.Native(path, 1.0)), path.canReach());
        }, true));
    }

    /**
     * Creates an unsubmitted request backed by a lazy command-capture callback.
     * {@link NavigationSession} records ordered movement commands without
     * starting movement until Patrick selects the request.
     *
     * The callback may be invoked again after preemption or on later persistent
     * route cycles. Do not retain the session after the callback returns, or
     * directly change the mob's vanilla navigation inside the callback.
     *
     * @param targetEntity mob whose navigation will be coordinated
     * @param pathAction callback receiving the mob and its temporary session
     * @return a new, configurable, single-use handle
     * @throws NullPointerException if either argument is null
     * @see NavigationSession
     */
    public static PatrickPathRegistry create(Mob targetEntity,
                                            BiConsumer<Mob, NavigationSession> pathAction) {
        Objects.requireNonNull(targetEntity, "targetEntity");
        BiConsumer<Mob, NavigationSession> action =
                Objects.requireNonNull(pathAction, "pathAction");
        return new PatrickPathRegistry(targetEntity, new PathDirector.Request(mob -> {
            NavigationSession session = new NavigationSession();
            try {
                action.accept(mob, session);
                return new PathDirector.Plan(session.closeAndSnapshot(), null);
            } finally {
                session.close();
            }
        }, true));
    }

    /** Translate legacy waypoint input to the same internal execution plan. */
    static PathDirector.Plan toPlan(PatrickPath path) {
        if (path == null) {
            return null;
        }
        List<PathDirector.Step> steps = new ArrayList<>(path.waypoints().size() + 1);
        for (Vec3 point : path.waypoints()) {
            steps.add(new PathDirector.Point(point, path.speed()));
        }
        steps.add(new PathDirector.Point(Vec3.atBottomCenterOf(path.destination()), path.speed()));
        return new PathDirector.Plan(steps, path.reachableOverride().orElse(null));
    }

    /**
     * Submits a persistent navigation behavior. Dynamic routes can be recalculated
     * after completion; fixed routes rest after arrival until the mob moves away.
     * The behavior remains registered until cancellation, mob removal/death,
     * a change of level, or server shutdown. Acceptance freezes the handle.
     *
     * @param registration unsubmitted handle created by a {@code create} overload
     * @return {@code true} if accepted for scheduling, not if the route succeeds;
     *         {@code false} for null, already accepted or otherwise rejected
     *         requests (including invalid mobs, wrong threads and full queues)
     * @see #oneShot(PatrickPathRegistry)
     * @see #unregister(PatrickPathRegistry)
     * @see PatrickPathRegistry#deferOneShot(boolean)
     */
    public static boolean register(PatrickPathRegistry registration) {
        return submit(registration, true);
    }

    /**
     * Submits a single navigation run, removed on completion or failure.
     * One-shots take precedence over idle persistent behaviors and can preempt
     * a non-deferring passive route after its lock expires. Only higher-priority
     * one-shots can preempt a running one-shot after its lock expires.
     * Acceptance freezes the handle.
     *
     * @param registration unsubmitted handle created by a {@code create} overload
     * @return {@code true} if accepted for scheduling, not if the route succeeds;
     *         {@code false} for null, already accepted or otherwise rejected requests
     * @see #register(PatrickPathRegistry)
     * @see #unregister(PatrickPathRegistry)
     */
    public static boolean oneShot(PatrickPathRegistry registration) {
        return submit(registration, false);
    }

    /**
     * Cancels an active or queued persistent behavior or one-shot using its
     * original handle. Cancellation does not make a submitted handle reusable.
     * The owning server thread must perform this operation.
     *
     * @param registration the original accepted handle
     * @return {@code true} if a matching request was removed; {@code false} otherwise
     * @see #register(PatrickPathRegistry)
     * @see #oneShot(PatrickPathRegistry)
     */
    public static boolean unregister(PatrickPathRegistry registration) {
        return registration != null && registration.registered()
                && PathDirector.cancel(registration.target(), registration);
    }

    private static boolean submit(PatrickPathRegistry registration, boolean persistent) {
        if (registration == null || registration.registered()) {
            return false;
        }
        boolean accepted = PathDirector.enqueue(
                registration.target(), registration, registration.request(),
                registration.getPriority(), registration.getLockMillis(),
                persistent, persistent && registration.deferOneShot()
        );
        if (accepted) {
            registration.markRegistered();
        }
        return accepted;
    }
}
