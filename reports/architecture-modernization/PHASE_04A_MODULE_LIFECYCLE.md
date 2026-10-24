# Phase 4A — Module Dependencies & Lifecycle Patterns

## Baseline and scope

- Baseline: clean local `master`, `9a3557946908148d23184106bc75c959d2fca9ee`, matching the observed integrated remote commit.
- Git ancestry verified: `a8694ca91a968c34f2f1d8dcd4afaeebb1532409` (Phase 3C) is an ancestor of HEAD. No merge/rebase/reset/cherry-pick performed.
- Work branch: `phase/module-lifecycle-patterns`.
- Actual canonical baseline: Maven clean package PASS; Spotless PASS; **189 discovered/executed/passed harnesses, 29,447 checks; Failed=0, Missing/Invalid=0**.
- Pilot: FriendModule and FriendMenu lifecycle only. No changes to ModuleManager, CoreModule, composition order, FriendService, FriendCommand, repository, aliases, permissions, schema, translation keys, gameplay, GUI layout or reload participants. Clan inspected for comparison only.

## Architecture and ownership findings

Construction dependencies remain the public seven-argument constructor: JavaPlugin, ConfigService, PlayerModule, SocialModule, IdentityModule, MessageService and CommandHelpRenderer. They are created/wired explicitly in `VapeeCore.onEnable`; borrowed module services must already be active.

```text
VapeeCore composition root / explicit registration order
  Localization -> Player -> Social -> Identity -> Friend -> Presence / later consumers
                                                  |
                 +--------------------------------+---------------------+
                 | Runtime acquisition                                  |
                 +-- PlayerModule -> PlayerSettingsService (borrowed)     |
                 +-- SocialModule -> SocialService (borrowed)             |
                 +-- IdentityModule -> PlayerIdentityService (borrowed)   |
                 +-- ConfigService::getFriendLimits (live supplier)       |
                 +-- MessageService -> LocalizationService (borrowed)     |
                 +-- CommandHelpRenderer (borrowed)                       |
                 +-- JavaPlugin -> Server, Logger, PluginCommand, path    |
                 | Owned runtime                                         |
                 +-- FriendService -> FileFriendRepository / policy / clock
                 +-- FriendMenu -> two observer callbacks / active views
                 +-- FriendMenuListener -> Bukkit registration
                 +-- FriendCommand -> executor + completer bindings
```

The diagram shows relative relevant order, not every intervening production module. The complete production list remains authoritative. Operationally the existing FriendRequestPolicy consults ignore state and request privacy; it is not duplicated in the module or GUI. Identity owns online/offline resolution; localization occurs at the message/menu boundary. FriendService owns relationship/request snapshots and commits through its repository. FriendModule creates that runtime but does not acquire ownership of foreign services or Bukkit's PluginCommand descriptor.

The complete creator/owner/acquisition/availability/release table is maintained in [MODULE_DEVELOPMENT.md](../../docs/MODULE_DEVELOPMENT.md). JavaPlugin supplies actual Paper boundaries; ConfigService's supplier and the narrow policy are already appropriate dependencies. Whole Player/Social/Identity module references could later be narrowed if acquisition timing and fresh runtime semantics remain explicit. This phase deliberately retains the public constructor.

## Confirmed original defects

FriendModule protected registration and two setters, but observer startup, publication and final logging followed that catch block. A partial observer failure could leave a handler and command bindings with incomplete runtime fields. A final log failure could leave a fully registered runtime that ModuleManager never tracked. The catch performed sequential unprotected cleanup and could mask its primary exception.

FriendMenu set `observingChanges` only after a successful second registration; a registration throwing after effect could bypass removal. Locale unsubscribe preceded friend unsubscribe and view closure without independent failure boundaries. Disable likewise allowed an earlier failing cleanup to skip later resources/reference release. No duplicate-enable guard prevented replacement of active services or duplicated callbacks.

## Implementation

### Enable and publication

FriendModule acquires dependencies, constructs local runtime objects, registers its listener, sets executor, sets completer, starts both menu observers, publishes fields and finishes logging in one RuntimeException recovery boundary. Public getters become usable only after the last stage succeeds. `active`/`enabling` guard duplicate and reentrant enable before touching the active runtime; the rejected call does not trigger cleanup of valid resources.

