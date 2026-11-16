package comvyne.patrickstar.internal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * One coordinator and one MOVE goal per participating Mob.
 *
 * Only the Minecraft server thread may access this class. Requests are installed
 * at the end of a server tick to avoid modifying a GoalSelector during its iteration.
 */
public final class PathDirector {
    private static final Logger LOG = LoggerFactory.getLogger("PatrickStarAPI");
    private static final int MAX_REQUESTS_PER_MOB = 256;
    private static final int MAX_STEPS_PER_PLAN = 1025;
    private static final int MAX_NODES_PER_NATIVE_PATH = 4096;
    private static final Map<Mob, Controller> CONTROLLERS = new IdentityHashMap<>();
    private static long nextTicket;
    private static boolean initialized;

    private PathDirector() {
    }

    /** A lazy route provider; dynamic providers are evaluated again after suspension. */
    public record Request(Function<Mob, Plan> factory, boolean refreshOnResume) {
        public Request {
            Objects.requireNonNull(factory, "factory");
        }
    }

    /** A complete ordered route, with an optional reachability ranking override. */
    public record Plan(List<Step> steps, Boolean reachableOverride) {
        public Plan {
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            if (steps.size() > MAX_STEPS_PER_PLAN) {
                throw new IllegalArgumentException("at most 1025 movement steps are supported");
            }
        }
    }

    public sealed interface Step permits Point, Native {
    }

    public record Point(Vec3 point, double speed) implements Step {
        public Point {
            Objects.requireNonNull(point, "point");
            requireSpeed(speed);
        }
    }

    public record Native(Path path, double speed) implements Step {
        public Native {
            requireSpeed(speed);
            // Vanilla Path.copy() shares the original Node list. Snapshot each node.
            path = copyNativePath(Objects.requireNonNull(path, "path"));
        }
    }

    private static void requireSpeed(double speed) {
        if (!Double.isFinite(speed) || speed <= 0) {
            throw new IllegalArgumentException("movement speed must be positive and finite");
        }
    }

