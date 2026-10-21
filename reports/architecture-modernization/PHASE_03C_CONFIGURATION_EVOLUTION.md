# Phase 3C — Configuration Architecture & Evolution

## Baseline and scope

- Clean integrated `master`: **`3dd99fb1ada173d79b89d458d82ac870598f4435`**, also the observed local `origin/master`.
- Actual `merge-base --is-ancestor 56cbf736048db7f369cd6ba3f308888153685b00 HEAD` exited **0**. No automatic integration or discarded changes.
- Created **`phase/configuration-extension-patterns`**.
- Official baseline: **186 discovered/executed/passed harnesses, 28,880 checks, failures 0, missing/invalid 0; exit 0**. Maven clean package and the unchanged six-file Spotless pilot PASS.
- Captured SHA-256 inventory of **22 installed Dev-Server plugin files**, including installed VapeeCore data/config/lang files and deployed JAR, before verification. Final comparison confirms **22/22 identical paths and byte hashes, zero changes**; these paths never served as fixtures.
- No production key, default, resource version, gameplay feature, public command/permission, reload membership/order, player schema or language file changed.

## Configuration architecture findings

Operator config is owned by each parser/module, not a global mutable registry. Core's `CoreConfigState` is a **private nested record inside ConfigService**, not a separate source file. Core, Chat, PM, Lobby and Presentation use complete typed snapshot publication; DailyQuest's immutable state/registry remains main-thread-owned. Collection snapshots retain their existing defensive-copy rules.

| Category / current files | Default/load owner | Reload | Evolution / write / backup |
|---|---|---|---|
| `config.yml` **1** | Bundled resource, Java fallbacks, ConfigService/readState | Core complete snapshot | Startup six-file ConfigEvolution; operator-owned file; exact unique evolution backup |
| `lobby.yml` **2** | LobbyConfig / ConfigFiles / parser | LobbyModule also reapplies spawn, NORMAL gamemode/items | Evolution plus a distinct explicit spawn-save SafeFileWriter contract |
| `chat.yml` **1**, `private-messages.yml` **1** | Owning Config + service template preparation | Owning module config/runtime pair | Evolution; ordinary reload performs no save |
| `presentation.yml` **1** | PresentationConfig, renderer and service | Snapshot, renderer, tasks and owned UI effects | Evolution; ordinary reload performs no save |
| `daily-quests.yml` **1** | DailyQuestConfig, domain catalog validation | State/definition registry; closes quest views | Evolution; daily assignments/progress belong to separate profile persistence |
| Player/feature files | Player, Friend, Clan, Warp, Moderation and Blackjack owners | Feature-specific mutations/lifecycle, not arbitrary operator reload | Excluded from ConfigEvolution; existing repository-specific schemas/backups/writers |
| Installed `lang/en.yml`, `lang/de.yml` | Bundled catalogs plus operator overrides / LocalizationService | Independent LocalizationModule participant | Excluded from ConfigEvolution; copy missing only; explicit adoption has separate backup contract |

The production allowlist stays explicit and immutable: Core 1, Lobby 2 and the other four files 1. `ConfigResourceContractHarness` independently freezes both names and versions, loads each actual bundled resource with a strict safe reader, rejects malformed roots/duplicates and compares resource/registry/frozen version agreement. It also freezes the actual bundled `ranks.track: ranks` default. Persistent and localization paths are explicitly excluded.

## Real config value traced

**`ranks.track`** is suitable because the existing real consumer reads it dynamically. Bundled/default value is **`ranks`**. `ConfigService.readNonBlankString` trims valid values, silently uses the fallback for missing keys and warns/falls back for blank/non-string values. Existing Bukkit null/contains semantics remain unchanged. The nested immutable CoreConfigState stores it; `getRankTrack` exposes the active snapshot. RankService retains `configService::getRankTrack`; its real LuckPermsService lookup consumes the active track during `getPublicRanks`. Missing tracks retain the existing controlled result.

The real integration fixture constructs ConfigService, RankService/LuckPermsService and **real ChatModule.enable/prepareReload**, ChatConfig and ChatService. Existing ModuleRuntimeFixture supplies Paper registration and dependency-module boundaries; LuckPerms track I/O returns a controlled missing-track result while recording the actual requested name. No reload participant implementation is replaced with a fake.

## Implemented bounded improvements

Only production **ConfigEvolution.java** changed:

