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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Independent Fabric consumer: submits the lower-priority route for the shared probe cow. */
public final class AlphaConsumerMod implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("patrick_probe_alpha");
    private static final String PROBE_TAG = "patrick_dual_consumer_probe";
    private static final String REGISTERED_TAG = "patrick_alpha_registered";
    private static final String FACTORY_TAG = "patrick_alpha_factory";

    private final Map<Cow, ObservedCow> observed = new IdentityHashMap<>();

    @Override
    public void onInitialize() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Cow cow && cow.entityTags().contains(PROBE_TAG)) {
                observed.putIfAbsent(cow, new ObservedCow(cow, level, cow.blockPosition().immutable()));
                LOG.info("PCONSUMER ALPHA LOAD cow={} startBlock={} position={}",
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
        // Register only after ServerLevel has indexed the cow by its live entity ID.
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

                state.attempted = true;
                BlockPos start = state.start;
                BlockPos firstWaypoint = start.offset(7, 0, 0);
                BlockPos destination = start.offset(14, 0, 0);
                LOG.info("PCONSUMER ALPHA REQUEST start={} waypoint={} destination={} current={}",
                        start, firstWaypoint, destination, cow.position());
                PatrickPathRegistry request = Patrick.create(cow, mob -> {
                    state.factoryCalls++;
                    mob.addTag(FACTORY_TAG);
                    LOG.info("PCONSUMER ALPHA FACTORY count={} waypoint={} destination={}",
                            state.factoryCalls, firstWaypoint, destination);
                    return PatrickPath.builder(destination)
                            .waypoint(Vec3.atBottomCenterOf(firstWaypoint))
                            .speed(0.72)
                            .reachable(true)
                            .build();
                }).priority(100).lock(1800);
                boolean accepted = Patrick.oneShot(request);
                if (accepted) {
                    cow.addTag(REGISTERED_TAG);
                }
                LOG.info("PCONSUMER ALPHA REGISTER {}", accepted);
            } catch (RuntimeException ex) {
                state.attempted = true;
                LOG.warn("PCONSUMER ALPHA ERROR mob={} type={}", cow.getUUID(),
                        ex.getClass().getSimpleName());
            }
        }
    }

    private static final class ObservedCow {
        final Cow cow;
        final ServerLevel level;
        final BlockPos start;
        boolean attempted;
        int factoryCalls;

        ObservedCow(Cow cow, ServerLevel level, BlockPos start) {
            this.cow = cow;
            this.level = level;
            this.start = start;
        }
    }
}
