# Patrick Star API 검증 기록 (2026-11-16)

프로젝트: C:/Users/user/Documents/vbc/Patrick Star API

## 환경

- Minecraft: 26.3
- Java: Temurin OpenJDK 25.0.4.1
- Fabric Loader: 0.20.1
- Fabric API: 0.166.2+26.3
- Fabric Amper: JitPack 태그 v0.1.3, settings plugin 0.1.3
- 실행된 Gradle: 9.9.1
- Loom: 1.17.21
- 사용자의 명시적 동의에 따라 **독립 개발 서버의** `run/eula.txt`를 `eula=true`로 변경
- 서버는 `127.0.0.1:25577`로 제한하고 `patrick-runtime-test-world` 및 `patrick-dual-consumer-cold-world` 테스트 월드만 사용함. 실제 사용자 월드·인증 설정은 수정하지 않음

## 실행 결과

| 검사 | 실제 명령/증거 | 결과 | 정확한 범위 |
| --- | --- | --- | --- |
| Config/tasks | 프로젝트에서 ..\fabric-amper.bat tasks | PASS | Gradle task 목록, compileJava, test, runServer, verifyFabricAmperJar 생성 |
| Build/test | 프로젝트에서 ..\fabric-amper.bat build | PASS | validateFabricAmper, compileJava, jar, sourcesJar, test, verifyFabricAmperJar, build 성공 |
| JUnit 공개 API | build/test-results/test/TEST-comvyne.patrickstar.api.PublicApiContractTest.xml | PASS 6/6 | 서비스 두 메서드, 레지스트리 두 빌더, 경계, 경로 불변값과 waypoint 한도 |
| JUnit 순위/락 | build/test-results/test/TEST-comvyne.patrickstar.internal.PathPolicyTest.xml | PASS 4/4 | 후보 정렬·우선순위·동점·최소 잠금 판정 |
| JUnit 오버로드 | build/test-results/test/TEST-comvyne.patrickstar.api.NavigationOverloadTest.xml | **PASS 4/4** | `Patric.create`의 6가지 선언/람다 컴파일·기존 시그니처/세션 공개 메서드 |
| JUnit 세션·호환성 | build/test-results/test/TEST-comvyne.patrickstar.api.NavigationSessionContractTest.xml | **PASS 3/3** | 세션 명령 순서·만료 이후 접근 금지·입력 검증·기존 빌더 동등 변환 |
| 배포 Loader metadata | build/generated/fabric-amper/resources/main/fabric.mod.json | STATIC PASS | patrick_star_api, version, Loader/Java/Minecraft/Fabric API 조건 확인 |
| 배포 JAR 구조 | jar tf build/libs/patrick-star-api-1.0.0+26.3.jar | STATIC PASS | 루트 fabric.mod.json 1개, 공개 API 및 내부 실행 클래스 포함 |
| 반복 빌드 | 미수정 상태로 ..\fabric-amper.bat build 재실행 | PASS | compileJava/jar/test UP-TO-DATE, verifyFabricAmperJar PASS |
| 독립 개발 서버 부트 | ..\fabric-amper.bat server | PASS | EULA 동의 후 127.0.0.1:25577에서 MC26.3 서버 월드 생성·작동, Patrick Star API 인식 및 `stop` 정상 종료 |
| 서버 월드 경로 실행 | `run/patrick-runtime-smoke-results.txt` | **PASS 18/18** | 실물 Cow Mob의 등록·우선순위·경유지·목표 도착·lock·선점 및 복귀·결함 격리·언로드·비등록 확인 |
| 실제 독립 모드 간 경합 | `run/patrick-dual-consumer-results.txt` + Alpha/Beta 별도 JAR | **PASS 7/7** | 실제 Fabric Loader가 별도 모드 ID 두 개를 로드, 같은 Cow에 각자의 Path 요청. lock·선점·복귀·Goal 정리 검증. 비참여 모드/외부 Mixin 호환성은 미확정 |
| 신규 A/B/C 오버로드 서버 실행 | `run/patrick-overload-runtime-results.txt` | **PASS 4/4** | 동점 거리 선정, Native Path 깊은 복제·원본 커서 유지, 지연 Supplier, C 3연속 명령 및 만료 검증 |
| IDE 첫 동기화·클라이언트 | IntelliJ GUI, runClient 실행 안 함 | NOT RUN | IDE, 클라이언트, 렌더 무관 검증 |

최신 코드 전체 빌드가 PASS했고 **JUnit 17/17(실패/오류/스킵 0)**, 기존 DedicatedServer Mob 런타임 **18/18**, **별도 Fabric 모드 JAR 두 개를 로드한 동시 소비자 테스트 7/7**, 신규 A/B/C 오버로드 전용 서버 실측 **4/4**(모두 실패 0)을 검증했다. 실제 서버 틱과 실물 Cow의 PathNavigation으로 이동·선점·도착을 확인했다. 단, 정식 Minecraft GameTest 함수 등록 및 독립 Gradle 소비자 프로젝트 각자에서의 빌드는 아니다.

## 최종 산출물

배포 JAR:

    C:/Users/user/Documents/vbc/Patrick Star API/build/libs/patrick-star-api-1.0.0+26.3.jar

SHA-256:

    A3847F64E651F272CF55BB834A8BFB1597A5858575D3B5B3DFE2D94C5A8F3FBD

