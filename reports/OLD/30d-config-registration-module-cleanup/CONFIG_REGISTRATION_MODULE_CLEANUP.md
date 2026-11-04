# Phase 30D – Config Registration and Module Cleanup

## Baseline and scope

- Repository: https://github.com/xd-vape/mc-vapeecore
- Branch: `phase/30d-config-registration-module-cleanup`
- Exact initial HEAD: `228a1f21c70da2e84afc6eb4be521b6d2d7acdbb`
- Initial subject: `Merge Phase 30C - GUI Inventory and Item Cleanup`
- Initial working tree: clean; SHA-256 snapshot of all 465 tracked files and eight live YAML files.
- One worker, existing checkout, no subagents, no new worktree.
- Historical baseline: 104 executable harnesses, 17,602 checks, zero failures.
- Scope: **LIFE-001 and CONFIG-001 only**.

The source audit covered UtilityModule, UtilityService, UtilityListener, InvseeService,
CoreModule, ModuleManager, the Moderation/Friend/Clan/Settings/DailyQuest comparison
modules, all configuration classes below, reload coordinator/wiring, and relevant
feature, lifecycle and config harnesses. The historical full audit and Phase
30A.1/30B/30C reports remain historical evidence rather than rewritten status reports.

## LIFE-001: root cause and acquisition map

The old enable installed twelve executor/completer pairs followed by two listeners.
It recorded each command only after both setters succeeded and had no local
failure cleanup. ModuleManager records a module only after enable returns, so its
predecessor rollback cannot clean a failed Utility attempt. Published service fields
were still null at that point, making normal disable insufficient as a fallback.

| Order | Acquired resource | Ownership / release |
|---|---|---|
| 1 | UtilityService | Local candidate; cleanup restores its managed transient movement state. |
| 2 | UtilityListener | Local candidate; disable deactivates queued join normalization. |
| 3 | InvseeService | Local candidate; disable closes its owned views through the existing implementation. |
| 4–15 | build, fly, speed, gamemode, tp, tphere, heal, feed, ping, clear, invsee, enderchest | Explicit source registrations; each resolved command pair is tracked **before** either setter. |
| 16 | UtilityListener event registration | Tracked before registerEvents, including a partially installed registration. |
| 17 | InvseeService event registration | Same rule. |
| 18 | Success log, then field publication | All three runtime fields become available only after the entire attempt succeeds. |

The production constructor retains JavaPlugin, LobbyModule, ActivityModule,
RankModule and MessageService. Its explicit command construction retains the
same services and OnlineStaffTargetGuard. Dependency lookups now occur inside
the guarded attempt after service candidate construction, before any command
registration; a dependency failure therefore also cleans those candidates.

The small package-private factories, CommandRegistration callback and Hooks
boundary substitute external I/O in the lifecycle harness. CommandHook contains
only the real command's executor/completer setter callbacks. Production adapters
call PluginCommand setters, PluginManager.registerEvents and HandlerList.unregisterAll
directly. There is no command discovery, reflection registration, utility facade,
global tracker, DI container or new module dependency. Reflection in the harness
only checks private ownership lists/fields; it does not bootstrap Paper registries.

Cleanup attempts Invsee disable/unregistration, UtilityListener disable/unregistration,
UtilityService cleanup, then command pairs in reverse acquisition order.
Executor and completer removal are independent attempts, so a setter cleanup
exception cannot skip its partner or subsequent commands. Lists are cleared.

Failed enable passes the original RuntimeException into cleanup and rethrows that
same object; secondary RuntimeExceptions are suppressed in attempt order.
Self-suppression is guarded. Normal disable likewise tries every resource, then
clears all three fields in finally and throws the first cleanup failure with later
failures suppressed. Repeated disable is harmless. A second enable after rollback
uses fresh candidates and installs exactly the expected hooks.

