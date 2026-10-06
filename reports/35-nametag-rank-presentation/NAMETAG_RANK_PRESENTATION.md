# Phase 35 – Nametag and Rank Presentation

## Delivery and acceptance

Implementation complete. Automated acceptance passed; visual multiplayer
acceptance pending. **Live client test: not performed.** Server smoke and API
fixtures do not prove client-side rendering. Resource-pack badges remain an
explicitly deferred optional feature. Phase 36 was not started.

Baseline: `d32876547f13c015a3d03fe7bb31b6c5f94f87d4`.
Branch: `phase/35-nametag-rank-presentation`. Initial working tree clean;
branch, exact HEAD, status and last 20 commits checked before changes. One worker
in the existing checkout; no subagents, new branch/worktree, merge, rebase or push.
Raw hashes captured for all 500 baseline tracked files and ten active live YAMLs.

## Architecture and inherited contracts

Phase 30F's literal raw `<clan_tag>`, exact current-viewer board identity and
per-field tablist previous/lastWritten/guarded restore contracts are retained.
The historical 30F and 34 reports remain unchanged. The existing bootstrap
supplies `UUID -> ClanService.getClanOf(UUID).map(Clan::tag)`. Clan starts before
Presentation and stops after it. Rank, Clan and Visibility production code,
bootstrap, PresentationModule/Listener and TablistService are byte-identical.

`ScoreboardService` remains the sole board owner/acquirer. `NametagService`
consumes only its verified owned board; it never assigns a board. Presentation
combines the existing renderer, shared board owner, nametag team reconciler and
tablist writer. There is one existing synchronous Presentation timer, default
20 ticks, plus the existing one-shot join update. No extra poller, follow task,
entity, NMS, packet implementation, cache/database, player setting or event bus.

### Local Paper API evidence

Inspected actual local Paper **1.21.11** API and build-132 CraftScoreboard,
CraftTeam and CraftScoreboardComponent bytecode with Java 21 javap. Evidence is
under ignored `dev-server/.vapeecore-dev/phase35-*-api.txt`,
`phase35-team-options.txt` and `phase35-team-handle.txt`.

* `prefix`/`suffix` use Adventure Components. CraftTeam converts Components
  through PaperAdventure; these setters have no legacy 16/64-character truncation.
  This is an API finding, not a claim of unlimited client text rendering.
* `registerNewTeam` checks non-null, uniqueness and a maximum of **32767 Java
  string characters**. IDs here are deterministic `vc_n_` plus the complete UUID
  without hyphens: **37 ASCII characters**, unique per target on each viewer board.
  Rank names and clan tags are never technical IDs or ownership proofs.
* `getTeam`/`getEntryTeam` create fresh CraftTeam wrappers. Java wrapper `==`
  would be incorrect. **CraftTeam.equals compares the underlying PlayerTeam**;
  a recreated team with the same name differs. The base component retains the
  original CraftScoreboard instance, so board identity comparison uses `==`.
* `addEntry` checks non-null and delegates membership to the underlying scoreboard;
  it can move an entry from another team. Both initial creation and the immediate
  pre-add path check `getEntryTeam` first. No guessed legacy entry limit or name
  truncation is introduced; entries are actual online player names.
* `removeEntry` checks registration and actual membership; `unregister` removes
  the captured underlying team. Cleanup uses unregister only after identity and
  structure checks; it never removes membership from a foreign team.
* Color reads accept TextColor; writes require NamedTextColor. RankService also
  supports arbitrary RGB, so **no Team.color writes or approximate mapping** are
  used. Prefix Components retain their formatting; the Vanilla overhead name
  keeps its default team color. The tablist name retains RankInfo color, including
  RGB. Team options are NAME_TAG_VISIBILITY, DEATH_MESSAGE_VISIBILITY and
  COLLISION_RULE; statuses ALWAYS/NEVER/FOR_OTHER_TEAMS/FOR_OWN_TEAM.
  All options, friendly-fire and friendly-invisibility settings remain untouched.

### Shared board and independent features

Board lifetime is **Sidebar OR Nametags**. Acquisition occurs only from the exact
main/default board identity. Main is never a rendering target. Each viewer has
an independent board and every target relation has an independent team handle.

| Sidebar | Nametags | Board/structures |
|---|---|---|
| ON | ON | Own board, own sidebar objective and native teams |
| ON | OFF | Own board and objective; own teams removed |
| OFF | ON | Own board and teams; own objective removed |
| OFF | OFF | Own structures removed; return to main only if exact own board is current |

