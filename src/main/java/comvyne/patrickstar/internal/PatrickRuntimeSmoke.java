package comvyne.patrickstar.internal;

import comvyne.patrickstar.api.Patric;
import comvyne.patrickstar.api.PatricPath;
import comvyne.patrickstar.api.PatricPathRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explicitly opt-in, dedicated-server integration probe with REAL Mob navigation.
 * Create run/patrick-runtime-smoke.enabled to activate. This class is never
 * called in an ordinary Minecraft installation.
 */
final class PatrickRuntimeSmoke {
    private static final Logger LOG = LoggerFactory.getLogger("PatrickRuntimeSmoke");
    private static final int LOCK_MILLIS = 1800;
    private static final int BOOT_TICKS = 40;
    private static final Path RESULT = Path.of("patrick-runtime-smoke-results.txt");

    private final List<String> results = new ArrayList<>();
    private int ticks;
    private int stage;
    private int stageTicks;
    private int passes;
    private int failures;
    private Mob mob;
    private int baseX;
    private int baseY;
    private int baseZ;
    private boolean first;
    private boolean second;
    private boolean third;
    private boolean stageRequestsRegistered;
    private boolean highRegistered;
    private long lockStartNanos;
    private long highTargetDelayMillis = -1;
    private net.minecraft.world.level.pathfinder.Path originalNavigationPath;
    private int invalidHighRegisteredAt;
    private final AtomicInteger callbacksA = new AtomicInteger();
    private final AtomicInteger callbacksB = new AtomicInteger();
    private final AtomicInteger callbacksC = new AtomicInteger();

    static void initialize() {
        PatrickRuntimeSmoke probe = new PatrickRuntimeSmoke();
        LOG.info("PTEST ENABLED: isolated dedicated-server Mob integration scenarios.");
        ServerTickEvents.END_SERVER_TICK.register(probe::tick);
    }

