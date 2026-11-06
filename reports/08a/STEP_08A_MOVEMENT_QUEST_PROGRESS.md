# Step 08A — Movement Quest Progress Producers

## Scope and starting point

Implementation on `codex/step08a-movement-quest-progress`, based on master
`fd5ec7a722afe3615b7b5536ee1fbb5886174f41`. The original worktree was clean.
All existing modules, seven reload participants, permissions, commands, bundled
quest definitions, persistence schema and reward ownership remain unchanged.
The 47-quest configuration is not activated.

## Implemented contract

| Progress key | Observation |
| --- | --- |
| `movement:walk` | Whole horizontal blocks accumulated from eligible ground movement, including sprinting and sneaking |
| `movement:sprint` | Additional horizontal blocks while sprinting |
| `movement:sneak` | Additional horizontal blocks while sneaking; takes precedence over contradictory sprint state |
| `movement:jump` | Paper jump intent confirmed by the matching accepted movement event |
| `movement:rotation_full` | Signed net 360-degree ground rotation in either direction |

The walk/subset interpretation follows the user's explicit clarification.
No quest IDs, localized names, targets or rewards are built into the producer.
Multiple definitions with the same key use the existing reporter fan-out.

## Measurement and boundaries

The MONITOR listener checks the current online player instance, loaded profile,
unfinished assigned definitions, current definition snapshot and assignment
generation. NORMAL and no Activity membership are required. Survival and
Adventure are supported. BUILD, Creative, Spectator, death, vehicles, flying,
gliding, swimming, water, climbing and riptide discard observations. Merely
having `allowFlight` is deliberately allowed: LobbyGlide arms double jump using
that flag. The producer does not change flight, velocity, game mode or position.

Ground distance requires directional `Player.getCurrentInput()`, uses
`hypot(dx, dz)`, and retains independent fractional remainders for each distance
key. Y-only movement earns no distance. Segments and aggregate movement per
server tick are limited to one horizontal block; absolute segment Y delta is
limited to one block. Cancelled, nonfinite, discontinuous and excessive movement
discards partial measurements and suppresses the rest of that tick. Invalid
events cannot refill the same tick's distance budget.

Paper `PlayerJumpEvent` records a temporary intent; only an accepted move with
matching world/positions within two ticks credits it. Credited jumps require at
least five ticks between them. Y changes alone never produce jumps. Effective
velocity events suppress observations for 20 ticks. Teleports, world changes,
join/quit, death/respawn, mode/flight/glide/mount changes, BUILD and Activity
transitions invalidate partial measurements. Stale old-session events cannot
clear the replacement session's measurements.

Yaw uses the shortest signed difference across wraparound. Reversal subtracts
from the current arc. Changes over 90 degrees per event or an aggregate absolute
90 degrees per tick are rejected for rotation. A completed signed 360 degrees
emits one unit and consumes that arc. Pitch-only events and replayed endpoints
produce no progress. Observation gaps over 40 ticks establish a new baseline.

## Ownership, performance and existing services

`QuestModule.startMovement` binds once after the existing `enableAll()` sequence
has made Lobby and Activity services available. Registration order is preserved.
The module owns the listener and both context subscriptions. Failed acquisition
rolls back; failed release retains the original resources for explicit cleanup
retry. Disable revokes authority before cleanup. Re-enable uses a fresh producer.
There is no added scheduler; the existing 100-tick quest task remains the only
Quest task.

Samples contain UUIDs, primitive coordinates, temporary fractions, bounded turn
and jump state, plus identity tokens for existing in-memory assignments and
definitions. They retain no Player/World references and no second definition or
persistent progress cache. Assignment replacement/reset invalidates partial
samples even when the same quest ID is assigned again. Ordinary progress updates
and flushes do not invalidate fractions. The assignment token is runtime-only.

`QuestProgressReporter` → `QuestService` → `RewardService`/`PlayerService` remain
authoritative. No movement path loads YAML, calls a repository or grants coins
directly. Existing deferred saves, reward-pending handling, completion and dirty
retry contracts are preserved.

