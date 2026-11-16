# Patrick Star API — Minecraft Fabric 26.3 길찾기 중재 API

상태: 공개 API 확장 명세 v2, 2026-11-16. 기존 및 신규 A/B/C 오버로드를 구현했으며 전용 서버 검증을 완료했다. 각 게이트별 실제 검증 범위와 미검증 부분은 QA_REPORT.md에 기록한다.

## 1. 프로젝트 목표

한 Mob의 길찾기를 여러 모드가 독립적으로 수정하면 마지막 PathNavigation 호출이 이전 경로를 덮어쓰거나 각 Goal의 MOVE 제어가 충돌한다. 이 API를 이용해 등록한 모드들은 목적지 좌표, 이미 계산한 바닐라 Path, 현재 상태에서 다시 계산할 Supplier 또는 기존 커스텀 알고리즘의 명령 시퀀스를 전달할 수 있다. Patrick Star API는 한 Mob당 실행자를 하나만 설치하여 대기 중인 요청 중 하나를 선택, 실행, 완료/중지 후 다음을 실행한다. API는 경로를 계산하는 알고리즘을 제한하지 않는다.

클래스 이름 Patric은 사용자가 제시한 철자를 그대로 사용한다. 공개 정적 **서비스 메서드 이름은 정확히 두 개**(`create`, `register`)이며 `create`에 여섯 오버로드를 둔다.

~~~java
public static PatricPathRegistry create(Mob targetEntity, Function<Mob, PatricPath> pathFactory);
public static PatricPathRegistry create(Mob targetEntity, BlockPos destination);
public static PatricPathRegistry create(Mob targetEntity, Vec3 destination);
public static PatricPathRegistry create(Mob targetEntity, Path path);
public static PatricPathRegistry create(Mob targetEntity, Supplier<Path> pathFactory);
public static PatricPathRegistry create(Mob targetEntity, BiConsumer<Mob, NavigationSession> pathAction);
public static boolean register(PatricPathRegistry registration);
~~~

create는 등록되지 않은 핸들만 만든다. 인자로 받은 Mob은 Entity의 하위 클래스이며 vanilla PathNavigation을 지원한다. 일반 Entity나 플레이어에는 공용 PathNavigation이 없으므로 현 버전은 Mob을 대상으로 한다. 기존 `Function<Mob,PatricPath>` 팩토리는 해당 Mob을 인자로 받아 PatricPath를 반환한다. 신규 B/C 팩토리도 해당 Mob의 서버 스레드에서 실행한다. create 호출 자체는 경로를 등록·실행하지 않는다. register는 살아 있고 실제 ServerLevel에 존재하는 Mob에 대해 서버 메인 스레드에서 처음 등록할 때만 true를 반환한다. null 핸들·재등록·제거된 Mob·클라이언트/오프스레드 등록은 false를 반환한다. create의 null 인자는 거부하며 `Patric.create(mob, null)`은 여러 오버로드 중 무엇인지 불명확하여 Java 컴파일 단계에서 모호하다. **register의 true는 접수 성공이며 길찾기 완료가 아니다.**

모든 create 오버로드에서 PatricPathRegistry의 공개 인스턴스 설정 메서드는 priority(int)와 lock(int) 두 빌더만 제공한다. priority는 0~65535 양끝 포함, 큰 값이 우선하며 기본 0이다. lock은 0 이상의 실제 밀리초 단위, 기본 0이다. 범위 밖 우선순위·음수 lock은 IllegalArgumentException을 발생시킨다. register 이후 핸들의 설정은 동결되고 수정은 실패한다. 한 핸들은 일회성 작업이며 반복하려면 새 create/register를 한다. Patric에 cancel/remove/tick 등의 서비스 메서드를 추가하지 않는다. `create(Mob,Goal)` 형태의 D는 지원하지 않는다. Goal은 Patrick 내부의 단일 MOVE Goal로 관리한다.

## 2. 경로 자료형 및 사용 방식

### 2.1 A — BlockPos / Vec3 목적지