1. Package-local supplied-version constructor makes a genuine future additive revision expressible in tests. It defensively copies the supplied map. Every public startup constructor still delegates to the exact unchanged MANAGED_CONFIGS allowlist; no scanning/global registry/public configuration API was added.
2. Existing FileAccess now exposes the existing restore-copy operation, permitting deterministic restore failure testing while using the same production copy/recovery logic.
3. A failed non-atomic recovery now logs **the exact retained original backup path and restoration exception**, explicitly requesting manual reconciliation. Previously only the general startup exception and suppressed I/O cause surfaced that failure. Original replace failure remains primary; restoration failure remains suppressed; no false success.
4. Independent executable version/default, additive evolution and real reload contracts prevent incomplete configuration additions without consolidating typed defaults into a map/framework.

All normal parser/default/reload behavior remains unchanged. ConfigEvolution and runtime reload remain separate. No production ConfigService/ReloadService/ChatModule changes were needed.

## Synthetic additive extension and actual transitions

Fixture **TEST_EXTENSION_OPERATOR_FLAG** is `example.additional-flag`, default **false**, in test-only version 2 resource content. Installed fixture starts with an actual **config-version 1**; the actual production node merger evolves **1 → 2** using the package-local manifest. This is not merely an unversioned → 1 test. A separate fixture proves **unversioned 0 → 2**. Existing ConfigEvolution/Lobby upgrade harnesses retain real production version coverage.

Independent checks preserve custom false, zero, empty string/list, existing list, quoted numeric-looking string, nested map/unknown subtree, explicit null and a scalar-versus-map conflict. Supported header/inline/version comments survive. Candidate version/default and backup filename transition are checked. CRLF plus non-ASCII UTF-8 original bytes match the stored backup exactly. A repeated current-version evolution performs no candidate write, resource read or new backup. Reinstalled old input gets another unique backup while the first remains untouched.

Current version with a missing new key remains untouched; future 3/99 and invalid negative/string/Boolean/null/list/oversized metadata are refused without candidate/default read, backup or byte changes. Missing file remains the owning default loader's responsibility. Invalid YAML, duplicates and recursive legacy aliases remain covered by the existing harness, without lossy rewrite.

### Failure, concurrency and recovery evidence

The extension harness checks the exact original backup **before candidate write**, then injects candidate write failure/corruption, ordinary atomic I/O error, unsupported atomic success, failed non-atomic replacement after destination damage, restore failure and a concurrent operator edit. Recoverable cases retain/restore exact source bytes and permanent backup; temporary candidates are cleaned. Concurrent edits survive and prevent replacement. Restore failure leaves visible damaged destination plus retained original backup, preserves primary/suppressed exceptions and emits the new path-specific SEVERE diagnostic. It does not pretend restoration succeeded or introduce a different recovery-marker engine.

No backup pruning is added. Runtime reload does not run evolution or write backups. Failed restore diagnostics are exercised only in isolated tests.

## Reload participants, snapshots and failure matrix

Actual explicit order: **ConfigService → LocalizationModule → LobbyModule → ChatModule → PrivateMessageModule → PresentationModule → DailyQuestModule**. Membership/order are unchanged.

All prepare before apply. Apply remains sequential rather than a global transaction across arbitrary consumers. The failed plan rolls back first, followed by already applied plans in reverse order. ROLLBACK_INCOMPLETE and ALREADY_RUNNING retain their original distinct meaning. Snapshot assignment does not itself replace feature-owned effects: Lobby reapplies state/items/gamemode; Presentation manages renderer/task/UI restoration; Localization refreshes recipient views; DailyQuest restores state/definition registry and deliberately closes open menus on apply. No promise to reopen closed views or recreate today's assignments on a catalog reload is introduced.

