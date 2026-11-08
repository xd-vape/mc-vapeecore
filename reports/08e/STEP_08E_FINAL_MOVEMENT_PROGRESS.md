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