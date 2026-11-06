# Step 08C — Community Quest Progress Producers

Date: 2026-11-07. Base: `2e3081a60f34aa43391b2b24e47a21c507212b0a`.
Local feature branch: `codex/step08c-community-quest-progress`.

## Scope and architecture

Community actions now enter the existing `QuestProgressReporter` → `QuestService`
path. No producer selects a quest ID, target, reward amount or name. Assignments,
numeric progress, reward attempts and persistence remain with the existing
services. The generic Step-08B observer automatically drives the existing HUD.
There is no second actionbar/XP display, quest registry, persistent counter or
skin/chat/history cache. There are still 27 modules and seven reload participants.

The new post-enable binding follows Movement/HUD in `VapeeCore`: QuestModule
borrows the already enabled Chat, Moderation, PM, Friend and Utility owners.
Those domain services do not import QuestService or any quest package.

## Supported keys and success boundaries

| Technical key | Credited player and condition |
| --- | --- |
| `social:chat_hello` | Public author: accepted recognizable word greeting; one increment per message, not per matching word. |
| `social:chat_thanks` | Public author: accepted recognizable word thanks; same observation rules. |
| `social:chat_unique` | Public author: accepted distinct meaningful message meeting the thresholds below. |
| `social:pm_sent` | Sender, after both outgoing/incoming delivery calls and both conversation links succeed; successful replies also count. |
| `social:pm_reply` | Sender, only when that successful delivery originated in `reply`. |
| `social:friend_request` | Sender, after a newly created outgoing request has been saved successfully. |
| `social:friend_accepted` | Both currently online/loaded participants, after the new friendship is saved; includes automatic counter-request acceptance. |
| `social:tpa_visit` | Request sender, after accepted regular `/tpa` and successful existing UtilityService teleport. |
| `social:tpa_host` | Explicit consenting request recipient, after the associated teleport succeeds. |

For `/tpahere`, the stored `here` flag moves the recipient to the sender. It does
**not** emit regular `tpa_visit`. The explicitly consenting recipient receives
`tpa_host`; this key denotes consent here, not the physical destination player.
The destination/request sender is not credited as a consenting recipient.

PM errors/offline/settings/Ignore/mute/self/blank inputs or failed delivery emit
no success. Reply uses one private send implementation with an origin flag, so
there is no nested duplicate `pm_sent`. Friend duplicates, existing friendships,
Deny/Cancel/Remove and failed saves do not emit positive operation signals.
Counter-requests emit friendship success rather than request creation. TPA send,
deny, expiry, cancellation, invalid/stale nonce, second accept, unsafe targets,
wrong worlds/sessions and a false/throwing/cancelled teleport do not count.
Existing consent, cooldowns, permissions and safe-destination rules are unchanged.

Every signal checks the current owner generation, real current online session,
loaded profile and an ACTIVE matching assigned definition below its target.
No matching assignment means no report. Completed quests cannot earn more;
multiple definitions of one key receive the one authoritative signal with their
own targets. Producers do not directly retry pending rewards or grant coins.
Existing QuestService reward/persistence semantics remain unchanged.

## Public chat classification and anti-abuse

`CommunityChatText` is a pure bounded classifier; it returns only a SHA-256 digest
and three booleans. Neither queued work nor per-player maps keep complete chat
messages. Input is capped at 256 UTF-16 code units before normalization.

* Normalize NFKC, lowercase with `Locale.ROOT`, and collapse Unicode whitespace.
* Greeting words: `hallo`, `hi`, `hey`, `hello`.
* Thanks phrases: `danke`, `thanks`, `thank you`, `vielen dank`.
* Boundaries include Unicode letters/marks/numbers and underscores: embedded
  `hi`, including inside longer words or decorated with combining marks, is not
  a greeting. Full-width forms normalize normally.
* Reject blank/command-like input, remaining control/format characters, fewer
  than two letters, more than three identical consecutive non-space characters,
  a complete repeated pattern of length 1–3 repeated at least three times, or
  three or more identical whitespace-separated words.
* Unique additionally requires at least eight codepoints, four alphanumeric
  characters and three distinct alphanumeric characters.
* A common **three-second monotonic cooldown** applies between credited public
  messages across all three chat keys. A rejected duplicate does not move it.
* Each player retains at most **32 SHA-256 fingerprints**, for **up to 120
  seconds**. Digests also collapse punctuation-only variants. Capacity eviction
  can shorten the effective duplicate window.

This is a conservative bounded heuristic, not a semantic language detector or
complete anti-farming system. Logout/restart discards the duplicate history;
rejoining can make repeated messages eligible again. Assignment/Force or catalog
changes intentionally start a new quest-related measurement epoch. No durable
chat history or account-wide exploit-prevention claim is made.

## Async/main-thread and lifecycle ownership

