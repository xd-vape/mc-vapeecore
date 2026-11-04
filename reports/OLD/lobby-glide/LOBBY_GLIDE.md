# Lobby double jump and armor-free glide

## Baseline and ownership audit

- Inspected clean `master`: `9d1f0b10a48ec0d35a824e96ddb584fd05971b26`, containing `9ce9f5a` command highlights and `a59d177` consolidation. No earlier uncommitted work remained.
- Requested branch: `phase/lobby-glide`, same checkout, no worktree/rebase/merge/push.
- Fresh canonical baseline measured before production edits: **173 discovered/executed/passed, 27,064 checks, 0 failures, 0 missing/invalid**. Local receipts: `dev-server/.vapeecore-dev/lobby-glide/baseline-results.json`, `baseline-inventory.txt`, `baseline.log`.
- `LobbyPlayerStateService` owns NORMAL/BUILD and existing inventory transitions. BUILD changes to Creative; existing inventory clearing remains unrelated to Glide. LobbyExperience is the final enabled module and first disabled module.
- `NavigatorAccessPolicy` already checks online, loaded profile, configured lobby world, NORMAL and no Activity. Reused that authority. `UtilityService.hasManagedFlight(UUID)` already exists and Utility exclusively owns `/fly`'s managed set.
- Utility join normalization is deferred. Its GameMode/flight mutations need synchronous release before new flags and scoped reconciliation afterward.
- ActivityService publishes membership before session hooks. Ownership release must precede both, without depending on Blackjack's inventory callbacks. Leave/close/rollback notifications follow the domain hooks.
- Lobby transitions and reload changing the lobby world need pre-change release/post-change reconciliation. Existing world/quit/respawn/death seams remain authoritative. No prior flight-toggle/glide/move handler or repeating flight-repair task existed.

## Actual Paper API decision

Inspected the resolved Paper **1.21.11** API sources: `paper-api-1.21.11-R0.1-20260511.115010-91-sources.jar`, including PlayerToggleFlightEvent, EntityToggleGlideEvent and LivingEntity#setGliding. The latter documents armor-free operation and immediate server reversion unless the corresponding event is handled. This API has no dedicated on-ground event.

Consume only a feature-owned flight-toggle gesture. Cancel ordinary flight, use a single modest impulse, then request gliding once on the next tick after exact ownership/phase, airborne and eligibility checks. Suppress only that feature's active, eligible, airborne stop-gliding event. Never repeatedly set gliding true; landing, fluid, vehicle and authority exits terminate it. Real client physics remain an explicit acceptance gate.

## State, eligibility and launch

`LAUNCH_UP = 0.85`, `LAUNCH_FORWARD = 0.85`, both local to LobbyGlideService. Horizontal normalized look direction uses a yaw fallback for vertical aim. Velocity is not restored. After the real-client test, the operator requested a little more height. The upward impulse was raised from 0.65 to 0.85. The operator then explicitly confirmed the height and requested a little more forward push, authorizing continuation afterward (“Höhe ist in ordnung, vorwärtsschub kann bisschen stärker sein und dann sollte es passen und man kann weiter machen”). Forward impulse alone is now raised from 0.70 to 0.85. Final validation/deployment of that bounded change are pending below; no separate client observation of the new forward value is claimed.

UUID/token/phase/task metadata only: WAITING -> grounded ARMED -> one LAUNCHING -> GLIDING -> termination/WAITING -> grounded ARMED. No long-term Player, World, Location, Inventory or ItemStack. Pre-existing allowFlight/flying/gliding flags are refused; Creative, Spectator, managed `/fly`, death and the shared access policy exclude ownership. Fluid, swimming, climbing and vehicles cannot launch/maintain glide. Ordinary flying is never enabled. Airborne repeated input cannot add another launch.

Owned `allowGranted` and `glideOwned` flags record actual authority. Release removes the UUID authority before setters fire events and never blindly overwrites foreign/native/managed flight. Deferred requests coalesce by UUID/request identity. Launch compares exact state identity/phase; tokenized release cannot remove a newer generation. Canceled old work cannot reacquire or overwrite `/fly`.

The move fallback checks only already-owned UUIDs, ignores rotation-only events and does no broad player scan. It handles ground rearm after interrupted glide and a single deferred observation when Paper applies ground status after the final vertical Move event. There is no repeating task or continuous setGliding call.

## Handoffs and lifecycle

