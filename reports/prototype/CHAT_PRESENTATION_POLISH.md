# Chat UX and Message Presentation Polish

Completed 2026-11-02 on `codex/chat-presentation-polish`, based on fetched integrated `master`/`origin/master` at `e518c338404e1531a1004f78a3f8bd0ce23644b4`. Composition remains **27 modules / 7 ReloadService participants**. No push, merge or deployment.

## Preflight and isolated worktree

The original checkout was already on the expected integrated TPA commit, but contained additional pre-existing source/document changes and numerous `conflicted copy` files. Its content inventory captured **31 modified tracked paths including the approved Run change, plus 36 untracked conflict copies**. They were not overwritten, restored, staged, stashed, cleaned or committed. No synchronization/conflict repair was attempted in this presentation task.

A clean feature worktree was created directly from the integrated commit at:

`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\chat-polish-wt`

The initial canonical verifier ran there before source changes and passed **223/223 harnesses / 33,144 checks**, Failed=0, Missing/Invalid=0, Maven clean package PASS and Spotless PASS. The original checkout stays on master with its existing local work intact; the completed feature and its local commit live on the dedicated worktree branch.

## Inventory and presentation rules

The review covered MessageService, MessageStyle, SocialChannel, CommandHelpRenderer, TeleportRequests, ModerationComponents, ModerationMuteChatListener/projection, all seven moderation executors, StaffPanel and both language catalogs. Representative Friends, Clan, Utility, Coins/Rewards/Quests, Settings/Profile and Navigator feedback was also inspected.

| Surface | Previous presentation | Result |
|---|---|---|
| TPA | Dedicated `[TPA]` heading on every raw response; all-green success sentence | Raw, prefix-free single-line feedback; existing rank-colored names and Accept/Deny actions; small green success accent |
| Staff Panel and public roster | General MessageService delivery, optional global wrapper, no dedicated Staff heading | One subtle `[Staff]` EN / `[Team]` DE heading through the existing SocialChannel enum and raw path |
| Moderation actor results | Entire message colored yellow; syntax explanation plus fully aqua second line | Neutral gray body, selective green success/error-label accents, aqua values/command root, neutral arguments; one usage line or two lines when the actual reason is useful |
| Punishment recipient | General wrapper for chat notices; full three-line mute notice on every blocked attempt | Raw victim notices without administrative/global wrappers; full initial mute information in two lines; compact repeat reminder |
| History | Three to six lines per entry, repeated global wrapper | Two lines per entry retaining facts; one Staff heading per page, raw entries/navigation; full record ID still in hover |
| Shared help | Gold/bold branding, separator, blank lines, two or three lines per entry | Existing permission-filtered categories, one line per command with inline description/optional hint, no separator or forced bold; same SUGGEST_COMMAND |
| Utility/Settings usage | `/tp` dumped six forms; ping/BUILD used extra lines; bright entire syntax | Relevant `/tp` form only, single-line ping/BUILD syntax, neutral Settings arguments; command parsing unchanged |

Normal explanations remain gray. Existing supported primary-rank name colors are preserved in TPA, social messages and Staff roster names; existing moderation name/value fallbacks remain intact rather than introducing another rank dependency/cache. Dynamic values use the established aqua palette. Reasons and names remain literal Components, including text resembling MiniMessage/click tags. No new universal notification abstraction was added: MessageService is unchanged, SocialChannel gains only the focused Staff case, and MessageStyle gains a literal syntax helper.

Friends/Clan already used their localized channel headings, rank-aware names and compact action feedback. Coins, rewards/quests and Settings additions/removals already used neutral bodies with highlighted values. Profile fields and Navigator's localized, family-specific feedback were already readable; Navigator's `say` path is gray and does not dump full help on ordinary malformed subcommands. These surfaces were retained except for the identified Settings syntax styling. Existing independent operator-configured chat, PM, lobby and other templates remain untouched. This was not a sweep of every source file.

Existing help categories are retained and vertical volume is reduced substantially; no new paging argument grammar or help GUI was introduced. A privileged all-commands help can still be long and needs rendered-client review for wrapping.

## Exact prefix policy

