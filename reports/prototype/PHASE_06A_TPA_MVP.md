# Phase 06A — Player Teleport Requests MVP

Completed 2026-11-02 on `codex/player-tpa-mvp`, from fetched `master`/`origin/master` at `9e65c2b5de5d78b96871c9bb45889fd0d7c09f2d`. Staff Panel was already integrated. No merge, push or operator deployment was performed. Module composition remains **27 modules / 7 ReloadService participants**.

## Commands and consent

| Command | Behavior |
|---|---|
| `/tpa <player>` | A requests permission to teleport A to B. Only B can accept. |
| `/tpahere <player>` | A asks B to teleport to A. Only B can accept; B is the mover. |
| `/tpaccept [player]` | Accept an eligible incoming request, optionally selecting its sender. |
| `/tpdeny [player]` | Deny an eligible incoming request, optionally selecting its sender. |
| `/tpcancel` | Cancel the current session's own outgoing request. |

All five commands use the single new independent permission `vapeecore.tpa.use`, **default true**, with no children. Both participants must currently have it. Administrative teleport permissions, OP, staff rank and teleport bypass nodes grant no exemption from TPA consent or eligibility checks. Console/command-block senders cannot create or answer requests. Permission-aware completion offers eligible current names, or actual incoming senders when answering.

Requests bind immutable UUID identity, exact current Player objects, initial World objects and a random request UUID. Names are only exact case-insensitive online lookup/display input, never durable authority. Optional manual selection accepts the current sender's complete name or UUID; partial matches are not accepted. Without an argument, answering requires exactly one eligible incoming request. Ambiguous requests remain pending.

The recipient receives one compact, localized `[TPA]` line with the current primary-rank-colored literal sender name, explicit direction and two RUN_COMMAND actions. The actions invoke `/vapeecore:tpaccept <sender-uuid>:<request-uuid>` and the corresponding `tpdeny` command as the clicking player. The internal selector still requires the real recipient, live request, session and effective permissions. Replayed or forged selectors cannot accept or deny a later request from the same sender. No console dispatch or administrative teleport command is involved. Feedback is concise EN/DE; operator/server prefixes are not added to this dedicated channel.

## Bounds and lifecycle

- **60 seconds** eligibility from successful creation, measured with monotonic `System.nanoTime`; the exact deadline is expired.
- **15 seconds** between successful sends. Cancel, denial and teleport failure do not waive that session's cooldown. Rejected sends do not create a cooldown.
- At most **one outgoing** request per sender and **five incoming** per recipient. Duplicate/self requests and implicit replacement of a still-valid outgoing request are denied.
- Lazy pruning runs through allowed commands/completion and existing lifecycle hooks. Expiry is enforced at acceptance even without a timer. Expiry feedback is sent when an expired record is pruned, rather than scheduled at the deadline. Invalid entries are removed before selecting or counting requests.
- Quit, new session identity, world change, death and Utility shutdown invalidate affected requests. An old session's quit event cannot erase its replacement's request or cooldown.
- Existing Activity membership, Lobby state and Ignore observers invalidate requests during Activity entry, BUILD membership or Ignore changes. Leaving/unignoring afterward does not revive removed consent. Ordinary NORMAL-state refreshes preserve valid requests.
- Permission/context and visibility changes are checked freshly at the next operation and immediately before teleport. There is no LuckPerms polling/subscription or new permission cache. Bukkit may reject a revoked command sender at its descriptor gate before the executor runs; a stored but ineligible entry is pruned by the next permitted operation/hook. It cannot teleport while either participant lacks permission.
- Acceptance removes the exact request before teleport/event callbacks. Failure, cancellation, repeated acceptance, cancel/deny races and callback reentrancy do not reuse consent. A short in-call participant guard blocks reentrant requests involving the moving pair.

All state is session-only and bounded by online participants. There is no new scheduler, YAML configuration, persistent setting, table, request queue, rank cache or reload participant. Utility creates a fresh runtime on re-enable; old command instances remain inactive.

## Existing contracts and destination safety

`TeleportRequests` uses actual UtilityService teleport execution, SocialService's loaded Ignore snapshots in both directions, PlayerSettingsService's loaded-profile availability, ActivityService participation, LobbyPlayerStateService BUILD membership and Paper `canSee` in both directions. A failed private relationship/state check returns a generic unavailable response without explaining Ignore or exposing a hidden target. Existing PM, friend-request and friend-presence preferences retain their original scopes; none is reinterpreted as a TPA setting.

Both participants must be exact online sessions, alive, outside vehicles and carrying no passengers. Sender and recipient eligibility is checked at send and acceptance, with a final repeat after destination inspection. There is no activity/BUILD bypass even for administrators. No friend-only restriction is introduced.

