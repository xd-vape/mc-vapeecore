# Phase 30B – Architecture Refactor

## Baseline and scope

- Repository: `https://github.com/xd-vape/mc-vapeecore`.
- Branch: `phase/30b-architecture-refactor`.
- Exact initial HEAD: `b8921d5fa34fc2226a978ef987c516e3e5a3f1a7`.
- Initial subject: `Merge Phase 30A.1 - Moderation Authorization Freshness Hotfix`.
- Initial working tree: clean; 453 tracked files captured with SHA-256 before edits.
- Exactly one worker, the existing checkout, no delegation or new worktree.
- Baseline: 101 executable harnesses, 15,668 checks, zero failures.

This change resolves **API-001 only**, the low-severity concrete Blackjack read
boundary finding. The engine continues to own mutable domain state and coordinate
normal gameplay mutations. Presentation consumes immutable, detached read views.
This is encapsulation and API clarity, not a Java plugin or reflection sandbox.

The full audit, developer guide, Quest Completion report, moderation hotfix report,
Blackjack implementation, relevant table/activity integration and all five existing
Blackjack harnesses/fixtures were reviewed. The caller audit
preceded all edits. The historical 687-line commented harness remains intact.

## Root cause and old escape paths

The public API returned the actual engine objects:

1. `session.getDealerHand().add(card)` could change the dealer hand.
2. `session.getPlayerRound(id).orElseThrow().getHand().add(card)` could change a player hand.
3. `session.getPlayerRounds().get(id).getHand().add(card)` did the same through a map.
4. `session.getCurrentShoe().orElseThrow().draw()` could consume the next engine card.
5. `BlackjackPlayerRound#getHand` continued the mutable escape from either round read.

`Map.copyOf(playerRounds)` protected the map structure but retained mutable round
values. Existing presentation callers only read them, but the API permitted future
consumers to bypass current-turn/phase/settlement checks, refresh and scheduler
generation handling. No malicious current caller or live exploit is claimed.

## Complete caller inventory

Repository-wide search covered `getCurrentShoe`, `getDealerHand`, `getPlayerRounds`,
`getPlayerRound`, `getHand` and direct `BlackjackHand`, `BlackjackShoe`,
`BlackjackPlayerRound` type uses, including tests and documentation. A/B denote
engine mutation/internal read, C presentation, D test setup/assertions and E other.
The ignored baseline line-by-line evidence is
`dev-server/.vapeecore-dev/phase30b-baseline-callers.txt`.