    private void tick(MinecraftServer server) {
        if (stage == 8) {
            return;
        }
        try {
            if (++ticks < BOOT_TICKS) {
                return;
            }
            if (stage == 0) {
                ServerLevel level = server.overworld();
                for (int cx = 0; cx <= 3; cx++) {
                    for (int cz = 0; cz <= 3; cz++) {
                        level.setChunkForced(cx, cz, true);
                    }
                }
                // The extra marker isolates the later regression cases without
                // repeating the long preemption scenario during debugging.
                startStage(level, Files.isRegularFile(Path.of("patrick-runtime-smoke-fast.enabled")) ? 5 : 1);
                return;
            }
            stageTicks++;
            checkStage(server.overworld());
        } catch (Throwable error) {
            LOG.error("PTEST exception in stage {}", stage, error);
            record(false, "stage-" + stage, "unexpected " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            if (stage >= 7) {
                finish();
            } else {
                startStage(server.overworld(), stage + 1);
            }
        }
    }

    private void startStage(ServerLevel level, int next) {
        if (mob != null && !mob.isRemoved()) {
            mob.discard();
        }
        stage = next;
        stageTicks = 0;
        first = false;
        second = false;
        third = false;
        highRegistered = false;
        stageRequestsRegistered = false;
        highTargetDelayMillis = -1;
        originalNavigationPath = null;
        invalidHighRegisteredAt = 0;
        callbacksA.set(0);
        callbacksB.set(0);
        callbacksC.set(0);
        if (stage > 7) {
            finish();
            return;
        }

        baseX = (stage == 1 || stage == 3) ? 8 : 24;
        baseZ = (stage == 1 || stage == 2) ? 8 : 24;
        mob = spawn(level, baseX, baseZ);
        baseY = mob.blockPosition().getY();
        LOG.info("PTEST BEGIN stage={} mob={} pos={}", stage, mob.getUUID(), mob.position());

    }

    private void registerStageRequests() {
        switch (stage) {
            case 1 -> {
                // Three independent consumers submit requests in reverse priority order.
                boolean a = Patric.register(Patric.create(mob, e -> {
                    callbacksA.incrementAndGet();
                    return route(baseX, baseZ + 6, 1.15);
                }).priority(100));
                boolean b = Patric.register(Patric.create(mob, e -> {
                    callbacksB.incrementAndGet();
                    return route(baseX + 6, baseZ + 6, 1.15);
                }).priority(200));
                boolean c = Patric.register(Patric.create(mob, e -> {
                    callbacksC.incrementAndGet();
                    return route(baseX + 6, baseZ, 1.15);
                }).priority(300));
                record(a && b && c, "register-three", "accepted=" + a + "," + b + "," + c);
            }
            case 2 -> {
                boolean accepted = Patric.register(Patric.create(mob, e ->
                        PatricPath.builder(pos(baseX + 6, baseZ + 6))
                                .waypoint(Vec3.atBottomCenterOf(pos(baseX + 6, baseZ)))
                                .speed(1.15).reachable(true).build()
                ).priority(123));
                record(accepted, "waypoint-register", "accepted=" + accepted);
            }
            case 3 -> {
                boolean a = Patric.register(Patric.create(mob, e -> {
                    callbacksA.incrementAndGet();
                    return null;
                }).priority(300));
                boolean b = Patric.register(Patric.create(mob, e -> {
                    callbacksB.incrementAndGet();
                    throw new IllegalStateException("intentional runtime-smoke failure");
                }).priority(200));
                boolean c = Patric.register(Patric.create(mob, e -> {
                    callbacksC.incrementAndGet();
                    return route(baseX + 6, baseZ, 1.15);
                }).priority(100));
                record(a && b && c, "fault-isolation-register", "accepted=" + a + "," + b + "," + c);
            }
            case 4 -> {
                boolean accepted = Patric.register(Patric.create(mob, e -> {
                    callbacksA.incrementAndGet();
                    return route(baseX + 14, baseZ, 0.72);
                }).priority(100).lock(LOCK_MILLIS));
                record(accepted, "lock-low-register", "accepted=" + accepted);
            }
            case 5 -> {
                boolean accepted = Patric.register(Patric.create(mob, e -> {
                    callbacksA.incrementAndGet();
                    return route(baseX + 10, baseZ, 0.75);
                }).priority(100).lock(0));
                record(accepted, "invalid-high-low-register", "accepted=" + accepted);
            }
            case 6 -> {
                PatricPathRegistry neverRegistered = Patric.create(mob, e -> {
                    callbacksA.incrementAndGet();
                    return route(baseX + 5, baseZ, 1.0);
                });
                record(neverRegistered != null && !Patric.register(null),
                        "create-without-register", "created but intentionally not submitted");
            }
            case 7 -> {
                boolean accepted = Patric.register(Patric.create(mob, e -> {
                    callbacksA.incrementAndGet();
                    return route(baseX + 14, baseZ, 0.65);
                }).priority(100));
                record(accepted, "unload-register", "accepted=" + accepted);
            }
            default -> throw new IllegalStateException("unknown stage " + stage);
        }
    }

    private void checkStage(ServerLevel level) {
        if (!stageRequestsRegistered) {
            if (level.getEntity(mob.getId()) != mob) {
                if (stageTicks >= 40) {
                    record(false, "spawn-index", "entity absent from ServerLevel index after 40 ticks; id="
                            + mob.getId() + " alive=" + mob.isAlive());
                    startStage(level, stage + 1);
                }
                return;
            }
            stageRequestsRegistered = true;
            LOG.info("PTEST stage={} entity indexed after {} ticks; id={}", stage, stageTicks, mob.getId());
            registerStageRequests();
            stageTicks = 0;
            return;
        }
        BlockPos target = mob.getNavigation().getTargetPos();
        switch (stage) {
            case 1 -> {
                if (!first && targetIs(target, baseX + 6, baseZ)) {
                    first = true;
                    LOG.info("PTEST STAGE1 target high at tick {}", stageTicks);
                }
                if (!second && targetIs(target, baseX + 6, baseZ + 6)) {
                    if (!first) {
                        record(false, "priority-order", "middle target observed before high");
                    }
                    second = true;
                    LOG.info("PTEST STAGE1 target middle at tick {}", stageTicks);
                }
                if (!third && targetIs(target, baseX, baseZ + 6)) {
                    if (!first || !second) {
                        record(false, "priority-order", "low target observed too early");
                    }
                    third = true;
                    LOG.info("PTEST STAGE1 target low at tick {}", stageTicks);
                }
                if (first && second && third && at(baseX, baseZ + 6) && stageTicks > 20) {
                    record(callbacksA.get() == 1 && callbacksB.get() == 1 && callbacksC.get() == 1,
                            "priority-sequence", "all-targets=" + first + "/" + second + "/" + third
                                    + " callbacks=" + callbacksA + "/" + callbacksB + "/" + callbacksC
                                    + " elapsedTicks=" + stageTicks);
                    startStage(level, 2);
                } else if (stageTicks >= 400) {
                    record(false, "priority-sequence", "timeout targets=" + first + "/" + second + "/" + third
                            + " mob=" + mob.blockPosition() + " navigationTarget=" + target);
                    startStage(level, 2);
                }
            }
            case 2 -> {
                if (targetIs(target, baseX + 6, baseZ)) {
                    first = true;
                }
                if (at(baseX + 6, baseZ)) {
                    second = true;
                }
                if (targetIs(target, baseX + 6, baseZ + 6)) {
                    third = true;
                }
                if (first && second && third && at(baseX + 6, baseZ + 6)) {
                    record(true, "waypoint-navigation", "waypoint and final reached; elapsedTicks=" + stageTicks);
                    startStage(level, 3);
                } else if (stageTicks >= 350) {
                    record(false, "waypoint-navigation", "timeout target1/visited/target2=" + first
                            + "/" + second + "/" + third + " mob=" + mob.blockPosition()
                            + " navigationTarget=" + target);
                    startStage(level, 3);
                }
            }
            case 3 -> {
                if (at(baseX + 6, baseZ) && stageTicks > 15) {
                    record(callbacksA.get() == 1 && callbacksB.get() == 1 && callbacksC.get() == 1,
                            "fault-isolation", "null/throw ignored, next route reached; callbacks="
                                    + callbacksA + "/" + callbacksB + "/" + callbacksC);
                    startStage(level, 4);
                } else if (stageTicks >= 260) {
                    record(false, "fault-isolation", "timeout callbacks=" + callbacksA + "/" + callbacksB
                            + "/" + callbacksC + " target=" + target);
                    startStage(level, 4);
                }
            }
            case 4 -> {
                if (!highRegistered && targetIs(target, baseX + 14, baseZ)) {
                    first = true;
                    lockStartNanos = System.nanoTime();
                    highRegistered = Patric.register(Patric.create(mob, e -> {
                        callbacksB.incrementAndGet();
                        return route(baseX, baseZ + 8, 1.15);
                    }).priority(500));
                    record(highRegistered, "lock-high-register", "higher request after low started");
                    LOG.info("PTEST STAGE4 low active, high registered after {} ticks", stageTicks);
                }
                if (highRegistered && highTargetDelayMillis < 0 && targetIs(target, baseX, baseZ + 8)) {
                    second = true;
                    highTargetDelayMillis = (System.nanoTime() - lockStartNanos) / 1_000_000;
                    record(highTargetDelayMillis >= LOCK_MILLIS - 30,
                            "lock-min-duration", "high target first seen after " + highTargetDelayMillis
                                    + "ms, minimum=" + LOCK_MILLIS + "ms");
                }
                if (second && at(baseX, baseZ + 8)) {
                    third = true;
                }
                if (third && at(baseX + 14, baseZ) && stageTicks > 40) {
                    record(first && second && highTargetDelayMillis >= LOCK_MILLIS - 30
                                    && callbacksA.get() == 1 && callbacksB.get() == 1,
                            "preempt-resume", "high target reached and low resumed; elapsedTicks="
                                    + stageTicks + " delay=" + highTargetDelayMillis + "ms");
                    startStage(level, 5);
                } else if (stageTicks >= 750) {
                    record(false, "preempt-resume", "timeout targets=" + first + "/" + second + "/" + third
                            + " highDelay=" + highTargetDelayMillis + " mob=" + mob.blockPosition()
                            + " navigationTarget=" + target + " callbacks=" + callbacksA + "/" + callbacksB);
                    startStage(level, 5);
                }
            }
            case 5 -> {
                if (!highRegistered && targetIs(target, baseX + 10, baseZ)) {
                    originalNavigationPath = mob.getNavigation().getPath();
                    invalidHighRegisteredAt = stageTicks;
                    boolean nullAccepted = Patric.register(Patric.create(mob, e -> {
                        callbacksB.incrementAndGet();
                        return null;
                    }).priority(500));
                    boolean throwAccepted = Patric.register(Patric.create(mob, e -> {
                        callbacksC.incrementAndGet();
                        throw new IllegalStateException("invalid-high consumer");
                    }).priority(400));
                    highRegistered = nullAccepted && throwAccepted;
                    record(highRegistered, "invalid-high-register", "registered both invalid higher requests");
                }
                if (highRegistered && !first && stageTicks >= invalidHighRegisteredAt + 4) {
                    first = true;
                    record(originalNavigationPath != null
                                    && mob.getNavigation().getPath() == originalNavigationPath
                                    && targetIs(target, baseX + 10, baseZ),
                            "invalid-high-no-interrupt", "sameNavigationPath="
                                    + (mob.getNavigation().getPath() == originalNavigationPath)
                                    + " target=" + target);
                }
                if (first && at(baseX + 10, baseZ)) {
                    record(callbacksA.get() == 1 && callbacksB.get() == 1 && callbacksC.get() == 1,
                            "invalid-high-low-completes", "reached low after invalid high callbacks="
                                    + callbacksA + "/" + callbacksB + "/" + callbacksC);
                    startStage(level, 6);
                } else if (stageTicks >= 350) {
                    record(false, "invalid-high-low-completes", "timeout mob=" + mob.blockPosition()
                            + " target=" + target + " callbacks=" + callbacksA + "/" + callbacksB
                            + "/" + callbacksC);
                    startStage(level, 6);
                }
            }
            case 6 -> {
                if (stageTicks >= 45) {
                    long ourGoals = mob.getGoalSelector().getAvailableGoals().stream()
                            .filter(g -> g.getGoal().getClass().getName()
                                    .equals("comvyne.patrickstar.internal.PathDirector$MoveGoal"))
                            .count();
                    record(callbacksA.get() == 0 && ourGoals == 0,
                            "create-does-not-schedule", "factoryCalls=" + callbacksA
                                    + " installedPatrickGoals=" + ourGoals);
                    startStage(level, 7);
                }
            }
            case 7 -> {
                if (!first && targetIs(target, baseX + 14, baseZ)) {
                    first = true;
                    mob.discard();
                    LOG.info("PTEST STAGE7 discard moving mob id={}", mob.getId());
                }
                if (first && stageTicks >= 5) {
                    boolean rejected = !Patric.register(Patric.create(mob, e -> route(baseX, baseZ, 1.0)));
                    long ourGoals = mob.getGoalSelector().getAvailableGoals().stream()
                            .filter(g -> g.getGoal().getClass().getName()
                                    .equals("comvyne.patrickstar.internal.PathDirector$MoveGoal"))
                            .count();
                    record(mob.isRemoved() && rejected && level.getEntity(mob.getId()) == null
                                    && ourGoals == 0,
                            "unload-cleanup", "removed=" + mob.isRemoved() + " rejected=" + rejected
                                    + " indexed=" + (level.getEntity(mob.getId()) != null)
                                    + " remainingPatrickGoals=" + ourGoals);
                    finish();
                } else if (stageTicks >= 180) {
                    record(false, "unload-cleanup", "could not acquire moving target before timeout");
                    finish();
                }
            }
        }
    }

    private Mob spawn(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        EntityType<?> cowType = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.withDefaultNamespace("cow"));
        Mob cow = cowType == null ? null : (Mob) cowType.create(level, EntitySpawnReason.COMMAND);
        if (cow == null) {
            throw new IllegalStateException("EntityType.COW.create returned null");
        }
        cow.setPos(x + .5, y, z + .5);
        cow.setPersistenceRequired();
        if (!level.addFreshEntity(cow)) {
            throw new IllegalStateException("could not add cow to ServerLevel");
        }
        return cow;
    }

