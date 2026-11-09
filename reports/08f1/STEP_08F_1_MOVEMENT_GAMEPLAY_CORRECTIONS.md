# Step 08F.1 — Movement Quest Gameplay Corrections

## Scope and starting point

Base master: 9e2cf0041fda66ee1a0bbe08d1cf2f93dc8a05ee.
Local feature branch: codex/step08f1-movement-gameplay-corrections.
Only sprint-jump WALK/SPRINT distance and observed fast rotation are corrected.
No new keys, module, setting, dependency, scheduler or persistence field.

## Cause and correction

Previously every airborne Move returned before measuring distance, including the
air phase of a genuine Paper jump. Jump intent was also collected only when a
JUMP quest was assigned. The existing Sample now holds a bounded air phase after
a matching, accepted Jump/Move pair. Jump must observe grounded state, actual
Jump input and positive height change. Native Paper updates onGround between
Jump and Move; Move need not still report grounded. WALK and SPRINT work even
without a JUMP assignment. Normal eligibility, input, endpoint continuity,
per-segment/tick distance budgets and five-tick jump gate still apply.

The phase expires after 20 ticks, outside startY-1 through startY+1.5, upon an
upward segment after descent, landing or any existing invalidation/Velocity
guard. Unproven falls, flight/glide/vehicles and unrelated later air motion gain
no authority. SNEAK/BACKWARD/ROTATION retain ground requirements. The producer
never modifies movement. Special jump bonuses and large drops can remain
uncounted; this is bounded observation, not an anti-cheat physics simulation.

Previously more than 90 degrees per event or tick erased the rotation remainder.
The same signed net accumulator now accepts shortest observed segments below
180 degrees and an absolute 360-degree tick budget. Exactly 180 degrees erases
the remainder because its direction is ambiguous. Raw deltas beyond 180 degrees
represent only the shortest arc. Identical normalized orientation, replay,
opposite arcs, cancellation, teleport and invalid geometry cannot invent spins.
Exceeded angular budgets discard the remainder without replenishing spent
budget. At most one rotation can be granted per tick.

QuestService remains the progress authority. Rewards, pending retry, numeric HUD,
seven keys, Velocity guards, sneak cooldown/readiness and shared confirmation
task are unchanged. The Step-08E.5 re-enable limitation remains: already online
players need a fresh genuine Loaded notification before SNEAK_TOGGLE can count.
No seamless re-enable support is claimed.

## Verification

Initial unchanged-production Red: 61 new gameplay scenario groups, 31 failures,
exit 1. Corrected production Green: all 61 groups pass; existing 290 Velocity,
621 Lifecycle, 71 Sneak-State, 67 Confirmation and 24 Readiness groups pass.
The final formatted test binaries were then run unchanged against the pure
original producer compiled outside the repository, and against the correction:
61 groups / 31 failures / exit 1 versus 61/61 / 107 checks / exit 0.
The original source's Git blob matches the baseline exactly:
3b1a640e42c0c39b509b8f6861854b6add13bb02. No original-source adapter or production
replacement was used. The same complete Movement harness passes 4,494 checks.

Final test SHA-256:
4559C53CA0CADD8198ABC6882CA0FD90DB30A2B617E745EDA07D01C649E88E2A;
support: 38E76F086BD230D7425EF852C5468C00A7BFFDE5E02B86F82B17F07732D87EBA.

New tests use real Paper event classes, Player fixtures and existing real
Quest/Reward services. Coverage includes jump-only distance assignments, all
seven keys, wrapped ticks, cancelled/mismatched/expired jump pairs, input,
grounding, height/time/descent bounds, replay, distance floods, session and
assignment/catalog changes, context/lifecycle invalidation, both rotation
directions, tick floods, ambiguous deltas, coins, overflow/pending and numeric HUD.

Historical Step-08E.5 proof was 245 harnesses / 40,761 checks and 525 native
checks; those numbers are historical, not new results for this correction.

### Fresh complete canonical verification

