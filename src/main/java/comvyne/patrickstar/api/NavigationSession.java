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
 * Up to 1024 commands may be collected, in insertion order.
 *
 * @see Patrick#create(net.minecraft.world.entity.Mob, java.util.function.BiConsumer)
 */
public final class NavigationSession {
    private static final int MAX_COMMANDS = 1024;

    private final List<PathDirector.Step> commands = new ArrayList<>();
    private boolean closed;

    // Created only by Patrick when it invokes a user-supplied callback.
    NavigationSession() {
    }

    /**
     * Appends a snapshot of a previously computed vanilla path. Its nodes and
     * cursor are copied so executing the command does not modify the original.
     *
     * @param path native path with at most 4096 nodes
     * @param speed positive finite navigation speed
     * @return this session for chaining
     * @throws IllegalStateException if the session has expired
     * @throws NullPointerException if the path, a path node or its target is null
     * @throws IllegalArgumentException if speed or path size is invalid, or the command limit is exceeded
     */
    public NavigationSession follow(Path path, double speed) {
        ensureOpen();
        validateSpeed(speed);
        add(new PathDirector.Native(Objects.requireNonNull(path, "path"), speed));
        return this;
    }

    /**
     * Appends a command to navigate to the bottom center of a block.
     * Vanilla pathfinding is performed when the command executes.
     *
     * @param destination block destination
     * @param speed positive finite navigation speed
     * @return this session for chaining
     * @throws IllegalStateException if the session has expired
     * @throws NullPointerException if the destination is null
     * @throws IllegalArgumentException if speed is invalid or the command limit is exceeded
     * @see #moveTo(Vec3, double)
     */
    public NavigationSession moveTo(BlockPos destination, double speed) {
        ensureOpen();
        validateSpeed(speed);
        return moveTo(Vec3.atBottomCenterOf(Objects.requireNonNull(destination, "destination")), speed);
    }

    /**
     * Appends a command to navigate to a precise point in world coordinates.
     * Vanilla pathfinding is performed when the command executes.
     *
     * @param destination point with finite coordinates
     * @param speed positive finite navigation speed
     * @return this session for chaining
     * @throws IllegalStateException if the session has expired
     * @throws NullPointerException if the destination is null
     * @throws IllegalArgumentException if coordinates or speed are invalid, or the command limit is exceeded
     */
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
