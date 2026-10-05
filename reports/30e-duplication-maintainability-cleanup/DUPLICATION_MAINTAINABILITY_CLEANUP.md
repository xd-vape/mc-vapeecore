# Phase 30E – Duplication and Maintainability Cleanup

## Baseline and scope

- Baseline: `fe524f61f673fc47732808a1bcaec96ec7bf2d3d` (`Merge Phase 30D - Config Registration and Module Cleanup`).
- Branch: `phase/30e-duplication-maintainability-cleanup`. The user explicitly confirmed this existing branch after clarification of the omitted `phase/` prefix in the request.
- Initial working tree: clean. All 474 tracked files were hashed before production edits.
- One worker, existing checkout; no subagents, worktree, push, merge or rebase.
- Scope: CMD-001, DUP-001, LEGACY-001 only. Phase 30D is protected.

## CMD-001 inventory before extraction

Permissions below are relative to `vapeecore.utility.`. P = Player, C = console.
Every explicit target uses the existing case-insensitive **exact online** lookup;
no known/offline lookup. Same UUID is self even when supplied explicitly.

| Command | Arity / P and C / self and other | Base / others permission | Errors and order / usage | Completion | Feature gates after authority |
|---|---|---|---|---|---|
| Feed | 0–1; P defaults to self; C requires explicit target | feed / feed.others | Arity first; missing console target; exact lookup before self/other capability; verb-specific denial; `/feed [player]` | Argument 1, only with others; guarded online supplier | Staff guard, activity, feed |
| Heal | 0–1; P defaults to self; C requires explicit target | heal / heal.others | Same structure, heal-specific denial; `/heal [player]` | Argument 1, only with others; guarded online supplier | Staff guard, activity, health-cap-aware heal |
| Fly | 0–1; P defaults to self; C requires explicit target | fly / fly.others | Same structure, flight-specific denial; `/fly [player]` | Argument 1, only with others; guarded online supplier | Staff guard, activity, BUILD ownership, native creative/spectator flight, managed toggle |
| Speed | 1–2; P defaults to self after level; C requires target | speed / speed.others | Arity then level 1–10 then target/capability; `/speed <1-10> [player]` | Argument 1 levels 1…10 in numeric encounter order; argument 2 guarded online supplier with others | Staff guard, activity; actual walk/fly channel remains in service |
| GameMode | 1–2; P defaults to self after mode; C requires target | gamemode / gamemode.others | Arity then mode/aliases then target/capability; `/gamemode <mode> [player]` | Argument 1 survival, creative, adventure, spectator in this order; argument 2 guarded online supplier with others | Staff guard, activity, BUILD; managed flight cleanup and lobby protection notice |

The five name paths first call `OnlineStaffTargetGuard.suggestiblePlayers`, then
filter non-null/online Players and map their names. All five normalize prefix and
names with Locale.ROOT, use startsWith, sort with String.CASE_INSENSITIVE_ORDER,
retain duplicates and encounter order among case-equal names, and return toList.
Empty prefix includes every locally eligible name. Missing others permission or
wrong completion arity produces an empty list. Self remains eligible for suggestions.

The extracted package-private `NameSuggestions.matching` owns only the String
stream's prefix filter and stable sort. Bukkit candidate selection stays in each
command. OnlinePlayerResolver remains unchanged: its separate suggestion method
also deduplicates and can exclude a UUID, so it is not substituted. Teleport and
its grammar are untouched. Mode/level order is not moved into a sorted helper.

Target plumbing remains local: Feed/Heal feedback and usages are command-specific;
Fly/Speed/GameMode add distinct parser and state decisions. No command superclass,
router, sender/authority abstraction or domain policy is introduced.

## DUP-001 inventory before extraction

