package comvyne.patrickstar.internal;

import comvyne.patrickstar.api.NavigationSession;
import comvyne.patrickstar.api.Patric;
import comvyne.patrickstar.api.PatricPathRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in dedicated-server verification of Patrick's six create overloads.
 *
 * This probe uses only the public Patrick API. Every result requires real Cow
 * navigation in a loaded arena; successful registration alone never counts as
 * a successful movement. It does not require or request any consumer mods.
 * The caller enables it with run/patrick-overload-runtime.enabled.
 */
final class PatrickOverloadRuntimeProbe {
    private static final Logger LOG = LoggerFactory.getLogger("PatrickOverloadRuntime");
    private static final String PREFIX = "POVERLOAD";
    private static final String PATRICK_GOAL = "comvyne.patrickstar.internal.PathDirector$MoveGoal";
    private static final java.nio.file.Path OUTPUT =
            java.nio.file.Path.of("patrick-overload-runtime-results.txt");

    private static final int START_X = 8;
    private static final int START_Z = 8;
    private static final int SERVER_BOOT_TICKS = 40;
    private static final int ARENA_WARM_TICKS = 30;
    private static final int ENTITY_INDEX_TIMEOUT = 40;
    private static final int NATIVE_PATH_SETTLE_TICKS = 12;
    private static final int NATIVE_PATH_SETTLE_TIMEOUT = 80;
    private static final int STAGE_TIMEOUT = 500;
    private static final int STAGE_COUNT = 4;

    private final List<String> results = new ArrayList<>();
    private final List<BlockPos> forcedChunks = new ArrayList<>();
    private final boolean[] observed = new boolean[3];
    private final boolean[] visited = new boolean[3];

    private int ticks;
    private int arenaWarmTicks;
    private int stage;
    private int stageTicks;
    private int quietTicks;
    private int passes;
    private int failures;
    private int worldY;
    private int supplierCalls;
    private int sessionCalls;
    private boolean arenaReady;
    private boolean submitted;
    private boolean done;
    private boolean supplierWasLazy;
    private boolean sessionExpired;
    private boolean originalCursorIntact = true;
    private Cow cow;
    private Path originalPath;
    private int originalPathCursor;
    private NavigationSession capturedSession;