- `UtilityFlightListener`: release synchronously before Utility toggle/GameMode/normalization; reconcile afterward. Utility keeps `/fly` authority.
- `LobbyPlayerStateListener`: release before NORMAL/BUILD/inventory transitions; reconcile actual mode/world afterward, including reload and rollback.
- `ActivityMembershipListener`: release before membership/session hooks; notify after successful join, rollback, leave or session-wide close. No game-specific dependency and no global event framework.
- World/teleport/GameMode changes release then evaluate the new state. Death/quit clear both early and after other domain handlers so no pending rearm survives. Respawn evaluates fresh eligibility without restoring stale flags.
- LobbyExperienceModule installs and detaches all three observers and the listener, including failed-enable cleanup. Disable cancels scoped tasks and releases UUIDs best-effort; one failure cannot block remaining players. Failures include UUID diagnostics.

No new module, command, permission, setting, persistence, GUI, chat text, sound, particle or equipment system. Phase 38 has not started.

## Armor and inventory integrity

Glide never calls an inventory API or constructs an ItemStack. Exact sentinel armor/storage/offhand/cursor snapshots remain equal in harnesses, with any Glide inventory getter forbidden. The native Paper probe additionally compares actual serialized ItemStack bytes. Existing NORMAL/BUILD domain clears are tested separately and remain the Lobby state service's existing responsibility, not a Glide restoration system.

Production Elytra items created: **0**. No chest-slot replacement or escrow is implemented.

## Focused checks

**61 distinct harnesses, 7,427 checks, all passed**. First group: 37 Lobby/Activity/Utility/Glide harnesses, 6,371 checks. Supplemental group: 27 Player/Visibility harnesses, 1,178 checks; three overlap, deduplicated. Receipts: `focused.log`, `focused-player-visibility.log`.

- `LobbyGlideHarness`: **60 checks**. Eligibility gates, activation/cancellation/modest velocity, one deferred start, owned stop-glide scope, spam, landing/rearm, fluids, rotation fast path, foreign flags, stale release/launch/reconciliation, world/reload/native-mode/quit/rejoin/death/respawn/disable, per-player cleanup failure/UUID log, silence and no equipment access.
- `LobbyGlideHandoffHarness`: **43 checks**. Real Utility armed/active handoffs in both directions, real BUILD enter/exit, generic Activity before-hook authority, active session/leave, failed-join rollback/session close and observer detachment. Native APIs/one-shot clock are substituted; actual domain services and shared access policy own the transitions.

## Actual temporary production negative controls

Each defect was compiled individually into production bytecode, failed at the expected assertion, restored from the exact original source bytes, recompiled and passed the positive harness. `negative-results.json` records restored SHA-256 hashes; logs: `negative-A.log` through `negative-F.log`.

| Control | Temporary production defect | Expected failure observed |
|---|---|---|
| A | Remove shared world gate | eligibility refuses world |
| B | Remove NORMAL gate; fixture independently excludes native/foreign flags | eligibility refuses build |
| C | Old post-Utility callback clears allowFlight after Utility establishes it | Glide releases before /fly establishes new flags |
| D | Allow active/consumed input to launch again | airborne spam cannot stack launches |
| E | Add Elytra chestplate setter to launch | Glide armor/inventory integrity: inventory access forbidden; attempted write stopped at getter |
| F | Remove release generation comparison | stale cleanup cannot release a newer ownership generation |

All six detected; no negative mutation remains. The final native Paper bytes comparison is independent of the harness's inventory API guard.

## Full regression, build and Paper

Final **0.85/0.85** tune: canonical regression **175 discovered/executed/passed, 27,176 checks, 0 failures, 0 missing/invalid**. All **173/173** baseline entrypoints retained, with exactly two Glide harnesses added. Existing source-wide PermissionDescriptor and GuiAuthoring audits cover new files too (+6 and +3 checks respectively); no permission or GUI was added. Receipts: `final-results.json`, `final-inventory.txt`, `final-regression.log`.

After client feedback, upward changed from 0.65 to the accepted 0.85, then forward alone from 0.70 to the requested 0.85. Both Glide harnesses passed again (**103 checks**, `forward-focused.log`), and all six actual production negative controls were repeated/restored for the tuned source. Then the full canonical regression above passed again. Initial focused coverage remains 61 distinct harnesses / 7,427 checks; that accepted prototype's full result is preserved as `initial-final-results.json`.

