# Configuration development

Start with the owning file and consumer. Operator configuration, stored feature data and language overrides have different lifecycles. [ARCHITECTURE.md](ARCHITECTURE.md) describes the boundaries; [CODE_STYLE.md](CODE_STYLE.md) covers code and verification conventions. Player preferences follow [SETTINGS_DEVELOPMENT.md](SETTINGS_DEVELOPMENT.md).

## Ownership inventory

Paths below are relative to `plugins/VapeeCore/` unless marked as bundled resources.

| File/category | Defaults | Runtime owner | Initial load | Runtime reload | Evolution | Writer | Backup |
|---|---|---|---|---|---|---|---|
| `config.yml` v1 | Bundled resource + typed Java fallbacks | `ConfigService` / private nested `CoreConfigState` | `saveDefaultConfig`, `readState` | First participant; complete snapshot | Startup `ConfigEvolution` | Operator; reload never writes | Unique exact-byte backup before an evolved candidate is written |
| `lobby.yml` v2 | Bundled resource + feature defaults | `LobbyConfig`, `LobbyModule` and lobby services | `ConfigFiles.copyDefault`, parser | Snapshot, spawn, NORMAL gamemodes and managed items | Same startup allowlist | Operator; explicit `/setspawn` uses LobbyConfig/SafeFileWriter | Evolution backup; explicit spawn save has its own protected replacement contract |
| `chat.yml`, `private-messages.yml` v1 | Bundled resources + feature fallbacks | Owning config plus service runtime state | `ConfigFiles.copyDefault`, parser, template preparation | Each owning module publishes config and prepared service state | Same startup allowlist | Operator | Evolution backup; no reload save |
| `presentation.yml` v1 | Bundled resource + feature defaults | Config, renderer, service and owned update task | Copy missing resource, parse and prepare renderer | Module also reconciles task/UI effects | Same startup allowlist | Operator | Evolution backup; no reload save |
| `daily-quests.yml` v1 | Bundled resource + DailyQuestConfig defaults | Typed state and quest definition registry | Copy missing resource, validate catalog | Module replaces state/registry and closes quest views | Same startup allowlist | Operator; assignments/progress are separate profile data | Evolution backup; no reload save |
| Player/feature persistence: `players/*.yml`, friends, clans, warps, moderation | Owning domain/repository | Feature services/repositories | Feature-specific readers and schemas | Not operator-config participants; use existing feature mutation/lifecycle paths | **Excluded** from ConfigEvolution | Owning service/repository | Feature-specific; do not assume all repositories use SafeFileWriter |
| `lang/en.yml`, `lang/de.yml` | Bundled catalogs plus installed overrides | `LocalizationService` snapshots / LocalizationModule | Copy only missing catalog, read overrides | Separate second participant | **Excluded** from ConfigEvolution | Operator or explicit language-adoption script | Adoption workflow has its own exact-byte backup; ordinary reload does not write |

The six-file allowlist is explicit in `ConfigEvolution.MANAGED_CONFIGS`: Core 1, Lobby 2, Chat 1, Private Messages 1, Presentation 1, Daily Quests 1. `ConfigResourceContractHarness` independently freezes these names/versions and verifies bundled resources, roots, duplicates and version agreement. No directory-wide YAML discovery is used.

Historical `blackjack.yml` files remain excluded from evolution and reload. Core no longer owns a table loader, writer or bundled default; no operator file is deleted or migrated.

## Add a Core value

1. Add the documented operator default to `src/main/resources/config.yml`.
2. Parse it in `ConfigService.readState()` through an existing appropriate helper. `ConfigValues` owns narrow scalar checks only; the caller retains fallback, diagnostics and domain/range rules.
3. Add the typed field and default to **the private nested `ConfigService.CoreConfigState` record**. There is no separate CoreConfigState source file. Keep the snapshot immutable; defensively copy collection fields in their owning records.
4. Expose a getter and read it from the owning consumer. Do not add another mutable global cache or a string-to-object registry.
5. Existing ConfigService prepare/apply/rollback captures the whole old and candidate records. A normal additional field needs no new ReloadParticipant. Test that prepare leaves active getters/consumer unchanged, apply exposes the complete new state and rollback restores the old usable state.
6. Decide separately whether old installed files should receive the new key through a deliberate schema revision. If yes, update the resource version and matching explicit supported version together, then extend additive-evolution tests. Update the independently frozen expected manifest in ConfigResourceContractHarness only as part of that reviewed version change. Do not bump real versions just to demonstrate a fixture.
7. Extend independent default/type/range/consumer/reload/disk-isolation tests and document the key.