- **TPA:** no `[TPA]`, Staff or global VapeeCore/operator prefix on any feature response. The unused `tpa.prefix` catalog entry is removed in both languages. Delivery still uses `sendRaw`.
- **Staff Panel feedback and public roster:** one localized dark-gray Staff heading. Inventory names/lore/layout are unchanged. PUBLIC/HIDDEN eligibility, visibility and privacy checks are unchanged.
- **Moderation actor results, plugin executor denials and relevant usage:** one Staff heading, including console actor responses. Error-label text is red, explanations/arguments remain neutral. Bukkit/Paper's earlier descriptor permission rejection remains platform-owned; permissions and dispatcher registration were not changed to intercept that rejection.
- **History:** Staff heading on the page header only; raw record entries and navigation avoid repeating either Staff or global wrappers.
- **Victims:** warning, mute, unmute and repeat reminders use raw chat delivery; ban/kick screens remain direct Components with no Staff wrapper. Reasons, duration/permanent status and expiry information remain available.
- **Friends/Clan:** established localized headings and existing global-prefix convention remain unchanged. Prefix-free Join Summary, operator-authored PM/chat/lobby wrappers and other independent channels retain their contracts.

Every chat action retains its original action type and payload. TPA buttons remain ordinary player RUN_COMMANDs containing sender UUID plus random request UUID. Staff input preparation and help remain SUGGEST_COMMAND; they never execute an automatic sanction.

## Compact EN/DE examples

Colors described here refer to actual Component spans, not text markup shown to players:

| English | German | Presentation |
|---|---|---|
| `Alex wants to teleport to you (60 seconds). [Accept] [Deny]` | `Alex möchte sich zu dir teleportieren (60 Sekunden). [Annehmen] [Ablehnen]` | No heading; gray body, rank-colored Alex, green/red buttons |
| `[Staff] Warned Alex.` followed by `Reason: Example` | `[Team] Alex verwarnt.` followed by `Grund: Beispiel` | One heading, small green verb, literal reason on useful second line |
| `[Staff] Error: Missing reason. Usage: /warn <player|uuid> <reason...>` | `[Team] Fehler: Grund fehlt. Verwendung: /warn <player|uuid> <reason...>` | One line; error label red, `/warn` aqua, arguments gray |
| `You are muted. Expires: Permanent` | `Du bist stummgeschaltet. Läuft ab: Dauerhaft` | Compact repeat reminder without reason repetition or administrative prefix |
| `/coins <…> — Short description (optional hint)` | `/coins <…> — Kurze Beschreibung (optionaler Hinweis)` | One help-entry line with neutral arguments and original insertion action |

Timed mute reminders show the actual UTC expiry rather than Permanent. Initial mute notices additionally show the complete literal reason on a second line. Ban/kick disconnect screens continue to show mandatory sanction information; these are not treated as generic command syntax lines.

## Mute feedback contract

The LOWEST/ignoreCancelled listener still cancels **every** chat event with an active immutable mute fact before the normal formatter. Only the reminder notification is throttled: **ten seconds per target, exact Player session and full immutable ModerationRecord value**, using a monotonic clock. A record with the same UUID but updated contents does not inherit stale notice state.

A ConcurrentHashMap compute makes the feedback claim atomic across parallel chat threads. Silent repeated attempts still stay cancelled. Successful claims schedule only the existing one-shot main-thread feedback task. The chat path reads the immutable projection, clock and event player's UUID; it performs no persistence, identity lookup, online-player lookup, permission/context query or audience send. There is no recurring scheduler or persisted throttle state.

The main-thread callback rechecks active runtime, current claim identity, the exact current online Player object and the exact current active mute record. Revoke, replacement, expiry, logout/reconnect and module shutdown cannot deliver an old queued reminder to a new session or display an obsolete sanction. Failed scheduling/delivery releases that claim, allowing a retry while continuing to block chat.

The existing committed snapshot publisher also reconciles feedback state after successful moderation changes, including unmute/replacement. Expiry cleanup is lazy on chat/feedback/projection activity rather than timer-based; retained quiet entries cannot make an expired record active or suppress a new/different record. Quit removes only its exact session's stamp; shutdown clears all state and re-enable creates a fresh listener. No changes were made to ModerationService, repository/storage or enforcement semantics.

Direct staff-issued mute/warn/unmute notifications never pass through this throttle. Full reasons and current timed/permanent details remain delivered even while repeated blocked-chat reminders are silent. Permission/hierarchy gates, actor identity, persistence/audit ordering and actual sanctions are unchanged.

## Canonical verification