| Class / callers | Category | Baseline use and disposition |
|---|---|---|
| `BlackjackSession` constructor, `beginRound`, `clearRoundData` | A | Owns/replaces dealer hand, shoe and ordered round map; unchanged mutation logic. |
| Session raw getters, `requirePlayerRound`, `getPlayerRoundsInOrder`, `requireShoe` | B | Internal object access; narrow only the four formerly public raw getters. Existing package helpers remain unchanged. |
| `BlackjackPlayerRound` constructor, `finish`, `settle`, `markDoubledDown` | A | Owns player hand/flags/outcome; unchanged mutation logic. |
| `BlackjackPlayerRound#getHand` and safe scalar/outcome getters | B | Narrow hand getter only. Other safe public getters retain visibility. |
| `BlackjackService` constructors / shoe supplier | A | Creates/injects six-deck mutable shoes; unchanged. |
| Service `hit`, `doubleDown`, `onActivated`, `runDealerTurn` | A/B | Draw/add/read hands, finish/double and deal in participant order; unchanged. |
| Service `stand`, `onParticipantLeft`, `advanceTurn`, `settleRound` | A/B | Finish/remove/select/settle mutable rounds, read dealer hand and outcomes; unchanged. |
| Service `shouldDealerHit`, `requiresDealerPlay` | B | Reads mutable internal hands and ordered rounds; unchanged. Public `shouldDealerHit` accepts a hand but returns only boolean, never an engine reference. |
| `BlackjackOutcome#determine` | B | Accepts hands and returns an immutable enum; unchanged. |
| `BlackjackHand` constructors / `add` / scoring / `getCards` | A/B | Mutable domain collection with immutable card-list reads; unchanged. |
| `BlackjackShoe` constructors / `sixDecks` / `draw` / remaining reads | A/B | Domain shuffle/draw state; unchanged. No outside-engine production consumer of current shoe exists. |
| `BlackjackInventoryService#canDoubleDown`, `statusItem` | C | Per-player round flags/card count/natural/value; migrated to round/hand views. |
| `BlackjackWorldViewService#addDealer`, `dealerDisplays`, `dealerHandText` | C | Dealer cards/value and a temporary one-card hand for hidden value; migrated to hand view and existing rank value. |
| World view `addPlayer`, `playerLabelText` | C | Player cards/value/outcome; migrated to round/hand views. |
| `BlackjackHarness`, active main/tests/private `Fixture` | D | Same-package domain scoring, deterministic shoes, deal/turn/reset/leave/double and Quest assertions; legitimate raw engine fixture reads retained. Adds real-service Session snapshot tests. |
| `BlackjackHarness`, historical commented block | D, inactive | Old hand/shoe/round reads and retired UI references are not compiled; byte-preserved, not counted as another harness. |
| `BlackjackFairnessHarness` | D | Independent mutable hands/shoes for distribution, draw and scoring assertions; unchanged. |
| `BlackjackPresentationHarness` | D | Builds domain inputs and reflects private renderer helpers; now passes views to migrated helper signatures. Adds output parity tests. |
| `BlackjackTableHarness#blackjackService` / `RuntimeFixture` | D | Reflective existing package constructor injects `Supplier<BlackjackShoe>`; no mutable Session read outside the engine package, unchanged. |
| `BlackjackPreviewHarness` / preview fixtures | D | Draft/geometry/display markers only; no mutable round/hand/shoe read, unchanged. |
| `BlackjackModule`, `BlackjackActivityType`, `BlackjackTableService` and lifecycle callbacks | E | Session creation/lifecycle/participant reads, presentation wiring and enum-based Quest adapter; no additional mutable-read migration. |
| Table definitions/drafts/seats/anchors, `BlackjackSeatService`, `BlackjackDisplayGeometry`, `BlackjackPreviewService` | C/E | Geometry, immutable table values, separate administrative draft copies and activity/seat ownership; no engine-hand/round/shoe consumer, unchanged. |
| `ActivitySession`, `ActivityParticipant`, Quest callback integration | E | Existing activity state/immutable participants and UUID/outcome values; unchanged. |
| `LobbyItemListener`, `SeatListener`, `BlackjackTableListener` `event.getHand()` | E, unrelated | Bukkit `EquipmentSlot`, not Blackjack hands; excluded after inspection, unchanged. |
| Developer guide / full audit matches | E, documentation | One current ownership paragraph added to guide; historical audit unchanged. README contains no false concrete read API claim and remains unchanged. |

There are no other production consumers of the mutable read API. Newly introduced
`BlackjackHandView#from`, round `toView` and Session view methods are conversion
reads next to the owning domain. The new read-view harness and presentation fixture
are test consumers only. No command, listener, table, preview or Quest API had to change.

## Read models and visibility

| New record | Fields |
|---|---|
| `card.BlackjackHandView` | `List<BlackjackCard> cards`, `int value`, `boolean blackjack` |
| `BlackjackPlayerRoundView` | `UUID playerId`, `BlackjackHandView hand`, `boolean finished`, `boolean doubledDown`, `Optional<BlackjackOutcome> outcome` |

The hand canonical constructor uses `List.copyOf` and a non-null input. Existing
`BlackjackCard` is an immutable record of immutable rank/suit enums, so copying card
objects or introducing card DTOs would add no isolation. Cards preserve their order.
The factory reads the existing domain scoring methods; no second hand evaluator is
introduced. Soft/bust fields are unnecessary for current presentation consumers.