Cleanup isolation guarantees every release is attempted and local ownership is
forgotten even when a release throws. It cannot guarantee an external API completed
an operation that itself failed; the original/secondary exceptions remain visible.
The harness injects observable release failures after hook removal to verify both
continued release attempts and clean state. No retry policy or global cleanup
framework is introduced. RuntimeException is the existing lifecycle failure boundary.
UtilityService and InvseeService implementations themselves remain byte-identical;
the distinct bulk-resource LIFE-002 finding is not addressed.

UtilityListener has only a package-private scheduler/enabled-predicate seam.
Its production join task, plugin-enabled/online checks, event behavior and active
flag are unchanged. The harness exercises the real listener and proves queued
normalization remains inactive after normal disable and failed enable.

## CONFIG-001: audited inventory before extraction

Repeated create-directories/exists/open/copy code and scalar type-check/fallback
code obscured meaningful policy differences. The inventory below guided the
extraction; no file parser, typed state or reload owner was centralized.

| Config class | Boolean / String / nonblank | Numeric / list / domain rules | Missing, invalid and fatal policy | Reload owner |
|---|---|---|---|---|
| ConfigService | Boolean; blank-permitting String shared. Rank track remains local nonblank + trim. | Positive int/long, tick overflow, friend/clan ranges, online-reward MiniMessage; ordered staff group list remain local. | Missing optional keys silently default; wrong types warn. Explicit staff null and null parents retain sentinel/warning/default. Load errors fatal. | ConfigService |
| ChatConfig | Boolean and raw nonblank String shared; caller checks missing format first. | LuckPerms enum trim/lowercase local; runtime template validation remains in ChatService. | Missing enabled/format silent; wrong type or blank format warns; load/resource failure fatal. | ChatModule |
| PrivateMessageConfig | Boolean and raw nonblank String shared. | Pair of feature-owned format defaults; runtime template validation remains local. | Missing enabled silent; missing outgoing/incoming each warns, as do invalid/blank formats. Fatal I/O unchanged. | PrivateMessageModule |
| PresentationConfig | Boolean and blank-permitting String shared. | Positive whole update interval, elementwise list recovery, empty lists, 15-line scoreboard cap and meta enum local. | Missing optional values silent; invalid entries warn/fallback; load/resource failure fatal. | PresentationModule |
| LobbyConfig | Boolean shared, fallback true. | Spawn numbers/world, strict/non-strict spawn, GameMode normalization, message section and strict MiniMessage validation local. | Invalid startup spawn warns/empty; invalid reload spawn throws. Invalid optional settings warn. saveSpawn/save unchanged. | LobbyModule |
| DailyQuestConfig | Boolean shared, fallback false. | 1..10,000 slots, strict HH:mm, system/IANA zone, sorted immutable definitions, required values and definition validation local. | Optional invalid values warn/default; invalid definition rejects whole catalog. Enabled empty/short catalog warnings retained. | DailyQuestModule |
| WarpConfig | Navigation Boolean remains local. | Optional navigation metadata, explicit null retention, location/material/ID validation; mutable warp persistence. | Malformed entries skipped with warnings; file failures fatal; existing save/retry policy retained. | None |
| BlackjackTableConfig | Draft Boolean remains local and throws for invalid type. | Draft/seat/position validation and existing persistence. | Malformed drafts skipped with warnings; file failures fatal; existing save/retry policy retained. | None |
| OnlineRewardConfig | Immutable validated value record, no YAML reader. | Positive intervals/coins, checked tick conversion, strict template validation. | Constructor/domain rules; no file creation. | ConfigService owns input |
| StaffHierarchyConfig | Immutable validated value record, no YAML reader. | LOW-to-HIGH group ordering, normalization, controls/whitespace/duplicate rejection. | Security rules unchanged; no file creation. | ConfigService owns input |

## Resource creation matrix