The destination is cloned from the destination player's current location at acceptance. Existing player-target Utility teleports have no extra global world whitelist; TPA adds no world-policy framework or requirement for administrative coordinate/world commands. Only the identical currently loaded World object is accepted. World changes after creation invalidate the request; fresh requests may target another already loaded world under the same consent and safety checks.

Destination inspection requires finite coordinates/rotation, bounded horizontal coordinates, WorldBorder containment, world-specific vertical limits including standing body/support, and already loaded chunks for every block touched by a 0.6 × 1.8 player footprint/body. It checks free passable non-liquid body space, solid support and rejects hazardous floor/body materials and portals. It never calls chunk/world loading, terrain search or generation APIs. Negative coordinates and adjacent columns/chunks are included. This is deliberately conservative: water, flight and some partial-block positions may require the target to stand on a clear solid surface before sending a fresh request. It is not a promise that an arbitrary modded block or another trusted plugin's later event modification is safe.

UtilityService's existing synchronous teleport result is respected, including native event cancellation. Other server plugins retain their normal teleport-event control. The relevant API contracts are [Paper Entity teleport](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html) and [Paper Server command access](https://jd.papermc.io/paper/1.21.11/org/bukkit/Server.html); actual runtime behavior was also tested below.

`TeleportRequestRegistration` preflights all five plain and namespaced routes as the same owned PluginCommand before modifying any route. A collision/missing command aborts startup and rolls back Utility's resources. It acquires command pairs, a Paper listener and three existing observers before activating requests. Cleanup deactivates/clears state first, removes all observer/listener ownership and restores prior executor/completer values independently only when still owned. Foreign replacements are preserved. Partial acquisition and cleanup failures are tested through the actual Utility lifecycle.

## Canonical verification

Before changes: `pwsh -File .\scripts\verify.ps1` — **221/221 harnesses, 32,788 checks, Failed=0, Missing/Invalid=0; Maven clean package PASS; Spotless PASS**.

Final same command — **223/223 harnesses, 33,144 checks, Failed=0, Missing/Invalid=0; Maven clean package PASS; Spotless PASS**. All 221 original harness identities remain present and passing. The complete canonical run passed on its first post-implementation execution; earlier focused compilation/catalog checks caught and corrected test-fixture compilation and escaped literal command-placeholder issues.

New executable harnesses:

- `TeleportRequestsHarness`: **161 checks** using real Utility, Social, settings, Activity, Lobby state, RankService/PlayerNameFormatter and localization code with controlled Paper boundaries and monotonic time. Covers directions/recipient authority, no force/self/partial-name requests, duplication, exact expiry/cooldown limits, bounded inbox, ambiguous/manual selection, forged/replayed buttons, both Ignore directions, both visibility directions, live permissions/loaded profiles/BUILD/Activity, failure/cancellation/exception/reentrancy, both reconnect roles, stale quit, world identities, shutdown/re-enable, dangerous/missing destination, negative body overlap, different world heights, current target position and EN/DE rank-colored RUN_COMMAND delivery.
- `UtilityTpaLifecycleHarness`: **63 checks** executing real PluginCommand leases and Utility lifecycle. Covers root/namespaced/owner collision preflight, inactive partial startup, listener/observer rollback, cleanup faults, independent external executor/completer ownership, old consent fencing, double enable, restart and repeated disable.

The existing frozen descriptor and moderation registration expectations now enumerate exactly **43 command roots / 53 permission nodes**. The 15 existing child edges and all administrative defaults remain intact; only TPA adds a twelfth default-true node. Historical check growth is CatalogUsage +73, Localization +21, PermissionDescriptor +34 and GuiAuthoringAudit +4 newly scanned test sources. Together with 224 new focused checks this accounts for the complete +356 check delta. No existing harness was weakened, disabled, deleted or bypassed.

## Native Paper + LuckPerms acceptance

Isolated `native-02`: **Paper 1.21.11 build 132 (`c5eb079`), Java 21.0.12.1, LuckPerms 5.5.84**, loopback `127.0.0.1:25619`, fresh generated worlds, fresh player files and a separate LuckPerms database. Only immutable runtime binaries/libraries were copied from the local installation; plugin/lobby inputs came from this build and repository resources. No operator world, player or LuckPerms data was used as a mutable fixture.

**236 server assertions, 17 connected-client assertions, 107 acknowledged fixture steps; zero failures.** Two actual non-OP offline protocol accounts, TpaAlpha and TpaBeta, sent ordinary network commands. Their received Components and position packets proved sender/recipient authority, both teleport directions, no movement before consent, actual namespaced RUN_COMMAND contents, denial/cancellation/replay, cooldown, both Ignore directions, hidden players, both BUILD/Activity roles and permission revocation at send/accept. Native tests also proved PlayerTeleportEvent cancellation consumed exactly one request, unsafe support rejection, Utility disable/re-enable command cleanup, recipient quit/reconnect with a new Player object and the real 60-second lazy-expiry deadline (61-second elapsed wait).

