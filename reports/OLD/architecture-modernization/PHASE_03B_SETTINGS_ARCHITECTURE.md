# Phase 3B — Settings Architecture & Extension Patterns

## Baseline and scope

- Started on clean integrated `master`, **`d1bdf22b97f63bf196c1bb667c6233de20b838e1`**, also the observed local `origin/master`. `merge-base --is-ancestor 54b3e8b41fa87b00ea4c32b4db8a244af22ce85e HEAD` exited **0**. No integration/discard action was needed.
- Created **`phase/settings-extension-patterns`**.
- Official baseline: **184 discovered/executed/passed harnesses, 28,714 checks; 0 failures; 0 missing/invalid; exit 0**. Maven clean package and six-file Spotless pilot PASS.
- No changes to domain defaults, fields, player keys/version, visibility policy, production layouts, public commands/permissions, language resources/overrides, operator files or deployed dev-server JAR.

## Existing architecture and ownership

| Responsibility | Actual owner / contract |
|---|---|
| Boolean defaults/state | `PlayerSettings` inside the loaded `CorePlayer` |
| Visibility state | `PlayerVisibilitySettings`: `VisibilityMode`, four filter Booleans, immutable UUID selection snapshots; specialized results/rollback |
| Loaded/known-offline profiles | `PlayerService`; ordinary Settings reads/mutations require loaded state; explicit known Friend Requests read may reconstruct offline data without loading it |
| Preference mutation | `PlayerSettingsService`; no-op true without write/event; apply → save → rollback on failure or notify after success |
| Durable profile boundary | `PlayerService.savePlayer` → `FilePlayerRepository`; unload saves before removing loaded state |
| YAML/schema | Explicit read/write mappings in repository; owned paths/version/preservation/candidate comparison in `PlayerFileSchema`; technical replace/recovery in `SafeFileWriter` |
| Definition/composition | `SettingsMenuEntries` supplies current-state icon/status/functions and post-save effect; `SettingsMenu.Action` / `MenuLayout` supplies semantic identity and local placements |
| Interaction | `SettingsListener`, with active exact owner/holder/inventory/top, permission, click-type and loaded-profile guards before entry execution |
| Active rendering | `SettingsMenu` binds views and observes commits/locale; no preference cache or domain truth |
| Integration | `SettingsModule`, with existing Phase 2B cleanup/rollback/retry; LobbyExperience owns parent Back callbacks |
| Translations/effects | Existing EN/DE catalogs/MessageService; effects remain with their owning feature |

General Boolean entries use the existing toggle helper. Visibility navigation is a dedicated entry and mode/filter/UUID operations remain specialized. Back/Close/Refresh are non-persisted controls. Supported-but-unplaced Visibility stays hidden in the root default. Optional status panes remain optional.

### Identified drift and bounded improvements

1. `Map.ofEntries().entrySet()` previously determined definition construction order. Fixed slot output was correct, but renderer execution/externally inspected list order was unspecified. Construction now follows enum declaration order and returns an immutable list.
2. Constructor validation previously covered bounds/duplicate/reserved slots but allowed a placed feature to have no supplied entry, an omitted placed status, reversed icon/status or a different status pair. It now snapshots entries first and verifies every placed feature has the exact declared icon/status pair. Test-only arbitrary entries remain supported through the existing injected list without production enum/switch changes.
3. A repeated six-feature action list in constructor validation had to be extended independently of the enum, definition and layout. Features now derive from all local actions except Back/Close/Refresh, and the same immutable feature set drives construction/validation. A newly placed action without a definition fails explicitly.
4. Renderer context restoration, service rollback/observer fanout, known-offline reads and module cleanup already satisfy the inspected contracts. They were tested rather than rewritten.

Only **SettingsMenu.java** and **SettingsMenuEntries.java** changed in production. No generic registry/framework, schema/persistence refactor or new UI behavior was introduced. Valid layouts retain the same appearance and actions; invalid/incomplete injected compositions now fail during construction.

