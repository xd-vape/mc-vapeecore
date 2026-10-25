# Phase 5C — Player Lifecycle & Persistence Safety

Date: 2026-10-25, Europe/Berlin. Bounded production safety correction for the Player owner. No
gameplay, command, permission, localization, configuration default, schema, module order or reload
membership changes. The separate development-environment branch was neither used nor modified.

## Baseline and toolchain

- Repository: `xd-vape/mc-vapeecore`; observed SSH origin. Initial local `master` was clean and
  exactly `4d8b763089b253fcaa5088378ff00629321cb712`, equal to origin/master. Phase 5B
  `9b6c4bdca94f8357721dd253618507352d9445d1` is an ancestor (exit 0).
- Requested branch: `phase/player-lifecycle-safety`. No reset, rebase, cherry-pick, merge, fetch,
  push or changes to `phase/module-lifecycle-audit`.
- No applicable AGENTS.md was found in the repository or its ancestor directories.
- Windows 11 x64; installed Oracle JDK **21.0.12.1**, complete java/javac/javap toolchain;
  Maven **3.9.16** from IntelliJ IDEA 2026.2.3; PowerShell **7.6.5** from the current Codex runtime.
  Paths were checked afresh. JAVA_HOME was set only for command processes to the verified
  `C:\Program Files\Java\jdk-21.0.12.1`; no installation or permanent environment change.
- Canonical unchanged baseline: `pwsh -File ./scripts/verify.ps1`, exit **0**; Maven clean package
  PASS, Spotless PASS, **197 discovered/executed/passed, 31,329 checks, Failed=0,
  Missing/Invalid=0**. Production edits began after that completed success.
- Real-file tests ran outside the filesystem sandbox, whose restrictions previously prevented
  atomic replacement in disposable temporary directories. No repository behavior was weakened
  to bypass filesystem or schema safety.