Paper documents that [AsyncChatEvent may be async or synchronous](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/AsyncChatEvent.html)
and recommends the [scheduler for main-thread Bukkit work](https://docs.papermc.io/paper/dev/chat-events/).
The new MONITOR/ignoreCancelled observer always hands off: it captures only UUID,
immutable entity/session/config/catalog identities, classification flags and a
digest. It reads the existing immutable Mute projection; it never queries the
Player repository, assignments, rewards or mutable Bukkit state asynchronously.
Entity ID and UUID are used only as immutable session identity, not authorization.
As usual, cancellation observation assumes plugins respect MONITOR semantics.

One event-triggered grouped `runTask` drains at most one pending entry per UUID,
with a hard 1024-entry cap. There is no new polling loop, timer per player or
recurring community task. Main-thread continuation rechecks the original entity
and session token, loaded current profile, owner/config/catalog identity, mute
and current progress-capable assignment. The thread precondition is executable.

An internal QuestService assignment invalidation observer runs after actual
assignment changes, including failed Force rollback identities. It discards old
queued messages and temporary counters without refreshing menus or writing data.
The registry publishes its existing immutable map through a volatile reference;
reload/rollback identity changes reject old queued events. Old bounded digest
state is invalidated lazily before the next matching observation. Ordinary
progress writes and flushes do not reset those identities.

`PrivateMessageService.DeliveryListener`, `FriendService.SuccessListener` and
`TeleportRequests.SuccessListener` are small operation-success boundaries. Friend
notifications follow successful domain persistence; its existing relationship
observer cannot distinguish request/accept/remove, which is why it is not reused
as an ambiguous progress source. PM/TPA pass Player only through synchronous
callbacks. Producer maps retain no Bukkit Player objects.

Subscriptions are owned before acquisition. Failed partial starts close authority
and independently release acquired observers/listeners. A refused release stays
in QuestModule's existing pending-cleanup contract; re-enable is blocked until
explicit disable retry confirms release. Quit/rejoin, unloaded profiles, new
assignments and catalog changes invalidate transient state. Disable makes queued
one-shot callbacks inert and clears maps before attempting independent releases.
Observers belonging to other consumers are retained.

A failing community observer/report is logged and does not roll back a delivered
PM, persisted friendship/request or completed teleport. There is no automatic
replay of an already successful social action. Existing QuestService/HUD observer
isolation preserves numeric progress/rewards if presentation fails.

## YAML compatibility

The ten reference IDs `chat_hello`, `chat_thanks`, `chat_unique_3`, `chat_unique_6`,
`private_message`, `private_reply`, `friend_request`, `friendship`, `tpa_visit`,
`tpa_host` are used by compatibility tests only. Producers have no ID lookups.
`daily-quests-08a1.yml` remains an operator reference catalog, not a resource to
install automatically. Bundled `daily-quests.yml` remains `enabled: false`,
`quests-per-day: 4`, `quests: {}`. Valid unsupported keys remain allowed;
`social:meet_unique` deliberately has no producer in this step.

## Verification

Baseline compiled from the original Step-08B state: **239/239 harnesses, 35,420
checks, Failed=0, Missing/Invalid=0; Maven and Spotless PASS**. The initial default
PATH Java version probe hung before compilation; only that identified verifier
probe was stopped. The successful run used the installed explicit JDK 21. No
project Run configuration, PATH or operator server was changed for that workaround.

Final canonical command, actually run with the explicit installed JDK 21:
`pwsh -NoProfile -File .\scripts\verify.ps1`.

**242/242 harnesses passed; 35,629 checks; Failed=0; Missing/Invalid=0;
Maven clean package PASS; Spotless PASS.** All 239 previous executable harnesses
remain present and passed. The added harnesses are:

| Harness | Checks |
| --- | ---: |
| `QuestCommunityChatHarness` | 78 |
| `QuestCommunitySocialHarness` | 59 |
| `QuestCommunityModuleHarness` | 65 |

The new harnesses contribute 202 checks. Existing discovery-based checks add seven
more: PermissionDescriptor 921→923, RepoLink 122→123, GuiAuthoringAudit 280→284.
These existing harness sources were not edited. The complete discovered inventory
comparison is retained outside the repository. No harness was removed/bypassed.
After finalizing this report, 33 local Markdown file targets in this report and
the Developer Guide were also checked successfully; `git diff --check` is clean.

Focused new harnesses cover word boundaries/NFKC, spam/duplicates/cooldown, async
handoff and main-thread precondition, muted/cancelled chat, logout/rejoin, unloaded
profiles, multiple same-key quests, PM/reply failure/Ignore/settings cases,
post-save friend request/accept/auto-accept and failed persistence, successful and
negative TPA paths including `/tpahere`, real HUD/rewards, Force/reset/catalog and
chat reload identities, partial start, foreign subscriptions, disable/re-enable
and cleanup retry. All original harness sources remain present.

The initial focused attempts corrected test-fixture issues: the PM/TPA actor
needed display-name/entity/statistic surfaces, one feedback assertion ran after
the replay attempt, and the YAML test initially targeted a nonexistent private
constructor instead of the existing public `(Path, Logger)` constructor. These
did not require production delivery/teleport changes.

Native Paper/LuckPerms result: **47 checks passed**, final `native-05`, process
9340 exited normally with code 0. Runtime: disposable Paper 1.21.11 build 132 and
LuckPerms 5.5.84, bound only to loopback port 25663. The final plugin artifact and
the isolated server's copied plugin are SHA-256 identical to the canonical build:
`6CBDE01AC94C03D93CC7ACDEC4E5ADA27C8B53DD1C0AFE813D46523F2DE85527`.

The helper exercised the real Paper async event dispatcher, actual core mute
cancellation/projection, scheduled main-thread handoff, PM/reply/Ignore success
boundaries, persisted friend request/counter-request, actual consent/nonce paths,
successful **CraftPlayer teleport**, cancelled **PlayerTeleportEvent**, expired/
denied/replayed TPA, `/tpahere` direction, existing generic HUD packets and unchanged
native server XP, logout invalidation and absence of duplicate coin rewards.

These are API/integration assertions with explicitly controlled online resolvers,
synthetic ServerPlayer connections and a valid-entity/online-session facade. PM
chat delivery is captured at the Player API boundary; no authenticated network
client or signed-chat/client acknowledgement is represented. The native
CraftPlayer teleport and cancellation path, world/block data and HUD packets are
real. No NMS or test facade was added to production sources.

The first disposable launch (`native-01`) packaged an incorrect helper main-class
namespace. Paper rejected only that external test helper; it performed no native
acceptance checks. The owning runner stopped its own disposable server normally.
This failed attempt is retained as evidence, not counted as successful acceptance.

`native-02`/`native-03` reached 25 successful checks but stopped at the conservative
TPA destination gate before any teleport call. Unregistered test actors did not
retain destination chunks between callbacks; a missing valid-entity flag was
also corrected in the fixture. `native-04` explicitly inspected/warmed the real
block/chunk data and passed 44 checks. The final `native-05` uses an **own test
plugin chunk ticket**, removing that diagnostic side effect. It additionally
proves that an unloaded distant target is rejected without loading its chunk.
All 47 checks then pass without production gate changes or looser teleport rules.
Each disposable process exited normally; no operator server was used or stopped.

Evidence is outside the repository under
`C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08c-community-quest-progress-20261107-001834/`.
It contains before snapshots, verifier results/logs, focused logs, native helper
source/arguments, isolated server logs, exact plugin hashes and process receipts.

## Changed paths

1. `src/main/java/dev/vapee/core/VapeeCore.java`
2. `src/main/java/dev/vapee/core/quest/CommunityChatText.java`
3. `src/main/java/dev/vapee/core/quest/QuestCommunityProducer.java`
4. `src/main/java/dev/vapee/core/quest/QuestModule.java`
5. `src/main/java/dev/vapee/core/quest/QuestService.java`
6. `src/main/java/dev/vapee/core/quest/QuestDefinitionRegistry.java`
7. `src/main/java/dev/vapee/core/privatemessage/PrivateMessageService.java`
8. `src/main/java/dev/vapee/core/friend/FriendService.java`
9. `src/main/java/dev/vapee/core/utility/UtilityModule.java`
10. `src/main/java/dev/vapee/core/utility/tpa/TeleportRequestRegistration.java`
11. `src/main/java/dev/vapee/core/utility/tpa/TeleportRequests.java`
12. `src/test/java/dev/vapee/core/quest/CommunityTestSupport.java`
13. `src/test/java/dev/vapee/core/quest/QuestCommunityChatHarness.java`
14. `src/test/java/dev/vapee/core/quest/QuestCommunitySocialHarness.java`
15. `src/test/java/dev/vapee/core/quest/QuestCommunityModuleHarness.java`
16. `src/test/java/dev/vapee/core/utility/tpa/TpaTestSupport.java`
17. `docs/DEVELOPER_GUIDE.md`
18. `reports/08c/STEP_08C_COMMUNITY_QUEST_PROGRESS.md`

## Protection and remaining client acceptance

The preservation checker compares **1,496 original files** across both existing
worktrees and permits only the 18 documented feature paths. Both Run files and
both protected receipts retain their exact initial SHA-256 values. All pre-existing
local/remote/tag refs and the protected `chat-polish-wt`, including its pre-existing
untracked report, remain unchanged. There are no unrelated local changes in the
main worktree. The new feature branch is the only additional ref.

No operator environment is a mutable fixture. Live operator files may change
externally; they are not claimed byte-identical. The exact one-commit SHA and
pre-/post-commit protection results are recorded in the final response and the
external delivery receipt, without a self-referential hash in this report.

Real Minecraft clients remain required to confirm public signed-chat delivery,
locale-specific quest name/percent display, Actionbar/XP appearance and restoration,
Navigator foreground priority, real network PM/reply and friend click actions,
and both regular TPA and TPAHere consent/arrival from two connected clients.
Native API assertions do not constitute this visual/network-client acceptance.

No push, merge, PR, deployment, live-server intervention, new command/permission,
Economy/Reward persistence change or subsequent feature phase belongs to this step.