The existing per-player scoreboard preference controls **sidebar only**. Loaded
online players and default lobby/world eligibility gate nametag viewers and
targets. `nametag.lobby-only: false` is explicit operator opt-in to other worlds.
Sidebar eligibility retains its existing setting/world rules. Sidebar line-count
changes preserve the board and teams while nametags need it; sidebar-only callers
retain the existing board-recreation behavior. An externally recreated same-name
objective is also skipped instead of adopted or unregistered.

A foreign current board is never mutated, cleared or taken back. The old captured
board's demonstrably own structures are cleaned; no new ownership is acquired
until the viewer returns to a safe main/default board on normal refresh. Quit,
disable and repeated cleanup preserve a foreign currently displayed board.

### Team identity, takeover and cleanup

Ownership stores viewer UUID (map key), target UUID (relation key), exact board,
technical name, equivalent exact Team identity, actual entry/expected entry set,
read-back last prefix/suffix, requested prefix/suffix and read-only snapshots of
display name, color, flags and options. No Player is retained in ownership state.
Player handles occur only in ephemeral refresh input.

Before updating or unregistering, current team identity, board, complete entries,
entry→team relation and all observed fields must match. Same-name collision or
preexisting foreign entry membership skips that target. Foreign prefix/suffix,
color, extra entry or moved membership relinquishes metadata and leaves foreign
state untouched; there is no periodic ownership fight. A same-name recreated
foreign team survives even direct cleanup without an intervening refresh.
Identical-value writes cannot identify another plugin; observable equality is
the conservative boundary, as in the inherited tablist contract.

Only changed requested prefix/suffix are written, followed by readable Component
snapshots. Requested/read-back values are separate so normalization does not
cause repeated writes. Membership is added once, and unchanged refresh neither
unregisters/recreates teams nor writes fields. External API failures are logged
per relation; surviving active metadata can retry safe cleanup. Terminal viewer
cleanup discards its metadata after attempts rather than retaining Player state.

### Directional visibility, lifecycle and cost

Presentation reads `viewer.canSee(target)` and never calls hidePlayer/showPlayer.
VisibilityService remains the sole hide/show owner. Hidden targets are absent
from that viewer's roster and own stale relations are cleaned. A hides B while B
sees A remains asymmetric. Showing again restores the relation on normal refresh.

Join acquires the new viewer's board next tick; existing viewers see the target
on their next existing refresh. Quit removes that target from every own roster
and removes its viewer state. World changes use the existing listener and refresh;
world exit/return, unloaded/offline targets, toggles, foreign board replacement,
reload/rollback and repeated disable follow the same service paths.

Periodic rendering reads current rank/clan data once per online player, then
reconciles eligible viewer×target relations: O(players + viewers×targets) reads
and O(viewers×targets) ownership storage. Default interval remains 20 ticks.
Tests use two independent viewers and up to four initially eligible targets;
the real server smoke had zero clients/eligible viewers. Unchanged team/sidebar
refresh writes are zero, including normalized Component returns. Existing active
tablist writes retain the 30F behavior. No new player-count cap or premature pool.

## Rank, clan and tablist data flow

Cached LuckPerms prefix/suffix use the existing configured legacy-ampersand,
MiniMessage or plain renderer; RankService/RankInfo supplies public rank metadata
and tablist-name color. Neither path loads users asynchronously or derives
capabilities/staff hierarchy from rank names, prefixes, clan membership or teams.
StaffHierarchyService and permission checks remain separate and unchanged.

Canonical Clan.tag is read through the existing injected function. Null/empty
Optional, empty/blank tags or whitespace/control-containing reads produce empty
raw text. Valid tags become **Component.text**, never MiniMessage/legacy parsing.
`<clan_tag_display>` renders the trusted configured wrapper only when the literal
tag is nonempty; otherwise the entire section is Component.empty. Raw
`<clan_tag>` remains compatible across sidebar title/lines, tablist name/header/
footer and now nametag prefix/suffix. Domain membership/tag mutations become
visible at the next normal refresh. Presentation writes no Clan data and adds
no reverse dependency from Clan or Rank.

Provider exceptions remain logged failed updates, not fabricated empty domain
reads. Prior sidebar/tablist fields for that player are preserved. Nametag
relations are still reconciled against visibility and successfully rendered
eligible targets; a failed target is omitted until a later successful render.