## Extension workflow before / after

| Change area for a hypothetical Boolean | Before | After |
|---|---|---|
| Domain/default plumbing and accessors | `PlayerSettings` | Same |
| Service accessor and mutation | Two methods, shared transaction | Same |
| Persistence | Repository write + read + schema-owned path | Same three explicit definitions |
| GUI | Action enum + repeated feature list + definition + optional layout | Action enum + one definition + optional layout |
| Language | EN/DE names/hints | Same two resource files |
| Tests | Service/persistence/UI/locale + lifecycle reuse | Same categories, independent contract fixtures now available |

**8 production/resource files before and after**: domain, service, repository, schema, menu, entries and two catalogs. A representative four-file feature test plan makes **12 total files** in either version. UI edit locations decrease **4 → 3**; one independent feature behavior definition remains; duplicated setting-specific listener rendering/dispatch remains **zero**; persistence definitions remain **three** intentionally explicit write/read/ownership boundaries. Real domain/persistence edits are necessary, not defects. Constructor checks now detect missing/mismatched/colliding pairs, and executable contracts detect wrong callbacks/defaults/keys/commit behavior.

Before: reconstruct these responsibilities and remember the extra allowlist manually. After: follow `docs/SETTINGS_DEVELOPMENT.md`, add the domain/service/storage definition, declare one GUI behavior, optionally place it, translate and extend independent tests; constructor validation catches incomplete placement. Multi-state values and navigation have separate documented paths.

## Real persisted setting proof

Representative: **Friend Presence Notifications**, default **false**, exact path **`settings.friend-presence-notifications`**, schema version **1**.

`SettingsPersistenceContractHarness` uses real `PlayerSettingsService` → `PlayerService` → `FilePlayerRepository` on temporary profiles. It independently freezes all current Boolean defaults, visibility ALL/false filters/empty selection and the twelve existing leaf paths under settings. It covers missing section/key, invalid section and invalid Boolean forms, strict true read, no eager rewrite and path-specific warning. Candidate comparison rejects valid YAML containing the wrong owned value before replacement.

True survives an independent repository reconstruction; false survives save/unload/load. A no-op produces no I/O/event. Unloaded mutations create no file/cache; explicit known-offline Friend Requests reads leave the profile unloaded. Captured actual file-operation order and an observer reading committed YAML prove notification follows replacement.

Real partial-write, close, invalid YAML, ordinary atomic-move failure and non-atomic replacement-after-damage injections propagate, roll back the in-memory presence value, preserve exact previous durable bytes and emit no observer event; candidate/recovery artifacts are cleaned or restored for these recoverable cases. Existing `PlayerSchemaPersistenceHarness` retains unsupported-atomic success, failed restore/recovery marker and future schema coverage. No claim is made that unrecoverable filesystem damage magically restores the destination; existing recovery protection remains authoritative.

Unrelated other Booleans, visibility mode/filter/selected UUID, wallet, ignored UUID, online reward, active quest/daily cycle and unknown root/nested extensions survive repeated saves/reloads. A throwing first observer cannot invalidate the successful setter result or suppress a subsequent independent observer. Duplicate registration does not duplicate delivery.

## Independent synthetic GUI proof

`SettingsEntryExtensionHarness` now defines **TEST_EXTENSION_TOGGLE** independently, with fixture-owned Boolean/decoy, text, diamond icon, optional status and controlled mutation. It does not call the built-in toggle helper or add a field/key/enum/catalog entry. The existing list-injection seam accepts it on an isolated layout and alongside all six supported built-ins.

Real listener clicks on main/status execute its exact callback, update only intended fixture state and rerender the same inventory. A refused fixture mutation keeps prior state with no success feedback. A real service commit refreshes the independently committed fixture state through the actual menu observer without executing the synthetic callback; a failed real save sends no optimistic refresh. This is **GUI extension**, not a claim of synthetic persistence. The real setting proof above covers storage separately.