The round constructor rejects null player/hand/optional. `Optional<BlackjackOutcome>`
contains only an immutable enum. Package-local round `toView()` creates a detached
hand plus captured flags/outcome. Session creates a fresh map of these immutable
values and finishes with `Map.copyOf`. Old lists, map values and optionals do not
change on hit, dealer draw, finish, double, settle or reset; subsequent reads reflect
new state. Capture occurs on the existing main-thread domain boundary; no new async
guarantee, caching, scheduler or cross-thread transaction is introduced.

| Old public method | New visibility / replacement | Reason |
|---|---|---|
| Session `getCurrentShoe(): Optional<BlackjackShoe>` | package-private; no public replacement | No production read consumer outside engine needs it. Exposing even an optional shoe permits `draw`; no speculative ShoeView. |
| Session `getDealerHand(): BlackjackHand` | package-private; public `getDealerHandView()` | Engine deal/draw/scoring still need raw hand; presentation needs only detached cards/value. |
| Session `getPlayerRounds(): Map<UUID, BlackjackPlayerRound>` | package-private; public `getPlayerRoundViews()` | Existing same-package test reads remain valid; public values must also be immutable, not merely the map. |
| Session `getPlayerRound(UUID): Optional<BlackjackPlayerRound>` | package-private; public `getPlayerRoundView(UUID)` | Same-package engine fixtures retain raw access; both external presentation callers migrate to snapshots. Null IDs remain rejected. |
| Round `getHand(): BlackjackHand` | package-private; public view `hand()` | Engine/tests mutate domain hand; view returns only `BlackjackHandView`. |

These are the only reduced visibilities. Session state/turn/generation/participant
reads, round scalar/outcome reads and `Optional<BlackjackSession>` lifecycle APIs
remain available. `BlackjackHand#add` and `BlackjackShoe#draw` stay public so the
parent engine package can use the card subpackage. Nothing moves packages.
Two concrete records suffice; no full SessionView, mapper framework, facade,
registry, service locator or public test seam is added.

## Presentation and engine parity

Inventory uses record accessors in exactly the prior double/status conditions.
Slots remain 0 Deal/Hit, 1 Stand, 2 Double, 4 Status, 8 Leave; names, materials,
lore, action/managed PDC, current-turn gating and ownership lifecycle are unchanged.
Double still requires an own active player turn, existing unfinished/non-doubled
round, exactly two cards and no natural. Status displays the captured domain value.

World view changes only its data source. Player cards remain ordered, dealer hole
card is hidden only during PLAYER_TURNS, results and turn/status retain prior text,
styles and geometry. A single visible dealer card uses `card.rank().getValue()`:
this equals the old one-card hand calculation for every rank (ace is 11). All 13
ranks are regression-tested against the old domain calculation. Revealed totals
still come from the canonical hand evaluator captured in the view.

The single `dealer-hand` key persists across dealer card growth. Repeated refresh
does not create extra entities. Existing VALUE-to-RESULT style replacement is
retained. `WorldDisplayService`, including the separately audited DISPLAY-001
teleport behavior, is unchanged.

`BlackjackService`, Hand, Shoe, Outcome, RoundPhase and Module remain byte-identical:
IDLE → PLAYER_TURNS → DEALER_TURN → SETTLED, natural/bust/ace/soft-17 evaluation,
six-deck Fisher-Yates shuffle and drawing, double-down, turn and round generations,
400-tick turn timeout, 60-tick reset, leave/disconnect/reset and settlement retain
the same code. Phase-29 callback still emits WIN/BLACKJACK once per player/round
through the same Quest adapter; callbacks for PUSH/LOSS/BUST do not advance quests.
No bets, coins, gameplay feature or persistence change is introduced.

## Regression evidence

Tests first: the new public return-type test was compiled and executed against the
unchanged production baseline. It failed with all five expected raw getters, before
the fix. The test traverses parameterized return types (including Map/Optional),
arrays, bounds and nested record components, including inherited public methods.
It now includes both view records and checks private-final fields.

