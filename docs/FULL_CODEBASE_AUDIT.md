# Phase 30A – Full Codebase Audit

## 1. Audit metadata, baseline and scope

Audit date: 2026-10-04, Europe/Berlin. Repository: `xd-vape/mc-vapeecore`.
One worker; no delegation. This is a documentation-only audit, not an implementation phase.

| Item | Verified contract |
|---|---|
| Starting commit | `a1e36bb6cb4ce188d00b41c58be18feb97631a10` |
| Branch | `phase/30a-full-codebase-audit` |
| Starting tree | Clean; exact requested baseline |
| Baseline build | BUILD SUCCESS, 28.419 seconds; clean package and harness classpath generation |
| Production sources | 311 Java files, 34,108 physical lines |
| Test sources | 111 Java files, 25,158 physical lines; 101 executable harnesses and 10 support files |
| Resources | Nine: plugin descriptor and eight configuration defaults |
| Existing documentation | README plus 12 files under docs |
| Development scripts | Three PowerShell scripts; two IntelliJ run configurations |
| Intended tracked change | Only `docs/FULL_CODEBASE_AUDIT.md` |
| Commit contract | Exactly one local commit, `Phase 30A - Full Codebase Audit`; no push |

All counts refer to this baseline. A command root means a key under `plugin.yml.commands`;
aliases are not additional roots. A persistence store means a logical plugin-owned file or
file family; `players/<uuid>.yml` is one family, not a fixed number of files. A task owner
means a production class directly scheduling work, not every class consuming a task handle.
Info observations count in the finding total but are not confirmed defects.

No production Java, test Java, POM, resource default, operator configuration or historic
report is changed. Audit probes and verification logs are ignored local artifacts.
No Phase 30B work, migrations, feature implementation or security fixes are included.

## 2. Methodology and evidence limits

The review covered bootstrap and constructor dependencies, every production feature/package,
all command executors, listeners, inventories, configuration parsers, repositories, records,
interfaces and lifecycle owners. The test review covered every test/support file's scenarios,
assertions and seams, with deeper body inspection for security, persistence, asynchronous
handoff, inventory binding and lifecycle cases. Source-contract assertions are distinguished
from execution of real services and from a real Paper/client session.

Independent descriptor counting was compared with executors and the frozen descriptor harness.
Scheduler, asynchronous API, mutable/static state, YAML, command resolution, inventory,
registration and cleanup searches were followed to their actual call sites. Source line count
alone is not a finding. No speculative O(n²), leak, dead-code or off-thread Bukkit allegation
is used as evidence. Large but coherent classes are described below without mandatory splits.

Four focused, untracked probes ran against the baseline compiled production classes and
existing test fixtures. They confirmed MOD-001, SEC-001, DISPLAY-001 and PERSIST-002.
PERSIST-001 was also checked against the bytecode of the locally used Paper API's
`FileConfiguration.save(File)`, which opens a `FileOutputStream` on the destination.
These are audit experiments, not newly committed regression tests and not additional members
of the 101-harness count. Each finding states whether its effect is reproduced, conditional
on an injected failure, or a future design observation.

The initial baseline build succeeded, so the requested stop-on-broken-baseline condition did
not apply. Final verification is recorded in section 33. Live client test: not performed.

## 3. Repository architecture overview

This is a modular monolith with feature packages and explicit constructor wiring in
`src/main/java/dev/vapee/core/VapeeCore.java#onEnable`. `CoreModule` defines enable/disable;
`ModuleManager` records successful enables and disables them in reverse order, isolating each
module's shutdown exception. There is no reflection discovery, global service locator,
production singleton service registry or static mutable player-state map.

`MessageService` and `CommandHelpRenderer` are bootstrap services, not extra modules.
Configuration snapshots and domain projections are explicit, rather than hidden global caches.
LuckPerms owns permissions/groups; Paper and LuckPerms APIs are `provided` Maven dependencies.
The descriptor requires LuckPerms. No production dependency is shaded into a second copy of
the provider. Java source/target is 21 and Paper API is 1.21.11.

The useful dependency spine is gameplay producer → Quest → Reward → Economy → Player.
Moderation remains separate from Player/Economy domain mutation; application adapters obtain
identity and authorization. Presentation consumes domain reads. Friend/Social publish
relationship notifications without depending on Visibility. LobbyExperience consumes Warp,
Settings, Activity and Visibility; Warp is not coupled back to the Compass UI.

Readability is generally good: an ordinary new quest producer needs the reporter seam, not
wallet persistence or Blackjack internals. A new menu currently requires repeating item,
pagination and session plumbing (GUI-001). A new utility repeats some target/completion
plumbing (CMD-001). A new module must get partial registration rollback right (LIFE-001).
These are focused maintenance costs, not evidence that the modular monolith should be replaced.

`FilePlayerRepository` combines one aggregate schema, validation and serialization, while
`BlackjackService` coordinates a bounded round state machine. Their size is understandable.
`ClanCommand`, `BlackjackCommand` and `TeleportCommand` are longer because their grammars and
rules differ. A split is justified only by an independently owned responsibility; no
class-length threshold, one-class-per-branch refactor or generic command framework is proposed.

## 4. Module/dependency and lifecycle overview

Order below is the actual registration/enable order. Each `XModule` has its exact source in
the source inventory appendix. Config/Message/Help mean bootstrap services; Plugin is omitted
from the dependency column. Disable uses the reverse order, 27 through 1.

| # | Module | Constructor dependencies | Main ownership | Reload |
|---:|---|---|---|---|
| 1 | Permission | — | LuckPerms provider adapter | No |
| 2 | Rank | Config, Permission, Message | Rank reads, staff hierarchy, rank/ranks commands | No |
| 3 | Player | Config, Message | Player repository/cache, settings service, join/quit | No |
| 4 | Social | Player, Message | Ignore state/projection and three commands | No |
| 5 | Economy | Player, Rank, Message, Help | Wallet operations, coins, staff target adapter | No |
| 6 | Identity | Player, Rank, Economy, Message | Known identities/profiles, profile command | No |
| 7 | Moderation | Identity, Rank, Message | Snapshot, repository, seven commands, login/mute listeners | No |
| 8 | Friend | Config, Player, Social, Identity, Message, Help | Friendship/request domain, file, command and GUI | No |
| 9 | Presence | Player, Social, Friend, Message | Online transitions and opt-in friend notices | No |
| 10 | Clan | Config, Identity, Message, Help | Clan domain/file, command and GUI | No |
| 11 | Reward | Player, Economy | Deferred grants and shared dirty-player flush | No |
| 12 | OnlineReward | Config, Player, Reward, Message | Cumulative statistic reward processing | No |
| 13 | Quest | Player, Reward, Message | Registry, progress, reward retry, reporter, playtime producer/flush | No |
| 14 | DailyQuest | Player, Quest, Message | Definitions, cycles, selection, assignments, quests command/menu | Yes |
| 15 | Lobby | Player, Message | Spawn, protection and NORMAL/BUILD inventory state | Yes |
| 16 | Visibility | Player, Social, Friend, Lobby | Visibility policy and plugin-owned hide pairs | No |
| 17 | Chat | Permission, Rank, Social, Message | Chat configuration/renderer and ignore filtering | Yes |
| 18 | PrivateMessage | Player, Social, Message, Moderation | PM configuration, reply partners and mute predicate | Yes |
| 19 | Presentation | Config, Message, Permission, Rank, Player, Economy, Lobby | Renderer, scoreboard, tablist and update task | Yes |
| 20 | Settings | Player, Presentation, Identity, Visibility, Lobby, Message | Settings command and three inventory menus | No |
| 21 | Activity | Player | Generic finite state machine, venue/member/task ownership | No |
| 22 | Utility | Lobby, Activity, Rank, Message | Twelve commands, runtime flight/speed and Invsee | No |
| 23 | Seat | Lobby, Activity | Generic seat service plus application-level casual-seat policy | No |
| 24 | WorldDisplay | — | Native TextDisplay/ItemDisplay ownership | No |
| 25 | Blackjack | Activity, Seat, WorldDisplay, Lobby, Message, Help, Quest | Physical table domain, setup, round and presentation adapters | No |
| 26 | Warp | Message, Help | Warp configuration/domain and administration | No |
| 27 | LobbyExperience | Lobby, Player, Settings, Warp, Visibility, Activity, Message | Compass/hotbar, Navigator and lobby messages | No |

All constructor module dependencies point to previously registered modules. No module cycle
was found. Domain service boundaries are narrower than module constructor lists: SeatService
does not know Lobby/Blackjack; PrivateMessageService receives a UUID mute predicate rather than
ModerationService; Blackjack reports an outcome through a callback rather than importing Quest.
API-001 is a local concrete-session read boundary, not a reversed module dependency.

The six reload participants, in apply order, are **ConfigService, LobbyModule, ChatModule,
PrivateMessageModule, PresentationModule, DailyQuestModule**. Prepare constructs candidates
without publishing; all prepare steps finish before apply. On failure, the failed apply's plan
is rolled back first, then successful applies in reverse order. Each rollback is attempted
even if another fails; running resets in `finally`. `ROLLBACK_INCOMPLETE` is explicit.
DailyQuest restores configuration and registry, not a stale copy of players. Already closed
menus need not reopen after rollback. Moderation, Warp and Blackjack are intentionally not
reload participants; external edits of their files are not silently reread by `/core reload`.
The coordinator's untested failure paths are TEST-001, not a claimed broken rollback algorithm.

## 5. Complete command matrix

Every one of the **37 descriptor roots** is below, including all aliases. Permissions are
relative to `vapeecore.`; the actual executor checks were reviewed in addition to descriptor
gates. `P` = Player; `C` = Console; `A` = any capability-bearing CommandSender accepted by that
read/admin metadata path, including P/C. Known = VapeeCore-known name/UUID, with ambiguity rejection;
Online = exact full case-insensitive online name, no OfflinePlayer/Mojang lookup.
`U` = loaded-only OnlineStaffTargetGuard; `M` = moderation hierarchy including async known
offline target load; `S` = utility's own activity/state restrictions. Self exemptions retain
capability requirements. All expected input errors are handled rather than relying on usage.

| Root | Aliases | Executor | Permission | Sender / target semantics | Hierarchy | State guards | Persistent mutation | Completion | Concern |
|---|---|---|---|---|---|---|---|---|---|
| vapeecore | core | command.CoreCommand | none; reload admin | A, no target | — | reload running guard | No; runtime reload only | permitted subcommands/help | TEST-001 coordinator coverage |
| quests | quest | quest.daily.command.DailyQuestCommand | quest.use | P; own daily state | — | online/loaded; daily sync statuses | sync can assign/rotate, buffered player save | empty | Healthy read-only UI after sync |
| mute | — | moderation.command.MuteCommand | moderation.mute | P/C; Known online/offline | M | no self; active sanction/no-op | moderation snapshot | online safe args | MOD-001 shared async path |
| unmute | — | moderation.command.UnmuteCommand | moderation.unmute | P/C; Known online/offline | M | no self; active mute required | moderation snapshot | online + active mute UUIDs | MOD-001 shared async path |
| warn | — | moderation.command.WarnCommand | moderation.warn | P/C; Known online/offline | M | no self | moderation snapshot | online safe args | MOD-001 shared async path |
| ban | — | moderation.command.BanCommand | moderation.ban | P/C; Known online/offline | M | no self; active ban/no-op | save before disconnect | online safe args | MOD-001 reproduced here |
| unban | — | moderation.command.UnbanCommand | moderation.unban | P/C; Known online/offline | M | no self; active ban required | moderation snapshot | online + active ban UUIDs | MOD-001 shared async path |
| kick | — | moderation.command.KickCommand | moderation.kick | P/C; Known must actually be online | M | no self; recheck live target | save before disconnect | online safe args | MOD-001 shared authorization path |
| history | — | moderation.command.HistoryCommand | moderation.history | P/C; Known online/offline | M | self allowed; positive page | No | online safe args | Shared async target snapshot; no punishment mutation |
| spawn | — | lobby.command.SpawnCommand | lobby.spawn | P; self | — | configured spawn/loaded world, teleport boolean | No | empty | Intentional existing scope, not a staff mutation |
| setspawn | — | lobby.command.SetSpawnCommand | lobby.setspawn | P; own location | — | finite valid location | lobby.yml | empty | PERSIST-001 |
| coins | — | economy.command.CoinsCommand | economy.coins; get/add/remove/set economy.admin | P self read; A explicit Known read; P/C online-loaded mutation | U on other mutations only | loaded balance, amount/overflow/funds | whole player save with rollback | subcommands; known-safe online args, guarded mutation targets | No offline wallet mutation |
| profile | — | identity.command.ProfileCommand | profile.view | P self; A explicit Known online/offline | — | read failures controlled | No | online names only | Offline rank/playtime unavailable is honest |
| friend | friends | friend.command.FriendCommand | friend.use | P; Known relationships/targets | — | current requests/privacy/limits; no self | friends.yml snapshot | domain-specific relationships, UUID fallback | SEC-001 in GUI, not command gate |
| clan | clans | clan.command.ClanCommand | clan.use | P; Known players, clan tag/UUID | — | current membership/role, disband confirm | clans.yml snapshot | current role/action-specific targets | Keep clan role policy separate |
| msg | — | privatemessage.command.MessageCommand | message.use | P; Online target | — | settings, bidirectional ignore, outgoing mute | No | exact online names | No async domain reads |
| reply | r | privatemessage.command.ReplyCommand | message.use | P; last partner UUID currently online | — | same PM policy, valid partner | No | empty | Runtime session only |
| settings | — | settings.command.SettingsCommand | settings.use | P; own settings, Known added-user args | — | loaded owner, no self add; unknown saved UUID removable | whole player save/rollback | visibility actions, online add/current remove | SEC-001 direct/open retained menu paths |
| ignore | — | social.command.IgnoreCommand | social.ignore | P; Online target | — | loaded owner; no self | whole player save/rollback | online names | Social publishes after persistence |
| unignore | — | social.command.UnignoreCommand | social.ignore | P; only own ignored Known name/UUID | — | reject ambiguous name | whole player save/rollback | own ignored IDs; safe UUID fallback | Do not broaden authority to global Known scan |
| ignorelist | — | social.command.IgnoreListCommand | social.ignore | P; own saved UUID set | — | loaded owner | No | empty | Known names then UUIDs; literal output |
| blackjack | — | activity.blackjack.command.BlackjackCommand | blackjack.admin | A setup metadata; location capture/preview P | — | definition/area/seat/world validation | blackjack.yml for setup; preview runtime only | legal setup actions/IDs/seat values | Public physical play intentionally has no use node |
| warp | — | lobby.warp.command.WarpCommand | warp.admin | A admin metadata; set captures Player position | — | ID/material/order/world validation | warps.yml; same-value changes no-op | action-specific visible/hidden/current IDs | PERF-001 on failing replacement only |
| build | — | utility.command.BuildCommand | utility.build | P self | — | lobby, no activity; safe exit first | No | empty | NORMAL/BUILD owns inventory, not permission-only bypass |
| fly | — | utility.command.FlyCommand | utility.fly + fly.others for other | P self; P/C explicit Online | U | S; BUILD/native creative flight ownership | No | guarded sorted online | CMD-001 repeated plumbing |
| speed | — | utility.command.SpeedCommand | utility.speed + speed.others | P self; P/C explicit Online | U | S; levels 1–10, actual fly/walk channel | No | levels, guarded online | CMD-001 |
| gamemode | gm | utility.command.GameModeCommand | utility.gamemode + gamemode.others | P self; P/C explicit Online | U | S; reject BUILD, finite accepted modes | No | full mode names, guarded online | CMD-001 |
| tp | teleport | utility.command.TeleportCommand | utility.teleport; others/world/others.world per grammar | P implicit self; P/C explicit Online source; loaded worlds/coordinates | U on moved other source, not destination | source/destination activity; state bypass never hierarchy/permission/cancel bypass | No | grammar-position online/world/coordinate suggestions | Preserve bounded Vanilla subset and source distinction |
| tphere | — | utility.command.TeleportHereCommand | utility.teleport.here | P; Online moved target | U | activity/state guards; self no-op | No | guarded online | Console lacks own destination |
| heal | — | utility.command.HealCommand | utility.heal + heal.others | P self; P/C explicit Online | U | S; current health cap | No | guarded sorted online | CMD-001 |
| feed | — | utility.command.FeedCommand | utility.feed + feed.others | P self; P/C explicit Online | U | S | No | guarded sorted online | CMD-001 |
| ping | — | utility.command.PingCommand | utility.ping + ping.others | P self; A explicit Online with others | No, intentional low-sensitivity read | online only | No | permitted online | No staff policy needed for latency read |
| clear | — | utility.command.ClearCommand | utility.clear + clear.others | P self; P/C explicit Online | U | activity/BUILD and managed inventory safety | No | guarded online | Real destructive inventory action, capability protected |
| invsee | — | utility.command.InvseeCommand | utility.invsee | P viewer; Online target | U | read-only snapshot owned by viewer | No | guarded online | modify permission reserved/inactive |
| enderchest | — | utility.command.EnderChestCommand | utility.enderchest + enderchest.others | P viewer; self or Online owner | U on other | real owner chest; no fake read-only claim | Native world/player storage, not plugin file | guarded online | Expected real inventory mutation |
| rank | — | rank.command.RankCommand | rank.view | P self; A explicit Online | — | loaded LP cache only | No | exact online | No rank assignment |
| ranks | — | rank.command.RanksCommand | ranks.view | A; public track | — | empty/missing track controlled | No | empty | Track order, not staff authority |

No descriptor/executor capability mismatch was found for these command paths. There are 50
explicit permission nodes: 11 `default: true`, 39 `default: op`, no wildcard nodes and exactly
15 positive child edges. The complete defaults/edges are in the descriptor appendix.
`vapeecore.admin` is Core reload capability, not a staff bundle. `visibility.staff` is a
classification marker, not an action permission. `lobby.build` is deprecated compatibility
with child `utility.build`; production checks the latter. OP never bypasses staff hierarchy.

## 6. Persistence inventory and risk map

Paths below are relative to `plugins/VapeeCore/`. There are **12 logical stores**, consisting
of eleven named files and one per-player file family. Not all exist until their first write.
Native worlds, Enderchests and LuckPerms' own files are external owners, not extra VapeeCore stores.