| Owner | Baseline creation | Phase 30D |
|---|---|---|
| ConfigService | JavaPlugin.saveDefaultConfig; path test constructor has no-op saver. | Unchanged. |
| ChatConfig | getResource(chat.yml), create parents, exists guard, Files.copy. | ConfigFiles; original contextual missing-resource SEVERE/fatal, IOException wrapper and created-file INFO retained. |
| PrivateMessageConfig | Same pattern for private-messages.yml. | Same narrow migration and diagnostics. |
| PresentationConfig | Same pattern for presentation.yml. | Same narrow migration and diagnostics. |
| LobbyConfig | Same pattern via existing defaultResourceSupplier; path constructor supplies minimal ADVENTURE bytes. | Same supplier and diagnostics; ConfigFiles performs copy. |
| DailyQuestConfig | Production plugin resource; null-plugin path constructor creates nothing; early exists check; different missing-resource error. | Guards and its distinct error/log wording retained; inner copy uses ConfigFiles. |
| WarpConfig | Resource/test supplier, copy-if-absent; distinct diagnostics and persistence owner. | Audited and deliberately retained with the persistence owner; no migration in this phase. |
| BlackjackTableConfig | Resource/test supplier, copy-if-absent; distinct diagnostics and persistence owner. | Audited and deliberately retained with the persistence owner; no migration in this phase. |
| OnlineRewardConfig / StaffHierarchyConfig | None. | None. |

Two stateless final helpers in the existing config package suffice:

- ConfigFiles.copyDefault creates parents, returns false for an existing destination,
  opens/closes a supplied stream only when absent and copies exact bytes without
  replacement. The caller supplies its missing-resource exception; IOException
  propagates to the unchanged feature wrapper. It never serializes YAML.
- ConfigValues.readBoolean and readString retain Bukkit contains/get semantics,
  silently return caller defaults for missing keys and invoke the caller's warning
  callback once for wrong types. Strings preserve blanks/whitespace.
  nonBlankString takes a raw value, rejects null/blank/non-String without trimming,
  and leaves missing-key policy to its caller.

Chat explicitly skips nonBlankString on a missing format; PM intentionally passes
null through so missing format warnings survive. Core rank trimming is not
generalized. Neither helper touches staff-null retention, lists, enums, numbers,
section validation, required values, typed records, logging state or reload.
No new keys, defaults, dependency, watcher, manager, YAML library, migration,
atomic-write/backup policy, auto-reload or eager rewrite is introduced.

## Reload and semantic parity

Exactly six participants remain in the original order:
config.yml, lobby.yml, chat.yml, private-messages.yml, presentation.yml,
daily-quests.yml. VapeeCore wiring, reload package and all six module/service
plan owners are byte-identical. Prepare reads typed candidates without publication;
apply and rollback retain their original state/runtime responsibilities.

Baseline counterproof compiled the six original config parsers from the exact
baseline into an ignored, isolated class directory. Only Chat/PM/Presentation
received the same path/logger/resource constructor seams to permit execution;
their baseline readers and copy logic were retained. The six strengthened feature
config harnesses all passed against those baseline parsers as well as the new
implementations. This verifies behavioral parity rather than new expected defaults.

The new tests cover warning text/cardinality, missing and invalid values, scalar
YAML null behavior, blank/whitespace preservation, PM missing-format warnings,
Presentation list recovery/cap/interval, Lobby strict spawn and message validation,
core rank trimming and reload snapshots. Existing ConfigServiceHarness retains
all explicit StaffHierarchy null, null-parent and invalid-list security checks.
DailyQuest invalid-definition and real plan/registry rollback tests remain green.

Optional fallback load/prepare/apply/rollback tests assert administrator file bytes
or text remain unchanged. Parser/missing-resource errors remain fatal.
The independent helper harness covers absent creation, exact bytes, one open/close,
no existing-file replacement, parent creation, caller-owned missing-resource failure,
copy IOException/stream closure and directory failure.

## Lifecycle regression and negative controls

UtilityModuleLifecycleHarness runs the actual module with real UtilityService,
UtilityListener and InvseeService candidates and local external-hook callbacks.
It tests early/middle/late missing commands, command-construction failures,
executor failures, half-installed completer failures, first/second listener failures,
all three service construction boundaries, success-log failure, each command,
listener and service cleanup fault, combined ordered suppressed failures,
self-suppression, unpublished fields, empty lists, repeated disable and re-enable.
Actual ModuleManager integration verifies reverse predecessor rollback without
changing the manager or disabling never-successful modules.