The successful native log contains no ERROR/SEVERE entries. All module shutdown calls returned normally and an explicitly retained process handle recorded JVM **exit code 0**. Its tested JAR is byte-identical to the final canonical artifact:

`506FDC8D9EEB6E6212856F33EB3D0B55E85F44EF0C5068597E144057C2E01C9C`

The earlier `native-01` attempt passed its initial consent/direction/cancel checks, then Paper's vanilla anti-spam disconnected the overly fast fixture controller (`disconnect.spam`). It was shut down through the disposable fixture's guarded command. Increasing only the external test driver's command spacing to 1.3 seconds produced the complete successful run; production code was unchanged. That unsuccessful attempt's logs are retained and are not counted as a completed acceptance run.

Native fixture sources, client transcripts, results, artifact and build receipts are retained outside Git at:

`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase06a-tpa-20261102`

Important receipts: `baseline-results.json`, `verification-final-results.json`, `harness-ledger.json`, `native-02/native-tpa-results.json`, `native-02/protocol-client-results.json`, `native-02/native-tpa-evidence.txt`, `native-02/protocol-client-transcript.txt`, `native-02/process-handle-exit.json`, and the protection manifests. Native helper/client code is not included in the plugin or repository commit.

Both disposable server processes are stopped. Their isolated server trees and generated fixture data are retained with the evidence; no destructive cleanup was run.

## Remaining physical Minecraft-client checks and limitations

- Confirm EN/DE line readability, rank colors, direction wording and Accept/Deny hover/click experience in a normal rendered Minecraft client.
- Exercise both directions in ordinary gameplay and inspect visual teleport/camera behavior, denial/cooldown feedback and expired/replaced chat buttons.
- Check real deployment visibility/protection/teleport plugins and practical standing surfaces; conservative destination rejection is intentional and requires a fresh request after failure.

Connected protocol clients and native API assertions are not physical Minecraft-client visual acceptance. No physical-client test or operator deployment is claimed. No known failing implementation or canonical/native acceptance check remains; the visual/deployment checks above remain open.

## Operator and Run protection

Approved pre-existing local change `.run/Start VapeeCore Dev Server.run.xml` was neither edited, restored, staged nor committed. Its before/after Git diff and SHA-256 are identical:

`EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`

Only the 16 intended paths below were explicitly staged. No add-all, reset, clean, stash, rebase or history rewrite was used. The approved Run diff is expected to remain dirty after the single additional local commit `Add player teleport request system`. Prior commits and master are preserved.

Read-only protection snapshots covered **954 files** under `dev-server`, `.run` and `scripts`: **932 comparable hashes equal, 13 changed by the running operator environment, nine locked/unreadable at both captures, zero added/removed**. All 26 `.run`/`scripts` files were comparable and unchanged. This is an observation of two snapshots, not a claim that live operator files were byte-identical or could not change between them. No operation wrote or stopped the operator server.

The 13 observed changing ignored operator files were the VapeeCore player YAML `plugins/VapeeCore/players/0d9efbe6-33bf-4cf7-ae67-61d6238b2561.yml`; `world/level.dat`, `world/level.dat_old`, `world/data/raids.dat`; the matching `world/playerdata/*.dat`, `*.dat_old` and `world/stats/*.json`; `world_nether/level.dat`, `level.dat_old`, `DIM-1/data/raids.dat`; and `world_the_end/level.dat`, `level.dat_old`, `DIM1/data/raids_end.dat`. These legitimate runtime changes were left in place. Locked paths include `logs/latest.log`, the LuckPerms H2 database, all three `session.lock` files, and four open world entity/POI/region files. Exact paths/errors and the full comparisons are recorded in `protection-before.json`, `protection-after.json` and `protection-comparison.json`. No unrelated tracked source changes were found; `tracked-comparison.json` lists only nine intended existing-file edits.

## Exact committed paths

1. `docs/DEVELOPER_GUIDE.md`
2. `docs/PERMISSIONS.md`
3. `reports/prototype/PHASE_06A_TPA_MVP.md`
4. `src/main/java/dev/vapee/core/VapeeCore.java`
5. `src/main/java/dev/vapee/core/utility/UtilityModule.java`
6. `src/main/java/dev/vapee/core/utility/tpa/TeleportRequestRegistration.java`
7. `src/main/java/dev/vapee/core/utility/tpa/TeleportRequests.java`
8. `src/main/resources/lang/de.yml`
9. `src/main/resources/lang/en.yml`
10. `src/main/resources/plugin.yml`
11. `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
12. `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java`
13. `src/test/java/dev/vapee/core/utility/UtilityTpaLifecycleHarness.java`
14. `src/test/java/dev/vapee/core/utility/tpa/TeleportRequestsHarness.java`
15. `src/test/java/dev/vapee/core/utility/tpa/TpaRegistrationTestSupport.java`
16. `src/test/java/dev/vapee/core/utility/tpa/TpaTestSupport.java`
