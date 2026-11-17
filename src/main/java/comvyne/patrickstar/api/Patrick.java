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
 * Navigation request creation, continuous registration and one-shot dispatch.
 * All navigation requests are processed on the owning server thread.
 */
public final class Patrick {
    private Patrick() {
    }

    /**
     * Creates an unregistered navigation request.
     * The callback is evaluated when the server arbitrates this request.
     */
    public static PatrickPathRegistry create(Mob targetEntity, Function<Mob, PatrickPath> pathFactory) {
        return new PatrickPathRegistry(
                Objects.requireNonNull(targetEntity, "targetEntity"),
                Objects.requireNonNull(pathFactory, "pathFactory")
        );
    }

    /** Register a vanilla destination with default speed 1.0 and no path builder. */
    public static PatrickPathRegistry create(Mob targetEntity, BlockPos destination) {
        Objects.requireNonNull(targetEntity, "targetEntity");
        BlockPos pos = Objects.requireNonNull(destination, "destination").immutable();
        return new PatrickPathRegistry(targetEntity,
                new PathDirector.Request(mob -> new PathDirector.Plan(
                        List.of(new PathDirector.Point(Vec3.atBottomCenterOf(pos), 1.0)), null), false, false));
    }

    /** Register a precise destination with default speed 1.0 and no path builder. */
    public static PatrickPathRegistry create(Mob targetEntity, Vec3 destination) {
        Objects.requireNonNull(targetEntity, "targetEntity");
        Vec3 point = Objects.requireNonNull(destination, "destination");
        return new PatrickPathRegistry(targetEntity,
                new PathDirector.Request(mob -> new PathDirector.Plan(
                        List.of(new PathDirector.Point(point, 1.0)), null), false, false));
    }

    /**
     * Accept a precomputed vanilla Path; the coordinator retains the original
     * and plays a defensive copy to avoid changing the caller's cursor.
     * Prefer the Supplier overload if the Mob may move before activation.
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
     * Generate a fresh native Path when the request is considered for
     * arbitration. This supplier is also invoked on resume after preemption.
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
     * Run existing pathfinding code inside a command-capture callback.
     * The NavigationSession records follow/moveTo commands; it cannot take
     * ownership of Mob navigation until Patrick selects this request.
     *
     * The callback may be re-invoked after a preempted request resumes.
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
     * Register a persistent navigation behavior. It remains registered across
     * route completions until explicitly unregistered or its Mob unloads.
     * @return true if accepted for scheduling, not a statement of route success
     */
    public static boolean register(PatrickPathRegistry registration) {
        return submit(registration, true);
    }

    /**
     * Queue one navigation run, removed after it succeeds or fails. One-shots
     * use the same path factories, priority, lock and arbitration as register.
     * @return true if accepted for scheduling, not a statement of route success
     */
    public static boolean oneShot(PatrickPathRegistry registration) {
        return submit(registration, false);
    }

    /** Cancel an active or queued registration or one-shot using its original handle. */
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
