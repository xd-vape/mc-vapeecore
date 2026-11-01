# Phase 04A — Targeted security remediation

Date: 2026-11-01. Scope: AUDIT-01 and AUDIT-02 (P2), and AUDIT-05 documentation (P3), from [Phase 04](PHASE_04_SECURITY_AUDIT.md).

## Starting point and scope

`master` and fetched `origin/master` both resolved to `d9222ce43fb053cb13f89df9a24e1cb9d02f41d1`. Work starts on `codex/security-hardening-phase-04a`, with the approved local Run change as the sole pre-existing tracked modification. Earlier history is retained. The complete pre-edit `scripts/verify.ps1` run passed Maven clean package, Spotless, **217/217 harnesses and 32,106 checks**, Failed=0, Missing/Invalid=0.

The implementation changes neither the 27-module composition nor the seven ReloadService participants. No permission nodes, persistent settings, configuration formats, rank caches or periodic jobs are added. AUDIT-03's public hidden-player disclosure behavior and AUDIT-04's existing GUI navigation remain outside this change.

## AUDIT-01: BUILD authority

**Root cause.** Lobby protection treated membership in the runtime BUILD UUID set as continuing authorization. `/build` checked the effective node on entry, but the protection handlers and state synchronization did not revalidate it after entry.

**After remediation.** `LobbyPlayerStateService.hasBuildAuthority(Player)` requires both owned BUILD membership and the player's current effective `vapeecore.utility.build` permission. The UUID set continues to represent lifecycle ownership; it is not a permission cache. Permission alone never grants a protection exemption.

`LobbyListener` rechecks this authority at block break/place, item drop/pickup, inventory click (including Creative), drag, item/entity interaction and hand-swap boundaries. Precise entity interaction and Armorstand manipulation have separate Bukkit handler lists and are registered explicitly; Creative uses the shared inventory-click handler list. Unauthorized owned BUILD actions are cancelled before their changes are applied. Movement also detects effective context loss within a world. These checks read Bukkit/LuckPerms effective permissions, including contextual grants, without OP or group-name shortcuts.

A deduplicated one-shot task on the existing Bukkit scheduler performs reconciliation outside inventory transactions. It is fenced by the listener lifetime, exact queued Player reference, quit cleanup and online state; it rechecks permission before cleanup. A permission regrant before reconciliation preserves a valid BUILD session. State synchronization and lobby reload reconciliation also release invalid BUILD ownership. Existing world-leave/join/quit paths continue to normalize owned temporary state.

Reconciliation uses the existing state-service before/after callbacks and normal inventory/lobby-item lifecycle. `exitBuildMode` now acts only on an owned BUILD session. It restores the configured mode when the current mode is the BUILD-assigned Creative mode, and preserves a different externally assigned mode. The added revocation path does not change independently assigned flight. Players outside BUILD are unaffected by revocation cleanup, including staff-command-assigned mode/flight. Existing normal lobby synchronization/reload policies otherwise remain in place. Disable/enable-failure deactivates the listener before unregistering it, preventing queued work from mutating a subsequent lifetime.

**Timing.** With no action, movement or synchronization, a revoked idle player may still display Creative temporarily. Each covered action is denied immediately; owned cleanup follows on the next tick. There is no permission polling job. Equal-value external reassignment of Creative during an owned BUILD session is not distinguishable from BUILD's own Creative value; VapeeCore's `/gamemode` and `/fly` already refuse active BUILD targets.

## AUDIT-02: administrative foreign Enderchest views

**Root cause.** The command opened the target's live native inventory after an initial capability/hierarchy check, but retained no access record or interaction-time guard.

**After remediation.** `EnderChestCommand` also owns a narrowly scoped listener and in-memory access records for foreign views successfully issued by that command. Records bind viewer UUID and exact Player session, owner UUID and exact Player session, returned InventoryView, and its exact top-inventory object. The current online-session identities, current effective `vapeecore.utility.enderchest.others` permission and existing loaded-only staff hierarchy are checked again for each matching click/drag. No cached permission decision, async rank load or second rank cache is used.

`UtilityService.openEnderChest` returns Paper's actual open result. A cancelled or replaced open cannot adopt a previously open other-plugin inventory. Initial ownership is verified against the owner's backing inventory using Bukkit inventory equality (Paper can create different wrappers); subsequent interactions use the captured view/top identities and actual current view.

On lost authority, the event is cancelled regardless of raw slot/action. This covers top and bottom inventory, shift transfer, hotbar number keys, offhand, double-click/collect, drop/outside and drag routes. The record is immediately marked revoked and remains guarded until the next-tick close. A regrant does not revive that already revoked view; reopening through the command establishes fresh authority. Delayed cleanup closes only its still-current exact view. It never closes a replacement inventory or a new viewer session.

