# Patrick Star API V2.0.1

A documentation-only patch for **Minecraft 26.3 (Fabric)**. V2.0.1 keeps the public API and navigation behavior of V2.0.0 unchanged.

## Changes

- Complete English JavaDoc for all 25 public methods across `Patrick`, `PatrickPathRegistry`, `PatrickPath`, `PatrickPath.Builder` and `NavigationSession`.
- Document every public parameter, return value, applicable exception, default value, request lifetime, server-thread requirement, and preemption behavior where relevant.
- Clarify that `create(...)` prepares an unsubmitted handle, not a registered request; `register(...)` and `oneShot(...)` submit it.
- Update all current installation examples and download paths to V2.0.1.

## Installing

- JitPack: `com.github.howShouldIChooseMyUsername:Patrick-Star-API:V2.0.1`
- Fabric Loader dependency: `patrick_star_api >=2.0.1`
- Runtime mod version: `2.0.1+26.3`

See the [README installation instructions](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API#installing-patrick-star-api-v201) for Fabric Loom and Fabric Amper examples.

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.20.1** or newer
- Fabric API **0.166.2+26.3** or a newer compatible build
- Java **25**

## Downloads

- **`patrick-star-api-2.0.1+26.3.jar`** — runtime mod.
- **`patrick-star-api-2.0.1+26.3-sources.jar`** — source archive, including JavaDoc for IDE quick documentation.
- **`SHA256SUMS.txt`** — checksums for the release artifacts.

## Verification

V2.0.1 was verified with a clean build, JUnit and JavaDoc generation. Navigation implementation and public signatures are unchanged. Dedicated-server lifecycle, overload, waypoint regression and independent consumer checks were previously run on V2.0.0; they are not claimed as new V2.0.1 server runs.

See [V2.0.0 release notes](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API/releases/tag/V2.0.0) for the original breaking API changes and [QA_REPORT.md](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API/blob/main/QA_REPORT.md) for verification history.