| Caller / candidate owner | Existing name source and roundtrip | Existing lexical check | Migrated output paths / boundaries |
|---|---|---|---|
| FriendMessages.commandArgument | findById; identities.resolve; FOUND UUID must match | null or failure of `\\S+` uses UUID | FriendCommand add uses online nonself excluding friends/outgoing; accept/deny incoming only; cancel outgoing only; remove own friends only. Choice buttons and actor notification buttons share this formatter. FriendModule's existing callback also uses it. |
| ClanCommand.identityCommandArgument | displayName (known name or UUID); identities.resolve; FOUND UUID must match | No separate lexical check | Outgoing cancel buttons/completion, other-member kick/transfer completion only. Invite online candidates and incoming clan tag/UUID suggestions keep their existing independent paths. Owner/member gates stay local. |
| SettingsCommand.identityCommandArgument | findById; identities.resolve; FOUND UUID must match | No separate lexical check | Visibility remove uses only the viewer's saved added-visible IDs, including unknown UUIDs. Add keeps online/nonself/not-already-added names. |
| ModerationCommandContext.safeArgument | findById; identities.resolve; FOUND UUID must match | Unicode Character.isWhitespace or ISO control uses UUID | Normal commands use online IDs; unban/unmute use active committed matching sanctions. Self exclusion except history, loaded hierarchy checks for online targets, offline relation candidates without LP loads, and local tie-break sort remain. |
| CoinsCommand.safeArgument | Live Player name; indexed findKnownIdsByName must contain exactly its UUID | No separate lexical check | All actions suggest online only. Get has no hierarchy filter; add/remove/set use canSuggest. Base/admin gates local; no offline snapshot load for completion. |

The small pure `IdentityCommandArgument.nameOrUuid` receives the expected UUID,
proposed name and a caller-supplied unique-ID resolver. It cannot enumerate or
authorize identities. Missing/blank/whitespace/control names, missing or ambiguous
resolution, and UUID mismatch produce the UUID. Valid names are returned unchanged,
without trimming, escaping or quoting. Resolver failures keep propagating to the
existing feature boundary; they are not silently hidden.

PlayerLookupResult guarantees only FOUND contains an identity, so the four identity
adapters map its optional identity to UUID. Coins retains its indexed name-only
resolver and never switches to the snapshot-reading PlayerIdentityService. Its
adapter first uses the existing canonical parseUuid grammar: a UUID-looking name
must roundtrip through that same branch as the executed command, not merely match
the name index. This also requires no snapshot lookup.

The requested single-argument safety rule is explicit: Clan/Settings/Coins now use
UUID spelling for whitespace-containing names even if the name index roundtrips;
Friends also rejects Unicode whitespace/control characters as Moderation already
did. This is formatting of the **same eligible UUID**, not a new target universe or
an authorization change. Ordinary valid names, ambiguity fallback, prefix and sort
rules remain unchanged. No existing test expectation is weakened to accept a change.

Unignore remains byte-identical and relation-only. No global identity scan, offline
LuckPerms load, staff/security/GUI capability change, social abstraction or repository
framework is introduced. SEC-001 is not addressed.

## LEGACY-001 pre-removal evidence

The baseline historical comment occupies lines 47–733 inclusive (687 lines).
The active class begins at line 735; active main begins at line 742. The baseline
BlackjackHarness ran in its own Java process and passed **317 checks**, exit 0,
before removal. Only the excluded comment is removed; active source and imports
are preserved. Final equivalence and regression results are recorded below.

The remaining bytes equal the original bytes with exactly that comment substring
removed (including neither neighboring newline). Both SHA-256 values are
`9E36E1DD229F372008536952A1F4BFFD472D0CD264B9FDBF521D737F21B62DB5`.
Git therefore reports 686 deleted physical lines: the closing line's newline is
retained as a blank line. All imports still have references in the active body.
The original 18 main-invoked scenarios remain: hand values/naturals, outcomes/dealer
rule, six-deck shoe, BUILD join guard, physical capacity/reopen, join rollback,
single-player reset/rematch, bust/natural dealer skips, multiplayer deal/actions,
double down, timeout/stale task safety, mid-round join rejection, leave/disconnect/
world-change cleanup, Quest outcomes, callback failure isolation, unloaded Quest
outcomes, and immutable session reads. The direct five-action enum assertion also
remains. Baseline, focused and full Blackjack runs all pass **317 checks**.

