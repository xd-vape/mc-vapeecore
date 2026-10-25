# Phase 4C — Persistence & Repository Structure

Date: 2026-10-25. This is one bounded Settings/visibility mapping extraction. No gameplay, command, permission, persistent field, player schema or operator configuration changed.

## Baseline and scope

- Integrated, clean branch: `master`; local and `origin/master` at `3230550d9ad08e4b8ecaa4108a4bdb6f785789dc`.
- `git merge-base --is-ancestor 02798c7c4e83c84c6184c5350e5defb6db3ed211 HEAD` returned 0: Phase 4B is integrated.
- Working branch: `phase/player-repository-structure`.
- Unchanged canonical baseline: **193 discovered/executed/passed harnesses, 30,109 checks, Failed=0, Missing/Invalid=0**. Maven clean package and the six-file Spotless pilot passed.
- Runtime: JDK 21.0.12.1, PowerShell 7, offline Maven with `C:\Users\mehdi\.m2\repository`. Baseline receipts and original compiled repository classes were retained outside the checkout before edits/clean.

## Architecture findings and selected extraction

| Original responsibility | Resulting owner |
|---|---|
| Directory initialization, canonical UUID filenames and path resolution | `FilePlayerRepository`, unchanged |
| Startup identity validation and name index | Repository, unchanged; not a second loaded-profile cache |
| Existence/load/save entry points and source bytes | Repository, unchanged |
| YAML read orchestration, identity/timestamp validation | Repository; strict root/version parsing remains `PlayerFileSchema` |
| Settings/visibility deserialization, Boolean fallback, mode/UUID policy and warning diagnostics | Extracted together into package-local `PlayerSettingsYamlCodec` |
| Wallet/Economy, Social, online rewards, regular quests and daily cycle mapping | Repository, unchanged |
| Whole aggregate construction and canonical serialization | Repository; one Settings writer delegation |
| Schema/version evolution, owned paths, unknown siblings, aliases and closed structures | `PlayerFileSchema`, unchanged |
| Migration backup coordination, exact source-change guard and candidate validation callback | Repository, unchanged |
| Serialize-before-open, write/close, exact candidate text, atomic/fallback replacement, recovery and cleanup | `SafeFileWriter`, unchanged |
| Storage errors and post-commit index publication | Repository, unchanged |

Settings was the cohesive pilot: its mapping consisted of four related methods and twelve YAML writes across read/save regions. Moving that complete domain-specific surface creates one obvious mapping file without splitting its transaction. No other domain was extracted. The schema and writer already have meaningful independent responsibilities and were left intact.

`PlayerSettingsYamlCodec.read(UUID, Path, YamlConfiguration, Map<Object,Object>, Logger)` reconstructs only preferences; `write(PlayerSettings, YamlConfiguration)` sets canonical values in the caller's in-memory configuration. Private helpers retain the existing rules. It has no instance fields, service/cache access, file reads/writes, schema version, backup, repository implementation or independent commit. The `Path` supplies diagnostic context only.

## Measured structure

Counts use actual physical source lines, including blank lines. The approximate 753-line starting point in the brief is **752 lines** in this checkout.

| Measure | Before | After |
|---|---:|---:|
| `FilePlayerRepository` lines | 752 | 591 |
| Settings mapping lines inside repository | 160: 119 read/visibility + 26 Boolean/warning + 15 write | 0; two orchestration delegations |
| Settings-specific repository methods | 4 | 0 |
| Settings-specific repository YAML writes | 12 | 0 |
| Settings-specific imports in repository | 3 | 1 (`PlayerSettings`, aggregate construction) |
| Production files affected | — | 2: existing repository and new helper |
| New production helper classes | 0 | 1, 219 lines |
| Player persistence authority | 1 | 1 |
| Player schema owner | 1 | 1 |
| Durable player commit boundary | 1 aggregate | 1 aggregate |
| Settings read/write maintenance regions | 2 in repository | 2 together in codec |
| Persistence edit files for a new setting | Repository + schema | Codec + schema |