The same callback is blocked for foreign UUIDs, opened clones, holder/inventory mismatch, disallowed clicks, bottom inventory, missing profile, permission revocation, replaced and shutdown inventories. Drags cancel. Optional status omission is inert. Existing feature composition and duplicate rejection remain tested.

## Security, render scope, navigation and lifecycle

Owner UUID, exact InventoryHolder/Inventory, active registry, current top, current permission/profile and top-click/type guards are unchanged. Renderer/dispatch share the actual entry. No material/name/lore/title authorization or second state cache was added.

The new definition harness independently checks expected main materials and deterministic order, mapping coverage, intentionally unplaced features, icon-only status, immutable lists, missing/reversed/mismatched pairs, bounds/control collision and defensive list copying. An injected renderer failure proves `renderingFor` is restored, no failed initial view is published, the next language remains independent, locale refresh saves nothing, and a failed active render closes/detaches its view without retaining context.

Existing root/visibility/VisiblePlayers security, EN/DE, Back/Close/optional Refresh, save-failure feedback, active-view and Phase 2B module failure/disable/re-enable coverage is retained. The maintained Developer Guide incorrectly still said Settings had no Back; corrected to the already-existing parent Back behavior. No layout/navigation code was changed.

### Side-effect audit

- Scoreboard: successful root GUI save calls `PresentationService.updatePlayer`; direct domain callers are observed by existing presentation update/lifecycle paths, not a new immediate settings observer.
- Sounds: feedback reads saved value; turning off is silent.
- PM: recipient preference read at send time.
- Friend Requests: existing request policy reads known loaded/offline recipient state at request time.
- Presence: existing notifier reads recipient opt-in during actual friend presence notification; no fabricated event on toggle.
- Visibility: GUI/command/hotbar/selection callers apply the owning visibility service after save; master changes refresh lobby item. Relationship seams remain Friend/Ignore listeners.
- Active settings views: existing post-commit/locale subscriptions rerender owned current inventories, without polling or duplicated domain truth.

These distinct timings are intentional existing boundaries; no global bus or new scheduler was introduced.

## Focused tests and negative controls

New: `SettingsDefinitionContractHarness` **39** checks; `SettingsPersistenceContractHarness` **72**. Improved retained `SettingsEntryExtensionHarness` **10 → 58** checks.

Existing focused passes: SettingsMenu **66**, Composition **191**, Localization **8**, AutomaticUpdate **8**, LayoutAlignment **6**, Layout **23**, SettingsCommand **36**, SettingsNavigation **19**, VisibilityMenuSecurity **327**, SettingsModuleLifecycle **195**, PlayerSettingsService **28**, PlayerSchemaPersistence **124**, PlayerVisibilityPersistence **17**, SettingsMenuSecurity **205**. All exited **0**.

Repeatable supplemental command: `scripts/tests/settings-contracts.Tests.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'`. It compiles separate temporary source variants, prepends each output directory, requires the exact intended behavioral failure and reruns ordinary-classpath positives. Working sources and real profiles are never mutated.

| Control | Compiled variant | Intended detected failure |
|---|---|---|
| N1 | Drop placed status mapping | Missing/mismatched placed feature entry |
| N2 | Synthetic callback toggles decoy | Intended fixture value did not change |
| N3 | Remove real Boolean rollback | Presence value not restored after WRITE failure |
| N4 | Notify before real save | Observer sees previous durable false value |
| N5 | Remove active-registration identity check | Opened forged inventory accepted |
| N6 | Alter real presence deserialization key | Independent repository reload loses true |
| N7 | Change real presence default to true | Frozen false default violated |
| N8 | Remove active/current-top gates | Replaced inventory executes mutation |

**8/8 compiled (exit 0), failed behavior as intended (exit 1); original-classpath positives pass before and after (39/58/72/205).** Compilation failure is never accepted as negative evidence. Supplemental receipt saved locally outside the later clean build.

## Final verification and native Paper