No production compatibility cleanup occurred. PlaytimeFormatter wrapper, group
alias, lobby.build child permission, reserved invsee.modify, legacy Blackjack
schema/seat cleanup with foreign entity preservation, WarpPoint constructor,
inert future Quest keys and unavailable participant visibility remain unchanged.

## Final production regression

- Focused: **64 harnesses, 15,079 checks, 0 failures, 0 skipped**.
- Full: **112 harnesses, 18,524 checks, 0 failures, 0 skipped**.
- Every previous one of the 110 executable harnesses was discovered and ran in a
  separate Java process. Zero missing baseline harnesses and zero reduced counts.
- Discovery strips block comments before checking for public static void main;
  Maven package is not used as a substitute for these process runs.
- Total increase over Phase 30D: **261 checks**: Utility +81, Friend +16, Clan +16,
  Settings +11, Moderation +70, Coins +9, new identity helper +47, new name helper
  +8, descriptor source scan +3 (two for the utility helper, one for identity).

| Required / new harness | Checks |
|---|---:|
| UtilityCommand | 1,302 |
| TeleportCommand / BuildCommand | 894 / 17 |
| FriendCommand / ClanCommand / SettingsCommand | 96 / 99 / 36 |
| ModerationCommand including all prior freshness races | 4,414 |
| CoinsCommand | 1,951 |
| BlackjackHarness | 317 |
| IdentityCommandArgument / NameSuggestions | 47 / 8 |
| PermissionDescriptor | 840 |

The focused set includes adjacent friend/clan/settings/moderation/economy domain,
persistence, integration, lifecycle and GUI security coverage; identity, visibility,
staff guard, PM/social and Blackjack paths also ran. Full regression covers prior
30A.1 freshness, 30B immutable reads, 30C UiItems/Pagination and owned-view cleanup,
30D partial-enable rollback, ConfigFiles/ConfigValues and config/reload semantics.
No earlier assertion or expected result was changed or removed.

Descriptor assertions confirm **37 roots, 50 permissions, 15 positive child edges**,
27 modules and the same six reload participants. The descriptor is byte-identical.

## Isolated controls, separate from production test totals

All controls were compiled into a separate ignored class directory, placed ahead
of the normal classes only for that process. They never overwrote production
classes, test classes, source files or the final JAR.

| Isolated variant | Tests that rejected it | Result |
|---|---|---|
| Accept a name without validating its roundtrip | Identity helper; Friend, Clan, Settings, Moderation, Coins command harnesses | Six expected AssertionError exits (1); real callers catch ambiguous names replacing UUIDs |
| Remove whitespace/control rejection | Same six harnesses | Six expected AssertionError exits (1); unsafe spelling detected |
| Remove prefix filtering | Name helper and UtilityCommand harnesses | Two expected AssertionError exits (1) |
| Reverse case-insensitive sort | Name helper and UtilityCommand harnesses | Two expected AssertionError exits (1) |

**16/16 expected failures.** Log files retain exact failure messages. Separately,
the five original utility command sources from the exact baseline were compiled
without changes into an isolated directory; the strengthened UtilityCommandHarness
passed **1,302 checks** against them. This demonstrates completion/grammar parity,
not merely agreement with the new helper. It is not included in final test totals.

The isolated directory was resolved and checked against its exact intended path,
then deleted in finally. It no longer exists. Only logs, result JSON and the
ignored reproduction script remain, outside the removed source/class copies.

## Build and final artifact