`Patric.create(mob, BlockPos)`와 `Patric.create(mob, Vec3)`는 목적지 하나만 받아 기본 속도 1.0으로 이동하는 요청이다. BlockPos는 `Vec3.atBottomCenterOf(pos)` 기준으로 해석한다. 별도 경로 빌더가 필요하지 않으며 이동은 register 후 중재자가 해당 요청을 실행할 때 시작한다.

~~~java
Patric.register(Patric.create(mob, new BlockPos(40, 65, 80)).priority(1000));
Patric.register(Patric.create(mob, new Vec3(45.5, 65, 85.5)).priority(1000));
~~~

### 2.2 B — 바닐라 Path와 Supplier<Path>

`Patric.create(mob, Path)`는 이미 계산한 바닐라 경로를 받는다. 전달한 Path의 현재 노드와 목적지는 계산 당시 스냅샷이므로 큐에서 대기하는 동안 Mob이나 지형이 바뀌면 오래될 수 있다. Patrick은 실제 실행용 경로를 복제해 전달한 **원본 Path 커서를 변경하지 않아야 한다**. 선점 후 재개하더라도 기존 스냅샷을 기준으로 한 실행이며, 원래 커서 및 경로 정보가 호출자 측에서 독립적으로 유지되어야 한다.

`Patric.create(mob, Supplier<Path>)`는 요청 평가 시점에 `get()`으로 최신 Path를 생성한다. 선점되어 멈췄다가 재개되는 경우 공급자를 다시 호출하여 최신 위치·상태에서 경로를 재생성한다. Supplier가 null을 반환하거나 계산에 실패하면 해당 후보를 격리해 실패시키며 다른 대기 요청은 유지한다. `Path.canReach()`는 도달 가능성 비교 자료로 사용할 수 있으나 이동 성공을 보장하지 않는다. 경로 생성·Navigation 접근은 서버 메인 스레드에서만 한다.

~~~java
Path previous = mob.getNavigation().createPath(new BlockPos(40, 65, 80), 0);
if (previous != null) {
    Patric.register(Patric.create(mob, previous).priority(1000));
}

Patric.register(Patric.create(mob,
        () -> mob.getNavigation().createPath(new BlockPos(40, 65, 80), 0))
        .priority(1000));
~~~

### 2.3 C — BiConsumer<Mob,NavigationSession>

`Patric.create(mob, (entity, session) -> { ... })`는 소비자의 기존 길찾기 알고리즘을 그대로 사용하면서 **실제 이동 명령**만 Patrick으로 보낸다. `NavigationSession`은 아래 공개 메서드로 이동 단계들을 캡처하는 임시 수집기다.

~~~java
public NavigationSession follow(Path path, double speed);
public NavigationSession moveTo(BlockPos destination, double speed);
public NavigationSession moveTo(Vec3 destination, double speed);

Patric.register(Patric.create(mob, (entity, session) -> {
    Path computed = myAlgorithm.calculate(entity);
    session.follow(computed, 1.2)
           .moveTo(new BlockPos(40, 65, 80), 1.0)
           .moveTo(new Vec3(45.5, 65, 85.5), 1.0);
}).priority(1000));
~~~

명령은 작성 순서대로 실행한다. `follow`는 기존 바닐라 Path 노드를 사용하며 `moveTo`는 목적지로 Mob의 바닐라 내비게이션을 이용한다. 콜백은 **우선순위/도달 가능성/거리 평가를 위해 후보를 평가하는 때** 호출될 수 있다. 이 시점은 해당 요청이 실제 이동을 시작하는 시점보다 빠를 수 있다. 콜백 호출 중엔 명령을 수집만 하고 직접 내비게이션을 실행하지 않는다. 반환된 명령 시퀀스의 실제 이동은 Patrick이 작업을 선택한 뒤 시작된다. 세션은 콜백 종료 즉시 만료되어 보관하거나 비동기 실행에서 재사용할 수 없다. 0 이하·NaN·Infinity 속도, 유효하지 않은 좌표, null 경로, 1024개 초과 명령은 거부한다.

