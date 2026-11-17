package comvyne.patrickstar.internal;

import comvyne.patrickstar.api.Patrick;
import comvyne.patrickstar.api.PatrickPath;
import comvyne.patrickstar.api.PatrickPathRegistry;
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
 * Opt-in, real-Cow acceptance tests for the persistent/one-shot API.
 * Enabled only by a marker in the dedicated server's working directory.
 */
final class PatrickLifecycleRuntimeProbe {
    private static final Logger LOG = LoggerFactory.getLogger("PatrickLifecycleRuntime");
    private static final String PREFIX = "PLIFECYCLE";
    private static final String GOAL_NAME = "comvyne.patrickstar.internal.PathDirector$MoveGoal";
    private static final java.nio.file.Path OUTPUT =
            java.nio.file.Path.of("patrick-lifecycle-runtime-results.txt");
    private static final int STAGES = 4;
    private static final int TIMEOUT = 500;

    private final List<String> results = new ArrayList<>();
    private final List<BlockPos> forcedChunks = new ArrayList<>();
    private int ticks;
    private int warmTicks;
    private int stage;
    private int stageTicks;
    private int passes;
    private int failures;
    private int worldY;
    private int factoryCalls;
    private boolean ready;
    private boolean submitted;
    private boolean shotSubmitted;
    private boolean sawShot;
    private boolean sawBaseline;
    private boolean nearVisited;
    private boolean released;
    private boolean done;
    private Cow cow;
    private PatrickPathRegistry persistent;