Two isolated negative-control class builds restored the original defects:
removing local rollback, and tracking commands after both setters. Each caused
the new lifecycle harness to fail with AssertionError. These were ignored copies,
never replacements for the final production sources or artifact.

## Verification results

Final focused run: **44 harnesses / 12,029 checks / 0 failures**.
Final full run: **110 harnesses / 18,263 checks / 0 failures / 0 skipped**.
All 104 baseline executable harnesses were included. Discovery stripped block
comments and selected every *Harness.java with public static void main; each
ran as a separate Java process. Maven package was not used as a harness substitute.

The +661 check delta is +618 in six new harnesses (452 Utility lifecycle,
33 helpers, 30 Chat config, 26 PM config, 32 Presentation config, 45 Lobby config),
+19 ConfigService, +22 DailyQuestConfig and +2 descriptor source scans for the two
new production helpers. No existing expectation was removed or weakened.

| Required regression | Checks / outcome |
|---|---:|
| UtilityModuleLifecycleHarness | 452 |
| UtilityCommand / TeleportCommand / BuildCommand | 1,221 / 894 / 17 |
| UtilityService / UtilityInventory / TeleportParser | 35 / 742 / 58 |
| ConfigHelpers / ConfigService / DefaultConsistency | 33 / 93 / 12 |
| ChatConfig / Chat | 30 / 17 |
| PrivateMessageConfig / PrivateMessageSocial | 26 / 117 |
| PresentationConfig / Presentation | 32 / 10 |
| LobbyConfig / Lobby | 45 / 53 |
| DailyQuestConfig / DailyQuestLifecycle | 35 / 16 |
| CoreCommand / ModerationLifecycle | 75 / 149 |
| PermissionDescriptor | 837; 37 roots, 50 permissions, 15 positive child edges |
| Phase 30A.1 ModerationCommand | 4,344; freshness races unchanged and green |
| Phase 30B BlackjackReadView | 21; immutable read boundary green |
| Phase 30C UiItems / Pagination | 17 / 1,409 |
| Phase 30C Friend / Clan / Settings / Visibility menu security | 114 / 114 / 111 / 195; close fault tests green |
| Phase 30C Navigator / Quest menu security | 82 / 45 |

### Build and artifact

The exact required PowerShell command completed with **BUILD SUCCESS**, exit 0,
in 14.019 seconds at 2026-10-04 21:37:13 +02:00:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

- Artifact: `target/vapeecore-1.0-SNAPSHOT.jar`
- Bytes: **1,055,936**
- SHA-256: `ED1583F0E5FD043F60C70B9DC3AF13D0FD94F33FEF5DF38097D982DA7B300C3F`
- Deployed to `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar`.
- Deploy SHA-256: `ED1583F0E5FD043F60C70B9DC3AF13D0FD94F33FEF5DF38097D982DA7B300C3F`

### Paper smoke and live files

Final artifact tested on Paper **1.21.11 Build 132 / c5eb079**, Java **21.0.12.1**
and LuckPerms **5.5.84**. Startup began at 21:37:42, Done at 21:37:59; console
checks at 21:38:22, stop at 21:38:34, process exit 0 at 21:38:35 (Europe/Berlin).

- 27 module enables, Utility explicitly reports 12 commands.
- `/core`, `/core version`, `/core help` successful.
- `/core reload`: all six named participants prepared/applied; success in 42 ms.
- `bukkit:help VapeeCore`: all 37 roots exactly match the descriptor.
- Console-safe build, ping and invsee return controlled player/target/usage feedback.
- 27 module disables in exact reverse startup order; clean shutdown.
- **0 ERROR/SEVERE** entries in the whole smoke log, including VapeeCore.
- Zero online players; no staff mutation or deliberate startup fault on the server.
- Paper's newer-Minecraft-release notice and Windows profiler-engine fallback are
  existing environment messages, not VapeeCore failures.
