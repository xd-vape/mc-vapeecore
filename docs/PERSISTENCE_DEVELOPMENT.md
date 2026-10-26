# Persistence development

Player preferences are part of one `CorePlayer` aggregate. Start here when adding stored player data; use [SETTINGS_DEVELOPMENT.md](SETTINGS_DEVELOPMENT.md) for the service and GUI path, [ARCHITECTURE.md](ARCHITECTURE.md) for ownership and [CODE_STYLE.md](CODE_STYLE.md) for conventions.

## Choose the storage owner

| Data | Runtime owner | Storage owner |
|---|---|---|
| Identity, join timestamps and loaded profiles | `CorePlayer`, `PlayerService` | `player/repository/FilePlayerRepository`, one `players/<uuid>.yml` |
| Settings and visibility | `PlayerSettings`, `PlayerVisibilitySettings`, `PlayerSettingsService` | Package-local `PlayerSettingsYamlCodec` maps values; `FilePlayerRepository` commits the aggregate |
| Coins | `CoinWallet`, `EconomyService` | Same player aggregate; direct mutations save/rollback, deferred rewards retain their existing batch boundary |
| Ignored players | `PlayerSocial`, `SocialService` | Same aggregate; Social publishes its derived snapshot only after save |
| Online reward baseline | `OnlineRewardProgress`, `OnlineRewardService` | Same aggregate; uninitialized progress has no persisted counter |
| Quest assignments/progress and daily cycle | `PlayerQuestState`, `PlayerDailyQuestState`, owning quest services | Same aggregate; retain existing dirty/flush and reconciliation rules |
| Friendships and requests | `FriendService` | `FileFriendRepository` / `friends.yml` |
| Clans and invitations | `ClanService` | `FileClanRepository` / `clans.yml` |
| Moderation records | `ModerationService` | `FileModerationRepository` / `moderation.yml` |
| Warps, lobby spawn, Blackjack table definitions | Their existing feature services/configuration | `WarpConfig`, `LobbyConfig`, `BlackjackTableConfig`; preserve their individual file contracts |
| Operator configuration | `ConfigService` and feature configs | Startup `ConfigEvolution` allowlist and coordinated reload; separate from player schema evolution |

Choose an independent feature repository when data has its own identity, aggregate and lifecycle, such as friendships between players. A new preference is not a reason to split player data into another file. Do not copy profiles or balances into another cache, introduce a second save loop or assume all repositories use `SafeFileWriter`.

## Player aggregate read and commit

`FilePlayerRepository` owns directories, canonical UUID filenames, identity indexing, public read/save entry points and storage diagnostics. Startup indexes valid identity metadata; it does not save/migrate profiles. Reads validate the source document, map identity and domain sections and construct `CorePlayer`. The Settings codec receives the owner UUID, diagnostic source path, Bukkit configuration, raw source map and logger; it owns no files, service, schema version, cache or transaction.

A save rereads and validates the latest source, constructs canonical state for every owned domain, overlays it through `PlayerFileSchema`, validates the temporary candidate and commits with `SafeFileWriter`. The name index is updated **after** replacement succeeds. Settings are never committed separately. A stale loaded aggregate cannot overwrite a file that now has a future schema, an invalid core structure, unresolved recovery evidence or edits made during preparation.

`PlayerService` owns the loaded-player map. Loading an existing profile updates its runtime name/last-join without an immediate save. Loading a new profile saves its defaults before caching it. `findKnownPlayer` can read an offline profile without loading it into the cache. `savePlayer` saves the current loaded aggregate; `unloadPlayer` saves before eviction and retains the profile if saving throws. `saveAll` logs individual failures and continues with other profiles; it does not make several files one transaction.

### PlayerModule shutdown and incomplete saves

The public `void saveAll()` contract remains log-and-continue for individual persistence failures.
PlayerModule uses a package-local strict variant of the same loop: every loaded profile is attempted,
then an incomplete batch throws with its exact failed UUID-to-cause mapping. Logging failures are
reported after the independent save attempts. Successful profiles remain committed. No successful
Settings/Reward/Quest observer event is fabricated by the shutdown fallback.

Shutdown first closes active getters and generation-specific listener authority, then independently
attempts listener removal and all profile commits. Only complete saving permits clearing the loaded
cache. On failure, the module privately retains the same PlayerService and aggregate objects for an
explicit same-process `disable()` retry. Normal getters stay closed, and `enable()` refuses to replace
the pending runtime. After deliberate reconciliation of the underlying storage conflict, a successful
retry saves the retained in-memory values and releases the cache. Uncertain listener releases remain
separately retryable; a listener error cannot skip persistence, and a save error cannot hide it.