C 콜백은 **선점 후 재개 때 다시 호출될 수 있으므로** 멱등에 가깝게 작성하고, 재호출될 때마다 현재 Mob 상태를 기준으로 경로를 계산해야 한다. 콜백 내 `mob.getNavigation().moveTo(...)` 등의 직접 이동 시작, 비동기 스레드에서 Mob·Navigation 접근, 세션의 콜백 외부 사용은 중재 계약을 위반한다. 콜백 자체의 임의 부수효과는 Patrick이 되돌릴 수 없다.

### 2.4 기존 Function<Mob,PatricPath> 빌더 호환

PatricPath는 최종 목적지 BlockPos, 순서가 있는 중간 경유지 List<Vec3>, 이동 속도(기본 1.0), 도달 가능성 옵션을 갖는다. 개발자는 `PatricPath.builder(BlockPos)`에서 waypoint(Vec3), waypoints(Collection<Vec3>), speed(double), reachable(boolean), build()로 만든다. 빈 waypoint 목록이면 목적지로 직행한다. BlockPos 최종 위치는 `Vec3.atBottomCenterOf(pos)`에 대응한다. null 좌표, NaN/Infinity 좌표·속도, 0 이하 속도, 1024개 초과 경유지는 거부한다. build 결과는 불변 스냅샷이며 반환된 리스트는 수정할 수 없다.

~~~java
PatricPathRegistry request = Patric.create(mob, entity ->
    PatricPath.builder(new BlockPos(40, 65, 80))
        .waypoint(new Vec3(20.5, 65, 30.5))
        .waypoint(new Vec3(29.5, 65, 51.5))
        .speed(1.15)
        .build()
).priority(1000).lock(1500);
boolean accepted = Patric.register(request);
~~~

기존 PatricPath의 reachable은 기본 자동 계산한다. 평가 시 현재 Mob의 navigation.createPath(finalDestination, 0)이 null이 아니고 결과 Path.canReach()가 true면 자동 참, 그 외는 거짓이다. 명시적 reachable(true/false)은 이를 덮는다. 이 값은 동점 판정에 쓰는 **바닐라 경로 탐색의 추정값**으로 사용자 지정 경유지 전체의 성공을 보장하지 않는다. 강제 false인 경로도 소비자의 계산 자유를 위해 자동 버리지는 않는다. 신규 A/B/C에서도 후보를 비교할 때 이용 가능한 경로·목적지에 대한 도달 가능성 추정값을 사용하며, 각 명령의 실제 이동 성공을 뜻하지 않는다. 동작 중 실제 각 구간의 Navigation 결과가 실패할 수 있다.

## 3. 게임·도구·배포 구성

| 요소 | 요구 구성 |
| --- | --- |
| Fabric Amper | product: fabric/api, mod.id: patrick_star_api |
| 작성자가 유지할 빌드 파일 | settings.gradle.kts + mod.yaml 하나만 |
| 금지 파일 | build.gradle, build.gradle.kts, 직접 작성한 fabric.mod.json |
| minecraft.version / mappings | "26.3" / mojang |
| Fabric Loader | "0.20.1" (2026-10-19 공개) |
| Fabric API | "0.166.2+26.3" (2026-11-11 공개) |
| Java / Kotlin | release: 25 / enabled: false |
| 테스트 | org.junit.jupiter:junit-jupiter:6.0.0 |
| 예제 Maven 좌표 | comvyne.patrickstar:patrick-star-api:1.0.0+26.3 |

Maven group과 archive 이름은 이 로컬 프로젝트의 식별자일 뿐 외부 네임스페이스 소유 또는 원격 게시가 아니다. API의 entrypoint는 comvyne.patrickstar.internal.PatrickStarMod, 공개 서비스·데이터 클래스는 comvyne.patrickstar.api 패키지에 둔다. Fabric Amper가 Loader JSON을 자동 생성하고 최종 JAR 루트에는 fabric.mod.json이 정확히 하나 있어야 한다. Fabric API/Loader의 기본 의존은 minecraft.fabricApi/loader로 구성하며 별도 build.gradle 설정이나 수동 metadata를 만들지 않는다. publish 작업은 별도 명시적 사용자 지시 없이는 실행하지 않는다.