- `BlackjackReadViewHarness`: detached hand/round snapshots, central scoring,
  defensive constructor copying, list add/set/clear rejection, null rejection,
  finish/double/settle snapshots and recursive public API contract.
- `BlackjackHarness`: actual Service double/settle/dealer draw/reset with retained
  Session optional/map/dealer views, immutable map/entries/nested card values,
  current reads, unknown/null IDs, plus all existing gameplay and Quest cases.
- `BlackjackPresentationHarness`: existing geometry/text/color/style tests plus
  exact inventory slots/materials/names/lore/PDC/actions, double denial cases,
  actual ownership/release/BUILD/shutdown, dealer/player/status/outcome output,
  stable display IDs and the single expected result-style replacement.
- `BlackjackPresentationFixture`: real InventoryService, LobbyPlayerStateService,
  WorldViewService and WorldDisplayService; substitutes external Paper surfaces.
  Paper's item registry and plugin shell use process-local test reflection because
  ItemStack creation needs server support. This fixture has no main, does not
  alter production API and is not counted as another executable harness. World
  gateway counts creates/removals and checks swallowed renderer failures.

Focused run completed first: **9 harnesses, 1,192 checks, zero failures/skips**:

| Harness | Checks |
|---|---:|
| ActivityHarness | 153 |
| BlackjackFairnessHarness | 393 |
| BlackjackHarness | 317 |
| BlackjackPresentationHarness | 123 |
| BlackjackReadViewHarness | 21 |
| BlackjackPreviewHarness | 16 |
| BlackjackTableHarness | 90 |
| SeatHarness | 52 |
| WorldDisplayHarness | 27 |

Full regression on 2026-10-04: **102 harnesses, 15,788 checks, zero failures,
zero skipped executable harnesses**. Every active `public static void main` in a
`*Harness.java` ran as its own Java process. Maven package is not counted as a
harness execution. The runner ignores commented historical main methods.

Delta from baseline: BlackjackHarness +17, BlackjackPresentationHarness +80,
new BlackjackReadViewHarness +21, PermissionDescriptorHarness +2 = **120** checks.
The unchanged descriptor harness scans every production Java file; the two view
records explain its increase from 830 to 832 without a permission change.

ModerationCommandHarness retains **4,344** checks, including the Phase-30A.1
freshness matrix. StaffHierarchyServiceHarness (109), LuckPermsAsyncHarness (16)
and OnlineStaffTargetGuardHarness (268) also pass unchanged. All Quest/Daily,
PlayerQuestPersistence, Reward/OnlineReward and the existing real Blackjack outcome
adapter tests pass, including all five outcomes, once-only progress, rematches,
callback failure isolation, reset and unloaded profiles. Remaining complete feature
regression is listed in the final inventory below.

Ignored evidence: `dev-server/.vapeecore-dev/phase30b-harness-results.json`,
`phase30b-harness-logs/`, `phase30b-focused-harness-results.json`,
`phase30b-focused-harness-logs/` and the expected initial `phase30b-red-api.log`.

## Final build and artifact

Executed exactly:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

**BUILD SUCCESS**, exit 0, 11.821 seconds, completed 2026-10-04 19:33:57
Europe/Berlin. Java 21.0.12.1. Existing javac annotation-processing/deprecated-API
notices remain informational. No Maven/dependency changes.

| Artifact | Value |
|---|---|
| Final JAR | `target/vapeecore-1.0-SNAPSHOT.jar` |
| Byte size | **1,053,701** |
| SHA-256 | `0281CE98D52BC639E3BB2F0FA62937EA784BA9FEA902F470DE999316F87977AF` |
| Local smoke deploy | `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar` |
| Deploy SHA-256 | `0281CE98D52BC639E3BB2F0FA62937EA784BA9FEA902F470DE999316F87977AF` |