Exact close events release ownership; stale close events cannot discard a newer record. Viewer quit forgets its old session. Owner quit invalidates matching views immediately and schedules their release. UtilityModule registers the command's listener before publication, rolls it back on partial enable failure, and disables/releases it before listener/command teardown. Re-enable creates a fresh guard. Personal Enderchests and unissued other-plugin views are not adopted.

**Timing and limits.** An idle revoked view can remain visible until its next interaction; already displayed contents cannot be undisclosed. Further guarded transfers are denied on first interaction. Cleanup from click/drag is deferred according to Paper's [InventoryClickEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryClickEvent.html) and [InventoryDragEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryDragEvent.html) contracts. This does not protect inventories independently issued by another plugin, or protect against a trusted plugin deliberately undoing event cancellation.

## AUDIT-05: permission guide

`docs/PERMISSIONS.md` now consistently states that `vapeecore.admin` authorizes both `/core reload` and `/core quest force [player]`. The actual executors and existing node remain unchanged.

## Executable regression verification

Final complete command: `pwsh -File .\scripts\verify.ps1` — **Maven clean package PASS; Spotless PASS; Discovered=219, Executed=219, Passed=219, Failed=0, Missing/Invalid=0; 32,474 checks**.

The original **217/217 identities** remain present and passing. New focused harnesses contribute 35 BUILD and 266 Enderchest checks. Existing UtilityModule lifecycle coverage increases from 452 to 517 checks. The source-discovered GUI authoring audit increases from 253 to 255 checks for the two added inventory listeners. These explain the +368-check increase over baseline; all other historical per-harness counts are unchanged.

All original harness identities and unrelated controls are retained. `Phase04SecurityAuditHarness` still has 19 checks: the affected BUILD and Enderchest assertions now require denial/release; its five AUDIT-03 disclosure controls are unchanged. `LobbyHarness` explicitly grants permission in its legitimate BUILD synchronization fixture. `UtilityCommandHarness` now models a real open-result/current-view identity and close, retaining its command/security assertions.

| Harness / verification | Behavior covered |
|---|---|
| `BuildPermissionRevocationHarness` | Grant/revoke, effective context changes, block break/place, Creative injection, click routes, drag, drop/pickup, item/entity use, swaps, deduplicated deferred cleanup, state callbacks, regrant, synchronization/reload, world leave/re-entry, quit/reconnect, independent staff mode/flight, disable fence |
| `EnderChestRevocationHarness` | Authorized controls and revoked top/bottom/outside routes, shift/number/offhand/collect/drop, drag, context loss, hierarchy change/unavailability/error, exact viewer/owner sessions, cancelled open, unissued/personal/foreign views, stale close, replacement before queued cleanup, owner/viewer quit, reconnect and disable/re-enable |
| `UtilityModuleLifecycleHarness` | All existing fault cases plus real EnderChestCommand listener installation, partial registration/executor/late-enable failure cleanup, shutdown and fresh re-enable |
| Existing canonical suite | Command restrictions/hierarchy, Friends/Clan, Navigator/region security, GUI identity/click ownership, all existing module/reload/persistence contracts |

The first full post-edit run preserved all 217 historical harnesses, but the new BUILD harness failed because it assumed `target/harness-temp` already existed after `clean`. The fixture now creates its own parent directory; no production behavior or assertion was weakened. The final complete verifier is the acceptance receipt.

## Disposable native Paper + LuckPerms acceptance

Final run: **Paper 1.21.11 build 132, Java 21.0.12.1, LuckPerms 5.5.84 — PASS**. The final production JAR is byte-identical to the canonical verifier artifact: SHA-256 `16C385407A08E8CF0519B9E29330750C81D36B8700A31E40929C352F6E53BA38`, also recorded in `native-08/tested-jar-sha256.txt` and `native-08/summary.json`.

Results: **198 server assertions**, **46 successful client fixture-command acknowledgements**, ten separately checked denied network cases, zero ERROR/SEVERE log entries, clean VapeeCore module shutdown and actual JVM exit code **0**. The two non-OP protocol clients performed one successful native diamond pickup and ten denied transfer attempts. Recorded network events were ten clicks (one allowed, nine cancelled) and one cancelled drag; synthetic API events are counted separately in the trace.

| Boundary | Native acceptance result |
|---|---|
| BUILD | Actual registered command, effective LP grant/revoke, permitted/denied block events, Creative and drag denial, precise entity and Armorstand allowed/denied event registration, next-tick normal-state cleanup, independent `/gamemode` and `/fly`, and real LP contextual-node loss: PASS |
| Connected client container transfers | Left pickup, shift, number key, offhand, double-click/collect, bottom shift, bottom placement and drag after node revocation: PASS |
| Changed authority | Actual target primary-group promotion to protected Owner, and actual LP contextual foreign-chest permission expiration, followed by real container packets: PASS |
| Owned cleanup | Replaced unrelated inventory survives queued release; actual owner disconnect closes view; actual viewer disconnect/reconnect uses same UUID/new session and inherits no view; actual UtilityModule disable/re-enable releases/reissues access: PASS |
| Existing scope | Native personal Enderchest and unrelated inventory remain outside foreign-view ownership; authorized click routes still work: PASS |