## 4. Minecraft 26.3 기존 기술 조사와 채택한 API

1. Mob.getNavigation(): PathNavigation, Mob.getGoalSelector(): GoalSelector가 공용으로 제공된다. GoalSelector.addGoal(int,Goal)과 removeGoal(Goal)을 통해 엔티티당 제어 Goal을 설치할 수 있다. Goal.Flag.MOVE는 vanilla GoalSelector 내 MOVE Goal 간의 실행을 조정한다.
2. PathNavigation.createPath(BlockPos,int)는 현재 상태의 A* 기반 경로 Path 또는 null을 반환한다. Path.canReach()는 최종 목적지를 찾았는지 보고한다. PathNavigation.moveTo(double,double,double,double)를 사용하면 Mob 고유의 이동 방식(땅·물·비행)에 맞춰 각 웨이포인트로 탐색/주행할 수 있다. PathNavigation.isDone(), isStuck(), getTargetPos(), stop()으로 결과를 관찰한다.
3. Path(List<Node>,BlockPos,boolean)로 바닐라 Node를 직접 만들 수도 있다. 새 B/C 오버로드는 해당 Path를 복제해 원본 커서를 보존하면서 그대로 실행하는 경로를 제공한다. A의 목적지와 기존 PatricPath의 Vec3 경유지는 해당 Mob의 내비게이션이 구간별 탐색/주행을 수행한다. 직접 장애물 우회 알고리즘을 구현하는 소비자는 B/C를 통해 계산된 Path를 넘길 수 있다.
4. Minecraft 26.3은 PathNavigation.moveTo(Entity,int,double) 오버로드를 추가했다. PathWeaver 26.3 릴리스는 기존 (Entity,double)만 가로채는 방식으로는 일부 호출이 누락됨을 설명한다. 전역 PathNavigation overwrite/redirect Mixin은 다른 모드와 충돌할 수 있으므로 채택하지 않는다.
5. GoalSelector는 매 tick 신규 Goal을 재평가하지 않을 수 있고, 바닐라의 우선권이 더 높은 MOVE Goal이 실행되면 시작/재개가 지연될 수 있다. Brain 직접 제어 또는 다른 모드의 독립 moveTo를 Goal.Flag.MOVE가 강제 차단할 수 없다. 따라서 **중재 보장 범위는 API에 참여하는 등록 작업끼리**다. 외부 비참여 모드와의 100% 배타성을 약속하지 않는다.

조사 출처:
- https://fabricmc.net/2026/09/15/263.html
- https://mappings.relativitymc.org/26.3/net/minecraft/entity/mob/MobEntity.html
- https://aldak0.ru/javadoc/26.3.x/net/minecraft/world/entity/ai/navigation/PathNavigation.html
- https://aldak0.ru/javadoc/26.3.x/net/minecraft/world/level/pathfinder/Path.html
- https://minecraftdocs.dev/systems/entities/pathfinding
- https://modrinth.com/mod/pathweaver/version/0.9.0%2B26.3
- https://github.com/FabricMC/fabric-api/releases

## 5. 상태 기계와 통합 흐름

~~~
CREATED -> register accepted -> QUEUED -> command/route evaluation -> READY
READY -> selected -> RUNNING -> next step -> RUNNING
RUNNING -> final destination reached -> SUCCEEDED -> REMOVED
RUNNING -> navigation failed/stuck/timeout -> FAILED -> REMOVED
RUNNING -> high priority after lock expiry -> PAUSED -> reevaluate resumable request -> READY
RUNNING -> vanilla Goal interruption -> PAUSED -> resumed later
ANY -> mob death / unload / server stop -> DISCARDED
~~~