The deployed bytes match the final artifact. Zero bundled `org/bukkit/` or
`net/luckperms/` entries. Harnesses ran against the final source before this clean
package; it rebuilt both production and test classes. No Java source changed afterward.

## Real Paper smoke

Final artifact started on **Paper 1.21.11 build 132**, commit `c5eb079`, API
`1.21.11-R0.1-SNAPSHOT`, Oracle Java **21.0.12.1+1-LTS-4**, Windows 11,
required LuckPerms **5.5.84**. Startup 19:34:44, Ready 19:34:59 on 2026-10-04
Europe/Berlin (15.966 seconds). LP connected and all **27 modules** enabled.

Console probes: `version`, `lp info`, `list`, `core`, `core version`, `core help`,
`core reload`, `bukkit:help VapeeCore`, then `stop`.

- `/core`: Running, 27 active modules, LP connected, zero loaded/online players.
- Help: exactly **37 roots**, exact set equal to the baseline descriptor.
- `/core reload`: exactly **6** prepares (config, lobby, chat, private-messages,
  presentation, daily-quests); successful apply in **14 ms**.
- Descriptor remains **50 permissions**, **15 positive child edges**; byte-identical
  `plugin.yml` and the unchanged descriptor harness independently verify the contract.
- `stop` at 19:36:08; all 27 modules disabled in exact reverse order, LP storage
  closed, worlds saved, process exit **0** at 19:36:09. The server is stopped.
- **0 ERROR/SEVERE** entries across startup, commands, reload and shutdown.
- All **8** live VapeeCore YAML files retain their pre-start SHA-256;
  no moderation.yml was created. No live table, round, LP group or player mutation.

Paper emitted its existing reminder about newer Minecraft versions. Spark selected
its Java engine on Windows. Neither is a VapeeCore error. The final smoke log and
machine-checked summary are `dev-server/.vapeecore-dev/phase30b-paper-smoke.log`
and `phase30b-smoke-summary.json`.

Live client test: not performed.

The real server test verifies loading, registration, reload and shutdown. Gameplay
and display/GUI output were tested in isolated harnesses; no connected client or
live Blackjack round is claimed.

## Scope, remaining findings and delivery

Exact file inventory (repository-relative):

| Status | File |
|---|---|
| Added | `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackHandView.java` |
| Added | `src/main/java/dev/vapee/core/activity/blackjack/BlackjackPlayerRoundView.java` |
| Changed | `src/main/java/dev/vapee/core/activity/blackjack/BlackjackSession.java` |
| Changed | `src/main/java/dev/vapee/core/activity/blackjack/BlackjackPlayerRound.java` |
| Changed | `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackInventoryService.java` |
| Changed | `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackWorldViewService.java` |
| Added | `src/test/java/dev/vapee/core/activity/blackjack/BlackjackReadViewHarness.java` |
| Added | `src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationFixture.java` |
| Changed | `src/test/java/dev/vapee/core/activity/blackjack/BlackjackHarness.java` |
| Changed | `src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationHarness.java` |
| Changed | `docs/DEVELOPER_GUIDE.md` (one current Blackjack ownership paragraph) |
| Added | `docs/ARCHITECTURE_REFACTOR.md` |

**5 added, 7 changed, 0 removed**. Comparing every baseline file's raw SHA-256
found exactly these seven existing files changed and the other **446 byte-identical**.
The 687-line historical Blackjack comment was separately compared byte-for-byte
against reconstructed baseline bytes whose whole-file SHA matches the initial
snapshot; it is unchanged.

`plugin.yml`, every other resource/default YAML, README, pom.xml and all persistence
code/schema (including blackjack.yml format) remain unchanged. No files in
Moderation, Permission, Rank, Player, Economy, Reward, Quest, Friends, Clan,
Presence, Lobby, Warp, Settings, Visibility, Utility, Seat or WorldDisplay change.
The module/reload graph stays **27/6**, commands/permissions/edges **37/50/15**.