### Worked example: `ranks.track`

The bundled value and Java missing-key fallback are **`ranks`**. `readState()` calls `readNonBlankString`; a valid string is trimmed, a blank or non-string warns and falls back, and a missing value uses the fallback without a write. Existing Bukkit null/contains behavior is retained. Core parsing does not modify the operator file.

The value lives in the immutable `CoreConfigState.rankTrack`, published through the volatile state reference and exposed by `getRankTrack()`. The real `RankService` constructor retains `configService::getRankTrack`, so `getPublicRanks()` asks LuckPerms for the currently active track. It does not need another copied track name or a restart after successful reload. A missing LuckPerms track remains the existing controlled `MISSING_TRACK` result.

`ConfigReloadIntegrationHarness` runs real ConfigService, RankService/LuckPermsService and ChatModule/ChatConfig/ChatService. Only the Paper registration and external LuckPerms lookup boundary are controlled fixtures. It verifies the default, trimmed custom value, old state during preparation, full apply, captured snapshots despite subsequent external disk edits, rollback after a real feature apply and exact file bytes.

## Add a feature-owned value

Use the existing feature config, not Core config merely because it is convenient. For example a DailyQuest value belongs in the bundled `daily-quests.yml`, `DailyQuestConfig` parsing and its immutable `State`, then the actual owning consumer. Existing `DailyQuestModule.prepareReloadPlan` captures both the state and definition-registry snapshot, replaces both on apply and restores both on rollback.

A new catalog entry is operator data interpreted by existing domain validation, not a new Java config field. A new value in an existing snapshot usually extends its existing reload plan. Add a participant only for a genuinely separate lifecycle/ownership boundary and explicitly wire it in VapeeCore.

### Preserve the existing validation policy

- Missing values use the owning fallback. Ordinary Boolean/string helpers use Bukkit's existing `contains` semantics; explicit null often behaves like absence. Security-sensitive staff hierarchy handling deliberately retains explicit null as invalid. Do not normalize these policies silently.
- Wrong scalar types warn/fall back where the feature already does so. Blank strings are valid for `ConfigValues.readString`, but not for rank-track IDs or a nonblank format. Evolving an empty string does not mean its parser must accept it for every domain.
- Numeric ranges belong to the feature: Core limits are positive, reward intervals must safely convert to ticks, Daily Quest count is bounded. Preserve existing fallback messages and limits.
- Template parsing is layered. Chat's config accepts a nonblank format; ChatService validates MiniMessage and falls back with a warning. Presentation and PM have their own template/localized-text preparation. Do not impose one global MiniMessage validator on unrelated strings.
- Invalid YAML or structural catalog errors can abort prepare. For example DailyQuest accepts fallback for an invalid reset scalar but rejects malformed quest definitions/sections. Do not turn these into silent success or change warning/fallback cases into fatal errors for uniformity.

## Startup evolution and version decisions

| Installed state | Actual startup behavior |
|---|---|
| Missing file | Evolution skips it; the owning loader creates bundled defaults |
| Empty/unversioned file | Treat as legacy version 0; additive evolution to the supported resource version |
| Older supported integer version | Add missing defaults, preserve existing operator values, advance only config-version |
| Current version | No rewrite, backup or addition of deleted/missing keys; parser fallbacks still apply |
| Future version | Warn; no downgrade, backup or rewrite; runtime loaders still interpret their supported keys |
| Invalid version metadata | Warn/skip without modification; oversized or non-decimal/invalid tagged versions do not authorize migration |
| Invalid YAML/root/legacy duplicate or recursive structure | Refuse evolution without replacing the source; review the reported cause |
| `/core reload` | Read/prepare/apply only; **no schema evolution** |

Adding a bundled default alone does not update already-current operator files. Deliberately evaluate compatibility, both version declarations, migration tests and fallbacks. Defaults are additive missing-key material, never permission to overwrite existing false/zero/empty/list/null/custom values. A type conflict stays operator-owned and uses its existing parser's warning/fallback policy. Existing lists are atomic, not appended. Unknown keys/subtrees survive. The node-based merger preserves supported header/inline comments and scalar types; this does not promise byte-identical migrated formatting.