After the full regression, the required offline command completed with
**BUILD SUCCESS / exit 0**, 21.939 seconds, 2026-10-05 15:46:56 +02:00:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

- Artifact: `target/vapeecore-1.0-SNAPSHOT.jar`
- Size: **1,056,982 bytes**
- SHA-256: `57588C1C6A9A01CA225729D69B20691C8D9F32AA965F980E490F8536516E7AB8`
- Deployed dev-server plugin JAR has exactly the same SHA-256.
- POM and test infrastructure remain unchanged.

## Paper smoke test

Environment: **Paper 1.21.11 Build 132 / c5eb079**, **Oracle Java
21.0.12.1+1-LTS-4**, **LuckPerms 5.5.84**, Windows 11. Startup 15:48:05,
Done 15:48:22, console checks 15:48:48, clean shutdown 15:49:02, process exit 0
(2026-10-05, Europe/Berlin).

- All 27 modules enabled; Utility reports its existing twelve commands.
- `/core`, `/core version`, `/core help` succeeded.
- `/core reload` prepared config.yml, lobby.yml, chat.yml, private-messages.yml,
  presentation.yml and daily-quests.yml, then applied successfully in 17 ms.
- `bukkit:help VapeeCore` listed exactly 37 roots matching the descriptor.
- All 27 modules disabled in the exact reverse startup order.
- **0 ERROR/SEVERE entries** in the entire smoke log, including VapeeCore.
- Eight existing live YAML files before and after; zero changes/additions/removals.
- Zero online players. No gameplay or player-facing command execution is claimed
  as live tested. **Live client test: not performed.**
- Paper's newer-Minecraft-release notice and Windows profiler fallback are
  environment messages; no server/plugin upgrade is part of this change.

## Protected files and scope review

SHA-256 comparison against the initial snapshot confirms **457 of 474 existing
tracked files byte-identical**. Only the 17 intended existing source/test files
changed; five files were added, zero files removed. The table below records all
explicitly protected documents/resources, plus the unchanged developer guide.

VapeeCore wiring, ModuleManager, every module lifecycle, reload code, all config
parsers/helpers, Teleport/OnlinePlayerResolver, StaffHierarchy/OnlineStaffTargetGuard,
domain services, repositories, UI/GUI and all compatibility production surfaces
are among those 457 unchanged files. The full baseline/final hashes are retained
in ignored evidence. Historical reports were not rewritten.

All production diffs were checked: ten callers change only completion/argument
formatting and relevant imports. Existing tests only gain scenarios; Blackjack
only loses the excluded comment. The entire source/test diff, including historical
deletion and all newly added files, was reviewed. git diff --check passes.

## Commit and remaining findings

The requested commit is the single local commit containing this report, with
subject **Phase 30E - Duplication and Maintainability Cleanup**, directly after
baseline `fe524f61f673fc47732808a1bcaec96ec7bf2d3d`. Its exact SHA, verified
one-commit distance and post-commit clean working tree are recorded in the final
response and ignored post-commit receipt, avoiding a self-referential commit hash
inside its own contents. No push, merge, rebase or later phase.

CMD-001, DUP-001 and LEGACY-001 are resolved within the documented boundaries.
Exactly **11 historical findings** remain open: SEC-001, DISPLAY-001, PERSIST-001,
PERSIST-002, PRES-001, PRES-002, LIFE-002, PERF-001, TEST-001, TEST-002, DOC-001.
None is addressed in this phase. Phase 30F and Phases 31–34 have not started.

Ignored evidence is under `dev-server/.vapeecore-dev/phase30e-*`: initial and
protected hashes, Blackjack baseline/active-source proof, focused/full per-process
logs and JSON, baseline utility parity and negative-control logs, offline build
log, artifact hash, Paper log/results and before/after live YAML hashes.

## Live YAML hashes