Patric.register는 Mob별 큐에 새 핸들을 추가한다. 서버 END_SERVER_TICK에서 GoalSelector를 수정하므로 다른 Goal 처리 중 셋을 바꿀 위험을 줄인다. Mob당 단일 PatrickMoveGoal을 priority 0, Goal.Flag.MOVE로 설치하며, GoalSelector의 타 Goal 제어권과 path registry 내부 우선순위는 서로 독립이다. Goal.canUse는 아직 유효한 작업이 있는 경우에만 true, canContinueToUse도 작업이 있는 동안만 true로 하여 idle일 때 vanilla MOVE를 방해하지 않는다. requiresUpdateEveryTick은 true다.

기존 `Function<Mob,PatricPath>` 람다는 후보의 첫 평가 때 한 번 실행한다(매 tick 실행 금지). B의 Supplier 및 C의 콜백은 후보 평가 때 실행되며 선점 후 재개 시 재실행할 수 있다. 따라서 C의 command-capture 콜백이 평가되는 시점에 해당 후보가 RUNNING이 될 필요는 없다. 콜백에서 수집된 순차 명령만 실제 선택 후 실행한다. 예외·null·부정확한 값이면 **그 후보만 실패**시키고 나머지 후보는 살아 있다. Path/명령 목록은 평가 결과를 안전하게 복사·유지하고 소비자 원본 Path의 커서를 변경하지 않는다. 각각의 `moveTo` 구간은 navigation.moveTo(x,y,z,speed)로 시작하고 `follow`는 제공된 바닐라 Path로 시작한다. 진행 중에는 동일한 탐색을 매 tick 재호출하지 않는다. 현재 위치의 근접 확인 후 다음 구간을 시작하며 마지막 목표가 실제로 가까워졌을 때 성공으로 제거한다. Nav가 끝나거나 stuck인데 목표가 멀면 실패로 종료하고 다음 후보를 선택한다. 재시작 중인 경로를 외부가 바꿀 때 목적지 변화를 감지하고 한정된 복구 시도를 할 수 있으나 복구 횟수는 제한한다. 각 경유지·최종 목적지는 이미 로드된 영역 범위 안에서 이동 가능해야 한다.

Goal.stop에서 자신의 현재 길찾기만 정리하고 미완료 후보는 PAUSED로 남길 수 있다. 여러 차원·월드의 상태는 섞지 않는다. Fabric ServerEntityEvents.ENTITY_UNLOAD에서 해당 Mob의 Goal 및 큐를 제거하고 서버 STOPPED에서 나머지 등록을 정리한다. 죽거나 제거된 Mob은 정기 정리한다. 백그라운드 스레드에서 vanilla game object에 접근하지 않는다.

## 6. 우선순위·동점·락 계약

후보 비교는 다음 사전식 안정 순서다.

1. 높은 priority(65535 최상)
2. reachable 참이 거짓보다 우선(기존 PatricPath의 명시 덮어쓰기 존중)
3. 현재 Mob 위치에서 남은 경유지·명령 목적지까지의 Euclidean 구간 거리 합이 짧은 경로 우선(바닐라 Path는 노드 경로 기반 예상 거리)
4. 등록 일련번호가 작은 경로 우선(FIFO)

어떤 시점에도 하나의 Mob에 RUNNING인 API 등록은 하나만 허용한다. 첫 경로가 끝나면 큐에 남은 후보들 중 다음 최선 후보를 재계산하고 계속한다. 3개면 3→2→1 순차로 모두 실행한다. **같은 priority의 새 등록은 기존 RUNNING 요청을 선점하지 않는다.** 더 높은 priority 후보가 새로 왔을 때만 현재 경로의 lock 만료를 확인하여 PAUSED로 옮기고 선점한다. PAUSED된 후보는 남은 거리의 재평가 후 재개한다. 재개되는 B Supplier/C callback의 경로·명령은 새로 평가할 수 있고, B 사전 계산 Path는 원래 스냅샷으로 남는다. 새 등록이 끝없이 높은 우선순위로 들어오면 낮은 우선순위의 무조건적인 유한 시간 완료를 약속할 수 없다.