Official final `scripts/verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'` exited **0**: **Maven clean package PASS; Spotless PASS; 186 discovered/executed/passed harnesses, 28,880 checks; failures 0; missing/invalid 0**. The same six-file formatter pilot remains in force.

Inventory comparison retains **all 184 baseline entry points**, adding only the two contract harnesses. Actual check deltas are:

| Harness | Before | After |
|---|---:|---:|
| SettingsPersistenceContractHarness | new | 72 |
| SettingsDefinitionContractHarness | new | 39 |
| SettingsEntryExtensionHarness | 10 | 58 |
| RepoLinkHarness (new maintained-guide links) | 40 | 45 |
| GuiAuthoringAuditHarness (two new Java files) | 216 | 218 |

Every other harness count is unchanged. The actual total grows **28,714 → 28,880 (+166)**; no entry point or previous check was removed to reach this result. The supplemental compiled negative controls also pass again using the final script after the clean verification.

Isolated native smoke: **Paper 1.21.11-132-c5eb079**, **LuckPerms 5.5.84**, **JDK 21.0.12.1**, loopback **25587**, disposable normal world and player profile. Tested plugin JAR SHA-256 **`413f86262ff59f6ac66538a62484a10d7bda50e4f42f979dedd558421b33f7cb`**. The temporary probe reported **`PHASE03B_NATIVE_PASS checks=48`** at **23:21:08 Europe/Berlin on 2026-10-21**. There were **0 ERROR/SEVERE** in stdout/stderr, all 28 modules enabled/disabled normally and the process exited after clean server shutdown.

Exercised actual PluginCommand `/settings`, native CraftPlayer/Inventory, five main/status defaults, authorized toggle and same-inventory rerender, external commit refresh, durable presence/sound keys, profile unload/reload, Settings Back/parent Settings navigation, parent Visibility, specialized visibility toggle, Manage Visible Players/Back, Visibility Back, Close, denied open, forged native inventory denial and removal of three owned settings observers on module shutdown. No production implementation was replaced by the probe. This is automated native acceptance, **not a real-client visual test**. Operator data/deployed JAR were not touched; temporary worlds/probe/binaries are removed after logs are retained outside the repository.

## Exact changed files

Production (2):

- `src/main/java/dev/vapee/core/settings/SettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java`

Java tests (3):

- `src/test/java/dev/vapee/core/settings/SettingsDefinitionContractHarness.java` (new)
- `src/test/java/dev/vapee/core/settings/SettingsEntryExtensionHarness.java` (retained entry point)
- `src/test/java/dev/vapee/core/player/repository/SettingsPersistenceContractHarness.java` (new)

Supplemental validation:

- `scripts/tests/settings-contracts.Tests.ps1` (new)

Documentation/report (5):

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/SETTINGS_DEVELOPMENT.md` (new)
- `reports/architecture-modernization/PHASE_03B_SETTINGS_ARCHITECTURE.md` (new)

New/expanded harness files individually formatted with pinned Google Java Format 1.22.0 AOSP; six-file Spotless allowlist unchanged. Existing production files have bounded functional edits, no repository-wide formatting. No generated binaries/worlds/profiles will be committed.

## Deferred / Phase 3C handoff

1. Inspect current `ConfigEvolution`, feature parser/fallback/snapshot and ReloadService ownership before changing configuration extension patterns. Keep profile schema/persistence separate from operator startup evolution.
2. Add independent extension/default/unknown-key/backup/future-version/reload rollback tests at existing seams; preserve operator values and language overrides. Reuse canonical verification, not another discovery runner.
3. Preserve explicit side-effect boundaries when adding future preferences. Unifying every feature into a registry/bus or decomposing FilePlayerRepository is outside this phase.

Completion: one local **`Standardize settings extension patterns`** commit on `phase/settings-extension-patterns`. `git diff --check` passes; only the eleven files above belong to the change. Temporary probe/server data were removed after evidence retention; generated target output stays ignored. The actual full commit SHA and post-commit working-tree check are returned with the completion response. No push, merge, rebase, cherry-pick or Phase 3C start.