New-installation tablist default: `<prefix><rank_name><clan_tag_display>`.
Overhead default: `<prefix>` + **actual Vanilla player-name team entry** +
`<clan_tag_display>`. Empty clans have no brackets/separators. Existing explicit
operator templates remain unchanged, including the development server's old
tablist template; operators can opt into the new template through normal reload.
TablistService's previous/lastWritten/guarded restore is unchanged: foreign
current name/header/footer survives cleanup independently. Active refresh is
still the configured writer, not general cross-plugin arbitration.

## Configuration and reload

New defaults in the resource and missing-key handling:

```yaml
clan-tag-format: " <dark_gray>[</dark_gray><gray><clan_tag></gray><dark_gray>]</dark_gray>"
nametag:
  enabled: true
  lobby-only: true
  prefix: "<prefix>"
  suffix: "<clan_tag_display>"
tablist:
  name-format: "<prefix><rank_name><clan_tag_display>"
```

The wrapper's dynamic placeholder is raw `<clan_tag>`; other presentation
placeholders belong in outer templates. No recursive template language is added.
Wrong scalar types warn and fall back locally; missing/null optional keys remain
silent. Invalid strict MiniMessage prefix/suffix fall back to empty Components,
and an invalid wrapper falls back to the valid internal wrapper. Empty operator
strings remain valid. No eager YAML rewrite, migration, schema or persistence
changes. Legacy source constructors remain compatible; explicitly constructed
old sidebar/tab-only State values keep their old feature scope.

Config and RenderState participate in the existing PresentationModule
prepare/apply/rollback transaction; still exactly six reload participants.
Preparation has no visible writes. Tests exercise the actual module, enabled
transitions, task replacement, scheduler failure before publication, and an
injected old-task cancellation failure **after** config/render publication and
native team updates. Rollback restores old feature/render state and original
timer, cancels the replacement and removes mixed state. Reload with a foreign
current board preserves it.

## Executed regression

Final standalone canonical runner: **125 discovered / 125 executed / 125 passed /
20,654 checks / 0 failures / 0 missing-invalid**; actual process exit 0.
The unchanged official build/deploy gate repeats the same 125-process runner
successfully before copying the JAR, with the same counts. All **122** Phase-34
baseline harnesses retained, **0** reduced checks and **3** newly discovered
Nametag harnesses. No manual inventory, skip flag or dependency changes.

Check reconciliation: 20,512 baseline + 71 NametagOwnership + 38 NametagRendering
+ 27 NametagLifecycle + 5 added PresentationConfig + 1 automatic new-source
PermissionDescriptor check = **20,654**. New dedicated harness total: **136**.
Focused separate run: **40 harnesses / 3,012 checks / 0 failures**.
The initial full run had 20,651 checks; the final normalized-Component and
sidebar line-count coverage adds three checks, with no removed checks.

### Six isolated negative controls

All mutations compile separate copies of real production classes ahead of the
unchanged test/production classpath. All compile exits are zero; each harness
exits **1** with its intended AssertionError, not a compile/launch failure.
Normal classes and source are untouched. Isolated copies were removed after
verifying their resolved absolute cleanup path stayed inside the ignored scratch
directory. These expected failures do not count toward passing check totals.

| Deliberate mutation | Detecting assertion |
|---|---|
| Render on main board only | nametag roster independent of sidebar |
| Treat foreign current board as rendering-owned | foreign same-name team/objective untouched |
| Cleanup by name alone | direct cleanup preserves recreated same-name foreign team |
| Ignore viewer.canSee | asymmetric visibility roster |
| Parse clan input as MiniMessage | MiniMessage-looking clan is literal on both surfaces |
| Couple board lifetime to sidebar | four-feature board ownership |

Focused coverage includes all requested Presentation/config/ownership, new
Nametag, Visibility/refresh, Rank/staff, Clan/security, descriptor, RepoLink and
ReloadService harnesses, plus direct Player settings/persistence and Settings
menu/lifecycle coverage. Config adds five checks; PresentationHarness updates
the two intended default-template assertions without reducing its ten checks.
PermissionDescriptorHarness gains one source-scan check for the new production
class, without changing descriptor or permissions.

