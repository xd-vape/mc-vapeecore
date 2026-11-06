# Step 08B — Daily Quest Progress HUD

## Scope and baseline

Feature branch `codex/step08b-quest-progress-hud`, based on verified clean master
`5dde7456d6f8e6efba66ffd61f6645044db6102c` (also origin/master).
Before snapshots cover 1,490 files in both existing worktrees plus the explicitly
included ignored receipts. The protected worktree's existing untracked
`reports/CHAT_PRESENTATION_POLISH.md` is retained.

No quest definitions, gameplay producers, selection changes, player schema,
reward implementation, command, permission, GUI layout or new module is added.
Daily defaults remain disabled and catalog-free. Movement measurement and the
playtime producer are unchanged. No real operator data is a test fixture.

## Display behavior

Actual numeric progress queues a localized actionbar and a temporary client XP
fraction. A single shared synchronous task runs every five ticks, coalescing
updates per UUID. There are no per-player tasks, raw-move packet sends, idle
refreshes, file/database polling or second quest-state cache. Each display expires
60 server ticks after its last relevant progress (approximately three seconds at
normal TPS). XP returns to real values; actionbar text fades naturally without
an unsafe blank clear. Under server lag, wall-clock timing follows server ticks.

Examples:

- EN: `Spin 200 times · 37/200 · 18.5%`
- DE: `Drehwurm 200 · 37/200 · 18,5 %`

Names come from the current definition and existing language fallback, as literal
Components. IDs/keys are never displayed. Long numerator/target text is exact;
BigDecimal computes bounded 0–100 percent rounded to one decimal. XP float values
are bounded 0–1 and can have less precision than the exact text at very large
targets. ACTIVE uses the configured accent, COMPLETED uses central SUCCESS and
REWARD_PENDING uses WARNING. A 100-percent pending task does not claim payout.
Existing completion/reward chat remains unchanged; ordinary progress adds no chat.
New bundled language keys use existing operator override/fallback behavior;
operator language/config files are not rewritten or evolved.

## Central observation and deterministic priority

`QuestService.addProgress` notifies an internal QuestProgressListener only after
successful processing and only for ACTIVE IDs whose numeric progress increased.
It passes the original assignment generation, catalog identity, changed IDs and
result. The domain stays independent of UI/Adventure. Callback and diagnostic
failures are isolated after domain processing. Direct service clients, the
existing reporter and all current/future supported keys therefore share the HUD.

No signal is generated for unknown/unloaded players, unmatched keys, rejected
amounts, unchanged completed quests, pending retry or pending normalization alone.
The HUD re-reads authoritative QuestViews and validates generations before frames.
There is no hardcoded quest, target, key allowlist or duplicated reward logic.
The existing main-thread-owned Quest API contract applies to these observers;
no asynchronous domain or UI mutation is introduced.

For multiple changed tasks, newly completed tasks preempt the old display; equal
completion candidates use ID order. Otherwise a still-changing displayed ID is
preferred, with ID order as final tie-breaker. A completion holds priority for ten
ticks; unrelated progress cannot renew that hold indefinitely. New actual
progress after the hold can choose a different task. No backlog of all quest
states is stored, so coalescing/priority may omit intermediate presentations.

## XP ownership and restoration

The only XP write API is `Player.sendExperienceChange(fraction, currentLevel)`.
The plugin never calls setExp/setLevel/giveExp/giveExpLevels or persists XP.
Ownership is recorded before sending to account for a possible after-effect
exception. Restoration always re-reads current real getExp/getLevel; no old
server-XP snapshot is restored. A disconnected/replaced session receives no old
packet. Known XP/level events retire the overlay and restore after event handling
on the next HUD task with fresh values.

Failures retain exact unconfirmed XP ownership; normal cleanup retries are
throttled to once per 20 ticks, and diagnostics are emitted once per display.
Module cleanup independently cancels tasks, unregisters handlers and detaches all
observers even if XP release fails. An unresolved release remains in existing
pending cleanup and prevents re-enable until explicit disable retry succeeds.

