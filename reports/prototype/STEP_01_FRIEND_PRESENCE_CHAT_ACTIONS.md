# Prototype Step 01 — Friend Presence & One-Click Chat Actions

## Baseline and focused scope

Clean integrated master `f70bbbf9e63f30d0d8a7dd7e4b9a91a0bb51d71c`, repository
`xd-vape/mc-vapeecore`, branch `prototype/friend-presence-chat-actions`. Status, branch, HEAD,
twelve commits and worktrees matched the requested preflight; no applicable AGENTS.md was found.
Baseline `scripts/verify.ps1`: **207 harnesses / 33,334 checks; Failed=0, Missing/Invalid=0,
Maven clean package PASS, Spotless PASS, exit 0**. Production changes started after this passed.
Oracle JDK 21.0.12.1+1-LTS-4, Maven 3.9.16, PowerShell 7.6.5, Windows 11.

Exactly one local commit is intended: `Improve friend presence and one-click social actions`.
The full commit SHA is returned in the final response and external completion receipt rather than
embedded self-referentially in its own contents. No push, merge, deployment or installed-server test.
Architecture modernization is closed; this is a focused prototype feature correction.

## Confirmed causes and compatibility

PlayerSettings previously defaulted friend presence to false; FriendPresenceNotifier requires a
loaded recipient with the current setting true. This explains silent new/default recipients without
assuming event order or dispatch is broken. The unchanged real Presence listener joins at MONITOR
after Player NORMAL load and quits at LOWEST before Player NORMAL save/unload. Actual transition
state suppresses duplicate join/quit delivery. FriendService supplies confirmed friends only,
excluding self/pending relationships; notifier preserves online, loaded-setting and both-direction
Ignore filtering. MessageService retains recipient localization and literal player names.

Only the model default changes to **true**. Missing YAML and existing malformed optional-value
fallback use that same default. Every actual persisted Boolean, including **false**, remains authoritative.
Reading does not eagerly save/migrate profiles; schema 1 and `settings.friend-presence-notifications`
are unchanged. Old serialized false may have originated from the old default or a deliberate choice;
the plugin cannot distinguish them. Those existing users enable notifications through the existing
Settings bell/status toggle. Explicit true/false, Save/Rollback, unrelated settings and the GUI remain tested.

No additional Presence production defect was demonstrated or changed. Module startup/cleanup,
event priorities, loaded-player requirements, module order and seven reload participants are preserved.

## Direct actions and security

Friend received-request notifications and missing-target Accept/Deny choices now use RUN_COMMAND
with `/friend accept <canonical-player-uuid>` and `/friend deny <canonical-player-uuid>`. Direct
Friend action construction validates the complete canonical UUID before concatenation; whitespace,
control/command-looking strings, names and abbreviated UUIDs are rejected. Display names remain
literal/localized content. The existing three-argument FriendMessages constructor remains compatible;
its formatter is no longer used to choose direct-action targets. IdentityCommandArgument and manual
name/UUID completion remain unchanged for editable commands and existing relation-based suggestions.

Clan received-invitation notifications now receive the actual clan UUID. Notifications and incoming
Accept/Deny choices execute `/clan accept <clan-uuid>` and `/clan deny <clan-uuid>`. The visible clan
name/tag remain unchanged. A tag rename/reuse cannot retarget an existing button; a disbanded ID
cannot resolve to a replacement clan with the old tag. Existing translated invite labels/hover text
are reused; no language resource or clan/profile schema is changed.

RUN_COMMAND submits the ordinary existing command as the clicking player. It is not permission or
request authority: FriendCommand/ClanCommand still check their existing permissions and services still
validate the clicking UUID, current request/invitation, Ignore/privacy policy, role, membership and
limits. No console execution, custom protocol, scheduler, cache or new persistence path exists.
Duplicate/handled/cancelled/nonexistent actions fail through existing errors without another domain
mutation. A forwarded button grants no rights. Existing friend/clan Save-before-publish behavior remains.

Friend Remove, both Cancel choice types and Clan Disband remain SUGGEST_COMMAND. No destructive
action or owner-relative cancellation is silently made immediate. Editable `/friend add ` and manual
name/tag syntax remain. There is no new TTL or per-request nonce: neither current service expires
invitations by wall-clock age. Removed/invalidated invitation state is tested; no claim of a newly
implemented expiry mechanism or versioned protection against reissuing the same relation is made.

## Audit of every production chat click site

