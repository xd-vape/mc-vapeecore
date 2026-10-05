# Phase 30F – Presentation and Nametag Polish

## Baseline and scope

Baseline `8fd744e5755aea95cc2f6473b185de2d7dc3dc9d`, subject
`Merge Phase 30E - Duplication and Maintainability Cleanup`, branch
`phase/30f-presentation-nametag-polish`. Initial tree clean; branch, HEAD,
status and decorated last twelve commits verified before production changes.
All 479 tracked files captured by raw SHA-256. One worker, existing checkout.
Only PRES-001 and PRES-002; no branch/worktree creation or remote publication.

## Ownership and dataflow audit before implementation

| Component | Owner / writer / cleanup | Previous value / foreign detection | Configuration / join / quit / reload | Viewer / scoreboard / visibility |
|---|---|---|---|---|
| Tablist name | TablistService writes Player.playerListName; PresentationService removes | Baseline UUID set only; no previous value, unconditional null reset | Periodic and next-tick join writes; tab disabled removes; quit removes; module disable removes online then clears; reload enabled updates, disabled removes | Name belongs to target, distributed by Paper; no viewer-specific name policy; no show/hide |
| Header and footer | Same service; combined send/reset in baseline | No previous values or per-field ownership; resets both to empty | Same lifecycle as name, independently foreign-writable fields | Each recipient's header/footer; no board dependency |
| Sidebar | ScoreboardService creates one new board and one vapeecore objective per player; updates bounded Scores; unregisters only captured objective | Takes over only main-board identity; state retains exact board; foreign replacement makes update drop own state and unregister own objective; cleanup sets main only when exact own board remains current | Global/config/loaded-settings/lobby gates; line-count change recreates owned board; join/world change/update; quit/removeAll/reload cleanup | Per-viewer board, not a global team board. Foreign board skipped without objective/slot/team mutation. No team creation anywhere in production |
| Renderer | PresentationRenderer consumes Config, Message, cached LP/Rank, Economy, Player statistic | Immutable template state, components, no write ownership | Main-thread periodic render; prepare validates without publishing; apply/rollback swaps render state | Self data only; no target roster or visibility decision |
| Rank | RankService reads LP primary group and metadata | No new rank cache/authority | Each render reads current cached LP data; missing rank neutral | Rank color and display are presentation only; hierarchy separate |
| Clan | ClanModule owns initialized ClanService immutable snapshot, getClanOf(UUID) returns immutable Clan | Canonical tag differs from name and UUID; no presentation read currently | Clan enabled before Presentation, disabled after it; no clan reload participant or display cache | Main-thread read only; no reverse Clan-to-Presentation dependency |
| Visibility | VisibilityService alone owns plugin hide/show pairs (UUID sets) | Paper plugin-specific relations; restore only tracked pairs | LobbyExperience join/respawn/world/quit, settings and relation events; module restoreAll | Directional viewer-to-target policy; ignore/privacy authority independent of presentation; no scoreboard access |
| Reload/task | PresentationModule owns config, renderer, services, listener, timer | Existing prepare/apply/rollback; enabled-enabled keeps ownership; disabled removes | Six participants unchanged; timer replaced only as existing interval/enabled rules require | No new scheduler or cross-plugin arbitration |

Existing placeholders: server, name, rank_name, prefix, suffix, rank, rank_id,
group (rank_id compatibility alias), playtime, coins, online, max_players.
No overhead teams or glyph subsystem exists. Main-scoreboard-only teams cannot
cover the current per-viewer boards. Phase 30F will keep overhead rendering
deferred, add an opt-in literal clan read seam and exercise the concrete owned/
foreign board and visibility boundaries without creating a new visible default.

## Paper API evidence