API references:
[Paper jump event](https://jd.papermc.io/paper/1.21.11/com/destroystokyo/paper/event/player/PlayerJumpEvent.html)
and [current input](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html#getCurrentInput()).
Jump `to` describes the client packet rather than a complete jump arc. Local
Paper 1.21.11 build 132 bytecode was also inspected: jump detection precedes the
regular move notification and uses matching cached/raw positions.

## Verification

The final complete command `pwsh -NoProfile -File .\scripts\verify.ps1` passed:

| Check | Actual result |
| --- | --- |
| Maven clean package | PASS |
| Spotless | PASS (repository's existing pilot allowlist) |
| Discovered / executed / passed harnesses | 237 / 237 / 237 |
| Total checks | 34,913 |
| Failed / missing-invalid | 0 / 0 |
| Original harness inventory | All 234 retained; exactly three new executable harnesses |

The final receipt is archived as `canonical-final-results.json`, with full
`canonical-final-2.log` outside the repository. Focused executable harnesses also
passed: movement 64 checks,
integration 28 checks, module ownership 24 checks, and existing Quest lifecycle
271 checks. These focused runs are not the full canonical result.

The new harnesses exercise fractions, subsets, diagonal/Y-only movement, input,
jump confirmation/cancellation/replay, signed turns/wraparound/oscillation,
per-tick burst limits, invalid contexts, velocity, session changes, assignments,
reload and teardown. Integration tests use actual configured targets 100/500,
multiple definitions, completion, pending rewards due to balance overflow,
deferred save failures/retry and unloaded profiles. Lifecycle tests cover
before/after-effect registration failures, observer release failures, failed
unregistration, explicit cleanup retry and re-enable.

The first complete run built successfully and passed Spotless, with 237
discovered/executed harnesses, 236 passed, one failed, zero missing/invalid and
34,871 successful checks. The failure was the pre-movement
`QuestLifecycleHarness` source boundary: it prohibited all Activity imports and
all movement events anywhere in the Quest package. Its intended foundation
boundary is retained and made precise: only `QuestModule` imports the two context
services, only the movement adapter imports their observer interfaces, and the
domain/foundation still rejects gameplay hooks, commands and UI dependencies.
No original harness was removed or disabled. This requires a full repeat of the
canonical verifier; the failed attempt is not presented as a passing result.
The corrected existing `QuestLifecycleHarness` passes 42 checks in isolation and
in the final complete run. The final run also passes all existing DailyQuest,
Quest, Reward and LobbyGlide harnesses, including LobbyGlide (60), Elytra (92)
and Handoff (43). The successful full run above supersedes the initial failure.

### Isolated native Paper / LuckPerms acceptance

Executed on Paper 1.21.11 build 132 (`c5eb079`) and LuckPerms 5.5.84 using JDK 21.
**72 executable checks passed**, no assertion failure, process exit 0. The own
disposable server bound exclusively to `127.0.0.1:25660` and shut down normally;
all VapeeCore module shutdown calls returned normally. No plugin errors or
movement-sample exceptions appeared. Offline-mode and old-version notices are
expected properties of this fixture.

The actual plugin artifact, 14 registered movement handlers, borrowed context
owners, configured YAML definitions, ReloadService, QuestService, RewardService,
PlayerService and native profile files were used. Tests cover all five keys,
fractions, same-key fan-out, one-time configured rewards, both turns/wraparound,
oscillation, cancelled jump and later cancelled move, replay and 100-event burst,
teleport, velocity suppression/expiry, vehicle/flight/glide/input exclusion,
real BUILD entry/exit, real generic Activity membership, reload, same-ID reset,
reconnect, disable and re-enable without leaked handlers.

The boundary is explicit: Player/Input are synthetic, and only the producer's
online lookup and tick clock are temporarily replaced in the disposable helper.
These seams are restored before module disable. Move/jump/teleport/velocity events
are dispatched through the real Paper PluginManager; the reconnect cleanup call
uses the producer directly to avoid pretending a synthetic actor is a connected
network client. This proves API/service integration, not physical client input,
packet timing, visible UI or LobbyGlide's actual in-game gesture.

Evidence is outside the repository in
`step08a-movement-quest-progress-20261106-150523/native-02/`: logs, result JSON,
process exit and artifact hashes. The source/native artifact SHA-256 was
`4AADB739E83B2A48654FF2C6F1DC117232EF7414AF09BCCC9D3DB9F08E7E3ABA`.
`native-01` contains only an initial helper compilation failure (nonpublic test
methods / incorrect helper accessor names), no launched server or acceptance
result. The corrected helper passed without production changes.
After the test-only architecture assertion correction, Maven rebuilt the JAR
with SHA-256 `A06305DA1440F2618E9E748B202119A03A43654C6C50C212DB9DBAFC3389CAEB`.
All **562 class entries** are byte-identical to the native-tested JAR (zero changed,
added or missing classes); archive hashes differ. The native acceptance was not
rerun; it applies to the first archive with identical production bytecode.

## Limitations and real-client acceptance

This is a conservative event observer, not an anti-cheat or a full movement
simulation. Bukkit events expose accepted snapshots and client input; they do
not prove the physical cause of every displacement. Known velocity, vehicle,
flight and context boundaries are excluded, but arbitrary external plugins can
move players without faithfully exposing the cause. Conservative speed, yaw and
gap limits can undercount unusual legitimate movement. Airborne horizontal
distance and airborne turns intentionally do not count. Partial fractions/arcs
are temporary and are discarded at lifecycle/context changes or restart.

Native API tests with synthetic player boundaries are distinct from connected
Minecraft-client tests. Real-client acceptance remains open: walk 100 blocks,
sprint/sneak subsets, step/slab movement, repeated normal jumps, both turn
directions and wraparound, teleport/vehicle/elytra exclusion, LobbyGlide double
jump, BUILD/Activity transitions, reconnect, reload, completion and reward UI.

Other future progress keys remain valid definition integration points but gain
no implicit producer here. No additional quest types or features are activated.

## Changed paths

- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/quest/PlayerQuestState.java`
- `src/main/java/dev/vapee/core/quest/QuestModule.java`
- `src/main/java/dev/vapee/core/quest/QuestMovementProducer.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementTestSupport.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementIntegrationHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestMovementModuleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java`
- `docs/DEVELOPER_GUIDE.md`
- `reports/08a/STEP_08A_MOVEMENT_QUEST_PROGRESS.md`

Protected-file hashes, original worktree snapshots and native/verifier evidence
are kept outside the repository in the task's visualization evidence directory.
Delivery consists of exactly one focused local commit on the named feature
branch. Its SHA is supplied in the completion response and external delivery
receipt. No push, merge, PR, operator server interaction or deployment is part of
this delivery.

## Preservation checks

The before/after SHA-256 comparison covers 1,482 original tracked/nonignored files
across both worktrees plus the explicitly included ignored receipts. Only the
five listed existing implementation/test/guide files differ; the remaining six
paths above are new. No unrelated original file change was found. All original
local/remote refs, Recovery branches and the existing worktree set are retained.
`master` and `origin/master` remain at the starting SHA. The protected
`chat-polish-wt` retains its original HEAD and its pre-existing untracked
`reports/CHAT_PRESENTATION_POLISH.md` unchanged.

Protected SHA-256 values (verified unchanged):

| Path | SHA-256 |
| --- | --- |
| `.run/Start VapeeCore Dev Server.run.xml` | `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F` |
| `.run/Build and Deploy VapeeCore.run.xml` | `FFE3EDC5279855568BA0E906434D82A2901540D42C0976202C4F3DFCE06E8E56` |
| `reports/06b/STEP_06B_CHAT_DESIGN_SYSTEM_COMMIT.txt` | `8E4EDAB77B7792D7EB16C1CEB500A6F614FD95A3277583B707A0FCD25E8886D9` |
| `reports/06b/STEP_06B_1_PREFIX_STYLING_CORRECTION_COMMIT.txt` | `A47BA33C7BC90D77B233150F84E692713A9CB1753F1013915928719CB19EA6B0` |

No operator process was stopped or modified, and no operator data was used as a
mutable test fixture. Ignored live operator world/log/player files are outside
the file-hash comparison; no byte-identity claim is made for externally changing
server files. Native tests created only their own new world/profile/config data.