| Site | Classification / result |
|---|---|
| FriendMessages actionButton; FriendCommand incoming choices | Accept/Deny immediate request actions, canonical player UUID, RUN_COMMAND; Cancel/Remove remain suggestions |
| ClanMessages inviteButton; ClanCommand incoming choices | Accept/Deny immediate request actions, stable clan UUID, RUN_COMMAND |
| ClanCommand outgoing Cancel | Deliberate editable owner-relative action, unchanged suggestion |
| ClanCommand disband confirmation; ClanMenu disband prompt | Destructive action requiring deliberate confirmation, unchanged suggestion |
| ClanCommand usage/examples/help; CommandHelpRenderer | Editable syntax/help, unchanged suggestion |
| FriendMenu add; ClanMenu create; VisiblePlayersMenu add | Editable input templates, unchanged suggestion |
| HistoryCommand pagination | Ordinary information/navigation, unchanged suggestion; possible future UX candidate only |
| BlackjackCommand setup progress/next-step actions | Privileged/editable setup guidance, unchanged suggestion |
| WarpCommand name/icon customization | Editable configuration examples, unchanged suggestion |

These are all discovered production ClickEvent/clickEvent sites. No global replacement was performed.
`/tphere` remains the existing direct staff teleport tool; no TPA/TPAHere feature is added.

## Behavioral verification

Existing **207 canonical entry points** are retained. Eight existing harness files are extended or
updated only for the intentional default/action contracts; their security and adversarial checks remain.

| Focused harness | Before | Current | Added behavior |
|---|---:|---:|---|
| FriendCommandHarness | 96 | 133 | Execute extracted RUN_COMMAND through real command/service and disposable FileFriendRepository; permission/copy/Ignore/privacy refusal, duplicate/cancelled actions, ambiguous names, UUID validation, independent cold relationship read |
| ClanCommandHarness | 99 | 130 | Execute extracted actions with real ClanService/FileClanRepository; permission/copy/stale rejection, both incoming sources, tag rename/reuse/disband, unrelated invite preservation, cold membership |
| PresenceLifecycleHarness | 24 | 42 | Real PlayerModule/PresenceModule, services, MessageService and files; early unloaded join, seed silence, join/quit delivery/dedup, self/nonfriend exclusion, disabled/Ignore filters, settings reload and cleanup |
| SettingsPersistenceContractHarness | 72 | 74 | Explicit historical false retained byte-for-byte; existing true/missing/fallback/schema/candidate/rollback assertions preserved |
| PlayerSettingsYamlCodecHarness | 668 | 668 | Updated independent default oracle; actual true/false/type/null mapping and warnings preserved |
| PlayerVisibilityPersistenceHarness | 17 | 17 | Missing/invalid default updated; unrelated visibility values/round-trip/read-only behavior preserved |
| PlayerSettingsServiceHarness | 28 | 28 | Enabled default; real toggle and failed-save rollback still exercised |
| SettingsMenuHarness | 66 | 66 | Green enabled initial status; both icon/status toggles and rollback remain functional |

Focused final round: **8 harnesses / 1,158 checks**, all PASS. Two initial focused failures were
outdated independent test oracles (Boolean fallback and candidate value inversion); those were updated
to the intentional new default while preserving refusal checks. They did not require a repository fix.
PresenceLifecycleHarness was formatted with the pinned GJF 1.22.0/AOSP because it is in the existing
six-file pilot; the formatter scope/configuration is unchanged.

The first complete gate rejected formatting of the expanded pilot test; the targeted pinned
`spotless:apply` corrected only PresenceLifecycleHarness. Its rejected log is retained separately.
Final canonical `scripts/verify.ps1`, exit **0**: **207 discovered/executed/passed harnesses,
33,422 checks, Failed=0, Missing/Invalid=0; Maven clean package PASS; Spotless PASS**.
All 207 baseline entry points remain. Exact net **+88**: Friend command **+37**, Clan command
**+31**, Presence lifecycle **+18**, Settings persistence **+2**, RepoLink **+1**, CatalogUsage
**-1**. The last is a source-regex reference-count change: the replaced dynamic hover-prefix call
became a conditional selecting existing translated invite hovers. The unchanged CatalogUsage harness
still renders every catalog key in both languages, consumes safe placeholders and validates all
matched production/resource references. No language key, behavioral assertion or harness is removed.
The per-entry delta receipt documents this distinction rather than hiding the audit-count decrease.

## Native Paper acceptance

**PASS: 48 checks across two independent JVMs: 40 in the first run, eight after cold restart.**
Paper **1.21.11-132**, LuckPerms **5.5.84**, Java 21; fresh worlds/configuration/four synthetic
profiles on loopback **127.0.0.1:25601**. Both processes exit **0**, with **zero ERROR/SEVERE**,
and actual Root shutdown reports all module calls returned normally. Distinct runtime tokens and a
persisted first-process marker establish a real cold restart.

The helper uses the actual 28-module composition, original Friend/Clan services and existing registered
PluginCommand executors. Extracted RUN_COMMAND strings execute through native PluginCommand.execute,
including permission refusal, forwarded Friend action refusal, direct Accept/Deny, repeated clicks,
Clan tag rename/reuse and the correct original clan membership. Incoming choices retain editable
usage suggestions alongside direct UUID actions. Core-owned Player/Social/Presence RegisteredListener
executors are selected from the native event HandlerLists and run in their baked priority order;
join/quit delivery, duplicate suppression, no self/nonfriend message, opt-out, Ignore and re-enabling
are asserted through actual MessageService delivery. Cold startup confirms friendship, exact renamed
clan membership, explicit false and unrelated true profile values; a separate fresh repository read
agrees with persisted false.

