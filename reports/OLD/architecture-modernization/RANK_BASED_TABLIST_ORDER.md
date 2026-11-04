# Rank-Based Tablist Ordering

Date: 2026-10-25, Europe/Berlin. Focused presentation improvement, independent of Player lifecycle
and persistence. No Phase 5C, commands, permissions, gameplay, profile schema, rank authority,
nametag team implementation, sidebar implementation or configuration resource was changed.

## Starting state and baseline

- Clean integrated local `master`, equal to origin/master:
  `4f6d40ab551079cb782727decbf91a13727eee4a` (Merge Player Lifecycle and Persistence Safety).
- New requested feature branch: `phase/tablist-rank-order`. No merge, reset, rebase, push or changes
  to another feature branch. No applicable AGENTS.md found in the repository or ancestor directories.
- Verified current Windows Oracle JDK **21.0.12.1** at `C:\Program Files\Java\jdk-21.0.12.1`, Maven
  **3.9.16** from IntelliJ IDEA 2026.2.3 and PowerShell **7.6.5** from the current runtime. The unchanged canonical
  prerequisite checks succeeded; JAVA_HOME is invocation-local, not a permanent environment edit.
- Complete unchanged `pwsh -File ./scripts/verify.ps1`: **198 harnesses / 31,537 checks**,
  Failed **0**, Missing/Invalid **0**; Maven clean package PASS, Spotless PASS, exit **0**.
  No production or test edits began until this baseline completed.

