# Phase 35 – Live Client Correction

## Scope and acceptance

Follow-up to `44969da2cc3cecfbe646606fc522160e9db1c43b`, 2026-10-06. The first real client
reported Paper's RESET color-read exception and clarified that rank colors,
not automatic `[ADMIN]`/rank labels, are the desired public rank presentation.
This report supersedes the original report's color and prefix-default decisions.

Implementation, automated regression, official deployment and repeated real
Paper smoke passed. **Post-correction visual client acceptance passed by operator
confirmation on 2026-10-06:** "Alle genannten Punkte geprüft und korrekt" in
response to the complete checklist below. The agent did not independently
observe client pixels; server smoke and API fixtures alone cannot establish
actual client rendering.
No agent push or merge. Before this correction, the original Phase-35 commit
had already been merged into `master` at 19:49:13, producing
`cda365c06ea1669b51ef37aa5202873a6b9201fb`, and its branch had been deleted.
The merge tree is identical to the original Phase-35 tree. This was discovered
by the guarded pre-commit check; it should have been rechecked before edits.
No Git mutation occurred in the failed guard. The operator explicitly chose
"Die Korrektur direkt auf master committen", superseding the earlier branch-only
restriction for this correction. Delivery is one local follow-up commit on
`master`, based on `cda365c06ea1669b51ef37aa5202873a6b9201fb`; no new branch,
worktree, merge, rebase or push by the agent.
Badges/resource packs and Phase 36 remain outside this fix.

## Final behavior

`RankService.getPrimaryRank()` supplies the same `RankInfo.effectiveColor()`
used by chat and tablist. The renderer resolves the primary rank once per player
refresh and carries a `NamedTextColor` through `RenderedPresentation` and
`NametagService.Target`. No group-to-color map, new scheduler or rank cache.

The native team is semantically: optional prefix (empty by default), exact rank
team color, actual Vanilla player-name entry, conditional clan suffix.
The tablist default is `<rank_name><clan_tag_display>`. Explicit operator
`<prefix>` templates remain supported; LuckPerms prefix metadata alone no
longer inserts a label into either default. Raw `<clan_tag>` stays a literal
Component; `<clan_tag_display>` is empty for missing/invalid clan tags and
otherwise applies the configured wrapper. No empty brackets or injected styling.

Paper's `Team.color(NamedTextColor)` accepts sixteen named values. The renderer
compares exact RGB integers against those sixteen colors, including colors
parsed from hex syntax. It never calls nearest-color approximation. A color
that has no exact native representation deliberately becomes **WHITE** overhead
and logs one warning per distinct RGB value per renderer lifetime. Chat and
tablist keep their RGB values. Missing/invalid rank metadata retains the
existing neutral white RankInfo fallback.

### Actual dev-server rank colors

Audited a copy of the stopped LuckPerms H2 database opened with
`ACCESS_MODE_DATA=r`, using the installed provider's connection semantics.
The original database and rank/group metadata were not edited. All six stored
color nodes have global server/world scope and empty context maps:

| Group | Stored `vapeecore.rank.color` | Native result |
|---|---|---|
| default | gray | GRAY, exact |
| vip | gold | GOLD, exact |
| builder | green | GREEN, exact |
| moderator | dark_green | DARK_GREEN, exact |
| admin | red | RED, exact |
| owner | dark_red | DARK_RED, exact |
| supporter | absent, no group inheritance | Existing neutral WHITE fallback |

The example VIP=blue did not replace the operator's actual VIP=gold metadata.
Future arbitrary RGB ranks use the explicitly documented fallback above.
Evidence: ignored `phase35-live-lp-groups.log`, `phase35-live-lp-colors.log`
and `phase35-live-lp-inheritance.log` under `dev-server/.vapeecore-dev/`.

## Safe color ownership

Fresh native teams have RESET, for which `hasColor()` is false and `color()`
throws `IllegalStateException: Team colors must have hex values`. Initial
capture, post-write capture and ownership comparison use the same guarded read:
`team.hasColor() ? team.color() : null`. Null is a valid initial/foreign RESET
state. The mutable snapshot records the color after each attempted write;
unchanged successful writes are skipped.

All existing team identity, exact board, membership, prefix/suffix, display name,
options and friendly-fire checks remain. A foreign color change, including
resetting to no usable color, relinquishes the relation. Refresh does not fight
the new owner; cleanup does not unregister its team. Rank-color changes on a
still-owned team update it in place. TablistService and its guarded restore
ownership are unchanged; shared scoreboard ownership/visibility gates remain.

## Chat and development workflow

Chat already resolves the same RankService/RankInfo color through `<rank_name>`.
Both resource and live `chat.yml` use this path, without a rank label or clan
tag. Chat production code/configuration were left unchanged. The existing
configured ` » ` separator was retained; this fix does not redesign chat.

Both shared IntelliJ run configurations now pass the installed full JDK via
`JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1`. This carries forward the preceding
build fix: Oracle's PATH launcher lacked adjacent `javap`, preventing canonical
harness discovery even when Maven succeeded. Official scripts/test gates are
unchanged. This local installation path must be adjusted on another machine.

