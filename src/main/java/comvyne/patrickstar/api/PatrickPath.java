package comvyne.patrickstar.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable ordered waypoint route, independent of Minecraft's internal Node class.
 * Waypoints are followed in insertion order, then the final block destination.
 * An absent reachability override is computed by the server during evaluation.
 * Use {@link #builder(BlockPos)} to construct a route.
 */
public final class PatrickPath {
    private final BlockPos destination;
    private final List<Vec3> waypoints;
    private final double speed;
    private final Boolean reachableOverride;

    private PatrickPath(Builder builder) {
        this.destination = builder.destination.immutable();
        this.waypoints = List.copyOf(builder.waypoints);
        this.speed = builder.speed;
        this.reachableOverride = builder.reachableOverride;
    }

    /**
     * Creates a builder for a route ending at a block destination. The builder
     * starts with no waypoints, speed {@code 1.0}, and automatic reachability.
     *
     * @param destination final block destination, captured as an immutable position
     * @return a new route builder
     * @throws NullPointerException if the destination is null
     */
    public static Builder builder(BlockPos destination) {
        return new Builder(destination);
    }

    /**
     * Returns the final block destination, navigated to after all waypoints.
     *
     * @return immutable final block position
     */
    public BlockPos destination() {
        return destination;
    }

    /**
     * Returns the intermediate points in execution order, excluding the final
     * block destination.
     *
     * @return unmodifiable list of waypoints, possibly empty
     */
    public List<Vec3> waypoints() {
        return waypoints;
    }

    /**
     * Returns the movement speed passed to vanilla navigation for each segment.
     *
     * @return positive finite speed; the default is 1.0
     */
    public double speed() {
        return speed;
    }

    /**
     * Returns the optional reachability override used in request ranking.
     * This is a ranking hint, not a guarantee of successful navigation.
     *
     * @return explicit reachability hint, or an empty optional for automatic evaluation
     * @see Builder#reachable(boolean)
     */
    public Optional<Boolean> reachableOverride() {
        return Optional.ofNullable(reachableOverride);
    }

    /**
     * Mutable builder for immutable {@link PatrickPath} snapshots.
     * Waypoints are appended in order and at most 1024 may be specified.
     */
    public static final class Builder {
        private final BlockPos destination;
        private final List<Vec3> waypoints = new ArrayList<>();
        private double speed = 1.0;
        private Boolean reachableOverride;

        private Builder(BlockPos destination) {
            this.destination = Objects.requireNonNull(destination, "destination").immutable();
        }

        /**
         * Appends an intermediate world-space waypoint.
         *
         * @param point waypoint with finite coordinates
         * @return this builder for chaining
         * @throws NullPointerException if the point is null
         * @throws IllegalArgumentException if coordinates are non-finite or the waypoint limit is exceeded
         */
        public Builder waypoint(Vec3 point) {
            Objects.requireNonNull(point, "point");
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
                throw new IllegalArgumentException("waypoint coordinates must be finite");
            }
            if (waypoints.size() >= 1024) {
                throw new IllegalArgumentException("at most 1024 waypoints are supported");
            }
            waypoints.add(point);
            return this;
        }

        /**
         * Appends intermediate waypoints in the collection's iteration order.
         * Each point is validated as it is appended.
         *
         * @param points collection of intermediate points
         * @return this builder for chaining
         * @throws NullPointerException if the collection or any point is null
         * @throws IllegalArgumentException if any coordinates are non-finite or the waypoint limit is exceeded
         * @see #waypoint(Vec3)
         */
        public Builder waypoints(Collection<Vec3> points) {
            Objects.requireNonNull(points, "points");
            for (Vec3 point : points) {
                waypoint(point);
            }
            return this;
        }

        /**
         * Sets the navigation speed for the entire route (default {@code 1.0}).
         *
         * @param value positive finite vanilla navigation speed
         * @return this builder for chaining
         * @throws IllegalArgumentException if the speed is not positive and finite
         */
        public Builder speed(double value) {
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("speed must be positive and finite");
            }
            speed = value;
            return this;
        }

        /**
         * Overrides automatic reachability evaluation for request ranking.
         * A false hint does not prevent execution, and a true hint does not
         * guarantee arrival. Omitting this call leaves evaluation automatic.
         *
         * @param value explicit reachability hint
         * @return this builder for chaining
         */
        public Builder reachable(boolean value) {
            reachableOverride = value;
            return this;
        }

        /**
         * Creates an immutable snapshot of the current builder configuration.
         * Later builder changes do not affect the returned route.
         *
         * @return a new immutable route
         */
        public PatrickPath build() {
            return new PatrickPath(this);
        }
    }
}
