# Settings development

Use this path for player preferences. Operator configuration follows a different lifecycle; see [ARCHITECTURE.md](ARCHITECTURE.md). Follow [CODE_STYLE.md](CODE_STYLE.md) and run `scripts/verify.ps1` before committing.

## Choose the right owner

| Responsibility | Owner |
|---|---|
| Boolean default, field and current value | `player/settings/PlayerSettings`, held by the loaded `CorePlayer` |
| Visibility mode, filters and selected UUIDs | `PlayerVisibilitySettings`; preserve its specialized result/mode contracts |
| Reads, mutation, no-op, persistence rollback and commit notification | `PlayerSettingsService` |
| Loaded profiles and save/unload boundary | `PlayerService` |
| YAML serialization/deserialization and fallback | `player/repository/FilePlayerRepository` |
| Owned paths, version checks, preservation and candidate comparison | `PlayerFileSchema` |
| Atomic replacement / protected fallback | Existing `persistence/SafeFileWriter` |
| Feature icon, optional status, text, state reader, action and post-save effect | `SettingsMenuEntries` |
| Semantic identity, visible composition, dimensions and positions | `SettingsMenu.Action` / `DEFAULT_LAYOUT` |
| Owned view rendering, commit/locale subscription | `SettingsMenu` |
| Click/drag authority, result handling and feedback | `SettingsListener` |
| Handler/observer/command ownership and shutdown | `SettingsModule` |
| Text | Bundled `lang/en.yml` and `lang/de.yml`, rendered through `MessageService` for the current viewer |

The GUI reads current profile state; it does not own another preference cache, default or save loop. Public setters on the model are used for reconstruction and rollback; new GUI actions must use the owning service.

## Add an ordinary Boolean

1. **Domain:** add the default, field, constructor/default-factory plumbing and accessors to `PlayerSettings`. Choose the absent-key behavior deliberately.
2. **Service:** add a loaded-profile reader and a setter delegating to the existing Boolean `updateSettings` helper. `false` means unavailable profile; an unchanged value returns `true` without save/event. A save exception restores the old value and propagates. Notify only after persistence succeeds. Independent observer exceptions do not undo the durable commit or stop subsequent observers.
3. **Persistence:** add the explicit YAML write and read with the domain default in `FilePlayerRepository`, and the owned path in `PlayerFileSchema.OWNED_FIELDS`. These three definitions are intentionally explicit. Preserve old profiles, unknown siblings and unrelated aggregate data. A new optional field does not automatically require a version bump; justify migrations separately. Do not put profile fields into `ConfigEvolution`.
4. **GUI definition:** add the local `SettingsMenu.Action` identity and one definition in `SettingsMenuEntries`. Reuse its `toggle` helper for an ordinary Boolean: material, translated name/hints, current-state reader, service setter and any justified post-save presentation effect. Do not add another listener switch or feature-action allowlist.
5. **Composition:** optionally place `.setting(Action.YOUR_FEATURE, iconSlot, statusSlot)` in the owning layout. Icon-only uses two arguments. Omitting the placement hides the feature without deleting domain support. Do not duplicate positions in listeners or tests.
6. **Language:** add the feature's name/hints to both bundled catalogs; use `tr(...)` and literal/safe components. Existing operator overrides remain authoritative. Changing client language rerenders the view without saving preferences.
7. **Tests:** add independent default, mutation/no-op/unloaded, real YAML round-trip, failure rollback, GUI mapping/action, permission/identity, EN/DE and lifecycle coverage. Then run the official verifier.

### Composition contracts

`defaults(layout)` constructs only placed features in enum declaration order, never hash-map iteration order. A placed feature without a definition fails immediately. `SettingsMenu` takes an immutable snapshot and rejects missing entries, mismatched icon/status pairs, duplicates, bounds violations and reserved-control collisions. An unplaced supported definition is valid. A missing status has slot `-1` and no click binding. Icon and status resolve to the same actual `SettingsMenuEntry` for rendering and dispatch.

This validates geometry/coverage, not the correctness of arbitrary callbacks: independently test that each definition reads and mutates its intended domain property. A layout alone never implements behavior.

`Result.SAVED` requests the existing rerender/feedback path after a successful mutation; it does not perform persistence itself. `OPENED` means a delegated menu transition, without toggle feedback. `UNAVAILABLE` means the required profile/state is missing. A thrown save failure is handled by the listener, which displays actual rolled-back state and gives no success sound. Normal menus have no manual Refresh control; the optional Java `REFRESH` action retains its existing read-only rerender semantics.

## Multi-state values and menu-only actions

Use a dedicated representation for non-Booleans: visibility uses `VisibilityMode`, filters and UUID membership in `PlayerVisibilitySettings`. Its service preserves exact-mode rollback and add/remove results. Do not represent ALL/FILTERED/HIDDEN as an ordinary Boolean or copy visibility policy into a GUI entry.

Navigation adds no profile field, YAML property or default. The optional root `VISIBILITY` definition delegates to its opener and returns `OPENED`; it stays absent from the production Settings layout. Back/Close/Refresh are controls, not persisted settings. LobbyExperience owns the Settings and Visibility Back callbacks to Player Menu and resets them to safe close behavior at teardown. Keep the current button positions.

## Worked example: Friend Presence Notifications

The existing preference is opt-in: `PlayerSettings` defaults it to **false**. `areFriendPresenceNotificationsEnabled(UUID)` reads only loaded profiles; `setFriendPresenceNotificationsEnabled(UUID, boolean)` uses the shared service transaction. The stored key is exactly **`settings.friend-presence-notifications`**.