Evidence is retained outside the checkout at
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\tablist-rank-order-evidence`:
baseline/final logs and receipts, inventory comparison, compiled mutation receipts, official client
metadata/mappings/bytecode investigation, installed CraftPlayer bytecode, native source/compile log,
native console/receipt and complete operator inventories.

## Existing owner and API investigation

PresentationModule acquires the active RankService during enable. PresentationRenderer already
reads primary RankInfo for colors/names; RankService delegates to LuckPermsService's **loaded
User.getPrimaryGroup()**, then actual group data and optional Group.getWeight(). Public ranks come
from ConfigService's configured `ranks.track`, preserving LP track order. No highest-inherited-rank,
prefix priority, permission node or hardcoded group-name mapping was introduced.

TablistService previously owned only name/header/footer snapshots; no Minecraft list order was
applied. PresentationService has a common periodic updateAll and existing single-player refreshes.
PresentationListener already schedules its one-shot join refresh and cleans quit state. The same
PresentationModule reload plan updates configuration, reconciles presentation and manages the
existing shared timer. These are the integration boundaries used here.

Primary references:

- Paper 1.21.11 Player Javadocs:
  https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html#setPlayerListOrder(int)
- Paper 1.21.11 CraftPlayer implementation:
  https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java
- LuckPerms Track API:
  https://github.com/LuckPerms/LuckPerms/blob/master/api/src/main/java/net/luckperms/api/track/Track.java
- Mojang's ordering-index announcement:
  https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-2

The actual installed Paper **1.21.11-132** CraftPlayer bytecode confirms that getPlayerListOrder
reads ServerPlayer.listOrder; the setter accepts **nonnegative** values, changes that field and
sends UPDATE_LIST_ORDER to players allowed to see the target. Despite Javadocs saying positive,
**zero is accepted and is the default**. The implementation uses only this public API, including
restoration; there is no production NMS/reflection/packet writer or sentinel substitution.

For the exact client precedence, official Mojang 1.21.11 metadata, client and mappings were fetched
and SHA-1 verified against that metadata. Client SHA-1:
`ba2df812c2d12e0219c489c4cd9a5e1f0760f5bd`; mappings SHA-1:
`031a68bebf55d824f66d6573d8c752f0e1bf232a`. Mapped PlayerTabOverlay (`gkg`) bytecode shows
negative list-order as the **first** ascending comparator key, followed by spectator status,
team name and case-insensitive profile name. Thus higher explicit orders display first and distinct
orders take precedence over the existing UUID-based nametag teams. The client was inspected,
not launched or used for visual confirmation. Modified/translated/older clients remain outside
the demonstrated vanilla-1.21.11 behavior.

## Actual sorting contract

Each refresh snapshots the current online roster, actual Minecraft username, UUID and primary
RankInfo. Optional track/group metadata is acquired only when some player lacks a weight. All
rank/track maps are temporary locals for that refresh; no second persistent rank cache exists.

1. Explicit numeric LP weights compare descending, using safe comparisons rather than subtraction.
   Equal, negative and extreme Integer weights remain valid rank inputs.
2. An unweighted primary group can use the configured LP track. LP defines index zero as the
   lowest starting group, so later positions have higher fallback priority. Arbitrary group IDs work.
3. For a track whose weighted anchors are nondecreasing and whose weighted online groups are all
   on that track, unweighted positions fit above their lower weighted anchor and below the next
   greater weight. Entries before the first anchor remain below weighted anchors; entries after
   the last anchor are higher. Fully weightless tracks use their own ordinal progression.
4. Equal weighted bounds have no strict priority gap: an unweighted entry between them shares
   their priority and therefore participates in the alphabetical tie. Explicit equal-weight ranks
   are never split by their track positions.
5. If a weighted online primary group is outside the track, its relation to an unweighted track
   position cannot be derived numerically. Explicit weighted ranks remain first; unweighted track
   ranks follow in track order. No index is converted into a guessed LP weight. Duplicate track IDs
   or descending weighted anchors disable that ambiguous fallback. Unknown primary rank / absent
   track data has neutral priority below placed ranks.
6. Within equal resolved priority, **Player.getName() case-insensitively**, then **UUID**, decides.
   Display name, rank display label, styling, prefix/suffix and clan tag do not decide order.

The small private Priority value consists of a numeric anchor and an ordinal step; this avoids
overflow, floating-point interpolation, pairwise inconsistent comparisons and rank-name guesses.
Complete consistent LP weights are the preferred configuration. The fallback cannot establish
an unavailable global hierarchy between unrelated tracks and weighted groups.

After sorting, actual `setPlayerListOrder` calls assign **N, N−1, …, 1**. These values are positive,
unique, deterministic and independent of LP weight magnitude. It is not just a Java-list reorder.
Only online players participate. Hidden players may consume an index without changing relative
ordering among the visible ones; existing visibility ownership is untouched.

## Refresh lifecycle and field ownership

Production changes are confined to three presentation files:

- PresentationModule passes its already acquired RankService into TablistService.
- PresentationService reconciles complete-roster ordering after updateAll and after a single-player
  refresh. The existing join callback reaches the latter. Quit restores the departing player's fields;
  the existing next periodic refresh reconciles the reduced roster. Rank/weight/track changes are
  read again by that same timer (normally 20 ticks, respecting the configured interval).
- TablistService plans priorities before any order write. Rank-provider errors propagate to the
  existing logged presentation boundary, preserving current order for a later refresh. Its original
  constructor remains compatible; production uses the RankService overload. Name/header/footer
  rendering and their existing ownership rules are unchanged.

OrderField lives in the existing UUID-only PlayerFields map. It captures the pre-write order only
on the first necessary successful setter write, preserves that original value across later refreshes,
and records the actual Paper-readable last written order. If the desired value already matches,
it performs no setter write and claims no new authority. Failed setters gain no cleanup ownership.

Cleanup restores the original nonnegative order only if the current value still equals the last
own write; otherwise it preserves an external takeover. It avoids redundant restoration writes
and forgets the activation snapshot. A new activation/session captures its new baseline. Tablist
disable, complete presentation disable, reload/rollback, quit, shutdown and re-enable use the
existing service paths. The order snapshot holds no Player reference.

While active, a later configured refresh can overwrite an intervening external value and retains
the first baseline, matching existing tablist-field policy. This is not continuous arbitration.
A foreign write equal to the last own value is indistinguishable (the existing value-based
ownership limitation). No separate scheduler, global owner registry, rank cache or scoreboard-team
renaming is added. Presentation resources and config schema are unchanged.

## Executable tests and compiled negative controls

New canonical `TablistRankOrderHarness`: **123 checks**, exercising actual RankService,
LuckPermsService, TablistService, PresentationService, PresentationModule reload plans and existing
listener/task callbacks. Only external Paper/LP surfaces are controlled through PresentationFixture.

Cases cover mixed weights, primary-group changes, negative/extreme weights, equal weights across
different groups, actual usernames versus styled names, case-insensitive order, UUID ties independent
of roster input, missing/empty/custom/reordered/duplicated tracks, monotonic mixed-weight anchors,
equal bounds, contradictory weights, unrelated weighted groups, unknown users, actual positive
setter values, unchanged-write suppression, zero/positive restoration, external ownership, repeated
cleanup, active interleaving, failed rank reads/setters, quit/offline/rejoin, tablist/global toggles,
reload rollback, shutdown and a new module activation.

PresentationFixture adds only optional weight/track state and actual order get/set behavior. Its
old defaults stay unchanged. Existing TablistOwnership **58**, PresentationLifecycle **38** and
NametagLifecycle **27** checks remain intact. Existing name/header/footer/style/null-sentinel,
scoreboard ownership, nametag colors/teams and config/reload harnesses remain canonical.

Supplemental `scripts/tests/tablist-rank-order.Tests.ps1` uses isolated compiled overlays, never
replaces ordinary classes and rejects compilation failure as behavioral evidence. All **8** compile
successfully and fail the intended named assertion:

| Control | Compiled defect | Detection |
|---|---|---|
| N1 | Reverse rank priorities | Higher actual primary weight must be first |
| N2 | Case-sensitive username tie | Actual case-insensitive alphabetical order |
| N3 | Ignore UUID tie-breaker | Stable result independent of input order |
| N4 | Unconditional cleanup restore | External order takeover must survive |
| N5 | Rebase snapshot on refresh | Original order baseline must survive |
| N6 | Omit Paper order setter | Applied native-order values are required |
| N7 | Disconnect ordering from service refresh | Join refresh must order the whole roster |
| N8 | Reverse weightless track | Ending/highest track rank must come first |

Ordinary-classpath positives run before and after: **123 + 58 + 38 + 27 = 246 checks per round**.
All overlay directories are temporary, with validated resolved cleanup paths. Receipts were copied
outside target before final canonical clean. The new harness alone was formatted individually
using pinned GJF 1.22.0/AOSP, without changing the six-file Spotless 2.43.0 allowlist or POM.

## Native acceptance and final verification

**Native PASS: 69 checks**, Paper **1.21.11-132-c5eb079**, LuckPerms **5.5.84**, Oracle JDK 21,
final built VapeeCore JAR, loopback **127.0.0.1:25596**. Fresh copied runtime with new worlds,
plugin configuration, LuckPerms database/groups/track/users and synthetic profiles. The first native
attempt passed, process exit **0**, **0 ERROR/SEVERE**. All 28 production modules start in both
activations and reverse shutdown is exercised before platform-wide safety cleanup.

The probe creates actual ServerPlayer/CraftPlayer instances with embedded channels and controlled
native online-roster membership. LuckPerms groups, integer weights, primary memberships and the
configured track use real LP APIs. Blocking LP preparation runs asynchronously before the main-
thread acceptance step; production remains synchronous on its existing update path. Probe-only
NMS/reflection/online-roster setup is outside the repository and is not plugin production code.

Native observations include:

- Default order is zero; setPlayerListOrder(0) succeeds, negative input is rejected.
- Mixed primary weights and `alpha` before `Zulu` within the same rank receive explicit N..1 orders.
- Real native sidebar objectives, four nametag teams per viewer, rank-colored tablist names and
  headers/footers coexist. Ordering neither replaces team identities nor creates a competing board.
- **138 outbound UPDATE_LIST_ORDER packet objects** are captured at the native listener send
  boundary across the acceptance flow; a highest-priority entry is verified with its exact UUID/order.
  Repeating an unchanged presentation refresh adds **zero** order packets. This is not a TCP capture.
- A real LP primary-group mutation is reflected by a single-player presentation refresh; removing
  weights exercises the real configured-track fallback.
- Tablist-only reload restores zero and an earlier positive **77**, preserves foreign order **901**
  and keeps nametag teams. Rollback reapplies correct ordering.
- Departure restores the current session, next refresh compacts the remaining native roster, and
  reattachment receives a deterministic new order.
- Full reverse shutdown restores the activation baselines. All consumers stop before trusted root
  lifecycle callbacks rebuild the composition with an open classloader; another complete activation
  gets fresh ownership and another clean shutdown. No isolated Presentation hot swap under active
  consumers or support for reopening a platform-closed plugin classloader is claimed.

This is **native API/service/packet acceptance, not real network login or visual Minecraft-client
confirmation**. Actors are removed before Paper stops. The disposable server/world directory was
resolved, checked against its intended evidence path and removed after confirmed process exit;
probe source/classes, receipt and console logs remain outside the checkout.

Native-tested JAR SHA-256:
`b1d2ef8d1defd08e3f825b830b8dae14e35634b3c805209245c603fd733c78ac`.

Complete final `pwsh -File ./scripts/verify.ps1`, exit **0**: Maven **clean package PASS**,
Spotless **PASS**, **199 discovered/executed/passed harnesses / 31,662 checks**, Failed **0**,
Missing/Invalid **0**. All **198** prior canonical entry points remain; only TablistRankOrderHarness
is added. The exact **+125** check delta is **+123** new behavioral checks, RepoLink **79 → 80**
for the new maintained report link, and GuiAuthoringAudit **230 → 231** for the new test source.
Every other existing harness retains its count. The final canonical JAR hash equals the native-
tested hash above. PowerShell parser check reports **0** errors; final new-harness formatter and
Git whitespace checks pass without changing the established formatter scope.

Before testing, the complete current installed `dev-server/` inventory captured relative paths,
lengths and SHA-256 for **913 files**, including all worlds, libraries, installed plugins and data,
profiles, operator YAML and existing migration/recovery/config backups. The current VapeeCore
subset contains **22** files. Final complete-set comparison is **913 before / 913 after**, **0
added/removed/changed paths, lengths or hashes**; no protected location was unavailable. Native
uses copied runtime inputs and new mutable data. No deploy or operator-server start/stop script
was invoked. No live data, deployed JAR, generated build/server, client artifact or mutation overlay
belongs to the commit.

## Scope and remaining validation

Exactly eight intended files: the three production presentation files above; the existing
PresentationFixture extension; new TablistRankOrderHarness; new compiled-control script;
the presentation section of `docs/DEVELOPER_GUIDE.md`; and this report. Phase 5C source/tests,
Player persistence, RankService/LuckPermsService, commands, permissions, nametag/scoreboard
implementations, config resources and schema remain unchanged.

Remaining validation: a real Minecraft 1.21.11 client visually opening the tablist with representative
players and normal spectator/visibility/team conditions. API values, generated packets and client
bytecode establish the ordering mechanism; they are not a screenshot or end-to-end client test.

Deliver exactly one local commit, no push and no merge. The final commit SHA and clean working-tree
status are returned separately to avoid embedding a self-referential hash in this report.
