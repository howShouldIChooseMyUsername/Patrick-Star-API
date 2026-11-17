package comvyne.patrickstar.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * User-supplied ordered waypoint route, independent of Minecraft's internal Node class.
 * An absent reachability override is computed by the server when the request is evaluated.
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

    public static Builder builder(BlockPos destination) {
        return new Builder(destination);
    }

    public BlockPos destination() {
        return destination;
    }

    public List<Vec3> waypoints() {
        return waypoints;
    }

    public double speed() {
        return speed;
    }

    /** Empty means the server calculates reachability using vanilla navigation. */
    public Optional<Boolean> reachableOverride() {
        return Optional.ofNullable(reachableOverride);
    }

    public static final class Builder {
        private final BlockPos destination;
        private final List<Vec3> waypoints = new ArrayList<>();
        private double speed = 1.0;
        private Boolean reachableOverride;

        private Builder(BlockPos destination) {
            this.destination = Objects.requireNonNull(destination, "destination").immutable();
        }

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

        public Builder waypoints(Collection<Vec3> points) {
            Objects.requireNonNull(points, "points");
            for (Vec3 point : points) {
                waypoint(point);
            }
            return this;
        }

        public Builder speed(double value) {
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("speed must be positive and finite");
            }
            speed = value;
            return this;
        }

        public Builder reachable(boolean value) {
            reachableOverride = value;
            return this;
        }

        public PatrickPath build() {
            return new PatrickPath(this);
        }
    }
}
