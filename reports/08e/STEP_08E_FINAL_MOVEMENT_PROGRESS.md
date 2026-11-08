# Step 08E — Final Movement Quest Progress Producers

## Scope and starting point

Local branch `codex/step08e-final-movement-progress`, directly based on verified master/origin/master `6136eb2eef794508011f4eaa656b4121ddb44dfc`. The original worktree was clean. Before snapshots cover 1510 original files across both existing worktrees, including the ignored protected receipts. No operator server is used as a test fixture.

The existing QuestMovementProducer now supports `movement:backward` and `movement:sneak_toggle`. No new module, scheduler, quest engine, persistence schema, reward boundary, command, permission or localization/template is introduced. Quest IDs, names, descriptions, targets and rewards remain YAML-owned. Bundled daily defaults remain `enabled: false`, `quests-per-day: 4`, `quests: {}`; no prepared catalog is installed.

## Backward distance contract

The existing valid-movement path must accept the segment: current online instance, loaded profile, current assignment/catalog, active runtime, NORMAL/non-Activity context, Survival/Adventure, on ground, and no flight/glide/vehicle/water/swimming/climbing/riptide/death. Existing cancelled-event, teleport/world/session, nonfinite-coordinate, endpoint-replay, discontinuity, vertical-segment, one-horizontal-block segment/tick budget and twenty-tick effective-velocity exclusions apply unchanged.

`Input.isBackward()` is necessary; it is not proof of distance. Forward+Backward, Left+Right, and either the Input or Player sprint flag conservatively exclude BACKWARD. They do not change existing WALK, SPRINT or Sneak-precedence rules. Holding S stationary and pure strafing yield no backward progress.

For horizontal displacement `(dx,dz)` and event yaw `a`, the backward axis is `(sin(a),-cos(a))`, giving projection `dx*sin(a)-dz*cos(a)`. The producer takes the smaller From/To projection. It must be at least half the horizontal length: cosine threshold 0.5, meaning at most 60 degrees from BOTH endpoint backward axes. The shortest yaw change must be at most 45 degrees per segment. Larger turns are ambiguous and yield no BACKWARD credit, while existing WALK behavior remains unchanged. Finite event orientation is used, rather than a later Player location. Yaw wraparound is normalized.

For yaw zero, a `(0.6,-0.8)` diagonal travels one horizontal block but contributes only 0.8 BACKWARD blocks. Independent fractions accumulate and only whole blocks are reported. One valid backward block can signal WALK once and BACKWARD once; sneaking can additionally signal SNEAK. No second measurement or additional per-tick distance allowance is created. This is conservative server-observed progress, not causal physics reconstruction or anti-cheat proof; unobserved external motion or speed effects are subject to the existing limitations.

## Sneak activation contract

The handler observes uncancelled `PlayerToggleSneakEvent` at MONITOR. It requires an actual pre-event flag different from the event target and an accepted-observation state different from that target. Only false -> true reports one unit. True -> false only re-arms the observation. Duplicate start callbacks are rejected both before and after the native flag commit. Already-sneaking initial state does not earn an activation. Cancellation does not alter the accepted observation.

Paper 1.21.11 build 132's `ServerGamePacketListenerImpl.handlePlayerInput` calls the toggle event before `ServerPlayer.setShiftKeyDown`, then publishes the new `lastClientInput` after the event. Consequently `Player.isSneaking()` and `getCurrentInput()` still describe the old state during the callback; `event.isSneaking()` is the intended target. This was checked in runtime bytecode AND by invoking the actual native input-packet handler with a controlled CraftPlayer. The producer intentionally does not infer the new state from current Input.

There must be at least ten server ticks between credited activations. At 20 TPS this allows at most two credited starts per second in one continuing sample. An accepted end does not reset the interval. A start inside the interval consumes the observed transition without progress, and does not advance the last credited tick; a later real end/start can qualify at the exact boundary. Duplicate and cancelled events do not extend or bypass the interval. Ticks are injected deterministically in tests; there is no new task or persistent history.

Eligibility shares the existing runtime, loaded-profile, session, assignment, catalog, lobby/Activity and normal-player-mode gates, including the velocity exclusion. Unlike distance, a state transition needs no horizontal movement, directional input or ground contact; a normal airborne crouch can qualify. This does not permit flight, glide, mounts, water, climbing or Creative/Spectator. Sneak distance and activation are independent.

## Sample, lifecycle, services and HUD

The original five key-bit positions are preserved; BACKWARD and SNEAK_TOGGLE are appended. A separate four-key list indexes only WALK/SPRINT/SNEAK/BACKWARD fractions. Discrete Jump/Rotation/Toggle do not address distance slots. Unassigned keys do not accumulate. Unassigned toggle events cannot refresh unrelated measurements or keep expired WALK fractions alive. Toggle-only assignments skip Move geometry.

Temporary state remains in the same UUID sample map, with numeric entity/session identity and no Player/World references. Existing join/quit/rejoin, teleport/world, death/respawn, mode/flight/glide/mount/dismount and Lobby/Activity callbacks clear it. Assignment/restore/clear and catalog reload/rollback generation changes invalidate it before the next signal; ordinary progress/flush preserves valid measurements. Idle gaps over forty ticks rebase. A new session does not inherit a prior cooldown or accepted sneak state. Already-active sneak after any invalidation is not a new activation.