lock(ms)는 System.nanoTime 단조 시간으로 RUNNING 시작 때 기한을 계산한다. 기한 전에는 이 API로 등록된 다른 경로가 해당 Mob을 선점할 수 없다. 단, 실제 목적 달성, Nav가 더 진행하지 못함, 죽음/언로드, 가능한 최선 결과가 정해짐은 잠금 시간이 남았어도 즉시 종료한다. 0은 즉시 선점 가능하지만 다른 후보가 없으면 중단하지 않는다. 선점 후 재개될 때는 lock을 새 실행 구간에 재적용해 빈번한 재선점을 방지한다. GoalSelector 자체의 vanilla 우선권에 의한 외부 중단은 본 내부 lock으로 막을 수 없으며 문서화한다. 실제 시간 만료 시에도 Goal 틱이 수행되는 다음 시점에 반영된다.

## 7. 경계·성능·안전

- 조정 상태, Navigation, GoalSelector 접근은 오직 서버 메인 스레드에서 수행한다. 중복 register는 true로 잘못 성공 처리하지 않는다.
- 유효성: null 입력, 죽은 Mob, 다른 서버의 Mob, 비유한 좌표/속도, 1024개 초과 경유지 또는 C 명령, 범위를 벗어난 priority를 일찍 거부한다. 여러 create 오버로드 때문에 null을 타입 지정 없이 넘기는 Java 호출은 모호하다.
- 후보 람다 실패는 다른 후보에 전파하지 않는다. 잘못된 경로를 매 tick 재계산하지 않으며 로깅에 비밀 정보나 불필요한 사용자 스택 데이터 노출을 피한다. B Supplier/C callback은 평가·선점 후 재개 시 재실행할 수 있으므로 소비자 코드가 반복 호출을 견뎌야 한다.
- 경로가 교착되어 이동 진척이 없으면 유한 횟수/틱 제한으로 실패 처리한다. 잠금이 장시간이라도 목표 달성 또는 최선의 실패 판정 시 조기 해제한다.
- 한 Mob당 단일 Goal/큐; 엔티티 unload, 서버 종료 시 즉시 정리한다. WeakReference만으로 자동 해제된다고 가정하지 않는다.
- 한 Mob당 접수 한도는 256개다. 초과 register는 false를 반환하며 무제한 서버 틱당 A* 및 큐 메모리를 허용하지 않는다. 실행 중 새 후보의 바닐라 경로 계산이 현재 탐색 목표를 바꿀 수 있으므로 락 보호 중과 낮거나 같은 priority에는 기존 경로가 끝날 때까지 평가를 미룬다.
- 다른 모드의 전역 Mixin·Brain 직접 경로·vanilla Goal 우선권과의 실제 호환성은 명시적으로 실험해야 한다. 컴파일 통과를 전역 충돌 해결 보증으로 해석하지 않는다.
- C의 NavigationSession은 명령 기록 전용이며 호출 종료 후 무효화한다. 콜백에서 `getNavigation().moveTo` 직접 호출 또는 오프스레드 이동을 지원하지 않는다. 외부 Goal 전달(D) 역시 지원하지 않는다.

## 8. 검증 게이트