| Store | Owner / reads and writes | Schema and load policy | Save/failure boundary | Risk / owner phase |
|---|---|---|---|---|
| config.yml | ConfigService; core/rank/staff/friend/clan/online reward settings | immutable validated CoreConfigState; defaults/warnings; staff list fail-safe | created if absent; reload does not overwrite live file | CONFIG-001; explicit reload policy preserved |
| chat.yml | ChatConfig / ChatModule | immutable config, validated MiniMessage/meta mode | create absent; prepare/apply/rollback only runtime | CONFIG-001 |
| lobby.yml | LobbyConfig / LobbyService | optional spawn; gamemode/protection/messages validated | setspawn saves before runtime swap; direct destination write | PERSIST-001 / 31 |
| presentation.yml | PresentationConfig / PresentationModule | interval, lines/templates/meta validated; bounded fallback | create absent; state/render/task replace with rollback | CONFIG-001, PRES-002 |
| private-messages.yml | PrivateMessageConfig / PrivateMessageModule | format pair/enabled validated | create absent; immutable runtime state | CONFIG-001 |
| warps.yml | WarpConfig / WarpService | ID→position/name/icon/navigation; invalid entries skipped with warning; legacy missing navigation defaults without startup rewrite | sibling temp, atomic attempt, replace retries then copy fallback; candidate saved before map publish | PERF-001 / 33; fallback crash durability / 31 |
| blackjack.yml | BlackjackTableConfig / BlackjackTableService | table area/dealer/display/seats; modern and complete legacy interaction schemas distinguished | sibling temp/atomic/replace/copy; candidate persisted before activation; adapter rollback | PERF-001 / 33; explicit legacy migration / 31 |
| daily-quests.yml | DailyQuestConfig / DailyQuestModule | enabled/count/reset/definitions; malformed definition aborts prepare; valid unknown progress key allowed | absent default creation; runtime config/registry reload, no automatic operator rewrite | Keep catalog and assignment ownership separate |
| players/<uuid>.yml | FilePlayerRepository / PlayerService | identity, settings/visibility, coins, social, online-reward counter, quests/cycle; unversioned | whole current aggregate to sibling temp; atomic move, AtomicMoveNotSupported fallback; save before unload/index publish | PERSIST-002 / 31; optional malformed state recovery policy explicit |
| friends.yml | FileFriendRepository / FriendService | friendship/request snapshot and domain invariants; no player/name duplication | copy-on-write candidate save before state; temp replacement | phase 31 version/recovery policy; do not merge friendship rules with clans |
| clans.yml | FileClanRepository / ClanService | schema, UUIDs, roles, tags, memberships/invites validated; strict snapshot | copy-on-write save before state; temp replacement | phase 31 recovery/future-version contract |
| moderation.yml | FileModerationRepository / ModerationService | strict schema 1, exact keys, canonical UUIDs/Instants; duplicate IDs and YAML merges rejected | immutable whole snapshot, temp/atomic move then supported replace fallback; save→projection publish→state | Preserve fail-closed corruption handling; backups/fsync recovery deferred 31 |

Player core identity, coins and social corruption fail load rather than silently granting a
fresh wallet/profile. Optional malformed preferences/progress fall back per validated entry
with warnings. Valid unknown quest IDs are preserved; generic future keys remain inert without
a producer. This intentional tolerance is not the same as preserving arbitrary unknown YAML
fields (PERSIST-002). Loading alone does not overwrite corrupt files or grant rewards.

Player, friendship, clan and moderation atomic replacement protects ordinary partial writes,
but no repository promises crash-proof fsync, multi-file transactions, external concurrent
writers or a complete recovery/backup system. A non-atomic filesystem fallback is a documented
limit, not proof of observed loss. Lobby's direct write and unversioned player reconstruction
are the two actionable persistence findings; other fallback/recovery work stays in phase 31.

Settings, immediate wallet and ignore mutations revert the in-memory change on save failure.
Reward grants deliberately retain new runtime coins and a dirty marker for retry. Quest state
and coins share the same current CorePlayer save; there is no stale partial snapshot writer.
Reward flushes every 20 ticks; Quest's combined playtime/dirty flush every 100 ticks. Normal
quit/disable saves; a hard process/filesystem failure still has a bounded batching window,
extended by continuing save failures. No durable exactly-once journal guarantee is claimed.

## 7. Configuration and reload overview

Eight shipped configuration defaults plus `plugin.yml` comprise the nine resources. Existing
live files are not overwritten by changed resource defaults. No hidden auto-migration occurs.

| Default file | Important baseline defaults | Validation and ownership |
|---|---|---|
| config.yml | server `Vapee Community`; public track `ranks`; protected groups builder→moderator→admin→owner; friends 100/25/25; clans 25/25/10; name 3–24, tag 2–8; online reward enabled, 60 minutes/250 coins; debug false | ConfigService plus typed configuration records; public ranks and protected hierarchy are distinct policies |
| lobby.yml | ADVENTURE; join/respawn teleport, void rescue and six protection switches true; join/quit messages enabled | LobbyConfig; no spawn is fabricated when absent; unavailable world not auto-loaded |
| chat.yml | enabled; literal message component in rank_name format; legacy-ampersand LP meta | ChatConfig; event-scoped renderer, validated template fallback |
| private-messages.yml | enabled; distinct incoming/outgoing formats | PrivateMessageConfig; mute/ignore/settings are service policies, not template logic |
| presentation.yml | enabled; update 20 ticks; scoreboard enabled/lobby-only; tablist enabled; rank_name and rank/playtime/coins | PresentationConfig; immutable render state; scoreboard bounded lines, configurable interval |
| warps.yml | `warps: {}` | WarpConfig validates entries and navigator metadata; no hardcoded destinations |
| blackjack.yml | `tables: {}` | BlackjackTableConfig validates physical layout, uniqueness, legacy/modern distinction |
| daily-quests.yml | disabled; 4/day; 00:00, system timezone; `quests: {}` | safe general defaults; invalid definition is whole-prepare failure; no shipped quest/balance catalog |

The configured protected group defaults are deliberately named in Java/config, while rank
presentation remains data-driven. Empty/invalid protected lists use safe defaults; an empty
list does not silently turn protection off. Runtime group decisions use one current hierarchy
snapshot. No OP, color, weight, prefix, public track or inherited highest-rank shortcut exists.

MiniMessage validation and fallback remain contextual. Admin-authored templates and selected
LuckPerms metadata can be trusted formatting; player names, clan names, quest descriptions,
reasons, IDs, help syntax and message components are inserted literally or with component/
unparsed placeholders. Introducing shared readers must preserve missing-key, explicit-null,
wrong-type, warning and fatal-definition differences (CONFIG-001).

## 8. GUI / inventory matrix

There are **eight custom inventory GUIs**, plus Blackjack's owned player hotbar and native
Enderchest access: **ten inventory-facing surfaces**. Profile, rank and history are text UIs.
Blackjack has no active old chest/menu selector in production. Generic seat/display entities
are not inventory GUIs.

`Bound` means owner UUID + holder identity + exact bound inventory and active UUID registry.
Different menus have different checks: Settings, Navigator and DailyQuest additionally check
the player's currently open top; Friend/Clan rely on event top plus registry/binding. Do not
erase this distinction when extracting a helper. Every recognized menu event is canceled
before a rejected spoof/stale/unsupported action can transfer an item.

| Class | Size / holder | Binding and active tracking | Pagination / refresh | Close / quit / disable | Behavior | Current state guards | Shared candidates |
|---|---|---|---|---|---|---|---|
| settings.SettingsMenu | 54 / SettingsInventoryHolder | Bound; actual open top | no pages; refresh existing inventory | exact-instance forget; quit forget; tracked close | own five toggles + visibility navigation | loaded owner; missing permission recheck SEC-001 | literal nonitalic items/status, GUI-001/002 |
| settings.visibility.VisibilitySettingsMenu | 54 / VisibilitySettingsHolder | Bound | no pages; current preferences redraw | exact-instance forget; quit; tracked close | own master/friends/staff/added settings; game option unavailable | loaded preferences; SEC-001 | items/status/back/close, GUI-001/002 |
| settings.visibility.VisiblePlayersMenu | 54 / VisiblePlayersHolder | Bound, server slot→UUID | 45 content, clamped pages, fresh sort | exact-instance forget; quit; tracked close | right-click own added-user removal; command suggestion to add | known display/actual online read, owner loaded; SEC-001 | item/page helpers, GUI-001/002 |
| friend.gui.FriendMenu | 54 / FriendMenuHolder | Bound, slot→UUID | 45 content, view-specific pages; fresh reopen | exact-instance close; quit; tracked close | accept/deny/cancel; SHIFT_RIGHT removes friendship | current relation/privacy/limits; SEC-001 | renderItem/uiText/pageCount, GUI-001/002 |
| clan.gui.ClanMenu | 54 / ClanMenuHolder | Bound, clan snapshot ID and slot→UUID | 45 content; members/invites; fresh reopen | exact-instance close; quit; tracked close | current-role kick/transfer, invite accept/deny/cancel; suggestions for create/disband | clan.use rechecked on click; fresh clan membership/role | render/text/pageCount, GUI-001/002 |
| lobby.experience.navigator.NavigatorMenu | 54 / NavigatorInventoryHolder | Bound + actual top; publish after successful open | 45 content, current visible ordering, clamped | exact old-close safety; quit/world exit; isolated tracked close/final clear | current Warp ID teleport; controls | online, loaded, lobby, NORMAL, no activity; post-open check; current visibility/existence/world/cancel | renderItem/uiText/page helper; preserve access policy |
| quest.daily.menu.DailyQuestMenu | 54 / DailyQuestInventoryHolder | Bound + actual top; publish after real open | 45 content, clamped; sync before open/refresh | exact old-close safety; quit; isolated close/final clear; reload closes views | read-only quest items, automatic rewards; controls | online, loaded, quest.use at open/click; sync result handling | renderItem/text/page helper; keep sync semantics |
| utility.InvseeService | 54 / InvseeInventoryHolder | viewer/target UUID, bound snapshot, active view | no pages/refresh; cloned snapshot | exact close; viewer/target quit; owned close on disable | all click/drag/drop/offhand transfers canceled; modify inactive | command capability + staff guard before snapshot; exact active view | cloning/security distinct; not ordinary paginated menu |
| activity.blackjack.presentation.BlackjackInventoryService | player hotbar 0/1/2/4/8; PDC blackjack_action | activity membership and inventory owner; no chest holder | state-driven hotbar refresh | activity leave/quit/death/world/shutdown release | Deal/Hit/Stand/Double/Leave; status inert | current participant/phase/turn; BUILD exclusion and rollback in join flow | item text only; never unify authority with chest controls |
| UtilityService.openEnderChest | native owner inventory / native holder | Bukkit owner's real Enderchest | native client behavior | native close; no plugin snapshot map | explicitly mutable real inventory | Player viewer, base/others capability, Online owner, staff guard on other | intentional external/native UI; no snapshot abstraction |

SHIFT_RIGHT removal in Friends and SHIFT_LEFT/SHIFT_RIGHT member actions in Clans are
intentional gestures, not unhandled shift transfer. The listeners cancel movement first.
Navigator allows bottom-only drag only with valid active access; other custom menus are
stricter. Do not normalize all menus to one action or drag policy during a helper extraction.

## 9. Scheduler and ownership matrix

There are **14 direct scheduler owner classes**, **five shared repeating task types** and
the following finite delayed/handoff types. Counts describe types/classes, not simultaneous
runtime tasks. All use the synchronous Paper scheduler. No async scheduler, executor pool,
per-player reward poller or moderation expiry timer was found.

| Direct owner / work | Repeating or delay | Cancellation / stale guard | Work and scaling | Disk access | Recommendation |
|---|---|---|---|---|---|
| reward.RewardModule | repeating 20 ticks | task canceled on enable failure/disable; dirty flush/final cleanup | O(dirty players), one whole-profile save each attempted dirty UUID | Yes, dirty flush only | Preserve batching/retry; measure before merging schedulers |
| onlinereward.OnlineRewardModule | repeating 20 ticks | task canceled on failure/disable; final processing then clear | O(online loaded players), cumulative tick delta and aggregated intervals | indirect Reward dirty save later | No per-player tasks; statistics reset rebases |
| quest.QuestModule | repeating 100 ticks | canceled on failure/disable; final sample/flush; reporter deactivated | O(online loaded samples + dirty players) | Yes on dirty flush | Preserve producer/reporter and whole aggregate boundary |
| quest.daily.DailyQuestModule | repeating 1200 ticks | canceled on failure/disable; guarded per-player processing | O(online loaded players plus rotation/definition selection) | indirect Quest dirty saves | no second save task; pending prevents unsafe rotation |
| presentation.PresentationModule | repeating configured interval, default 20; initial delay 1 | cancel/recreate on reload, rollback/failure/disable | O(online players × bounded scoreboard lines), LP cached reads/components/tab writes | No | Profile realistic population before caching/delta extension |
| moderation.ModerationModule | runTask for offline authorization resume and async mute feedback | active command generation/listener flag; original actor session and fresh online lookup | one task per completed authorization/blocked chat event | resume can issue/revoke/save; feedback no | MOD-001 target freshness; no blocking futures |
| lobby.LobbyListener | next-tick join/respawn state | plugin/online/loaded/current world checks; Paper plugin disable | one callback per event | No direct scheduled write | retain real world recheck |
| lobby.experience.LobbyExperienceListener | delayed synchronization, 1 tick | active + plugin/online/loaded/lobby | one callback per join/respawn | No | event-driven visibility, no global poller |
| lobby.experience.LobbyItemListener | next-tick item repair; 10-tick visibility cooldown removal | plugin/player/event relevance; Bukkit cooldown plus UUID set cleanup | bounded per-interaction work | setting toggle itself saves synchronously; delayed repairs do not | retain managed-item ownership |
| utility.UtilityListener | next-tick join normalization | plugin/online checks; runtime UUID cleanup | one callback per join | No | ordering after lobby normalization intentional |
| presentation.PresentationListener | next-tick player presentation | plugin/online checks | one callback per join | No | cache-independent initial update |
| seat.SeatService | next-tick voluntary managed dismount | expected key/entity UUID, online/enabled, programmatic suppression | one callback per real managed dismount | No | retain relevance guard; LIFE-002 fault cleanup |
| activity.blackjack.BlackjackService | turn timeout 400 ticks; result reset 60 ticks | BlackjackSession task identity + round/turn generation; track/untrack; Activity reset/close cancel | bounded active tables/turns, no polling | No (quest callback buffers reward/progress) | preserve stale timeout/reset protections |
| activity.blackjack.presentation.BlackjackPreviewService | timeout 240 ticks (12 s) | per-admin preview identity; replacement/quit/shutdown cancels | finite markers per preview | No | LIFE-002 isolate external remove failure |

`ActivitySession` is the indirect task owner/registry: identity-based tracking, cancel before
and after reset and on close, with hook/error isolation. `BlackjackSession` consumes its own
task handles. They are not extra direct scheduler-owner classes. Short-lived callbacks may
capture a Player for one tick; no durable static Player map was found. Paper cancels plugin
tasks on disable, while code-level relevance guards prevent stale actions. This is not proof
that every individual callback has an explicit cancelable handle.

## 10. Threading / async boundary matrix

There are **four distinct production asynchronous boundaries**. StaffHierarchyService forwards
the LuckPerms future; it is not a fifth independent producer. Main-thread scheduled callbacks
are accounted for above and are not mislabeled asynchronous work.

| Boundary / trigger | Thread | Reads | Writes / synchronization | Main-thread handoff | Assessment |
|---|---|---|---|---|---|
| chat.ChatListener / AsyncChatEvent → ChatService | Paper async chat event; event-scoped viewer-unaware renderer | current immutable config, LP already-loaded cached group/meta, Social immutable concurrent ignore snapshot; chat/display components supplied by event path | event renderer/viewer filtering; no repository, statistics or wallet mutation | none needed for formatting | Deliberate supported async path; fresh renderer prevents cross-event cached-message reuse |
| moderation.ModerationMuteChatListener / AsyncChatEvent LOWEST | Paper async chat | ModerationMuteProjection's volatile immutable map/list snapshot and time; not ModerationService | cancel event; immutable fact/UUID captured; projection publication is one whole-map reference swap after save | one synchronous task, player resolved fresh for feedback, active flag | Healthy explicit projection; no async repository/Player feedback |
| permission.LuckPermsService.loadPrimaryGroup / UserManager.loadUser | provider completion thread, not assumed main | returned User primary group | completion produces Optional<String>; no LP mutation, join/get blocking, player lookup or command feedback | consumer schedules via next boundary | read-only controlled provider API, failure propagated |
| moderation.command.AbstractModerationCommand.whenComplete → resume | callback may be provider thread, resume is Paper main | callback result/Throwable; resume checks active generation, original actor session, current capability/config/actor group | runtime/domain mutation only on resume; AtomicBoolean generation validity | explicit injected main-thread executor | MOD-001: target group snapshot can become stale during queued handoff |

Main-thread-owned mutable maps in Player, Economy, Reward, Quest, Presence, Visibility,
Activity, seats, displays and GUI sessions are intentional. The mute projection and Social
ignore snapshot are the designed asynchronous read surfaces. Future consumers must use these
surfaces or hand off; making every service map concurrent would not make Bukkit/domain
transactions safe. No concrete unsupported off-thread world/inventory/statistics mutation was
found. MOD-001 is authorization freshness, not an off-thread Bukkit mutation.

## 11. Test coverage overview

All 111 test sources were inventoried and reviewed by area. The final separately launched
101 main classes and their exact counts are in the verification appendix. Support-only files:
`StaffTargetTestFixture`, `ModerationTestSupport`, `ClanMenuFixture`, `FriendMenuFixture`,
`NavigatorFixture`, `QuestMenuFixture`, `QuestCompletionFixture`, `SettingsMenuFixture`,
`VisibilityMenuFixture`, `VisibilityRelationshipFixture` (exact paths in source inventory).
These ten files are not skipped executable harnesses.

| Area | Coverage found in actual test sources | Important limit |
|---|---|---|
| Config/defaults/descriptor/Core/help | defaults, exact alias/usage/permission/child contract, safe help rendering, command statuses/filtering | Core uses a reload-result seam, not actual ReloadService failure execution (TEST-001) |
| Rank/permissions/staff | cached ranks/track order/color, hierarchy decisions/config, loaded online guard, async provider errors | source-contract checks cannot prove every real LP event race; MOD-001 missing queued-target-promotion case |
| Moderation | domain time/revocation, strict persistence, save-failure COW, real module partial registration, command ordering/permission/session checks, ban and mute enforcement | no real punished player or live chat client; target freshness race absent |
| Player/settings/visibility | field roundtrips, legacy and wrong-type fallback, rollback, bidirectional friend/ignore refresh, policy/asymmetric visibility | arbitrary unknown player fields/future version contract absent (PERSIST-002) |
| Economy/reward/online rewards | long bounds/funds/overflow, guarded commands, deferred vs immediate save, dirty retry, cumulative/reset/disabled reward counter | hard process crash/fsync and operator recovery are not tested |
| Identity/profile/social/PM | known/ambiguous/UUID handling, loaded-first identity, offline presentation honesty, safe names, privacy/mute/reply paths | proxy fixture is not a client/server message exchange |
| Friends/clans/presence | snapshots/invariants/limits/roles, persistence rollback, command/UI mutations, stale holders, opt-in/ignore/dedup/error-isolated notification | GUI denied/revoked capability and bulk-close fault coverage uneven (SEC-001, GUI-002) |
| Quest/daily/completion | generic keys/longs, pending retries, reporter exact messages/no duplicate grant, playtime boundaries/resets, deterministic UUID/cycle selection/DST, safe read-only UI | no live inventory rendering; source lifecycle tests supplement rather than execute all bootstrap faults |
| Lobby/warp/navigator | NORMAL/BUILD, protection, current-world flow, persistence failure/no-op, legacy/null navigation, forged/stale views/current targets/canceled teleport | world transfer with another live plugin/client not exercised |
| Utility/teleport/Invsee | grammar/world/relative/local/finite values, permissions/self/console, staff-before-side-effects, inventory clones and canceled transfer variants | native actual client inventory UX and full partial-enable fault path absent |
| Activity/Blackjack | state machine/task tracking, settlement/dealer/natural/bust/double, exact outcome once, fairness with fixed seeds, table/preview/geometry | historical commented harness body is not executed (LEGACY-001) |
| Seat/world display | generic ownership/key/entity cleanup, mount rollback/dismount relevance, stale/foreign protection, item clones | alive entity with teleport=false absent (DISPLAY-001); bulk-remove exception isolation absent |
| Presentation/chat/playtime | literal placeholders/rank color, renderer fallback/cache scope, personal scoreboard behavior, formatter limits | no multiplayer overhead nametag/client integration; no tablist foreign-owner restoration proof |