Stable evidence root outside the checkout:
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase05c-evidence`.
It contains original production sources, the independently compiled pre-fix probe/source/log,
baseline/final canonical receipts and inventories, compiled-control logs, native probe/source/log,
synthetic profiles and full operator inventories. The previous Phase 5B evidence was available as
a reference; inaccessible old Windows-profile evidence was not used to invent current results.

## Original findings and executable pre-fix observations

`PlayerModule.enable()` initialized storage and constructed services/listener, called Bukkit
registration, and published fields only after registration returned. There was no rollback or
double/reentrant guard. `disable()` unregistered before saving, called the public log-and-continue
`saveAll()`, then unconditionally cleared the cache and all fields. Getters tested only field presence.
`PlayerListener` had no module-generation authority check.

`PlayerPreFixProbe.java` was compiled against the unmodified baseline and real ModuleRuntimeFixture,
FilePlayerRepository, HandlerLists and disposable profile YAML. Observations occur before fixture
safety teardown:

| Reproduction | Actual baseline result |
|---|---|
| Registration throws after its real effect | Original registration exception, **2 handlers remain**, Player getter unavailable |
| Abandoned listener receives direct join callback | It still creates a durable profile through its abandoned PlayerService |
| Call disable after failed startup | Still **2 handlers**; unpublished fields give production cleanup no listener reference |
| Retry enable | **4 handlers**, two listener/service generations |
| A saves, B has a real future-schema conflict | A's unique unsaved name reaches disk; shutdown returns **without exception**; B's unique unsaved state is no longer in the loaded cache; cache size **0** |
| External HandlerList refuses its own removal before effect | Disable throws; A remains at its old disk name; both profiles still loaded; **1 refused handler remains** |
| Old quit callback after that failed shutdown | It unexpectedly saves/evicts B; no activation gate prevents the old handler operating |

No fake save counter or fixture cache clearing substitutes for these observations. The controlled
future version exists only inside temporary profile directories. Production repository protection
correctly refused the save; the lifecycle incorrectly discarded the in-memory state afterward.

## Ownership and compatibility decision

| Resource | Owner / release responsibility |
|---|---|
| FilePlayerRepository and name index | Player runtime; repository keeps file/schema/index publication authority |
| PlayerService and loaded CorePlayer map | PlayerModule runtime / PlayerService; exactly one aggregate cache |
| PlayerSettingsService | Same Player runtime; no independent file/cache |
| PlayerListener and its Bukkit registration | PlayerModule; Bukkit registry is an external API |
| Profile YAML, validation and migration policy | FilePlayerRepository and unchanged PlayerFileSchema |
| Atomic candidate/replacement/recovery | Unchanged SafeFileWriter |
| ConfigService, MessageService, plugin/server | Borrowed stable dependencies; Player does not stop them |
| Production start/reverse-stop ordering | Unchanged ModuleManager and VapeeCore composition root |

Call-site inspection found PlayerModule as the only production `saveAll()` caller. Existing test
callers include RewardModuleLifecycleHarness and PresenceDependencyBoundaryHarness. Public
`void saveAll()` keeps its signature, INFO/per-profile SEVERE messages, RuntimeException isolation,
continuation and lack of an aggregate exception for ordinary profile failures. A narrow package-local
`saveAllOrThrow()` uses the same loop and requests a complete result for module cleanup.

After all profiles are attempted, package-local `SaveFailures` associates each exact failed UUID
with its original RuntimeException in an immutable map and contextual suppressed causes. Successful
files stay committed. Logging exceptions are deferred until independent save attempts finish, so
a diagnostic cannot stop later profile commits. A pure diagnostic failure remains observable and
conservatively retains the service for another cleanup attempt. No new public save-result framework,
multi-profile transaction, event, asynchronous writer or repository implementation was introduced.

`unloadPlayer()` remains save-before-evict. Normal join behavior, existing name/last-join updates,
first-join creation, strict invalid/future refusal, localized failure disconnect and debug messaging
are unchanged. The public four-argument PlayerListener constructor remains available; only the
module-owned listener receives the additional package-local generation gate.

## Implemented lifecycle and failure contract

1. Reject double/reentrant enable or lifecycle overlap before acquisition. Reject fresh enable if
   a previous cleanup remains pending, carrying the retained previous diagnostic as its cause.
2. Protect dependency access, storage initialization, service/listener creation, real registration
   and publication in one startup boundary. Package-local no-op checkpoints follow real operations.
   Getter access remains closed while enabling, including after fields are assigned.
3. On startup failure clear published references, revoke authority, clean the acquired listener
   and service, attach cleanup causes, retain unresolved ownership and rethrow the **same exception
   object**. Registration failures before and after their external effect are handled.
4. On disable mark the runtime inactive before external cleanup by clearing its published fields.
   The listener checks that its captured exact PlayerService is still the current module service
   and no lifecycle change is underway. `plugin.isEnabled()` is not the generation discriminator.
5. Attempt listener removal and strict aggregate saves independently. Unregister failure cannot
   skip saving; profile failure cannot skip another player. Both causes remain visible together.
6. Clear the loaded cache only after the entire strict save returns successfully. Release the
   listener reference independently after successful unregister. Privately retain only unresolved
   resources: a service with its existing cache and/or a listener whose release is uncertain.
7. Keep both normal getters closed after incomplete disable. An explicit later `disable()` retries
   retained resources using the same original CorePlayer objects. Fresh enable is allowed only
   after that cleanup succeeds. No background or automatic scheduled retry exists.

The retained service may include already saved players until the whole batch succeeds; they can be
saved again on retry. This is a temporary ownership reference, not a second permanent cache or an
active duplicate generation. A thrown unregister-after-effect remains an honestly reported failure
and is conservatively retried, even when inspection shows that handler already removed.

The SEVERE **INCOMPLETE PLAYER SHUTDOWN** message includes exact affected UUIDs through the
aggregate exception, original causes, explicit same-process disable retry, required operator
intervention and **JVM exit loses unsaved changes**. Getter closure does not revoke arbitrary Java
references deliberately retained by external consumers; production reverse dependency shutdown
remains essential. No extra startup INFO message was added.

## Multi-player and cleanup matrix

Real profiles A/B/C have deterministic verified save traversal and distinguishable unsaved names.
C demonstrates a successful commit after either A or B fails; an unrelated future-version profile
is checked byte-for-byte throughout.

| Scenario | Demonstrated corrected result |
|---|---|
| A/B/C save | All durable, cache released, normal repeated disable safe |
| A fails, B/C save | Later successful files durable; A exact unsaved state retained/reported |
| A saves, B fails, C saves | Earlier success retained and later C committed; B retained/reported |
| A/B fail, C saves | Both exact UUID/cause entries available; C durable |
| Unresolved save retry | Failure remains visible, successful files and all conflict bytes unchanged |
| Enable while pending | Refused; no replacement runtime or handler registration, no file writes |
| Operator-reconciled temporary future conflict | Explicit disable retry commits original unique unsaved values, then permits a fresh runtime |
| Unregister refuses before its own effect | Actual remaining handler reported; all required saves still attempted; stale callbacks inert |
| Unregister throws after effect | Actual zero remaining handlers observed; exception retained; repeat disable reconciles uncertainty |
| Unregister plus B save fail | Both independent root causes available; successful A remains durable; retry saves original B |
| Existing recovery marker | Target and marker bytes preserved through repeated failure; marker never automatically deleted |
| Failed Settings mutation | Exact value rollback; no successful Settings observer invented |
| Whole aggregate fails then retries | Same aggregate identity retained; Settings, visibility, Wallet, Social, online reward, all quest statuses and daily cycle survive |

The aggregate retry includes a deferred 25-Coin grant (**1234 → 1259**), unsaved sound/mode/Social
changes, online progress **600 → 777**, daily cycle **2026-10-25 → 2026-10-26**, three quest statuses
and an unknown YAML extension. Successful retry creates exactly the existing one profile file.
Standalone successful matrix cases produce only their expected UUID files; no emergency format,
separate Settings file, manufactured recovery/migration backup or schema bump is introduced.

## ModuleManager and complete-shutdown limitation

The real ModuleManager tests prove both predecessor rollback after failed Player startup and
continued reverse shutdown after a Player disable exception. The manager logs the failure, stops
the predecessor and clears its enabled list. A second `manager.disableAll()` does **not** retry
Player. After deliberate conflict reconciliation, only a direct `playerModule.disable()` commits
the retained unique state. A retained reference is not a scheduled retry.

Distinguish three outcomes:

- **Successful shutdown:** all loaded aggregates durably committed and cache released.
- **Incomplete cleanup while the same JVM/module remains alive:** getters closed, errors reported,
  pending ownership retained, explicit same-process retry supported.
- **Complete server/JVM shutdown with unresolved saves:** in-memory-only changes can still be lost.
  Forced termination, crash or power failure has the same residual limitation. This phase provides
  no crash-safe recovery guarantee, shutdown veto, emergency file, database or durable retry queue.

The global manager/root policy remains unchanged and is deferred. Consumers must be disabled in
dependency order and reacquire fresh services before restart; isolated Player replacement under
running Reward/Quest/Settings consumers is not supported.

## Tests, compiled negative controls and style

New canonical `PlayerModuleLifecycleHarness`: **204 checks**, 21 scenarios, actual production
module/services/listener/repository, real HandlerLists and disposable YAML. It covers L1–L25 via
normal/startup/storage/stage/duplicate/reentrant/refused-release/matrix/recovery/stale/join-quit/
public-save/diagnostic/manager/aggregate cases. Assertions observe state before fixture teardown;
no production cache is fabricated or repaired by reflection.

All 197 existing canonical entry points remain unchanged, including PlayerSchemaPersistence,
PlayerSettingsYamlCodec, SettingsPersistenceContract, PlayerVisibilityPersistence,
PlayerRewardPersistence, PlayerQuestPersistence, PlayerSettingsService, RewardModuleLifecycle,
RewardLifecycle, QuestLifecycle, OnlineRewardLifecycle and ModuleExtension harnesses.

`scripts/tests/player-lifecycle.Tests.ps1` compiles three temporary production source copies per
variant, prepends only the generated classes to the child classpath, runs the focused real harness
and requires the named AssertionError. All **10** variants compile (exit 0) and fail the intended
behavior (exit 1). Compilation failure is explicitly rejected. Original classes are not replaced.

| Control | Mutation / detected assertion |
|---|---|
| N1 | Omit unregister: actual listener rollback leak |
| N2 | Allow duplicate enable: active runtime replaced or duplicate handlers |
| N3 | Return after unregister failure: real profile commit skipped |
| N4 | Clear cache after partial failure: original unsaved state unavailable |
| N5 | Break after first failed player: later independently successful file remains stale |
| N6 | Hide strict incomplete result: shutdown falsely appears successful |
| N7 | Ignore pending cleanup guard: unsafe re-enable replaces pending state |
| N8 | Wrap original startup error: original exception object identity lost |
| N9 | Always-active listener gate: old generation acts as a second runtime |
| N10 | Retry using names read from stale disk: unique unsaved original value lost |

Positive controls run on the ordinary classpath both before and after: PlayerModuleLifecycle 204,
PlayerSchemaPersistence 124, PlayerSettingsYamlCodec 668, RewardModuleLifecycle 136 and
ModuleExtension 11: **1,143 checks per positive round**. Temporary overlay cleanup validates the
resolved absolute path against the temporary root and expected prefix. Receipts are copied into
stable evidence before canonical clean removes target output.

Spotless **2.43.0**, Google Java Format **1.22.0**, AOSP and the six-file pilot remain unchanged.
Only the new Java harness was formatted individually with pinned GJF, disabled long-string/Javadoc
reflow and the required JDK exports. Existing Java files received bounded functional edits only.
No POM, gitattributes, runner, existing test assertion or canonical discovery policy changed.

## Native acceptance, operator inventory and final verification

**Native PASS: 39 checks**, Paper **1.21.11-132-c5eb079**, LuckPerms **5.5.84**, final built
VapeeCore JAR and JDK 21. Fresh server root, worlds, configs and synthetic profiles; loopback
**127.0.0.1:25594**. The first native run passed, server process exit **0**, **0 ERROR/SEVERE**
in the clean console log. Both ordered activations contain all 28 exact production modules,
126 handlers and five production tasks without online clients/open views. Player owns exactly
one listener with two native join/quit handlers.

The probe invokes actual PlayerListener join/quit adapters on Paper's main thread with proxy
Player actors. It proves existing-profile load/name update, first-join profile creation, actual
Settings-service persistence, quit save-before-evict, unchanged Economy data, deferred reward
shutdown persistence (**250 → 267 Coins**), Player fallback for unsaved online progress, all
three stored quest statuses, daily cycle, Social and unknown YAML preservation. It observes zero
handlers/tasks and an empty loaded cache immediately after real ModuleManager reverse shutdown,
before Paper's plugin-wide safety teardown could conceal a module leak. Native logs show Quest
and Reward stopping before Player in both cycles.

After all consumers and owners stop, trusted `VapeeCore.onDisable()/onEnable()` callbacks rebuild
the complete composition root while the Paper classloader remains open. Fresh owner/service
identities, exact handler/task inventory, no indexing rewrite, reopened latest durable balance,
inert old-generation callbacks and another clean reverse shutdown are verified. This is **native
API/console/adapter acceptance, not a real Minecraft-client/network-login or visual test**. Direct
adapter calls are not claimed as global Bukkit event dispatch. No isolated Player hot swap under
active consumers, live plugin unload/reload, or reopening a platform-closed classloader is promised.
Intentionally failing persistence paths are confined to standalone fixtures, outside the native
clean log. The exact disposable server/world directory was validated and removed after confirmed
process exit; logs, source, receipt and synthetic profiles remain outside the checkout.

Natively tested JAR SHA-256:
`eb5a2b0c9700d88226349dc3f47e7f01c5fcc652e45f881bbb9965682cdc8857`.

| Lifecycle metric | Baseline observation | Corrected executable observation |
|---|---|---|
| Handler count after registration-after-effect failure | 2 leaked | 0 before fixture teardown |
| Listener inventory after retry | 4 handlers / two listeners | 2 handlers / one listener |
| Duplicate enable | Replaces runtime / leaves old registration | Rejected, original service/settings identities intact |
| Saves after refused unregister | A remains at original disk value | A's distinguishable unsaved value committed |
| Partial save shutdown | Returns success and cache size 0 | Throws exact UUID/cause aggregate; original failed profile retained |
| Explicit reconciled retry | No retained module-owned cache | Original unsaved value committed, then cache released |
| Old callback after disable | Can create or evict a profile | Inert during failed cleanup and after fresh activation |
| Normal production module/reload counts | 28 / 7 | 28 / 7, ordering unchanged |

Final canonical `pwsh -File ./scripts/verify.ps1`, exit **0**: Maven **clean package PASS**,
Spotless **PASS**, **198 discovered/executed/passed, 31,537 checks, Failed=0,
Missing/Invalid=0**. Baseline/final inventories retain **all 197** previous entry points and add
only PlayerModuleLifecycleHarness. The exact **+208** delta is: new lifecycle harness **+204**,
GuiAuthoringAudit **229 → 230** for its new test source (**+1**) and RepoLink **76 → 79** for the
three maintained-guide links (**+3**). Every other existing harness retains its check count.
The final canonical artifact's SHA-256 equals the natively tested JAR above.

Before any test, the complete currently installed `dev-server/` file set was captured by relative
path, length and SHA-256, including worlds, libraries, all plugins/data, configs, profiles and
existing migration/config/recovery backups. Actual protected inventory: **913 files**, including
**22 current VapeeCore-related files** (deployed JAR and its data tree). Final comparison uses
the same full path/hash contract: **913 before / 913 after, 0 added/removed/changed paths,
lengths or hashes**. No locations in this protected installation were unavailable. These are
newly measured current values, not assumed historical counts or inaccessible earlier hashes.

Neither deployed VapeeCore JAR, operator YAML, real profiles, world data nor existing backups
were modified. No build-and-deploy or operator-server start/stop script was invoked. Native
runtime inputs were copied read-only into a new disposable server and all mutable test state
belonged there. Overlay classes and generated build/native artifacts are outside the commit.

Final `git diff --check` and intended-path review pass. The requested commit contains exactly
the eight production/test/docs/report paths below; final post-commit status is reported separately.

## Exact change scope and deferred work

Production: `player/PlayerModule.java`, `player/PlayerService.java`, `player/PlayerListener.java`.
The listener edit is justified by the executable pre-fix abandoned-join and stale-quit reproductions.
No other production source changed.

Tests/tooling: new `src/test/java/dev/vapee/core/player/PlayerModuleLifecycleHarness.java` and
`scripts/tests/player-lifecycle.Tests.ps1`. Existing tests and fixtures are unmodified.

Docs: bounded additions in `docs/MODULE_DEVELOPMENT.md` and `docs/PERSISTENCE_DEVELOPMENT.md`;
this report is new. Earlier reports and README are unchanged; navigation is provided from those
two guides. Total intended scope: **8 files**.

Deferred: global incomplete-shutdown/retry policy and durable recovery after JVM exit; permanently
refused external listener removal; arbitrary external stale service references; the separate risks
from Phase 5B in other modules. No blanket hardening, module graph, gameplay or persistence rewrite.
Recommended follow-up is an explicit policy decision for incomplete player saves at full shutdown,
with durable requirements defined before any implementation. No next phase is started.

Deliver as exactly one local commit `Harden player module lifecycle and shutdown persistence`.
The full resulting SHA and clean post-commit status are returned separately; no self-referential
commit hash is embedded here. No push or merge.
