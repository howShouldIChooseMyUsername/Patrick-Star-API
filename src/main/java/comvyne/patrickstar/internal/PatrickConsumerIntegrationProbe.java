package comvyne.patrickstar.internal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in integration test against TWO separately packaged, server-loaded Fabric mods.
 *
 * Neither consumer directly references this probe. They independently react to the
 * tagged cow via Fabric events and call only the public Patrick Star API.
 */
final class PatrickConsumerIntegrationProbe {
    static final String PROBE_TAG = "patrick_dual_consumer_probe";
    private static final String ALPHA = "patrick_probe_alpha";
    private static final String BETA = "patrick_probe_beta";
    private static final String GOAL_CLASS = "comvyne.patrickstar.internal.PathDirector$MoveGoal";
    private static final Logger LOG = LoggerFactory.getLogger("PatrickConsumerIntegration");
    private static final Path OUTPUT = Path.of("patrick-dual-consumer-results.txt");
    private static final int START_X = 8;
    private static final int START_Z = 8;
    private static final int FIRST_X = START_X + 7;
    private static final int FINAL_X = START_X + 14;
    private static final int BETA_Z = START_Z + 8;
    private static final int TIMEOUT_TICKS = 700;

    private final List<String> results = new ArrayList<>();
    private Mob mob;
    private int y;
    private int ticks;
    private int warmupTicks;
    private int scenarioTicks;
    private int passes;
    private int failures;
    private int goalMax;
    private int finishTicks;
    private long firstAlphaTargetNanos;
    private long betaTargetDelayMillis = -1;
    private boolean initialized;
    private boolean done;
    private boolean sawAlpha;
    private boolean sawBeta;
    private boolean reachedBeta;
    private boolean sawAlphaResume;
    private boolean sawAlphaFinal;
    private boolean checkedRegistrations;