The harness architecture is strong for deterministic domain/adapter checks. Several integration
and lifecycle harnesses assert source wiring strings; their names alone do not establish
runtime fault coverage. GUI proxy fixtures execute real handlers/events and preserve exact
inventory identity, but simplify Bukkit mechanics. Moderation tests include real concurrent
projection/chat-thread execution; this is more evidence than a source-only threading check.
The 14,705 checks are assertions, not 14,705 independent user scenarios. No test is weakened,
removed or rewritten during this audit. Standard Maven compilation is not a substitute for
launching these executable main classes (TEST-002).

## 12. Architecture Worth Preserving

- Explicit modular-monolith constructor DAG and reverse module shutdown; no discovery magic.
- Read-only LuckPerms ownership, data-driven rank presentation and separate capability/hierarchy.
- Immutable moderation records/snapshots, strict schema, save-before-publish, exact expiry and
  async mute projection; no duplicated Boolean ban/mute cache or polling expiry task.
- Player UUID authority and loaded-first name ambiguity handling; no invented online/offline
  target semantics or Mojang/OfflinePlayer lookup in utilities.
- Friend/Clan copy-on-write snapshots; domain-specific privacy, roles, limits and relation
  callbacks published only after persistence; no reverse dependency into Visibility.
- RewardService as the grant boundary, whole current player saves and quiet pending retries;
  no direct Blackjack economy payout or stale balance/quest partial writer.
- Generic Quest keys, deterministic daily cycle selection, pending reward rotation block,
  small reporter/producer boundaries and no default hardcoded quest/balance catalog.
- Event-driven opt-in Presence with per-recipient failure isolation and no queue/AFK poller.
- Lobby NORMAL/BUILD/Activity inventory ownership and managed-item tags; capability alone does
  not bypass lobby protection. Startup normalizes movement/creative inventory state.
- Generic Warp domain with Compass application policy/current ID lookup; missing worlds and
  canceled teleports do not produce false success or world auto-load.
- Focused feature GUIs with server-owned UUID/ID bindings, canceled transfers and literal text;
  read-only Invsee remains distinct from deliberately mutable native Enderchest.
- Generic Activity/Seat/WorldDisplay ownership, nonpersistent marked native entities, finite
  session tasks and generation checks; Blackjack is free play with explicit current-turn rules.
- Scoreboard ownership respects a foreign board by identity; presentation uses safe components,
  cached LP reads and configured main-thread updates rather than packet/NMS machinery.

These healthy designs are constraints on any later change, not optional cleanup targets.

## 13. Findings summary by severity

**20 findings: Critical 0, High 1, Medium 9, Low 9, Info 1.** Critical IDs: none.
High IDs: **MOD-001**. Conditional failure findings are not reported as observed server-wide
failure or likely ongoing data loss. Neither persistence finding meets Critical's likelihood
threshold on the current known schema. Repeated helper code is Low unless tied to an actual
failure boundary. An unimplemented future clan/nametag capability is Info, not a regression.

| ID | Severity | Category | Primary phase | Short description |
|---|---|---|---|---|
| MOD-001 | High | Security | 32 | Offline target promotion during queued authorization handoff is not rechecked |
| SEC-001 | Medium | Security | 32 | Friend and Settings GUI routes omit current capability check |
| DISPLAY-001 | Medium | Lifecycle | 33 | Rejected teleport discards handle of a still-live display |
| PERSIST-001 | Medium | Persistence | 31 | Lobby spawn write truncates destination directly |
| PERSIST-002 | Medium | Persistence | 31 | Unversioned whole-player save discards unknown YAML fields |
| GUI-001 | Low | Duplication | 30C | Repeated literal item/render and page arithmetic |
| GUI-002 | Medium | GUI | 30C | Bulk close of several menus stops at first exception |
| LIFE-001 | Medium | Lifecycle | 30D | Utility partial enable has no local registration rollback |
| CONFIG-001 | Low | Config | 30D | Repeated default creation and scalar validation plumbing |
| CMD-001 | Low | Command | 30E | Repeated utility target/usage/completion plumbing |
| DUP-001 | Low | Duplication | 30E | Repeated safe known identity command-argument selection |
| API-001 | Low | API | 30B | Concrete Blackjack read API exposes mutable hands/rounds/shoe |
| PRES-001 | Info | Architecture | 30F | Clan/nametag/glyph extension must respect per-viewer boards/visibility |
| PRES-002 | Low | API | 30F | Tablist cleanup resets fields without foreign-write ownership comparison |
| LIFE-002 | Medium | Lifecycle | 33 | Entity/preview bulk cleanup stops at first external remove failure |
| PERF-001 | Low | Performance | 33 | Failing config replacement retries sleep up to 250 ms on main thread |
| TEST-001 | Medium | Testing | 34 | Actual reload coordinator failure/rollback paths lack direct harness |
| TEST-002 | Medium | Testing | 34 | Standard Maven build does not execute the 101 main harnesses |
| DOC-001 | Low | Documentation | 34 | Current guide overview/dependencies/harness list have stale claims |
| LEGACY-001 | Low | Duplication | 30E | 687-line commented historical Blackjack harness retained in active test file |

## 14. Findings summary by category

| Category | Count | IDs |
|---|---:|---|
| Architecture | 1 | PRES-001 |
| GUI | 1 | GUI-002 |
| Command | 1 | CMD-001 |
| Persistence | 2 | PERSIST-001, PERSIST-002 |
| Config | 1 | CONFIG-001 |
| Threading | 0 | No concrete unsupported-thread defect found |
| Performance | 1 | PERF-001 |
| Security | 2 | MOD-001, SEC-001 |
| Lifecycle | 3 | DISPLAY-001, LIFE-001, LIFE-002 |
| Duplication | 3 | GUI-001, DUP-001, LEGACY-001 |
| API | 2 | API-001, PRES-002 |
| Testing | 2 | TEST-001, TEST-002 |
| Documentation | 1 | DOC-001 |
| Other | 0 | — |

Each issue is counted once under its dominant category. Tests missing the concrete MOD/SEC/
DISPLAY regressions are supporting gaps, not three extra findings. GUI-001's map repeats its
locations, not separate findings per menu. GUI-002 concerns player inventory-close isolation;
LIFE-002 concerns external entity/preview removal and resource reconciliation. DISPLAY-001
has a distinct boolean-result misclassification root cause.

## 15. Top priorities

Twelve priorities, ordered by actual impact and follow-up value rather than file order:

1. MOD-001: recheck target authority at main-thread resume; regression for the exact race.
2. SEC-001: enforce GUI capability consistently at entry and action, including revocation.
3. DISPLAY-001: retain/remove correctly when teleport rejects a live entity.
4. PERSIST-001: protect old lobby file on failed/interrupted spawn save.
5. PERSIST-002: define player version/unknown-field migration contract before schema growth.
6. GUI-002: isolate each owned close and always clear session bindings.
7. LIFE-001: explicit rollback for each installed utility hook during partial enable.
8. LIFE-002: attempt all owned resource removals despite an individual failure.
9. TEST-001: execute actual coordinator prepare/apply/rollback/reentrancy failure cases.
10. TEST-002: make the full separately executable harness process reproducible and mandatory.
11. GUI-001: remove only proved shared item/page plumbing after preserving existing behavior.
12. CONFIG-001: centralize narrow validated-reader/default creation patterns with policy parity.

The phase sequence is a scope map, not a reason to postpone MOD-001 behind cosmetic work.
No priority is implemented in 30A.

## 16. Detailed findings

### MOD-001 — stale offline-target authority at resume

**Finding ID:** MOD-001

**Severity:** High

**Category:** Security

**Evidence:** `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`,
`authorize`, future `whenComplete`, `resume`, `authorizedExecute`;
`src/main/java/dev/vapee/core/rank/staff/StaffHierarchyService.java#decideLoaded`;
`src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`, async cases.

**Problem:** The offline-load completion captures its target group. Resume rereads actor
session/capability/group and current hierarchy config but uses the captured target group.
In the probe, admin's ban waited for an unloaded moderator target; the load completed, then
the now-loaded target changed to owner before the queued main runnable ran. A fresh
`decideLoaded` denied same-or-higher, but the resumed command saved an active ban and kicked.

**Impact:** A target promotion in this precise handoff window can bypass the protection of
equal/higher staff. This is a real authorization defect, not a hypothetical async map race.
Other shared commands inherit this decision path; their effects differ.

**Recommendation:** On main resume use a freshly available loaded target group, or a clearly
fresh, consistent resolution strategy, before deciding; fail closed when freshness cannot be
established. Preserve original UUID/session binding and the current config/actor rechecks.
Add the promotion-after-completion-before-resume regression.

**Do not:** Block LP futures, access Bukkit from the provider callback, add OP/weight bypasses,
rewrite moderation schemas or cache group levels in another service.

**Suggested phase:** 32

**Confidence:** High; reproduced against actual baseline executor/domain.

### SEC-001 — capability missing on retained/direct GUI paths

**Finding ID:** SEC-001

**Severity:** Medium

**Category:** Security

**Evidence:** `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java#open`,
`FriendMenuListener.java#onInventoryClick`; `src/main/java/dev/vapee/core/settings/SettingsMenu.java#open`,
`SettingsListener.java#onInventoryClick`; `settings/visibility/VisibilitySettingsMenu.java`,
`VisibilitySettingsListener.java`, `VisiblePlayersMenu.java`, `VisiblePlayersListener.java`;
`src/main/java/dev/vapee/core/lobby/experience/LobbyItemListener.java`, settings item path.

**Problem:** Commands check friend.use/settings.use, but these menu opens/action handlers do
not consistently check the current capability. The settings hotbar opens directly. A real
Friend click with hasPermission=false, same owner/binding and a valid incoming request
accepted and persisted the friendship in the probe. Already open menus also survive revocation.

**Impact:** An explicitly denied feature can still mutate the owner's preferences/relations
through GUI routes. Default true limits ordinary exposure; no admin escalation is claimed.
Clan and Quest click capability checks already show the intended local pattern.

**Recommendation:** Check the feature capability at open and immediately before permitted
actions; on denial cancel/forget/close only the owned view and give controlled feedback.
Test explicit denial, revocation and direct hotbar access.

**Do not:** Add a rank gate, a global permission listener, change domain privacy/limits,
or require blackjack.admin for intentionally public physical play.

**Suggested phase:** 32

**Confidence:** High; Friend action reproduced, analogous Settings paths source-confirmed.

### DISPLAY-001 — live entity loses ownership on teleport false

**Finding ID:** DISPLAY-001

**Severity:** Medium

**Category:** Lifecycle

**Evidence:** `src/main/java/dev/vapee/core/worlddisplay/WorldDisplayService.java#teleport`,
`DisplayGateway` and Bukkit gateway teleport;
`src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackWorldViewService.java#apply`;
`src/test/java/dev/vapee/core/worlddisplay/WorldDisplayHarness.java`.

**Problem:** Any false teleport removes the handle, treating a rejected teleport as a vanished
entity. The actual call's entity can still exist. With an alive fake gateway entity and
teleport=false, the probe could recreate the same key, producing two live entities. Owner
cleanup removed only the replacement, leaving one old entity and zero registry handles.

**Impact:** Conditional orphan display/duplicate after a rejected teleport; later owner cleanup
cannot address the forgotten entity until a broader tagged stale scan. Normal successful
refreshes are not shown to leak. Existing false test covers disappearance, not rejection.

**Recommendation:** Distinguish missing entity from rejected movement. Retain a live handle
on rejection, or actually remove/reconcile that entity before forgetting/recreating. Add the
alive-false regression through service and Blackjack refresh.

**Do not:** Add a periodic full-world scanner or packet hologram framework to hide the cause.

**Suggested phase:** 33

**Confidence:** High; reproduced with production service and controlled gateway.

### PERSIST-001 — lobby save directly overwrites destination

**Finding ID:** PERSIST-001

**Severity:** Medium

**Category:** Persistence

**Evidence:** `src/main/java/dev/vapee/core/lobby/config/LobbyConfig.java#saveSpawn`,
`saveConfiguration`, call `configuration.save(configFile.toFile())`;
`src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java`; local Paper API
`org.bukkit.configuration.file.FileConfiguration#save(File)` bytecode.

**Problem:** Unlike the repository/tempfile patterns, this path opens/truncates the live
lobby.yml before writing. Runtime swaps only after success, but disk's previous good contents
are not protected from mid-write interruption or I/O failure.

**Impact:** Conditional loss/partial lobby configuration, despite correct runtime error
feedback. No actual operator file was corrupted and current healthy saves work.

**Recommendation:** Serialize to a validated sibling temporary file, close it, replace with
an atomic attempt and explicit supported fallback/recovery policy. Test destination preserved
on failed serialization/replacement.

**Do not:** Introduce a database, rewrite all configuration schemas, or treat command exception
handling alone as disk atomicity.

**Suggested phase:** 31

**Confidence:** High; source plus actual dependency bytecode, crash effect conditional.

### PERSIST-002 — player schema cannot preserve future fields

**Finding ID:** PERSIST-002

**Severity:** Medium

**Category:** Persistence

**Evidence:** `src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java#findByUniqueId`,
`save`, reconstruction of `new YamlConfiguration`; no schema-version/future-version guard.

**Problem:** A valid player file with an unknown durable subtree loaded successfully; a normal
save regenerated only recognized fields and removed that subtree in the probe. The format is
unversioned. Preserving unknown quest IDs within known fields does not protect arbitrary
future/extension fields.

**Impact:** Current known fields round-trip, but running an older plugin against a future
schema or valid extension can silently discard data on save. This is not a claim that current
wallet/identity corruption is auto-rewritten or that all unknown YAML is contractually supported.

**Recommendation:** Define supported schema versions, explicit migrations/backups and a
higher-version refusal or safe preservation policy before adding durable fields. Add tests
for unknown fields and version downgrade/forward handling.

**Do not:** Blindly preserve invalid core values, eagerly rewrite on load, or merge all domain
repositories into a universal serializer.

**Suggested phase:** 31

**Confidence:** High; actual temporary-file load/save reproduced unknown-field removal.

### GUI-001 — repeated safe item and pagination plumbing

**Finding ID:** GUI-001

**Severity:** Low

**Category:** Duplication

**Evidence:** `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java#renderItem/#uiText/#pageCount`;
`src/main/java/dev/vapee/core/clan/gui/ClanMenu.java#render/#text/#pageCount`;
`src/main/java/dev/vapee/core/settings/SettingsMenu.java#renderItem/#uiText`;
`settings/visibility/VisibilitySettingsMenu.java#renderItem`, `VisiblePlayersMenu.java#renderItem`;
`lobby/experience/navigator/NavigatorMenu.java#renderItem`; `quest/daily/menu/DailyQuestMenu.java#renderItem`.

**Problem:** These concrete menus repeat ItemStack/meta name/lore assignment, explicit
nonitalic literal text and clamped 45-content page arithmetic/navigation.

**Impact:** A simple rendering convention change or new menu requires matching several copies;
no current item-transfer or page-overflow defect is inferred from duplication alone.

**Recommendation:** A tiny UiItemFactory for safe literal components/meta and a pure page helper,
introduced with existing output parity. Consider a session check helper only after reconciling
the actual-top/open-publication differences. Keep feature layouts/targets/gestures local.

**Do not:** Build a menu DSL, central GUI controller, shared domain action dispatcher or infer
authorization from item name/material/lore.

**Suggested phase:** 30C

**Confidence:** High; exact repeated implementations identified.

### GUI-002 — one failed close interrupts remaining owned views

**Finding ID:** GUI-002

**Severity:** Medium

**Category:** GUI

**Evidence:** `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java#closeOpenInventories`;
`clan/gui/ClanMenu.java#closeOpenInventories`; `settings/SettingsMenu.java#closeOpenInventories`;
`settings/visibility/VisibilitySettingsMenu.java#closeOpenInventories`,
`VisiblePlayersMenu.java#closeOpenInventories`; contrasting NavigatorMenu and DailyQuestMenu cleanup.

**Problem:** A lookup/getOpenInventory/closeInventory exception aborts the loop before remaining
players and before active map clear. Several modules remove their fields/listeners in finally,
but that does not close other players' owned screens.

**Impact:** Conditional incomplete shutdown/failure cleanup of menu bindings/views; no permanent
heap leak or ordinary close failure is claimed. Navigator/Quest already isolate closes.

**Recommendation:** Attempt each tracked owned view independently, log owner context, and clear
bindings in finally. Retain exact-instance comparison so foreign replacement views survive.
Fault-test one failing close alongside another healthy viewer.

**Do not:** Close all online inventories indiscriminately or centralize feature domain policy.

**Suggested phase:** 30C

**Confidence:** High on code path; failure is conditional, not observed Paper smoke behavior.

### LIFE-001 — utility partial registration lacks local rollback

**Finding ID:** LIFE-001

**Severity:** Medium

**Category:** Lifecycle

**Evidence:** `src/main/java/dev/vapee/core/utility/UtilityModule.java#enable/#registerCommand/#disable`;
`src/main/java/dev/vapee/core/module/ModuleManager.java#enableAll`; compare
`moderation/ModerationModule.java` and `quest/daily/DailyQuestModule.java` local rollback.

**Problem:** Utility installs twelve executor/completer pairs and two listeners without a
local catch/rollback, and publishes service fields only afterwards. A late failure leaves
earlier hooks installed; the failing module is not in ModuleManager's successful-enable list.
Command tracking also starts after both setter calls.

**Impact:** Conditional partial startup ownership/hook inconsistency until Paper's broader
plugin cleanup. This is not evidence of a steady-state leak or broken clean startup.
Relying on normal disable is insufficient for a not-successfully-enabled module.