    private static Path copyNativePath(Path path) {
        int count = path.getNodeCount();
        if (count < 0 || count > MAX_NODES_PER_NATIVE_PATH) {
            throw new IllegalArgumentException("native path exceeds node limit");
        }
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Node source = Objects.requireNonNull(path.getNode(i), "native path node");
            Node node = new Node(source.x, source.y, source.z);
            node.g = source.g;
            node.h = source.h;
            node.f = source.f;
            node.walkedDistance = source.walkedDistance;
            node.costMalus = source.costMalus;
            node.type = source.type;
            nodes.add(node);
        }
        BlockPos target = Objects.requireNonNull(path.getTarget(), "native path target").immutable();
        Path copy = new Path(nodes, target, path.canReach());
        copy.setNextNodeIndex(path.getNextNodeIndex());
        return copy;
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> maintenance());
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Mob mob) {
                Controller controller = CONTROLLERS.get(mob);
                if (controller != null) {
                    controller.discarded = true;
                    try {
                        controller.releaseNavigation(controller.running);
                    } catch (RuntimeException ex) {
                        LOG.warn("Could not stop owned navigation on unload for mob {} ({}).",
                                mob.getUUID(), ex.getClass().getSimpleName());
                    }
                    controller.running = null;
                    controller.tasks.clear();
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Controller controller : CONTROLLERS.values()) {
                try {
                    controller.dispose();
                } catch (RuntimeException ex) {
                    LOG.warn("Could not dispose navigation during server stop ({}).",
                            ex.getClass().getSimpleName());
                }
            }
            CONTROLLERS.clear();
            nextTicket = 0;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CONTROLLERS.clear();
            nextTicket = 0;
        });
    }

    public static boolean enqueue(Mob mob, Request request,
                                  int priority, int lockMillis) {
        if (mob == null || request == null
                || priority < 0 || priority > 65_535 || lockMillis < 0
                || !(mob.level() instanceof ServerLevel serverLevel)
                || !serverLevel.getServer().isSameThread()
                || mob.isRemoved() || !mob.isAlive()
                || serverLevel.getEntity(mob.getId()) != mob) {
            return false;
        }

        Controller controller = CONTROLLERS.computeIfAbsent(mob, Controller::new);
        if (controller.discarded || controller.sourceLevel != serverLevel
                || controller.tasks.size() >= MAX_REQUESTS_PER_MOB) {
            return false;
        }
        controller.tasks.add(new Task(request, priority, lockMillis, nextTicket++));
        return true;
    }

    private static void maintenance() {
        Iterator<Map.Entry<Mob, Controller>> iterator = CONTROLLERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Controller controller = iterator.next().getValue();
            Mob mob = controller.mob;
            if (controller.discarded || mob.isRemoved() || !mob.isAlive()
                    || mob.level() != controller.sourceLevel) {
                controller.dispose();
                iterator.remove();
                continue;
            }

            if (controller.tasks.isEmpty()) {
                controller.dispose();
                iterator.remove();
            } else if (!controller.installed) {
                try {
                    mob.getGoalSelector().addGoal(0, controller.goal);
                    controller.installed = true;
                } catch (RuntimeException ex) {
                    LOG.warn("Could not install navigation goal for mob {} ({}).",
                            mob.getUUID(), ex.getClass().getSimpleName());
                    controller.dispose();
                    iterator.remove();
                }
            }
        }
    }

    private static final class Task {
        final Request request;
        final int priority;
        final int lockMillis;
        final long ticket;
        Plan plan;
        boolean evaluated;
        boolean rejected;
        boolean reachable;
        int nextStep;
        int nativeNextNode;
        int observedNodeIndex = -1;
        boolean moving;
        Vec3 expectedPoint;
        BlockPos expectedTarget;
        Path ownedNativePath;
        double bestDistance = Double.POSITIVE_INFINITY;
        int noProgressTicks;
        int externalOverrideAttempts;
        long lockDeadlineNanos;

        Task(Request request, int priority, int lockMillis, long ticket) {
            this.request = request;
            this.priority = priority;
            this.lockMillis = lockMillis;
            this.ticket = ticket;
        }

        Step step() {
            return plan.steps().get(nextStep);
        }

        boolean atFinalStep() {
            return nextStep == plan.steps().size() - 1;
        }
    }

    private static final class Controller {
        final Mob mob;
        final ServerLevel sourceLevel;
        final MoveGoal goal = new MoveGoal(this);
        final List<Task> tasks = new ArrayList<>();
        Task running;
        boolean installed;
        boolean discarded;

        Controller(Mob mob) {
            this.mob = mob;
            this.sourceLevel = (ServerLevel) mob.level();
        }

        void dispose() {
            try {
                releaseNavigation(running);
            } catch (RuntimeException ex) {
                LOG.warn("Could not release owned navigation for mob {} ({}).",
                        mob.getUUID(), ex.getClass().getSimpleName());
            }
            if (installed) {
                try {
                    mob.getGoalSelector().removeGoal(goal);
                } catch (RuntimeException ex) {
                    LOG.warn("Could not remove navigation goal for mob {} ({}).",
                            mob.getUUID(), ex.getClass().getSimpleName());
                }
                installed = false;
            }
            running = null;
            tasks.clear();
            discarded = true;
        }

        void evaluatePending(int higherThanPriority) {
            // Providers are called outside any synchronized section. A callback may
            // register another task, so iterate a snapshot of the current queue.
            for (Task task : List.copyOf(tasks)) {
                if (task.evaluated || task.rejected || task.priority <= higherThanPriority
                        || discarded || !tasks.contains(task)) {
                    continue;
                }
                task.evaluated = true;
                try {
                    Plan provided = task.request.factory().apply(mob);
                    if (provided == null || !validPlan(provided)
                            || sourceLevel.getEntity(mob.getId()) != mob || !mob.isAlive()) {
                        task.rejected = true;
                        LOG.warn("Navigation provider returned an invalid or unloaded route for mob {}.",
                                mob.getUUID());
                        continue;
                    }
                    // Separate the running route from any mutable Path retained by
                    // the provider; vanilla Path.copy() does not clone its nodes.
                    List<Step> steps = new ArrayList<>(provided.steps().size());
                    for (Step step : provided.steps()) {
                        if (step instanceof Native nativeStep) {
                            steps.add(new Native(nativeStep.path(), nativeStep.speed()));
                        } else {
                            steps.add(step);
                        }
                    }
                    task.plan = new Plan(steps, provided.reachableOverride());
                    task.nextStep = 0;
                    task.nativeNextNode = 0;
                    Boolean override = task.plan.reachableOverride();
                    if (override != null) {
                        task.reachable = override;
                    } else {
                        Step finalStep = task.plan.steps().getLast();
                        if (finalStep instanceof Native nativeStep) {
                            task.reachable = nativeStep.path().canReach();
                        } else {
                            BlockPos target = BlockPos.containing(((Point) finalStep).point());
                            Path vanilla = mob.getNavigation().createPath(target, 0);
                            task.reachable = vanilla != null && vanilla.canReach();
                        }
                    }
                } catch (RuntimeException ex) {
                    task.rejected = true;
                    LOG.warn("Navigation provider failed for mob {} ({}).",
                            mob.getUUID(), ex.getClass().getSimpleName());
                }
            }
            tasks.removeIf(task -> task.rejected);
            if (running != null && !tasks.contains(running)) {
                running = null;
            }
        }

        boolean validPlan(Plan plan) {
            if (plan.steps().isEmpty() || plan.steps().size() > MAX_STEPS_PER_PLAN) {
                return false;
            }
            int nativeNodes = 0;
            for (Step step : plan.steps()) {
                if (step instanceof Point point) {
                    if (!validPoint(point.point())) {
                        return false;
                    }
                } else if (step instanceof Native nativeStep) {
                    Path path = nativeStep.path();
                    int count = path.getNodeCount();
                    nativeNodes += count;
                    if (count < 1 || count > MAX_NODES_PER_NATIVE_PATH || nativeNodes > 8192
                            || path.getNextNodeIndex() < 0 || path.getNextNodeIndex() >= count
                            || !withinWorld(Vec3.atBottomCenterOf(path.getTarget()))) {
                        return false;
                    }
                    for (int index = 0; index < count; index++) {
                        if (!validPoint(Vec3.atBottomCenterOf(path.getNodePos(index)))) {
                            return false;
                        }
                    }
                } else {
                    return false;
                }
            }
            return true;
        }

        boolean withinWorld(Vec3 point) {
            return point != null && Double.isFinite(point.x) && Double.isFinite(point.y)
                    && Double.isFinite(point.z)
                    && Math.abs(point.x) < 30_000_000 && Math.abs(point.z) < 30_000_000
                    && point.y >= sourceLevel.getMinY()
                    && point.y <= sourceLevel.getMaxY() + 1;
        }

        boolean validPoint(Vec3 point) {
            return withinWorld(point) && sourceLevel.hasChunkAt(BlockPos.containing(point));
        }

        Vec3 endpoint(Step step) {
            if (step instanceof Point point) {
                return point.point();
            }
            Path path = ((Native) step).path();
            return path.getEntityPosAtNode(mob, path.getNodeCount() - 1);
        }

        double remainingDistance(Task task) {
            Vec3 previous = mob.position();
            double total = 0;
            for (int i = task.nextStep; i < task.plan.steps().size(); i++) {
                Step step = task.plan.steps().get(i);
                if (step instanceof Native nativeStep) {
                    Path nativePath = nativeStep.path();
                    int start = i == task.nextStep ? Math.max(task.nativeNextNode,
                            nativePath.getNextNodeIndex()) : nativePath.getNextNodeIndex();
                    for (int n = start; n < nativePath.getNodeCount(); n++) {
                        Vec3 next = nativePath.getEntityPosAtNode(mob, n);
                        total += previous.distanceTo(next);
                        previous = next;
                    }
                } else {
                    Vec3 next = ((Point) step).point();
                    total += previous.distanceTo(next);
                    previous = next;
                }
            }
            return Double.isFinite(total) ? total : Double.MAX_VALUE;
        }

        Task choose() {
            return PathPolicy.choose(
                    tasks.stream().filter(task -> task.evaluated && !task.rejected
                            && task.plan != null).toList(),
                    task -> new PathPolicy.Score(task.priority, task.reachable,
                            remainingDistance(task), task.ticket)
            );
        }

        void selectOrPreempt() {
            // Never evaluate pending providers during a protected lock period
            // or if a pending request cannot possibly preempt the current one.
            if (running != null) {
                long now = System.nanoTime();
                boolean higherWaiting = tasks.stream()
                        .anyMatch(task -> task != running && PathPolicy.canPreempt(
                                running.priority, running.lockDeadlineNanos, task.priority, now));
                if (!higherWaiting) {
                    return;
                }
                // Crucially, a newly arrived higher-priority provider can fail
                // or return null. Validate candidates BEFORE releasing the
                // existing navigation, otherwise an invalid request restarts
                // a perfectly healthy running path.
                evaluatePending(running.priority);
                Task candidate = choose();
                if (candidate == null || candidate == running
                        || candidate.priority <= running.priority) {
                    return;
                }
                Task previous = running;
                suspendTask(previous);
                running = null;
                activate(candidate);
                return;
            }

            evaluatePending(-1);
            if (tasks.isEmpty()) {
                running = null;
                return;
            }
            Task candidate = choose();
            if (candidate == null) {
                return;
            }
            if (running == null) {
                activate(candidate);
            }
        }

        void activate(Task task) {
            running = task;
            task.moving = false;
            task.expectedPoint = null;
            task.expectedTarget = null;
            task.ownedNativePath = null;
            task.observedNodeIndex = -1;
            task.noProgressTicks = 0;
            task.bestDistance = Double.POSITIVE_INFINITY;
            long now = System.nanoTime();
            task.lockDeadlineNanos = now + (long) task.lockMillis * 1_000_000L;
        }

        void releaseNavigation(Task task) {
            if (task == null) {
                return;
            }
            Path owned = task.ownedNativePath;
            if (owned != null) {
                if (task.plan != null && task.nextStep < task.plan.steps().size()
                        && task.step() instanceof Native) {
                    task.nativeNextNode = Math.max(task.nativeNextNode, owned.getNextNodeIndex());
                }
                PathNavigation navigation = mob.getNavigation();
                if (navigation.getPath() == owned
                        && Objects.equals(navigation.getTargetPos(), task.expectedTarget)) {
                    navigation.stop();
                }
            }
            task.ownedNativePath = null;
            task.expectedTarget = null;
            task.expectedPoint = null;
        }

        void suspendTask(Task task) {
            releaseNavigation(task);
            task.moving = false;
            task.noProgressTicks = 0;
            task.bestDistance = Double.POSITIVE_INFINITY;
            task.observedNodeIndex = -1;
            if (task.request.refreshOnResume()) {
                task.evaluated = false;
                task.plan = null;
                task.nextStep = 0;
                task.nativeNextNode = 0;
            }
        }

        void finishRunning() {
            if (running != null) {
                releaseNavigation(running);
                tasks.remove(running);
                running = null;
            }
        }

        void suspend() {
            if (running != null) {
                suspendTask(running);
                running = null;
            }
        }

        void tick() {
            if (discarded) {
                return;
            }
            if (mob.isRemoved() || !mob.isAlive() || mob.level() != sourceLevel
                    || sourceLevel.getEntity(mob.getId()) != mob) {
                // GoalSelector is iterating right now. Its removal happens at
                // END_SERVER_TICK, as does normal goal installation.
                discarded = true;
                releaseNavigation(running);
                running = null;
                tasks.clear();
                return;
            }
            selectOrPreempt();
            Task task = running;
            if (task == null) {
                return;
            }
            Step step = task.step();
            Vec3 point = endpoint(step);
            if (step instanceof Point && reached(point)) {
                advanceStep(task);
                return;
            }
            PathNavigation navigation = mob.getNavigation();
            if (!task.moving) {
                try {
                    boolean started;
                    if (step instanceof Native nativeStep) {
                        if (!validPlan(new Plan(List.of(nativeStep), null))) {
                            finishRunning();
                            return;
                        }
                        Path toFollow = copyNativePath(nativeStep.path());
                        toFollow.setNextNodeIndex(Math.min(toFollow.getNodeCount() - 1,
                                Math.max(task.nativeNextNode, toFollow.getNextNodeIndex())));
                        started = navigation.moveTo(toFollow, nativeStep.speed());
                    } else {
                        if (!validPoint(point)) {
                            finishRunning();
                            return;
                        }
                        started = navigation.moveTo(point.x, point.y, point.z, ((Point) step).speed());
                    }
                    if (!started || navigation.getPath() == null) {
                        LOG.warn("Navigation could not start for mob {} from {} to {} (segment={}).",
                                mob.getUUID(), mob.position(), point, task.nextStep);
                        finishRunning();
                        return;
                    }
                } catch (RuntimeException ex) {
                    LOG.warn("Navigation failed to start for mob {} ({}).",
                            mob.getUUID(), ex.getClass().getSimpleName());
                    finishRunning();
                    return;
                }
                task.moving = true;
                task.expectedPoint = point;
                task.ownedNativePath = navigation.getPath();
                task.expectedTarget = navigation.getTargetPos();
                task.observedNodeIndex = task.ownedNativePath.getNextNodeIndex();
                task.bestDistance = distanceToNextNode(task.ownedNativePath, point);
                task.noProgressTicks = 0;
                return;
            }
            BlockPos activeTarget = navigation.getTargetPos();
            if (navigation.getPath() != task.ownedNativePath
                    || !Objects.equals(activeTarget, task.expectedTarget)) {
                // Another owner replaced the path. Do not stop their navigation.
                if (++task.externalOverrideAttempts > 2) {
                    finishRunning();
                } else {
                    task.moving = false;
                    task.ownedNativePath = null;
                    task.expectedTarget = null;
                }
                return;
            }
            if (step instanceof Point && reached(point)) {
                advanceStep(task);
                return;
            }
            if (navigation.isStuck()) {
                finishRunning();
                return;
            }
            if (navigation.isDone()) {
                if (reached(point)) {
                    advanceStep(task);
                } else {
                    finishRunning();
                }
                return;
            }

            Path active = task.ownedNativePath;
            int nodeIndex = active.getNextNodeIndex();
            if (step instanceof Native) {
                task.nativeNextNode = Math.max(task.nativeNextNode, nodeIndex);
            }
            double distance = distanceToNextNode(active, point);
            if (nodeIndex > task.observedNodeIndex || distance + 0.15 < task.bestDistance) {
                task.observedNodeIndex = nodeIndex;
                task.bestDistance = distance;
                task.noProgressTicks = 0;
            } else if (++task.noProgressTicks >= 120) {
                finishRunning();
            }
        }

        double distanceToNextNode(Path path, Vec3 fallback) {
            return path != null && path.getNextNodeIndex() >= 0
                    && path.getNextNodeIndex() < path.getNodeCount()
                    ? mob.position().distanceTo(path.getNextEntityPos(mob))
                    : mob.position().distanceTo(fallback);
        }

        void advanceStep(Task task) {
            if (task.atFinalStep()) {
                finishRunning();
                return;
            }
            releaseNavigation(task);
            task.nextStep++;
            task.nativeNextNode = 0;
            task.observedNodeIndex = -1;
            task.moving = false;
            task.externalOverrideAttempts = 0;
            task.noProgressTicks = 0;
            task.bestDistance = Double.POSITIVE_INFINITY;
        }

        boolean reached(Vec3 point) {
            double dx = mob.getX() - point.x;
            double dz = mob.getZ() - point.z;
            return dx * dx + dz * dz <= 2.25 && Math.abs(mob.getY() - point.y) <= 1.5;
        }
    }

    private static final class MoveGoal extends Goal {
        final Controller controller;

        MoveGoal(Controller controller) {
            this.controller = controller;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !controller.discarded && controller.mob.isAlive()
                    && controller.mob.level() == controller.sourceLevel
                    && !controller.tasks.isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            try {
                controller.tick();
            } catch (RuntimeException ex) {
                LOG.warn("Navigation request failed for mob {} ({}).",
                        controller.mob.getUUID(), ex.getClass().getSimpleName());
                Task failed = controller.running;
                if (failed != null) {
                    try {
                        controller.releaseNavigation(failed);
                    } catch (RuntimeException stopFailure) {
                        LOG.warn("Could not release failed navigation for mob {} ({}).",
                                controller.mob.getUUID(), stopFailure.getClass().getSimpleName());
                    }
                    controller.running = null;
                    controller.tasks.remove(failed);
                } else {
                    // Unexpected failures without an owner must not crash or
                    // retry forever in another Minecraft server tick.
                    controller.discarded = true;
                    controller.tasks.clear();
                }
            }
        }

        @Override
        public void stop() {
            try {
                controller.suspend();
            } catch (RuntimeException ex) {
                LOG.warn("Could not suspend navigation for mob {} ({}).",
                        controller.mob.getUUID(), ex.getClass().getSimpleName());
                controller.running = null;
            }
        }
    }
}