| Contract | Execution evidence |
|---|---|
| R1 valid prepare | Real Core/Chat plans leave active getters, rank lookup and Chat config/runtime unchanged |
| R2 invalid prepare | Malformed real Chat YAML refuses after Core prepared; old runtime remains usable |
| R3 apply | Successful two-real-participant reload publishes complete Core fields and Chat config/runtime |
| R4 later apply failure | Inject after real second apply and after a third control apply; actual Chat and Core plans restore old state in exact reverse order |
| R5 rollback error | A controlled error after real Chat rollback returns ROLLBACK_INCOMPLETE; independent Core rollback still runs |
| R6 retry | Corrected parse, apply and rollback faults can reload successfully |
| R7 nested reload | Retained ReloadServiceHarness exercises two nested attempts during prepare/apply/rollback; ALREADY_RUNNING |
| R8 operator bytes | Before/after file set and exact bytes across real prepare/apply/rollback/failure; N8 detects a forbidden prepare write |
| R9 persistence | Temporary profile/friend/clan/warp/moderation/table/backup sentinels stay byte-identical; installed files are separately hash-protected |
| R10 localization | Retained actual LocalizationReloadHarness covers override/reload/rollback semantics; language sentinels are outside evolution and remain untouched |

Prepared plans remain independent of subsequent external file edits. Real Chat rollback restores the exact previous config/runtime objects. Actual RankService follows the restored Core value. Bad MiniMessage keeps the existing Chat warning/runtime fallback while raw accepted config remains unchanged; it is not converted into a hard structural failure.

## Extension complexity before / after

For a representative Core Boolean: resource; ConfigService parser + nested snapshot/default + getter; actual consumer = **3 production/resource files** before and after. A deliberately evolved revision also edits ConfigEvolution = **4**. Existing participant registration needs no extra edit for a field in its snapshot.

Representative two-file feature test plan plus one documentation file: **6 total without evolution before/after**. With evolution: **7 before / 8 after**, because updating the new independently frozen version oracle is a deliberate additional test-file edit. Actual feature test placement varies. Necessary parser/domain/consumer boundaries are not defects. The improvement is stronger agreement/isolation contracts and a predictable guide, not an artificial lower edit count or untyped registry.

## Focused tests and negative controls

New canonical entry points: ConfigResourceContractHarness **47**, ConfigEvolutionExtensionHarness **83**, ConfigReloadIntegrationHarness **428** checks.

Retained focused passes: ConfigService **93**, ConfigEvolution **81**, ConfigHelpers **33**, DefaultConsistency **12**, LobbyConfigUpgrade **13**, ReloadService **213**, ChatConfig **30**, PrivateMessageConfig **26**, PresentationConfig **37**, LobbyConfig **45**, DailyQuestConfig **35**, DailyQuestLifecycle **16**, LocalizationReload **11**. All exited 0. No old entry point/check was replaced.

Repeatable supplemental command: `scripts/tests/configuration-contracts.Tests.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'`. It uses existing canonical harness classes, temporary classpath overlays and real source/resource variants; it is not another discovery runner.

| Control | Variant | Intended observed failure |
|---|---|---|
| N1 schema disagreement | Temporary config.yml resource version 2 versus supported 1 | Resource/registry version mismatch |
| N2 overwrite | Compiled merger overwrites existing Boolean and bypasses its preservation guard | Independent false-value preservation assertion |
| N3 missing exact backup | Compiled writer leaves an empty backup | Exact original backup missing before candidate write |
| N4 future rewrite | Compiled version guard permits downgrade | Future version 3 no longer refused |
| N5 premature publish | Compiled Core prepare publishes candidate | Active core track changes during prepare |
| N6 broken rollback | Compiled Core rollback is omitted | Old core track not restored after real later apply failure |
| N7 persistence eligible | Compiled allowlist adds warps.yml | Independent exact allowlist contract |
| N8 reload save | Compiled Core prepare appends operator YAML | Exact file byte isolation assertion |

**8/8 intended failures detected: seven Java variants compile successfully and fail behavior; one isolated resource override needs no Java compilation. Ordinary-classpath positives pass before and after.** An uncompilable variant is a script failure, never accepted as negative evidence. The final script rerun after full clean verification also passed, with positive counts **47/83/428** before and after.

## Official verification, native Paper and file isolation

Official final `scripts/verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'` exited **0**: **Maven clean package PASS; Spotless PASS; 189 discovered/executed/passed harnesses, 29,447 checks; failures 0; missing/invalid 0**. All **186 baseline entry points** are retained; the three new entries are the focused contracts above.

| Actual check delta | Baseline | Final |
|---|---:|---:|
| ConfigResourceContractHarness | new | 47 |
| ConfigEvolutionExtensionHarness | new | 83 |
| ConfigReloadIntegrationHarness | new | 428 |
| RepoLinkHarness (new maintained-guide links) | 45 | 51 |
| GuiAuthoringAuditHarness (three new Java files) | 218 | 221 |