**Recommendation:** Explicitly track each installed hook as soon as owned and rollback local
candidates in an enable failure path. Reuse a small registration cleanup helper only where
this removes repeated ownership code. Add real partial-installation failure cases.

**Do not:** Make ModuleManager blindly disable every never-enabled module, introduce reflection
registration/DI container, or convert 30B into all bootstrap cleanup.

**Suggested phase:** 30D

**Confidence:** High; control flow and manager contract are explicit.

### CONFIG-001 — repeated narrow configuration infrastructure

**Finding ID:** CONFIG-001

**Severity:** Low

**Category:** Config

**Evidence:** `src/main/java/dev/vapee/core/chat/config/ChatConfig.java#readBoolean/#createDefaultFile`;
`privatemessage/config/PrivateMessageConfig.java#createDefaultFile`;
`presentation/config/PresentationConfig.java#readBoolean/#readString/#createDefaultFile`;
`lobby/config/LobbyConfig.java#readBoolean/#createDefaultFile`;
`config/ConfigService.java#readString/#readBoolean`; `quest/daily/DailyQuestConfig.java`.

**Problem:** Absent-file creation, scalar type checks and contextual warning/fallback plumbing
are maintained in multiple feature configs.

**Impact:** Future schema/default conventions are easy to update unevenly; current policy
differences are meaningful and no current default mismatch is asserted.

**Recommendation:** Extract only small absent-resource creation and validated scalar readers
with caller-specified defaults/context. Keep each typed state, prepare/apply ownership and
fatal-vs-tolerant parsing explicit.

**Do not:** Add reflection schema mapping, universal config manager or silently normalize live
files. Filesystem replacement/durability policy is phase 31, not this reader cleanup.

**Suggested phase:** 30D

**Confidence:** High on repeated patterns; consolidation value is modest.

### CMD-001 — repeated utility target and completion plumbing

**Finding ID:** CMD-001

**Severity:** Low

**Category:** Command

**Evidence:** `src/main/java/dev/vapee/core/utility/command/FeedCommand.java#resolveTarget/#playerNames`;
`HealCommand.java#resolveTarget/#playerNames`; `FlyCommand.java#playerNames`;
`SpeedCommand.java#playerNames`; `GameModeCommand.java#playerNames`;
`src/main/java/dev/vapee/core/utility/OnlinePlayerResolver.java`.

**Problem:** Existing exact online resolution is already shared, but utility commands repeat
self/explicit-other sender plumbing, syntax/error construction and prefix filtering/sorting.

**Impact:** Small new utility commands require copying incidental code. No claim that the
exact-online algorithm itself is independently reimplemented everywhere.

**Recommendation:** Share a pure name completion helper and, if justified by exact parity,
a small optional-self target result helper. Leave capability ordering, staff decisions,
activity/BUILD and teleport grammar in each command.

**Do not:** Introduce an abstract mega-command/router, add offline targets, or merge teleport's
source/destination grammar with simple optional-target utilities.

**Suggested phase:** 30E

**Confidence:** High.

### DUP-001 — safe identity command argument rules repeat

**Finding ID:** DUP-001

**Severity:** Low

**Category:** Duplication

**Evidence:** `src/main/java/dev/vapee/core/friend/FriendMessages.java#commandArgument`;
`clan/command/ClanCommand.java#identityCommandArgument`;
`settings/command/SettingsCommand.java#identityCommandArgument`;
`moderation/command/ModerationCommandContext.java` safe target argument;
`economy/command/CoinsCommand.java` safe completion identity argument.

**Problem:** Name→same UUID roundtrip and UUID fallback checks occur in several known-identity
UI/completion paths; whitespace safety is more explicit in Friends. Authority sets differ.

**Impact:** Safe command suggestions are harder to maintain consistently; no command injection
is established merely by different implementations, and normal Minecraft names are constrained.

**Recommendation:** A small pure safe-argument helper using supplied identity resolution and
UUID, with explicit whitespace/ambiguity fallback. Callers still choose their allowed candidate set.

**Do not:** Create global Known-player scanning, change Unignore's own-list scope, load offline
LP users for completion or unify domain authorization.

**Suggested phase:** 30E

**Confidence:** High on repetition; future-risk impact is limited.

### API-001 — mutable Blackjack values exposed to read consumers

**Finding ID:** API-001

**Severity:** Low

**Category:** API

**Evidence:** `src/main/java/dev/vapee/core/activity/blackjack/BlackjackSession.java#getCurrentShoe/#getDealerHand/#getPlayerRounds/#getPlayerRound`;
`BlackjackPlayerRound.java#getHand`; `card/BlackjackHand.java#add`, `card/BlackjackShoe.java#draw`;
`presentation/BlackjackInventoryService.java`, `presentation/BlackjackWorldViewService.java` consumers.

**Problem:** Read-facing access returns mutable hand/round/shoe objects. Map.copyOf protects the
map structure, not its round values. Presentation currently only reads but can mutate engine
facts without going through the service's current-turn/phase invariants.

**Impact:** Accidental future internal consumer mutation could bypass game rules. This is a
local API boundary issue, not a player exploit or reason to make every domain immutable.

**Recommendation:** Narrow the read API or expose an immutable hand/round view for presentation
while preserving package-level engine mutation. Audit callers before reducing public visibility.

**Do not:** Replace CorePlayer's intentional mutable main-thread aggregate, break existing
callers blindly or introduce a plugin-wide public API framework.

**Suggested phase:** 30B

**Confidence:** High on exposure; no current misbehaving read consumer found.

### PRES-001 — constrained future clan/nametag/glyph extension

**Finding ID:** PRES-001

**Severity:** Info

**Category:** Architecture

**Evidence:** `src/main/java/dev/vapee/core/presentation/PresentationRenderer.java` placeholder set;
`presentation/scoreboard/ScoreboardService.java` per-player owned boards;
`presentation/tablist/TablistService.java`; `visibility/VisibilityService.java` plugin hide pairs;
`clan/ClanService.java` read API; `VapeeCore.java` current presentation constructor.

**Problem:** Clan tags, overhead nametags and glyphs are future work, not current promised
features. Presentation currently has no Clan dependency/placeholder. Personal scoreboards
mean a main-scoreboard-only team plan cannot cover the real viewer boards.

**Impact:** A naive later extension could lose visibility or foreign-scoreboard compatibility.
No present correctness defect is assigned to the absence of these features.

**Recommendation:** Plan a small literal clan-tag read/supplier seam and viewer-aware team
ownership compatible with owned and foreign boards; explicitly define neutral/missing-clan
behavior and hide/show interactions. Glyphs remain optional future resource-pack work.

**Do not:** Use main-scoreboard-only teams, NMS/packet stack by default, reverse Clan→Presentation
dependency, or add clan/rank persistence/feature content during 30A.

**Suggested phase:** 30F

**Confidence:** High on current constraints; extension design is an option for later review.

### PRES-002 — tablist cleanup has no field-level ownership check

**Finding ID:** PRES-002

**Severity:** Low

**Category:** API

**Evidence:** `src/main/java/dev/vapee/core/presentation/tablist/TablistService.java#updatePlayer/#removePlayer`,
`modifiedPlayers`; contrast `presentation/scoreboard/ScoreboardService.java` board identity guard.

**Problem:** A tracked player's tablist name is reset to null and header/footer to empty on
remove, without comparing last owned values or restoring a previous owner value.

**Impact:** Conditional multi-plugin interference if another plugin changed those fields while
VapeeCore still tracks that player. Exclusive current presentation use works; interoperability
has not been proven by smoke.

**Recommendation:** Define field ownership/coexistence explicitly. Where the API permits,
restore only if the field still matches the last VapeeCore write, preserving a previous value.
Test interleaved writes/remove and config-disabled transitions.

**Do not:** Assume all tab fields belong to VapeeCore forever, implement a global tab plugin
arbitrator or conflate this with scoreboard team architecture.

**Suggested phase:** 30F

**Confidence:** High on reset behavior; external-plugin occurrence is conditional.

### LIFE-002 — bulk resource release lacks per-resource isolation

**Finding ID:** LIFE-002

**Severity:** Medium

**Category:** Lifecycle

**Evidence:** `src/main/java/dev/vapee/core/worlddisplay/WorldDisplayService.java#remove/#removeOwner/#cleanup`;
`seat/SeatService.java#release/#releaseOwner/#cleanup`;
`activity/blackjack/presentation/BlackjackPreviewService.java#shutdown`;
`activity/blackjack/table/BlackjackSeatService.java#shutdown`.

**Problem:** Bulk forEach release stops on one external entity remove/cancel failure. Some
individual paths remove ownership entries before the external operation, so retry/reconciliation
also loses knowledge of that failed resource. ModuleManager continuing to the next module
does not finish the remainder of this owner's resources.

**Impact:** Conditional incomplete release of other live entities/seats/previews and stale
technical state. No ordinary-world leak or periodic accumulation is inferred.

**Recommendation:** Attempt every owned release, report failures with key/entity context, and
use finally for pure registry cleanup while retaining enough failed-resource information for
the chosen reconciliation policy. Add one-fails/others-succeed fault cases.

**Do not:** Remove all foreign entities, silently swallow failures, or add a recurring full-world
scan as the primary fix. GUI close isolation is separately owned by GUI-002.

**Suggested phase:** 33

**Confidence:** High on conditional control flow; normal shutdown verified separately.

### PERF-001 — replacement retries sleep on synchronous admin path

**Finding ID:** PERF-001

**Severity:** Low

**Category:** Performance

**Evidence:** `src/main/java/dev/vapee/core/lobby/warp/WarpConfig.java#replaceConfiguration/#waitBeforeReplaceRetry`;
`activity/blackjack/table/BlackjackTableConfig.java` same methods; calling setup/admin commands.

**Problem:** Failed replacement retries call Thread.sleep(25 × attempt), attempts 1–4:
25+50+75+100 = 250 ms of deliberate delay, in addition to I/O, on the main-thread path.

**Impact:** A locked/failing Windows file can stall several ticks during an admin save. It is
not a successful hot-path sleep, measured normal lag or unbounded retry loop.

**Recommendation:** Review bounded retry/failure policy with filesystem fault tests and timings;
prefer controlled failure or a carefully designed persistence handoff if justified. Preserve
save-before-state-swap and main-thread mutation ownership.

**Do not:** Move the whole Warp/Blackjack service or Bukkit calls to async, create per-command
thread pools, or promise atomic final copy. Durability policy belongs to phase 31.

**Suggested phase:** 33

**Confidence:** High on delay bound/call path; real-world frequency not measured.

### TEST-001 — coordinator rollback needs execution coverage

**Finding ID:** TEST-001

**Severity:** Medium

**Category:** Testing

**Evidence:** `src/main/java/dev/vapee/core/reload/ReloadService.java#executeReload/#rollback`;
`src/test/java/dev/vapee/core/command/CoreCommandHarness.java` reload-result seam;
test-source search found no direct `new ReloadService`/ReloadService.class or coordinator
failure-state execution cases. Participant config/lifecycle tests exist.

**Problem:** The actual cross-participant prepare/apply/failed-plan rollback/reverse rollback
and rollback-incomplete behavior lacks a dedicated executable harness. Successful smoke and
stubbed command status replies cannot prove these failure paths.

**Impact:** A future change to the coordinator may pass existing checks while leaving inconsistent
runtime state after failure. No current algorithm defect is inferred from missing coverage.

**Recommendation:** A small real coordinator harness using deterministic participants/plans:
prepare failure with zero applies, failed apply's rollback, reverse order, continued rollback
after a rollback exception, ALREADY_RUNNING reentrancy and running reset after failure.

**Do not:** Add full server automation for these pure boundaries, rewrite ReloadService, or
assert private source strings instead of the observable sequence/results.

**Suggested phase:** 34

**Confidence:** High on source inventory and current coverage gap.

### TEST-002 — standard build omits executable harness execution

**Finding ID:** TEST-002

**Severity:** Medium

**Category:** Testing

**Evidence:** `pom.xml`; every main-based `src/test/java/**/*Harness.java`;
`scripts/build-and-deploy.ps1`; `docs/DEVELOPER_GUIDE.md#Build und Tests`.

**Problem:** Maven clean package compiles test sources but its standard test lifecycle does not
launch the 101 custom main harnesses. The three tracked dev scripts contain no full-harness
runner, and the guide lists classes without a current complete execution recipe. Prior reports
correctly show separate runs; this audit likewise executes each explicitly.

**Impact:** A normal successful build/deploy can omit behavioral regression entirely. This is a
workflow gap, not a false claim that Maven failed to compile tests or that this audit skipped them.

**Recommendation:** A small explicit tracked runner/verification command that builds classpath,
launches each real main class separately, records counts, and exits nonzero on failure/missing
results. Document that command and make it part of the chosen regression workflow.

**Do not:** Convert the suite wholesale to a test framework, count support fixtures as skipped
tests or reduce the current checks to gain a green default build.

**Suggested phase:** 34

**Confidence:** High; actual baseline build and separate execution process inspected.

### DOC-001 — current guide overview has localized drift

**Finding ID:** DOC-001

**Severity:** Low

**Category:** Documentation

**Evidence:** `docs/DEVELOPER_GUIDE.md` line 5 blanket claim Java knows no rank names;
line 207 Quest/Daily constructor dependency prose omits MessageService; later Utility paragraph
also broadly says Builder/Moderator/Admin are unknown; Build und Tests list omits later harnesses.
Compare `rank/staff/StaffHierarchyConfig.java` safe defaults and `VapeeCore.java` constructors.

**Problem:** Current overview prose overgeneralizes the data-driven rank presentation rule:
the deliberately explicit staff hierarchy defaults are the exception. The module prose and
incomplete harness list lag later documented sections and actual wiring.

**Impact:** A new developer can incorrectly remove safe hierarchy defaults or miss verification
classes/dependencies. README/PERMISSIONS current 27/6/37/50/15 counts are correct.

**Recommendation:** Qualify overview to presentation vs explicit staff protection, update exact
constructor dependency prose and link the current complete runner/list. Preserve historical
reports' dated counts, implementations and future plans as historical evidence.

**Do not:** Rewrite past phases as though they already had 37 roots or treat every old count as
current drift. This audit intentionally leaves all those documents untouched.

**Suggested phase:** 34

**Confidence:** High; concrete contradictory current passages and code compared.

### LEGACY-001 — historical commented harness body in active source

**Finding ID:** LEGACY-001

**Severity:** Low

**Category:** Duplication

**Evidence:** `src/test/java/dev/vapee/core/activity/blackjack/BlackjackHarness.java`, lines
47–733 block comment explicitly labeled previous Phase-15 harness; active main begins at 742.

**Problem:** 687 physical lines of excluded historic imports, main, tests and helpers remain
inside the current harness source. Text-based scenario/main searches can accidentally attribute
them to current coverage. The active replacement below remains functional.

**Impact:** Local reading/search noise and misleading source indexes; no production dead code,
duplicate executed test class or runtime overhead is claimed. Git already preserves history.

**Recommendation:** After verifying active boundaries/callers, remove this commented archive
from the active test file or replace it with a concise historical commit reference. Retain all
compiled current tests and independently compare actual check totals.

**Do not:** Remove active compatibility constructors, PlaytimeFormatter wrapper, legacy table
schema/tag cleanup, reserved nodes or generic future quest keys just because they look old.

**Suggested phase:** 30E

**Confidence:** High; exact excluded comment boundaries and active main verified.

## 17. Duplication map — mandatory A / B / C distinction

`A` = worthwhile shared infrastructure (harmful maintenance repetition, not automatically a
functional bug). `B` = intentional local repetition retaining readability. `C` = domain-specific
differences that must not be generalized. Low A findings need cost/benefit, not compulsory frameworks.

| Type / pattern | Exact occurrences (methods) | Actual differences | Smallest direction / IDs |
|---|---|---|---|
| A: safe literal item/meta rendering | FriendMenu.renderItem/uiText; ClanMenu.render/text; SettingsMenu.renderItem/uiText; VisibilitySettingsMenu.renderItem; VisiblePlayersMenu.renderItem; NavigatorMenu.renderItem; DailyQuestMenu.renderItem | titles, colors, lore, status rows and gestures differ | tiny UiItemFactory preserving literal/nonitalic semantics; GUI-001 |
| A: page count/clamp/content slice | FriendMenu.open/pageCount; ClanMenu.open/pageCount; VisiblePlayersMenu.open; NavigatorMenu.open; DailyQuestMenu.open | domain candidate sorting/views/visible-only filtering differ | pure size/page/range helper only; GUI-001 |
| A: registration rollback bookkeeping | UtilityModule.enable/registerCommand; ModerationModule.enable; DailyQuestModule.enable; Friend/Clan/Settings module registration | local published fields and rollback hooks differ; Utility lacks catch | explicit helper/list of owned hooks where useful, no reflection; LIFE-001 |
| A: default resource and scalar readers | ChatConfig, PresentationConfig, PrivateMessageConfig, LobbyConfig, ConfigService, DailyQuestConfig methods in CONFIG-001 | paths/defaults/fatal-vs-tolerant parsing differ | small caller-controlled readers/creation; CONFIG-001 |
| A: utility suggestions/target feedback | Feed/Heal.resolveTarget/playerNames; Fly/Speed/GameMode.playerNames | permissions/arity/activity/native flight differ | pure filter/sort and narrow optional-target helper; CMD-001 |
| A: safe known name-or-UUID argument | FriendMessages.commandArgument; ClanCommand/SettingsCommand.identityCommandArgument; ModerationContext/CoinsCommand target suggestion | each owns permitted candidate universe; Friend checks whitespace explicitly | shared safety predicate/argument formatting; DUP-001 |
| A: atomic/replace/retry save plumbing | WarpConfig.replaceConfiguration/waitBeforeReplaceRetry and BlackjackTableConfig same; FilePlayer/Friend/Clan/Moderation repository replacement | retries/copy fallback versus AtomicMoveNotSupported-only fallback; schema/errors differ | phase 31 narrow reviewed file replacement policy, not universal repository; PERF-001 flags sleep only |
| B: explicit permission/sender/arity gates | all thin commands; shared moderation base; GUI action listeners | distinct source grammar/capability order | keep visible local gates; no broad abstract command |
| B: loaded-player checks at event entry | Player/Presence/Quest/OnlineReward/Lobby/Navigator listeners/services | different priority and lifecycle ownership | intentional boundary validation, not a universal context object |
| B: safe Component.text/unparsed calls | FriendMessages, ClanMessages, ModerationComponents, help renderer, PM/Quest/Presence | local message semantics/recipient policies | preserve literal insertion; share only actual formatting primitive |
| C: friendship versus clan authorization | FriendService accept/remove; ClanService invite/kick/transfer/disband | pair reciprocity/privacy vs role/membership/owner constraints | do not genericize social mutations or relation repository |
| C: immediate versus buffered saves | SettingsService/EconomyService/SocialService vs RewardService/QuestService | rollback on immediate failure vs retained runtime grant/dirty retry | preserve domain transaction policy and whole aggregate |
| C: YAML validation | FileModerationRepository exact keys/schema vs FilePlayerRepository optional fields vs Warp/Blackjack per-entry metadata | strict historic facts vs optional preference recovery vs independent admin entries | preserve safety differences; phase 31 explicit migration policies |
| C: teleport/world access | Utility TeleportCommand/Parser vs LobbyService vs WarpService/NavigatorAccessPolicy | explicit source/capability/activity vs spawn vs current visible destination | no universal teleport policy/domain |
| C: text/time rendering | Moderation UTC historic Instants/durations, Profile server-local dates, Playtime compact ticks, Daily zoned cycle boundary | different units/timezones/domain facts | share only proven same formatting; no arbitrary date utility rewrite |
| C: GUI gestures and close/open lifecycle | Friend/Clan vs Settings/Navigator/Quest/Invsee | shift mutation vs controls, real top checks, open cancellation, strict/bottom drag | helper must not erase these checks; GUI-001/002 separate |