소스 JAR:

    C:/Users/user/Documents/vbc/Patrick Star API/build/libs/patrick-star-api-1.0.0+26.3-sources.jar

SHA-256:

    ADF169DBD92AACC6E5D1D64B5F6892A5ADE3A3670689409798C39ABE0A685258

비게시 로컬 산출물이다. 원격 Maven/Modrinth/CurseForge 릴리스 전송은 수행하지 않았다.

## 실제 서버 로그와 한계

**역사적 기록: EULA 동의 이전 최초 서버 부트** 출력:

    [FabricLoader/GameProvider] Loading Minecraft 26.3 with Fabric Loader 0.20.1
    [FabricLoader] Loading 44 mods:
    ...
    - fabric-api 0.166.2+26.3
    ...
    - patrick_star_api 0.1.0+26.3
    [Minecraft] You need to agree to the EULA in order to run the server.

첫 시도에서는 EULA 미승인으로 중단됐고 당시 BUILD SUCCESSFUL을 실제 월드 기동으로 오인하지 않았다. **이후 사용자가 EULA 내용을 이해하고 명시적으로 동의했으므로 `run/eula.txt=eula=true`를 적용하고 실제 서버 실행을 반복했다.**

공개 26.3 매핑/문서 및 정적 독립 코드 리뷰로 API 선택을 확인했으며, 리뷰 중 발견된 위험을 수정했다: 현재 경로를 보호하는 동안 낮은 우선순위 경로 계산 보류, Path identity로 소유권 판별 후 stop, 서버 thread/엔티티 등재 검사, 동일 Mob 단일 Goal, 엔티티 원래 ServerLevel 비교, unloaded 경로 정리, 미로딩 경유지 차단, tick 예외 격리, 256개/몹 한도, 외부 overwrite 시도 제한.

남은 검증: 실제 외부 배포된 서드파티 모드·Mixin/Brain 간섭, 차원 변경/청크 언로드(현재 entity discard만 검증), 클라이언트 게임 구동, 장시간 성능·고부하, IDE Sync. **별도 소비자 Fabric JAR 두 개의 동일 Mob 경합**은 아래 새 섹션에서 실측했다. 이 API를 사용하지 않는 다른 모드의 직접 PathNavigation 호출을 완전히 배타 제어한다고 주장하지 않는다.

## 실제 전용 서버 통합 테스트 — 2026-11-16

- 준비: `run/eula.txt=eula=true` (사용자 명시 승인), `server-ip=127.0.0.1`, `server-port=25577`, `level-name=patrick-runtime-test-world`, `level-type=minecraft:flat`, `difficulty=peaceful`, `pause-when-empty-seconds=-1`. 테스트 외부 접근을 열지 않고 접속 플레이어 없이 서버 틱을 수행했다.
- 재현: `run/patrick-runtime-smoke.enabled` 존재 시에만 `src/main/java/comvyne/patrickstar/internal/PatrickRuntimeSmoke.java`가 내부 테스트를 활성화한다. 실제 `ServerLevel`에 Cow를 생성하고 엔티티 인덱스가 반영된 다음 틱에 `Patric.create/register`를 호출한다. 7개 단계는 결과 파일에 각 증명을 기록하며, 자동 종료가 아닌 콘솔 `stop`으로 서버를 정상 종료한다.
- 근거: `run/patrick-runtime-smoke-results.txt` **18 PASS / 0 FAIL**, `run/logs/latest.log`, 서버 콘솔의 `PTEST SUMMARY PASS=18 FAIL=0`.
- 단계 1: priority 100/200/300을 역순 등록해 **300→200→100** 목표를 실제 탐색하고 각 콜백 1회, 최종 위치 근접 확인.
- 단계 2: 지정된 Vec3 waypoint로 먼저 이동한 뒤 BlockPos 목적지에 도착.
- 단계 3: `null` 공급자와 예외 공급자(의도적 경고 로그)를 한 번씩 평가하되 다음 유효 경로가 성공.
- 단계 4: `lock(1800)` 동안 높은 priority 요청 대기, 실제 단조 시간 약 1.8초 후 선점, 고우선 경로 도달 후 저우선 경로 재개 및 도착.
- 단계 5: 실행 중인 정상 `Path`에 잘못된 고우선 공급자 두 개를 추가. **정상 Path 객체 identity가 그대로 유지되며 최종 목적지 도착**.
- 단계 6: `create()`만 호출했을 때 팩토리 실행 0회, Patrick MOVE Goal 설치 0개. `register(null)`도 false.
- 단계 7: 실제 이동 중 Cow discard 후 엔티티 인덱스 제거, 추가 등록 거부, Patrick MOVE Goal 제거 확인.

### 테스트가 실제로 발견하고 고친 결함

최초 확장 서버 실행에서는 `PTEST FAIL invalid-high-no-interrupt`: 실행 중인 경로가 있는데 더 높은 priority의 공급자 두 개가 `null`/예외를 내면 `Controller.selectOrPreempt()`가 **검증 전에 기존 PathNavigation을 stop**해 정상 경로가 한 번 새 Path로 재시작됐다(나머지 테스트는 PASS). 수정: lock 만료/우선순위 필터 후 후보의 `evaluatePending()` 및 `choose()`를 먼저 수행하고, **실제로 유효한 더 높은 우선순위 후보가 확정됐을 때만** 기존 경로를 해제한다. 수정 뒤 동일 빠른 회귀 **8/8 PASS**, 빠른 옵션을 제거한 최종 전체 회귀 **18/18 PASS**. 테스트 로그의 의도적 공급자 예외 경고는 서버 오류가 아니다.

