package comvyne.patrickstar.api;

import comvyne.patrickstar.internal.PathDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An invocation-scoped command collector for a controlled Patrick navigation request.
 *
 * A consumer's callback may reuse any existing pathfinding algorithm, then submit
 * one or more movement commands to this session. The commands do not touch vanilla
 * navigation while the callback runs: Patrick starts them only after arbitration.
 *
 * A session becomes invalid after its callback returns. In particular, retaining
 * a session and invoking it from an asynchronous callback is not supported.
 */
public final class NavigationSession {
    private static final int MAX_COMMANDS = 1024;

    private final List<PathDirector.Step> commands = new ArrayList<>();
    private boolean closed;

    // Created only by Patric when it invokes a user-supplied callback.
    NavigationSession() {
    }

    /** Enqueue a previously computed vanilla Path without converting its nodes. */
    public NavigationSession follow(Path path, double speed) {
        ensureOpen();
        validateSpeed(speed);
        add(new PathDirector.Native(Objects.requireNonNull(path, "path"), speed));
        return this;
    }

    /** Enqueue vanilla pathfinding to a block position. */
    public NavigationSession moveTo(BlockPos destination, double speed) {
        ensureOpen();
        validateSpeed(speed);
        return moveTo(Vec3.atBottomCenterOf(Objects.requireNonNull(destination, "destination")), speed);
    }

    /** Enqueue vanilla pathfinding to a world-space point. */
    public NavigationSession moveTo(Vec3 destination, double speed) {
        ensureOpen();
        validateSpeed(speed);
        Vec3 point = Objects.requireNonNull(destination, "destination");
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            throw new IllegalArgumentException("navigation coordinates must be finite");
        }
        add(new PathDirector.Point(point, speed));
        return this;
    }

    private void add(PathDirector.Step step) {
        if (commands.size() >= MAX_COMMANDS) {
            throw new IllegalArgumentException("at most " + MAX_COMMANDS + " navigation commands are supported");
        }
        commands.add(step);
    }

    private static void validateSpeed(double speed) {
        if (!Double.isFinite(speed) || speed <= 0) {
            throw new IllegalArgumentException("speed must be finite and greater than zero");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("navigation session has already expired");
        }
    }

    List<PathDirector.Step> closeAndSnapshot() {
        ensureOpen();
        closed = true;
        return List.copyOf(commands);
    }

    void close() {
        closed = true;
    }
}