Five package-local EnableStage checkpoints follow actual operations (listener, executor, completer, observer start, runtime publication). Public construction supplies a no-op. Observer faults use the actual underlying callback sets; final logging failure uses a real logger Handler. No publicly accessible fault API, scanner or lifecycle framework was added.

### Common rollback / disable path

One module cleanup method is used by failed enable and ordinary disable:

1. Shutdown owned menu while its close handlers are still installed.
2. Unregister the owning FriendMenuListener.
3. Reset executor to Bukkit's plugin fallback.
4. Reset tab completer independently to null.
5. Release runtime fields and deny getters.

Menu shutdown independently removes locale and friend callbacks, even after partially successful startup, then closes/detaches views in finally. Each player's current inventory must be identical to the tracked owned inventory before close; foreign inventories are untouched. Existing per-view logging and final registry clearing continue processing despite an individual close exception. Temporary renderer/presence references are released.

Cleanup failures attach resource-context wrappers as suppressed exceptions to the original startup failure. Ordinary disable surfaces an aggregate RuntimeException for ModuleManager's existing log-and-continue contract. Normal successful cleanup is quiet. Repeated disable is inert after references are released; retry and normal re-enable create fresh service/menu/listener/command objects and reload durable friendships without rewriting them during disable.

If an external unsubscribe refuses removal before its effect, cleanup reports failure rather than claiming the registration disappeared. The menu test proves that independent resources are still released and a subsequent menu shutdown can retry the refused removal. Normal existing observer implementations simply remove from their owned Sets; no new ownership registry was introduced.

## Measured complexity

Measurement unit: five independently installed registrations/bindings (one listener, two command bindings, two observer subscriptions). Publication and final logging are two additional observable startup stages. Pure object construction is listed separately rather than counted as an installed registration.

| Metric | Before | After |
|---|---|---|
| Independent registration/binding acquisition stages | 5 | 5 |
| Observable registration/publication/log stages protected by module recovery | 3 of 7 | 7 of 7 |
| Dependency acquisition / local construction inside same recovery boundary | No (except command construction) | Yes |
| Module cleanup implementations | 2 divergent sequences | 1 common method |
| Explicit lifecycle resource categories | 5 registrations + active views + runtime fields | Same 7 categories; all covered by independent cleanup |
| Enumerated late-start leak paths | 2: partial observer startup; final startup log | 0 for these paths under tested release APIs |
| Additional enumerated shutdown short-circuit paths | 2: locale remove skips other observer/views; menu shutdown skips later module cleanup/reference clearing | 0 in controlled after-effect failures |
| Independent real-module startup failure points behaviorally injected | 0 in old FriendLifecycleHarness (source assertions only) | 11: missing dependency, missing command, registration after effect, five checkpoints, locale add after effect, friend add after effect, final log |
| Production classes modified | — | 2 |
| Existing / newly added Java classes in this change | — | 4 existing modified, 3 new test classes |

Counts describe the bounded inspected paths, not an assertion that arbitrary permanently failing external APIs can always be forced to release a resource. Added code improves explicit ownership/failure coverage; line reduction is not claimed.

## Tests and security

- `FriendModuleLifecycleHarness`: real FriendModule, real PluginCommand, real HandlerLists; five handlers for one listener; successful acquisition, missing descriptors/dependency, partial registration, both setters, partial observers, publication, final log, simultaneous cleanup failures, exception identity, repeated disable, retry, double/reentrant enable, fresh references and actual disposable friendship persistence.
- `FriendMenuLifecycleHarness`: real rendering via existing fixture; partial observer registration, independent removals, multiple views, foreign inventory exclusion, real refusal before unsubscribe effect and retry, stale/forged holder rejection across menu runtimes, permission revocation and authorized UUID-target commit.
- `ModuleExtensionHarness`: test-only TEST_EXTENSION_MODULE and narrow dependency/later modules with the real ModuleManager. Explicit registration/order, usable dependency, partial self-cleanup, reverse predecessor rollback, duplicate-name rejection, retry/fresh runtime, independent shutdown despite a later failure. Separate synthetic composition evidence, not a substitute for the Friend proof. Existing Moderation/Utility manager coverage is retained.
- `FriendLifecycleHarness`: two source-spelling lifecycle assertions replaced by stronger behavior above. Stable constructor/CoreModule/no-ReloadParticipant, explicit order, module count, data path/live limits, no scheduler/shutdown rewrite, no bundled friends.yml, alias and permission assertions retained.
- `PresenceLifecycleHarness`: its already-owned Friend dependency shell now supplies `active=true` along with its service. The shell never called Friend enable; the new getter guard correctly rejected that previously incomplete representation. Presence's 24 behavior checks and the real Friend lifecycle tests remain unchanged; no fixture performs Friend cleanup to obtain a pass.
- Existing Friend command, integration, service, persistence, domain, composition/layout/localization/security/pagination/lobby-item harness entry points remain available. GUI authority still requires owner UUID, exact holder/inventory, active registry, current top inventory, current permission, allowed clicks and runtime target mapping; no presentation-based authorization.