첫 Cow 생성 즉시 같은 END_SERVER_TICK에서 `register`하면 서버 엔티티 인덱스에 아직 실리지 않아 false를 반환하는 것도 관찰했다. 이는 현재 `PathDirector.enqueue`의 실제 월드 등재 보장에 따른 결과다. 테스트는 다음 틱에 `ServerLevel.getEntity(id)==mob` 확인 후 등록해 재현 조건을 분리했다. 생성 직후 같은 틱에 즉시 요청하고 싶다면 등록 시점을 서버 엔티티 로드 후로 예약해야 한다.

최종 JAR 및 sources JAR SHA-256은 상단 산출물에 갱신했다. `run/` 개발 월드·로그·EULA 파일은 .gitignore 대상이며 배포 JAR에 포함되지 않는다. 내부 smoke 구현 클래스는 배포 JAR에 포함되지만 `patrick-runtime-smoke.enabled` 마커가 없는 일반 실행에서는 테스트 이벤트를 전혀 등록하지 않는다.

## 별도 Fabric 소비자 모드 JAR 2개의 실제 경합 — 2026-11-16

사용자가 두 독립 모드를 만들거나 실제 모드를 구해 충돌을 직접 시험하는 작업을 허용해, 외부 다운로드 없이 **서로 다른 ModInitializer와 Fabric Loader ID를 가진 두 개의 실행 가능한 Fabric 모드 JAR**를 만들었다. 두 모드는 `src/test/java/comvyne/patrickstar/consumers/` 아래에서 API의 공개 `Patric.create/register`만 호출하도록 작성했다. 이를 분리된 JAR로 패키징하는 도구는 `integration-consumers/pack-test-mods.ps1`이며, API 배포 JAR에는 이 두 소비자의 `*.class`가 없다.

| 모드 | 독립 파일 및 Loader ID | 등록 조건 | 경로 |
| --- | --- | --- | --- |
| Alpha | `build/test-consumers/jars/patrick_probe_alpha-1.0.0.jar`, `patrick_probe_alpha` | Cow ENTITY_LOAD 후 실제 서버 엔티티 인덱스 확인 | `priority(100).lock(1800)`; X+7 Vec3 경유지 → X+14 BlockPos 최종점, speed=0.72 |
| Beta | `build/test-consumers/jars/patrick_probe_beta-1.0.0.jar`, `patrick_probe_beta` | Alpha의 실제 첫 내비게이션 목표 확인 후, 동일 Cow에 등록 | `priority(500)`; Z+8 BlockPos 최종점, speed=1.15 |

공통 사전조건: `run/eula.txt=eula=true`(사용자 사전 승인), `server-ip=127.0.0.1`, `server-port=25577`, `pause-when-empty-seconds=-1`. 소비자 JAR은 각각 고유 `fabric.mod.json`과 `ModInitializer` 클래스를 가지며, `patrick_star_api` 및 Fabric API/Loader/Minecraft 버전 의존성을 선언했다. 생산자는 기존대로 **Fabric Amper settings.gradle.kts + mod.yaml만 유지**했다. 두 소비자 클래스의 컴파일은 생산자 `src/test` classpath를 사용했기 때문에 독립 Gradle 프로젝트의 의존성 다운로드 검증은 아니다.

검증 재현:

1. API 프로젝트에서 `..\fabric-amper.bat build` 실행.
2. `pwsh -NoProfile -File integration-consumers\pack-test-mods.ps1` 실행. 이 스크립트는 두 `build/test-consumers/jars/` JAR을 생성하고 개발용 `run/mods/`에만 복사한다. 인증/원격 게시 없음.
3. 개발 서버 `run/`에 빈 `patrick-consumer-integration.enabled`를 작성하고 `..\fabric-amper.bat server` 실행.
4. 메인 서버의 `PatrickConsumerIntegrationProbe`가 테스트 Cow를 생성한다. 두 모드는 서버 엔티티 로드 이벤트와 서버 틱을 독립적으로 받고 경로를 등록한다. 로그 `PCONSUMER ALPHA/BETA REGISTER true`, `FACTORY count=1`을 확인한다.
5. `run/patrick-dual-consumer-results.txt`의 `PCROSS SUMMARY PASS=7 FAIL=0` 검증 후 콘솔 `stop`으로 서버 종료. 활성화 마커와 개발 서버 모드 설치본은 종료 후 제거해 일반 실행에 영향을 주지 않는다. 빌드 산출 JAR들은 재현용으로 보존한다.

**최종 신설 콜드 월드 실행 증거:** `level-name=patrick-dual-consumer-cold-world`, Loader 46개(기존 44 + 두 독립 소비자), 실제 첫 월드 생성 후 force-loaded 2×2 chunks를 30 서버 틱 예열. `run/patrick-dual-consumer-results.txt`에 다음이 저장됨:

