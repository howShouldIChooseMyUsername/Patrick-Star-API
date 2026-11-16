# Patrick Star API

**Mob navigation, without the tug-of-war.**

Patrick Star API is a small navigation coordinator for Fabric mods. It lets different mods submit paths for the same mob without having to fight over which one gets to control its movement.

You keep your pathfinding code. Patrick handles the queue.

Built for **Minecraft 26.3**, **Fabric Loader 0.20.1**, **Fabric API 0.166.2+26.3**, and **Java 25**.

## Overview

A mob only has one active navigation at a time. If two mods keep sending it somewhere different, the last call to `PathNavigation` usually wins.

Patrick gives participating mods a common place to submit their movement requests. It picks one to run, allows higher-priority work to take over when appropriate, and can return to an interrupted request afterward.

- **Bring your own pathfinding.** Use a destination, a vanilla `Path`, a path supplier, or your existing algorithm.
- **Priority and locking.** Decide which requests matter most and how long an active request should be protected.
- **One coordinator per mob.** Patrick manages a single internal MOVE goal instead of adding a competing goal for every request.
- **No mandatory builder.** `PatricPath` is still available for waypoints, but you're free to skip it.

Patrick only coordinates requests made through its API. It does **not** take control of unrelated mods that call vanilla navigation directly.

## Getting started

Most requests only need two calls: `create` and `register`.

~~~java
import comvyne.patrickstar.api.Patric;
import net.minecraft.core.BlockPos;

boolean accepted = Patric.register(
    Patric.create(mob, new BlockPos(100, 64, 100))
        .priority(100)
        .lock(1500)
);
~~~

`create` prepares a request. `register` puts it in the queue. A successful registration does **not** mean the mob has reached its destination.

`priority` accepts values from `0` to `65535` (higher goes first). `lock` is measured in milliseconds; `lock(1500)` protects an active request against preemption for at least 1.5 seconds, unless it finishes or fails sooner.

Requests must be registered on the Minecraft server thread, using a living `Mob` that is already present in its `ServerLevel`.

### Use an existing Path

Already have a vanilla `Path`? You can pass it straight in:

~~~java
Path path = mob.getNavigation().createPath(destination, 0);

if (path != null) {
    Patric.register(Patric.create(mob, path).priority(200));
}
~~~

Patrick copies the supplied path so its navigation progress doesn't modify your original object.

If the mob might move before its request gets a turn, it's usually better to supply a factory:

~~~java
Patric.register(
    Patric.create(mob, () ->
        mob.getNavigation().createPath(destination, 0)
    ).priority(200)
);
~~~

The supplier runs when Patrick evaluates the request, rather than at registration time. It can also be called again if the request is resumed after being interrupted.

### Bring your own algorithm

For custom pathfinding, use the two-argument callback:

~~~java
Patric.register(
    Patric.create(mob, (entity, navigation) -> {
        Path path = myPathfinder.calculate(entity);

        if (path != null) {
            navigation.follow(path, 1.2);
            navigation.moveTo(new BlockPos(120, 64, 80), 1.0);
        }
    }).priority(300)
);
~~~

`navigation` is a `NavigationSession`, not the mob's raw `PathNavigation`. Its `follow` and `moveTo` calls collect commands in order. They don't move the mob until Patrick selects that request.

The session expires when the callback returns. Callbacks may run again after preemption, so avoid side effects that can't safely be repeated. Calling `mob.getNavigation().moveTo(...)` directly inside the callback bypasses Patrick's coordination.

### Waypoints

The original builder is still there if you prefer it:

~~~java
Patric.register(
    Patric.create(mob, entity ->
        PatricPath.builder(new BlockPos(100, 64, 100))
            .waypoint(new Vec3(40.5, 64, 20.5))
            .waypoint(new Vec3(70.5, 64, 60.5))
            .speed(1.0)
            .build()
    ).priority(100)
);
~~~

You can also pass a `Vec3` destination directly with `Patric.create(mob, position)`. The destination overloads use a default speed of `1.0`.