Actors are controlled Player proxies; permission/locale/message transport and online-player lookup are
external test boundaries. The helper adapts only the existing message-feedback/notifier lookup callbacks
to those actors. No production domain service, file writer or global logger is replaced. Selected
event executors are exercised, not the unfiltered whole-server event bus. There is **no real network
login, client UI or physical mouse-click/Enter test**; RUN_COMMAND API assertions remain separate from
the manual checklist. No LuckPerms ranks/permissions are assigned or altered.

Tested JAR SHA-256 equals the final canonical artifact:
`8253d84f829963d8536e2cf65a5a853c43d9921cad87c76bee00346f014ea566`.
All production source hashes agree before/after native execution. Source, wrapper, compilation,
consoles, two receipts, binary manifest, tested configs and four hash-verified profiles remain external.
A wrapper interpolation parse error was corrected before launching a server and is not a behavioral
test result. The disposable server/world directory was removed only after both confirmed exits,
checked absolute containment/no reparse points and saved evidence; installed operator data was untouched.

## Manual two-client acceptance still required

No real Minecraft client acceptance is claimed. On a disposable Paper/LuckPerms server:

1. Connect two real players and establish friendship.
2. Enable Friend Presence in Settings, including historical profiles persisted as false.
3. A quits: B receives exactly one offline message. A rejoins: B receives exactly one online message;
   A receives no self notification.
4. Disable notifications, repeat join/quit and verify silence; check Ignore in both directions.
5. Send a new Friend request and click Accept without pressing Enter; replay the stale click.
   Repeat Deny and check ordinary permission/privacy errors.
6. Repeat Clan invitation Accept/Deny without Enter; rename/reuse a tag before clicking and verify
   the original clan identity is retained. Cancelled/handled invitations remain rejected.
7. Restart the entire server JVM, rejoin and confirm friendship, settings and clan membership.

## Protected files and exact changed paths

Fresh before inventory: **914 actual installed server files**, nine .run/top-level development files,
711 pre-existing tracked files and ten recovery/ref-log/sync-conflict evidence files. These are current
measurements, not inherited historical counts. Profiles/Coins/quests/cycles, worlds, LuckPerms, installed
JARs/configs and recovery evidence are never mutable fixtures. Existing stashes/refs are preserved.
Before/after relative path, length and SHA-256 comparisons are retained externally.

Final complete comparison: **914/914 operator files**, **9/9 Run/development files** and
**10/10 recovery/ref-log/conflict evidence files**, each with **0 added, removed, changed or
inaccessible files**. Existing ref values and stash listings are unchanged. Of 711 previously tracked
files, exactly the 17 listed existing paths changed and the other **694** retain identical bytes/hashes;
the report is the sole new file. Unexpected changes: **0**. `git diff --check` passes. Final doc-link
validation after report completion passes 90 checks. No installed server or deployment script ran.

Production (five files):

- `src/main/java/dev/vapee/core/player/settings/PlayerSettings.java`
- `src/main/java/dev/vapee/core/friend/FriendMessages.java`
- `src/main/java/dev/vapee/core/friend/command/FriendCommand.java`
- `src/main/java/dev/vapee/core/clan/ClanMessages.java`
- `src/main/java/dev/vapee/core/clan/command/ClanCommand.java`

Tests (eight existing files):

- `src/test/java/dev/vapee/core/friend/FriendCommandHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanCommandHarness.java`
- `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java`
- `src/test/java/dev/vapee/core/player/repository/PlayerSettingsYamlCodecHarness.java`
- `src/test/java/dev/vapee/core/player/repository/SettingsPersistenceContractHarness.java`
- `src/test/java/dev/vapee/core/player/repository/PlayerVisibilityPersistenceHarness.java`
- `src/test/java/dev/vapee/core/player/settings/PlayerSettingsServiceHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsMenuHarness.java`

Guidance: `docs/DEVELOPER_GUIDE.md`, `docs/SETTINGS_DEVELOPMENT.md`,
`docs/PERSISTENCE_DEVELOPMENT.md`, `docs/SERVICE_DEPENDENCIES.md`; plus
`reports/prototype/STEP_01_FRIEND_PRESENCE_CHAT_ACTIONS.md`.
Total intended scope: **18 files**. No production Presence, services/repositories/writer, schema,
localization resources, commands/permissions metadata, module order, reload, build/deploy or operator changes.

Evidence root:
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\prototype-step01-evidence-20261028`.

Recommended next prototype step: execute the above two-client acceptance and fix only any demonstrated
interaction failure before expanding features. Do not automatically start Navigator V2, Blackjack
extraction, TPA, Staff Panel, Extras, Housing, database migration or another modernization phase.