- `two-loader-mods`: 두 모드의 `isModLoaded`가 모두 true.
- `independent-public-api-register`: 두 `ModInitializer`가 동일한 Cow UUID에 실제 성공 등록, 개별 이벤트 확인.
- `high-priority-respects-alpha-lock`: Alpha 첫 목표 관찰 뒤 Beta 목표 최초 관찰 **1,839ms**, Alpha lock=1,800ms(관측 시점 차이를 고려한 테스트 판정 기준 1,550ms). 잠금 중 즉시 선점되지 않음.
- `independent-consumer-factories`: 두 모드 각각 별개의 경로 생성 람다를 실행하고 해당 태그 확인.
- `dual-mod-route-order`: Alpha waypoint → Beta 목적지 → Alpha waypoint 재개 → Alpha 최종점; **440 ticks**.
- `single-goal-and-cleanup`: 동시 Patrick MOVE Goal 최대 1개, 완료 후 0개.
- `dual-mod-path-completion`: Cow 실제 최종 블록 `{x=21, y=-60, z=8}` 근처 도착. 각 목표의 수평/수직 거리 판정은 1.5블록 허용치 사용.

최종 **PCROSS SUMMARY PASS=7 FAIL=0**, 서버 `stop` 정상 종료, Gradle runServer `BUILD SUCCESSFUL`. 앞서 다른 테스트 월드 첫 실행에서 Alpha의 경로가 시작되지 않아 `dual-mod-path-completion` 타임아웃을 확인했다. 당시엔 월드 초기화 직후 spawn·청크 로딩과 잔존 테스트 Cow 가능성이 혼재했으므로 API 자체의 결함으로 단정하지 않았다. 이후 정확한 `ENTITY_LOAD` 태그/시작 좌표/내비게이션 대상 및 상태를 로깅하고, **청크 로딩 뒤 30 틱 예열 + SERVER_STOPPING Cow 정리**를 추가했다. 따로 생성한 완전 신설 콜드 월드에서는 모두 통과했다. 초기 비재현 현상 자체가 완전히 규명됐다고 주장하지 않는다.

두 테스트 모드 JAR의 SHA-256:

- Alpha (comvyne 패키지로 재패키징): `10A3348814AD3E845463D7022AE9AC12F9F5DB546E2EA00165508D8CB223B425`
- Beta (comvyne 패키지로 재패키징): `444C5BBF7F514540FB5A113E128C7BFFF00A875705720E3B4780C000981D3C97`

확인 범위는 **두 실제 독립 Fabric Loader 소비자 모드가 Patrick API를 통해 경쟁하는 상황**이다. 외부 모드가 Patrick API에 참여하지 않고 직접 `PathNavigation.moveTo`나 Brain/Mixin 전역 패치를 사용하는 상황의 완전 배타성은 이 통과 결과로 보증하지 않는다.

## A/B/C 신규 오버로드 + 내부 중재기 리팩터링 검증 — 2026-11-16

사용자 확정 요구: 개발자에게 노출하는 `Patric` 서비스 **이름은 오직 `create`, `register` 두 개**로 유지하면서 `create`를 오버로드한다. 기존 `Function<Mob,PatricPath>` 방식은 완전히 호환되도록 보존한다. A는 `BlockPos`/`Vec3`, B는 바닐라 `Path`/`Supplier<Path>`, C는 `BiConsumer<Mob,NavigationSession>` 형태로 `NavigationSession.follow(Path,double)`, `moveTo(BlockPos,double)`, `moveTo(Vec3,double)`의 순서 있는 명령을 수집한다. `Goal` 직접 등록(D)은 지원하지 않는다. 동일 priority의 미실행 후보는 reachable(true) 우선 → 현재 기준 경로 길이 짧은 순 → 등록 FIFO이며 이미 실행 중인 동순위 경로는 비선점한다.

### 실제 변경 코드와 빌드

- `src/main/java/comvyne/patrickstar/api/Patric.java`: `create`의 5개 신규 오버로드 추가(기존 포함 총 6개), `register` 1개 유지. 정적 `Path`는 create 시점부터 노드 깊은 복제본으로 고정한다.
- `src/main/java/comvyne/patrickstar/api/NavigationSession.java` 신규: 만료되는 순서형 명령 수집기. 콜백을 종료한 뒤 같은 세션을 호출하면 `IllegalStateException`; null/잘못된 속도/NaN/무한 좌표 거부. 람다 안에서는 실제 내비게이션을 즉시 시작하지 않고 선택 이후 실행한다.
- `src/main/java/comvyne/patrickstar/api/PatricPathRegistry.java`: 기존 `.priority()`, `.lock()`만 공개 설정으로 유지. 등록 핸들 내부에 통합 실행 요청 저장.
- `src/main/java/comvyne/patrickstar/internal/PathDirector.java`: `Request/Plan/Point/Native` 단일 중재 계획, 네이티브 `PathNavigation.moveTo(Path,speed)`, Path node/커서 보호, 유효성/길이/노드 한도, 실행 중 경로 소유권(identity), 선점/복귀 시 정적 진행 커서 또는 동적 팩토리 재평가, 단일 `Goal.Flag.MOVE`, 잘못된 후보 격리.
- `src/test/java/comvyne/patrickstar/api/NavigationOverloadTest.java` 신규 JUnit **4/4 PASS**.
- `src/test/java/comvyne/patrickstar/api/NavigationSessionContractTest.java` 신규 JUnit **3/3 PASS**.
- 기존 `PublicApiContractTest` **6/6 PASS**, `PathPolicyTest` **4/4 PASS**. 합계 **17/17 PASS**.
- `..\fabric-amper.bat build`: Java 25 MC26.3/Loader0.20.1, `:compileJava`, `:compileTestJava`, `:test`, `:verifyFabricAmperJar`, `:jar`, `:sourcesJar`, `:build` 모두 `BUILD SUCCESSFUL`. 기존 `settings.gradle.kts + mod.yaml`만 유지했고 소비자 `build.gradle(.kts)`는 추가하지 않았다.