| ID | 시나리오 | 수락 조건 |
| --- | --- | --- |
| P01 | Fabric Amper 소비자 build | 설정 두 파일만 존재, 단일 generated fabric.mod.json, 배포 JAR·sources JAR |
| P02 | create만 호출 | 등록/이동이 발생하지 않음 |
| P03 | 정상·중복·null·off-thread register | 접수 성공/실패 정확한 bool, 부수효과 없음 |
| P04 | priority 경계 | 0,65535 수락; -1,65536 거부 |
| P05 | lock 경계·만료 | 음수 거부; 0/양수 허용; 조기 성공/실패 시 해제 |
| P06 | 같은 Mob에 priority 다른 3개 | 최댓값 먼저, 다른 후보들이 순차 실행 |
| P07 | 동점 3개 | reachable, 예상 거리, 등록 순서 기반 결정적 순서 |
| P08 | 실행 중 새로운 높은 priority | 남은 lock 동안 보호, 만료 후 선점·기존 후보 복귀 |
| P09 | 실제 경유지/최종 도착 | 올바른 Vec3 구간 순서와 BlockPos 도착 여부 확인 |
| P10 | reachable override | 명시 true/false가 자동 값보다 우선 |
| P11 | null/예외/고착된 경로 | 후보 하나만 실패하고 큐 다음 후보 진행 |
| P12 | Mob unload/death/server stop | API Goal·큐 정리, 유령 이동 없음 |
| P13 | 빈 큐 | vanilla MOVE Goal 정상 동작 |
| P14 | 실제 26.3 server/client, 외부 경합 모드 | 기존 API 전용 서버·별도 참여 모드 충돌은 과거 PASS 기록 보유; 클라이언트·비참여 모드 충돌은 NOT RUN |
| P15 | A BlockPos/Vec3 등록 | 같은 priority에서 더 가깝고 도달 가능한 목표를 먼저 수행, 두 목표 모두 최종 도착 |
| P16 | B Path 전달 | 원본 Path 커서 불변, 바닐라 Path로 목적지 도착, 오래된 스냅샷 한계 확인 |
| P17 | B Supplier<Path> | 평가 시 생성, 선점 후 재개 때 재생성, null/예외 후보 격리 |
| P18 | C NavigationSession 순차 명령 | follow(Path,double) → moveTo(BlockPos,double) → moveTo(Vec3,double) 순차 이동·도착 |
| P19 | C 콜백/세션 경계 | 이동은 선택 뒤 시작, 콜백 반복 호출 가능, 종료 후 세션 사용 불가, 오프스레드 직접 이동 금지 |
| P20 | 동일 priority 실행 중 등록 | 현재 RUNNING 요청은 유지, 다음 후보의 reachable·거리·FIFO 순위가 결정적 |
| P21 | 공개 API 호환·제한 | Patric 서비스 이름 두 개, 기존 Function<Mob,PatricPath> 유지, D Goal 미지원, null 오버로드 모호성 문서화 |

개발 순서: JUnit 핵심 규칙 테스트 → Gradle help/tasks/test/build/verifyFabricAmperJar → 배포 JAR 실제 ZIP 엔트리·SHA-256 확인 → 격리된 server 실물 Mob 테스트. 외부 플랫폼 업로드, 사용자 동의 없는 EULA 임의 승인, 사용자의 실제 월드 사용은 하지 않는다. 각 결과를 PASS, STATIC PASS, COMPILED, BLOCKED, NOT RUN으로 구분한다.

**최종 실측 상태(2026-11-16):** 사용자의 명시적 EULA 수락 허가를 받은 독립 개발 전용 서버에서 기존 `Function<Mob,PatricPath>` 기반 런타임 **18/18 PASS**, 서로 다른 Fabric Loader ID의 독립 소비자 모드 Alpha/Beta JAR 두 개의 같은 Cow 중재 **7/7 PASS**, 그리고 신규 A/B/C 오버로드 실물 Cow 실행 **4/4 PASS**를 기록했다. JUnit은 기존 10개와 신규 오버로드·세션 7개를 합쳐 **17/17 PASS**, Fabric Amper `build` 및 `verifyFabricAmperJar` 통과. A 동점 거리는 나중에 등록된 가까운 Vec3 목표가 먼저 도착, B 네이티브 Path 원본 인덱스 불변 및 Supplier 지연 실행, C `follow(Path)`→`moveTo(BlockPos)`→`moveTo(Vec3)` 및 만료 후 접근 차단을 확인했다. 원문 `run/patrick-overload-runtime-results.txt` `POVERLOAD SUMMARY PASS=4 FAIL=0`; 기존 `run/patrick-runtime-smoke-results.txt` `PTEST SUMMARY PASS=18 FAIL=0`; 독립 모드 `run/patrick-dual-consumer-results.txt` `PCROSS SUMMARY PASS=7 FAIL=0`. **P17/P19의 B Supplier/C 콜백 선점 후 실제 재생성**과 다른 종류 간 동점 혼합 경합·외부 비참여 AI/Brain/Mixin·클라이언트·차원 변경·장시간 부하·IDE Sync는 이번 검증에 포함되지 않았다. 자세한 SHA 및 범위는 `QA_REPORT.md`.