Domain/default, service mutation, codec read/write, schema ownership, appropriate GUI/localization and independent tests remain intentional edit responsibilities. A usual Boolean still involves the same eight production/resource files described in the Settings guide; the mapping file is replaced, not eliminated. Formatter expansion explains the helper's 219 lines versus the old 160-line mapping surface; line reduction alone is not the justification.

## Preserved mappings and behavior

All twelve existing paths remain: `settings.scoreboard`, `settings.sounds`, `settings.private-messages`, `settings.friend-requests`, `settings.friend-presence-notifications`, `settings.lobby-players-visible`, `settings.visibility.mode`, `settings.visibility.show-friends`, `settings.visibility.show-staff`, `settings.visibility.show-added-users`, `settings.visibility.show-game-participants`, `settings.visibility.added-players`.

Defaults remain scoreboard/sounds/private messages/friend requests true, presence false, visibility ALL, four filters false, added set empty. Ordinary fallback reads the current domain object; the existing visibility filter fallback remains false. No independently maintained new default table was introduced in production.

- Missing explicit mode uses the legacy Boolean: true → ALL, false → FILTERED. Missing/null visibility section retains that mode. Invalid section warns without discarding unrelated preferences.
- Explicit ALL/FILTERED/HIDDEN overrides the Boolean. Invalid mode or explicit null mode warns and uses HIDDEN. Raw map key presence remains distinct from Bukkit `contains`.
- Missing/null Booleans retain fallback; wrong optional types warn/fallback, not fatal login errors. Identity, Economy and Social remain strict.
- UUID input rejects nonstrings, malformed/shortened noncanonical values and self; canonical uppercase text remains accepted. Repeats deduplicate without extra warning. Save sorts canonical UUID text and writes empty lists explicitly.
- The existing diagnostic strings/order/frequency, owner UUID and source path are frozen by exact independent assertions. Reads do not normalize or write data.

Schema remains current **1**, legacy **0**. Unknown root/nested siblings remain governed by `PlayerFileSchema`; `quests.active` remains wholly owned. Existing alias-isolation, strict duplicate/root parsing, invalid metadata and future-version refusal are unchanged. Legacy loads/indexing do not rewrite or back up; the first legitimate save protects the exact original migration bytes. Future disk data rejects a stale loaded aggregate's mutation. Source-change detection and name index publication remain on their original side of the commit boundary.

## Independent compatibility evidence

New canonical `player/repository/PlayerSettingsYamlCodecHarness`: **668 passing checks**. It independently freezes all paths/values, defaults, every Boolean field across valid/null/wrong types, mode/legacy/null distinctions, malformed sections and UUID list shapes, canonical UUID ordering and exact contextual warnings. Codec read fixtures verify unchanged source bytes and no new files. No identity/security rule is inferred from display text.

The full-profile fixture loads identity/timestamps, Settings, visibility/UUIDs, wallet, ignored players, initialized online rewards, ACTIVE/REWARD_PENDING/COMPLETED quests and daily cycle. A real `PlayerSettingsService` mutation performs exactly one aggregate candidate/open/replacement; its observer sees the durable new value. Reload preserves all domains, sorted Social UUIDs and quest ordering. Closed assignment extras disappear by the existing schema rule. A later real reward-progress save preserves unknown Settings/root/other nested extension siblings. Only the existing aggregate file is created.

Test-only `settings.future-extension: {enabled: true, label: operator-value}` and unknown visibility/root fields are not production settings or schema-owned keys. They survive Settings and unrelated-domain saves. No synthetic production setting, schema bump or feature file was added.

The same **668-check** contract passed with the saved **original repository classes** first on the classpath. Direct codec checks still exercise the new helper; aggregate/service/fault/migration sections use the original repository in that run. An additional isolated `FixedAggregateProbe` saves identical complete fixed-time aggregates using original and refactored repositories for ALL/FILTERED/HIDDEN. All three outputs are byte-identical (stronger than required semantic YAML equality):