### 전용 서버 A/B/C 신규 런타임: 4 PASS / 0 FAIL

전용 서버에서 `run/patrick-overload-runtime.enabled`를 만들어 `..\fabric-amper.bat server` 실행. 실제 Cow를 생성하여 Flat 아레나 청크를 강제 로드하고 지면/엔티티 인덱스가 준비된 다음 서버 틱에서 공개 API를 호출했다. 테스트 코드는 `PatrickOverloadRuntimeProbe.java`에 opt-in으로 존재하며 일반 실행에서는 활성화되지 않는다. 1단계씩 실물 Cow를 생성/제거하고 경로 인식·실제 이동 거리·도착·내비게이션 Goal 해제를 확인했다.

| 항목 | 실제 서버 증거 (`run/patrick-overload-runtime-results.txt`) | 결과 |
| --- | --- | --- |
| P15 동순위 BlockPos/Vec3 | `far registered first; near Vec3 target/reached=true/true, far BlockPos target/reached=true/true, ticks=138` | PASS |
| P16 네이티브 Path 원본 불변 | `original cursor stayed=0; active path observed=true; real Cow arrived=true; ticks=105` | PASS |
| P17 Supplier 정상 경로 | `supplierCalls=1, active path observed=true, Cow reached=true, ticks=105` (register 시 실행되지 않고 지연 평가) | PASS (정상 이동만) |
| P18/P19 C 세션 정상/만료 | `callbackCalls=1, expiredThrows=true, activePathSteps=true/true/true, each waypoint reached, ticks=182` | PASS (정상 이동만) |

원문: **`POVERLOAD SUMMARY PASS=4 FAIL=0`**. 첫 서버 실행에서는 A 동순위와 B Supplier가 PASS했으나, 다른 두 시나리오가 Cow 생성 직후 한 틱만 지나 아직 지면에 완전히 놓이기 전에 `mob.getNavigation().createPath`가 null을 반환해 시작 전 실패했다(`PASS=2 FAIL=2`). 이 문제를 숨기지 않고 프로브가 Cow의 `tickCount >= 12` 및 `onGround()`를 확인한 뒤 사전 계산을 하도록 수정했다. 동일한 원래 실제 경로 검증을 유지한 최종 재실행이 위 **4/4 PASS**다.

### 변경 후 원래 기능 회귀

- 기존 opt-in `PatrickRuntimeSmoke`: **`PTEST SUMMARY PASS=18 FAIL=0`**. priority 300→200→100, 기존 빌더 waypoint, null/예외 격리, `lock(1800)`의 고우선 선점 지연 실측 **1,846ms**, 선점·재개, 무효 고우선 후보가 원래 Path identity를 유지하는지, 미등록 시 MOVE Goal 0개, unload 정리 재확인.
- 실제 독립 Fabric 모드 Alpha/Beta JAR 2개를 `integration-consumers/pack-test-mods.ps1`로 다시 만들고 `run/mods/`에 설치한 뒤 **`PCROSS SUMMARY PASS=7 FAIL=0`**. Loader 46개 및 `patrick_probe_alpha`/`patrick_probe_beta` 두 ID 실제 로드, 같은 Cow에게 등록, Alpha lock 후 Beta 선점 지연 **1,837ms**, 복귀·최종 도착 **440 ticks**, Patrick MOVE Goal 최대 1개/종료 0개. 이는 개별 JVM 모의 테스트가 아닌 Fabric dedicated server 실행이다.
- 세 실행의 최종 로그는 순서대로 `run/patrick-overload-runtime-results.txt`, `run/patrick-runtime-smoke-results.txt`, `run/patrick-dual-consumer-results.txt`. 각 테스트 종료 후 콘솔 `stop`으로 전용 서버 정상 종료했으며 활성화 마커 3개와 `run/mods/`의 Alpha/Beta 설치본은 제거했다. 패키징된 2개 테스트 JAR은 `build/test-consumers/jars`에 재현용으로 남겼다.

### 확정된 제한 및 미검증 시나리오

- `register`가 true라는 사실은 요청 접수만 뜻한다. `Path`가 오래됐거나 지형/월드·Mob 상태가 바뀌면 실제 내비게이션은 실패할 수 있다.
- `Supplier<Path>`/C는 선점 후 재개 때 재호출되도록 구현됐지만, **실제 게임에서 B/C가 중단·재개되면서 재생성되는 시나리오**는 이번 4개 실행 항목에 포함되지 않았다. 이는 P17/P19의 별도 하위 게이트로 남는다.
- C의 사용자가 `mob.getNavigation().moveTo`를 직접 호출하면 Patrick의 캡처/선택 제어를 벗어나므로 이를 자동 차단할 수 있다고 주장하지 않는다. 사용자는 C `NavigationSession`의 `follow/moveTo`를 호출해야 한다.
- 컴파일/JUnit 검증과 실물 Cow의 런타임 통과는 확인했지만, 외부 비참여 서드파티 AI/Brain/Mixin 충돌, 타 차원 변경, 장기간 TPS/성능, 클라이언트, IDE Sync, 여러 소비자의 별도 독립 Gradle 프로젝트 빌드 및 서드파티 코드 전체 호환성은 미검증이다.