Initial focused test construction exposed an invalid holder layout accessor and a wrong expected handler count (four rather than five); both test assumptions were corrected against the actual API. These initial attempts are not reported as passes.

### Compiled negative controls

`scripts/tests/friend-lifecycle.Tests.ps1` compiles isolated temporary classpath overlays; no original production class file or operator file is overwritten. Each compile must exit zero and the child harness must fail its named behavior. Positive controls run before and after overlays.

| Control | Deliberate defect | Observed contract |
|---|---|---|
| N1 | Omit listener rollback | Registered handler leak |
| N2 | Omit executor cleanup | Stale command executor |
| N3 | Omit completer cleanup | Stale completer |
| N4 | Omit locale unsubscribe | Stale locale observer |
| N5 | Omit friend unsubscribe | Stale friend callback |
| N6 | Skip view closure | Active view registry retained |
| N7 | Replace primary exception | Exact startup exception identity lost |
| N8 | Permit double enable after failed-start retry | Duplicate listener/observer |

Executed result: **8 compiled variants detected; all compile exits 0, all intended behavior exits nonzero; positive controls pass before and after**. Focused counts at execution: real module 310, menu 31, synthetic module 11. Evidence: `target/verification/friend-lifecycle/` (copied outside target before canonical clean).

## Final verification and native acceptance

The first complete final run discovered/executed all 192 harnesses, with 191 passing and one failure: PresenceLifecycleHarness's dependency shell omitted Friend's new active-state field. That fixture was corrected to represent its intended already-enabled dependency, without changing production or weakening assertions. Its focused rerun passed all **24 checks**. The eight compiled controls and their positive controls were rerun successfully on the final sources after the bounded formatting changes. The failed canonical run is not counted as a passing verification.

The complete repeated `scripts/verify.ps1 -Offline -MavenRepository 'C:/Users/mehdi/.m2/repository'` passed:

| Final verification | Actual result |
|---|---|
| Maven clean package | PASS |
| Spotless check, unchanged six-file allowlist | PASS |
| Discovered / executed / passed harnesses | 192 / 192 / 192 |
| Total checks | 29,808 |
| Failures | 0 |
| Missing/invalid | 0 |
| Baseline entry points retained | All 189 |
| Added entry points | FriendModuleLifecycleHarness, FriendMenuLifecycleHarness, ModuleExtensionHarness |

Receipt comparison accounts for the exact check delta: new harnesses +310/+31/+11; FriendLifecycleHarness 12→10 as two brittle checks are replaced by real behavior; GuiAuthoringAuditHarness 221→224 for three new test source files; RepoLinkHarness 51→59 for eight maintained guide links. All other baseline entry points retain their counts, including PresenceLifecycleHarness at 24. Overall delta: **+3 harnesses, +361 checks**. Counts are read from actual receipts, not used as a passing threshold.

Final receipt and inventory: `target/verification/harnesses/results.json` and `inventory.txt`. Baseline/final receipts, inventories, full logs and supplemental controls are also retained outside the clean-build directory under `C:/Users/mehdi/AppData/Local/Temp/vapeecore-phase04a-evidence/` and the `vapeecore-phase04a-*` evidence files.

