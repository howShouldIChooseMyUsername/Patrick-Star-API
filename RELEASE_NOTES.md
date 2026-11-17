# Patrick Star API V2.0.0

Patrick Star API **V2.0.0** for **Minecraft 26.3 (Fabric)** introduces persistent mob navigation behaviors and event-driven one-shot requests. This is a **breaking public API update** from V1.0.0.

## Highlights

- **Persistent `Patrick.register(handle)`:** registers a passive navigation behavior that remains active across route completions. Dynamic factories can calculate the next route without re-registering.
- **Event-driven `Patrick.oneShot(handle)`:** submits a single-run path request; completed or failed one-shots are removed automatically.
- **`Patrick.unregister(handle)`:** cancels an active or queued registration using the original `PatrickPathRegistry` handle.
- **`deferOneShot(boolean)`:** defaults to `false`, allowing one-shots to interrupt a passive route after its lock expires. Set to `true` to defer one-shots until the passive route completes.
- **Shared arbitration:** higher priority, reachability, shorter remaining route, then FIFO order within each request category. One-shots get a turn before the next passive route; paused passive work can resume.
- **Navigation ownership fix:** switching routes releases the previously owned path correctly even when vanilla path evaluation updates the navigation target.
- **Existing six `create` overloads retained:** `BlockPos`, `Vec3`, vanilla `Path`, `Supplier<Path>`, `Function<Mob, PatrickPath>`, and `BiConsumer<Mob, NavigationSession>`.

## Breaking changes / migration from V1.0.0

1. Rename Java imports and types: `Patric` → `Patrick`, `PatricPath` → `PatrickPath`, and `PatricPathRegistry` → `PatrickPathRegistry`. The Java package **`comvyne.patrickstar.api`** and Fabric mod ID **`patrick_star_api`** are unchanged.
2. `register(...)` **no longer means one run**. If you previously registered a path for a one-time event, call `Patrick.oneShot(...)` instead.
3. For ongoing patrol or other passive behavior, use `Patrick.register(...)`. Save the returned `PatrickPathRegistry` from `Patrick.create(...)` so you can call `Patrick.unregister(handle)` when that behavior is no longer needed.
4. Update the JitPack dependency to **`com.github.howShouldIChooseMyUsername:Patrick-Star-API:V2.0.0`**, and the Fabric Loader dependency to `patrick_star_api >=2.0.0`.

These names and behavior changes are not binary-compatible with the V1.0.0 public API: rebuild consumer mods with V2.0.0.

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.20.1** or newer
- Fabric API **0.166.2+26.3** or newer compatible build
- Java **25**

## Downloads

- **`patrick-star-api-2.0.0+26.3.jar`** — runtime Fabric mod JAR.
- **`patrick-star-api-2.0.0+26.3-sources.jar`** — sources for developers (not a runtime mod).
- **`SHA256SUMS.txt`** — SHA-256 hashes for both JARs.

## Verification

The release passed **20/20 JUnit tests**, **4/4 real-Mob dedicated-server lifecycle tests** covering one-shot interruption and return to passive navigation, deferral, equal-priority one-shot ordering, persistent route regeneration, and unregister cleanup; **4/4** overload tests; **18/18** original waypoint regression checks adapted for one-shot semantics; and **7/7** integration checks with two independently loaded Fabric consumer mods. All server checks were rerun against `2.0.0+26.3`. Full build and JAR verification passed.

**Limitations:** Arbitration applies only to mods that submit work through Patrick; it cannot reliably override unrelated mods that directly manipulate `PathNavigation`. Cross-dimension behavior, third-party AI interactions, and extended load tests have not been exhaustively verified.

See the [README](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API#readme) for usage and [QA_REPORT.md](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API/blob/main/QA_REPORT.md) for verification details.