## How requests are picked

Waiting requests are ordered by:

1. **Priority** — higher first.
2. **Reachability** — reachable routes before unreachable ones at the same priority.
3. **Distance** — shorter estimated remaining route first.
4. **Registration order** — first in, first out when everything else ties.

Once a request is running, another request with the **same priority** won't interrupt it. A higher-priority request can take over after the running request's lock expires. The interrupted work stays queued for later.

Each mob is coordinated separately. Failed or invalid path providers are discarded without stopping unrelated valid requests.

## Adding Patrick to a mod

Use the [V1.0.0 release](https://github.com/howShouldIChooseMyUsername/Patrick-Star-API/releases/tag/V1.0.0) through JitPack. The release targets Minecraft 26.3 and Java 25. The JitPack version is the Git tag **`V1.0.0`**, not the mod version `1.0.0+26.3`.

### Fabric Loom (Gradle Kotlin DSL)

Add JitPack to `settings.gradle.kts` (merge this into your existing repository configuration):

~~~kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
~~~

Then add Patrick to `build.gradle.kts`:

~~~kotlin
dependencies {
    implementation("com.github.howShouldIChooseMyUsername:Patrick-Star-API:V1.0.0")
}
~~~

For Minecraft 26.3's non-remapping Fabric Loom, use `implementation`. Older remapping Loom projects typically use `modImplementation` instead (this release is built for 26.3).

**Repository mode note:** `FAIL_ON_PROJECT_REPOS` rejects repositories added by a project or by plugins such as Loom. If your Fabric project adds its own repositories, omit the `dependencyResolutionManagement` block above and add `maven("https://jitpack.io")` to your existing `repositories` block in `build.gradle.kts` instead.

Declare Patrick as a required mod in your `fabric.mod.json`:

~~~json
"depends": {
    "patrick_star_api": ">=1.0.0"
}
~~~

### Fabric Amper (`mod.yaml` / `mod.yml`)

Keep your Fabric Amper `settings.gradle.kts` bootstrap (see [this project's example](settings.gradle.kts)). Add JitPack and Patrick to your existing `mod.yaml` or `mod.yml`:

~~~yaml
mod:
  depends:
    patrick_star_api: ">=1.0.0"

repositories:
  - fabric
  - mavenCentral
  - maven: "https://jitpack.io"

dependencies:
  - maven: "com.github.howShouldIChooseMyUsername:Patrick-Star-API:V1.0.0"
    kind: mod
~~~

Merge these fields into your existing YAML; don't replace its other `mod`, `repositories`, or `dependencies` entries. Fabric Amper configures the build from `settings.gradle.kts` and the YAML file, so **no `build.gradle.kts` is needed**.

In both setups, the Maven dependency supplies the classes to compile against, while `patrick_star_api` in `depends` tells Fabric Loader that the Patrick mod must also be installed at runtime. Do not bundle a second copy of Patrick into your mod.

## Building

This project uses Fabric Amper with `settings.gradle.kts` and `mod.yaml`. There is no project `build.gradle` or `build.gradle.kts`.

From the project directory:

~~~powershell
.\gradlew.bat build
~~~

On Linux or macOS, use `./gradlew build`. If you have the Fabric Amper batch launcher installed alongside the project, you can also run:

~~~powershell
..\fabric-amper.bat build
~~~

The compiled library and sources JAR are written to `build/libs/`. The V1.0.0 release is built as `patrick-star-api-1.0.0+26.3.jar`.

## Testing

The current Minecraft 26.3 build has been tested on a real dedicated server:

- **17/17** JUnit tests
- **4/4** tests for the new destination, native path, supplier, and session APIs
- **18/18** original waypoint API regression checks
- **7/7** integration checks with two separately loaded Fabric consumer mods competing for the same cow

Those tests cover the common navigation and arbitration paths, not every possible AI interaction. In particular, unrelated mods that overwrite `PathNavigation` directly, cross-dimension behavior, and dynamic callback regeneration after preemption still need more testing.