`FilePlayerRepository` writes the Boolean and reads only actual Boolean values. Missing values use the model default; invalid values warn and use that same default. Reads do not rewrite old files. `PlayerFileSchema` owns this path, preserves unknown siblings, and compares written candidate values against the canonical aggregate before replacement. Existing schema version remains **1**. `SafeFileWriter` first validates a temporary candidate, then attempts atomic replacement; unsupported atomic moves use protected backup/restore. Unresolved restoration blocks further reads/writes until reconciliation. GUI code owns none of that recovery logic.

The `FRIEND_PRESENCE` definition supplies the bell, translated name/hint, enabled/disabled icon/status and the service callback. Its positions come only from `DEFAULT_LAYOUT`. `FriendPresenceNotifier` reads the recipient's current preference when a real friend presence event is delivered; it also retains online/friend/ignore checks. Toggling a preference does not fabricate a presence event.

## Side effects: use the existing feature boundary

| Preference / consumer | Actual application timing |
|---|---|
| All Settings/Visibility/VisiblePlayers active views | Settings commit observer rerenders affected owned view; same-view actions also rerender on success |
| Scoreboard | Root GUI's successful save invokes `PresentationService.updatePlayer`; other callers are read by existing presentation update/lifecycle paths, not a new settings observer |
| Sounds | Feedback checks the currently saved sound value; disabling is silent |
| Private Messages | `PrivateMessageService` reads recipient setting for each send |
| Friend requests | `FriendModule` request policy reads loaded/known-offline preference for each request; known-offline read does not load a runtime profile |
| Friend presence | `FriendPresenceNotifier` reads recipient preference for each presence notification |
| Visibility / lobby item | Existing GUI/command/hotbar/selection adapters apply visibility after successful service mutation; master changes also refresh the hotbar. Friend/Ignore relationship listeners refresh affected pairs |

A commit observer refreshes views; it does not automatically apply every domain side effect. Direct service callers must retain their owning application boundary. Do not add global polling/event buses or duplicate policy to make all rows identical.

## Security, localization and lifecycle tests

Entry callbacks run only after owner UUID, exact holder binding, active inventory identity, current top inventory, permission, top-click and allowed click type checks. A loaded profile is required before executing the entry. All settings-holder clicks/drags are cancelled, including invalid/forged views. Names, materials, lore and titles never authorize actions. Stale/replaced/closed/shutdown inventories remain inert. Post-commit refresh checks current ownership/permission/profile again and drops invalid views.

Rendering uses current recipient language and restores temporary viewer context in `finally`, including renderer failure. Disable removes locale/domain subscriptions, closes only owned current views and clears bindings. The Phase 2B lifecycle tests also cover failed starts, retry and duplicate observer/handler prevention.

| Proof | Executable references under `src/test/java/dev/vapee/core/` |
|---|---|
| Independent definitions, stable construction, optional status, invalid layout and render-context failure | `settings/SettingsDefinitionContractHarness` |
| Independent `TEST_EXTENSION_TOGGLE` render, owned click, success/failure, commit refresh, permissions, stale views | `settings/SettingsEntryExtensionHarness` |
| Frozen defaults/keys, real presence save/reload, candidate validation, I/O failure rollback, observers and unrelated data | `player/repository/SettingsPersistenceContractHarness` |
| Existing service modes/results and visibility storage | `player/settings/PlayerSettingsServiceHarness`, `player/repository/PlayerVisibilityPersistenceHarness`, `PlayerSchemaPersistenceHarness` |
| Existing GUI behavior, navigation, authority and locale | `settings/SettingsMenuHarness`, `SettingsMenuSecurityHarness`, `SettingsLocalizationHarness`, `settings/visibility/SettingsNavigationHarness`, `VisibilityMenuSecurityHarness` |
| Module failure/disable/retry | `settings/visibility/SettingsModuleLifecycleHarness` |

The synthetic toggle supplies its own fixture state, presentation and mutation through the existing entry-list constructor. It needs no production enum member, switch, model field, YAML key or translation. It proves GUI extension only. The real presence preference separately proves domain/service/storage behavior. A fixture commit followed by a real service commit exercises the actual menu observer; this does not claim synthetic state was persisted.

Repeat the eight isolated compiled negative controls with:

```powershell
pwsh -File ./scripts/tests/settings-contracts.Tests.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
```

They mutate temporary source copies, require compilation and the intended behavioral failure, then rerun ordinary-classpath positives. Results are written to `target/verification/settings-contracts/`; this supplements the official verifier, not another regression runner.

## Before / after extension checklist

| Ordinary Boolean change | Before Phase 3B | Now |
|---|---|---|
| Domain/default/accessors, service reader/setter | Required explicit edits | Same owners and edits |
| YAML write, YAML read, schema-owned path | Three explicit persistence definitions | Same; independent real-file contract tests |
| UI identity, definition, layout, repeated feature allowlist | Four UI edit locations in two files | Three locations; non-control actions derive the feature set |
| EN/DE | Two bundled files | Same; no live-override edits |
| Definition drift | Slot collisions/bounds checked | Also missing placed entries and mismatched pairs fail early; deterministic construction |
| Tests | Feature behavior/security/storage coverage | Add independent definition, synthetic extension and transaction checks |

Counted mechanically: **8 production/resource files before and after**, plus the feature's tests. A representative test plan edits four test files (service, real persistence, GUI, locale): **12 files total** either way, with lifecycle tests reused. There remains one feature-specific GUI behavior definition, no duplicated listener rendering/dispatch switch, and three intentional persistence definitions. These necessary domain/storage edits are not architectural defects. The improvement is one fewer repeated UI action list and earlier detection of incomplete or inconsistent extensions.