- **Live client test: not performed.**

All eight existing VapeeCore YAML files retained identical pre/post SHA-256 values;
there were eight files before and after, zero new/missing/changed files:

| Live file | SHA-256 before = after |
|---|---|
| blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| config.yml | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` |
| daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| lobby.yml | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` |
| presentation.yml | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` |
| private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |

Ignored evidence is in `dev-server/.vapeecore-dev/phase30d-*`: baseline file hashes,
focused/full per-process logs and JSON results, isolated counterproof sources/classes,
build log, artifact JSON, smoke log/results and live YAML hashes. These operational
files are not part of the commit.

### Protected files and diff scope

All 454 unchanged baseline tracked files retain their original SHA-256.
The eleven changed existing files are listed below; nine files are added,
zero removed. All eight bundled resource YAMLs and plugin.yml are unchanged.
Core wiring, ModuleManager, reload/**, Utility command implementations, UtilityService,
InvseeService, staff hierarchy, GUI code, domain/persistence code, historical
reports, README and pom.xml are unchanged.

| Protected file | Baseline = final SHA-256 |
|---|---|
| docs/ARCHITECTURE_REFACTOR.md | `0D0DC4032A1C0CAA95855DC7DBB39BE1D794E5029C973B638F7D54B6683A404A` |
| docs/FULL_CODEBASE_AUDIT.md | `73F7FBF38E4DD01F5A3328275A8045C9FB3812D4B4BF6BA4D817458F1FFEA2CC` |
| docs/GUI_INVENTORY_ITEM_CLEANUP.md | `42E21FD11F804DDDB50E69DF9CEAEB78742DA3C21DD448148F67F53B6D9672A5` |
| docs/MODERATION_AUTHORIZATION_HOTFIX.md | `99735909CC7A5B05F5CBE1DA6D01E563061988EC98F6A16B6F8B3D84BA09982A` |
| pom.xml | `4410C0CAE35B81F3CF251D566B913947E71F0DA8EBFD7D9178900CF79D20BC5C` |
| README.md | `5E128286D7F6E74AF61A80C687FD20AD785F0136D13A79E3C8023384BB593F0C` |
| src/main/resources/plugin.yml | `1E4A9591559E669CD5753812885B0604A03CE070F48BD1DE39AD95B505D9DF5C` |

### File inventory and git boundary

Added (9):

- `src/main/java/dev/vapee/core/config/ConfigFiles.java`
- `src/main/java/dev/vapee/core/config/ConfigValues.java`
- `src/test/java/dev/vapee/core/config/ConfigHelpersHarness.java`
- `src/test/java/dev/vapee/core/chat/config/ChatConfigHarness.java`
- `src/test/java/dev/vapee/core/privatemessage/config/PrivateMessageConfigHarness.java`
- `src/test/java/dev/vapee/core/presentation/config/PresentationConfigHarness.java`
- `src/test/java/dev/vapee/core/lobby/config/LobbyConfigHarness.java`
- `src/test/java/dev/vapee/core/utility/UtilityModuleLifecycleHarness.java`
- `docs/CONFIG_REGISTRATION_MODULE_CLEANUP.md`

Changed (11):

- `src/main/java/dev/vapee/core/config/ConfigService.java`
- `src/main/java/dev/vapee/core/chat/config/ChatConfig.java`
- `src/main/java/dev/vapee/core/privatemessage/config/PrivateMessageConfig.java`
- `src/main/java/dev/vapee/core/presentation/config/PresentationConfig.java`
- `src/main/java/dev/vapee/core/lobby/config/LobbyConfig.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestConfig.java`
- `src/main/java/dev/vapee/core/utility/UtilityModule.java`
- `src/main/java/dev/vapee/core/utility/UtilityListener.java`
- `src/test/java/dev/vapee/core/config/ConfigServiceHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestConfigHarness.java`
- `docs/DEVELOPER_GUIDE.md`

The whole source/test/documentation diff and git diff --check were reviewed before
the single local commit named **Phase 30D - Config Registration and Module Cleanup**.
The final response records its exact SHA, the one-commit distance from the verified
baseline and clean working tree. No push. The tested change is ready for review/merge
within the stated scope; a live client session is not claimed.

### Complete independently executed harness inventory

All rows below passed with exit 0. No executable harness was skipped.

| Harness (under dev.vapee.core) | Checks |
|---|---:|
| activity.ActivityHarness | 153 |
| activity.blackjack.BlackjackFairnessHarness | 393 |
| activity.blackjack.BlackjackHarness | 317 |
| activity.blackjack.BlackjackPresentationHarness | 123 |
| activity.blackjack.BlackjackReadViewHarness | 21 |
| activity.blackjack.presentation.BlackjackPreviewHarness | 16 |
| activity.blackjack.table.BlackjackTableHarness | 90 |
| chat.ChatHarness | 17 |
| chat.config.ChatConfigHarness | 30 |
| clan.ClanDomainHarness | 52 |
| clan.ClanIntegrationHarness | 15 |
| clan.ClanPersistenceHarness | 39 |
| clan.ClanServiceHarness | 136 |
| clan.gui.ClanCommandHarness | 83 |
| clan.gui.ClanMenuHarness | 84 |
| clan.gui.ClanMenuSecurityHarness | 114 |
| command.CoreCommandHarness | 75 |
| command.OnlineStaffTargetGuardHarness | 268 |
| config.ConfigHelpersHarness | 33 |
| config.ConfigServiceHarness | 93 |
| config.DefaultConsistencyHarness | 12 |
| economy.command.CoinsCommandHarness | 1942 |
| economy.EconomyIntegrationHarness | 27 |
| economy.EconomyServiceHarness | 44 |
| friend.FriendCommandHarness | 80 |
| friend.FriendDomainHarness | 29 |
| friend.FriendIntegrationHarness | 28 |
| friend.FriendLifecycleHarness | 12 |
| friend.FriendPersistenceHarness | 28 |
| friend.FriendServiceHarness | 79 |
| friend.gui.FriendMenuHarness | 159 |
| friend.gui.FriendMenuSecurityHarness | 114 |
| identity.IdentityHarness | 26 |
| identity.ProfileHarness | 19 |
| lobby.command.LobbyCommandHarness | 26 |
| lobby.config.LobbyConfigHarness | 45 |
| lobby.experience.navigator.NavigatorMenuHarness | 113 |
| lobby.experience.navigator.NavigatorSecurityHarness | 82 |
| lobby.player.LobbyHarness | 53 |
| lobby.warp.command.WarpCommandHarness | 86 |
| lobby.warp.WarpHarness | 118 |
| message.CommandHelpHarness | 85 |
| moderation.command.ModerationCommandHarness | 4344 |
| moderation.command.ModerationDurationHarness | 53 |
| moderation.ModerationBanEnforcementHarness | 31 |
| moderation.ModerationDomainHarness | 112 |
| moderation.ModerationLifecycleHarness | 149 |
| moderation.ModerationMuteEnforcementHarness | 52 |
| moderation.ModerationMuteProjectionHarness | 20 |
| moderation.ModerationPersistenceHarness | 523 |
| moderation.ModerationServiceHarness | 141 |
| onlinereward.OnlineRewardLifecycleHarness | 42 |
| onlinereward.OnlineRewardServiceHarness | 42 |
| permission.LuckPermsAsyncHarness | 16 |
| permission.PermissionDescriptorHarness | 837 |
| player.repository.PlayerQuestPersistenceHarness | 21 |
| player.repository.PlayerRewardPersistenceHarness | 11 |
| player.repository.PlayerVisibilityPersistenceHarness | 17 |
| player.settings.PlayerSettingsServiceHarness | 28 |
| player.settings.PlayerVisibilitySettingsHarness | 11 |
| presence.FriendPresenceNotifierHarness | 8 |
| presence.PresenceLifecycleHarness | 14 |
| presence.PresenceServiceHarness | 15 |
| presentation.config.PresentationConfigHarness | 32 |
| presentation.PlaytimeFormatterHarness | 14 |
| presentation.PresentationHarness | 10 |
| privatemessage.config.PrivateMessageConfigHarness | 26 |
| privatemessage.PrivateMessageSocialHarness | 117 |
| quest.daily.command.QuestCommandHarness | 45 |
| quest.daily.DailyQuestConfigHarness | 35 |
| quest.daily.DailyQuestCycleSelectorHarness | 18 |
| quest.daily.DailyQuestLifecycleHarness | 16 |
| quest.daily.menu.QuestMenuHarness | 20 |
| quest.daily.menu.QuestMenuSecurityHarness | 45 |
| quest.DailyQuestServiceHarness | 20 |
| quest.QuestDefinitionHarness | 29 |
| quest.QuestLifecycleHarness | 42 |
| quest.QuestPlaytimeProducerHarness | 18 |
| quest.QuestProgressReporterHarness | 18 |
| quest.QuestServiceHarness | 44 |
| rank.RankCommandHarness | 16 |
| rank.RanksCommandHarness | 12 |
| rank.RankServiceHarness | 23 |
| rank.staff.StaffHierarchyServiceHarness | 109 |
| reward.RewardLifecycleHarness | 40 |
| reward.RewardServiceHarness | 126 |
| seat.SeatHarness | 52 |
| settings.command.SettingsCommandHarness | 25 |
| settings.SettingsMenuHarness | 66 |
| settings.SettingsMenuSecurityHarness | 111 |
| settings.visibility.SettingsModuleLifecycleHarness | 23 |
| settings.visibility.SettingsNavigationHarness | 17 |
| settings.visibility.VisibilityMenuSecurityHarness | 195 |
| settings.visibility.VisibilitySettingsMenuHarness | 29 |
| settings.visibility.VisiblePlayersMenuHarness | 74 |
| ui.PaginationHarness | 1409 |
| ui.UiItemsHarness | 17 |
| utility.command.BuildCommandHarness | 17 |
| utility.command.TeleportCommandHarness | 894 |
| utility.command.UtilityCommandHarness | 1221 |
| utility.TeleportParserHarness | 58 |
| utility.UtilityInventoryHarness | 742 |
| utility.UtilityModuleLifecycleHarness | 452 |
| utility.UtilityServiceHarness | 35 |
| visibility.FriendVisibilityRefreshHarness | 11 |
| visibility.IgnoreVisibilityRefreshHarness | 7 |
| visibility.VisibilityModuleHarness | 22 |
| visibility.VisibilityPolicyHarness | 28 |
| visibility.VisibilityServiceHarness | 10 |
| worlddisplay.WorldDisplayHarness | 27 |


## Remaining findings and phase boundary

LIFE-001 and CONFIG-001 are resolved by this phase. Exactly 14 historical findings
remain open:

SEC-001, DISPLAY-001, PERSIST-001, PERSIST-002, CMD-001, DUP-001,
PRES-001, PRES-002, LIFE-002, PERF-001, TEST-001, TEST-002, DOC-001,
LEGACY-001.

TEST-001 remains open: this phase preserves coordinator code and feature plan
coverage; it does not add the separate full coordinator-fault project. TEST-002
also remains open: harnesses are run explicitly in separate processes, without
changing Maven test infrastructure. The historical commented harness is retained.
No permissions, command syntax/completion/hierarchy, GUI, domain persistence,
Blackjack/Quest gameplay, presence, visibility or presentation runtime behavior
is changed. The guide received one targeted ownership paragraph.

Phase 30E (CMD-001/DUP-001/LEGACY-001) is the next scoped phase and has not started.
Phase 31 persistence, Phase 32 SEC-001, Phase 33 lifecycle/display/performance and
future presentation/team-teleport work remain deferred. No push or merge is performed.