    static void initialize() {
        LOG.info("PCROSS ENABLED: two independently loaded Fabric consumer mods on one real Cow.");
        PatrickConsumerIntegrationProbe probe = new PatrickConsumerIntegrationProbe();
        ServerTickEvents.END_SERVER_TICK.register(probe::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Even an interrupted probe must not persist a tagged test Cow,
            // which could otherwise be rediscovered after the next restart.
            if (probe.mob != null && !probe.mob.isRemoved()) {
                probe.mob.discard();
            }
        });
    }

    private void tick(MinecraftServer server) {
        if (done) {
            return;
        }
        try {
            if (++ticks < 40) {
                return;
            }
            if (!initialized) {
                initialized = true;
                boolean loadedAlpha = FabricLoader.getInstance().isModLoaded(ALPHA);
                boolean loadedBeta = FabricLoader.getInstance().isModLoaded(BETA);
                record(loadedAlpha && loadedBeta, "two-loader-mods",
                        "alphaLoaded=" + loadedAlpha + " betaLoaded=" + loadedBeta);
                if (!loadedAlpha || !loadedBeta) {
                    finish();
                    return;
                }
                prepareArena(server.overworld());
                return;
            }
            if (mob == null) {
                if (++warmupTicks >= 30) {
                    spawn(server.overworld());
                }
                return;
            }
            scenarioTicks++;
            check(server.overworld());
        } catch (Throwable error) {
            LOG.error("PCROSS probe crashed", error);
            record(false, "uncaught-exception", error.getClass().getSimpleName() + ": " + error.getMessage());
            finish();
        }
    }

    private void prepareArena(ServerLevel level) {
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
        }
        LOG.info("PCROSS arena chunks forced and loaded; warming for 30 server ticks before Cow spawn.");
    }

    private void spawn(ServerLevel level) {
        y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, START_X, START_Z);
        Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        if (cow == null) {
            throw new IllegalStateException("Cannot create Cow");
        }
        cow.setPos(START_X + 0.5, y, START_Z + 0.5);
        cow.setPersistenceRequired();
        cow.addTag(PROBE_TAG);
        if (!level.addFreshEntity(cow)) {
            throw new IllegalStateException("Could not spawn Cow in test world");
        }
        mob = cow;
        LOG.info("PCROSS BEGIN Cow id={} uuid={} pos={}", mob.getId(), mob.getUUID(), mob.position());
    }

    private void check(ServerLevel level) {
        if (scenarioTicks >= TIMEOUT_TICKS) {
            record(false, "dual-mod-path-completion", "timeout ticks=" + scenarioTicks
                    + " tags=" + mob.entityTags()
                    + " alpha=" + sawAlpha + " beta=" + sawBeta + " betaVisited=" + reachedBeta
                    + " resumed=" + sawAlphaResume + " final=" + sawAlphaFinal
                    + " mob=" + mob.blockPosition()
                    + " activeTarget=" + mob.getNavigation().getTargetPos());
            finish();
            return;
        }
        if (level.getEntity(mob.getId()) != mob || mob.isRemoved() || !mob.isAlive()) {
            record(false, "cow-lifecycle", "Mob was lost during navigation at tick=" + scenarioTicks);
            finish();
            return;
        }

        if (!checkedRegistrations && mob.entityTags().contains("patrick_alpha_registered")
                && mob.entityTags().contains("patrick_beta_registered")) {
            checkedRegistrations = true;
            record(true, "independent-public-api-register",
                    "both external ModInitializers accepted on same Mob: " + mob.getUUID());
        }

        int goals = (int) mob.getGoalSelector().getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(goal -> GOAL_CLASS.equals(goal.getClass().getName()))
                .count();
        goalMax = Math.max(goalMax, goals);
        if (goals > 1) {
            record(false, "one-goal-per-mob", "multiple Patrick MOVE goals: " + goals);
            finish();
            return;
        }

        BlockPos target = mob.getNavigation().getTargetPos();
        if (scenarioTicks == 20 || scenarioTicks == 75 || scenarioTicks == 150 || scenarioTicks == 300) {
            LOG.info("PCROSS DIAG tick={} position={} target={} tags={} pathExists={} done={}",
                    scenarioTicks, mob.position(), target, mob.entityTags(),
                    mob.getNavigation().getPath() != null, mob.getNavigation().isDone());
        }
        if (!sawAlpha && targetIs(target, FIRST_X, START_Z)) {
            sawAlpha = true;
            firstAlphaTargetNanos = System.nanoTime();
            LOG.info("PCROSS ALPHA waypoint goal observed at tick={}", scenarioTicks);
        }
        if (!sawBeta && targetIs(target, START_X, BETA_Z)) {
            sawBeta = true;
            betaTargetDelayMillis = firstAlphaTargetNanos == 0 ? -1
                    : (System.nanoTime() - firstAlphaTargetNanos) / 1_000_000;
            record(sawAlpha && betaTargetDelayMillis >= 1550,
                    "high-priority-respects-alpha-lock",
                    "beta first target after " + betaTargetDelayMillis + "ms; alpha lock=1800ms");
        }
        if (sawBeta && near(START_X, BETA_Z)) {
            reachedBeta = true;
        }
        if (sawBeta && targetIs(target, FIRST_X, START_Z)) {
            sawAlphaResume = true;
        }
        if (sawBeta && targetIs(target, FINAL_X, START_Z)) {
            sawAlphaFinal = true;
        }
        if (sawAlphaFinal && near(FINAL_X, START_Z) && reachedBeta) {
            finishTicks++;
            if (finishTicks >= 8) {
                record(checkedRegistrations
                                && mob.entityTags().contains("patrick_alpha_factory")
                                && mob.entityTags().contains("patrick_beta_factory"),
                        "independent-consumer-factories",
                        "alphaFactory=" + mob.entityTags().contains("patrick_alpha_factory")
                                + " betaFactory=" + mob.entityTags().contains("patrick_beta_factory"));
                record(sawAlpha && sawBeta && reachedBeta && sawAlphaResume && sawAlphaFinal,
                        "dual-mod-route-order",
                        "alphaWaypoint->betaDestination->alphaResume->alphaFinal, ticks=" + scenarioTicks);
                record(goalMax == 1 && goals == 0, "single-goal-and-cleanup",
                        "maxPatrickGoals=" + goalMax + " remaining=" + goals);
                record(true, "dual-mod-path-completion",
                        "Cow arrived at " + mob.blockPosition() + " after " + scenarioTicks + " ticks");
                finish();
            }
        } else {
            finishTicks = 0;
        }
    }

    private boolean targetIs(BlockPos target, int x, int z) {
        return target != null && target.getX() == x && target.getZ() == z;
    }

    private boolean near(int x, int z) {
        Vec3 position = mob.position();
        double dx = position.x - (x + 0.5);
        double dz = position.z - (z + 0.5);
        return dx * dx + dz * dz <= 2.25 && Math.abs(position.y - y) <= 1.5;
    }

    private void record(boolean passed, String name, String detail) {
        if (passed) {
            passes++;
        } else {
            failures++;
        }
        String line = "PCROSS " + (passed ? "PASS " : "FAIL ") + name + ": " + detail;
        results.add(line);
        if (passed) {
            LOG.info(line);
        } else {
            LOG.error(line);
        }
    }

    private void finish() {
        done = true;
        if (mob != null && !mob.isRemoved()) {
            mob.discard();
        }
        String summary = "PCROSS SUMMARY PASS=" + passes + " FAIL=" + failures;
        results.add(summary);
        LOG.info(summary);
        try {
            Files.writeString(OUTPUT, String.join(System.lineSeparator(), results)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOG.error("PCROSS could not write report: {}", OUTPUT, error);
        }
    }
}