## 18. GUI duplication map and extraction constraints

The seven feature menus have ItemSpec/renderer seams for testability. Do not replace these seams
with a reflection-configured UI engine. Safe shared item creation is separate from cloning a
live player's Invsee snapshot, PDC-marked lobby items and state-driven Blackjack hotbar items.
Status panes in Settings/Visibility use explicit icons and saved booleans; Quest status is a
three-state reward lifecycle. Colors may share primitives, but those domains must not merge.

Friend/Clan/VisiblePlayers/Navigator/Quest use 45 content slots and bottom controls. Differences
include Friend/Clan views, navigator visibility/order, quest sync and absent next pages. Reuse
page arithmetic, not candidate lists or slot→target authority. Common close/back/refresh items
are safe candidates only with identical output. Session validation extraction should retain
exact bound instance, owner, active map, actual current top where applicable, late current
domain facts and failed-open publication behavior. No item metadata parsing as authority.

## 19. Lifecycle / ownership map and error handling

| Owner | Resources | Exit/failure paths | Assessment |
|---|---|---|---|
| ModuleManager | successfully enabled module list | enable rollback; per-module reverse shutdown | good explicit manager; failing module must clean its local candidates |
| Config/reload participants | typed candidate/runtime snapshots and Presentation task | prepare no publish, failed-plan/reverse rollback, running finally | good design; TEST-001 coverage gap |
| Player | loaded UUID/name index and current CorePlayer | save before unload; quit failure retains cache; disable attempts saves | deliberate retry and controlled error; phase 31 recovery limits |
| Moderation | snapshot/projection, seven command generations, login/mute listeners | local partial-install rollback; invalidate callbacks/remove hooks/clear projection | strong ownership, no extra shutdown rewrite; MOD-001 freshness |
| Friend/Clan/Settings | own command/listener/menu maps | exact-instance close/quit; module listener deregistration | GUI-002 fault isolation uneven |
| Reward/Quest/Online/Daily | five task types counted above, dirty UUIDs, producer samples/definitions | cancel, final process/flush, listener removal, references clear | healthy batching and per-player retry; no duplicate persistent balance |
| Presence/Visibility | online UUIDs, relationship subscriptions, plugin hide pairs | unsubscribes, per-pair restore, clear | event-driven; no visibility polling/cache of group level |
| Utility | command pairs, listeners, Invsee views, managed flight/speed UUIDs | normal disable resets movement/clears hooks; join normalization | LIFE-001 partial-enable path distinct from healthy normal path |
| Activity/Blackjack | memberships/venues, seats, round tasks, inventory transfer, outcome callback | hook rollback; close/reset generation; quit/death/world exit; callback no-op on shutdown | strong FSM; cleanup failure edges LIFE-002 |
| Seat/WorldDisplay | owner/key/player/entity UUID indexes | marked stale startup cleanup, mount/spawn rollback, release owner/shutdown | no normal polling/leak; DISPLAY-001 and LIFE-002 |
| Preview | admin→preview markers/task owner | replace, timeout, quit, shutdown | bounded nonpersistent resources; LIFE-002 |
| Presentation | personal scoreboard handles/tab fields/task | reload task swap/rollback, player removal, foreign board comparison | PRES-002 tab ownership weaker than scoreboard ownership |
| Navigator | active inventory map and three experience listeners | world leave, quit, exact close, per-view close isolation/final clear, partial enable cleanup | good reference pattern for GUI-002 |

Expected denials return domain/status results and controlled chat; persistence/framework
exceptions get server-side context/cause. Exception swallowing is not used to pretend a failed
save succeeded. Moderation external disconnect/notice failure after save does not roll back an
already committed record. Friend/Clan/Visibility callbacks likewise cannot undo a committed
domain mutation. Reporter/send failures do not reset earned coins/progress. These are healthy
transaction boundaries, not generic catch-and-rollback opportunities.

Logs generally include affected UUID/action/file and cause, while users get concise errors.
Ordinary hierarchy denial and repeat unsuccessful pending-reward retries do not emit routine
spam. Persistent per-player Presentation/save faults may repeat on their shared task intervals;
this is an operational profiling/rate-control consideration, not an observed log storm or
new finding. Do not suppress the first diagnostic or expose stacktraces/group internals in UI.

## 20. Security observations

MOD-001 is the only High finding; no Critical issue was established. Capability, sender,
exact target UUID/name, self/other policy, staff hierarchy and state guards remain separate.
Console hierarchy exemption is deliberate only for actual ConsoleCommandSender in supported
forms. OP, bypass, visibility.staff, rank track and LP weight do not become hierarchy exceptions.
Ping/Profile/Coins GET are deliberate nonmutation reads; utility moved SOURCE is protected,
destination is not an administrative target. Completion is advisory; execution is authoritative.

Inventory action source is server-held UUID/ID, not client text. Unsupported number-key,
double-click, creative/offhand/drop and shift transfer paths are canceled. Invsee never
implements the reserved mutation permission. Physical Blackjack play is intentionally public;
its admin command gate does not imply a gameplay use capability. NORMAL/BUILD/Activity inventory
ownership is exclusive. No fabricated staff ban or live player punishment was used for smoke.

All player-controlled display text inspected uses literal components or safe placeholders.
Admin MiniMessage/selected LP meta formatting is a separate trust boundary. File paths use
UUID filenames or fixed plugin filenames, while warp/table/quest/clan keys are validated domain
identifiers. Reasons/amounts/durations/pages have explicit empty/number/overflow handling.
No concrete path traversal or MiniMessage player-input execution defect was found.

## 21. Performance observations

No runtime benchmark/load client was run, so no TPS or memory headroom is asserted. Most work
is bounded by online players, domain entries, active tables or dirty UUIDs. Visibility updates
are event-driven O(n) for one viewer/person; initial synchronize-all can perform O(n²) pair work
because all viewers need pair decisions. This is explicit startup/event work, not a discovered
recurring quadratic poller. Pair refresh after Friend/Ignore is scoped to two players.

Presentation re-renders and writes tab components at the configured default 20-tick interval,
with scoreboard delta application and no disk access. Measure actual component/task costs
before introducing caches. OnlineReward is one shared 20-tick O(n) statistic pass; Quest samples
and dirty flush every 100 ticks. Fairness randomization is six bounded decks, not dynamic player
win-rate adjustment. Seat/Table block indexes are O(1); display refresh is event-driven.

Disk I/O is synchronous at intentional persistence boundaries. Known offline profile/identity/
balance reads can read a player file; online loaded-player paths use current aggregates. Async
chat/mute paths do not perform repository saves/scans. Moderation, Friend and Clan whole-snapshot
saves/history sorting grow with their actual records; no unbounded-frequency hot path was
demonstrated. Large histories/file durability belong to measured phase 31/33 work. PERF-001
is a concrete fault-path delay, not a reason to make all Bukkit/domain code asynchronous.

## 22. Test gaps and required regressions

- MOD-001: promote the newly loaded target after future completion but before main resume;
  verify no save/kick/notice and current group/config/session/capability rules.
- SEC-001: explicit negative/revoked feature permission, retained views and direct settings
  hotbar open; unchanged server-bound ownership and relation/privacy rollback.
- DISPLAY-001: alive entity with teleport rejected versus genuinely missing entity, replacement
  and owner cleanup; no forgotten live resource.
- GUI-002/LIFE-002: one lookup/close/remove failure alongside another healthy resource; all
  attempts happen, contextual logging, clear/reconciliation policy, foreign resources preserved.
- LIFE-001: real partial command/listener installation boundaries, cleanup/retry/getter state.
- PERSIST-001/002: temp write/replace interruption contract, future schema refusal/preservation,
  backups/migration and malformed optional/core distinction in phase 31.
- TEST-001: real coordinator failed prepare/apply and rollback-incomplete/reentrant execution.
- PRES-002/PRES-001: interleaved foreign tab writes, personal/foreign viewer boards, hide/show
  and nametag integration, then real multiplayer client verification when features are approved.

These gaps describe evidence limits and future acceptance tests. They do not turn all proxy
tests into worthless tests or authorize fixes in 30A. Live client test: not performed.

## 23. Documentation and scripts audit

| File | Role reviewed | Assessment |
|---|---|---|
| README.md | current Phase 29 overview, prerequisites/build/dev workflow, architectural feature summaries | current 27/6/37 and phase links agree; prior chapters clearly historical |
| docs/DEVELOPER_GUIDE.md | current where-to-change/default/module/dependency/state/UI/persistence/command/test guidance | detailed useful map; localized DOC-001 overview/list drift |
| docs/PERMISSIONS.md | current roots/defaults/edges, individual capabilities, hierarchy and operator Vanilla lockdown | current 37/50/15 contract matches; external LP parent advice not automatic plugin authority |
| docs/FORMATTING.md | MiniMessage versus rank color/legacy/meta modes, rank/chat/presentation/reward placeholders, live/default files | actual placeholder/format trust distinctions agree; examples are operator configuration, not shipped defaults |
| docs/COMMAND_AUDIT.md | dated Phase 23 before/after command matrix and verification | retain historical 25-module/no-moderation context; not a current unresolved bug list |
| docs/MODERATION_FOUNDATION.md | Phase 24 domain/schema/atomic/thread foundations | strict schema/COW contract preserved; later enforcement intentionally absent then |
| docs/MODERATION_TOOLS.md | Phase 25A command/ban enforcement/order/failure limits | save-before-disconnect and known identity semantics preserved; hierarchy added later |
| docs/MODERATION_MUTES.md | Phase 25B async projection/chat/outgoing PM ownership and tests | async boundaries still explicit; historical no-hierarchy is dated, not current claim |
| docs/STAFF_HIERARCHY.md | Phase 26A group ordering, capability split and offline load handoff | documents original captured target snapshot; MOD-001 is new full-audit assessment of freshness window |
| docs/PERMISSION_HARDENING.md | Phase 26B loaded utility/economy hierarchy and inheritance | current core behavior preserved; historic 36/49 is correct for that phase |
| docs/NOTIFICATIONS_PRESENCE.md | Phase 27 transitions/recipient policy/opt-in and event priorities | matches current owner/no queue/no scheduler; dated 93 harness count retained |
| docs/LOBBY_NAVIGATION.md | Phase 28 metadata/current target/access/close lifecycle | matches navigator; dated 36/49/96 harnesses are historical |
| docs/QUEST_COMPLETION.md | Phase 29 reporter/playtime/outcome/GUI/config/reload and full 101-harness evidence | current contract matches; not a substitute for this audit's new execution |
| scripts/build-and-deploy.ps1 | managed clean-stop, Maven result, temp deployment/hash/failure handling | build failure leaves previous deployed jar; no automatic restart; TEST-002 separate regression runner absent |
| scripts/start-dev-server.ps1 | resolve Java/Paper, marked process, console relay, stop request/finally | explicit managed owner, 4 GB heap; no verified Java-version enforcement merely from executable name; controlled stop, no forced kill |
| scripts/stop-dev-server.ps1 | PID/marker detection, unmanaged Paper refusal, bounded stop wait | no forced kill; unmanaged Paper requires own console stop; stale/reused PID can cause conservative block, not arbitrary kill |
| .run/Build and Deploy VapeeCore.run.xml; .run/Start VapeeCore Dev Server.run.xml | IntelliJ PowerShell 7 entry points, project working directory | resolve the corresponding tracked scripts; no extra Java/build behavior |
| .gitignore; pom.xml | ignored build/server/IDE artifacts; Java/Paper/LP scopes/resource filtering | audit scratch is excluded; Paper/LP remain provided; custom harness execution is separate |

Historical counts and Known Issues were evaluated in their own dated scope; no blanket rewrite
is proposed. The current complete source inventory and fresh verification are this report's
authority. Documentation-only reports do not prove live-client behavior. Scripts were inspected;
the final smoke uses an explicit Java 21 console process with the established dev-server files.
A pending fake server/remote deploy/production install is not part of this phase.

## 24. Legacy, naming and public API review

LEGACY-001 is the confirmed excluded archive in an active test file. No production class is
labeled unused merely because a name search was empty. Constructors, tests, descriptors,
listeners, adapters, compatibility aliases and serialization consumers were checked before
suggesting visibility/removal. The following remain intentional:

- presentation.PlaytimeFormatter delegates to the shared formatter and is still consumed/tested.
- `<group>` aliases `<rank_id>` for old operator templates.
- `lobby.build` permission delegates to `utility.build`; invsee.modify is reserved and inactive.
- Complete legacy Blackjack interaction/flat seat/display schemas load without eager rewrite;
  old marked blackjack seats are cleaned, foreign stands are preserved.
- Compatibility WarpPoint constructor provides navigator defaults; hidden is only Compass visibility.
- Generic future quest keys/unknown valid IDs remain inert or pending, not dead feature branches.
- Visibility game-participant option is deliberately unavailable with production predicate false;
  it must not fabricate activity membership or mutate an unavailable setting.

Feature package naming generally matches ownership. The large old Blackjack test comment is
more confusing than production package structure. API-001 is a specific read/mutation seam;
CorePlayer/PlayerSettings mutability is intentional in main-thread aggregate ownership.
No broad rename/repackage/public-to-private sweep is justified.

## 25. Recommended Phase 30B — Architecture Refactor

**IDs: API-001 only.** Narrow concrete Blackjack presentation reads so consumers cannot mutate
engine hands/shoe/rounds through read methods. Verify all production/test callers and preserve
the engine FSM. Existing module DAG, repository domains, reporter, rank/capability separation
and constructor injection remain. No major dependency cycle/ownership restructure was found.
There is no requirement to manufacture a broad refactor if review rejects the small API scope.
GUI duplication → 30C; registration/config → 30D; command helpers → 30E; persistence/security/
resource failures → 31/32/33. 30B is not a general cleanup bucket.

## 26. Recommended Phase 30C — GUI / Inventory / Item Cleanup

**IDs: GUI-001, GUI-002.** Tiny safe UiItemFactory/page helpers and exception-isolated owned-view
closure. Optional session validation only after preserving all real differences from section 8.
Acceptance: existing layout/text/gestures, owner/holder/instance/active/current-top rules,
no stale mutation, current target resolution, canceled open/teleport behavior, no foreign close
and independently fault-isolated cleanup. No giant menu framework, DSL or centralized domain
actions. SEC-001's capability behavior is separately owned by security phase 32.

## 27. Recommended Phase 30D — Config / Registration / Module Cleanup

**IDs: LIFE-001, CONFIG-001.** Explicit local hook acquisition/rollback for Utility and a small
reviewed registration helper where repeated bookkeeping warrants it. Narrow absent-file/scalar
reader helpers with contextual validation parity. Existing six reload participants/order and
typed snapshots remain explicit. No reflection registration, DI container, generic schema mapper,
new reload participant or runtime/resource default change as incidental cleanup. Atomic write,
backup/version/migration changes are phase 31, not bootstrap cleanup.

## 28. Recommended Phase 30E — Duplication & Maintainability Cleanup

**IDs: CMD-001, DUP-001, LEGACY-001.** Share exact safe suggestion/filtering primitives and
optional-target plumbing only where existing grammar/authority is identical; remove the
excluded historical test block after active coverage verification. Keep staff/state gates,
known versus online target universes and domain authorization local. No universal command
framework, repository, teleport or social relation abstraction. Preserve actual check totals
and all intentional compatibility surfaces from section 24.

## 29. Recommended Phase 30F — Presentation & Nametag Polish

**IDs: PRES-001, PRES-002.** Define literal Clan read/placeholder ownership, optional clan tab
rendering and tab field restoration/coexistence. Plan nametags for the actual viewer's owned
or foreign scoreboard and Visibility hide/show lifecycle; include multiplayer/client acceptance
when implemented. Do not propose main-scoreboard-only teams. Resource-pack rank glyphs remain
optional future presentation input, not new mandatory dependency or rank authority.
Phase 30A documents constraints and does not add any placeholder/team/glyph or Clan dependency.

## 30. Items deferred to Phase 31+

| Phase | Finding IDs | Concrete scope / acceptance boundary |
|---|---|---|
| 31 Persistence & Migration Hardening | PERSIST-001, PERSIST-002 | protected lobby save; player version/forward/downgrade/unknown fields; explicit backup/recovery and atomic fallback fault policy; no database by default |
| 32 Security & Exploit Audit | MOD-001, SEC-001 | current target authority at resume, capability at GUI entry/action; precise race/denial regressions; preserve COW, UUID/session and no async Bukkit |
| 33 Performance & Reliability | DISPLAY-001, LIFE-002, PERF-001 | live-false display ownership, independent resource cleanup/reconciliation, bounded file-retry main-thread timings; profiling before caches/pools |
| 34 Full Regression & Documentation Audit | TEST-001, TEST-002, DOC-001 | actual coordinator failure harness; explicit complete runner; current guide fixes; integrated verification after accepted prior phases |
| Later feature review | no additional counted finding | activity visibility integration, staff teleport consent, AFK/queues/pay/new games/new quest producers and glyph content require their own approved product scope |

Cross-feature regression belongs with every implementing phase, followed by 34's integrated
review. Deferred persistence crash/recovery and load profiling observations are not artificially
promoted into extra duplicate findings. There is no new roadmap permission to implement them here.

## 31. Overall assessment

| Dimension | Rating | Evidence and limit |
|---|---|---|
| Architecture health | Good | explicit acyclic 27-module constructor graph, narrow domain/adapters; API-001 localized boundary |
| Correctness confidence | Good | broad deterministic harnesses plus reproduced audit edge cases; failure/client gaps prevent Strong |
| Security posture | Mixed | capability/hierarchy/UUID/GUI boundaries largely explicit; confirmed High MOD-001 and limited SEC-001 |
| Persistence robustness | Mixed | strict facts/COW/temp replacement and rollback strong; direct lobby save, unversioned player unknown fields and no complete crash recovery |
| Lifecycle robustness | Mixed | reverse cleanup/generation checks/ownership strong on normal path; partial-enable/bulk-exception/false-display edges |
| GUI maintainability | Mixed | focused clear menus and strong binding; repeated plumbing and uneven close/capability checks |
| Command maintainability | Good | thin explicit executors, safe help, exact semantic boundaries; low-risk helper repetition |
| Configuration maintainability | Good | typed validated states/operator defaults/reload plans; narrow repetition and policy complexity |
| Test maturity | Good | 101 separately executable harnesses, real domain/adapter/concurrency tests; runner/coordinator/live-client gaps |
| Documentation quality | Good | extensive ownership/config/operator/history maps; localized current guide drift |