    static void initialize() {
        if (!Files.isRegularFile(java.nio.file.Path.of("patrick-lifecycle-runtime.enabled"))) {
            return;
        }
        PatrickLifecycleRuntimeProbe probe = new PatrickLifecycleRuntimeProbe();
        ServerTickEvents.END_SERVER_TICK.register(probe::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(probe::stopping);
        LOG.info("{} ENABLED: persistent and one-shot arbitration on real Cow", PREFIX);
    }

    private void tick(MinecraftServer server) {
        if (done || ++ticks < 40) {
            return;
        }
        ServerLevel level = server.overworld();
        try {
            if (!ready) {
                prepareArena(level);
                ready = true;
                return;
            }
            if (++warmTicks < 30) {
                return;
            }
            if (stage == 0) {
                nextStage(level);
                return;
            }
            ++stageTicks;
            if (cow == null || cow.isRemoved() || !cow.isAlive()
                    || cow.level() != level) {
                fail(level, "Cow was lost");
                return;
            }
            if (!submitted) {
                if (level.getEntity(cow.getId()) != cow || cow.tickCount < 12 || !cow.onGround()) {
                    if (stageTicks > 100) {
                        fail(level, "Cow never became indexed and grounded");
                    }
                    return;
                }
                submit();
                submitted = true;
                stageTicks = 0;
                return;
            }
            if (stageTicks > TIMEOUT) {
                fail(level, "timeout at " + cow.position() + ", navigation=" + target()
                        + " shotSubmitted=" + shotSubmitted + " sawShot=" + sawShot
                        + " sawBaseline=" + sawBaseline + " nearVisited=" + nearVisited
                        + " released=" + released + " factoryCalls=" + factoryCalls);
                return;
            }
            switch (stage) {
                case 1 -> verifyInterruptAndResume(level);
                case 2 -> verifyDeferredShot(level);
                case 3 -> verifyShotRanking(level);
                case 4 -> verifyPersistentReevaluation(level);
                default -> throw new IllegalStateException("Unexpected stage " + stage);
            }
        } catch (Throwable failure) {
            LOG.error("{} unexpected failure in stage {}", PREFIX, stage, failure);
            fail(level, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void submit() {
        switch (stage) {
            case 1 -> {
                persistent = Patrick.create(cow, pos(24, 8))
                        .priority(1000).deferOneShot(false);
                check(Patrick.register(persistent), "persistent register failed");
            }
            case 2 -> {
                persistent = Patrick.create(cow, pos(20, 8))
                        .priority(1000).deferOneShot(true);
                check(Patrick.register(persistent), "deferred persistent register failed");
            }
            case 3 -> {
                // Farther first: same-priority shots must still choose the closer route.
                check(Patrick.oneShot(Patrick.create(cow, pos(24, 8)).priority(100)),
                        "far shot rejected");
                check(Patrick.oneShot(Patrick.create(cow, pos(14, 8)).priority(100)),
                        "near shot rejected");
            }
            case 4 -> {
                persistent = Patrick.create(cow, entity -> {
                    factoryCalls++;
                    return PatrickPath.builder(factoryCalls == 1 ? pos(14, 8) : pos(14, 14))
                            .build();
                }).priority(200);
                check(Patrick.register(persistent), "dynamic persistent register failed");
            }
            default -> throw new IllegalStateException("Unexpected stage " + stage);
        }
    }

    private void verifyInterruptAndResume(ServerLevel level) {
        // Let vanilla navigation progress before interrupting it: this also
        // verifies that we interrupt an established route, not just its setup.
        if (!shotSubmitted && targetIs(24, 8) && cow.getX() > 12.0) {
            check(Patrick.oneShot(Patrick.create(cow, pos(14, 14)).priority(1)),
                    "low-priority interrupting shot rejected");
            shotSubmitted = true;
            LOG.info("{} stage=1 one-shot queued during baseline at {}", PREFIX, cow.position());
        }
        if (!sawShot && at(24, 8)) {
            sawBaseline = true;
        }
        // Vanilla's traversed Path target can be a partial/nearby node. Observe
        // real motion toward the side destination instead of only an exact
        // Path.getTarget() match to avoid false negatives.
        if (shotSubmitted && cow.getZ() > 11.0 && !sawShot) {
            check(!sawBaseline, "passive route finished before interrupting shot");
            LOG.info("{} stage=1 observed one-shot movement at {}", PREFIX, cow.position());
            sawShot = true;
        }
        if (sawShot && at(14, 14)) {
            if (!nearVisited) {
                LOG.info("{} stage=1 one-shot destination reached at {}", PREFIX, cow.position());
            }
            nearVisited = true;
        }
        if (nearVisited && at(24, 8) && !released) {
            check(Patrick.unregister(persistent), "could not unregister resumed passive");
            released = true;
        }
        if (released && goalCleared()) {
            pass(level, "non-deferred shot interrupted, passive resumed, unregister removed goal");
        }
    }

    private void verifyDeferredShot(ServerLevel level) {
        if (!shotSubmitted && targetIs(20, 8)) {
            check(Patrick.oneShot(Patrick.create(cow, pos(20, 17)).priority(65535)),
                    "deferred high-priority shot rejected");
            shotSubmitted = true;
        }
        if (at(20, 8)) {
            sawBaseline = true;
        }
        if (targetIs(20, 17)) {
            check(sawBaseline, "deferred shot interrupted the current passive route");
            sawShot = true;
        }
        if (sawBaseline && sawShot && at(20, 17) && !released) {
            check(Patrick.unregister(persistent), "persistent registration vanished after shot");
            released = true;
        }
        if (released && goalCleared()) {
            pass(level, "deferred shot waited for completion and passive remained registered");
        }
    }

    private void verifyShotRanking(ServerLevel level) {
        if (targetIs(24, 8) && !nearVisited) {
            check(false, "far one-shot started before closer equal-priority one-shot");
        }
        if (at(14, 8)) {
            nearVisited = true;
        }
        if (nearVisited && targetIs(24, 8)) {
            sawShot = true;
        }
        if (sawShot && at(24, 8) && goalCleared()) {
            pass(level, "two one-shots ran nearest first then farther, with no retained goal");
        }
    }

    private void verifyPersistentReevaluation(ServerLevel level) {
        if (at(14, 8)) {
            nearVisited = true;
        }
        if (nearVisited && factoryCalls >= 2 && at(14, 14) && !released) {
            check(Patrick.unregister(persistent), "dynamic persistent unregister failed");
            released = true;
        }
        if (released && goalCleared()) {
            pass(level, "dynamic passive factory recalculated and cancelled after two routes");
        }
    }

    private void prepareArena(ServerLevel level) {
        for (int cx = 0; cx <= 1; cx++) {
            for (int cz = 0; cz <= 1; cz++) {
                if (level.setChunkForced(cx, cz, true)) {
                    forcedChunks.add(new BlockPos(cx, 0, cz));
                }
                level.getChunk(cx, cz);
            }
        }
        worldY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 8, 8);
        if (worldY < level.getMinY() + 1 || worldY + 3 > level.getMaxY()) {
            throw new IllegalStateException("Arena Y out of world height limits: " + worldY);
        }
        for (int x = 4; x <= 27; x++) {
            for (int z = 4; z <= 27; z++) {
                level.setBlock(pos(x, z).below(), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(pos(x, z).above(dy), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private void nextStage(ServerLevel level) {
        discardCow();
        if (++stage > STAGES) {
            finish(level);
            return;
        }
        stageTicks = 0;
        submitted = false;
        shotSubmitted = false;
        sawShot = false;
        sawBaseline = false;
        nearVisited = false;
        released = false;
        factoryCalls = 0;
        persistent = null;
        Cow fresh = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        if (fresh == null) {
            throw new IllegalStateException("Could not create Cow");
        }
        fresh.setPos(8.5, worldY, 8.5);
        fresh.setPersistenceRequired();
        fresh.getGoalSelector().removeAllGoals(goal -> true);
        check(level.addFreshEntity(fresh), "Could not add Cow to level");
        cow = fresh;
        LOG.info("{} BEGIN stage={} Cow={}", PREFIX, stage, cow.getUUID());
    }

    private void pass(ServerLevel level, String details) {
        record(true, details);
        nextStage(level);
    }

    private void fail(ServerLevel level, String details) {
        record(false, details);
        nextStage(level);
    }

    private void record(boolean success, String details) {
        String line = PREFIX + (success ? " PASS " : " FAIL ")
                + "stage=" + stage + " " + details;
        results.add(line);
        if (success) {
            passes++;
            LOG.info(line);
        } else {
            failures++;
            LOG.error(line);
        }
    }

    private void check(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }

    private BlockPos pos(int x, int z) {
        return new BlockPos(x, worldY, z);
    }

    private boolean at(int x, int z) {
        Vec3 point = Vec3.atBottomCenterOf(pos(x, z));
        double dx = cow.getX() - point.x;
        double dz = cow.getZ() - point.z;
        return dx * dx + dz * dz <= 2.25 && Math.abs(cow.getY() - point.y) <= 1.5;
    }

    private BlockPos target() {
        Path path = cow.getNavigation().getPath();
        return path != null && !path.isDone() ? path.getTarget() : null;
    }

    private boolean targetIs(int x, int z) {
        return pos(x, z).equals(target());
    }

    private boolean goalCleared() {
        return cow.getGoalSelector().getAvailableGoals().stream()
                .noneMatch(goal -> GOAL_NAME.equals(goal.getGoal().getClass().getName()));
    }

    private void discardCow() {
        if (cow != null && !cow.isRemoved()) {
            cow.discard();
        }
        cow = null;
    }

    private void stopping(MinecraftServer server) {
        if (!done) {
            record(false, "server stopped before probe completed");
            finish(server.overworld());
        }
    }

    private void finish(ServerLevel level) {
        if (done) {
            return;
        }
        done = true;
        discardCow();
        for (BlockPos chunk : forcedChunks) {
            level.setChunkForced(chunk.getX(), chunk.getZ(), false);
        }
        forcedChunks.clear();
        String summary = PREFIX + " SUMMARY PASS=" + passes + " FAIL=" + failures;
        results.add(summary);
        if (failures == 0 && passes == STAGES) {
            LOG.info(summary);
        } else {
            LOG.error(summary);
        }
        try {
            Files.writeString(OUTPUT, String.join(System.lineSeparator(), results)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOG.error("{} could not write probe results", PREFIX, error);
        }
    }
}