Target diamonds remain unchanged in every denied network case. Original viewer items, including pre-existing cursor items, are conserved across vanilla close/return/stack merging; denied drag/bottom placement creates no items in the candidate destination slots. Native acceptance uses controlled disposable items only. Exact traces and receipts are in `native-08/` under the evidence root.

Two non-OP offline-mode protocol clients connect exclusively to `127.0.0.1:25608`, using Mineflayer 4.39.0 with Minecraft 1.21.11 protocol support. A disposable test plugin calls the actual registered PluginCommand objects for native API setup and records real dispatched inventory events. Actual client packets exercise vanilla container transfer handling with seven diamonds in the target's native Enderchest and controlled emerald/cursor items. Server-side assertions inspect the actual native target inventory, viewer inventory, cursor and open view.

Native API events and connected-client packets are separately labelled `API` and `NETWORK` in the evidence. This is stronger than checking only event cancellation, and does not constitute visual confirmation by a physical Minecraft client.

Initial native test setup was corrected for nested Brigadier command deferral and the existing normal lobby managed-item guard. Another initial attempt was stopped after vanilla command-spam protection disconnected the fixture client; the final driver spaces commands. A subsequent attempt correctly blocked collection, but the test had incorrectly expected an already owned cursor item to remain on the cursor after close: vanilla returns it to the viewer's inventory. The final check verifies total viewer/cursor item conservation alongside unchanged target items. These are retained failed test attempts, not claimed passing acceptance. No production change was made to disable those protections or alter vanilla cursor handling.

## Protected files and repository scope

Read-only snapshots before and after verification hashed all **952 files** under `dev-server`, `.run` and `scripts`: all were readable at both observations, with no added/removed/hash-changed files and no locked/unreadable entries. This is a comparison of observed snapshots, not an assertion that a running operator server could not write between them. No operator world, player, log, configuration or server process was used as a mutable fixture. Only immutable Paper/LuckPerms binaries and libraries were read/copied into new test roots.

The Run diff compares equal and its byte hash is unchanged. A tracked-file hash manifest detects only the eleven intended existing-file edits, apart from the three new intended files listed below; the approved Run file itself has no change from preflight. No unexpected local modifications were found.

Approved Run SHA-256 before and after: `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Its pre-existing diff remains outside the commit. No reset, clean, stash, amend, merge, push or deployment is performed.

Exact committed paths (explicitly staged; approved Run change excluded):

```text
docs/PERMISSIONS.md
reports/security/PHASE_04A_SECURITY_REMEDIATION.md
src/main/java/dev/vapee/core/lobby/LobbyListener.java
src/main/java/dev/vapee/core/lobby/LobbyModule.java
src/main/java/dev/vapee/core/lobby/player/LobbyPlayerStateService.java
src/main/java/dev/vapee/core/utility/UtilityModule.java
src/main/java/dev/vapee/core/utility/UtilityService.java
src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java
src/test/java/dev/vapee/core/lobby/BuildPermissionRevocationHarness.java
src/test/java/dev/vapee/core/lobby/player/LobbyHarness.java
src/test/java/dev/vapee/core/utility/UtilityModuleLifecycleHarness.java
src/test/java/dev/vapee/core/utility/command/EnderChestRevocationHarness.java
src/test/java/dev/vapee/core/utility/command/Phase04SecurityAuditHarness.java
src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java
```

Evidence root: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/phase04a-security-remediation-20261101/`. It contains preflight and Run snapshots, baseline/final verifier receipts, tracked/protected-file manifests, disposable native helper/client sources, dependency lockfile, tested JAR hash, event evidence and client transcript. Native helper/client dependencies are acceptance tooling outside the repository and are not shipped in VapeeCore. All eight disposable server trees were removed only after checking their absolute paths, stopped test PIDs and absence of reparse points; sources, logs, receipts and the final tested JAR remain available.

## Remaining follow-ups

- Physical Minecraft client visual/UI confirmation remains open: Creative-to-normal resync, revoked-window closing animation/cursor display, and the allowed personal/foreign chest experience.
- AUDIT-03's vanished-player disclosure policy is unchanged and still requires a product decision. The five historical audit controls continue to characterize it.
- AUDIT-04's pre-existing synchronous GUI navigation remains a separate P3 follow-up. Only new security cleanup uses next-tick inventory release here.
- No claim is made that this bounded remediation is a new repository-wide audit or protects against malicious trusted server plugins.

Delivery: exactly one additional local commit, `Fix BUILD and Enderchest permission revocation`; full SHA is reported with the delivery. Push: **no**. Merge: **no**. Deployment: **no**.
