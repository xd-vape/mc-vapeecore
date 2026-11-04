# Command value highlights

## Scope and cause

User report: `/coins add/remove` rendered the whole success sentence gray. The previous validated baseline is `a59d177b86b81e7cb42a157ff9ec10dc76e6dca2` (173 harnesses, 26,986 checks). The working tree was clean before this follow-up. This is a separate presentation correction after the accepted consolidation, not a new architectural phase.

Coin mutation/recipient placeholders had no explicit text color and inherited the sentence's gray. The correction colors meaningful dynamic values at their owning command adapter using the existing `MessageStyle.HIGHLIGHT` (Aqua). It changes no translation content, parser, authorization, mutation, persistence, audit order, message routing or global localization/rendering policy. No YAML migration or language-file edits are required.

## Complete command audit

Reviewed all 37 declared command roots through their command implementations, shared help/messages and presentation adapters (42 command/presentation source files in the source inventory). The inventory includes abstract/context helpers; it is not a count of Bukkit command roots.

| Command group | Presentation decision |
|---|---|
| Coins | Highlight player, amount, new balance in add/remove/set; recipient amount/balance; target name in get and unloaded-data feedback. Own balance already highlighted. |
| Fly, Gamemode, Speed, Ping | Add missing state/mode/type/level/ping highlights. Existing target-name highlights remain. |
| Teleport/TeleportHere, Heal, Feed, Clear, Enderchest | Existing target/destination highlights retained; invalid coordinate/rotation values receive the accent too. |
| Core/status/version/reload | Highlight runtime values and named failed reload participant; retain semantic status/error colors. |
| Friend | Preserve existing success/notification highlights; align list/request names, counts, limits and literal unknown-command values. |
| Clan | Preserve success/invitation highlights and clan identity/action colors; highlight owner/member names, counts/limits, IDs and unknown input; neutral member-capacity sentence. |
| Ignore/Unignore/Ignorelist | Highlight names/UUIDs/count; successful sentences neutral. |
| Settings visibility add/remove | Highlight selected player name and keep successful sentence neutral; domain and GUI state unchanged. |
| Profile | Highlight unstyled field values via fallback while preserving explicit RankInfo colors. |
| Rank/Ranks | Highlight queried player and missing track value; preserve rank, server-name, description and status presentation. |
| Ban/Mute/Kick/Warn/Unban/Unmute/History | Highlight target, duration, page numbers, actors and expiry/revocation timestamps; reasons remain literal body text; severity/disconnect/record-status colors and authorization unchanged. |
| Blackjack setup/info/list | Add missing table IDs, seat numbers, counts and preview-duration highlights; ordinary setup success body neutral. Existing diagnostic labels, marker colors, configured/missing states and clickable workflow syntax retained. |
| Spawn/Setspawn, Build, DailyQuest | Fixed messages/menu entry points have no missing dynamic value to emphasize; existing state/severity/usage styling retained. |
| Message/Reply and shared help | Existing operator-owned private-message wrappers, safe user text and colored command suggestions retained. |

Remaining one-argument `Component.text` calls were inspected: they include profile values colored by its field helper, helper arguments colored by their explicit caller, free moderation reasons, technical record-ID hover text and readable validation/body text. No automatic recoloring by placeholder name, display text, or raw string parsing was introduced.

## Verification

Color regressions exercise actual command execution and delivered Components. They flatten effective text colors, including inherited colors, so a gray ancestor containing a colored child cannot satisfy a false color assertion. Coins covers DE/EN add/remove/set, sender/recipient, get/own/self balance, neutral surrounding text, literal markup-looking player names and no duplicate self notice. Utility covers actual Fly/Speed/Ping feedback. Moderation covers DE/EN targets, duration, history page values and safe reasons. Profile verifies highlighted name/balance and unchanged explicit rank color.

Focused regression: **19 harnesses, 13,977 checks, 0 failures**. The first run identified two outdated test expectations (the English speed label is “Flight”, and an explicitly highlighted unknown Clan argument is Aqua rather than White); both were corrected while retaining literal-text assertions.

An isolated negative control compiled the original baseline `CoinsCommand` into a separate ignored directory and ran the new delivered-color regression against it. It failed at the expected localized admin target/balance color assertion. Production source/classes were never replaced for this control. Receipt: local `dev-server/.vapeecore-dev/command-highlights/baseline-color-control.log`.

Full canonical regression through the supported offline build/deploy: **173 discovered/executed/passed, 27,064 checks, 0 failures, 0 missing/invalid**. All 173 existing entry points are retained; the color assertion helper is not another executable harness. Maven `clean package` succeeded and the JAR was deployed locally. Deployed/target SHA-256: `5A0BD5911ED28CA581EA4933FE472219B8216103C4328D60F42A26C03D61867B` (1,275,940 bytes).

Receipts are ignored under `dev-server/.vapeecore-dev/command-highlights/`: `focused.json`, per-harness logs, `full-results.json`, `build-deploy.log` and the negative-control log. No translation keys/resources or live YAML were edited. Existing language overrides and custom prefixes remain in place. No real-client color acceptance is claimed from harness execution.

Paper 1.21.11-132 smoke passed: ordinary startup, console status/version and `/coins help`, successful seven-participant reload and clean shutdown, with 28 modules and 0 VapeeCore ERROR/SEVERE. All ten checked operator/data YAML files and both existing language files retained their exact bytes. The server is left stopped for the supported development launcher. Receipts: `paper-smoke.log` and `paper-receipt.json` in the same local directory. This smoke does not claim a visual Minecraft client test.

This follow-up remains uncommitted on the existing branch; the accepted consolidation commit was not amended. No push or merge was performed.
