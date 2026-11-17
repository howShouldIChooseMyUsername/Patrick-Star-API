package comvyne.patrickstar.consumers;

import comvyne.patrickstar.api.Patrick;
import comvyne.patrickstar.api.PatrickPath;
import comvyne.patrickstar.api.PatrickPathRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Independent Fabric consumer: competes only after Alpha's first navigation leg starts. */
public final class BetaConsumerMod implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("patrick_probe_beta");
    private static final String PROBE_TAG = "patrick_dual_consumer_probe";
    private static final String REGISTERED_TAG = "patrick_beta_registered";
    private static final String FACTORY_TAG = "patrick_beta_factory";

    private final Map<Cow, ObservedCow> observed = new IdentityHashMap<>();

    @Override
    public void onInitialize() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Cow cow && cow.entityTags().contains(PROBE_TAG)) {
                observed.putIfAbsent(cow, new ObservedCow(cow, level, cow.blockPosition().immutable()));
                LOG.info("PCONSUMER BETA LOAD cow={} startBlock={} position={}",
                        cow.getUUID(), cow.blockPosition(), cow.position());
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Cow cow) {
                ObservedCow state = observed.get(cow);
                if (state != null && state.level == level) {
                    observed.remove(cow);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> observed.clear());
    }

    private void onServerTick(MinecraftServer server) {
        for (ObservedCow state : List.copyOf(observed.values())) {
            Cow cow = state.cow;
            if (observed.get(cow) != state || state.attempted) {
                continue;
            }
            try {
                if (cow.isRemoved() || !cow.isAlive() || cow.level() != state.level
                        || !cow.entityTags().contains(PROBE_TAG)) {
                    observed.remove(cow);
                    continue;
                }
                if (state.level.getServer() != server || state.level.getEntity(cow.getId()) != cow) {
                    continue;
                }

                // Observe an actual Alpha navigation target rather than just its registration tag.
                BlockPos firstAlphaWaypoint = state.start.offset(7, 0, 0);
                if (!firstAlphaWaypoint.equals(cow.getNavigation().getTargetPos())) {
                    if (++state.waitTicks == 20 || state.waitTicks == 100 || state.waitTicks == 300) {
                        LOG.info("PCONSUMER BETA WAIT tick={} alphaWaypoint={} navTarget={} "
                                        + "alphaRegistered={} pathExists={} currentPos={}",
                                state.waitTicks, firstAlphaWaypoint,
                                cow.getNavigation().getTargetPos(),
                                cow.entityTags().contains("patrick_alpha_registered"),
                                cow.getNavigation().getPath() != null, cow.position());
                    }
                    continue;
                }

                state.attempted = true;
                BlockPos destination = state.start.offset(0, 0, 8);
                PatrickPathRegistry request = Patrick.create(cow, mob -> {
                    state.factoryCalls++;
                    mob.addTag(FACTORY_TAG);
                    LOG.info("PCONSUMER BETA FACTORY count={}", state.factoryCalls);
                    return PatrickPath.builder(destination)
                            .speed(1.15)
                            .reachable(true)
                            .build();
                }).priority(500);
                boolean accepted = Patrick.oneShot(request);
                if (accepted) {
                    cow.addTag(REGISTERED_TAG);
                }
                LOG.info("PCONSUMER BETA REGISTER {}", accepted);
            } catch (RuntimeException ex) {
                state.attempted = true;
                LOG.warn("PCONSUMER BETA ERROR mob={} type={}", cow.getUUID(),
                        ex.getClass().getSimpleName());
            }
        }
    }

    private static final class ObservedCow {
        final Cow cow;
        final ServerLevel level;
        final BlockPos start;
        boolean attempted;
        int waitTicks;
        int factoryCalls;

        ObservedCow(Cow cow, ServerLevel level, BlockPos start) {
            this.cow = cow;
            this.level = level;
            this.start = start;
        }
    }
}
