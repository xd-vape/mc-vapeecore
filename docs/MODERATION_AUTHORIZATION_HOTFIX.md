# Phase 30A.1 — Moderation Authorization Freshness Hotfix

## Baseline and scope

Baseline: `95ac61632e505d3d51fa947ac22d7a8616613929`,
`Merge Phase 30A - Full Codebase Audit`.
Branch: `phase/30a1-moderation-authorization-hotfix`.
The branch, exact HEAD and clean working tree were verified before editing.

**MOD-001 resolved by this phase.** This is the High / Security finding
“stale offline-target authority at resume” in
[the historical audit](FULL_CODEBASE_AUDIT.md#mod-001--stale-offline-target-authority-at-resume).
That audit remains byte-identical to the baseline; its original finding is retained.
Only the shared moderation authorization handoff is fixed.

## Original race and root cause

1. An admin commands a ban of a known offline target whose LP user is not loaded.
2. `loadPrimaryGroup(targetId)` completes with `moderator` and queues a main-thread task.
3. Before that task executes, the currently loaded target group becomes `owner`.
4. The old resume checks the active enable generation, original actor session,
   current permission, actor group and hierarchy config, but passes the captured
   future target group to `StaffHierarchyService.decide`.
5. It therefore authorizes against `moderator` and saves/disconnects, despite a
   current loaded decision denying the `owner` target.

The old target authorization point was future completion; the actor/config checks
were already at resume. The captured target value crossed the scheduling boundary
as authority and could become stale during that window.

The new command regression was compiled and run **before the production edit**.
On the baseline production code it exited 1 at `ban/higher`:

```text
AssertionError: fresh hierarchy denies without success/history feedback ban/higher
received: Banned Offline for 7d.
```

This invokes the real `BanCommand` and shared async command path with the real
moderation domain/repository boundary. It is not an isolated hierarchy comparison.
The offline target comes online after completion, making an improper save,
disconnect and notification observable. The future completes on a separate worker;
the injected main-thread queue is deliberately not drained until after the promotion.

## Fix and authorization point

**Authorization Point = Main-Thread Resume.** After a successful LP load and the
existing lifecycle/session/capability checks, resume calls:

```java
context.hierarchy().decideLoaded(actor.playerId().orElseThrow(), target.uniqueId())
```

The existing read-only service obtains one current immutable
`StaffHierarchyConfig` snapshot and the current loaded actor and target primary
groups for that decision. Both levels use the same config snapshot. A tiny local
decision application helper keeps the existing allow/deny/error switch shared
with the unchanged synchronous loaded-target path.

The future is only a **load barrier** and exceptional-load signal. Its successful
`Optional<String>` value is named `ignoredGroup` in the callback and is no longer
passed to resume. A promotion to equal/higher protection now denies; a demotion
may allow under the current policy. This does not combine old/new levels or keep
a last-known group.

If the current loaded target group is absent, blank or invalid, the existing
`UNAVAILABLE` decision fails closed, even when the future previously returned a
valid group. There is no stale-result fallback, retry, polling or guessed default.
Formal successful null completion with unavailable current authority is also
covered. Exceptional completion remains a contextual controlled failure on main.

Same/higher denial keeps “You cannot target a staff member at your level or above.”
Unprotected-actor denial keeps “You cannot target a protected staff member.”
Unavailable authority logs SEVERE with action, actor and target UUID and gives
only the existing controlled operation-failure message, without ranks/exceptions.
Denied authorization produces no moderation success audit.

## Threading and preserved security contracts

The provider callback checks only harmless per-enable activity, queues the resume
and logs a scheduling failure if submission fails. It performs no Player lookup,
CommandSender/Bukkit access, MessageService feedback or ModerationService work.
All session/permission/group checks, decisions, mutation, save, feedback and
disconnect remain inside the main-thread continuation. No future `join()` or
`get()`, sleep, polling, secondary staff cache or LP writes were introduced.

Resume still requires the exact original online Player object. A reconnect with
the same UUID does not inherit a pending command. The per-enable active-generation
guard still discards commands across disable/re-enable. The current action-specific
permission is checked again; actor primary group and config remain fresh.
OP is never evaluated as a hierarchy bypass. Only the primary group and configured
protected-group ordering determine staff authority; tracks, weights, inheritance
maximum, prefixes, suffixes and colors are unchanged.

Console continues to require the command capability and bypasses only hierarchy,
without an LP target load. Self mutations remain denied for warn, mute, unmute,
ban, unban and kick. Permission-gated self history remains allowed without an LP
load. Foreign history continues through hierarchy, preventing disclosure after a
target promotion.

The order remains permission → actor → arguments → known identity → self rule →
hierarchy → domain mutation → persistence → audit/feedback/external action.
Existing save-before-disconnect and save-before-mute-publication/notification
tests remain green. No moderation domain, persistence schema, sanction migration,
mute projection, login enforcement, chat/PM policy or reload redesign is included.

## Commands and regression coverage

Source inspection confirmed that `WarnCommand`, `MuteCommand`, `UnmuteCommand`,
`BanCommand`, `UnbanCommand`, `KickCommand` and `HistoryCommand` all inherit the
same `AbstractModerationCommand` boundary. All seven are exercised through their
real executors, including active-ban/active-mute fixtures and seeded private history.

The new `hierarchyFreshness` matrix runs these 15 cases for each of the seven:

| Change after completion, before resume | Expected outcome |
|---|---|
| Target future moderator → current owner; actor admin | Same/higher denial |
| Target future builder → current moderator; actor moderator | Same/higher denial |
| Target future owner → current moderator; actor admin | Allow under current policy |
| Target current group unloaded | UNAVAILABLE, controlled failure |
| Target current group blank | UNAVAILABLE, controlled failure |
| Target current group contains a line break | UNAVAILABLE, controlled failure |
| Successful null completion, current group unavailable | UNAVAILABLE, controlled failure |
| Actor capability revoked | Permission denial |
| Original actor logged out | Silent discard |
| Actor reconnects with same UUID and different Player object | Silent discard, replacement untouched |
| Actor group admin → builder | Same/higher denial |
| Actor group builder → admin | Allow under current policy |
| Actor current group unavailable against protected target | UNAVAILABLE, controlled failure |
| Config reordered to put moderator above admin | Same/higher denial using current config |
| Original enable generation becomes inactive | Silent discard |

For every denied/failed/discarded case, probes require unchanged repository save
count and snapshot identity, all records/history, active ban, active mute and mute
projection, zero target kicks/output, zero success audit and zero player writes.
Actor feedback must be precisely the denial/failure message or empty for discard;
seeded history cannot leak. Allowed ban/kick cases require a real disconnect.
Worker-thread guards prohibit Bukkit access and repository writes; source
inspection also confirms no domain/feedback work in the callback. Completion must
queue exactly one task with no side effects, and resume must leave no queued task
or extra load.

Existing pre-completion permission/session/actor/config/disable and load-failure
tests are retained, as are console/self matrices and the static hierarchy matrix.
The successful-load fixtures now explicitly populate the simulated loaded LP
state before draining the queue, matching the state queried by the new boundary.
The fixture's deferred-future result no longer substitutes for that loaded state.

## Measured validation

Baseline: 101 executable harnesses, 14,705 checks, 0 failures.
Final: **101 executable harnesses, 15,668 checks, 0 failures, 0 skipped executable
harnesses**. Every harness with an active `public static void main` was launched
in its own Java process; Maven package was not used as a substitute. The command
harness increased from 3,381 to **4,344 checks** (+963).

The focused run also launched all nine moderation harnesses separately, plus
hierarchy, provider async and the unchanged online administrative guard:

| Harness | Checks | Failures |
|---|---:|---:|
| ModerationCommandHarness | 4,344 | 0 |
| ModerationDurationHarness | 53 | 0 |
| ModerationBanEnforcementHarness | 31 | 0 |
| ModerationDomainHarness | 112 | 0 |
| ModerationLifecycleHarness | 149 | 0 |
| ModerationMuteEnforcementHarness | 52 | 0 |
| ModerationMuteProjectionHarness | 20 | 0 |
| ModerationPersistenceHarness | 523 | 0 |
| ModerationServiceHarness | 141 | 0 |
| LuckPermsAsyncHarness | 16 | 0 |
| StaffHierarchyServiceHarness | 109 | 0 |
| OnlineStaffTargetGuardHarness | 268 | 0 |
| **Focused total** | **5,818** | **0** |

This preserves the Phase 26A primary-group decision matrix, Phase 26B loaded
online utility/economy guard, moderation persistence/enforcement/lifecycle and
all unrelated project harnesses. Local ignored evidence resides under
`dev-server/.vapeecore-dev/phase30a1-*` (red/green logs, per-process logs and result
JSON); no new runner or build-system change is committed.

## Final build and artifact

Executed from the repository with the existing offline dependency cache:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

Result: **BUILD SUCCESS**, 23.556 seconds, 2026-10-04 17:00:15 Europe/Berlin.
Java: Oracle JDK 21.0.12.1, runtime `21.0.12.1+1-LTS-4`.
Existing compiler annotation-processing/deprecated-API notices were informational;
no build configuration or dependencies were changed.

| Artifact | Value |
|---|---|
| Final JAR | `target/vapeecore-1.0-SNAPSHOT.jar` |
| Byte size | **1,050,325** |
| SHA-256 | `8141DD62E53A52475E304C318BBF1383B6D2FAFE9F1C009EC83B5D88872BA764` |
| Local smoke copy | `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar` |
| Local smoke deploy SHA-256 | `8141DD62E53A52475E304C318BBF1383B6D2FAFE9F1C009EC83B5D88872BA764` |

The local smoke copy hash equals the final artifact hash. LuckPerms and Bukkit
classes are not bundled (0 `net/luckperms/` entries, 0 `org/bukkit/` entries).
There was no production deployment or Git push.

## Paper smoke and shutdown

The final artifact was started in the existing local dev server on Windows 11:

- Paper **1.21.11-132**, `c5eb079`, API `1.21.11-R0.1-SNAPSHOT`.
- Oracle Java **21.0.12.1+1-LTS-4**.
- LuckPerms **5.5.84**, loaded and connected.
- **27 modules** enabled; `/core` reports 27 active modules.
- **37 root commands** returned by `bukkit:help VapeeCore`; their exact names
  match the baseline descriptor. `/core help` includes all seven moderation commands.
- Descriptor remains **50 permission nodes** and **15 positive child edges**.
- `/core reload` prepared **6 participants** (config, lobby, chat, private-messages,
  presentation, daily-quests) and completed successfully in **15 ms**.
- **0 ERROR/SEVERE log entries** across startup, commands, reload and shutdown.
- All **27 modules** disabled in reverse order; LP storage closed; process exit **0**.

Safe console probes: `version`, `lp info`, `list`, `core`, `core version`,
`core help`, `core reload`, `bukkit:help VapeeCore`, then `stop`.
There were 0 online clients. No punishment or LP group mutation was performed.
All eight existing live VapeeCore YAML files retained their SHA-256; no
`moderation.yml` was created. The server is stopped.

Paper's console emitted its existing newer-Minecraft-release reminder, and spark
selected its Java profiler on Windows; neither was a VapeeCore error. The real
promotion race was exercised in the command harness, not a live staff session.

**Live client test: not performed.**

## Diff scope, unchanged contracts and Git

Exact changed-file set:

1. `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`
   — the only production edit; fresh loaded authority at async resume.
2. `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
   — real queued-race matrix and successful-load fixture state.
3. `docs/DEVELOPER_GUIDE.md`
   — only the two current async-authorization paragraphs.
4. `docs/MODERATION_AUTHORIZATION_HOTFIX.md`
   — this report.

SHA-256 verification against all **452 baseline tracked files** found exactly
those three existing files changed; the other **449** remained byte-identical.
The report is the only added file. In particular:

- `plugin.yml`, `config.yml`, all other YAML defaults, `pom.xml`, README and the
  historical full audit are unchanged.
- ModerationRecord/Snapshot/Action/Actor/Service, repository/schema 1, mute
  projection, login/chat/PM enforcement and existing sanctions are unchanged.
- StaffHierarchyService/Config, OnlineStaffTargetGuard, module graph and reload
  coordinator are unchanged. No second cache, provider write, generic auth
  framework or unrelated refactor was added.
- Utility, Economy, GUI, Blackjack and persistence code are unchanged.

Selected unchanged file SHA-256 values:

| File | SHA-256 |
|---|---|
| `src/main/resources/plugin.yml` | `1E4A9591559E669CD5753812885B0604A03CE070F48BD1DE39AD95B505D9DF5C` |
| `src/main/resources/config.yml` | `37ECEFAC7B0EFCFB006718D4A350066138733809DD53C4B2FD2DA5E585566DC4` |
| `docs/FULL_CODEBASE_AUDIT.md` | `73F7FBF38E4DD01F5A3328275A8045C9FB3812D4B4BF6BA4D817458F1FFEA2CC` |

Delivery is exactly one local commit on the specified branch:
`Phase 30A.1 - Moderation Authorization Freshness Hotfix`.
No push, rebase, history rewrite or foreign merge. The full four-file diff is
reviewed and `git diff --check`, status and stat are checked before committing.
The completion report records the final commit SHA, clean working tree and the
verified one-commit distance from the exact baseline; the commit cannot embed
its own SHA in this report.

## Remaining findings and Phase 30B readiness

No new hotfix regression was observed in this validation. The live-client
limitation above remains. This fix does not claim to resolve any other audit
finding. These **19 baseline findings** remain for their assigned later work:

`SEC-001`, `DISPLAY-001`, `PERSIST-001`, `PERSIST-002`, `GUI-001`, `GUI-002`,
`LIFE-001`, `CONFIG-001`, `CMD-001`, `DUP-001`, `API-001`, `PRES-001`, `PRES-002`,
`LIFE-002`, `PERF-001`, `TEST-001`, `TEST-002`, `DOC-001`, `LEGACY-001`.

The historical audit still records all 20 baseline findings. The narrow guide
correction here does not repair its broader DOC-001 overview/dependency/test-list
issues. SEC-001 GUI capability checks, DISPLAY-001 and persistence/GUI fixes have
not been implemented. Team teleport consent remains Phase 30G.

**Phase 30B readiness:** the isolated MOD-001 hotfix is validated and ready as a
baseline for separately authorized Phase 30B work. API-001's Blackjack read
boundary remains unchanged. No Phase 30B or Phase 32 implementation was started;
this phase stops after its local commit and completion report.