Executed pwsh -NoProfile -File .\scripts\verify.ps1 in full, exit 0:
245 discovered / 245 executed / 245 passed, 40,868 checks,
Failed=0, Missing/Invalid=0. Maven clean package PASS and Spotless PASS.
All 245 original harness classes remain present. Movement changes 4,387 to
4,494 checks (+107); every other harness check count is unchanged. Existing
movement/module, Quest/Reward/Pending/Distinct, HUD, persistence, Daily and
lifecycle regressions pass. No Java source changed after final Red/Green and
the successful canonical build. Only the three intended Java files were
formatted; Spotless's existing pilot scope was preserved.

### Fresh isolated native acceptance

Native-01 passes 46 checks with zero failures on Paper 1.21.11 build 132,
LuckPerms 5.5.84 and JDK 21, bound only to loopback port 25679. It uses a real
CraftPlayer/ServerPlayer and actual native Input/Move packet handlers, which
generate real Paper Jump/Move events. The positive jump arc is accepted natively
and completes WALK/SPRINT with once-only coins, with and without a JUMP
assignment. Jump observes grounded state and Jump input; subsequent Move observes
airborne state. Unproven air motion, missing Jump input, cancelled Jump and
Velocity invalidation stay excluded. Both rotation directions, same/separate
observation ticks, shortest-arc handling and exact-half-turn rejection pass.
All seven keys, actual localized HUD packets and untouched server XP are checked.

Online player registration and producer/HUD observation clocks are controlled
fixture seams. The packet sequence executes synchronously in one native probe
callback; separate observation ticks are not claimed to be elapsed wall-clock
server ticks. No Minecraft network client connected. NMS is confined to the
external probe, never production. The own disposable PID 33168 exited normally,
code 0, after shutdown of its isolated worlds only.

Native and canonical build JARs match byte-for-byte, SHA-256
C7C347DA2C109FF4D988A02463CFE194A22D273B1C4FF3B425DCBD023220A325.
All probe sources, logs, Red/Green and preservation receipts are retained outside
the repository under
C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08f1-movement-gameplay-20261109-011839/.

## Safety and remaining client acceptance

Existing branches/worktrees, Run configurations and receipts are protected by
before/after hashes and Git snapshots. Ignored live
operator worlds/logs/player files may change externally; byte identity for those
live files is not claimed. Tests use disposable external data only.

The historical operator-plugin JAR comparison differs from its present bytes:
old recorded SHA-256 428882C13CFE60DAD93D8111931FF4C7263C224EEFDC1D519C43B0AD84F46186,
observed SHA-256 156FC26173999937B784D3666805BEA744CA2A8C8A264384D7800EC2B0000BCF.
Its write timestamp is 2026-11-08 23:31:44, before this work. The user explicitly
confirmed earlier local Daily-Quest testing/build updates and instructed that
this be treated as pre-existing. Read-only ZIP inspection matches its Producer
class to the previous 08E.5 native artifact and confirms that it contains no new
AirJump class. It is not the new canonical/native test artifact. This does not
claim provenance for every ZIP byte. The original historical baseline is retained
unaltered; a separate observation records this discrepancy. The operator JAR is
never written, replaced or used as a mutable fixture and is rechecked against
that observation at completion. Other deployment artifact comparisons remain
strict. No task-start byte-identity claim is made for this pre-existing JAR.

Before commit, all 1,511 original files across both existing worktrees were
compared: only the four intended existing paths differ, plus this new report;
zero unrelated tracked/untracked file changes. Both Run configurations and both
protected receipts retain their raw SHA-256 values. Existing branch refs,
master/origin-master, foreign worktree and its untracked report, tags and remote
configuration are unchanged. Post-commit checks are recorded externally and
require exactly one feature commit with the same five-path scope and a clean
main worktree.

No push, merge, PR, deployment or operator-server intervention is authorized.
Real Minecraft-client confirmation of ordinary sprint-jump counting, fast mouse
rotation, visible HUD and unchanged movement feel remains open. Native API or
packet tests do not replace that visual/client acceptance.

## Commit paths

- src/main/java/dev/vapee/core/quest/QuestMovementProducer.java
- src/test/java/dev/vapee/core/quest/QuestMovementHarness.java
- src/test/java/dev/vapee/core/quest/QuestMovementTestSupport.java
- docs/DEVELOPER_GUIDE.md
- reports/08f1/STEP_08F_1_MOVEMENT_GAMEPLAY_CORRECTIONS.md