The existing QuestModule owns one movement listener, now fifteen registered event-handler methods, and its original context observers. There remain 27 modules and seven reload participants. Disable revokes authority and clears samples; partial-acquisition cleanup, independent owned release, foreign observers, retained cleanup failure and re-enable guards remain unchanged and executable regressions retain coverage.

Both new keys report only through QuestProgressReporter. QuestService owns numeric progress, pending retry, completion, rewards and persistence; the Step-08D.1 Distinct compatibility guard and strict evidence loader remain unchanged. No direct coins or server XP mutation occurs. The original HUD automatically receives both keys through the existing generic observer: localized name, progress/target/percent, temporary XP packet, priority arbitration and restoration. No second HUD is introduced.

## Executed verification

Fresh pre-edit baseline: **245/245 harnesses, 36,362 checks, Failed=0, Missing/Invalid=0, Maven clean package PASS, Spotless PASS**. Initial sandbox launch could not write Maven resolver metadata; the successful run used the existing JDK 21/Maven installation and authorized cache access. No repository configuration was changed for this.

Final canonical verifier: The final command `pwsh -NoProfile -File .\scripts\verify.ps1` passed **245/245 discovered/executed/passed harnesses, 36,536 checks, Failed=0, Missing/Invalid=0, Maven clean package PASS and Spotless PASS (existing pilot allowlist)**. All 245 baseline harness classes are retained with an identical inventory. Actual final focused counts: Movement 175 (+111), Integration 77 (+49), Module 25 (+1), HUD 79 (+12); RepoLinkHarness also increases from 124 to 125 because of the new guide-to-report link; all other harness counts are unchanged. The additional 174 checks bring the total from 36,362 to 36,536. Canonical class bytes match the final native-tested producer and Sample; no production/test source changed after the successful final build.

Focused executable coverage extends the existing Movement, Integration, Module and HUD harnesses, retaining every original harness. It covers pure/fractional/diagonal/backward geometry, direction thresholds and turns, opposed/missing inputs, sprint contradictions, Sneak/WALK subsets, cancellation, velocity boundary, teleport, replay, burst budget, invalid coordinates, excluded modes, unloaded/stale sessions, idle invalidation, assignment/catalog/reset/Force changes, stationary transitions, duplicate pre/post-commit callbacks, cancelled ends/starts, exact cooldown, retired runtime and re-enable. Real DailyQuestService tests cover same-cycle preservation, Force reconciliation, failed-save rollback and daily rotation. Configured 50/100-block and 20-activation shapes use real QuestService/RewardService; pending overflow, retry, once-only rewards and deferred persistence failures retain their existing semantics. German/English HUD rendering and XP restoration are exercised for both keys. The existing 529-check Distinct harness and all 42 pending-integrity scenarios remain green in focused checks.

Four additional compiled behavioral negative controls ran entirely outside the repository and all exited 1 at the intended assertion: optimistic rather than conservative endpoint projection, full diagonal credit, nine-tick cooldown, and removed pre-commit duplicate observation. These expected failures demonstrate assertion sensitivity and are not canonical failures. No production file was mutated for these controls.

### Native Paper / LuckPerms acceptance

Final native-04 passed **33 checks**, no failure, normal isolated-server shutdown and exit 0: Paper 1.21.11 build 132 (`c5eb079`), LuckPerms 5.5.84, JDK 21, loopback port 25669. The tested plugin copy and final canonical artifact are byte-identical (SHA-256 `9213803BC7003767D4A435336B2FC88941CFEC10A560018A2B55F78CA1878472`); the producer and Sample class bytes also match the final compiled classes. There were no ERROR/SEVERE/assertion or movement-sample-rejection entries in the final native log.

The probe uses a real CraftPlayer/ServerPlayer, real registered production handlers, actual Paper input-packet processing and the real Quest/Reward/Player/HUD services. Only its temporary online UUID registration and tick clocks are controlled. Move events and input packets are generated by the probe without a network client. Native state, cancellation, Input publication, YAML roundtrip and outgoing HUD packets are checked. This is controlled native/API acceptance, not a Minecraft network-client or visual acceptance.

The first two native attempts deliberately remain archived as failed diagnostics, not successful tests: ten nominal Z-axis blocks yielded nine whole projected blocks because the fixture reset body yaw but left CraftPlayer head yaw slightly rotated. Diagnostics showed accepted events, WALK=10 and BACKWARD=9. Resetting both head and body yaw fixed the fixture, without loosening production direction/projection checks. An intermediate native run then passed 33 checks. Final results below must refer to the final artifact, after the unassigned-key fast path.

Real Minecraft-client checks remain open: backward/diagonal motion and head turns, key presses/repeated crouch, supported movement modes, visual HUD names/percent/XP restoration and Navigator actionbar priority. Controlled native players are not represented as real clients.

## Exact changed paths

- `src/main/java/dev/vapee/core/quest/QuestMovementProducer.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementTestSupport.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementIntegrationHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementModuleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestProgressHudHarness.java`
- `docs/DEVELOPER_GUIDE.md`
- `reports/08e/STEP_08E_FINAL_MOVEMENT_PROGRESS.md`

## Protection and delivery