| Executed class | Checks | Exit |
|---|---:|---:|
| dev.vapee.core.clan.ClanDomainHarness | 52 | 0 |
| dev.vapee.core.clan.ClanIntegrationHarness | 15 | 0 |
| dev.vapee.core.clan.ClanPersistenceHarness | 39 | 0 |
| dev.vapee.core.clan.ClanServiceHarness | 136 | 0 |
| dev.vapee.core.clan.gui.ClanCommandHarness | 99 | 0 |
| dev.vapee.core.clan.gui.ClanMenuHarness | 84 | 0 |
| dev.vapee.core.clan.gui.ClanMenuSecurityHarness | 114 | 0 |
| dev.vapee.core.permission.PermissionDescriptorHarness | 843 | 0 |
| dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness | 17 | 0 |
| dev.vapee.core.player.settings.PlayerSettingsServiceHarness | 28 | 0 |
| dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness | 11 | 0 |
| dev.vapee.core.presentation.config.PresentationConfigHarness | 37 | 0 |
| dev.vapee.core.presentation.nametag.NametagLifecycleHarness | 27 | 0 |
| dev.vapee.core.presentation.nametag.NametagOwnershipHarness | 71 | 0 |
| dev.vapee.core.presentation.nametag.NametagRenderingHarness | 38 | 0 |
| dev.vapee.core.presentation.PlaytimeFormatterHarness | 14 | 0 |
| dev.vapee.core.presentation.PresentationHarness | 10 | 0 |
| dev.vapee.core.presentation.PresentationLifecycleHarness | 38 | 0 |
| dev.vapee.core.presentation.scoreboard.ScoreboardOwnershipHarness | 17 | 0 |
| dev.vapee.core.presentation.tablist.TablistOwnershipHarness | 58 | 0 |
| dev.vapee.core.rank.RankCommandHarness | 16 | 0 |
| dev.vapee.core.rank.RanksCommandHarness | 12 | 0 |
| dev.vapee.core.rank.RankServiceHarness | 23 | 0 |
| dev.vapee.core.rank.staff.StaffHierarchyServiceHarness | 109 | 0 |
| dev.vapee.core.reload.ReloadServiceHarness | 213 | 0 |
| dev.vapee.core.repository.RepoLinkHarness | 23 | 0 |
| dev.vapee.core.settings.command.SettingsCommandHarness | 36 | 0 |
| dev.vapee.core.settings.SettingsHotbarSecurityHarness | 12 | 0 |
| dev.vapee.core.settings.SettingsMenuHarness | 66 | 0 |
| dev.vapee.core.settings.SettingsMenuSecurityHarness | 206 | 0 |
| dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness | 23 | 0 |
| dev.vapee.core.settings.visibility.SettingsNavigationHarness | 17 | 0 |
| dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness | 327 | 0 |
| dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness | 29 | 0 |
| dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness | 74 | 0 |
| dev.vapee.core.visibility.FriendVisibilityRefreshHarness | 11 | 0 |
| dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness | 7 | 0 |
| dev.vapee.core.visibility.VisibilityModuleHarness | 22 | 0 |
| dev.vapee.core.visibility.VisibilityPolicyHarness | 28 | 0 |
| dev.vapee.core.visibility.VisibilityServiceHarness | 10 | 0 |

## Build, artifact and real Paper smoke

Normal offline Maven **clean package: BUILD SUCCESS, exit 0**, after final
canonical regression. Then unchanged `scripts/build-and-deploy.ps1 -Offline
-MavenRepository <existing local cache>`: clean package, mandatory full runner
and deployment all succeed, process exit 0. Existing managed start/stop scripts
and runner/gate are unchanged; no bypass and no automatic restart.

Artifact `target/vapeecore-1.0-SNAPSHOT.jar`: **1,090,909 bytes**.
Built and deployed SHA-256 are identical:
`4EC33E6A0D6A3F8976C98798DBB813A7AFDE17D8D21512F65E01481C53EB5CB4`.

Final real smoke: **06.10.2026 18:32:47 through 06.10.2026 18:33:05**, Europe/Berlin.
Actual runtime: Paper **1.21.11-132-ver/1.21.11@c5eb079**,
Java **21.0.12.1+1-LTS-4**, LuckPerms **5.5.84**.
All **27** modules enable. `core` reports Running/27 modules/connected LP;
`core version`, `core help`, `core reload`, `bukkit:help VapeeCore`, `version`,
`lp info` and `list` all pass. The six exact existing configurations are prepared
and coordinated reload completes successfully (the coordinator logs one final
success, not six individual apply messages). Help has all **37** exact descriptor
roots. The normal managed stop completes; all **27** modules disable in exact
reverse order, worlds save and managed wrapper/Java exit is **0**.
Final smoke: **0 ERROR/SEVERE**, no plugin warning. Paper's existing newer-release
advisory remains a WARN. The server is stopped after validation.