Inspected local Paper API 1.21.11 sources and the actual build-132 CraftPlayer
bytecode (ignored `phase30f-craftplayer-api.txt`). Name getter returns the effective
Component, substituting Component.text(getName()) for the internal null sentinel;
that sentinel cannot be distinguished from an explicitly identical name. Restore
therefore preserves the observable Component, not an unknowable internal flag.
Header/footer getters expose nullable Components. Adventure sends accept non-null
Components; the existing nullable Bukkit String setter is used **only for null**
restoration, with no string conversion of styled values. Separate sends preserve
the other field. Comparisons use Component equality, never serialized strings.

## PRES-002 implementation and coexistence

The old modified-player set becomes UUID-to-three-fields metadata. Each field
retains `previous`, `lastWritten`, and whether a successful own write occurred.
No Player, callback bound to a Player, or offline session is retained. Before the
first successful write the field is read; subsequent refreshes retain that first
value. The last-own value is read back after the setter, accommodating Paper's
Component normalization. Header/footer writes and restoration are independent.
This adds separate header/footer sends instead of one combined update packet.

Removal takes the metadata out of the map, then restores each written field only
if its current Component equals lastWritten. Foreign-current fields remain
untouched. Final clear discards offline metadata after normal online cleanup.
Repeated removal is inert; a later ownership cycle captures a fresh baseline.
Paper cannot expose who made an identical-value write: equality is the observable
ownership contract, not proof of plugin identity. Active periodic updates remain
the configured writer; an intervening foreign write followed by another own
refresh does not rebase the original value. This is cleanup coexistence, not a
global arbitration mechanism or prevention of all active-plugin contention.

For the following actual test matrix, every row starts at old N/H/F, writes own
A, then own B. X denotes a later foreign Component. Each row also verifies no
second-cleanup writes and an empty ownership map:

| Foreign fields after B | Name after cleanup | Header after cleanup | Footer after cleanup |
|---|---|---|---|
| None | old N | old H | old F |
| Name | X | old H | old F |
| Header | old N | X | old F |
| Footer | old N | old H | X |
| Name, header | X | X | old F |
| Name, footer | X | old H | X |
| Header, footer | old N | X | X |
| All | X | X | X |

Additional executed cases cover same text/different Component style, nullable
header/footer restoration, normalized readable name, partial write failure,
tablist-specific enabled/disabled transitions, removal/reconnect with the same
UUID, final metadata clear, and interleaved refresh retaining the original value.
The real PresentationModule lifecycle harness covers enable, next-tick join,
enabled-enabled reload and rollback, enabled-disabled reload, disabled rollback,
disabled-enabled reload, quit/reconnect, module disable and repeated disable.
The reload coordinator and all other participants remain unchanged (TEST-001).

## PRES-001 implementation and deliberate boundaries

Bootstrap supplies `UUID -> ClanService.getClanOf(UUID).map(Clan::tag)` through
PresentationModule to PresentationRenderer. Presentation receives only a read
function returning an Optional String, with no Clan mutation capability; Clan
has no new dependency. Existing constructors remain compatible via an empty read.
Clan lifecycle already precedes Presentation enable and follows its disable.
No new module, reload participant, repository, schema or display cache is added.

`<clan_tag>` is available in the existing sidebar title/lines and tablist name/
header/footer templates, including strict template validation. It inserts a
literal Component and never evaluates clan input as MiniMessage or legacy text.
The canonical tag is distinct from name and UUID. A player without a clan, an
unknown player/missing clan, empty Optional, null Optional, blank or whitespace/
control-containing read yields empty text. No automatic brackets, separators or
spaces are added. A provider exception is still the existing logged failed-update
path, not silently reported as a missing clan. Normal boot cannot render before
Clan successfully initializes. Changes of clan membership/tag are read on the
next existing update. No resource YAML default or live template is changed.

The concrete `ScoreboardService.ownsScoreboard(viewer)` boundary requires both
UUID state and identity of the viewer's **currently displayed** board. Actual
update and cleanup use this same predicate. This preserves the baseline behavior:
take over only the main-board identity, create a separate viewer board, skip an
unowned foreign board, unregister only the captured own objective, and reset to
main only when the own board is still current. Main is an eligibility/default
board, never a global rendering target for teams. A foreign objective called
`vapeecore` on a replacement board does not become owned by name.