| File | SHA-256 before = after |
|---|---|
| blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| config.yml | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` |
| daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| lobby.yml | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` |
| presentation.yml | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` |
| private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |

## Protected file hashes

| File | Baseline = final SHA-256 |
|---|---|
| docs/ARCHITECTURE_REFACTOR.md | `0D0DC4032A1C0CAA95855DC7DBB39BE1D794E5029C973B638F7D54B6683A404A` |
| docs/CONFIG_REGISTRATION_MODULE_CLEANUP.md | `351F9AE8CA6E3DE6C5964A0A408583C18F4637947C402E4AD16B92443C9F1B24` |
| docs/DEVELOPER_GUIDE.md | `596581EF40B591C8BD922F205C042ED1F466E35DEF35F38056B40F3BE5FF5360` |
| docs/FULL_CODEBASE_AUDIT.md | `73F7FBF38E4DD01F5A3328275A8045C9FB3812D4B4BF6BA4D817458F1FFEA2CC` |
| docs/GUI_INVENTORY_ITEM_CLEANUP.md | `42E21FD11F804DDDB50E69DF9CEAEB78742DA3C21DD448148F67F53B6D9672A5` |
| docs/MODERATION_AUTHORIZATION_HOTFIX.md | `99735909CC7A5B05F5CBE1DA6D01E563061988EC98F6A16B6F8B3D84BA09982A` |
| pom.xml | `4410C0CAE35B81F3CF251D566B913947E71F0DA8EBFD7D9178900CF79D20BC5C` |
| README.md | `5E128286D7F6E74AF61A80C687FD20AD785F0136D13A79E3C8023384BB593F0C` |
| src/main/resources/blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| src/main/resources/chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| src/main/resources/config.yml | `37ECEFAC7B0EFCFB006718D4A350066138733809DD53C4B2FD2DA5E585566DC4` |
| src/main/resources/daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| src/main/resources/lobby.yml | `AC6B3FD2FD2840E41564F521E83571CD22CE429DC41EDF33CE47B33812B82F6C` |
| src/main/resources/plugin.yml | `1E4A9591559E669CD5753812885B0604A03CE070F48BD1DE39AD95B505D9DF5C` |
| src/main/resources/presentation.yml | `9EB8A3362622549C1228DBDDF8ED0D17ABB19A3AA7DFD13E5C4D7F5F1284BE2C` |
| src/main/resources/private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| src/main/resources/warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |

## Complete file inventory

| Status | Path |
|---|---|
| Added | `docs/DUPLICATION_MAINTAINABILITY_CLEANUP.md` |
| Added | `src/main/java/dev/vapee/core/identity/IdentityCommandArgument.java` |
| Added | `src/main/java/dev/vapee/core/utility/command/NameSuggestions.java` |
| Added | `src/test/java/dev/vapee/core/identity/IdentityCommandArgumentHarness.java` |
| Added | `src/test/java/dev/vapee/core/utility/command/NameSuggestionsHarness.java` |
| Modified | `src/main/java/dev/vapee/core/clan/command/ClanCommand.java` |
| Modified | `src/main/java/dev/vapee/core/economy/command/CoinsCommand.java` |
| Modified | `src/main/java/dev/vapee/core/friend/FriendMessages.java` |
| Modified | `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java` |
| Modified | `src/main/java/dev/vapee/core/settings/command/SettingsCommand.java` |
| Modified | `src/main/java/dev/vapee/core/utility/command/FeedCommand.java` |
| Modified | `src/main/java/dev/vapee/core/utility/command/FlyCommand.java` |
| Modified | `src/main/java/dev/vapee/core/utility/command/GameModeCommand.java` |
| Modified | `src/main/java/dev/vapee/core/utility/command/HealCommand.java` |
| Modified | `src/main/java/dev/vapee/core/utility/command/SpeedCommand.java` |
| Modified | `src/test/java/dev/vapee/core/activity/blackjack/BlackjackHarness.java` |
| Modified | `src/test/java/dev/vapee/core/clan/gui/ClanCommandHarness.java` |
| Modified | `src/test/java/dev/vapee/core/economy/command/CoinsCommandHarness.java` |
| Modified | `src/test/java/dev/vapee/core/friend/FriendCommandHarness.java` |
| Modified | `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java` |
| Modified | `src/test/java/dev/vapee/core/settings/command/SettingsCommandHarness.java` |
| Modified | `src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java` |

Removed files: none. The historical comment removal is included in the modified BlackjackHarness.

## Complete independently executed harness inventory

All rows passed in a separate process with exit 0. F marks membership in the 64-harness focused run.

| Harness (under dev.vapee.core) | Checks | Focused |
|---|---:|---|
| activity.ActivityHarness | 153 |  |
| activity.blackjack.BlackjackFairnessHarness | 393 | F |
| activity.blackjack.BlackjackHarness | 317 | F |
| activity.blackjack.BlackjackPresentationHarness | 123 | F |
| activity.blackjack.BlackjackReadViewHarness | 21 | F |
| activity.blackjack.presentation.BlackjackPreviewHarness | 16 | F |
| activity.blackjack.table.BlackjackTableHarness | 90 | F |
| chat.ChatHarness | 17 |  |
| chat.config.ChatConfigHarness | 30 |  |
| clan.ClanDomainHarness | 52 | F |
| clan.ClanIntegrationHarness | 15 | F |
| clan.ClanPersistenceHarness | 39 | F |
| clan.ClanServiceHarness | 136 | F |
| clan.gui.ClanCommandHarness | 99 | F |
| clan.gui.ClanMenuHarness | 84 | F |
| clan.gui.ClanMenuSecurityHarness | 114 | F |
| command.CoreCommandHarness | 75 |  |
| command.OnlineStaffTargetGuardHarness | 268 | F |
| config.ConfigHelpersHarness | 33 |  |
| config.ConfigServiceHarness | 93 |  |
| config.DefaultConsistencyHarness | 12 |  |
| economy.command.CoinsCommandHarness | 1951 | F |
| economy.EconomyIntegrationHarness | 27 | F |
| economy.EconomyServiceHarness | 44 | F |
| friend.FriendCommandHarness | 96 | F |
| friend.FriendDomainHarness | 29 | F |
| friend.FriendIntegrationHarness | 28 | F |
| friend.FriendLifecycleHarness | 12 | F |
| friend.FriendPersistenceHarness | 28 | F |
| friend.FriendServiceHarness | 79 | F |
| friend.gui.FriendMenuHarness | 159 | F |
| friend.gui.FriendMenuSecurityHarness | 114 | F |
| identity.IdentityCommandArgumentHarness | 47 | F |
| identity.IdentityHarness | 26 | F |
| identity.ProfileHarness | 19 | F |
| lobby.command.LobbyCommandHarness | 26 |  |
| lobby.config.LobbyConfigHarness | 45 |  |
| lobby.experience.navigator.NavigatorMenuHarness | 113 |  |
| lobby.experience.navigator.NavigatorSecurityHarness | 82 |  |
| lobby.player.LobbyHarness | 53 |  |
| lobby.warp.command.WarpCommandHarness | 86 |  |
| lobby.warp.WarpHarness | 118 |  |
| message.CommandHelpHarness | 85 |  |
| moderation.command.ModerationCommandHarness | 4414 | F |
| moderation.command.ModerationDurationHarness | 53 | F |
| moderation.ModerationBanEnforcementHarness | 31 | F |
| moderation.ModerationDomainHarness | 112 | F |
| moderation.ModerationLifecycleHarness | 149 | F |
| moderation.ModerationMuteEnforcementHarness | 52 | F |
| moderation.ModerationMuteProjectionHarness | 20 | F |
| moderation.ModerationPersistenceHarness | 523 | F |
| moderation.ModerationServiceHarness | 141 | F |
| onlinereward.OnlineRewardLifecycleHarness | 42 |  |
| onlinereward.OnlineRewardServiceHarness | 42 |  |
| permission.LuckPermsAsyncHarness | 16 |  |
| permission.PermissionDescriptorHarness | 840 | F |
| player.repository.PlayerQuestPersistenceHarness | 21 |  |
| player.repository.PlayerRewardPersistenceHarness | 11 |  |
| player.repository.PlayerVisibilityPersistenceHarness | 17 | F |
| player.settings.PlayerSettingsServiceHarness | 28 | F |
| player.settings.PlayerVisibilitySettingsHarness | 11 | F |
| presence.FriendPresenceNotifierHarness | 8 | F |
| presence.PresenceLifecycleHarness | 14 |  |
| presence.PresenceServiceHarness | 15 |  |
| presentation.config.PresentationConfigHarness | 32 |  |
| presentation.PlaytimeFormatterHarness | 14 |  |
| presentation.PresentationHarness | 10 |  |
| privatemessage.config.PrivateMessageConfigHarness | 26 |  |
| privatemessage.PrivateMessageSocialHarness | 117 | F |
| quest.daily.command.QuestCommandHarness | 45 |  |
| quest.daily.DailyQuestConfigHarness | 35 |  |
| quest.daily.DailyQuestCycleSelectorHarness | 18 |  |
| quest.daily.DailyQuestLifecycleHarness | 16 |  |
| quest.daily.menu.QuestMenuHarness | 20 |  |
| quest.daily.menu.QuestMenuSecurityHarness | 45 |  |
| quest.DailyQuestServiceHarness | 20 |  |
| quest.QuestDefinitionHarness | 29 |  |
| quest.QuestLifecycleHarness | 42 |  |
| quest.QuestPlaytimeProducerHarness | 18 |  |
| quest.QuestProgressReporterHarness | 18 |  |
| quest.QuestServiceHarness | 44 |  |
| rank.RankCommandHarness | 16 |  |
| rank.RanksCommandHarness | 12 |  |
| rank.RankServiceHarness | 23 |  |
| rank.staff.StaffHierarchyServiceHarness | 109 |  |
| reward.RewardLifecycleHarness | 40 |  |
| reward.RewardServiceHarness | 126 |  |
| seat.SeatHarness | 52 |  |
| settings.command.SettingsCommandHarness | 36 | F |
| settings.SettingsMenuHarness | 66 | F |
| settings.SettingsMenuSecurityHarness | 111 | F |
| settings.visibility.SettingsModuleLifecycleHarness | 23 | F |
| settings.visibility.SettingsNavigationHarness | 17 | F |
| settings.visibility.VisibilityMenuSecurityHarness | 195 | F |
| settings.visibility.VisibilitySettingsMenuHarness | 29 | F |
| settings.visibility.VisiblePlayersMenuHarness | 74 | F |
| ui.PaginationHarness | 1409 |  |
| ui.UiItemsHarness | 17 |  |
| utility.command.BuildCommandHarness | 17 | F |
| utility.command.NameSuggestionsHarness | 8 | F |
| utility.command.TeleportCommandHarness | 894 | F |
| utility.command.UtilityCommandHarness | 1302 | F |
| utility.TeleportParserHarness | 58 | F |
| utility.UtilityInventoryHarness | 742 | F |
| utility.UtilityModuleLifecycleHarness | 452 | F |
| utility.UtilityServiceHarness | 35 | F |
| visibility.FriendVisibilityRefreshHarness | 11 | F |
| visibility.IgnoreVisibilityRefreshHarness | 7 | F |
| visibility.VisibilityModuleHarness | 22 | F |
| visibility.VisibilityPolicyHarness | 28 | F |
| visibility.VisibilityServiceHarness | 10 | F |
| worlddisplay.WorldDisplayHarness | 27 |  |