| Mode | Matching SHA256 |
|---|---|
| ALL | `87e8f261a2c9dc9a63ffa858280f02ced6ddd0a580a11bccf956dad9d0272418` |
| FILTERED | `9202767bc4d39d53c53037e796616357a000db82e2b5c7e1f8fbb7b1b2b25f99` |
| HIDDEN | `a2d35de636ac2154b293e75d6b3da8d8cbc542be9c830e2cc8ad80ea52f23066` |

## Existing tests and fault safety

Thirteen focused existing entry points passed **709 checks**: PlayerSchemaPersistence 124, SettingsPersistenceContract 72, PlayerVisibilityPersistence 17, VisibilityMode 57, PlayerRewardPersistence 11, PlayerQuestPersistence 21, PlayerSettingsService 28, LobbyPersistence 112, EconomyIntegration 27, FriendIntegration 28, QuestService 44, OnlineRewardService 42, RewardService 126. No existing test source or entry point was removed/replaced.

The existing `PersistenceFaults` infrastructure is reused, not duplicated. New real-service cases cover WRITE, CLOSE, INVALID_YAML, ATOMIC_IO, UNSUPPORTED, REPLACE, RESTORE, BACKUP, MIGRATION_BACKUP and TEMP_CLEANUP. Failure propagates, restores actual Settings, emits no commit notification, retains exact source where recoverable and does not publish the attempted rename. Failed unload retains the cache. Failed restoration retains the exact recovery copy and blocks further reads/unload. Unsupported atomic moves and post-commit cleanup warnings do not cause false rollback. Existing schema/lobby fault tests retain source-change, damaged-candidate, backup-conflict and other cleanup contracts.

## Compiled negative controls

`scripts/tests/player-settings-codec.Tests.ps1` uses disposable classpath overlays, existing canonical compilation/classpath preparation and real temporary profiles. It does not introduce a harness discovery system. **All ten variants compiled (exit 0) and failed their intended behavioral assertion (exit 1)**; ordinary-classpath positives passed 668 checks both before and after. The source checkout and original compiled classes are not mutated.

| Control | Defect | Detected behavior |
|---|---|---|
| N1 | Wrong sounds fallback | Independent true-default assertion |
| N2 | Write sounds under wrong key | Frozen canonical path/value assertion |
| N3 | Ignore legacy Boolean | False must remain FILTERED |
| N4 | Invalid mode falls back to ALL | Invalid explicit mode must be HIDDEN |
| N5 | Allow self UUID through reader and domain add call | Exact allowed-target set |
| N6 | Delete unknown Settings extension before overlay | Extension preservation assertion |
| N7 | Omit canonical candidate validation | Ambiguous dotted owned value cannot claim a successful aggregate commit |
| N8 | Omit service rollback | Actual loaded sounds value restored after WRITE failure |
| N9 | Omit migration protection | Exact original backup must exist |
| N10 | Weaken future schema guard | Future source must refuse normal load |

N5 deliberately changes both the parser's self check and its owner passed to the domain: removing only the parser check would still be safely rejected by the existing domain guard. N7 uses a valid serialized document with an ambiguous literal dotted key; simply corrupting candidate bytes would still be rejected by the writer's independent byte guard. These controls reach the intended defenses rather than counting compilation errors or redundant guards as detection.

## Native Paper acceptance and operator isolation

Paper **1.21.11-132**, LuckPerms **5.5.84**, final built VapeeCore JAR; a separate copied server under the system temporary directory, disposable worlds/configs and three synthetic profile IDs. **44 native API/file checks passed**, all **28 modules** enabled and disabled, followed by normal server shutdown; **0 ERROR/SEVERE** in stdout/stderr. The server process exited.

Native proof includes current and untouched legacy startup bytes, empty loaded cache after indexing, new schema-1 defaults, real Settings/visibility/opt-in mutations, unload/reload persistence, actual FriendService privacy denial then allow after enabling the existing preference, economy/Social/reward/quest/daily retention, unknown extensions, shutdown persistence of unsaved reward state and reopening saved profiles. An untouched never-loaded legacy fixture remains byte-exact through shutdown. Shutdown is driven through the real module manager before rereading saved files, followed by actual Paper shutdown.