Every other harness count is unchanged: **28,880 → 29,447 (+567)**. No old entry or check was removed. The supplemental controls passed again after full verification. Installed operator YAML, language overrides, feature/player data and deployed JAR retain their original 22-file SHA-256 inventory.

Native smoke used isolated **Paper 1.21.11-132-c5eb079**, **LuckPerms 5.5.84**, **JDK 21.0.12.1**, loopback **25588**, disposable normal world and separate plugin data. Actual tested JAR SHA-256: **`31a41fe5eac5836e8dae77aee3889c09affce16a2c760952523e8741b9cd4a92`**; it matches the final built artifact.

- The first temporary probe aborted after three startup assertions because it treated the plugin's relative data folder as an absolute parent path. Only the disposable probe path was corrected; this attempt is not acceptance evidence and its log was retained separately.
- Repeated fault run at **01:24:16 Europe/Berlin, 2026-10-22**: **`PHASE03C_NATIVE_FAULT_PASS checks=95`**. Startup preserved invalid Core version metadata and future Chat version 99 exactly, without migration/backup. Actual native command reload, malformed real Chat prepare failure, injected later apply failure after all seven real participants, real Core/Chat/Presentation snapshot/task restoration, retry, actual LuckPerms rank consumer and byte isolation passed. Exactly **two ERROR entries** are expected: rejected Chat prepare and the controlled tail apply failure; no other ERROR/SEVERE.
- Separate clean acceptance at **01:25:31 Europe/Berlin, 2026-10-22**: **`PHASE03C_NATIVE_CLEAN_PASS checks=50`**, **0 ERROR/SEVERE**. All six current versions and seven actual participant order, normal startup, authorized `/core reload`, changed rank/chat/presentation values, active owned presentation task, actual RankService/LuckPerms lookup, retained installed localization override and exact file bytes passed.

Both completed runs activated and normally disabled all **28 modules**, followed by clean server shutdown and exited processes. These were automated console/API probes with no connected Minecraft client; no visual/client acceptance is claimed. Restore-failure and rollback-incomplete injection remain covered by standalone contracts; native controlled apply failure used the actual production participant plans plus one temporary failing tail, then restored the original participant list. No production command/participant implementation was replaced. Logs/receipts are retained locally outside the repository; temporary server/worlds/probe/classes and generated migration fixtures were removed. Existing operator backups were never deleted.

## Exact changed files

Production (1): `src/main/java/dev/vapee/core/config/ConfigEvolution.java`.

New Java tests (3):

- `src/test/java/dev/vapee/core/config/ConfigResourceContractHarness.java`
- `src/test/java/dev/vapee/core/config/ConfigEvolutionExtensionHarness.java`
- `src/test/java/dev/vapee/core/config/ConfigReloadIntegrationHarness.java`

Supplemental validation: `scripts/tests/configuration-contracts.Tests.ps1` (new).

Documentation/report (5):

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/CONFIG_DEVELOPMENT.md` (new)
- `reports/architecture-modernization/PHASE_03C_CONFIGURATION_EVOLUTION.md` (new)

Pinned Google Java Format 1.22.0 AOSP applied individually to new Java files. Spotless 2.43.0 six-file allowlist unchanged. Existing production file receives bounded functional changes, no broad whitespace/package/schema/resource rewrite. No generated class/JAR, temporary world, operator file or mutation variant belongs in the commit.

## Deferred / next modernization recommendation

1. Continue with bounded feature-module dependency/lifecycle extension patterns: identify one owning module, its resources and external boundaries, then add independent start/failure/disable/retry tests. Preserve the proven state/service/persistence and configuration boundaries.
2. Before extending another participant, test its actual owned runtime effects (tasks, visibility, views, item ownership) through existing narrow seams. Core/Chat proof is not a claim that arbitrary feature effects form a globally atomic transaction.
3. Keep future repository/lifecycle cleanup bounded by one owner and independent behavior tests; no FilePlayerRepository/BlackjackCommand rewrite, file watching, backup pruning or automatic next stage was started here.

Completion uses one local **Standardize configuration evolution and extension patterns** commit on `phase/configuration-extension-patterns`. `git diff --check` passes; only the ten files above belong to the change. The actual full commit SHA and post-commit working-tree check are returned with the completion response. No push, merge, rebase, reset, cherry-pick or automatic next modernization stage.