Evolution validates a merged candidate in memory, writes a unique original-byte backup under `backups/config/<file>.v<old>-to-v<new>-<time>-<uuid>.bak`, then writes/rechecks a temporary candidate. Earlier backups are never replaced or pruned. Before replacement it checks the installed bytes for concurrent edits. Atomic replacement is preferred; unsupported atomic moves use the protected fallback. A failed fallback copies the backup back to the destination. An unsuccessful restore aborts startup and now logs the exact retained backup path plus the original restoration error; manual reconciliation is required. This writer has its own contract and does not introduce a SafeFileWriter recovery marker.

Use the package-local supplied-version constructor only in isolated evolution fixtures. Public startup constructors always use the unchanged six-file allowlist. `ConfigEvolutionExtensionHarness` declares **TEST_EXTENSION_OPERATOR_FLAG** as fixture `example.additional-flag`, supplies version 2 defaults and starts with an actual version 1 file. It proves **1 → 2**, separately **unversioned → 2**, preserved operator values/comments, exact backups, idempotency, future/invalid refusal and injected I/O/recovery failures. No production option/version is introduced.

## Reload participation and effects

VapeeCore explicitly orders **ConfigService → LocalizationModule → LobbyModule → ChatModule → PrivateMessageModule → PresentationModule → DailyQuestModule**. All prepare before any apply. Prepare may parse/build candidates but must not publish state or write config/data files. Apply publishes prepared state and owned effects; an error rolls back the failed participant first, then prior applied participants in reverse order. Rollback errors yield `ROLLBACK_INCOMPLETE`; nested calls yield `ALREADY_RUNNING`. The coordinator is synchronous; sequential participant apply is not a globally atomic multi-service transaction.

Snapshot assignment is only part of some participants' responsibility: Lobby reapplies spawn/gamemode/items, Presentation restores renderer/task/UI ownership, Localization refreshes recipient views, and DailyQuest swaps its catalog and closes menus. Rollback restores directly owned state/effects as implemented; it does not reopen views deliberately closed during an apply. Daily assignments/progress are not recreated just because a catalog reload succeeded. Use the existing cycle/force paths. Modules, permissions, schemas, world state and feature persistence do not become dynamically reloadable by adding a config key.

## Contributor test checklist and edit count

Before and after Phase 3C, a representative Core Boolean uses **3 production/resource files without evolution**: resource, ConfigService (parser + nested snapshot/default + getter) and its owning consumer. A deliberate supported-version change also edits ConfigEvolution: **4 files**. A representative two-file feature test plan plus one documentation file gives **6 files without evolution in either version**. With evolution, the comparable plan is **7 before / 8 after**, because the new independent frozen-manifest oracle is a third test-file edit. Actual feature test placement varies. This extra independent check is intentional; there is no promised file-count reduction. Feature-owned values follow their existing config/module/runtime shape. Reload registration is unchanged for a field within an existing participant.

The improvement is independently executable resource/version consistency, a real future additive transition, real multi-component rollback proof and a documented path. Resource/supported versions intentionally remain separate; tests enforce agreement. Parser/domain defaults remain with their owners, rather than moving into an untyped registry.

Validate missing/default/wrong-type/range values, previous custom values, snapshots before apply, success, prepare/apply/rollback failures, retry, known effect ownership, exact disk bytes, persistence/lang exclusions and real consumer behavior. Existing relevant harnesses include ConfigService, ConfigEvolution, ConfigHelpers, DefaultConsistency, ReloadService, feature ConfigHarnesses, DailyQuestLifecycle and LocalizationReload.

```powershell
# Supplemental isolated compiled/resource negative controls; uses canonical harness classes:
pwsh -File ./scripts/tests/configuration-contracts.Tests.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
# Official build + six-file formatter pilot + all discovered harnesses:
pwsh -File ./scripts/verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
```

New focused contracts are `ConfigResourceContractHarness`, `ConfigEvolutionExtensionHarness` and `ConfigReloadIntegrationHarness` under `src/test/java/dev/vapee/core/config/`. The supplemental script modifies temporary copies only, detects eight intended failures (seven compiled Java variants plus one resource override) and reruns ordinary-classpath positives. Installed operator YAML, language overrides, profiles, feature data and deployed JAR must never serve as mutation fixtures.