These are trusted owning-service API calls on Paper's main thread, **not a native GUI/permission interaction or real Minecraft-client acceptance**. Existing canonical GUI/command/security harnesses retain those adapter boundaries. The native probe initially failed compilation because backslashes in the Java argument file were interpreted as escapes; normalizing argument-file paths fixed setup before the single clean server run. No failed setup was counted as acceptance.

Tested JAR SHA256: `04a6bf917da035f0d1813069a72b9fb47bbfaca78b79d116493459204cb378a6`.

Operator protection: the before/after **complete path sets and SHA256 values match for all 22 actual installed files**, including the JAR, configs, language overrides, player and feature data; 0 added/removed/changed protected paths. No live deployment occurred. The disposable Paper process exited, evidence was copied outside the checkout, and its exact resolved temporary server directory/worlds were removed. No live profile or existing migration/recovery backup was touched.

## Canonical verification

Final `scripts/verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'`: **PASS**. Maven clean package, Spotless and all **194 discovered/executed/passed harnesses, 30,786 checks, Failed=0, Missing/Invalid=0**. Comparing actual class inventories shows **0 removed baseline entry points and exactly 1 added**, the 668-check codec harness.

The **+677** check delta is fully accounted for: +668 new behavioral checks, `PermissionDescriptorHarness` 884 → 885 (+1 scan of the new production Java source), `GuiAuthoringAuditHarness` 225 → 226 (+1 scan of the new test Java source), and `RepoLinkHarness` 67 → 74 (+7 maintained Markdown links). Every other existing harness keeps the same check count. The six-file formatter allowlist, `pom.xml` and `.gitattributes` remain unchanged; only the two new Java files were individually formatted with pinned Google Java Format 1.22.0/AOSP. Final built JAR hash equals the JAR used in native acceptance.

Evidence logs/receipts, fixed-profile comparisons and native probe source/logs are retained outside the checkout in `C:\Users\mehdi\AppData\Local\Temp\vapeecore-phase04c-evidence`; normal verifier receipts remain under `target/verification/harnesses/`.

## Exact changed files

Production (2):

- `src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java`
- `src/main/java/dev/vapee/core/player/repository/PlayerSettingsYamlCodec.java` (new)

Tests/tooling (2):

- `src/test/java/dev/vapee/core/player/repository/PlayerSettingsYamlCodecHarness.java` (new)
- `scripts/tests/player-settings-codec.Tests.ps1` (new)

Documentation/report (6):

- `docs/PERSISTENCE_DEVELOPMENT.md` (new practical guide)
- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/SETTINGS_DEVELOPMENT.md`
- `reports/architecture-modernization/PHASE_04C_PERSISTENCE_STRUCTURE.md` (this new report)

Existing historical reports were not rewritten. No operator files, deployed JARs, builds, worlds, fault overlays or backup artifacts are part of the commit.

## Remaining complexity and next recommendation

The repository still coordinates paths/indexing/reads, maps identity, Wallet, Social, rewards and both quest states, builds the full aggregate and coordinates schema/backup/commit errors. This phase has **not** decomposed all player persistence. Schema ownership and safe-write behavior remain separate explicit contracts. Synchronous aggregate writes and the existing quest/reward batching model remain intentional.

Prioritize a bounded review of remaining player mapping discoverability and optional-state diagnostics against real maintenance friction before selecting another pilot. A future cohesive reward/quest mapping extraction may be evaluated independently; do not turn this into a generic codec/repository framework or async migration. No next phase was started.

Git review: `git diff --check` passed. Scope is exactly the ten files above (five modified and five new before staging), with no unrelated formatting or generated/operator artifacts. Deliver as the single local commit `Extract player settings YAML mapping from repository`; its full SHA and post-commit working-tree status are returned separately to avoid a self-referential report hash. No push, merge, rebase or automatic next phase.