최종 JAR과 sources JAR SHA-256은 본 보고서 상단의 값에 갱신했다. 테스트 프로브 클래스는 API JAR에 포함되나 실행 디렉터리에 별도 마커가 없으면 활성화되지 않는다. 외부 플랫폼에 게시하지 않았다.

## Java 패키지명 변경 — dev.patrickstar → comvyne.patrickstar (2026-11-16)

- 사용자 지정 새 Java 패키지 `comvyne.patrickstar`로 메인/테스트 소스 모두 이동. 공개 사용 예: `import comvyne.patrickstar.api.Patric;`. 기존 Java 서비스 메서드 및 Fabric mod ID `patrick_star_api`는 그대로 유지.
- `mod.yaml`의 Maven group `comvyne.patrickstar`, Fabric Loader 진입점 `comvyne.patrickstar.internal.PatrickStarMod`, `integration-consumers/pack-test-mods.ps1`의 모드 클래스·진입점 경로 및 `README.md`, `specs.md` 등 문서 참조를 같은 값으로 통일.
- `..\fabric-amper.bat build`: `:compileJava`, `:compileTestJava`, `:test`, `:jar`, `:sourcesJar`, `:verifyFabricAmperJar` 모두 **BUILD SUCCESSFUL**. 변경된 패키지명으로 새 XML `TEST-comvyne.patrickstar.*.xml` 네 개가 생성됐고 **17/17 PASS**.
- 최종 배포 JAR 검사: `jar tf`에 `comvyne/patrickstar/api/Patric.class` 등 새 경로만 존재하고 `dev/patrickstar/` 클래스가 없다. 자동 생성된 `fabric.mod.json`의 새 entrypoint 확인.
- `pwsh -NoProfile -File integration-consumers\pack-test-mods.ps1 -NoInstall` **PASS**. Alpha/Beta 각각 별도의 JAR에 `comvyne/patrickstar/consumers` 클래스가 정상 포함됐고 일반 서버 `run/mods`에는 설치하지 않았다.
- `..\fabric-amper.bat server`로 실제 MC26.3 dedicated server 부트 **PASS** (`Done` 출력, Loader 모드 로드 성공), 콘솔 `stop`으로 정상 종료. 테스트 활성화 마커 없이 일반 서버 부트를 확인했다. 기존의 4/4·18/18·7/7 경로 실행 결과는 패키지 변경 **이전**에 실행한 기록이며, Java 경로 변경 후 경로 이동 시나리오를 전부 재실행한 것으로 해석하지 않는다.
- Maven group과 Java 클래스 경로가 달라졌으므로 기존 `dev.patrickstar.api.*`를 import 하던 소비자 모드는 `comvyne.patrickstar.api.*`로 변경한 뒤 다시 컴파일해야 한다. 새 JAR SHA-256은 보고서 상단에 갱신했다.

## 공개 V1.0.0 릴리스 준비 — 2026-11-16

- GitHub 릴리스 태그 `V1.0.0` 요청에 맞춰 Fabric 버전을 `1.0.0+26.3`으로 변경. 이 릴리스의 아카이브 이름은 `patrick-star-api-1.0.0+26.3.jar` 및 `patrick-star-api-1.0.0+26.3-sources.jar`다. 기존 프로젝트 구조와 `comvyne.patrickstar.api` Java 패키지 및 `patrick_star_api` 모드 ID는 변경하지 않았다.
- 소스 저장소용 Gradle Wrapper 9.9.1을 생성해 `gradlew`, `gradlew.bat`, `gradle/wrapper/*`을 추가함으로써 기존 `jitpack.yml`의 `./gradlew` 경로를 제공했다. `.gitignore`는 개발용 `run/`(EULA/월드), `logs/`, `.gradle/`, `build/` 및 IDE/로컬 비밀 파일을 제외한다.
- Windows에서 `.\gradlew.bat clean build publishToMavenLocal`을 실제 실행했고 컴파일, **JUnit 17/17**, `verifyFabricAmperJar`, JAR와 sources JAR 생성 및 Maven 로컬 게시 작업까지 **BUILD SUCCESSFUL**.
- GitHub 릴리스 설명은 영문 `RELEASE_NOTES.md`에, 최종 JAR 체크섬은 릴리스 자산 `SHA256SUMS.txt`에 보관한다. 로컬 Maven 게시만 실행했으며 외부 Maven 배포는 별도 검증되지 않았다.
- 본 릴리스 이전에 실행한 A/B/C 서버 4/4, 기존 요청 18/18, Alpha/Beta 독립 모드 7/7은 코드 로직의 근거다. 이번 버전 변경은 모드 메타데이터와 빌드/패키징 식별자만 수정했다.
- V1.0.0의 실제 Fabric Loader 환경에서 `patrick_star_api 1.0.0+26.3` 모드 로딩, MC 26.3 dedicated server `Done` 로그 및 콘솔 `stop` 정상 종료를 확인했다. 이 추가 실행은 기존 경로 이동 게이트의 재실행이 아닌 **서버 부트 스모크**다.

## 등록 수명 주기 API v3 — 2026-11-17 (로컬 개발 변경)