Pre-commit comparison passed for all 1510 original files: only the seven intended existing files changed; the eighth intended path is the new report. Both Run configurations and both protected receipts retain their original SHA-256 values. No unrelated content or LF/CRLF-only changes occurred outside this scope; the six intended Java files were formatted explicitly with the existing Google Java Format 1.22 AOSP configuration. All existing references, remote configuration and the protected worktree status/files remain unchanged. Final after-commit protection results belong to the external delivery receipt. Both Run configurations, protected Step-06B receipts, Recovery/other existing feature branches, the existing chat-polish worktree and its pre-existing untracked report are protected. Live operator files are not sampled as byte-identical while externally running; this task performs no operator-server operations. No player data migration or automatic catalog installation. Previously lost Distinct evidence is not reconstructed.

Evidence and native helper sources/scripts are outside the repository under `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08e-movement-20261108-012844/`. The one local feature commit SHA is recorded in the final delivery receipt/response. No amend, push, merge, PR, deployment, branch deletion or Step 08F.

API references: [Paper Input](https://jd.papermc.io/paper/1.21.11/org/bukkit/Input.html), [Player current Input](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html#getCurrentInput()), [PlayerToggleSneakEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerToggleSneakEvent.html).

## Post-Audit Correction – Velocity Guard Preservation

### Confirmed defects and narrow correction

Step 08E.1 continues on `codex/step08e-final-movement-progress`, without changing implementation commit `0b626cd41d9904956075a02e075ae22b2f77b3dd` or master/origin/master `6136eb2eef794508011f4eaa656b4121ddb44dfc`. The earlier implementation figures above are historical evidence for that original commit, not evidence that the newly discovered combinations were safe.

Two independent executable reproductions confirm the shared guard defects. Effective velocity at tick 100 creates `blockedUntil=120`. A cancelled move at 101 calls `reject()`, which formerly overwrote it with 102. Separately, a cancelled velocity at 101 formerly ran `forget(id)` before cancellation validation, removing the guard completely. Both allowed progress at 102. This is inherited shared infrastructure, affecting all seven keys, including the new BACKWARD and SNEAK_TOGGLE paths.

`Sample.blockedAt(tick)` now centralizes the existing signed elapsed-tick comparison. `reject()` still clears fractions, accumulated turn and jump intent and resets the endpoint according to the existing rejection path, but only creates a one-tick deadline if no exclusion is active. It preserves an active deadline exactly, without shortening OR extending it. Cancelled velocity is a no-op after current-instance/runtime checks. New effective velocity replaces the sample and starts twenty ticks from its own event. No numeric maximum of absolute deadlines is used: signed subtraction remains correct across integer tick wraparound for these short intervals.

The audit of every sample mutation also found cancelled/invalid Jump events and invalid Sneak locations deleting the same guard. These rejected observations now use `reject()` too. Their regression cases were included in the same pre-fix red run. No second timer, map, cooldown manager, scheduler, history or persisted field is introduced.

### Exact lifetime and ownership contract

| Sequence | Required boundary |
| --- | --- |
| Velocity at 100; rejected moves at 101, 110 and 119 | Still blocked at 119; eligible from 120 |
| Velocity at 100; cancelled velocity at 101, 110 and 119 | Same original deadline 120 |
| Velocity at 100; another effective velocity at 110 | New deadline 130; blocked at 129 |
| Ordinary rejection without an active exclusion | Current tick rejected, normal sampling from the next tick |
| Cancelled velocity without an exclusion | No new block; valid fractions and toggle cooldown retained |

While blocked, invalid endpoints/input observations do not become credited distance. Pre-impulse fractions are not resurrected at expiry. Repeated rejections neither re-arm the velocity interval nor consume a new twenty-tick window. A genuinely rejected event at an already expired deadline may still create the normal local one-tick rejection; this is a new rejection, not extension of a live velocity exclusion.

Current online instance, entity identity and runtime authority retain ownership. Stale Move/Jump/Sneak/Velocity/Quit callbacks cannot remove the replacement session's guard. Assignment, catalog reload and rollback do not bypass a still-active physical exclusion of the same valid session; after expiry the current assignment/catalog starts fresh observations. Legitimate teleport/world/logout/rejoin/Lobby/Activity and module retirement cleanup remains unchanged, as do existing ineligible/unloaded/no-assignment/error cleanup paths. This fix does not persist velocity history across those lifecycle boundaries.

The ten-tick credited Sneak activation interval remains separate. Cancelled velocity can no longer erase that cooldown or valid distance fractions. Genuine ends/starts during the velocity exclusion do not count. Backward cosine 0.5 / sixty-degree and forty-five-degree turn limits, original key-bit positions, distance slots, input gates, Jump confirmation, rotation, per-segment/per-tick budgets and reporter ownership are unchanged.

### Actually executed red / green regression

Before changing production, the new executable tests ran against unchanged commit `0b626cd41d9904956075a02e075ae22b2f77b3dd`; producer SHA-256 `B187A3B8C3D6E925A9E049175A335E3B1508B489119010EAA32F6EA2DD07C7C5`. **290 scenario groups ran, 207 failed, process exit 1.** The log explicitly reports expected progress 0 and actual progress 1 at t+2 for both cancelled-Move and cancelled-Velocity sequences. Those failures are diagnostic evidence, not passing verification.

With the minimal correction, the identical test-source SHA-256 `3DCF8CFA877B6BC7034CC6EEAEC772DCBF690EC51D0A8E2473F1935FD0AFE1F9` passes **all 290 groups / 1,285 new checks, exit 0**. Full QuestMovementHarness including its original 175 checks passes **1,460 checks**. Additional focused runs retain Integration 77, Module 25, HUD 79 and Distinct 529 checks; all 42 Distinct pending-integrity scenarios pass.

Coverage combines all seven keys with cancelled moves, invalid coordinates, mismatched worlds/endpoints, oversized motion, same-tick bursts, cancelled velocity, cancelled/invalid jumps and invalid Sneak locations. All guard/renewal combinations run at ordinary tick 100, immediately before positive integer overflow, and negative ticks. Tests assert no progress at t+2 and t+19, normal credit at t+20, and renewed boundaries t+29/t+30. Rotation uses four legitimate ninety-degree samples on separate ticks rather than violating its budget. Tests also cover one-tick-only normal rejection, fractions before/after impulse, stale/replacement sessions, assignment/catalog changes, profile unload/reload, legitimate lifecycle transitions, runtime retirement and re-enable.

Real QuestService/RewardService integration uses target-one definitions for WALK, BACKWARD and SNEAK_TOGGLE across both original bypasses. Before expiry there is no progress, completion, coin grant, completion chat or numeric progress notification to the existing HUD boundary. At expiry the normal grant occurs exactly once. Overflow-induced Reward Pending, a real failed retry, a later guard around a reached target, eventual successful retry and completed idempotence retain their contracts. No changes to QuestService, rewards, HUD, player persistence, daily selection or the Step-08D.1 distinct-evidence fix.

Final canonical verification: The new complete command `pwsh -NoProfile -File .\scripts\verify.ps1` passed **245/245 discovered/executed/passed harnesses, 37,821 checks, Failed=0, Missing/Invalid=0, Maven clean package PASS and Spotless PASS (existing pilot allowlist)**. The inventory is identical to the historical Step-08E baseline. Only QuestMovementHarness changes its count, from 175 to 1,460 (+1,285); all other harness counts remain unchanged. All seven movement keys, Community/Lobby producers, Daily Reset/Force, HUD, persistence, rewards and the Step-08D.1 Distinct guard retain their existing regression coverage. No production or test source changed after this final build; only the report received actual result values.

### Additional isolated native acceptance

The new isolated native-01 run passed **114 checks**, no failures, normal own-server exit 0: Paper 1.21.11 build 132 (`c5eb079`), LuckPerms 5.5.84, JDK 21, loopback-only port 25670. It actually reran the original 33 assertions and added 81 guard assertions. Both concrete bypasses are prevented for WALK, BACKWARD and SNEAK_TOGGLE; exact t+19/t+20 and renewed t+29/t+30 boundaries, repeated cancellations, integer wraparound, no early coins/HUD packets and normal post-expiry completion are verified. The native-tested artifact and canonical artifact have identical SHA-256 `3B3323E547D177946DE6709E949405B08DB1C458473C70AE2CE605B72F643CB9`.

The additional probe reuses the original 33 native assertions and adds both cancellation sequences for WALK, BACKWARD and stationary SNEAK_TOGGLE, ordinary and wrapped ticks, repeated rejection, exact expiry, renewed effective impulse, no early rewards/HUD packets, and eventual once-only completion with unchanged real XP. Real CraftPlayer/ServerPlayer, native input-packet processing, registered production event handlers and real quest/economy/player/HUD services are used. Online UUID registration and tick clocks are controlled; events/inputs are synthetic. This is native/API acceptance, not a network or visual Minecraft-client test. The original historical 33 checks are counted as rerun only when included in the actual new successful run.

### Exact correction paths and protection

Only these four existing files belong to the additional local fix commit:

- `src/main/java/dev/vapee/core/quest/QuestMovementProducer.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementHarness.java`
- `docs/DEVELOPER_GUIDE.md`
- `reports/08e/STEP_08E_FINAL_MOVEMENT_PROGRESS.md`

Pre-commit comparison passes for all 1511 original files across both existing worktrees; only the four intended existing paths differ. Both Run configurations and both Step-06B receipts retain their original SHA-256 values. The protected worktree and its pre-existing untracked report, Recovery/other feature branches, tags and remote configuration are unchanged. No unrelated LF/CRLF normalization occurred. Master and origin/master remain at `6136eb2eef794508011f4eaa656b4121ddb44dfc`. Final after-commit preservation results are recorded externally. Operator-owned live files may change externally and are not claimed byte-identical; this task neither uses nor modifies the operator server.

Evidence, before hashes/references, unchanged-source red proof, same-test green proof, native probe and final verification receipts are outside the repository under `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08e1-velocity-guard-20261108-032137/`. The new local fix SHA belongs to the final receipt/response. No amend, history rewrite, merge, push, PR, deployment, operator-server intervention or Step 08F. Real Minecraft-client acceptance remains open.

## Post-Audit Correction 08E.2 – Cancellation & Lifecycle Guard Integrity

### Scope, evidence and confirmed causes

This correction stays on `codex/step08e-final-movement-progress`, directly after unchanged `b0eb08a44c57734849668bf0304d5e726c10765b`; original Step 08E `0b626cd41d9904956075a02e075ae22b2f77b3dd` and master/origin/master `6136eb2eef794508011f4eaa656b4121ddb44dfc` remain unchanged. Historical results in earlier sections describe their original commits. No operator server is used.

The Flight handler removed its sample even when Paper cancelled the request and left the actual Flying flag false. The same unconditional cleanup pattern existed for cancelled Teleport (both entry points), GameMode, Glide, Mount, Dismount and Paper PlayerDeath events. Paper 1.21.11 PlayerDeathEvent inherits Cancellable: assuming death was always an irrevocable notification would leave another hole. Rejected lifecycle attempts could erase both the physical twenty-tick exclusion and accepted Sneak cooldown history. Accepted no-op Flight/GameMode/Glide requests also erased state despite an unchanged target state.

The service audit found a separate instance of the same cause: LobbyPlayerStateService calls beforeStateChange before operations and afterStateChange in finally, including no-op synchronize, early returns and failed attempts. ActivityService calls beforeJoin before membership is established, and membershipChanged on failed handoff, duplicate membership, rollback and final reconciliation. These are not unconditional commit notifications. No foreign service contract is changed; the movement observer now reconciles its own state against current eligibility.

A controlled API-read exception in onSneak also reproduced guard loss through failed(UUID). Error cleanup now retains a current owned guard and accepted Toggle history rather than treating a failed observation as evidence of lifecycle success. This is controlled error-path hardening, not a claim that a network player can force arbitrary Bukkit API exceptions.

### Complete handler and callback audit matrix

The matrix was checked against the actual API hierarchy, current source, service callback sites and compiled behavioral tests. Paper 1.21.11 build 132 bytecode confirms event-before-commit ordering for Flight, Glide, Mount and Dismount. Native/API acceptance is reported separately below.

| Entry point | Cancellable / authority | Cancellation or ineffective observation | Accepted or confirmed transition / coverage |
| --- | --- | --- | --- |
| onMove | Yes | cancelled/invalid/discontinuous/over-budget -> reject; active deadline retained | valid guarded sampling, replay exclusion and all seven-key tests; Teleport delegates to onTeleport |
| onJump | Yes | cancelled/invalid -> reject; no pending jump adopted | valid current unblocked intent, matching accepted Move required; old 290 cases retained |
| onSneak | Yes | cancellation returns without state change; invalid location -> reject | actual pre-state plus accepted-observation state and ten-tick credited interval; API-read failure uses safe observation cleanup |
| onVelocity | Yes | cancellation is a no-op in a current runtime; stale instance ignored | eligible loaded current player receives a fresh twenty ticks; ineligible context is released without retaining history |
| onTeleport and Teleport via onMove | Yes, own Paper HandlerList | cancellation, invalid source/target or no positional change -> reject with no invented target | accepted finite positional/world change -> forget; fixtures test each entry and combined Move-then-Teleport calls, native tests real Teleport dispatch |
| onGameMode | Yes | cancelled or unchanged target mode -> no-op | differing accepted target -> forget; actual native mode checked |
| onFlight | Yes | cancelled or unchanged target flag -> no-op | differing accepted target -> forget; packet final Flying state checked; later actual Velocity still starts twenty ticks |
| onGlide | Yes | cancelled or unchanged target flag -> no-op | differing accepted target -> forget; native start/stop methods validate final flag |
| onMount | Yes | cancelled -> no-op | accepted native mount -> forget; native vehicle relation checked |
| onDismount | Yes, unless forced non-cancellable | effective cancellation -> no-op | accepted/forced removal -> forget; cancellation cannot be set for a non-cancellable event |
| onDeath | Yes in Paper | cancelled -> no-op | accepted death -> forget; successful fixture state and Paper cancellation API tested |
| onWorld | No | not an attempt notification | actual world-change notification -> forget; existing world/reset coverage |
| onJoin | No | new session notification | forget only owned identity; new session cannot inherit prior history |
| onQuit | No | disconnect notification | owned identity removed, including lookup already absent; stale player cannot remove replacement |
| onRespawn | No | respawn notification from death context | forget; original respawn/re-enable tests retained |
| beforeStateChange / beforeJoin | synchronous pre-attempt service callback | reject existing owned observations only, retain live deadline and accepted Toggle state | actual resulting context is reconciled by after callback/native events; failed/no-op handoffs cannot unlock |
| afterStateChange / membershipChanged | finally/reconciliation callback, not unconditional commit | eligible current loaded context retains guard via observation rejection | offline/ineligible/unloaded context releases its owned measurement; tests cover rollback and actual context changes |
| failed | observation exception, not context proof | discard owned observations while retaining deadline/Toggle history; secondary cleanup error is logged/suppressed | no new progress or replacement session mutation; controlled getLocation failure regression |
| forget(Player) / forget(UUID) / clear | internal ownership and lifecycle boundary | Player form checks current online identity plus entity identity | UUID cleanup is current-context reconciliation or loaded/assignment cleanup; clear belongs to retired runtime |

### Every sample mutation and invariants

There remains exactly one UUID sample map, seven technical key bits and four distance slots, without retained Player/World objects. Map insertion occurs only at valid sample initialization, owned rejection, or effective eligible velocity. Full removal occurs at confirmed lifecycle/context invalidation, unloaded/no-assignment cleanup or runtime clear. The Player-specific path rejects a different current online instance, even before comparing entity IDs.

Only effective velocity assigns a new twenty-tick deadline. reject assigns tick+1 only when the existing interval has expired. blockedAt uses signed tick subtraction; active deadlines survive normal and wrapped ticks, repeated rejection and both Teleport entry points. sample clears blocked only after expiry, and then revalidates assignment/catalog identity. Fractions, turn, pending jump and endpoint are discarded on rejection without replenishing the tick budget. Cancelled boolean lifecycle requests leave valid observations untouched. Service pre/finally callbacks discard observations but retain accepted Sneak state and last credited tick. Discrete toggle still has no distance accumulator.

Assignment/catalog generation changes remain lazy measurement invalidation and cannot release an active physical exclusion in the same valid session. Real World, mode, flight/glide, vehicle, death/respawn, join/quit, BUILD/Activity and runtime transitions retain safe cleanup. An impulse observed in an already ineligible context is not carried into a new eligible context. Existing current(), eligible(), reporter, reward, persistence, daily selection and HUD ownership remain central; no second cache, task, event bus, player field or module.

### Confirmed hypotheses and limits of the findings

Executable same-context cases confirm premature progress after cancelled Flight, Teleport/World-teleport, GameMode, Glide, Mount, cancelled Death, no-op target events, failed/no-op service notifications and observation exceptions. Cancelled Dismount also erased state in API dispatch. A real mounted player is already movement-ineligible, so that structural cleanup defect is not claimed as an independently demonstrated valid-context vehicle exploit; native mounted cancellation is tested as vehicle-state/eligibility behavior, while synthetic unmounted API dispatch covers defensive guard retention. Likewise, cancelled Flight/Glide end while actually flying/gliding remains ineligible; spurious end callbacks in a valid context are covered without claiming a vanilla packet can create every synthetic case.

Forced non-cancellable Dismount is not a cancellation vulnerability: the API refuses cancellation and actual removal is a legitimate transition. World, Join, Quit and Respawn are not cancellable and retain their existing contracts. API callbacks accepted by Paper are observed at MONITOR before native commit; arbitrary foreign plugins directly modifying native state without normal events are not represented as Minecraft-client validation.

### Actual red / green evidence

The first new focused run against unchanged working production `b0eb08a44c57734849668bf0304d5e726c10765b` (producer SHA-256 `2C5615F6C0D3A78CEE674A2D4CA4479661E2BF7C74CE21C84D233D4996774F6F`) executed **621 scenario groups, 619 failures, exit 1**. After making the legacy success fixtures describe actual context changes, the final original producer was compiled into an isolated external class directory; no Git reset or production replacement was used. The exact final test sources again produced **621 groups / 619 failures / exit 1**. Logs explicitly show expected zero and actual one progress before the original deadline.

The same final test-source hashes on the corrected producer pass **621/621 new groups, 2,482 checks, exit 0**. All **290 existing 08E.1 guard groups** still pass. QuestMovementHarness passes **3,942 checks**, Integration 77, Module 25, HUD 79 and Distinct 529; all 42 distinct pending-integrity scenarios pass. The only adjustments to existing tests strengthen their meaning: successful Teleport uses a changed destination, successful BUILD/Activity fixtures actually change context, and borrowed pre-attempt observer tests verify retained accepted Toggle history rather than asserting that an attempt deletes all state. No existing test or assertion group is removed or bypassed.

Tests cover every key with sufficient valid post-cancellation events (including four tick-separated rotation segments), repeated rejection, t+19/t+20, positive/negative/wrapped ticks, real small-target quest grants and pending retry, no premature numeric HUD signal/chat, cooldown retention without velocity, stale sessions, actual successful transitions, and eligible versus ineligible impulse ownership. The new fixture error switch controls only a Bukkit location-read exception.

Final canonical verification was freshly executed with `pwsh -NoProfile -File .\scripts\verify.ps1`: **245 discovered / 245 executed / 245 passed, 40,303 checks, Failed=0, Missing/Invalid=0, exit 0**. Maven clean package and Spotless both PASS. The result inventory matches all 245 previous harness classes exactly; the only check-count difference is QuestMovementHarness, 1,460 -> 3,942 (+2,482). This is a new verification of the correction, separate from the historical 37,821-check result. The final report-value update is followed by a focused repository-link check; no Java changes follow the canonical run.

### Isolated native API / service acceptance

The new disposable run passes **386 native assertions, zero failures**, on Paper **1.21.11 build 132**, LuckPerms **5.5.84** and Java 21. This includes the prior 114 assertions plus 272 lifecycle assertions. The owned process exits normally with code 0. The server runs only under the external evidence directory on loopback port 25671; no operator process is stopped or used.

For Walk, Backward and Sneak-Toggle, real Flight input packets, cancelled positional/world Teleports, GameMode, native Glide and Mount operations preserve the guard at t+2/t+19 and permit one target-one completion with seven coins at t+20. Before expiry there are no progress, completion, coins, XP-bar or actionbar packets. The same checks exercise actual no-op Lobby synchronization, synthetic cancellable Death/Dismount dispatch, and actual accepted native transitions. Separate real mounted Dismount cancellation leaves the vehicle relation intact. Cancelled real Flight/Glide ends keep their actual ineligible flags. All seven keys, wrapped ticks and pending rewards receive the broader executable harness coverage above; these are not all seven-key native-client tests.

The copied native plugin JAR and canonical build have identical SHA-256 `C55EB25F5EE7B59B6CB74468FFCE7E78B9198B657256AA1F96B7E466010EE0CB`. Producer, Sample, Point and Jump class bytes also match the canonical target classes individually. Native results, process exit, artifact hashes, class-identity receipt and logs are retained externally.

The probe uses the existing disposable Paper 1.21.11 / LuckPerms setup with actual CraftPlayer, ServerPlayer, input/abilities packet handlers and registered production listeners. It checks final native state after cancellation, not just event flags. It also covers actual LobbyPlayerStateService synchronization, actual accepted lifecycle operations and the existing 114 assertions from 08E.1. Synthetic Dismount/Death API dispatch and real mounted Dismount behavior are explicitly distinguished. Controlled online registration and tick clocks remain test seams. This is not a real Minecraft network-client test or visual acceptance.

### Exact correction paths and protection

Only these six existing paths belong to the one additional local correction commit:

- `src/main/java/dev/vapee/core/quest/QuestMovementProducer.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementTestSupport.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementModuleHarness.java`
- `docs/DEVELOPER_GUIDE.md`
- `reports/08e/STEP_08E_FINAL_MOVEMENT_PROGRESS.md`

Pre-commit preservation passes for all **1,511 original files** across both existing worktrees (782 main, 729 protected); only the six intended existing paths differ. Both Run configurations and both protected Step-06B receipts retain their original SHA-256 values. The protected worktree retains its original HEAD, branch, status and pre-existing untracked report. Recovery/other feature branches, tags and remote configuration remain unchanged; master and origin/master remain at `6136eb2eef794508011f4eaa656b4121ddb44dfc`. No unrelated normalization or file-inventory changes occurred. The post-commit comparison is retained in the external delivery receipt. Live operator files may change independently and are not claimed byte-identical; this task does not use or modify them.

Evidence, bytecode inspection, original-source red compilation, identical-source green results and disposable probe sources/scripts are outside the repository in `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08e2-lifecycle-20261108-035750/`. No amend, rebase, reset, stash, clean, merge, push, PR, deployment, branch deletion, operator-server intervention or Step 08F. Real Minecraft-client acceptance remains open.

API references for the additional audit: [Paper PlayerDeathEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/PlayerDeathEvent.html), [Paper EntityDismountEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/EntityDismountEvent.html).

## Post-Audit Correction 08E.3 – Sneak State Reconciliation

### Confirmed cause and required reproduction

The read-only integration audit stopped at unchanged `d046b5608531d13a3128225c464e12079837718b`. In the same eligible session and assignment, Start at tick 100 credits one. Cancelled Move at 110 rejects that tick. An accepted End at 110 changes the actual player flag to false, but sample() returned null before onSneak could update its accepted state. Start at 111 therefore encountered stale `sample.sneaking=true` and was rejected as a duplicate: actual progress 1, expected 2. This is lost legitimate progress, not a demonstrated velocity bypass.

The same state-loss mechanism was reproduced when an accepted End was followed by a controlled location-read exception or invalid coordinates: the old positional early return hid a reliable boolean transition. These are API/error-path regressions, not a claim that a network client can force arbitrary Bukkit API failures. A separate controlled synchronous progress-observer test also demonstrates why the shared current() gate must reject a newly blocked sample before the remaining reports of the same Move. This is service-boundary hardening, not a claimed player exploit.

### Observation versus progress permission

Only Sneak requests observation access to a blocked sample. Existing eligibility, actual online instance, runtime authority, loaded profile, assigned key and catalog/assignment identity still apply. When that identity requires a fresh sample, an active physical deadline is copied unchanged; the new boolean baseline comes from the actual pre-event player state. Old assignments, catalog generations and sessions cannot contribute their previous Toggle state.

An uncancelled event must disagree with both the actual pre-event flag and the last accepted observation to be a new transition. A reliable End is recorded before a positional read can fail. Invalid positional observations still use reject; invalid Starts do not become confirmed state. A valid Start during a guard updates only observation. The shared current() predicate checks the active deadline before any report; only an actually credited Start changes lastSneak. End, cancelled events, blocked Starts, repeated rejection and observation errors neither reset nor prolong that credited-start cooldown.

Twenty-tick Velocity and one-tick rejection deadlines retain signed tick subtraction and original expiry. Observation never renews them. At expiry an already active blocked Start is not replayed as progress. A fresh accepted End followed by Start is required, with the normal ten-tick credited-start interval. Duplicate callbacks before Paper commits its flag remain rejected. Cancellation remains an immediate no-op.

### Other movement states and lifecycle review

| State | Required treatment and evidence |
| --- | --- |
| WALK/SPRINT/SNEAK/BACKWARD fractions | Four independent distance slots; rejection/Velocity discard partial measurements. Blocked Sneak observations add no distance or per-tick budget. Only fresh post-guard displacement counts. |
| Pending Jump | No new jump intent while blocked; rejection/Velocity discard old intent. Accepted matching Move remains necessary. |
| Rotation | No blocked accumulation; rejected arc stays discarded, no deferred full-turn report. |
| Backward input / Sneak and Sprint flags | Input and physical flags are read from each accepted Move, without a second input cache. Sneak boolean observation never synthesizes distance. |
| Toggle state / credited-start time | Accepted state is reconciled independently of progress; lastSneak changes only on credited Start. Nine/ten/eleven-tick and wrapped-tick tests distinguish these responsibilities. |

All seven keys have executable collateral checks with actual accepted Sneak events inside a physical guard and fresh post-expiry events. Assignment replacement, real DailyQuestService rotation, catalog reload/rollback, logout/rejoin with a new Player, stale events, actual-context BUILD/Activity boundaries, Flight/Glide and runtime clear are covered. Existing real QuestModule disable, failed-release retry and re-enable harnesses remain unchanged. No scheduler, persistent field, cache, module, configuration, command or permission is added. QuestService, RewardService, player persistence, Distinct Pending, HUD and Double Jump production code remain unchanged.

### Executed red / green regressions

Tests were written before the production correction. The first focused run on the unchanged producer (SHA-256 `B059237C2DCF15ADC36CB53B2608558AB2AFC267E49DAFFE7D1C2C58B1BCBF97`) ran 67 scenarios, **35 failures, exit 1**. After adding Daily/rollback/logout and the synchronous gate case, the final original source was compiled into an external class directory without Git reset or source replacement. The final tests ran **71 scenarios, 36 failures, exit 1**. Logs show the required target-two expected=2 / actual=1, including wrapped ticks.

The identical final test source (SHA-256 `ACC29EB246553E3977CD06CE8C812C71882D3359C8DAFBD194AA3B3FB2002CC1`) passes **71/71 scenarios, 158 new checks, exit 0** on the fix. QuestMovementHarness now passes **4,100 checks**, including all **290 Velocity and 621 Lifecycle scenarios**. Integration 77, Module 25, HUD 79 and Distinct 529 checks pass; all 42 distinct pending scenarios remain green. No existing tests are deleted or weakened. During implementation the old invalid-Sneak controls detected a validation-order regression for unrelated keys; the production order was corrected, and the existing assertions were preserved.

The target-two cases use real QuestService, RewardService, EconomyService and PlayerService. They verify two numeric progress/HUD-boundary signals, exactly one completion/pending message, seven configured coins once, replay idempotence, genuine overflow-induced REWARD_PENDING, failed retry and eventual successful retry without a new numeric HUD signal. Nine/ten/eleven-tick cases include accepted and cancelled ends; the 100/105/110/115/125/126/127 sequence proves no retroactive progress or cooldown shift. Wraparound uses the same executable operations, not source-string checks.

Canonical verification freshly executed `pwsh -NoProfile -File .\scripts\verify.ps1`: **245 discovered / 245 executed / 245 passed, 40,461 checks, Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS, Spotless PASS**. All 245 baseline harness classes are retained. The sole count change is QuestMovementHarness, 3,942 -> 4,100 (+158); every other count remains unchanged. These are new correction results, separate from the historical 40,303-check baseline. Only final report values follow this run; final repository-link checks and preservation are recorded separately.

### Isolated native Paper acceptance

The new disposable **native-02** run passes **440 assertions, zero failures**, Paper 1.21.11 build 132, LuckPerms 5.5.84 and Java 21, normal own-process exit 0. This actually reruns the prior 386 assertions and adds 54 reconciliation assertions; historical checks are not merely carried forward as new results. The new server uses only the external evidence directory and loopback port 25673. The copied plugin and canonical JAR both have SHA-256 `9EFCD3FF7BC96C4251F1A82C8407796D827F4083B081F267DDE0D84E5F4E200D`; Producer, Sample, Point and Jump class bytes match the canonical target classes individually.

The first native-01 attempt stopped at assertion 425, expecting a fresh End after a cancelled End without first changing native input back to Start. Bytecode inspection proved that Paper updates last-client-input even when the Toggle is cancelled and the physical flag stays true. Repeating identical End input therefore emitted no accepted End. The external probe was corrected to send Start input (which cannot recount the already active state), then verify a genuinely accepted End callback and final false flag. No production or repository test change was needed; the entire corrected suite was rerun in a fresh native-02 directory. The failed attempt and its original helper source are retained and are not counted as successful acceptance.

The disposable probe extends the existing 386-check suite with real native input packets for accepted/cancelled Starts and Ends. MONITOR observes the old player flag; Paper commits the accepted flag afterwards. It checks the exact lost-End reproduction, original and wrapped ticks, physical Velocity guards, no retrospective credit, cooldown boundaries, actual HUD packets, actual server XP preservation and real completion/pending payouts. Controlled online registration and tick clocks remain explicit seams. Existing synthetic Death/Dismount checks retain their earlier scope. This is API/service acceptance, not connected or visual Minecraft-client acceptance.

### Exact paths and preservation

Only these four existing paths belong to the additional local correction commit:

- `src/main/java/dev/vapee/core/quest/QuestMovementProducer.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementHarness.java`
- `docs/DEVELOPER_GUIDE.md`
- `reports/08e/STEP_08E_FINAL_MOVEMENT_PROGRESS.md`

Pre-commit protection compares all **1,511 original files** across both existing worktrees and permits only these four intended existing paths to differ. Both Run configurations and both protected receipts retain their historical SHA-256 values. The foreign worktree, its pre-existing untracked report, Recovery/other feature branches, tags and remote configuration remain unchanged. No unrelated normalization or file-inventory change occurred. The operator deployment JAR is separately checked unchanged; the build artifact changes only through the authorized verifier. Live operator world/log/player files may change independently and are not claimed byte-identical. Final post-commit evidence is recorded externally.

Original Step 08E, 08E.1 and 08E.2 commits remain unchanged; the new commit is directly after d046b560 on the existing feature branch. Master and origin/master remain `6136eb2eef794508011f4eaa656b4121ddb44dfc`. Evidence is outside the repository in `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08e3-sneak-reconciliation-20261108-151458/`, including source hashes, original-source red compilation, final green logs and delivery/preservation receipts. No amend, rebase, reset, push, merge, PR, deployment, branch deletion, operator-server intervention or Step 08F. Real Minecraft-client acceptance remains open, especially blocked End/Start timing, cancellation/Double Jump interactions and visible HUD/reward feedback.