The SEVERE incomplete-shutdown diagnostic identifies affected profiles, original causes, required
operator intervention and the JVM-exit limitation. ModuleManager catches disable errors, continues
reverse shutdown and clears its enabled list; it neither schedules nor automatically retries pending
Player saves. Retention works only while the module object and JVM remain alive. Complete server
shutdown, forced termination, crash or power loss can still lose unresolved in-memory changes.
This is not crash-safe storage, an emergency backup format or permission to remove recovery evidence.

Repository validation, schema 1/legacy 0, unknown-field preservation, exact migration backups,
candidate validation, atomic/fallback recovery and post-commit indexing are unchanged. Settings,
Economy and Social keep their immediate transaction rules; Reward/Quest keep existing deferred
flush rules. The Player shutdown fallback runs after its dependent modules in the existing order.
See [module lifecycle guidance](MODULE_DEVELOPMENT.md) and the
[Phase 5C evidence](../reports/architecture-modernization/PHASE_05C_PLAYER_LIFECYCLE.md).

### Quest shutdown flush and the original aggregate

Quest's public log-and-continue flush is not evidence that every dirty profile committed. Its
package-local strict lifecycle flush attempts each UUID independently and reports original causes;
failed saves keep Quest dirty tracking. Incomplete cleanup privately retains the original QuestService
and its borrowed PlayerService, with inactive getters, for an explicit same-process disable retry.
It never reconstructs unsaved progress from older disk or clears RewardService tracking. PlayerModule
still saves the same aggregate later; only a successful save-before-eviction/cache release establishes
that handoff. If both owners fail, their retained memory is not durable and JVM exit can lose it.
See [Quest lifecycle and flush guidance](MODULE_DEVELOPMENT.md#quest-lifecycle-und-flush-sicherheit).

## Add a player profile field

1. Establish the domain owner, validation, missing-value behavior and mutation boundary. Keep a single authoritative value in `CorePlayer` or its existing domain component.
2. Add explicit mapping in the owning repository section. Settings/visibility mapping belongs in `PlayerSettingsYamlCodec`; other player domains currently remain in `FilePlayerRepository`.
3. Declare the exact owned path in `PlayerFileSchema.OWNED_FIELDS`. Decide whether it is an open container with unknown siblings or a wholly owned structure. Do not recursively claim arbitrary extensions.
4. Define legacy and malformed-value behavior. Required identity, Economy and Social errors remain fatal; optional Settings, reward and quest fields have specific warning/fallback rules. Do not globally soften validation.
5. Decide explicitly whether an actual migration/version change is necessary. Optional fields with deliberate absent-key defaults do not automatically require a bump. This extraction keeps schema **1**, legacy **0**, and adds no field.
6. Use the owning service to save/rollback or its existing deferred dirty/flush path. Emit success/change notifications only at the existing successful commit boundary.
7. Test independent expected YAML and domain values, full-profile preservation and failures; do not rely only on a reader/writer round trip.

## Settings example: an existing Boolean

`settings.sounds` demonstrates the mapping path: the domain default is `true`; the codec explicitly reads the Boolean with `settings.isSoundsEnabled()` as fallback and writes `settings.sounds`. `PlayerFileSchema` separately owns that path. `PlayerSettingsService#setSoundsEnabled` stores the old value, mutates, calls `PlayerService.savePlayer`, restores on failure and notifies after success. The GUI calls this service and does not write YAML.

For an intentionally approved new setting, add its domain/default/accessors, service API, **both mappings in the codec**, schema ownership, appropriate GUI definition/layout, EN/DE text and tests. Read and write mapping now have one maintenance file; the domain, service and schema remain distinct required edit locations. No generic codec registry or reflection is involved.

### Existing paths and compatibility

| Paths under `settings` | Existing default / behavior |
|---|---|
| `scoreboard`, `sounds`, `private-messages`, `friend-requests` | `true`, obtained from `PlayerSettings.defaults()` |
| `friend-presence-notifications` | `false`, obtained from the same domain object |
| `lobby-players-visible` | Legacy Boolean; domain default `true`. `true` maps to ALL, `false` to FILTERED |
| `visibility.mode` | Explicit ALL/FILTERED/HIDDEN wins over the legacy Boolean |
| `visibility.show-friends`, `show-staff`, `show-added-users`, `show-game-participants` | Existing filter defaults `false`; independent of the selected mode |
| `visibility.added-players` | Empty set by default; write a sorted list of canonical UUID strings, including an explicit empty list |

Missing mode retains the legacy mapping. Explicit invalid mode, including `null`, warns and uses HIDDEN. Detection uses the **raw source map's key presence**, because Bukkit's `contains` does not distinguish explicit null from absence. Missing/null optional Booleans retain their default; other wrong types warn. Invalid Settings/visibility sections warn without rejecting a valid core profile. Invalid visibility preserves the legacy mode and unrelated preferences.

Added-player entries must be UUID strings: malformed or shortened noncanonical UUIDs and the owner's UUID warn/skip; canonical uppercase text is accepted case-insensitively. Repeated UUIDs deduplicate without warnings. Keep the existing warning text, order, frequency, owner and actual source path. Reads never normalize/rewrite stored text. Canonicalization occurs only during a legitimate aggregate save.

## Schema, unknown fields and migration

`PlayerFileSchema` owns strict map-root/duplicate-key parsing, integral version metadata, legacy evolution, future refusal, explicit owned paths and canonical candidate comparison. Unversioned files and explicit version 0 load as legacy; version 1 is current. Negative, null, nonintegral/incorrectly typed and future versions are rejected. Do not move these rules into a domain codec.

Open siblings such as `settings.future-extension`, `settings.visibility.custom`, `economy.custom` and unknown root subtrees survive saves. `quests.active` is **closed**: the canonical current assignments replace that whole subtree, so removed assignments and unknown assignment fields do not resurrect. Overlay copies along owned paths so a YAML alias into an unrelated subtree is not indirectly mutated. Normal save promises semantic YAML preservation of unknown data, not original comments/whitespace. Failed writes and migration/recovery copies have their separate exact-byte guarantees.

Legacy reads/indexing perform no eager rewrite. Before the first successful legacy replacement, the repository protects the exact original bytes in `<uuid>.yml.vapeecore-pre-migration.bak`. An existing identical copy may be reused; conflicting/nonregular evidence requires operator reconciliation. Current/new profiles do not create or replace a migration backup. Player migration is not `ConfigEvolution`: the latter handles its six startup operator resources and excludes player files, feature data and language overrides.

## Safe writes, recovery and rollback

`SafeFileWriter` serializes before opening a candidate, writes/closes it, runs repository validation, compares the exact serialized candidate text, runs the source-change/migration protection callback and attempts atomic replacement. Ordinary atomic I/O failure propagates; only `AtomicMoveNotSupportedException` selects the fallback. No second writer belongs in the codec.

Fallback protects an existing regular destination in `<uuid>.yml.vapeecore-recovery.bak` without overwriting older evidence. Failed replacement attempts restoration. Failed restoration retains the recovery copy, reports the failure and blocks subsequent reads/writes via `requireReconciled`. Reconcile source/recovery evidence deliberately before removing the marker; never silently delete it to make writes resume. Cleanup failures are warnings and cannot undo an already committed file or trigger false domain rollback.

Settings mutate in memory before saving. A failed repository commit must throw so `PlayerSettingsService` restores the exact previous Boolean, mode or membership and emits no successful observer event. Unload must not evict after failed save; the name index must not publish an attempted rename. Preserve the exception and diagnostic chain. Other domains' deferred persistence remains intentional; extracting mapping does not change reward batching or quest retry behavior.

## Tests and contributor checklist

- Legacy/current load, no eager rewrite and independently frozen defaults/paths.
- Legacy missing/explicit/null/invalid visibility mode; wrong Boolean/section types and exact contextual warnings.
- UUID rejection/deduplication/canonical output and stable order.
- Full profile load → owning Settings service mutation → save → reload, including identity, visibility, wallet, Social, rewards, all quest statuses and daily cycle.
- Unknown root/nested extension preservation, closed assignments and YAML alias isolation.
- Future/invalid schema, duplicate-key/invalid YAML refusal and mutation through a stale loaded profile.
- Atomic commit, changed source/candidate refusal, unsupported-atomic fallback, exact migration backup, recovery and blocked unresolved evidence.
- Failed unload/cache retention, failed Settings rollback, observer timing and post-commit name index consistency.
- Complete `scripts/verify.ps1`, retained harness inventory, isolated Paper smoke, untouched operator files.

`PlayerSettingsYamlCodecHarness` independently exercises the mapper and real aggregate commits; `PlayerSchemaPersistenceHarness`, `SettingsPersistenceContractHarness`, `PlayerVisibilityPersistenceHarness`, `VisibilityModeHarness`, `PlayerRewardPersistenceHarness`, `PlayerQuestPersistenceHarness` and `PlayerSettingsServiceHarness` retain the surrounding contracts. Use the existing `PersistenceFaults` FileAccess seam in disposable directories.

Repeat the ten compiled negative controls:

```powershell
pwsh -File ./scripts/tests/player-settings-codec.Tests.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
```

Each temporary variant must compile and fail its intended behavioral assertion. Ordinary-classpath positives run before and after; receipts are under `target/verification/player-settings-codec/`. This supplements the canonical verifier rather than replacing its discovery. Never use installed profiles, deployed JARs or existing backups as test fixtures.

## Remaining boundaries

`FilePlayerRepository` still maps identity, Wallet, Social, online rewards, regular quests and daily cycle and coordinates one safe aggregate commit. This pilot extracts only Settings/visibility mapping. Evaluate any later domain extraction against a concrete maintenance need and independent compatibility tests; there is no blanket plan to create one class per section.