- 사용자 결정에 맞춰 `Patrick.register(PatrickPathRegistry)`는 경로 완료 후에도 유지되는 지속형 기본 행동, `Patrick.oneShot(PatrickPathRegistry)`은 단발 이동 요청으로 구분했다. `Patrick.unregister(PatrickPathRegistry)`는 동일한 등록 핸들을 받으며, `.deferOneShot(boolean)` 기본값은 `false`다. 여섯 `create(Mob, ...)` 오버로드와 기존 `.priority(int)`/`.lock(int)` API 시그니처는 유지했다.
- `.deferOneShot(false)`에서는 현재 패시브의 lock 만료 뒤 One-Shot이 선점 가능하며, `.deferOneShot(true)`에서는 현재 패시브 경로가 완료될 때까지 One-Shot을 보류한다. One-Shot끼리는 priority → reachable → distance → FIFO를 적용하고, 완료된 One-Shot은 제거한다. 정적 지속형은 도착 후 휴식하고 Mob이 다시 멀어지면 활성화하며, 동적 지속형 경로는 성공 10틱·실패 40틱 이후 재평가한다.
- **실제 dedicated server MC 26.3 / Fabric Loader 0.20.1 / Java 25**에서 새 opt-in `PatrickLifecycleRuntimeProbe`로 실물 Cow 네 시나리오를 실행: `run/patrick-lifecycle-runtime-results.txt`에 **`PLIFECYCLE SUMMARY PASS=4 FAIL=0`** 기록. 검증 항목은 (1) 낮은 priority 단발 요청의 패시브 선점·목적지 도착·패시브 재개·핸들 해제·Goal 제거, (2) defer=true 보류·경로 완료 후 순서 양보, (3) 동시 동순위 단발 두 건의 가까운 목적지 우선 실행 및 큐 종료, (4) 동적 패시브 팩토리의 두 번째 경로 재계산·등록 해제다. 테스트 전용 마커 `run/patrick-lifecycle-runtime.enabled`는 실행 이후 삭제했으며 전용 서버 `stop` 정상 종료를 확인했다.
- **실제 버그 수정 근거:** 후보 도달성 평가 중 vanilla `navigation.createPath()`는 `getTargetPos()`를 바꿔도 기존 `getPath()` 객체는 유지할 수 있다. 이전 구현의 `releaseNavigation()`이 경로 객체 및 목표 좌표 두 조건의 일치를 요구하여 선점 때 소유 경로 정리를 건너뛰었고, 단발 이동 명령이 새 목적지로 시작됐어도 Mob이 이전 목적지로 계속 이동했다. 소유한 `Path` 객체의 identity를 기준으로 정지·외부 교체를 판정하도록 고친 뒤 같은 Cow 시나리오를 재실행해 **4/4 PASS**했다. 외부 모드가 소유한 다른 경로 객체는 정지하지 않는다.
- 최신 `\.\gradlew.bat clean build --no-daemon --console=plain` **BUILD SUCCESSFUL**, Java compile, JUnit **20/20 PASS** (PublicApiContractTest 7, PathPolicyTest 6, NavigationOverloadTest 4, NavigationSessionContractTest 3), `verifyFabricAmperJar`, jar·sourcesJar 모두 통과. `git diff --check` 통과. 테스트 코드를 새 oneShot 의미로 맞췄으나 **기존 opt-in 18/18, 독립 Alpha/Beta 7/7, A/B/C 4/4는 이번 소스 변경 후 dedicated server 재실행하지 않았음**. 이 결과들은 이전 V1.0.0 기반의 역사적 검증이다.
- **배포 구분:** 이번 v3 변경은 로컬 작업 트리에만 적용했다. 기존 공개 GitHub/JitPack 태그 `V1.0.0`, 기존 릴리스 JAR, GitHub `origin/main`은 수정·푸시하지 않았다. JitPack V1.0.0의 `register`는 여전히 기존 단발 의미로, 현재 로컬 빌드와 동작이 다르므로 배포 시 호환성 및 버전 전략 검토가 필요하다.

### 공개 Java API 오타 수정 — 2026-11-17

- 사용자 요청으로 개발 버전 공개 클래스 이름을 `Patric` → `Patrick`, `PatricPath` → `PatrickPath`, `PatricPathRegistry` → `PatrickPathRegistry`로 일괄 수정했다. 소스 파일명·선언·내부 경로 제공자·실물 Mob 프로브·독립 소비자 테스트 모드·JUnit·README·specs.md를 같은 이름으로 정리했다. **Java 패키지 `comvyne.patrickstar.api`와 Fabric mod ID `patrick_star_api`는 그대로 유지했다.** 역사적 `RELEASE_NOTES.md`/V1.0.0 배포 바이너리는 수정하지 않았다.
- 이름 변경 이후 `\.\gradlew.bat clean build --no-daemon --console=plain` **BUILD SUCCESSFUL**, JUnit **20/20 PASS**, `verifyFabricAmperJar` PASS. 생성 JAR의 공개 API 항목은 `Patrick.class`, `PatrickPath.class`, `PatrickPath$Builder.class`, `PatrickPathRegistry.class`로 확인했고 오래된 `Patric*.class`는 포함되지 않았다.
- 이름 변경 후 새 Java 클래스들로 opt-in dedicated server 검증을 **다시 실행**했다. 실제 Cow의 요청 중재 4개 시나리오 재실행 결과 `run/patrick-lifecycle-runtime-results.txt`에 **`PLIFECYCLE SUMMARY PASS=4 FAIL=0`** (2026-11-17 기록). 서버는 콘솔 `stop`으로 정상 종료하고 테스트 마커는 삭제했다.
- GitHub `origin/main`과 공개 JitPack `V1.0.0`은 변경하지 않았다. **기존 V1.0.0 사용자는 이전 공개 클래스명을 사용해야 하고, 이름 변경 및 새로운 지속/단발 API는 후속 릴리스에서 제공해야 한다.**