Final `pwsh -File .\scripts\verify.ps1`: **225/225 harnesses / 33,292 checks**, **Failed=0**, **Missing/Invalid=0**, **Maven clean package PASS**, **Spotless PASS**. All **223 original harness identities** remain present and passing. Module count, seven reload participants, command descriptor and permission graph remain unchanged.

New compiled executable coverage:

- `ChatPresentationHarness`: **68 checks**, both EN/DE, real moderation commands/Staff panel/TPA components, explicit global-prefix fixture, exact Staff scope, literal reasons, dynamic/rank colors, raw victim notices, history metadata/compactness, relevant syntax, regrant/revoke, RUN_COMMAND/SUGGEST_COMMAND and permission-filtered compact help.
- `ModerationMuteFeedbackHarness`: **80 checks**, 24 concurrent chat threads, all attempts cancelled, one atomic claim, exact ten-second boundary, no chat-thread lookup/delivery/persistence, new and same-ID-updated records, obsolete callback suppression, expiration, old/new session identity, stale quit, scheduler failure/retry, actual module publication/disable/re-enable and unsuppressed staff-issued sanction notices.

The old MuteEnforcement harness now asserts compact reminders rather than a repeated full reason and advances an injected clock where another notification is required. Its runtime check count decreases by one because throttling deliberately prevents an extra scheduling callback; no enforcement/security assertion was removed. CatalogUsage decreases by one due to the removed direct prefix reference; GuiAuthoringAudit scans the two added test sources (+2). These changes cancel numerically, so the full **+148** delta is exactly the two new harnesses. Existing moderation freshness tests now require the exact new `[Staff] Error: …` heading while preserving all denied-operation state and side-effect assertions; old ping/TPA display expectations were updated narrowly.

Focused compilation/tests caught and corrected a new test's inaccessible inferred base type and stale formatting expectations. The complete final canonical run passed on its first post-implementation execution. No harness was disabled, deleted or bypassed.

## Native Paper + LuckPerms acceptance

Successful `native-02`: **Paper 1.21.11 build 132 (`c5eb079`), Java 21.0.12.1, LuckPerms 5.5.84**, fresh generated worlds, independent player/moderation/LuckPerms data, loopback **127.0.0.1:25629**. Runtime JARs/libraries were copied only from the already stopped prior disposable TPA fixture, not the live dev-server. The isolated default config deliberately set a visible `[Operator]` global prefix to expose accidental duplicate wrappers.

**57 native server assertions and 21 connected-client assertions; 32 acknowledged fixture steps, zero failures.** Two actual non-OP offline protocol accounts, ChatStaff and ChatPlayer, sent commands and real chat packets. Packet-generated AsyncChatEvent cancellation was observed at MONITOR and confirmed off the main thread. Five successive blocked attempts yielded one compact reminder, the later interval yielded the next, and direct staff warnings/full replacement mutes were still received. Actual expiry allowed chat without rewriting moderation.yml. Effective LuckPerms revoke/regrant, prefix-free TPA UUID actions, raw victim reasons, grouped history, public HIDDEN roster privacy, reconnect and actual module disable/re-enable were verified.

Staff GUI suggestion testing used four explicitly synthetic native InventoryClickEvents against the real bound inventory; those API clicks remained cancelled and their next-tick transitions produced the unchanged SUGGEST_COMMAND on the connected client. These were not physical mouse/client GUI clicks. The TPA and moderation command paths and all chat packets were network-originated. One additional executor-denial check intentionally called the native executor API to verify plugin Staff error scope; a separate network command proved descriptor-level denial had no sanction effect.

The successful Paper log has **zero ERROR/SEVERE entries**. All module shutdown calls returned normally, and an explicitly retained process handle recorded **exit code 0**. The native-tested artifact is byte-identical to the final canonical JAR:

`404444BEDECEF7356234C8C750583D9448190B407F9B8E9EDAA1CC77A722F903`

The earlier `native-01` client driver falsely treated the literal substring `run_command` inside the malicious test reason as an actual ClickEvent. Inspection showed the reason rendered literally. The external driver was corrected to traverse structured `action` fields, the first server was stopped normally, and the fresh complete run passed. No product change was made for that test-driver correction; the failed attempt's logs remain available and are not counted as completed acceptance.