    static void initialize() {
        if (!Files.isRegularFile(java.nio.file.Path.of("run", "patrick-overload-runtime.enabled"))
                && !Files.isRegularFile(java.nio.file.Path.of("patrick-overload-runtime.enabled"))) {
            return;
        }
        PatrickOverloadRuntimeProbe probe = new PatrickOverloadRuntimeProbe();
        LOG.info("{} ENABLED: public overloads on real Cow with loaded flat arena", PREFIX);
        ServerTickEvents.END_SERVER_TICK.register(probe::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(probe::onServerStopping);
    }

    private void tick(MinecraftServer server) {
        if (done) {
            return;
        }
        try {
            if (++ticks < SERVER_BOOT_TICKS) {
                return;
            }
            ServerLevel level = server.overworld();
            if (!arenaReady) {
                prepareArena(level);
                arenaReady = true;
                return;
            }
            if (++arenaWarmTicks < ARENA_WARM_TICKS) {
                return;
            }
            if (stage == 0) {
                startStage(level, 1);
                return;
            }

            stageTicks++;
            if (cow == null || cow.isRemoved() || !cow.isAlive()
                    || cow.level() != level) {
                failStage(level, "test Cow was lost before movement completed");
                return;
            }
            if (!submitted) {
                if (level.getEntity(cow.getId()) != cow) {
                    if (stageTicks >= ENTITY_INDEX_TIMEOUT) {
                        failStage(level, "Cow was never indexed by ServerLevel; id=" + cow.getId());
                    }
                    return;
                }
                // A real Cow can be indexed before its first physics ticks have
                // placed it on the floor. Vanilla createPath() may return null
                // at that moment even in a clear, loaded arena. Stages 2 and 4
                // build native Paths immediately, so wait for a settled Mob.
                if ((stage == 2 || stage == 4)
                        && (cow.tickCount < NATIVE_PATH_SETTLE_TICKS || !cow.onGround())) {
                    if (stageTicks >= NATIVE_PATH_SETTLE_TIMEOUT) {
                        failStage(level, "Cow was not grounded and ticked before native createPath: "
                                + "entityTicks=" + cow.tickCount + " onGround=" + cow.onGround()
                                + " position=" + cow.position());
                    }
                    return;
                }
                submitStage(level);
                submitted = true;
                stageTicks = 0;
                return;
            }
            if (stageTicks >= STAGE_TIMEOUT) {
                failStage(level, "timeout after " + stageTicks + " ticks: " + diagnostics());
                return;
            }
            if (stageTicks % 60 == 0) {
                LOG.info("{} DIAG stage={} tick={} {}", PREFIX, stage, stageTicks, diagnostics());
            }
            observeStage(level);
        } catch (Throwable error) {
            LOG.error("{} stage {} threw", PREFIX, stage, error);
            record(false, stageName(), "unexpected " + error.getClass().getSimpleName()
                    + ": " + error.getMessage() + "; " + diagnostics());
            try {
                startStage(server.overworld(), stage + 1);
            } catch (Throwable cleanupError) {
                LOG.error("{} could not continue after stage error", PREFIX, cleanupError);
                finish(server.overworld());
            }
        }
    }

    private void prepareArena(ServerLevel level) {
        for (int chunkX = 0; chunkX <= 1; chunkX++) {
            for (int chunkZ = 0; chunkZ <= 1; chunkZ++) {
                if (level.setChunkForced(chunkX, chunkZ, true)) {
                    forcedChunks.add(new BlockPos(chunkX, 0, chunkZ));
                }
                level.getChunk(chunkX, chunkZ);
            }
        }

        worldY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, START_X, START_Z);
        if (worldY < level.getMinY() + 1 || worldY + 3 > level.getMaxY()) {
            throw new IllegalStateException("Test arena Y is outside build limits: " + worldY);
        }
        // Use one level floor for all four mobs, regardless of world generation.
        // Leave a 3-block-high air corridor for navigation and Cow collisions.
        for (int x = 4; x <= 27; x++) {
            for (int z = 4; z <= 27; z++) {
                BlockPos floor = new BlockPos(x, worldY - 1, z);
                if (!level.getBlockState(floor).is(Blocks.STONE)) {
                    level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                }
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos air = new BlockPos(x, worldY + dy, z);
                    if (!level.getBlockState(air).isAir()) {
                        level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        LOG.info("{} ARENA forced and leveled around ({},{},{}); warming {} ticks",
                PREFIX, START_X, worldY, START_Z, ARENA_WARM_TICKS);
    }

    private void startStage(ServerLevel level, int nextStage) {
        discardCow();
        if (nextStage > STAGE_COUNT) {
            finish(level);
            return;
        }
        stage = nextStage;
        stageTicks = 0;
        quietTicks = 0;
        submitted = false;
        supplierCalls = 0;
        sessionCalls = 0;
        supplierWasLazy = false;
        sessionExpired = false;
        originalCursorIntact = true;
        originalPath = null;
        capturedSession = null;
        for (int i = 0; i < observed.length; i++) {
            observed[i] = false;
            visited[i] = false;
        }

        Cow fresh = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        if (fresh == null) {
            throw new IllegalStateException("EntityTypes.COW.create returned null");
        }
        fresh.setPos(START_X + 0.5, worldY, START_Z + 0.5);
        fresh.setPersistenceRequired();
        // Suppress random grazing/wandering while preserving real Cow navigation.
        fresh.getGoalSelector().removeAllGoals(goal -> true);
        if (!level.addFreshEntity(fresh)) {
            throw new IllegalStateException("Could not add Cow to ServerLevel");
        }
        cow = fresh;
        LOG.info("{} BEGIN {} cow={} pos={}", PREFIX, stageName(), cow.getUUID(), cow.position());
    }

    private void submitStage(ServerLevel level) {
        boolean accepted;
        switch (stage) {
            case 1 -> {
                // Submit farther first. Equal priority means the later, closer
                // Vec3 request must still execute before the BlockPos request.
                PatricPathRegistry farther = Patric.create(cow, pos(21, 8)).priority(200);
                PatricPathRegistry closer = Patric.create(cow,
                        Vec3.atBottomCenterOf(pos(12, 8))).priority(200);
                boolean farAccepted = Patric.register(farther);
                boolean nearAccepted = Patric.register(closer);
                accepted = farAccepted && nearAccepted;
                LOG.info("{} A REGISTER far={} near={}", PREFIX, farAccepted, nearAccepted);
            }
            case 2 -> {
                originalPath = cow.getNavigation().createPath(pos(8, 17), 0);
                if (originalPath == null || !originalPath.canReach() || originalPath.getNodeCount() == 0) {
                    throw new IllegalStateException("Vanilla createPath to (8,17) returned no reachable path");
                }
                originalPathCursor = originalPath.getNextNodeIndex();
                accepted = Patric.register(Patric.create(cow, originalPath).priority(200));
                LOG.info("{} B-PATH REGISTER {} cursor={} nodes={}", PREFIX, accepted,
                        originalPathCursor, originalPath.getNodeCount());
            }
            case 3 -> {
                accepted = Patric.register(Patric.create(cow, () -> {
                    supplierCalls++;
                    LOG.info("{} B-SUPPLIER FACTORY count={}", PREFIX, supplierCalls);
                    return cow.getNavigation().createPath(pos(17, 8), 0);
                }).priority(200));
                supplierWasLazy = supplierCalls == 0;
                LOG.info("{} B-SUPPLIER REGISTER {} lazy={}", PREFIX, accepted, supplierWasLazy);
            }
            case 4 -> {
                Path firstPath = cow.getNavigation().createPath(pos(14, 8), 0);
                if (firstPath == null || !firstPath.canReach() || firstPath.getNodeCount() == 0) {
                    throw new IllegalStateException("Vanilla createPath for session follow returned no path");
                }
                accepted = Patric.register(Patric.create(cow, (entity, session) -> {
                    sessionCalls++;
                    capturedSession = session;
                    LOG.info("{} C-SESSION CALLBACK count={}", PREFIX, sessionCalls);
                    session.follow(firstPath, 1.0)
                            .moveTo(pos(14, 14), 1.0)
                            .moveTo(Vec3.atBottomCenterOf(pos(8, 14)), 1.0);
                }).priority(200));
                LOG.info("{} C-SESSION REGISTER {}", PREFIX, accepted);
            }
            default -> throw new IllegalStateException("Unexpected stage " + stage);
        }
        if (!accepted) {
            throw new IllegalStateException("public Patric.register returned false");
        }
    }

    private void observeStage(ServerLevel level) {
        Path active = cow.getNavigation().getPath();
        boolean moving = active != null && !cow.getNavigation().isDone();
        if (stage == 2 && originalPath != null
                && originalPath.getNextNodeIndex() != originalPathCursor) {
            originalCursorIntact = false;
        }
        switch (stage) {
            case 1 -> observeBlockAndVec3(level, moving);
            case 2 -> observeNativePath(level, moving);
            case 3 -> observeSupplier(level, moving);
            case 4 -> observeSession(level, moving);
            default -> throw new IllegalStateException("Unexpected stage " + stage);
        }
    }

    private void observeBlockAndVec3(ServerLevel level, boolean moving) {
        BlockPos near = pos(12, 8);
        BlockPos far = pos(21, 8);
        if (moving && pathPointsTo(near)) {
            observed[0] = true;
        }
        if (observed[0] && at(near)) {
            visited[0] = true;
        }
        if (moving && pathPointsTo(far)) {
            if (!observed[0] || !visited[0]) {
                failStage(level, "farther BlockPos route began before closer Vec3 was reached; "
                        + diagnostics());
                return;
            }
            observed[1] = true;
        }
        if (observed[1] && at(far)) {
            visited[1] = true;
        }
        if (visited[1] && goalsCleared()) {
            if (++quietTicks >= 4) {
                passStage(level, "equal-priority-distance", "far registered first; near Vec3 target/reached="
                        + observed[0] + "/" + visited[0] + ", far BlockPos target/reached="
                        + observed[1] + "/" + visited[1] + ", ticks=" + stageTicks);
            }
        } else {
            quietTicks = 0;
        }
    }

    private void observeNativePath(ServerLevel level, boolean moving) {
        BlockPos destination = pos(8, 17);
        if (moving && pathPointsTo(destination)) {
            observed[0] = true;
        }
        if (observed[0] && movedFromStart() && at(destination)) {
            visited[0] = true;
        }
        if (!originalCursorIntact) {
            failStage(level, "input Path cursor advanced from " + originalPathCursor
                    + " to " + originalPath.getNextNodeIndex());
            return;
        }
        if (visited[0] && goalsCleared()) {
            if (++quietTicks >= 4) {
                passStage(level, "native-path-copy", "original cursor stayed=" + originalPathCursor
                        + "; active path observed=" + observed[0]
                        + "; real Cow arrived=" + visited[0] + "; ticks=" + stageTicks);
            }
        } else {
            quietTicks = 0;
        }
    }

    private void observeSupplier(ServerLevel level, boolean moving) {
        BlockPos destination = pos(17, 8);
        if (moving && pathPointsTo(destination)) {
            observed[0] = true;
        }
        if (observed[0] && movedFromStart() && at(destination)) {
            visited[0] = true;
        }
        if (visited[0] && goalsCleared()) {
            if (++quietTicks >= 4) {
                if (!supplierWasLazy || supplierCalls != 1) {
                    failStage(level, "Supplier<Path> factory should run once on demand, registeredLazy="
                            + supplierWasLazy + " calls=" + supplierCalls);
                } else {
                    passStage(level, "supplier-native-navigation", "supplierCalls=" + supplierCalls
                            + ", active path observed=" + observed[0]
                            + ", Cow reached=" + visited[0] + ", ticks=" + stageTicks);
                }
            }
        } else {
            quietTicks = 0;
        }
    }

    private void observeSession(ServerLevel level, boolean moving) {
        if (capturedSession != null && !sessionExpired) {
            // This runs in END_SERVER_TICK after the callback has returned.
            // Never instantiate a NavigationSession outside the public API.
            boolean followRejected = rejectsExpired(() -> capturedSession.follow(
                    cow.getNavigation().getPath(), 1.0));
            boolean blockRejected = rejectsExpired(() -> capturedSession.moveTo(pos(14, 14), 1.0));
            boolean vectorRejected = rejectsExpired(() -> capturedSession.moveTo(
                    Vec3.atBottomCenterOf(pos(8, 14)), 1.0));
            if (!followRejected || !blockRejected || !vectorRejected) {
                failStage(level, "expired NavigationSession accepted a command: follow="
                        + followRejected + " block=" + blockRejected + " vec=" + vectorRejected);
                return;
            }
            sessionExpired = true;
        }

        BlockPos[] destinations = {pos(14, 8), pos(14, 14), pos(8, 14)};
        for (int i = 0; i < destinations.length; i++) {
            if (moving && pathPointsTo(destinations[i])) {
                if (i > 0 && !visited[i - 1]) {
                    failStage(level, "session step " + i + " started before step " + (i - 1)
                            + " was reached; " + diagnostics());
                    return;
                }
                observed[i] = true;
            }
            if (observed[i] && at(destinations[i])) {
                visited[i] = true;
            }
        }
        if (visited[2] && goalsCleared()) {
            if (++quietTicks >= 4) {
                if (sessionCalls != 1 || !sessionExpired || !observed[0] || !observed[1]
                        || !observed[2] || !visited[0] || !visited[1]) {
                    failStage(level, "session callback/step contract was incomplete: " + diagnostics());
                } else {
                    passStage(level, "session-follow-block-vec", "callbackCalls=" + sessionCalls
                            + ", expiredThrows=" + sessionExpired
                            + ", activePathSteps=" + observed[0] + "/" + observed[1]
                            + "/" + observed[2] + ", each waypoint reached, ticks=" + stageTicks);
                }
            }
        } else {
            quietTicks = 0;
        }
    }

    private boolean pathPointsTo(BlockPos target) {
        Path current = cow.getNavigation().getPath();
        if (current == null || current.isDone()) {
            return false;
        }
        // Native moveTo(Path, speed) need not update getTargetPos(), and
        // createPath() can leave getTargetPos() referring to an earlier route.
        // The running Path's own target identifies the actual active segment.
        return target.equals(current.getTarget());
    }

    private boolean goalsCleared() {
        return cow.getGoalSelector().getAvailableGoals().stream()
                .noneMatch(wrapped -> PATRICK_GOAL.equals(wrapped.getGoal().getClass().getName()));
    }

    private static boolean rejectsExpired(Runnable command) {
        try {
            command.run();
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }

    private BlockPos pos(int x, int z) {
        return new BlockPos(x, worldY, z);
    }

    private boolean at(BlockPos destination) {
        Vec3 point = Vec3.atBottomCenterOf(destination);
        double dx = cow.getX() - point.x;
        double dz = cow.getZ() - point.z;
        return dx * dx + dz * dz <= 2.25 && Math.abs(cow.getY() - point.y) <= 1.5;
    }

    private boolean movedFromStart() {
        double dx = cow.getX() - (START_X + 0.5);
        double dz = cow.getZ() - (START_Z + 0.5);
        return dx * dx + dz * dz >= 1.0;
    }

    private String diagnostics() {
        if (cow == null) {
            return "cow=null";
        }
        Path nativePath = cow.getNavigation().getPath();
        return "pos=" + cow.position() + " target=" + cow.getNavigation().getTargetPos()
                + " nativeTarget=" + (nativePath == null ? null : nativePath.getTarget())
                + " path=" + (nativePath != null) + " isDone=" + cow.getNavigation().isDone()
                + " observed=" + observed[0] + "/" + observed[1] + "/" + observed[2]
                + " visited=" + visited[0] + "/" + visited[1] + "/" + visited[2]
                + " supplierCalls=" + supplierCalls + " sessionCalls=" + sessionCalls;
    }

    private String stageName() {
        return switch (stage) {
            case 1 -> "A-blockpos-vec3-ranking";
            case 2 -> "B-original-native-path";
            case 3 -> "B-supplier-native-path";
            case 4 -> "C-navigation-session";
            default -> "setup";
        };
    }

    private void passStage(ServerLevel level, String assertion, String details) {
        record(true, assertion, details);
        startStage(level, stage + 1);
    }

    private void failStage(ServerLevel level, String details) {
        record(false, stageName(), details);
        startStage(level, stage + 1);
    }

    private void record(boolean success, String name, String details) {
        String result = PREFIX + " " + (success ? "PASS " : "FAIL ") + name + ": " + details;
        results.add(result);
        if (success) {
            passes++;
            LOG.info(result);
        } else {
            failures++;
            LOG.error(result);
        }
    }

    private void discardCow() {
        if (cow != null && !cow.isRemoved()) {
            cow.discard();
        }
        cow = null;
    }

    private void onServerStopping(MinecraftServer server) {
        if (!done) {
            record(false, stageName(), "server stopped before runtime probe finished");
            finish(server.overworld());
        } else {
            discardCow();
        }
    }

    private void finish(ServerLevel level) {
        if (done) {
            return;
        }
        done = true;
        discardCow();
        for (BlockPos chunk : forcedChunks) {
            try {
                level.setChunkForced(chunk.getX(), chunk.getZ(), false);
            } catch (RuntimeException error) {
                LOG.warn("{} could not release forced chunk {}", PREFIX, chunk, error);
            }
        }
        forcedChunks.clear();
        String summary = PREFIX + " SUMMARY PASS=" + passes + " FAIL=" + failures;
        results.add(summary);
        if (failures == 0 && passes == STAGE_COUNT) {
            LOG.info(summary);
        } else {
            LOG.error(summary);
        }
        try {
            Files.writeString(OUTPUT, String.join(System.lineSeparator(), results)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
            LOG.info("{} wrote {}", PREFIX, OUTPUT.toAbsolutePath());
        } catch (IOException error) {
            LOG.error("{} could not write results file {}", PREFIX, OUTPUT, error);
        }
    }
}
