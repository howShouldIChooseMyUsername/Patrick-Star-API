# Patrick Star API V1.0.0

The first public release of Patrick Star API for **Minecraft 26.3 (Fabric)**.

Patrick Star API lets multiple participating mods request navigation for the same mob without competing over its movement controls. Keep your own pathfinding logic; Patrick takes care of scheduling the requests.

## Highlights

- **Simple API:** create and register navigation requests through `Patric.create(...)` and `Patric.register(...)`.
- **Use existing paths:** submit a destination (`BlockPos` or `Vec3`), a vanilla `Path`, or a lazy `Supplier<Path>`.
- **Bring your own algorithm:** use `NavigationSession` to submit ordered `follow` and `moveTo` commands.
- **Priority-based scheduling:** support for reachability, route distance, registration order, timed locks, preemption, and resumption.
- **Compatible waypoint builder:** existing `PatricPath` requests continue to work.
- **One coordinator per mob:** a single internal MOVE goal manages participating navigation requests.

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.20.1** or newer
- Fabric API **0.166.2+26.3** or newer compatible build
- Java **25**

The Fabric mod ID is `patrick_star_api`. The public Java API is in `comvyne.patrickstar.api`.

## Downloads

- **`patrick-star-api-1.0.0+26.3.jar`** — install this in your Fabric mods folder when another mod requires Patrick Star API.
- **`patrick-star-api-1.0.0+26.3-sources.jar`** — source archive for developers, not a runtime mod.
- **`SHA256SUMS.txt`** — checksums for the attached JARs.

## Verification

The project has passed 17 JUnit tests, four dedicated-server checks for the public overloads, 18 regression checks for waypoint navigation, and seven integration checks involving two independent Fabric consumer mods.

**Limitations:** Coordination applies to mods using the Patrick API; it does not automatically override navigation issued directly by unrelated mods. Cross-dimension behavior and dynamic callback regeneration after preemption have not been fully tested.

See the [README](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API#readme) for usage examples and [QA_REPORT.md](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API/blob/main/QA_REPORT.md) for test details.