Required offline Maven `clean package` after the tuned full regression: **BUILD SUCCESS** (23.442 s), `package.log`. Deployed JAR SHA-256: `CBD88A08CAF6A0E2F708632321977E02F243102C69D06C7D27AC5D662B4A51DB`. Target and deployed bytes match; no production change followed this validation.

Final tuned Paper **1.21.11-132** smoke: **18 native checks passed**, clean startup, actual Lobby/Utility Join-listener eligibility flow through the native scheduler, real event dispatch, serialized ItemStack armor/storage/offhand/cursor equality, Utility handoff, real BUILD enter/exit, `/core reload`, clean shutdown (exit 0). Measured **28 modules, 7 reload participants, 37 command roots, 50 permissions**. **0 VapeeCore/GlideProbe ERROR/SEVERE** in this final run. All **10 operator YAMLs and 2 existing language files** SHA-256 unchanged; no new language file. Receipts: `paper-smoke.log`, `paper-receipt.json`; initial accepted build's receipts are preserved with `initial-` prefixes.

The probe uses a virtual Player with actual Paper ItemStacks, event dispatcher, scheduler, profile repository and owning domain services. Only its isolated Glide instance resolves the virtual UUID; production's lookup is untouched. This does not test client packets or real entity physics. Temporary probe JAR and remapped copy were removed afterward. The Dev-Server was cleanly stopped through its supported stop script before deployment and remains stopped after smoke.

Validation repair history: the first initial full run caught a UTF-8 defect in new guide text. It was corrected, the focused RepoLinkHarness passed 21 checks and subsequent complete runs passed; the failed attempt remains in `first-results.json` / `first-regression.log`. The initial temporary Paper adapter also needed a plugin-classloader access correction; the adapter alone was fixed and native smoke repeated successfully (failure receipt: `paper-adapter-failure-server.log`). Neither repair required a production behavior patch.

## Real client acceptance and final commit

**Base mechanic and tuned height accepted in the real client; final bounded forward adjustment explicitly requested with authorization to continue.** After initially selecting “deviations found,” the operator clarified: “Funktioniert alles” and requested a little more height plus a future temporary Elytra. This confirms the listed initial-client checks (basic jump/glide/landing/rearm, spam, /fly, BUILD, world/rejoin, unchanged armor/inventory). Activity/Blackjack was not separately confirmed and remains harness-only; no client success is claimed for that case.

The operator confirmed the **0.85 height** after the tuned client test and requested a slightly stronger forward impulse, then explicitly authorized continuing. Only `LAUNCH_FORWARD` changes from 0.70 to 0.85; upward remains 0.85. The bounded final adjustment passed full regression, clean build and native Paper smoke, and is deployed. Its exact forward value has not separately been observed in a real client; the report does not invent that observation. The already performed client tests establish the base mechanic and accepted height. No additional confirmation is inferred for Activity/Blackjack.

The operator explicitly selected **“Ja, danach separate Elytra-Erweiterung”**: after completing this armor-free phase, implement temporary Elytra only with an empty chest slot; existing chest armor must remain untouched. That is a separate follow-up, not an armor fallback or equipment change in the current phase. No Elytra code or equipment mutation has been added here.

Git completion: one local `Add lobby double jump glide` commit after validation and the operator’s explicit instruction to continue with the bounded forward adjustment. The exact commit identity is reported from Git; no push, merge or Phase 38.

## Exact changed files

- `docs/DEVELOPER_GUIDE.md`
- `reports/lobby-glide/LOBBY_GLIDE.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/activity/ActivityMembershipListener.java`
- `src/main/java/dev/vapee/core/activity/ActivityService.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/glide/LobbyGlideListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/glide/LobbyGlideService.java`
- `src/main/java/dev/vapee/core/lobby/player/LobbyPlayerStateListener.java`
- `src/main/java/dev/vapee/core/lobby/player/LobbyPlayerStateService.java`
- `src/main/java/dev/vapee/core/utility/UtilityFlightListener.java`
- `src/main/java/dev/vapee/core/utility/UtilityService.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/LobbyGlideFixture.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/LobbyGlideHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/LobbyGlideHandoffHarness.java`

Local ignored helpers/receipts under `dev-server/.vapeecore-dev/lobby-glide/` are not production plugins or committed source. The temporary Paper probe is removed after smoke.