    private PatricPath route(int x, int z, double speed) {
        return PatricPath.builder(pos(x, z)).speed(speed).reachable(true).build();
    }

    private BlockPos pos(int x, int z) {
        return new BlockPos(x, baseY, z);
    }

    private boolean targetIs(BlockPos target, int x, int z) {
        return target != null && target.getX() == x && target.getZ() == z;
    }

    private boolean at(int x, int z) {
        return mob.distanceToSqr(x + 0.5, baseY, z + 0.5) <= 2.25;
    }

    private void record(boolean pass, String test, String detail) {
        if (pass) {
            passes++;
        } else {
            failures++;
        }
        String line = "PTEST " + (pass ? "PASS " : "FAIL ") + test + ": " + detail;
        results.add(line);
        if (pass) {
            LOG.info(line);
        } else {
            LOG.error(line);
        }
    }

    private void finish() {
        stage = 8;
        if (mob != null && !mob.isRemoved()) {
            mob.discard();
        }
        String report = String.join(System.lineSeparator(), results)
                + System.lineSeparator() + "PTEST SUMMARY PASS=" + passes + " FAIL=" + failures
                + System.lineSeparator();
        LOG.info("PTEST SUMMARY PASS={} FAIL={}", passes, failures);
        try {
            Files.writeString(RESULT, report, StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOG.error("PTEST could not write {}", RESULT, error);
        }
    }
}