Before/after raw hash parity and file membership: **10/10 active live YAMLs
unchanged**. No live defaults, player/domain files or operator templates edited.
Source/runtime inventories remain **37 commands / 50 permissions / 15 positive
child edges / 27 modules / 6 reload participants**.

Local smoke-helper correction: the first attempt aborted in the local readiness/log
helper and requested stop during bootstrap; Paper rejected that early
console stop with one ERROR. That smoke-owned process was subsequently shut down
through its normal Bukkit shutdown API, without killing it. A temporary local
JDK attach helper used only for this recovery was removed. A second attempt
passed the actual server checks and clean stop but the local helper incorrectly
treated an unset LASTEXITCODE as failure. Shared-file log reads and that helper
check were corrected. The final complete repeated smoke above passed against
the same unchanged JAR; aborted logs are retained separately as ignored evidence.
Neither incident required production, managed-script or live configuration edits.

**Live client test: not performed. Visual multiplayer acceptance: pending.**

| Active YAML | Before = after SHA-256 |
|---|---|
| blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| config.yml | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` |
| daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| lobby.yml | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` |
| presentation.yml | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` |
| private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |
| players/0d9efbe6-33bf-4cf7-ae67-61d6238b2561.yml | `B133BFDD5875ED00C3B6B18EDDBEE4B1B126336202732037F4E994F0367FA5E1` |
| players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `6DFE9926FADA32EA3B60DADD401A71E78072C5DEC8E0EFEF78021A7D980DF511` |

### Two-client visual checklist — pending

1. Connect two clients in the lobby with different cached ranks; give one a clan.
   Verify prefix + actual name + suffix above each other and configured tablist
   display. Verify an unclanned player has no empty brackets.
2. Change prefix/rank and canonical clan tag; join/leave/disband a clan. Observe
   both surfaces by the next configured refresh and confirm literal-looking tag
   text never injects colors/events.
3. Hide B for A while B still sees A, then show again. Check directional visible
   player/nametag behavior, join while hidden, quit/reconnect and world exit/return.
4. Toggle sidebar independently and reload nametag on/off; verify the four feature
   combinations. Reload interval/templates, then clean plugin/server shutdown.
5. With a controlled foreign-board/team fixture, replace A's board, collide a
   technical team name, move an entry and recreate a same-name team. Verify no
   take-back/foreign overwrite and normal recovery after returning to main.
6. Check long/formatted prefixes, name legibility and default Vanilla name color
   on both clients. Resource packs are not required. Restore any temporary
   operator test configuration afterward; this checklist has not been executed.

## Protected areas and Full Audit regression

All **482 protected baseline files** match their raw before/after SHA-256.
Across all 500 baseline tracked files only the ten intended modified paths
changed; the other **490** are byte-identical. This protects moderation,
persistence and player schema/repositories, friend/economy/quest/activity,
WorldDisplay/Seat, all Rank/Clan/Visibility production, reload coordinator,
ReloadServiceHarness, all four scripts, pom.xml, plugin.yml, bootstrap and all
20 historical reports. The Developer Guide's Phase-34 testing workflow is
unchanged; edits only describe current Presentation semantics/configuration.

Group digest input: sorted `path:raw-file-SHA256` records joined by LF,
UTF-8 without trailing LF. Each digest below is identical before and after.

| Group | Files | Unchanged digest |
|---|---:|---|
| reports/ | 20 | `4679042F9F7D137F175F3A0D6BD3F953DD41FF88951B171A1C6CC8725E4E6462` |
| scripts/ | 4 | `357F0ABE2CAA4671006FCB360F7E47F6866C13EA30FC0F6EB345648E86F0B08B` |
| src/main/java/dev/vapee/core/rank/ | 8 | `D3955EF7EB4E809D16D1FB8C4F40C8348A0E94D889CDB46E72EAA7F7C123DB02` |
| src/main/java/dev/vapee/core/clan/ | 20 | `673E8D3CE8994AF6C92E76346D77916F035D88DF0B855E6558BDB0DEA5535D5E` |
| src/main/java/dev/vapee/core/visibility/ | 3 | `05CCCED50235E5E4E8530D4B0F1376F7EB84DE71804F2182F8B01E3AF74AD088` |
| src/main/java/dev/vapee/core/reload/ | 4 | `91DA8E5CD5514B324B393D835E816870C8C42893E9444EC786F76CBFCA5BD193` |
| src/main/java/dev/vapee/core/moderation/ | 29 | `C18AACB8DB692466C6C71B69C3D345F2B1D31A1DD530451D7A7D7EFC2AB081E5` |
| src/main/java/dev/vapee/core/persistence/ | 1 | `E18E7E4BA2D376ACDE75FD70EEC156EFCAE5810B004C8DC75E97715CD509DB1D` |
| src/main/java/dev/vapee/core/player/ | 12 | `12C336F7FE16838BDF0F55F5DC0567274FD0C6351E18C658FCB15123C0633179` |

All 20 counted Full Codebase Audit findings remain resolved. This is regression
evidence against the closed Phase-34 baseline, not a new audit-finding phase.
Remaining Audit Findings: **0**.

| Findings | Current regression evidence |
|---|---|
| MOD-001 | ModerationCommandHarness and unchanged authorization source |
| SEC-001 | Friend/Settings/Visibility menu security harnesses |
| DISPLAY-001, LIFE-002 | WorldDisplay, Seat, Blackjack presentation/table cleanup suites |
| PERSIST-001, PERSIST-002 | LobbyPersistence, PlayerSchemaPersistence and unchanged persistence |
| GUI-001, GUI-002 | UiItems, Pagination and complete GUI lifecycle/security suites |
| LIFE-001 | UtilityModuleLifecycleHarness |
| CONFIG-001 | ConfigHelpers and all feature configuration harnesses |
| CMD-001, DUP-001 | Utility/Teleport/Build/NameSuggestions and identity command suites |
| API-001 | BlackjackReadView and domain/presentation harnesses |
| PRES-001, PRES-002 | Existing 30F harnesses plus native Nametag matrices and controls |
| PERF-001 | WarpReplacement and BlackjackReplacement harnesses |
| TEST-001 | Unchanged real ReloadServiceHarness, 213 checks |
| TEST-002 | Unchanged canonical runner and mandatory deployment gate, real final success |
| DOC-001 | Minimal current guide/formatting updates, RepoLink and runtime inventories |
| LEGACY-001 | Active BlackjackHarness and protected source parity |

## File inventory and future seam

Added (5):

* `src/main/java/dev/vapee/core/presentation/nametag/NametagService.java`
* `src/test/java/dev/vapee/core/presentation/nametag/NametagOwnershipHarness.java`
* `src/test/java/dev/vapee/core/presentation/nametag/NametagRenderingHarness.java`
* `src/test/java/dev/vapee/core/presentation/nametag/NametagLifecycleHarness.java`
* `reports/35-nametag-rank-presentation/NAMETAG_RANK_PRESENTATION.md`

Modified (10):

* Presentation production: `PresentationRenderer.java`, `PresentationService.java`,
  `config/PresentationConfig.java`, `scoreboard/ScoreboardService.java`.
* Resource: `src/main/resources/presentation.yml`.
* Tests/support: `PresentationFixture.java`, `PresentationHarness.java`,
  `config/PresentationConfigHarness.java` under the presentation test package.
* Current docs: `docs/DEVELOPER_GUIDE.md`, `docs/FORMATTING.md`.

Removed: **0**. Full name/status/stat/diff and whitespace checks reviewed before
commit. Every change belongs to native Nametag production, Presentation config,
tests, minimal current docs or this report. Ignored execution logs, local helper
scripts and hash/result receipts are evidence only, not shipped implementation.

A future literal rank glyph can enter the existing trusted formatted prefix
Component path without changing Team identity/ownership or becoming authority.
No font assets, ZIP, hosting, download, pack sending or required glyph system was
implemented. Badges are explicitly deferred; the Component input seam remains
future-compatible.

## Git delivery

Exactly one local commit with subject **`Phase 35 - Nametag and Rank Presentation`**
is created after implementation, focused tests, six negative controls, final
canonical regression, clean package, official build/deploy, real Paper smoke,
final report and diff audit. Its full SHA, baseline distance **1** and clean
working tree are verified afterward and returned in the final response and
ignored commit receipt. A commit cannot embed its own final SHA here.
No push, merge, rebase, force action, extra branch/worktree or Phase-36 work.