The executable board test uses real ScoreboardService, narrow external Paper
proxies, two independent viewer boards and a third target. It checks delta
updates, line-count recreation, settings disable/re-enable, foreign replacement
on refresh and direct removal, idempotent removeAll and untouched foreign/main
boards. No team API call is allowed by the fixture. The actual VisibilityService
sets asymmetric hidden/shown relations; Presentation refresh/remove/rejoin never
calls hide/show or changes them. Only Visibility changes the target relation.
Build-132 CraftPlayer name-write bytecode also checks viewer.canSee(target) before
distributing name updates; Presentation introduces no visibility override.

**Implemented:** literal opt-in clan integration, a concrete current-viewer board
identity boundary used by production cleanup/update, per-field tab ownership,
real service/lifecycle regressions and focused operator/developer documentation.
**Deferred:** visible overhead nametags, team namespace/membership/collision
implementation, viewer-target roster rendering and resource-pack glyph delivery.
Future teams must use the actual viewer board, own exact team instances, skip
unsafe foreign boards/collisions and respect Visibility. No team ownership is
claimed simply from `ownsScoreboard`. Rank/permission authority remains LuckPerms;
an arbitrary literal private-use glyph character is text, not a new rank authority.

PRES-001 is resolved as the explicitly permitted small architecture/read-seam
scope, not as delivery of an overhead nametag feature. PRES-002 is resolved under
the observable Paper field semantics above.

## Regression and isolated controls

Focused: **39 harnesses, 8,269 checks, 0 failures, 0 skipped**. Includes all
required Presentation/Config, Visibility/settings, Clan, Rank, descriptor,
ModerationCommand (4,414), BlackjackReadView (21) and UtilityCommand (1,302) checks.
New dedicated harnesses: TablistOwnership **58**, ScoreboardOwnership **17**,
PresentationLifecycle **38**. PresentationFixture is support, not an executable
harness. It replaces only external Paper/LP surfaces and module shells; services,
renderer, config, Clan domain and PresentationModule enable/reload/disable run
their production implementations. It does not simulate client packet visuals.

Three isolated controls all fail with the intended AssertionError/exit 1:

| Deliberate mutation | Detecting test / failure |
|---|---|
| Always restore tracked tab fields | TablistOwnership: foreign-current name preserved, mask 1 |
| Treat any tracked board as owned | ScoreboardOwnership: foreign replacement detected by current viewer identity |
| Parse clan tag as MiniMessage | PresentationLifecycle: join uses canonical tag literally |

Controls compile separate source copies/classes ahead of the normal classpath
only for their own process; no production/test class is overwritten. All isolated
copies were removed after validating their exact absolute directory. Logs/results
remain ignored. These expected failures are excluded from final regression totals.

Full regression: **115 executable harnesses, 18,637 checks, 0 failures, 0 skipped**.
All 112 Phase-30E harnesses are present, with zero reduced check counts; the three
new harnesses add 113 checks. Each active public static main in a *Harness.java
ran in its own Java process. No existing test source was modified. The descriptor
harness remains 840 checks. Maven compilation/package is not counted as harness
execution. Full per-process inventory is below.

## Build and artifact

After full regression, this exact offline build ran:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

**BUILD SUCCESS, exit 0**, 12.909 seconds, completed 2026-10-05 at 16:32:09
+02:00. Artifact `target/vapeecore-1.0-SNAPSHOT.jar`: **1,061,227 bytes**,
SHA-256 `375EB2F749FAE811CDCDBE199611AF270870D8D5EF75F380F3DA5AFBFED5490C`.
The dev-server deployment has identical bytes/hash. No source changed after
the final regression/build. Existing compiler annotation/deprecation notices
are informational; POM/dependencies/test infrastructure are unchanged.