| Protected file | Unchanged SHA-256 |
|---|---|
| `docs/FULL_CODEBASE_AUDIT.md` | `73F7FBF38E4DD01F5A3328275A8045C9FB3812D4B4BF6BA4D817458F1FFEA2CC` |
| `docs/MODERATION_AUTHORIZATION_HOTFIX.md` | `99735909CC7A5B05F5CBE1DA6D01E563061988EC98F6A16B6F8B3D84BA09982A` |
| `src/main/resources/plugin.yml` | `1E4A9591559E669CD5753812885B0604A03CE070F48BD1DE39AD95B505D9DF5C` |
| `src/main/resources/config.yml` | `37ECEFAC7B0EFCFB006718D4A350066138733809DD53C4B2FD2DA5E585566DC4` |

API-001 is the only finding addressed. MOD-001 remains resolved by Phase 30A.1;
Moderation/StaffHierarchy production and tests are untouched. These **18** findings
remain intentionally open in their assigned phases:

`SEC-001`, `DISPLAY-001`, `PERSIST-001`, `PERSIST-002`, `GUI-001`, `GUI-002`,
`LIFE-001`, `CONFIG-001`, `CMD-001`, `DUP-001`, `PRES-001`, `PRES-002`,
`LIFE-002`, `PERF-001`, `TEST-001`, `TEST-002`, `DOC-001`, `LEGACY-001`.

The full audit continues to document its historical 20 findings. Both
`FULL_CODEBASE_AUDIT.md` and `MODERATION_AUTHORIZATION_HOTFIX.md` are preserved.
Phase 30C concerns GUI-001/GUI-002 and is not started here. Team teleport consent
remains later Phase 30G work. No GUI framework, security fix, general architecture
cleanup, scheduler/persistence/config/command/permission change is included.

Delivery requires exactly one local commit `Phase 30B - Architecture Refactor`,
with no push, rebase, merge, reset or history rewrite. Final commit SHA and clean
one-commit distance from the exact baseline are reported outside this self-referential
document. The complete tracked and added-file diff, `git diff --check`, status and
stat are reviewed before that commit. Phase ends after validation, local commit
and completion report.

**API-001: resolved. MOD-001: remains resolved.** No known new production defect
was observed. Known limitations are the absent live-client test, unchanged other
audit findings and ordinary Java encapsulation rather than sandbox security.
**Merge readiness: ready**, with all validation above green. **Phase 30C readiness:
ready for separately authorized GUI-001/GUI-002 work; not started.**