Native acceptance executed on **2026-10-25, Europe/Berlin**, Paper **1.21.11-132**, LuckPerms **5.5.84**, JDK 21. The disposable local server bound only to `127.0.0.1:25589`. The native API probe passed **46 checks** at 00:38:00. After the test-only Presence-fixture correction and a fresh canonical package, the same native acceptance was repeated against the exact final JAR: **46 checks passed at 00:43:31**. In both runs all 28 modules started and received clean reverse-order disable, followed by clean server shutdown and process exit. Combined stdout/stderr contained **0 ERROR/SEVERE** in each run. Expected offline-mode/version notices are warnings, not claimed absent.

Verified normal Friend runtime initialization, `/friend` and `/friends` resolving to the same command, current executor/completer, exactly one listener with five event handlers, locale/change subscriptions, permission denial and completion denial, authorized help/completion, actual holder-bound native inventory opening and navigation, command-created request and GUI acceptance between two temporary ServerPlayers, UUID target mappings, permission revocation, foreign inventory preservation, owned view closure, observer/handler/command cleanup, stale inventory rejection, durable friendship bytes unchanged by shutdown, and repeated disable.

This is native Paper API/console acceptance with embedded network channels, **not a real Minecraft-client or visual acceptance test**. Intentionally broken startup paths were tested in standalone controlled fixtures, not injected into any live server. Native probe compiled and passed on its first execution; no production correction was needed from the smoke test.

Final built and natively tested plugin JAR SHA-256: `812bace9f0f3594509d5fbee539a87174c54b75d892299d6e69df6746bc5f27e` (both files compared equal). First native build: `b2d4a645f82207b6b22e1105c27f5955dfceb1a391aee936daa21ce9bf9df753`.

Evidence retained outside the repository under `C:/Users/mehdi/AppData/Local/Temp/vapeecore-phase04a-evidence/`: native stdout/stderr, result JSON, probe source and compile log; the exact final artifact run is under `native-final/`. Disposable Paper servers/worlds/data and ignored workspace probe directories were removed after each process exit.

## Data isolation

Before work, SHA-256/paths of all 22 installed operator files including deployed VapeeCore JAR were captured outside the repository. All standalone fixtures use disposable directories. Native acceptance used a copied Paper/LuckPerms server, separate plugin data and temporary players. After the final native cleanup the complete live file-path set and all 22 original file hashes were confirmed identical. No real `friends.yml`, player profile, language override or deployed JAR was changed; no dev-server deployment took place.

## Changed files and completion

- Production: `src/main/java/dev/vapee/core/friend/FriendModule.java`; `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java`.
- Tests: `src/test/java/dev/vapee/core/friend/FriendLifecycleHarness.java`; `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java`; new `FriendModuleLifecycleHarness.java`, `friend/gui/FriendMenuLifecycleHarness.java`, `module/ModuleExtensionHarness.java`.
- Supplemental validation: `scripts/tests/friend-lifecycle.Tests.ps1`.
- Maintained docs: `README.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPER_GUIDE.md`, new `docs/MODULE_DEVELOPMENT.md`.
- This new report. Existing historical reports untouched. Spotless enforcement remains the original six-file pilot; new Java files formatted individually.

Final pre-commit review: `git diff --check` PASS; exactly **13 intended paths** (seven modified, six new) listed above, with no operator files, profiles, generated JARs, temporary worlds, probe classes or unrelated formatting. The one local commit uses `Harden friend module lifecycle and extension patterns`; its full SHA and post-commit working-tree status are returned in the completion response. No push, merge or Phase 4B implementation was performed.

## Prioritized Phase 4B handoff (deferred)

1. Review the similar late `startUpdates()`/publication/log pattern in ClanModule as a separate targeted lifecycle task, with the same real failure evidence before migration.
2. Consider replacing whole module constructor dependencies with narrow service/provider boundaries only when dependency availability, shutdown order and fresh-runtime reacquisition are preserved. FriendRequestPolicy and the live limits supplier already provide narrow seams worth retaining.
3. Audit consumer references across runtime restart and clarify service lifetime rather than introducing a global service locator. Persistent domain state remains in its current owner.
4. Apply additional module hardening only to measured defects with focused regression/native evidence. No global dependency engine or lifecycle abstraction follows automatically from this pilot.