## Real Paper smoke

Paper **1.21.11 build 132 / c5eb079**, API **1.21.11-R0.1-SNAPSHOT**, Oracle Java
**21.0.12.1+1-LTS-4**, LuckPerms **5.5.84**, Windows 11. Final JAR startup
2026-10-05 16:32:50, ready 16:33:03 (13.442 seconds); commands at 16:33:30;
stop 16:33:41, process exit **0** at 16:33:42 (Europe/Berlin). Server is stopped.

- All **27 modules** enabled; `/core` reports Running and LuckPerms Connected.
- `version`, `lp info`, `list`, `/core`, `/core version`, `/core help` successful.
- `/core reload` prepared exactly config.yml, lobby.yml, chat.yml,
  private-messages.yml, presentation.yml, daily-quests.yml and completed in **14 ms**.
- `bukkit:help VapeeCore`: **37 roots**, exact match with plugin.yml.
- **50 permissions / 15 positive child edges**, unchanged descriptor/hash and
  descriptor harness; **27 modules / 6 reload participants** unchanged.
- All **27 modules** disabled in exact reverse startup order; worlds saved and
  LuckPerms storage closed. **0 ERROR/SEVERE** entries in the entire smoke log.
- All **8 live YAML files** have identical before/after hashes, no additions or
  removals. No live configuration edit, clan/player mutation or persistence change.
- Zero online players. **Live client test: not performed.** The optional visible
  clan output and tablist restoration are harness-tested, not visually accepted
  in a connected client. No overhead feature or fake multiplayer test is claimed.

Paper's notice about newer Minecraft releases and Spark's Windows Java-engine
fallback are environment messages. They do not change the tested runtime version.

## File inventory and scope review

**5 added, 7 modified, 0 removed**; every file is assigned below. Comparison of
all 479 baseline files finds **472 byte-identical**, with only the seven listed
existing files changed. Protected resources, README, POM and historical reports
are unchanged. No production Clan, Rank, Visibility, config, reload coordinator,
domain/repository or previous-finding implementation was altered. Presentation
Service/Listener retain their lifecycle code. The Developer Guide and Formatting
changes only document the new concrete placeholder/ownership behavior (PRES-001/002),
not DOC-001 cleanup. Every added source, existing-file diff and report was reviewed;
status, diff/check/stat/name-status confirm only the intended twelve files.

| Status | File | Scope |
|---|---|---|
| Modified | src/main/java/dev/vapee/core/VapeeCore.java | PRES-001 canonical tag supplier wiring |
| Modified | src/main/java/dev/vapee/core/presentation/PresentationModule.java | PRES-001 read function injection |
| Modified | src/main/java/dev/vapee/core/presentation/PresentationRenderer.java | PRES-001 literal placeholder and validation |
| Modified | src/main/java/dev/vapee/core/presentation/scoreboard/ScoreboardService.java | PRES-001 explicit current-viewer board predicate |
| Modified | src/main/java/dev/vapee/core/presentation/tablist/TablistService.java | PRES-002 per-field capture/guarded restore |
| Modified | docs/DEVELOPER_GUIDE.md | PRES-001/002 technical ownership documentation |
| Modified | docs/FORMATTING.md | PRES-001 opt-in literal placeholder documentation |
| Added | src/test/java/dev/vapee/core/presentation/PresentationFixture.java | Tests: narrow external Paper/LP fixture |
| Added | src/test/java/dev/vapee/core/presentation/PresentationLifecycleHarness.java | Tests: real module/reload and clan rendering |
| Added | src/test/java/dev/vapee/core/presentation/scoreboard/ScoreboardOwnershipHarness.java | Tests: current/foreign viewer boards and Visibility coexistence |
| Added | src/test/java/dev/vapee/core/presentation/tablist/TablistOwnershipHarness.java | Tests: per-field interleaving/cycles |
| Added | docs/PRESENTATION_NAMETAG_POLISH.md | Phase-30F report |