## Complete executed harness inventory

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
| dev.vapee.core.clan.ClanDomainHarness | 52 | 0 |
| dev.vapee.core.clan.ClanIntegrationHarness | 15 | 0 |
| dev.vapee.core.clan.ClanPersistenceHarness | 39 | 0 |
| dev.vapee.core.clan.ClanServiceHarness | 136 | 0 |
| dev.vapee.core.clan.gui.ClanCommandHarness | 83 | 0 |
| dev.vapee.core.clan.gui.ClanMenuHarness | 84 | 0 |
| dev.vapee.core.clan.gui.ClanMenuSecurityHarness | 37 | 0 |
| dev.vapee.core.command.CoreCommandHarness | 75 | 0 |
| dev.vapee.core.command.OnlineStaffTargetGuardHarness | 268 | 0 |
| dev.vapee.core.config.ConfigServiceHarness | 74 | 0 |
| dev.vapee.core.config.DefaultConsistencyHarness | 12 | 0 |
| dev.vapee.core.economy.command.CoinsCommandHarness | 1942 | 0 |
| dev.vapee.core.economy.EconomyIntegrationHarness | 27 | 0 |
| dev.vapee.core.economy.EconomyServiceHarness | 44 | 0 |
| dev.vapee.core.friend.FriendCommandHarness | 80 | 0 |
| dev.vapee.core.friend.FriendDomainHarness | 29 | 0 |
| dev.vapee.core.friend.FriendIntegrationHarness | 28 | 0 |
| dev.vapee.core.friend.FriendLifecycleHarness | 12 | 0 |
| dev.vapee.core.friend.FriendPersistenceHarness | 28 | 0 |
| dev.vapee.core.friend.FriendServiceHarness | 79 | 0 |
| dev.vapee.core.friend.gui.FriendMenuHarness | 159 | 0 |
| dev.vapee.core.friend.gui.FriendMenuSecurityHarness | 37 | 0 |
| dev.vapee.core.identity.IdentityHarness | 26 | 0 |
| dev.vapee.core.identity.ProfileHarness | 19 | 0 |
| dev.vapee.core.lobby.command.LobbyCommandHarness | 26 | 0 |
| dev.vapee.core.lobby.experience.navigator.NavigatorMenuHarness | 113 | 0 |
| dev.vapee.core.lobby.experience.navigator.NavigatorSecurityHarness | 82 | 0 |
| dev.vapee.core.lobby.player.LobbyHarness | 53 | 0 |
| dev.vapee.core.lobby.warp.command.WarpCommandHarness | 86 | 0 |
| dev.vapee.core.lobby.warp.WarpHarness | 118 | 0 |
| dev.vapee.core.message.CommandHelpHarness | 85 | 0 |
| dev.vapee.core.moderation.command.ModerationCommandHarness | 4344 | 0 |
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
| dev.vapee.core.permission.PermissionDescriptorHarness | 832 | 0 |
| dev.vapee.core.player.repository.PlayerQuestPersistenceHarness | 21 | 0 |
| dev.vapee.core.player.repository.PlayerRewardPersistenceHarness | 11 | 0 |
| dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness | 17 | 0 |
| dev.vapee.core.player.settings.PlayerSettingsServiceHarness | 28 | 0 |
| dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness | 11 | 0 |
| dev.vapee.core.presence.FriendPresenceNotifierHarness | 8 | 0 |
| dev.vapee.core.presence.PresenceLifecycleHarness | 14 | 0 |
| dev.vapee.core.presence.PresenceServiceHarness | 15 | 0 |
| dev.vapee.core.presentation.PlaytimeFormatterHarness | 14 | 0 |
| dev.vapee.core.presentation.PresentationHarness | 10 | 0 |
| dev.vapee.core.privatemessage.PrivateMessageSocialHarness | 117 | 0 |
| dev.vapee.core.quest.daily.command.QuestCommandHarness | 45 | 0 |
| dev.vapee.core.quest.daily.DailyQuestConfigHarness | 13 | 0 |
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
| dev.vapee.core.settings.command.SettingsCommandHarness | 25 | 0 |
| dev.vapee.core.settings.SettingsMenuHarness | 66 | 0 |
| dev.vapee.core.settings.SettingsMenuSecurityHarness | 34 | 0 |
| dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness | 23 | 0 |
| dev.vapee.core.settings.visibility.SettingsNavigationHarness | 17 | 0 |
| dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness | 41 | 0 |
| dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness | 29 | 0 |
| dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness | 74 | 0 |
| dev.vapee.core.utility.command.BuildCommandHarness | 17 | 0 |
| dev.vapee.core.utility.command.TeleportCommandHarness | 894 | 0 |
| dev.vapee.core.utility.command.UtilityCommandHarness | 1221 | 0 |
| dev.vapee.core.utility.TeleportParserHarness | 58 | 0 |
| dev.vapee.core.utility.UtilityInventoryHarness | 742 | 0 |
| dev.vapee.core.utility.UtilityServiceHarness | 35 | 0 |
| dev.vapee.core.visibility.FriendVisibilityRefreshHarness | 11 | 0 |
| dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness | 7 | 0 |
| dev.vapee.core.visibility.VisibilityModuleHarness | 22 | 0 |
| dev.vapee.core.visibility.VisibilityPolicyHarness | 28 | 0 |
| dev.vapee.core.visibility.VisibilityServiceHarness | 10 | 0 |
| dev.vapee.core.worlddisplay.WorldDisplayHarness | 27 | 0 |