No dimension is assigned High Risk from unmeasured scale or absent feature work. The repository
supports focused follow-up; its explicit ownership is more valuable than a broad rewrite.
Security correctness gets priority over cosmetic consolidation. The audit identifies risks
without changing behavior or weakening working contracts.

## 32. Verification method and reproducible commands

The environment uses Java **21.0.12.1**, Maven bundled with IntelliJ IDEA **2026.2.2**, and the
existing local Maven cache. PowerShell executable paths are explicit because mvn need not be
on PATH. Initial classpath/compile command:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package dependency:build-classpath '-Dmdep.outputFile=target/harness-classpath.txt'
```

Each real harness class is independently launched in its own Java process, in sorted source
path order, with this command pattern (not a single synthetic test suite count):

```powershell
$taskClasspath = 'target\classes;target\test-classes;' + (Get-Content -LiteralPath 'target/harness-classpath.txt' -Raw).Trim()
& 'C:\Program Files\Java\jdk-21.0.12.1\bin\java.exe' -cp $taskClasspath <fully.qualified.HarnessClass>
```

The local audit runner records each exit/check count and fails if any exit is nonzero or a
count is absent. It is ignored verification tooling, not a tracked implementation of TEST-002.
The required final build is independently run after the harnesses:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

The established Paper environment is `dev-server/paper-1.21.11-132.jar` with
`dev-server/plugins/LuckPerms-Bukkit-5.5.84.jar`. The built JAR is copied into its existing
plugins directory and launched with the pinned Java 21, 1 GB initial/2 GB maximum heap,
`-jar paper-1.21.11-132.jar --nogui`. The console checks version, LP provider, zero players,
core status/help/version, reload and plugin help registration, then clean stop. Existing live
configuration hashes are compared around this run. No fabricated sanctions or clients.

## 33. Verification results and docs-only git boundary

All measurements below are fresh Phase 30A executions, after the source review/report draft.
Prior phase totals were expectations only until independently reproduced here.

| Check | Actual result |
|---|---|
| Full main-harness regression | 101 independently started processes; 14,705 checks; all exits 0; zero failures; zero skipped executable harnesses |
| Support sources | Ten fixture/support files, not executable/skipped harnesses |
| Harness command | pinned Java 21 command pattern in section 32; local runner `dev-server/.vapeecore-dev/phase30a-harnesses.ps1`; every FQCN and result in Appendix B |
| Final Maven command | explicit bundled mvn, offline local repo, `clean package` exactly as section 32 |
| Final Maven result | BUILD SUCCESS; exit 0; 19.031 seconds; finished 2026-10-04T15:46:31+02:00 |
| Final JAR | `Z:\NEXTCLOUD\Programmierung\JAVA\MINECRAFT\VapeeCore\target\vapeecore-1.0-SNAPSHOT.jar` |
| JAR byte size | 1,050,292 |
| JAR SHA-256 | `465F22E0BFEB5037A78EFC467C3798F99C469653AEDE8170FD98864D380FC89C` |
| Deployed smoke JAR | `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar`; same bytes/hash |
| Provider/server API classes shaded | Zero net/luckperms, org/bukkit or io/papermc class entries; provided contract preserved |
| Java | Oracle Java 21.0.12.1, VM 21.0.12.1+1-LTS-4 |
| Paper | 1.21.11 build 132, c5eb079, API 1.21.11-R0.1-SNAPSHOT |
| LuckPerms | 5.5.84, connected; separate required plugin |
| Real startup | Started 15:48:28, all 27 modules enabled in registered order, server Done 15:48:50 |
| Core/version/help/list | Running; 27 active modules; LuckPerms Connected; correct plugin/Paper/Java; zero online/loaded players |
| Command registration | `bukkit:help VapeeCore` lists all 37 exact descriptor roots; no missing roots; aliases not double-counted |
| `/core reload` | all six expected filenames prepared; successful apply at 15:49:34, 23 ms; success feedback |
| Real shutdown | stop 15:49:52; all 27 modules disabled in exact reverse order; process exit 0; no server left running |
| Runtime smoke errors | Zero ERROR/SEVERE in this complete fresh server log |
| Existing live configurations | Eight pre-existing plugin YAML files retain identical raw hashes after start/reload/stop |
| Moderation/player smoke mutations | No fabricated sanctions/players; absent moderation.yml remained absent; zero players |
| Live client | Live client test: not performed. |
| Raw baseline preservation | All 451 previously tracked files retain their original SHA-256, including every Java/test/resource/POM/historic doc/script |
| Source/resource diffs | Empty; test/POM diffs also empty; only newly added report |
| Whitespace check | `git diff --check` and staged `git diff --cached --check` pass |
| Local commit | one commit above exact baseline, message `Phase 30A - Full Codebase Audit`; only this report staged; no push |
| Final git contract | clean `git status --short`, single-commit baseline range and docs-only commit are checked after commit; final SHA is supplied in the completion response |

No broken-baseline anomaly was found. Expected compiler annotation-processing/deprecated-API
notices did not fail either clean build. Paper reports that the requested 1.21.11 line is
behind the newer Minecraft release line; spark uses its Windows Java fallback. These are
existing environment notices, not VapeeCore errors or grounds to change the requested target.
Successful smoke proves boot/registration/reload/normal shutdown, not live GUI/chat/nametag
behavior or fault-injected production cleanup. JAR reproducibility is not assumed from unchanged
source; actual bytes/hash are recorded above.

Local ignored proof files include `phase30a-baseline-build.log`, `phase30a-final-build.log`,
`phase30a-harness-results.json`, per-class logs, `phase30a-paper-smoke.log`,
`phase30a-paper-evidence.json`, `phase30a-artifact.json` and baseline hash comparison under
`dev-server/.vapeecore-dev/`. The durable tracked proof is this report's inventories/results.
The postcommit git SHA/status are delivered separately because a commit cannot embed its own
hash in its content. No verification artifact or development configuration is staged.

## 34. Final conclusion

The modular monolith and explicit domain ownership are worth preserving. Twenty deduplicated
findings support narrowly bounded follow-up phases, with one reproduced High staff-authorization
race and no Critical finding. Ordinary baseline startup/build success does not erase those
edge cases; conditional failure risks do not justify an indiscriminate architecture rewrite.

Only this audit document is committed. No audit finding is implemented, no Phase 30B is started,
and no push occurs. Independent review of this report is the next step.
Live client test: not performed.

## Appendix A. Complete source and descriptor inventory

All paths below are repository-relative and refer to baseline files. File line counts are physical lines, not complexity or coverage metrics.

### src/main/java (311 baseline files)

| Exact path | Lines |
|---|---:|
| `src/main/java/dev/vapee/core/activity/ActivityLeaveReason.java` | 11 |
| `src/main/java/dev/vapee/core/activity/ActivityListener.java` | 57 |
| `src/main/java/dev/vapee/core/activity/ActivityModule.java` | 81 |
| `src/main/java/dev/vapee/core/activity/ActivityResult.java` | 24 |
| `src/main/java/dev/vapee/core/activity/ActivityService.java` | 639 |
| `src/main/java/dev/vapee/core/activity/ActivitySession.java` | 162 |
| `src/main/java/dev/vapee/core/activity/ActivitySessionCreationResult.java` | 37 |
| `src/main/java/dev/vapee/core/activity/ActivityState.java` | 21 |
| `src/main/java/dev/vapee/core/activity/ActivityType.java` | 16 |
| `src/main/java/dev/vapee/core/activity/blackjack/BlackjackActivityType.java` | 41 |
| `src/main/java/dev/vapee/core/activity/blackjack/BlackjackModule.java` | 292 |
| `src/main/java/dev/vapee/core/activity/blackjack/BlackjackOutcome.java` | 33 |
| `src/main/java/dev/vapee/core/activity/blackjack/BlackjackPlayerRound.java` | 53 |
| `src/main/java/dev/vapee/core/activity/blackjack/BlackjackRoundPhase.java` | 8 |
| `src/main/java/dev/vapee/core/activity/blackjack/BlackjackService.java` | 701 |
| `src/main/java/dev/vapee/core/activity/blackjack/BlackjackSession.java` | 208 |
| `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackCard.java` | 15 |
| `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackHand.java` | 68 |
| `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackRank.java` | 33 |
| `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackShoe.java` | 67 |
| `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackSuit.java` | 22 |
| `src/main/java/dev/vapee/core/activity/blackjack/command/BlackjackCommand.java` | 849 |
| `src/main/java/dev/vapee/core/activity/blackjack/interaction/BlackjackTableListener.java` | 123 |
| `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackAction.java` | 25 |
| `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackDisplayGeometry.java` | 209 |
| `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackInventoryService.java` | 195 |
| `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewService.java` | 248 |
| `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackWorldViewService.java` | 297 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackBlockPosition.java` | 25 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackDisplayAnchor.java` | 21 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackSeat.java` | 34 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackSeatService.java` | 197 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableConfig.java` | 456 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableDefinition.java` | 245 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableDraft.java` | 198 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableInteractionMode.java` | 6 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableOperationResult.java` | 42 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableSeatReference.java` | 13 |
| `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableService.java` | 369 |
| `src/main/java/dev/vapee/core/activity/location/ActivityArea.java` | 78 |
| `src/main/java/dev/vapee/core/activity/location/ActivityPosition.java` | 55 |
| `src/main/java/dev/vapee/core/activity/location/ActivityVenue.java` | 36 |
| `src/main/java/dev/vapee/core/activity/player/ActivityParticipant.java` | 13 |
| `src/main/java/dev/vapee/core/chat/ChatListener.java` | 43 |
| `src/main/java/dev/vapee/core/chat/ChatModule.java` | 119 |
| `src/main/java/dev/vapee/core/chat/ChatService.java` | 190 |
| `src/main/java/dev/vapee/core/chat/config/ChatConfig.java` | 184 |
| `src/main/java/dev/vapee/core/clan/Clan.java` | 39 |
| `src/main/java/dev/vapee/core/clan/ClanFileMover.java` | 10 |
| `src/main/java/dev/vapee/core/clan/ClanInvite.java` | 14 |
| `src/main/java/dev/vapee/core/clan/ClanLimits.java` | 27 |
| `src/main/java/dev/vapee/core/clan/ClanMember.java` | 13 |
| `src/main/java/dev/vapee/core/clan/ClanMessages.java` | 111 |
| `src/main/java/dev/vapee/core/clan/ClanModule.java` | 90 |
| `src/main/java/dev/vapee/core/clan/ClanRepository.java` | 6 |
| `src/main/java/dev/vapee/core/clan/ClanRepositoryException.java` | 6 |
| `src/main/java/dev/vapee/core/clan/ClanResult.java` | 23 |
| `src/main/java/dev/vapee/core/clan/ClanRole.java` | 6 |
| `src/main/java/dev/vapee/core/clan/ClanService.java` | 284 |
| `src/main/java/dev/vapee/core/clan/ClanSnapshot.java` | 62 |
| `src/main/java/dev/vapee/core/clan/ClanText.java` | 45 |
| `src/main/java/dev/vapee/core/clan/command/ClanCommand.java` | 446 |
| `src/main/java/dev/vapee/core/clan/FileClanRepository.java` | 265 |
| `src/main/java/dev/vapee/core/clan/gui/ClanMenu.java` | 231 |
| `src/main/java/dev/vapee/core/clan/gui/ClanMenuHolder.java` | 54 |
| `src/main/java/dev/vapee/core/clan/gui/ClanMenuListener.java` | 153 |
| `src/main/java/dev/vapee/core/clan/gui/ClanMenuView.java` | 3 |
| `src/main/java/dev/vapee/core/command/CoreCommand.java` | 342 |
| `src/main/java/dev/vapee/core/command/help/CommandHelpEntry.java` | 42 |
| `src/main/java/dev/vapee/core/command/help/CommandHelpPage.java` | 43 |
| `src/main/java/dev/vapee/core/command/help/CommandHelpRenderer.java` | 107 |
| `src/main/java/dev/vapee/core/command/help/CommandHelpSection.java` | 15 |
| `src/main/java/dev/vapee/core/command/OnlineStaffTargetGuard.java` | 86 |
| `src/main/java/dev/vapee/core/config/ConfigService.java` | 387 |
| `src/main/java/dev/vapee/core/economy/CoinWallet.java` | 33 |
| `src/main/java/dev/vapee/core/economy/command/CoinsCommand.java` | 348 |
| `src/main/java/dev/vapee/core/economy/EconomyModule.java` | 91 |
| `src/main/java/dev/vapee/core/economy/EconomyResult.java` | 8 |
| `src/main/java/dev/vapee/core/economy/EconomyService.java` | 146 |
| `src/main/java/dev/vapee/core/format/PlaytimeFormatter.java` | 21 |
| `src/main/java/dev/vapee/core/friend/command/FriendCommand.java` | 303 |
| `src/main/java/dev/vapee/core/friend/FileFriendRepository.java` | 274 |
| `src/main/java/dev/vapee/core/friend/FriendFileMover.java` | 11 |
| `src/main/java/dev/vapee/core/friend/FriendLimits.java` | 26 |
| `src/main/java/dev/vapee/core/friend/FriendMessages.java` | 110 |
| `src/main/java/dev/vapee/core/friend/FriendModule.java` | 129 |
| `src/main/java/dev/vapee/core/friend/FriendPair.java` | 54 |
| `src/main/java/dev/vapee/core/friend/FriendRelation.java` | 8 |
| `src/main/java/dev/vapee/core/friend/FriendRelationshipListener.java` | 9 |
| `src/main/java/dev/vapee/core/friend/FriendRepository.java` | 8 |
| `src/main/java/dev/vapee/core/friend/FriendRepositoryException.java` | 12 |
| `src/main/java/dev/vapee/core/friend/FriendRequest.java` | 18 |
| `src/main/java/dev/vapee/core/friend/FriendRequestDecision.java` | 7 |
| `src/main/java/dev/vapee/core/friend/FriendRequestPolicy.java` | 18 |
| `src/main/java/dev/vapee/core/friend/FriendResult.java` | 16 |
| `src/main/java/dev/vapee/core/friend/FriendService.java` | 341 |
| `src/main/java/dev/vapee/core/friend/Friendship.java` | 22 |
| `src/main/java/dev/vapee/core/friend/FriendSnapshot.java` | 64 |
| `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java` | 257 |
| `src/main/java/dev/vapee/core/friend/gui/FriendMenuHolder.java` | 70 |
| `src/main/java/dev/vapee/core/friend/gui/FriendMenuListener.java` | 164 |
| `src/main/java/dev/vapee/core/friend/gui/FriendMenuView.java` | 7 |
| `src/main/java/dev/vapee/core/identity/command/ProfileCommand.java` | 141 |
| `src/main/java/dev/vapee/core/identity/IdentityModule.java` | 80 |
| `src/main/java/dev/vapee/core/identity/PlayerIdentity.java` | 21 |
| `src/main/java/dev/vapee/core/identity/PlayerIdentityService.java` | 51 |
| `src/main/java/dev/vapee/core/identity/PlayerLookupResult.java` | 27 |
| `src/main/java/dev/vapee/core/identity/PlayerLookupStatus.java` | 7 |
| `src/main/java/dev/vapee/core/identity/PlayerProfile.java` | 23 |
| `src/main/java/dev/vapee/core/identity/PlayerProfileLookupResult.java` | 15 |
| `src/main/java/dev/vapee/core/identity/PlayerProfileService.java` | 64 |
| `src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java` | 87 |
| `src/main/java/dev/vapee/core/lobby/command/SpawnCommand.java` | 93 |
| `src/main/java/dev/vapee/core/lobby/config/LobbyConfig.java` | 428 |
| `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceListener.java` | 94 |
| `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java` | 162 |
| `src/main/java/dev/vapee/core/lobby/experience/LobbyItemListener.java` | 247 |
| `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorAccessPolicy.java` | 51 |
| `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorInventoryHolder.java` | 63 |
| `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorListener.java` | 144 |
| `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java` | 242 |
| `src/main/java/dev/vapee/core/lobby/item/LobbyItemService.java` | 194 |
| `src/main/java/dev/vapee/core/lobby/item/LobbyItemType.java` | 27 |
| `src/main/java/dev/vapee/core/lobby/LobbyListener.java` | 178 |
| `src/main/java/dev/vapee/core/lobby/LobbyModule.java` | 206 |
| `src/main/java/dev/vapee/core/lobby/LobbyService.java` | 123 |
| `src/main/java/dev/vapee/core/lobby/LobbySpawn.java` | 26 |
| `src/main/java/dev/vapee/core/lobby/message/LobbyMessageService.java` | 75 |
| `src/main/java/dev/vapee/core/lobby/player/LobbyPlayerMode.java` | 7 |
| `src/main/java/dev/vapee/core/lobby/player/LobbyPlayerStateService.java` | 230 |
| `src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java` | 417 |
| `src/main/java/dev/vapee/core/lobby/warp/WarpConfig.java` | 344 |
| `src/main/java/dev/vapee/core/lobby/warp/WarpModule.java` | 86 |
| `src/main/java/dev/vapee/core/lobby/warp/WarpNavigation.java` | 13 |
| `src/main/java/dev/vapee/core/lobby/warp/WarpPoint.java` | 49 |
| `src/main/java/dev/vapee/core/lobby/warp/WarpPosition.java` | 60 |
| `src/main/java/dev/vapee/core/lobby/warp/WarpResult.java` | 14 |
| `src/main/java/dev/vapee/core/lobby/warp/WarpService.java` | 224 |
| `src/main/java/dev/vapee/core/message/MessageService.java` | 57 |
| `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java` | 175 |
| `src/main/java/dev/vapee/core/moderation/command/BanCommand.java` | 47 |
| `src/main/java/dev/vapee/core/moderation/command/HistoryCommand.java` | 57 |
| `src/main/java/dev/vapee/core/moderation/command/KickCommand.java` | 25 |
| `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java` | 130 |
| `src/main/java/dev/vapee/core/moderation/command/ModerationDurationParser.java` | 50 |
| `src/main/java/dev/vapee/core/moderation/command/MuteCommand.java` | 47 |
| `src/main/java/dev/vapee/core/moderation/command/UnbanCommand.java` | 23 |
| `src/main/java/dev/vapee/core/moderation/command/UnmuteCommand.java` | 25 |
| `src/main/java/dev/vapee/core/moderation/command/WarnCommand.java` | 30 |
| `src/main/java/dev/vapee/core/moderation/FileModerationRepository.java` | 216 |
| `src/main/java/dev/vapee/core/moderation/ModerationAction.java` | 9 |
| `src/main/java/dev/vapee/core/moderation/ModerationActor.java` | 24 |
| `src/main/java/dev/vapee/core/moderation/ModerationActorType.java` | 5 |
| `src/main/java/dev/vapee/core/moderation/ModerationComponents.java` | 78 |
| `src/main/java/dev/vapee/core/moderation/ModerationFileMover.java` | 10 |
| `src/main/java/dev/vapee/core/moderation/ModerationLoginListener.java` | 43 |
| `src/main/java/dev/vapee/core/moderation/ModerationModule.java` | 184 |
| `src/main/java/dev/vapee/core/moderation/ModerationMuteChatListener.java` | 59 |
| `src/main/java/dev/vapee/core/moderation/ModerationMuteProjection.java` | 41 |
| `src/main/java/dev/vapee/core/moderation/ModerationRecord.java` | 43 |
| `src/main/java/dev/vapee/core/moderation/ModerationRepository.java` | 6 |
| `src/main/java/dev/vapee/core/moderation/ModerationRepositoryException.java` | 6 |
| `src/main/java/dev/vapee/core/moderation/ModerationResult.java` | 23 |
| `src/main/java/dev/vapee/core/moderation/ModerationRevocation.java` | 13 |
| `src/main/java/dev/vapee/core/moderation/ModerationService.java` | 127 |
| `src/main/java/dev/vapee/core/moderation/ModerationSnapshot.java` | 29 |
| `src/main/java/dev/vapee/core/moderation/ModerationStatus.java` | 5 |
| `src/main/java/dev/vapee/core/moderation/ModerationText.java` | 27 |
| `src/main/java/dev/vapee/core/module/CoreModule.java` | 10 |
| `src/main/java/dev/vapee/core/module/ModuleManager.java` | 101 |
| `src/main/java/dev/vapee/core/onlinereward/OnlineRewardConfig.java` | 72 |
| `src/main/java/dev/vapee/core/onlinereward/OnlineRewardMessageRenderer.java` | 80 |
| `src/main/java/dev/vapee/core/onlinereward/OnlineRewardModule.java` | 133 |
| `src/main/java/dev/vapee/core/onlinereward/OnlineRewardProcessResult.java` | 52 |
| `src/main/java/dev/vapee/core/onlinereward/OnlineRewardProcessStatus.java` | 11 |
| `src/main/java/dev/vapee/core/onlinereward/OnlineRewardProgress.java` | 42 |
| `src/main/java/dev/vapee/core/onlinereward/OnlineRewardService.java` | 138 |
| `src/main/java/dev/vapee/core/permission/LuckPermsService.java` | 128 |
| `src/main/java/dev/vapee/core/permission/PermissionModule.java` | 56 |
| `src/main/java/dev/vapee/core/player/CorePlayer.java` | 180 |
| `src/main/java/dev/vapee/core/player/PlayerListener.java` | 62 |
| `src/main/java/dev/vapee/core/player/PlayerModule.java` | 82 |
| `src/main/java/dev/vapee/core/player/PlayerService.java` | 148 |
| `src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java` | 706 |
| `src/main/java/dev/vapee/core/player/repository/PlayerRepository.java` | 20 |
| `src/main/java/dev/vapee/core/player/settings/AddedVisiblePlayerResult.java` | 9 |
| `src/main/java/dev/vapee/core/player/settings/PlayerSettings.java` | 92 |
| `src/main/java/dev/vapee/core/player/settings/PlayerSettingsService.java` | 208 |
| `src/main/java/dev/vapee/core/player/settings/PlayerVisibilitySettings.java` | 47 |
| `src/main/java/dev/vapee/core/player/social/PlayerSocial.java` | 44 |
| `src/main/java/dev/vapee/core/presence/FriendPresenceNotifier.java` | 119 |
| `src/main/java/dev/vapee/core/presence/PresenceListener.java` | 81 |
| `src/main/java/dev/vapee/core/presence/PresenceModule.java` | 98 |
| `src/main/java/dev/vapee/core/presence/PresenceService.java` | 31 |
| `src/main/java/dev/vapee/core/presence/PresenceStatus.java` | 6 |
| `src/main/java/dev/vapee/core/presentation/config/PresentationConfig.java` | 339 |
| `src/main/java/dev/vapee/core/presentation/PlaytimeFormatter.java` | 11 |
| `src/main/java/dev/vapee/core/presentation/PresentationListener.java` | 42 |
| `src/main/java/dev/vapee/core/presentation/PresentationModule.java` | 282 |
| `src/main/java/dev/vapee/core/presentation/PresentationRenderer.java` | 314 |
| `src/main/java/dev/vapee/core/presentation/PresentationService.java` | 81 |
| `src/main/java/dev/vapee/core/presentation/scoreboard/ScoreboardService.java` | 185 |
| `src/main/java/dev/vapee/core/presentation/tablist/TablistService.java` | 52 |
| `src/main/java/dev/vapee/core/privatemessage/command/MessageCommand.java` | 129 |
| `src/main/java/dev/vapee/core/privatemessage/command/ReplyCommand.java` | 108 |
| `src/main/java/dev/vapee/core/privatemessage/config/PrivateMessageConfig.java` | 153 |
| `src/main/java/dev/vapee/core/privatemessage/PrivateMessageListener.java` | 21 |
| `src/main/java/dev/vapee/core/privatemessage/PrivateMessageModule.java` | 174 |
| `src/main/java/dev/vapee/core/privatemessage/PrivateMessageResult.java` | 15 |
| `src/main/java/dev/vapee/core/privatemessage/PrivateMessageService.java` | 304 |
| `src/main/java/dev/vapee/core/quest/daily/command/DailyQuestCommand.java` | 39 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestConfig.java` | 239 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestCycleId.java` | 20 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestCycleResolver.java` | 20 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestListener.java` | 24 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestModule.java` | 183 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestSelector.java` | 58 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestService.java` | 111 |
| `src/main/java/dev/vapee/core/quest/daily/DailyQuestSyncResult.java` | 12 |
| `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestInventoryHolder.java` | 31 |
| `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java` | 200 |
| `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuListener.java` | 44 |
| `src/main/java/dev/vapee/core/quest/daily/PlayerDailyQuestState.java` | 29 |
| `src/main/java/dev/vapee/core/quest/PlayerQuestProgress.java` | 22 |
| `src/main/java/dev/vapee/core/quest/PlayerQuestState.java` | 104 |
| `src/main/java/dev/vapee/core/quest/QuestAssignmentResult.java` | 12 |
| `src/main/java/dev/vapee/core/quest/QuestDefinition.java` | 44 |
| `src/main/java/dev/vapee/core/quest/QuestDefinitionRegistry.java` | 51 |
| `src/main/java/dev/vapee/core/quest/QuestListener.java` | 51 |
| `src/main/java/dev/vapee/core/quest/QuestModule.java` | 169 |
| `src/main/java/dev/vapee/core/quest/QuestPlaytimeProducer.java` | 69 |
| `src/main/java/dev/vapee/core/quest/QuestProgressKey.java` | 25 |
| `src/main/java/dev/vapee/core/quest/QuestProgressReporter.java` | 68 |
| `src/main/java/dev/vapee/core/quest/QuestProgressResult.java` | 80 |
| `src/main/java/dev/vapee/core/quest/QuestService.java` | 371 |
| `src/main/java/dev/vapee/core/quest/QuestStatus.java` | 7 |
| `src/main/java/dev/vapee/core/quest/QuestView.java` | 22 |
| `src/main/java/dev/vapee/core/rank/command/RankCommand.java` | 134 |
| `src/main/java/dev/vapee/core/rank/command/RanksCommand.java` | 113 |
| `src/main/java/dev/vapee/core/rank/RankInfo.java` | 47 |
| `src/main/java/dev/vapee/core/rank/RankModule.java` | 119 |
| `src/main/java/dev/vapee/core/rank/RankService.java` | 168 |
| `src/main/java/dev/vapee/core/rank/staff/StaffHierarchyConfig.java` | 37 |
| `src/main/java/dev/vapee/core/rank/staff/StaffHierarchyService.java` | 65 |
| `src/main/java/dev/vapee/core/rank/staff/StaffTargetDecision.java` | 5 |
| `src/main/java/dev/vapee/core/reload/ReloadParticipant.java` | 8 |
| `src/main/java/dev/vapee/core/reload/ReloadPlan.java` | 26 |
| `src/main/java/dev/vapee/core/reload/ReloadResult.java` | 32 |
| `src/main/java/dev/vapee/core/reload/ReloadService.java` | 159 |
| `src/main/java/dev/vapee/core/reward/RewardGrant.java` | 27 |
| `src/main/java/dev/vapee/core/reward/RewardListener.java` | 27 |
| `src/main/java/dev/vapee/core/reward/RewardModule.java` | 112 |
| `src/main/java/dev/vapee/core/reward/RewardResult.java` | 37 |
| `src/main/java/dev/vapee/core/reward/RewardService.java` | 106 |
| `src/main/java/dev/vapee/core/reward/RewardSource.java` | 11 |
| `src/main/java/dev/vapee/core/reward/RewardStatus.java` | 7 |
| `src/main/java/dev/vapee/core/seat/SeatAssignment.java` | 68 |
| `src/main/java/dev/vapee/core/seat/SeatKey.java` | 19 |
| `src/main/java/dev/vapee/core/seat/SeatListener.java` | 192 |
| `src/main/java/dev/vapee/core/seat/SeatModule.java` | 81 |
| `src/main/java/dev/vapee/core/seat/SeatPositionResolver.java` | 66 |
| `src/main/java/dev/vapee/core/seat/SeatService.java` | 380 |
| `src/main/java/dev/vapee/core/seat/SeatType.java` | 6 |
| `src/main/java/dev/vapee/core/settings/command/SettingsCommand.java` | 279 |
| `src/main/java/dev/vapee/core/settings/SettingsInventoryHolder.java` | 43 |
| `src/main/java/dev/vapee/core/settings/SettingsListener.java` | 232 |
| `src/main/java/dev/vapee/core/settings/SettingsMenu.java` | 288 |
| `src/main/java/dev/vapee/core/settings/SettingsModule.java` | 196 |
| `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsHolder.java` | 39 |
| `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsListener.java` | 180 |
| `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java` | 210 |
| `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersHolder.java` | 58 |
| `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersListener.java` | 183 |
| `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java` | 230 |
| `src/main/java/dev/vapee/core/social/command/IgnoreCommand.java` | 125 |
| `src/main/java/dev/vapee/core/social/command/IgnoreListCommand.java` | 106 |
| `src/main/java/dev/vapee/core/social/command/UnignoreCommand.java` | 153 |
| `src/main/java/dev/vapee/core/social/IgnoreRelationshipListener.java` | 9 |
| `src/main/java/dev/vapee/core/social/IgnoreResult.java` | 9 |
| `src/main/java/dev/vapee/core/social/SocialListener.java` | 28 |
| `src/main/java/dev/vapee/core/social/SocialModule.java` | 126 |
| `src/main/java/dev/vapee/core/social/SocialService.java` | 156 |
| `src/main/java/dev/vapee/core/utility/command/BuildCommand.java` | 172 |
| `src/main/java/dev/vapee/core/utility/command/ClearCommand.java` | 171 |
| `src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java` | 141 |
| `src/main/java/dev/vapee/core/utility/command/FeedCommand.java` | 177 |
| `src/main/java/dev/vapee/core/utility/command/FlyCommand.java` | 193 |
| `src/main/java/dev/vapee/core/utility/command/GameModeCommand.java` | 226 |
| `src/main/java/dev/vapee/core/utility/command/HealCommand.java` | 177 |
| `src/main/java/dev/vapee/core/utility/command/InvseeCommand.java` | 134 |
| `src/main/java/dev/vapee/core/utility/command/PingCommand.java` | 134 |
| `src/main/java/dev/vapee/core/utility/command/SpeedCommand.java` | 201 |
| `src/main/java/dev/vapee/core/utility/command/TeleportCommand.java` | 305 |
| `src/main/java/dev/vapee/core/utility/command/TeleportHereCommand.java` | 156 |
| `src/main/java/dev/vapee/core/utility/InvseeInventoryHolder.java` | 48 |
| `src/main/java/dev/vapee/core/utility/InvseeService.java` | 232 |
| `src/main/java/dev/vapee/core/utility/OnlinePlayerResolver.java` | 60 |
| `src/main/java/dev/vapee/core/utility/teleport/TeleportParser.java` | 104 |
| `src/main/java/dev/vapee/core/utility/UtilityListener.java` | 42 |
| `src/main/java/dev/vapee/core/utility/UtilityModule.java` | 151 |
| `src/main/java/dev/vapee/core/utility/UtilityService.java` | 244 |
| `src/main/java/dev/vapee/core/utility/UtilitySpeedResult.java` | 4 |
| `src/main/java/dev/vapee/core/utility/UtilitySpeedType.java` | 16 |
| `src/main/java/dev/vapee/core/VapeeCore.java` | 238 |
| `src/main/java/dev/vapee/core/visibility/VisibilityModule.java` | 100 |
| `src/main/java/dev/vapee/core/visibility/VisibilityPolicy.java` | 53 |
| `src/main/java/dev/vapee/core/visibility/VisibilityService.java` | 132 |
| `src/main/java/dev/vapee/core/worlddisplay/WorldDisplayHandle.java` | 18 |
| `src/main/java/dev/vapee/core/worlddisplay/WorldDisplayKey.java` | 19 |
| `src/main/java/dev/vapee/core/worlddisplay/WorldDisplayModule.java` | 57 |
| `src/main/java/dev/vapee/core/worlddisplay/WorldDisplayService.java` | 359 |