## Delivery and remaining findings

**PRES-001 resolved within the deliberate small architecture scope; PRES-002 resolved.**
MOD-001, API-001, GUI-001/002, LIFE-001, CONFIG-001, CMD-001, DUP-001 and LEGACY-001
remain protected and pass full regression. Exactly **9 findings remain open**:
SEC-001, DISPLAY-001, PERSIST-001, PERSIST-002, LIFE-002, PERF-001, TEST-001,
TEST-002 and DOC-001. None is implemented here; no subsequent phase started.

The single local commit containing this report has subject
`Phase 30F - Presentation and Nametag Polish`, directly after the specified
baseline. Exact commit SHA, one-commit distance and clean final working tree are
verified after commit and recorded in the final response plus ignored receipt,
avoiding a self-referential commit hash inside this document. No push, merge,
rebase, branch rename, additional worktree or subagent. Merge readiness: ready
within the documented scope and absent-client acceptance limit.

Ignored evidence is under `dev-server/.vapeecore-dev/phase30f-*`: baseline/final
raw hashes, local API inspection, focused/full individual process logs/results,
isolated control logs/results, build log, artifact metadata, Paper smoke log and
summary, live before/after hashes and post-commit receipt. No development-server
artifact or operational runner is committed.

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

| File | SHA-256 baseline = final |
|---|---|
| docs/ARCHITECTURE_REFACTOR.md | `0D0DC4032A1C0CAA95855DC7DBB39BE1D794E5029C973B638F7D54B6683A404A` |
| docs/CONFIG_REGISTRATION_MODULE_CLEANUP.md | `351F9AE8CA6E3DE6C5964A0A408583C18F4637947C402E4AD16B92443C9F1B24` |
| docs/DUPLICATION_MAINTAINABILITY_CLEANUP.md | `B395ACB7474FFDEAC2ED2493D185D9AE298E1CC8E0AA55C4C470DD020F8D50EA` |
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

## Complete executable harness inventory

Each row is one separate successful Java process; zero skipped executable harnesses.