The ignored live `plugins/VapeeCore/presentation.yml` received exactly one
template change: `<rank_name>` to `<rank_name><clan_tag_display>` for tablist.
The original file is backed up under `phase35-live-presentation-before.yml`.
Missing nametag keys use the revised defaults. No automatic live-file migration
was added. All ten YAML files remain byte-identical across the subsequent smoke.

## Regression and negative controls

The fixture now models Paper's RESET read failure instead of returning null from
an unsafe read. New production-code `NametagColorHarness` has **34 checks**:
fresh RESET capture and BLUE write; no labels/no clan; stable no-op refresh;
same primary group BLUE→RED on the same team; literal clan/leave; exact hex and
all sixteen native colors; nonrepresentable RGB→white with one warning and RGB
tablist preservation; return from RGB to named color; missing rank; owned
cleanup; foreign GREEN/RESET takeover with and without intervening refresh.
Existing config/default and ownership/rendering assertions were updated to the
new product contract; configured optional prefixes retain their coverage.

Four isolated negative variants compiled and each failed for its intended cause:

| Variant | Detection |
|---|---|
| Restore direct unsafe `team.color()` read | Fresh-team assertion with the exact Paper IllegalStateException as cause |
| Remove team color write | Fresh BLUE/native membership assertion fails |
| Force a fixed BLUE instead of RankInfo color | Same-group BLUE→RED assertion fails |
| Render clan wrapper unconditionally | Fresh no-clan suffix assertion fails |

Variants never overwrote repository production sources or canonical classes.
The validated temporary directory was removed; the clean positive control
passed afterward. Results/logs: ignored `phase35-live-negative-*` evidence.

* Focused presentation/nametag/rank/chat: **16 harnesses, 553 checks, zero failures**.
* Canonical full runner: **126 discovered/executed/passed; 20,690 checks;
  zero failed and zero missing/invalid**.
* Separate Maven `clean package`: BUILD SUCCESS, 15.917 seconds.
* Official `scripts/build-and-deploy.ps1`: clean stop, BUILD SUCCESS (16.536
  seconds), the same 126/20,690 regression result, successful JAR deployment.
* Final built/deployed JARs have identical SHA-256:
  `D8A113117C99F4BA1C17BAEB8F974497CC01BC61F74151EE261DB80904989169`;
  **1,092,037 bytes** each.

### Real Paper smoke

Java 21.0.12.1, Paper 1.21.11 build 132 and LuckPerms 5.5.84, actual dev world,
official managed start/stop. Verified status/version/help, `/core reload`, all
37 descriptor command roots, six prepared reload configs, 27 enabled modules
and exact reverse disable order. No clients were connected during these smoke
commands. This does not exercise client nametags.

The first smoke (20:26:08–20:26:29) completed all commands and clean shutdown
but failed its zero-error gate: Paper reported two SavedData read errors for
`chunks` and `raids` during world loading. It subsequently wrote nonempty
49-byte/72-byte data files on shutdown. No world files were manually deleted or
rewritten and no world-repair code was added. The original logs/results were
preserved as `phase35-live-first-paper-smoke.log`/`phase35-live-first-smoke-result.json`.
The cause of the first invalid saved data is not established by this audit.

The repeated smoke passed all gates, wrapper exit 0, zero ERROR/SEVERE entries,
successful reload and clean shutdown. The ten active VapeeCore YAML hashes and
exact file set were unchanged. Evidence: `phase35-live-paper-smoke.log`,
`phase35-live-smoke-result.json`, managed console/stderr and before/after receipts.
The deployed JAR remains available; the managed server was left stopped for
the operator's IntelliJ start/client test.

## Completed real client acceptance

The operator confirmed all requested checks on this corrected artifact:

1. Overhead player-name color matches the current LP rank (observe from another
   client; one's own first-person view is insufficient).
2. No undesired `[ADMIN]` or other rank label with the defaults.
3. Clan member displays `Name [CLAN]`; clanless player displays only `Name`.
4. Tablist has the same colored-name/conditional-clan behavior.
5. Join/refresh produces no nametag WARN/ERROR or RESET exception in server log.

The live server log independently records the operator's reconnect, clan
creation, chat and primary-group switches through builder/default/vip/moderator/admin.
At the captured observation it contains zero nametag warnings, zero RESET
exceptions and zero ERROR/SEVERE entries. Evidence:
`phase35-live-client-log-observation.json` and `phase35-live-client-server.log`.
These logs confirm exercised server paths; the visual result comes from the
operator's explicit report. The operator restarted the server after smoke;
the live session is left running.

The correction has not been merged/pushed by the agent; the earlier original
Phase-35 merge is recorded above and is not attributed to this follow-up.
The implementation follow-up is committed locally after automated and operator
client acceptance; its SHA is recorded in the final reply
and ignored commit receipt, rather than attempting a self-referential SHA here.