## V2.0.0 릴리스 검증 — 2026-11-17

- 사용자 요청에 따라 `mod.yaml`의 모드 버전을 **`2.0.0+26.3`**으로 올리고 새 Git 태그 **`V2.0.0`** 기준 README/JitPack 좌표, Fabric Loader 의존 예제 `patrick_star_api >=2.0.0`, 독립 소비자 검증 JAR의 의존조건 `>=2.0.0+26.3`을 업데이트했다. `settings.gradle.kts`의 Fabric Amper 부트스트랩 v0.1.3은 그대로다.
- `.\gradlew.bat clean build --no-daemon --console=plain` **BUILD SUCCESSFUL**: `compileJava`, `compileTestJava`, JUnit **20/20 PASS** (7+6+4+3), `verifyFabricAmperJar`, `jar`, `sourcesJar` 및 `build`. `integration-consumers/pack-test-mods.ps1 -NoInstall`로 독립 Alpha/Beta 소비자 JAR 2개도 컴파일된 테스트 클래스로 새로 생성했다.
- **최종 변경 버전의 실제 MC26.3 전용 서버에서 모두 새로 실행:** `run/patrick-lifecycle-runtime-results.txt` **PLIFECYCLE 4/4 PASS**, `run/patrick-overload-runtime-results.txt` **POVERLOAD 4/4 PASS**, `run/patrick-dual-consumer-results.txt` **PCROSS 7/7 PASS** (실제 Loader가 `patrick_probe_alpha`/`patrick_probe_beta` 별도 ID로 로드), `run/patrick-runtime-smoke-results.txt` **PTEST 18/18 PASS**. 모두 FAIL=0으로 확인했으며, 서버 시작 로그에서 `patrick_star_api 2.0.0+26.3` 로드를 확인했다. 서버는 각각 콘솔 `stop`으로 정상 종료했고, 테스트 활성화 마커와 `run/mods`에 설치했던 별도 소비자 JAR 2개는 제거했다.
- 배포 JAR: `build/libs/patrick-star-api-2.0.0+26.3.jar`, SHA-256 **CFC592400EFA9771F340B1EC1B0743020DCB6206463FA5A335786983CF520D08**. 소스 JAR: `build/libs/patrick-star-api-2.0.0+26.3-sources.jar`, SHA-256 **99F93392E1DDEC8B1788F8DF1ABCB66376CC5062F7394D39BBDED19F9156C0CD**. 런타임 JAR는 루트 `fabric.mod.json` 한 개와 새 `Patrick.class`/`PatrickPath.class`/`PatrickPathRegistry.class`를 포함하며 이전 `Patric*.class`는 포함하지 않는다.
- **비호환성:** 기존 V1.0.0의 `Patric` 공개 타입, 단발 의미의 `register`를 그대로 쓰는 소비자 모드는 V2.0.0에 맞게 import와 등록 코드를 수정하고 재컴파일해야 한다. Fabric 모드 ID `patrick_star_api`와 Java 패키지 `comvyne.patrickstar.api`는 유지한다.

## V2.0.1 JavaDoc 패치 릴리스 검증 — 2026-11-18

- 공개 API 타입 5개(`Patrick`, `PatrickPathRegistry`, `PatrickPath`, `PatrickPath.Builder`, `NavigationSession`)의 공개 메서드 25개 전체에 영문 JavaDoc을 작성했다. 매개변수, 반환값, 해당하는 예외, 기본값, 실행 시점, 서버 스레드 및 사용 수명 조건을 명시했다. Java 소스 변경은 JavaDoc 주석만이며 실행 코드와 공개 시그니처는 변경하지 않았다.
- 모드 버전 `2.0.1+26.3`, JitPack 태그 `V2.0.1`, Loader 의존 예제 `>=2.0.1` 및 현재 설치/다운로드 경로를 갱신했다. V2.0.0 실측 기록과 기존 릴리스는 유지한다.
- `.\gradlew.bat clean build javadoc --no-daemon --console=plain` **BUILD SUCCESSFUL** (33초): JUnit **20/20 PASS**, `verifyFabricAmperJar` PASS, 런타임 및 소스 JAR 생성, JavaDoc HTML 생성 PASS. 공개 API의 JavaDoc 경고는 없으며 기존 내부 구현 클래스에서 31개 경고가 발생했다. `build/docs/javadoc/comvyne/patrickstar/api/`에서 공개 타입 5개의 HTML을 확인했다.
- `git diff -U0`의 API Java 변경분이 모두 주석인 것을 확인했다. JAR 엔트리 확인: 루트 `fabric.mod.json` 1개 및 공개 API 클래스 5개, 소스 JAR에 JavaDoc을 포함한 API 소스 4개. 이전 V2.0.0의 dedicated server 33개 검증은 동작 변경이 없는 이번 문서 패치에서 새로 실행하지 않았다.
- 런타임 JAR `patrick-star-api-2.0.1+26.3.jar` SHA-256: **2226AE4E54545A5DC66C44C6191BAB39C4E308EAC3EA6F458745FA20C6B25CFC**.
- 소스 JAR `patrick-star-api-2.0.1+26.3-sources.jar` SHA-256: **4D504F88A15224E85E94F781D129F6A5FC5A20F03F24A9C4008129A1B256D1D1**.