| Class | Checks | Exit |
|---|---:|---:|
| dev.vapee.core.activity.ActivityHarness | 153 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackFairnessHarness | 393 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackHarness | 317 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackPresentationHarness | 123 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackReadViewHarness | 21 | 0 |
| dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness | 16 | 0 |
| dev.vapee.core.activity.blackjack.table.BlackjackTableHarness | 90 | 0 |
| dev.vapee.core.chat.ChatHarness | 17 | 0 |
| dev.vapee.core.chat.config.ChatConfigHarness | 30 | 0 |
| dev.vapee.core.clan.ClanDomainHarness | 52 | 0 |
| dev.vapee.core.clan.ClanIntegrationHarness | 15 | 0 |
| dev.vapee.core.clan.ClanPersistenceHarness | 39 | 0 |
| dev.vapee.core.clan.ClanServiceHarness | 136 | 0 |
| dev.vapee.core.clan.gui.ClanCommandHarness | 99 | 0 |
| dev.vapee.core.clan.gui.ClanMenuHarness | 84 | 0 |
| dev.vapee.core.clan.gui.ClanMenuSecurityHarness | 114 | 0 |
| dev.vapee.core.command.CoreCommandHarness | 75 | 0 |
| dev.vapee.core.command.OnlineStaffTargetGuardHarness | 268 | 0 |
| dev.vapee.core.config.ConfigHelpersHarness | 33 | 0 |
| dev.vapee.core.config.ConfigServiceHarness | 93 | 0 |
| dev.vapee.core.config.DefaultConsistencyHarness | 12 | 0 |
| dev.vapee.core.economy.command.CoinsCommandHarness | 1951 | 0 |
| dev.vapee.core.economy.EconomyIntegrationHarness | 27 | 0 |
| dev.vapee.core.economy.EconomyServiceHarness | 44 | 0 |
| dev.vapee.core.friend.FriendCommandHarness | 96 | 0 |
| dev.vapee.core.friend.FriendDomainHarness | 29 | 0 |
| dev.vapee.core.friend.FriendIntegrationHarness | 28 | 0 |
| dev.vapee.core.friend.FriendLifecycleHarness | 12 | 0 |
| dev.vapee.core.friend.FriendPersistenceHarness | 28 | 0 |
| dev.vapee.core.friend.FriendServiceHarness | 79 | 0 |
| dev.vapee.core.friend.gui.FriendMenuHarness | 159 | 0 |
| dev.vapee.core.friend.gui.FriendMenuSecurityHarness | 114 | 0 |
| dev.vapee.core.identity.IdentityCommandArgumentHarness | 47 | 0 |
| dev.vapee.core.identity.IdentityHarness | 26 | 0 |
| dev.vapee.core.identity.ProfileHarness | 19 | 0 |
| dev.vapee.core.lobby.command.LobbyCommandHarness | 26 | 0 |
| dev.vapee.core.lobby.config.LobbyConfigHarness | 45 | 0 |
| dev.vapee.core.lobby.experience.navigator.NavigatorMenuHarness | 113 | 0 |
| dev.vapee.core.lobby.experience.navigator.NavigatorSecurityHarness | 82 | 0 |
| dev.vapee.core.lobby.player.LobbyHarness | 53 | 0 |
| dev.vapee.core.lobby.warp.command.WarpCommandHarness | 86 | 0 |
| dev.vapee.core.lobby.warp.WarpHarness | 118 | 0 |
| dev.vapee.core.message.CommandHelpHarness | 85 | 0 |
| dev.vapee.core.moderation.command.ModerationCommandHarness | 4414 | 0 |
| dev.vapee.core.moderation.command.ModerationDurationHarness | 53 | 0 |
| dev.vapee.core.moderation.ModerationBanEnforcementHarness | 31 | 0 |
| dev.vapee.core.moderation.ModerationDomainHarness | 112 | 0 |
| dev.vapee.core.moderation.ModerationLifecycleHarness | 149 | 0 |
| dev.vapee.core.moderation.ModerationMuteEnforcementHarness | 52 | 0 |
| dev.vapee.core.moderation.ModerationMuteProjectionHarness | 20 | 0 |
| dev.vapee.core.moderation.ModerationPersistenceHarness | 523 | 0 |
| dev.vapee.core.moderation.ModerationServiceHarness | 141 | 0 |
| dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness | 42 | 0 |
| dev.vapee.core.onlinereward.OnlineRewardServiceHarness | 42 | 0 |
| dev.vapee.core.permission.LuckPermsAsyncHarness | 16 | 0 |
| dev.vapee.core.permission.PermissionDescriptorHarness | 840 | 0 |
| dev.vapee.core.player.repository.PlayerQuestPersistenceHarness | 21 | 0 |
| dev.vapee.core.player.repository.PlayerRewardPersistenceHarness | 11 | 0 |
| dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness | 17 | 0 |
| dev.vapee.core.player.settings.PlayerSettingsServiceHarness | 28 | 0 |
| dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness | 11 | 0 |
| dev.vapee.core.presence.FriendPresenceNotifierHarness | 8 | 0 |
| dev.vapee.core.presence.PresenceLifecycleHarness | 14 | 0 |
| dev.vapee.core.presence.PresenceServiceHarness | 15 | 0 |
| dev.vapee.core.presentation.config.PresentationConfigHarness | 32 | 0 |
| dev.vapee.core.presentation.PlaytimeFormatterHarness | 14 | 0 |
| dev.vapee.core.presentation.PresentationHarness | 10 | 0 |
| dev.vapee.core.presentation.PresentationLifecycleHarness | 38 | 0 |
| dev.vapee.core.presentation.scoreboard.ScoreboardOwnershipHarness | 17 | 0 |
| dev.vapee.core.presentation.tablist.TablistOwnershipHarness | 58 | 0 |
| dev.vapee.core.privatemessage.config.PrivateMessageConfigHarness | 26 | 0 |
| dev.vapee.core.privatemessage.PrivateMessageSocialHarness | 117 | 0 |
| dev.vapee.core.quest.daily.command.QuestCommandHarness | 45 | 0 |
| dev.vapee.core.quest.daily.DailyQuestConfigHarness | 35 | 0 |
| dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness | 18 | 0 |
| dev.vapee.core.quest.daily.DailyQuestLifecycleHarness | 16 | 0 |
| dev.vapee.core.quest.daily.menu.QuestMenuHarness | 20 | 0 |
| dev.vapee.core.quest.daily.menu.QuestMenuSecurityHarness | 45 | 0 |
| dev.vapee.core.quest.DailyQuestServiceHarness | 20 | 0 |
| dev.vapee.core.quest.QuestDefinitionHarness | 29 | 0 |
| dev.vapee.core.quest.QuestLifecycleHarness | 42 | 0 |
| dev.vapee.core.quest.QuestPlaytimeProducerHarness | 18 | 0 |
| dev.vapee.core.quest.QuestProgressReporterHarness | 18 | 0 |
| dev.vapee.core.quest.QuestServiceHarness | 44 | 0 |
| dev.vapee.core.rank.RankCommandHarness | 16 | 0 |
| dev.vapee.core.rank.RanksCommandHarness | 12 | 0 |
| dev.vapee.core.rank.RankServiceHarness | 23 | 0 |
| dev.vapee.core.rank.staff.StaffHierarchyServiceHarness | 109 | 0 |
| dev.vapee.core.reward.RewardLifecycleHarness | 40 | 0 |
| dev.vapee.core.reward.RewardServiceHarness | 126 | 0 |
| dev.vapee.core.seat.SeatHarness | 52 | 0 |
| dev.vapee.core.settings.command.SettingsCommandHarness | 36 | 0 |
| dev.vapee.core.settings.SettingsMenuHarness | 66 | 0 |
| dev.vapee.core.settings.SettingsMenuSecurityHarness | 111 | 0 |
| dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness | 23 | 0 |
| dev.vapee.core.settings.visibility.SettingsNavigationHarness | 17 | 0 |
| dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness | 195 | 0 |
| dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness | 29 | 0 |
| dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness | 74 | 0 |
| dev.vapee.core.ui.PaginationHarness | 1409 | 0 |
| dev.vapee.core.ui.UiItemsHarness | 17 | 0 |
| dev.vapee.core.utility.command.BuildCommandHarness | 17 | 0 |
| dev.vapee.core.utility.command.NameSuggestionsHarness | 8 | 0 |
| dev.vapee.core.utility.command.TeleportCommandHarness | 894 | 0 |
| dev.vapee.core.utility.command.UtilityCommandHarness | 1302 | 0 |
| dev.vapee.core.utility.TeleportParserHarness | 58 | 0 |
| dev.vapee.core.utility.UtilityInventoryHarness | 742 | 0 |
| dev.vapee.core.utility.UtilityModuleLifecycleHarness | 452 | 0 |
| dev.vapee.core.utility.UtilityServiceHarness | 35 | 0 |
| dev.vapee.core.visibility.FriendVisibilityRefreshHarness | 11 | 0 |
| dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness | 7 | 0 |
| dev.vapee.core.visibility.VisibilityModuleHarness | 22 | 0 |
| dev.vapee.core.visibility.VisibilityPolicyHarness | 28 | 0 |
| dev.vapee.core.visibility.VisibilityServiceHarness | 10 | 0 |
| dev.vapee.core.worlddisplay.WorldDisplayHarness | 27 | 0 |