Paper API reference:
[Player.sendExperienceChange](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html#sendExperienceChange(float,int)).
The supported runtime's CraftPlayer bytecode was inspected: it validates the
fraction/level and sends ClientboundSetExperiencePacket with real total XP;
it does not mutate the player's experience fields.

## Actionbar and external-plugin boundaries

MessageService has one small foreground observer boundary, without a UUID cache
or scheduler. Both Navigator actionbar sites now use `sendActionBar`, preserving
their content, localization and color. A foreground core message releases owned
XP and pauses HUD output for 60 ticks; suppressed progress does not revive an old
quest after that window. A new relevant progress signal is needed.

The HUD's own low-priority Player output intentionally does not notify its own
foreground observer. Cleanup never sends an empty Component and cannot erase a
later Navigator/foreign actionbar. Natural fade can leave text briefly visible
after a module disable or assignment/context invalidation even though XP has
already been restored; this does not send a stale quest frame again.

Neither Bukkit nor Paper exposes a trustworthy getter for the client's last
actionbar/XP packet or its plugin owner. Unknown direct/ProtocolLib/plugin XP
spoofs cannot be fully arbitrated. They may supersede the quest display, or an
owned restoration of real XP may replace an unknown foreign spoof. There is no
claim of full external arbitration, no packet-library dependency and no general
UI framework. Operators using exclusive HUD/XP plugins need explicit cooperation.

## Lifecycle and context

QuestModule binds the HUD after the unchanged 27-module startup order through
`startHud`, alongside the existing post-enable Movement binding. The original
100-tick playtime/flush task and seven reload participants remain intact. The HUD
owns one task, nine Bukkit handlers, a progress subscription, a foreground
subscription and the two existing context subscriptions. Acquisition failures
release partial resources using original identities; stale runtime authority is
revoked before cleanup.

Each transient display stores UUID, entity/session ID, selected quest ID,
assignment/catalog identities, deadlines and output/cleanup flags. It retains
no Player or World reference. Eligible contexts are current online/loaded, alive,
NORMAL and no Activity, with Survival/Adventure XP presentation. These are UI
gates; no producer movement rule is changed. Quit/rejoin, world/teleport,
death/respawn, mode and context transitions invalidate the display. Assignment
reset, Force and rotation change the existing runtime assignment token; reload
and rollback change catalog identity. Invalid queued frames are discarded.

## Verification

The complete canonical command `pwsh -NoProfile -File .\scripts\verify.ps1`
passed in this implementation run:

| Check | Actual result |
| --- | --- |
| Maven clean package | PASS |
| Spotless | PASS (existing repository pilot allowlist) |
| Discovered / executed / passed harnesses | 239 / 239 / 239 |
| Checks | 35,420 |
| Failed / missing-invalid | 0 / 0 |
| Original harnesses | All 237 retained, two executable HUD harnesses added |
| Final documentation link check | 122 checks PASS |

Focused real-domain tests pass: QuestProgressHudHarness 67 checks,
QuestHudModuleHarness 39 checks. Existing direct regressions also pass:
QuestLifecycle 42, QuestModuleLifecycle 271, QuestMovement 64,
QuestMovementModule 24 and QuestProgressReporter 18. The final canonical receipt
and full log are archived outside the repository.

The tests cover locale/long/fraction formatting and colors; central API and future
keys; 100-signal coalescing; expiry/current-XP restoration and XP events; stable
multi-quest/completion priority; pending overflow/retry without new progress;
completion chat and no duplicated reward; Force/rotation/catalog replacement and
rollback; loaded-profile/session/owner/context gates; send failures before/after
effect; retained cleanup ownership; actual registered module Movement/Playtime
producers, task handles, before/after registration failures, scheduler refusal,
observer/handler/task release failures and explicit cleanup retry/re-enable.

### Native Paper / LuckPerms acceptance

**49 native checks passed**, process exit 0, on the own disposable Paper 1.21.11
build 132 (`c5eb079`) / LuckPerms 5.5.84 / JDK 21 server, bound only to
`127.0.0.1:25662`. All VapeeCore module shutdown calls returned normally.

The XP boundary uses a real CraftPlayer backed by an unregistered test
ServerPlayer and a capturing Connection. Real sendExperienceChange packets are
inspected for fraction, current level and real total XP; real ServerPlayer XP
fields remain unchanged. Restoration packets use newly changed real values.
Invalid fraction negative controls are rejected by Paper. The real Actionbar API
also produces its native actionbar packet. NMS exists only in this external test
helper; production uses the supported Player API with no new dependency.

Separately, the actual registered production HUD and Quest/Daily/Reward/Economy
services are exercised with a synthetic Player plus controlled current-online
lookup and tick-clock seams. This verifies localized 37/200 output, 100-signal
throttling, current-XP restoration, foreground priority/pause, real overflow
pending retry without new progress, no duplicated coins, full console reload,
actual DailyQuestModule rollback in a test coordinator, Force, daily rotation,
real BUILD and Activity callbacks, reconnect, before/after-effect XP output
failure handling, pending cleanup, explicit retry and fresh HUD re-enable.
No XP mutation API or blank actionbar clear occurs.

The capture transport is not a connected Minecraft client. Synthetic HUD Player
calls also do not prove network timing or rendered UX. Known expected logs are
the deliberately failing later rollback probe and injected XP after-effect
failure, plus normal offline-mode/old-version fixture notices. No unexpected
plugin error or incomplete shutdown appeared. The helper's first argument-file
preflight had a quoting error; compilation was corrected before any test server
was launched. The first native runtime run passed without production changes.

Artifact SHA-256 (tested copy equals the final build):
`794F9F663AD7A0901AD6712284CA71E8A168127A1B65526F7D458F6C7760701D`.
External evidence: `step08b-quest-progress-hud-20261106-230308/`, including
`canonical-final.log`, `canonical-final-results.json`, API bytecode inspection,
helper source/compiler arguments, and `native-01/` logs/results/process/hash
receipts. The NMS helper uses the
[documented Mojang-mapping manifest marker](https://docs.papermc.io/paper/dev/internals/).

## Reproducible real-client acceptance (still open)

1. Use a separate disposable Paper + LuckPerms server; keep operator data separate.
2. Configure a valid localized movement quest in that fixture's daily-quests.yml,
   enable it there and use the existing authorized Force command to assign it.
3. Set up a known real XP level/progress using test-server tools; walk valid blocks
   and verify localized actionbar, current values, percentage and quest XP bar.
4. Stop progressing; verify real current XP returns after roughly three seconds.
5. Gain real XP while the overlay is visible and verify the latest values survive.
6. Trigger Navigator back-hint/tool feedback; confirm it takes priority and new
   quest progress cannot overwrite it during the pause window.
7. Complete a quest and check 100 percent, completion chat and exactly one reward.
8. Use two assigned definitions with the same key and different targets; verify
   stable priority and an interrupting completion without rapid flicker.
9. Exercise Force, reset, valid/invalid reload and rollback; observe no stale task.
10. Quit/rejoin, enter/leave BUILD or Activity and restart the fixture; verify no
    inherited display, altered XP or lost quest/reward data.

Automated/synthetic native checks are not connected-client or visual acceptance.
No actual Minecraft-client validation is claimed.

## Changed paths

- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/quest/QuestModule.java`
- `src/main/java/dev/vapee/core/quest/QuestService.java`
- `src/main/java/dev/vapee/core/quest/QuestProgressListener.java`
- `src/main/java/dev/vapee/core/quest/QuestProgressHud.java`
- `src/main/java/dev/vapee/core/message/MessageService.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/resources/lang/en.yml`
- `src/main/resources/lang/de.yml`
- `src/test/java/dev/vapee/core/quest/QuestHudTestSupport.java`
- `src/test/java/dev/vapee/core/quest/QuestProgressHudHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestHudModuleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java`
- `docs/DEVELOPER_GUIDE.md`
- `docs/development/chat-design-system.md`
- `reports/08b/STEP_08B_QUEST_PROGRESS_HUD.md`

Delivery is exactly one local feature commit after verification. Protected Run
files, receipts, Recovery refs, existing worktrees and operator data are retained.
No push, merge, PR or deployment is part of this task. Evidence is kept outside
the repository under the task's visualization directory.

The before/after SHA-256 comparison covers all 1,490 original tracked/nonignored
files across both worktrees and the explicitly included ignored receipts. Only
the eleven intended existing paths above differ; six listed paths are new.
There are no unrelated file changes. All pre-existing refs and worktrees are
preserved; master and origin/master remain at the starting commit. The protected
worktree retains its existing HEAD/branch and untracked report unchanged.

Protected hashes verified unchanged:

| Path | SHA-256 |
| --- | --- |
| `.run/Start VapeeCore Dev Server.run.xml` | `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F` |
| `.run/Build and Deploy VapeeCore.run.xml` | `FFE3EDC5279855568BA0E906434D82A2901540D42C0976202C4F3DFCE06E8E56` |
| `reports/06b/STEP_06B_CHAT_DESIGN_SYSTEM_COMMIT.txt` | `8E4EDAB77B7792D7EB16C1CEB500A6F614FD95A3277583B707A0FCD25E8886D9` |
| `reports/06b/STEP_06B_1_PREFIX_STYLING_CORRECTION_COMMIT.txt` | `A47BA33C7BC90D77B233150F84E692713A9CB1753F1013915928719CB19EA6B0` |

No operator process was stopped or modified. Ignored live operator logs/world/
player files are outside the hash snapshot; no byte-identity claim is made for
externally changing files. All native test data belongs to the disposable fixture.