### src/test/java (111 baseline files)

| Exact path | Lines |
|---|---:|
| `src/test/java/dev/vapee/core/activity/ActivityHarness.java` | 683 |
| `src/test/java/dev/vapee/core/activity/blackjack/BlackjackFairnessHarness.java` | 123 |
| `src/test/java/dev/vapee/core/activity/blackjack/BlackjackHarness.java` | 1431 |
| `src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationHarness.java` | 273 |
| `src/test/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewHarness.java` | 167 |
| `src/test/java/dev/vapee/core/activity/blackjack/table/BlackjackTableHarness.java` | 572 |
| `src/test/java/dev/vapee/core/chat/ChatHarness.java` | 229 |
| `src/test/java/dev/vapee/core/clan/ClanDomainHarness.java` | 111 |
| `src/test/java/dev/vapee/core/clan/ClanIntegrationHarness.java` | 97 |
| `src/test/java/dev/vapee/core/clan/ClanPersistenceHarness.java` | 196 |
| `src/test/java/dev/vapee/core/clan/ClanServiceHarness.java` | 266 |
| `src/test/java/dev/vapee/core/clan/gui/ClanCommandHarness.java` | 408 |
| `src/test/java/dev/vapee/core/clan/gui/ClanMenuFixture.java` | 188 |
| `src/test/java/dev/vapee/core/clan/gui/ClanMenuHarness.java` | 124 |
| `src/test/java/dev/vapee/core/clan/gui/ClanMenuSecurityHarness.java` | 83 |
| `src/test/java/dev/vapee/core/command/CoreCommandHarness.java` | 167 |
| `src/test/java/dev/vapee/core/command/OnlineStaffTargetGuardHarness.java` | 96 |
| `src/test/java/dev/vapee/core/command/StaffTargetTestFixture.java` | 35 |
| `src/test/java/dev/vapee/core/config/ConfigServiceHarness.java` | 277 |
| `src/test/java/dev/vapee/core/config/DefaultConsistencyHarness.java` | 104 |
| `src/test/java/dev/vapee/core/economy/command/CoinsCommandHarness.java` | 464 |
| `src/test/java/dev/vapee/core/economy/EconomyIntegrationHarness.java` | 114 |
| `src/test/java/dev/vapee/core/economy/EconomyServiceHarness.java` | 273 |
| `src/test/java/dev/vapee/core/friend/FriendCommandHarness.java` | 479 |
| `src/test/java/dev/vapee/core/friend/FriendDomainHarness.java` | 127 |
| `src/test/java/dev/vapee/core/friend/FriendIntegrationHarness.java` | 212 |
| `src/test/java/dev/vapee/core/friend/FriendLifecycleHarness.java` | 80 |
| `src/test/java/dev/vapee/core/friend/FriendPersistenceHarness.java` | 226 |
| `src/test/java/dev/vapee/core/friend/FriendServiceHarness.java` | 452 |
| `src/test/java/dev/vapee/core/friend/gui/FriendMenuFixture.java` | 267 |
| `src/test/java/dev/vapee/core/friend/gui/FriendMenuHarness.java` | 292 |
| `src/test/java/dev/vapee/core/friend/gui/FriendMenuSecurityHarness.java` | 107 |
| `src/test/java/dev/vapee/core/identity/IdentityHarness.java` | 159 |
| `src/test/java/dev/vapee/core/identity/ProfileHarness.java` | 209 |
| `src/test/java/dev/vapee/core/lobby/command/LobbyCommandHarness.java` | 113 |
| `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java` | 240 |
| `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenuHarness.java` | 98 |
| `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorSecurityHarness.java` | 255 |
| `src/test/java/dev/vapee/core/lobby/player/LobbyHarness.java` | 452 |
| `src/test/java/dev/vapee/core/lobby/warp/command/WarpCommandHarness.java` | 144 |
| `src/test/java/dev/vapee/core/lobby/warp/WarpHarness.java` | 417 |
| `src/test/java/dev/vapee/core/message/CommandHelpHarness.java` | 510 |
| `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java` | 578 |
| `src/test/java/dev/vapee/core/moderation/command/ModerationDurationHarness.java` | 38 |
| `src/test/java/dev/vapee/core/moderation/ModerationBanEnforcementHarness.java` | 74 |
| `src/test/java/dev/vapee/core/moderation/ModerationDomainHarness.java` | 150 |
| `src/test/java/dev/vapee/core/moderation/ModerationLifecycleHarness.java` | 274 |
| `src/test/java/dev/vapee/core/moderation/ModerationMuteEnforcementHarness.java` | 100 |
| `src/test/java/dev/vapee/core/moderation/ModerationMuteProjectionHarness.java` | 76 |
| `src/test/java/dev/vapee/core/moderation/ModerationPersistenceHarness.java` | 306 |
| `src/test/java/dev/vapee/core/moderation/ModerationServiceHarness.java` | 257 |
| `src/test/java/dev/vapee/core/moderation/ModerationTestSupport.java` | 178 |
| `src/test/java/dev/vapee/core/onlinereward/OnlineRewardLifecycleHarness.java` | 157 |
| `src/test/java/dev/vapee/core/onlinereward/OnlineRewardServiceHarness.java` | 501 |
| `src/test/java/dev/vapee/core/permission/LuckPermsAsyncHarness.java` | 87 |
| `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java` | 195 |
| `src/test/java/dev/vapee/core/player/repository/PlayerQuestPersistenceHarness.java` | 311 |
| `src/test/java/dev/vapee/core/player/repository/PlayerRewardPersistenceHarness.java` | 154 |
| `src/test/java/dev/vapee/core/player/repository/PlayerVisibilityPersistenceHarness.java` | 145 |
| `src/test/java/dev/vapee/core/player/settings/PlayerSettingsServiceHarness.java` | 131 |
| `src/test/java/dev/vapee/core/player/settings/PlayerVisibilitySettingsHarness.java` | 54 |
| `src/test/java/dev/vapee/core/presence/FriendPresenceNotifierHarness.java` | 174 |
| `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java` | 105 |
| `src/test/java/dev/vapee/core/presence/PresenceServiceHarness.java` | 50 |
| `src/test/java/dev/vapee/core/presentation/PlaytimeFormatterHarness.java` | 40 |
| `src/test/java/dev/vapee/core/presentation/PresentationHarness.java` | 133 |
| `src/test/java/dev/vapee/core/privatemessage/PrivateMessageSocialHarness.java` | 544 |
| `src/test/java/dev/vapee/core/quest/daily/command/QuestCommandHarness.java` | 71 |
| `src/test/java/dev/vapee/core/quest/daily/DailyQuestConfigHarness.java` | 137 |
| `src/test/java/dev/vapee/core/quest/daily/DailyQuestCycleSelectorHarness.java` | 105 |
| `src/test/java/dev/vapee/core/quest/daily/DailyQuestLifecycleHarness.java` | 143 |
| `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuFixture.java` | 125 |
| `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuHarness.java` | 102 |
| `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuSecurityHarness.java` | 96 |
| `src/test/java/dev/vapee/core/quest/DailyQuestServiceHarness.java` | 243 |
| `src/test/java/dev/vapee/core/quest/QuestCompletionFixture.java` | 96 |
| `src/test/java/dev/vapee/core/quest/QuestDefinitionHarness.java` | 187 |
| `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java` | 277 |
| `src/test/java/dev/vapee/core/quest/QuestPlaytimeProducerHarness.java` | 89 |
| `src/test/java/dev/vapee/core/quest/QuestProgressReporterHarness.java` | 73 |
| `src/test/java/dev/vapee/core/quest/QuestServiceHarness.java` | 550 |
| `src/test/java/dev/vapee/core/rank/RankCommandHarness.java` | 250 |
| `src/test/java/dev/vapee/core/rank/RanksCommandHarness.java` | 224 |
| `src/test/java/dev/vapee/core/rank/RankServiceHarness.java` | 168 |
| `src/test/java/dev/vapee/core/rank/staff/StaffHierarchyServiceHarness.java` | 98 |
| `src/test/java/dev/vapee/core/reward/RewardLifecycleHarness.java` | 229 |
| `src/test/java/dev/vapee/core/reward/RewardServiceHarness.java` | 348 |
| `src/test/java/dev/vapee/core/seat/SeatHarness.java` | 362 |
| `src/test/java/dev/vapee/core/settings/command/SettingsCommandHarness.java` | 312 |
| `src/test/java/dev/vapee/core/settings/SettingsMenuFixture.java` | 207 |
| `src/test/java/dev/vapee/core/settings/SettingsMenuHarness.java` | 141 |
| `src/test/java/dev/vapee/core/settings/SettingsMenuSecurityHarness.java` | 101 |
| `src/test/java/dev/vapee/core/settings/visibility/SettingsModuleLifecycleHarness.java` | 79 |
| `src/test/java/dev/vapee/core/settings/visibility/SettingsNavigationHarness.java` | 67 |
| `src/test/java/dev/vapee/core/settings/visibility/VisibilityMenuFixture.java` | 337 |
| `src/test/java/dev/vapee/core/settings/visibility/VisibilityMenuSecurityHarness.java` | 163 |
| `src/test/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenuHarness.java` | 141 |
| `src/test/java/dev/vapee/core/settings/visibility/VisiblePlayersMenuHarness.java` | 191 |
| `src/test/java/dev/vapee/core/utility/command/BuildCommandHarness.java` | 161 |
| `src/test/java/dev/vapee/core/utility/command/TeleportCommandHarness.java` | 331 |
| `src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java` | 862 |
| `src/test/java/dev/vapee/core/utility/TeleportParserHarness.java` | 119 |
| `src/test/java/dev/vapee/core/utility/UtilityInventoryHarness.java` | 573 |
| `src/test/java/dev/vapee/core/utility/UtilityServiceHarness.java` | 309 |
| `src/test/java/dev/vapee/core/visibility/FriendVisibilityRefreshHarness.java` | 63 |
| `src/test/java/dev/vapee/core/visibility/IgnoreVisibilityRefreshHarness.java` | 50 |
| `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java` | 110 |
| `src/test/java/dev/vapee/core/visibility/VisibilityPolicyHarness.java` | 179 |
| `src/test/java/dev/vapee/core/visibility/VisibilityRelationshipFixture.java` | 159 |
| `src/test/java/dev/vapee/core/visibility/VisibilityServiceHarness.java` | 148 |
| `src/test/java/dev/vapee/core/worlddisplay/WorldDisplayHarness.java` | 250 |