Paper documents the synchronous/asynchronous distinction for [AsyncChatEvent](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/AsyncChatEvent.html); the implementation handles both using the same safe projection/one-shot task boundary. [PluginCommand](https://jd.papermc.io/paper/1.21.11/org/bukkit/command/PluginCommand.html) remains registered under its existing permissions. Runtime API behavior, not just source strings, was exercised above.

## Remaining rendered Minecraft-client checks

- Inspect actual line wrapping, gray/white readability, rank-name colors, small green/red accents and EN/DE wording with normal client fonts and chat scale.
- Click TPA Accept/Deny and Staff/help suggestions in a rendered client; inspect normal chat-input insertion and hover behavior.
- Inspect larger permission-filtered help/history pages and literal long reasons, plus actual ban/kick disconnect-screen layout.
- Check the ten-second reminder experience and direct sanction notices during ordinary gameplay with real deployment chat/protection plugins.

Protocol clients, captured Components and native API events are **not** physical/rendered Minecraft-client visual acceptance. Those checks remain open. No known failing implementation or canonical/native acceptance check remains.

## Original checkout, Run and operator protection

All **67 captured original local paths** compare byte-identically after implementation. Their SHA-256 manifest includes the **36 existing untracked conflict copies**. Original master remains at the integrated TPA commit and its index remains empty. No unrelated original local changes were modified or absorbed into this commit. Evidence includes `root-status-before.txt`, `root-local-before.json` and `root-local-comparison.json`.

The approved `.run/Start VapeeCore Dev Server.run.xml` in the original checkout retains its exact original Git diff and SHA-256:

`EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`

It was never edited, reset, restored, stashed, staged or committed. The clean worktree's committed Run file was also untouched and is excluded from the changed paths below. Operator worlds, players, logs, LuckPerms databases and configurations were not opened, hashed, changed or stopped by this task. No claim of live operator-file byte identity is made. Native fixtures used only their own generated files and both test processes are stopped; their isolated server trees are retained as evidence without destructive cleanup.

Logs, manifests, fixture sources, protocol transcripts and verification receipts are outside Git at:

`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\chat-presentation-polish-20261102`

Principal receipts: `baseline-results.json`, `verification-final-results.json`, `harness-ledger.json`, `native-02/native-chat-results.json`, `native-02/protocol-client-results.json`, `native-02/native-chat-evidence.txt`, `native-02/protocol-client-transcript.txt`, `native-02/process-exit.json` and the Run/original-checkout manifests. Fixture code is not packaged in the plugin or included in the feature commit.

## Exact committed paths

1. `docs/DEVELOPER_GUIDE.md`
2. `reports/prototype/CHAT_PRESENTATION_POLISH.md`
3. `src/main/java/dev/vapee/core/command/help/CommandHelpRenderer.java`
4. `src/main/java/dev/vapee/core/message/MessageStyle.java`
5. `src/main/java/dev/vapee/core/message/SocialChannel.java`
6. `src/main/java/dev/vapee/core/moderation/ModerationComponents.java`
7. `src/main/java/dev/vapee/core/moderation/ModerationModule.java`
8. `src/main/java/dev/vapee/core/moderation/ModerationMuteChatListener.java`
9. `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`
10. `src/main/java/dev/vapee/core/moderation/command/BanCommand.java`
11. `src/main/java/dev/vapee/core/moderation/command/HistoryCommand.java`
12. `src/main/java/dev/vapee/core/moderation/command/KickCommand.java`
13. `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java`
14. `src/main/java/dev/vapee/core/moderation/command/MuteCommand.java`
15. `src/main/java/dev/vapee/core/moderation/command/WarnCommand.java`
16. `src/main/java/dev/vapee/core/settings/command/SettingsCommand.java`
17. `src/main/java/dev/vapee/core/staff/StaffPanel.java`
18. `src/main/java/dev/vapee/core/utility/command/PingCommand.java`
19. `src/main/java/dev/vapee/core/utility/command/TeleportCommand.java`
20. `src/main/java/dev/vapee/core/utility/tpa/TeleportRequests.java`
21. `src/main/resources/lang/de.yml`
22. `src/main/resources/lang/en.yml`
23. `src/test/java/dev/vapee/core/message/ChatPresentationHarness.java`
24. `src/test/java/dev/vapee/core/moderation/ModerationMuteEnforcementHarness.java`
25. `src/test/java/dev/vapee/core/moderation/ModerationMuteFeedbackHarness.java`
26. `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
27. `src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java`
28. `src/test/java/dev/vapee/core/utility/tpa/TeleportRequestsHarness.java`

Exactly one additional local feature commit: `Polish chat messages and moderation feedback`. No earlier commit is amended; no push, merge or operator deployment is performed.