### src/main/resources (9 baseline files)

| Exact path | Lines |
|---|---:|
| `src/main/resources/blackjack.yml` | 1 |
| `src/main/resources/chat.yml` | 6 |
| `src/main/resources/config.yml` | 46 |
| `src/main/resources/daily-quests.yml` | 9 |
| `src/main/resources/lobby.yml` | 24 |
| `src/main/resources/plugin.yml` | 352 |
| `src/main/resources/presentation.yml` | 29 |
| `src/main/resources/private-messages.yml` | 5 |
| `src/main/resources/warps.yml` | 1 |

### scripts (3 baseline files)

| Exact path | Lines |
|---|---:|
| `scripts/build-and-deploy.ps1` | 71 |
| `scripts/start-dev-server.ps1` | 143 |
| `scripts/stop-dev-server.ps1` | 100 |

### docs (12 baseline files)

| Exact path | Lines |
|---|---:|
| `docs/COMMAND_AUDIT.md` | 240 |
| `docs/DEVELOPER_GUIDE.md` | 1072 |
| `docs/FORMATTING.md` | 2277 |
| `docs/LOBBY_NAVIGATION.md` | 307 |
| `docs/MODERATION_FOUNDATION.md` | 249 |
| `docs/MODERATION_MUTES.md` | 256 |
| `docs/MODERATION_TOOLS.md` | 299 |
| `docs/NOTIFICATIONS_PRESENCE.md` | 59 |
| `docs/PERMISSION_HARDENING.md` | 317 |
| `docs/PERMISSIONS.md` | 329 |
| `docs/QUEST_COMPLETION.md` | 520 |
| `docs/STAFF_HIERARCHY.md` | 331 |

README.md and pom.xml were also reviewed. This newly added report is not counted in the 12 baseline docs files.

### Complete permission defaults and child graph

Source: src/main/resources/plugin.yml. All 50 nodes are explicit; all 15 listed child edges are positive. No alias is counted as a root and no transitive edge is double-counted.

| Permission node | Default | Direct positive children |
|---|---|---|
| `vapeecore.quest.use` | true | — |
| `vapeecore.moderation.mute` | op | — |
| `vapeecore.moderation.unmute` | op | — |
| `vapeecore.moderation.warn` | op | — |
| `vapeecore.moderation.ban` | op | — |
| `vapeecore.moderation.unban` | op | — |
| `vapeecore.moderation.kick` | op | — |
| `vapeecore.moderation.history` | op | — |
| `vapeecore.admin` | op | — |
| `vapeecore.lobby.spawn` | true | — |
| `vapeecore.lobby.setspawn` | op | — |
| `vapeecore.lobby.build` | op | `vapeecore.utility.build` |
| `vapeecore.utility.build` | op | — |
| `vapeecore.utility.fly` | op | — |
| `vapeecore.utility.fly.others` | op | `vapeecore.utility.fly` |
| `vapeecore.utility.speed` | op | — |
| `vapeecore.utility.speed.others` | op | `vapeecore.utility.speed` |
| `vapeecore.utility.gamemode` | op | — |
| `vapeecore.utility.gamemode.others` | op | `vapeecore.utility.gamemode` |
| `vapeecore.utility.teleport` | op | — |
| `vapeecore.utility.teleport.others` | op | `vapeecore.utility.teleport` |
| `vapeecore.utility.teleport.world` | op | `vapeecore.utility.teleport` |
| `vapeecore.utility.teleport.others.world` | op | `vapeecore.utility.teleport.others`, `vapeecore.utility.teleport.world` |
| `vapeecore.utility.teleport.bypass` | op | — |
| `vapeecore.utility.teleport.here` | op | — |
| `vapeecore.utility.heal` | op | — |
| `vapeecore.utility.heal.others` | op | `vapeecore.utility.heal` |
| `vapeecore.utility.feed` | op | — |
| `vapeecore.utility.feed.others` | op | `vapeecore.utility.feed` |
| `vapeecore.utility.ping` | op | — |
| `vapeecore.utility.ping.others` | op | `vapeecore.utility.ping` |
| `vapeecore.utility.clear` | op | — |
| `vapeecore.utility.clear.others` | op | `vapeecore.utility.clear` |
| `vapeecore.utility.invsee` | op | — |
| `vapeecore.utility.invsee.modify` | op | `vapeecore.utility.invsee` |
| `vapeecore.utility.enderchest` | op | — |
| `vapeecore.utility.enderchest.others` | op | `vapeecore.utility.enderchest` |
| `vapeecore.economy.coins` | true | — |
| `vapeecore.profile.view` | true | — |
| `vapeecore.friend.use` | true | — |
| `vapeecore.clan.use` | true | — |
| `vapeecore.visibility.staff` | op | — |
| `vapeecore.economy.admin` | op | `vapeecore.economy.coins` |
| `vapeecore.message.use` | true | — |
| `vapeecore.settings.use` | true | — |
| `vapeecore.social.ignore` | true | — |
| `vapeecore.blackjack.admin` | op | — |
| `vapeecore.warp.admin` | op | — |
| `vapeecore.rank.view` | true | — |
| `vapeecore.ranks.view` | true | — |

## Appendix B. Fresh independently executed harness results

Every row is one separately launched pinned-Java process. No executable harness was skipped. Support-only fixtures are listed in section 11 and Appendix A.

| Fully qualified main class | Actual checks | Exit |
|---|---:|---:|
| `dev.vapee.core.activity.ActivityHarness` | 153 | 0 |
| `dev.vapee.core.activity.blackjack.BlackjackFairnessHarness` | 393 | 0 |
| `dev.vapee.core.activity.blackjack.BlackjackHarness` | 300 | 0 |
| `dev.vapee.core.activity.blackjack.BlackjackPresentationHarness` | 43 | 0 |
| `dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness` | 16 | 0 |
| `dev.vapee.core.activity.blackjack.table.BlackjackTableHarness` | 90 | 0 |
| `dev.vapee.core.chat.ChatHarness` | 17 | 0 |
| `dev.vapee.core.clan.ClanDomainHarness` | 52 | 0 |
| `dev.vapee.core.clan.ClanIntegrationHarness` | 15 | 0 |
| `dev.vapee.core.clan.ClanPersistenceHarness` | 39 | 0 |
| `dev.vapee.core.clan.ClanServiceHarness` | 136 | 0 |
| `dev.vapee.core.clan.gui.ClanCommandHarness` | 83 | 0 |
| `dev.vapee.core.clan.gui.ClanMenuHarness` | 84 | 0 |
| `dev.vapee.core.clan.gui.ClanMenuSecurityHarness` | 37 | 0 |
| `dev.vapee.core.command.CoreCommandHarness` | 75 | 0 |
| `dev.vapee.core.command.OnlineStaffTargetGuardHarness` | 268 | 0 |
| `dev.vapee.core.config.ConfigServiceHarness` | 74 | 0 |
| `dev.vapee.core.config.DefaultConsistencyHarness` | 12 | 0 |
| `dev.vapee.core.economy.command.CoinsCommandHarness` | 1942 | 0 |
| `dev.vapee.core.economy.EconomyIntegrationHarness` | 27 | 0 |
| `dev.vapee.core.economy.EconomyServiceHarness` | 44 | 0 |
| `dev.vapee.core.friend.FriendCommandHarness` | 80 | 0 |
| `dev.vapee.core.friend.FriendDomainHarness` | 29 | 0 |
| `dev.vapee.core.friend.FriendIntegrationHarness` | 28 | 0 |
| `dev.vapee.core.friend.FriendLifecycleHarness` | 12 | 0 |
| `dev.vapee.core.friend.FriendPersistenceHarness` | 28 | 0 |
| `dev.vapee.core.friend.FriendServiceHarness` | 79 | 0 |
| `dev.vapee.core.friend.gui.FriendMenuHarness` | 159 | 0 |
| `dev.vapee.core.friend.gui.FriendMenuSecurityHarness` | 37 | 0 |
| `dev.vapee.core.identity.IdentityHarness` | 26 | 0 |
| `dev.vapee.core.identity.ProfileHarness` | 19 | 0 |
| `dev.vapee.core.lobby.command.LobbyCommandHarness` | 26 | 0 |
| `dev.vapee.core.lobby.experience.navigator.NavigatorMenuHarness` | 113 | 0 |
| `dev.vapee.core.lobby.experience.navigator.NavigatorSecurityHarness` | 82 | 0 |
| `dev.vapee.core.lobby.player.LobbyHarness` | 53 | 0 |
| `dev.vapee.core.lobby.warp.command.WarpCommandHarness` | 86 | 0 |
| `dev.vapee.core.lobby.warp.WarpHarness` | 118 | 0 |
| `dev.vapee.core.message.CommandHelpHarness` | 85 | 0 |
| `dev.vapee.core.moderation.command.ModerationCommandHarness` | 3381 | 0 |
| `dev.vapee.core.moderation.command.ModerationDurationHarness` | 53 | 0 |
| `dev.vapee.core.moderation.ModerationBanEnforcementHarness` | 31 | 0 |
| `dev.vapee.core.moderation.ModerationDomainHarness` | 112 | 0 |
| `dev.vapee.core.moderation.ModerationLifecycleHarness` | 149 | 0 |
| `dev.vapee.core.moderation.ModerationMuteEnforcementHarness` | 52 | 0 |
| `dev.vapee.core.moderation.ModerationMuteProjectionHarness` | 20 | 0 |
| `dev.vapee.core.moderation.ModerationPersistenceHarness` | 523 | 0 |
| `dev.vapee.core.moderation.ModerationServiceHarness` | 141 | 0 |
| `dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness` | 42 | 0 |
| `dev.vapee.core.onlinereward.OnlineRewardServiceHarness` | 42 | 0 |
| `dev.vapee.core.permission.LuckPermsAsyncHarness` | 16 | 0 |
| `dev.vapee.core.permission.PermissionDescriptorHarness` | 830 | 0 |
| `dev.vapee.core.player.repository.PlayerQuestPersistenceHarness` | 21 | 0 |
| `dev.vapee.core.player.repository.PlayerRewardPersistenceHarness` | 11 | 0 |
| `dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness` | 17 | 0 |
| `dev.vapee.core.player.settings.PlayerSettingsServiceHarness` | 28 | 0 |
| `dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness` | 11 | 0 |
| `dev.vapee.core.presence.FriendPresenceNotifierHarness` | 8 | 0 |
| `dev.vapee.core.presence.PresenceLifecycleHarness` | 14 | 0 |
| `dev.vapee.core.presence.PresenceServiceHarness` | 15 | 0 |
| `dev.vapee.core.presentation.PlaytimeFormatterHarness` | 14 | 0 |
| `dev.vapee.core.presentation.PresentationHarness` | 10 | 0 |
| `dev.vapee.core.privatemessage.PrivateMessageSocialHarness` | 117 | 0 |
| `dev.vapee.core.quest.daily.command.QuestCommandHarness` | 45 | 0 |
| `dev.vapee.core.quest.daily.DailyQuestConfigHarness` | 13 | 0 |
| `dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness` | 18 | 0 |
| `dev.vapee.core.quest.daily.DailyQuestLifecycleHarness` | 16 | 0 |
| `dev.vapee.core.quest.daily.menu.QuestMenuHarness` | 20 | 0 |
| `dev.vapee.core.quest.daily.menu.QuestMenuSecurityHarness` | 45 | 0 |
| `dev.vapee.core.quest.DailyQuestServiceHarness` | 20 | 0 |
| `dev.vapee.core.quest.QuestDefinitionHarness` | 29 | 0 |
| `dev.vapee.core.quest.QuestLifecycleHarness` | 42 | 0 |
| `dev.vapee.core.quest.QuestPlaytimeProducerHarness` | 18 | 0 |
| `dev.vapee.core.quest.QuestProgressReporterHarness` | 18 | 0 |
| `dev.vapee.core.quest.QuestServiceHarness` | 44 | 0 |
| `dev.vapee.core.rank.RankCommandHarness` | 16 | 0 |
| `dev.vapee.core.rank.RanksCommandHarness` | 12 | 0 |
| `dev.vapee.core.rank.RankServiceHarness` | 23 | 0 |
| `dev.vapee.core.rank.staff.StaffHierarchyServiceHarness` | 109 | 0 |
| `dev.vapee.core.reward.RewardLifecycleHarness` | 40 | 0 |
| `dev.vapee.core.reward.RewardServiceHarness` | 126 | 0 |
| `dev.vapee.core.seat.SeatHarness` | 52 | 0 |
| `dev.vapee.core.settings.command.SettingsCommandHarness` | 25 | 0 |
| `dev.vapee.core.settings.SettingsMenuHarness` | 66 | 0 |
| `dev.vapee.core.settings.SettingsMenuSecurityHarness` | 34 | 0 |
| `dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness` | 23 | 0 |
| `dev.vapee.core.settings.visibility.SettingsNavigationHarness` | 17 | 0 |
| `dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness` | 41 | 0 |
| `dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness` | 29 | 0 |
| `dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness` | 74 | 0 |
| `dev.vapee.core.utility.command.BuildCommandHarness` | 17 | 0 |
| `dev.vapee.core.utility.command.TeleportCommandHarness` | 894 | 0 |
| `dev.vapee.core.utility.command.UtilityCommandHarness` | 1221 | 0 |
| `dev.vapee.core.utility.TeleportParserHarness` | 58 | 0 |
| `dev.vapee.core.utility.UtilityInventoryHarness` | 742 | 0 |
| `dev.vapee.core.utility.UtilityServiceHarness` | 35 | 0 |
| `dev.vapee.core.visibility.FriendVisibilityRefreshHarness` | 11 | 0 |
| `dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness` | 7 | 0 |
| `dev.vapee.core.visibility.VisibilityModuleHarness` | 22 | 0 |
| `dev.vapee.core.visibility.VisibilityPolicyHarness` | 28 | 0 |
| `dev.vapee.core.visibility.VisibilityServiceHarness` | 10 | 0 |
| `dev.vapee.core.worlddisplay.WorldDisplayHarness` | 27 | 0 |
| **TOTAL** | **14705** | **0 failures** |
