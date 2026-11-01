# Phase 05A — Staff Panel MVP

Date: 2026-11-01. Base: `5d12cbd6c8300fd83f7521d7c466ac05797c9478` (integrated Phase 04A). Branch: `codex/staff-panel-mvp`.

## Preflight and baseline

Fetched `origin/master` and local `master` both resolved to the base SHA. The sole initial working-tree change was the explicitly approved `.run/Start VapeeCore Dev Server.run.xml` modification. The complete pre-edit canonical verifier passed **219 harnesses / 32,474 checks**, Failed=0, Missing/Invalid=0, Maven clean package PASS and Spotless PASS.

The source inventory contained 37 command roots and no `/staff` command or alias. The new command uses `/staff` plus `/staff list`, avoiding a separate `/team` root and its vanilla scoreboard-team meaning. Native acceptance checks the actual root and namespaced registration in its isolated plugin environment. Other operator-installed plugins are not treated as mutable test fixtures.

## Workflow and layout

- `/staff`: private, paginated online-staff roster. HIDDEN roster members remain visible here to authorized staff, subject to the viewer's existing Bukkit `canSee` result.
- Bottom filter: switch between online staff and visible online players for whom at least one supported action is currently allowed.
- Select an entry: open its contextual action view. Heads show actual usernames with existing primary-rank colors and a rank lore line.
- Own head/status control: toggle PUBLIC/HIDDEN. The lore explicitly explains that this affects only the public team list and is not vanish.
- `/staff list`: small public chat roster, usable without panel permission. It contains only currently online, eligible, PUBLIC staff that a Player caller can already see. No hidden count, internal status or moderation data is disclosed. Tab completion offers only `list`.

Both screens use fixed **27-slot** `MenuLayout` definitions. Zero-based slots:

| Screen | Content and actions | Operational/navigation row |
|---|---|---|
| Roster | Player heads 0–17, empty marker 13 when there are no entries | Previous 18, filter 19, refresh 20, own status 22, close 24, page info 25, next 26 |
| Selected target | Target head 4; teleport 10, history 11, warn 12, mute 14, kick 15 | Back 18, own status 22, close 26 |

Missing capabilities leave their predefined action slot empty; remaining actions never move into arbitrary gaps. Both navigation and action execution are deferred from inventory clicks. The list is alphabetical by actual username, case-insensitively, with UUID as deterministic tie-breaker. Pagination clamps when online entries change.

`CachedPlayerHeads` is reused without modification: authoritative UUID, available online profile or Paper cache texture, otherwise a normal PLAYER_HEAD fallback with UUID presentation tag. Profiles and PDC do not authorize clicks. There is no new download path, blocking profile completion, async inventory result or persistent skin cache. Actual Minecraft-client texture appearance remains a manual check when a genuine profile texture is available.

## Identity, permission and visibility model

Staff eligibility is the player's **current loaded primary LuckPerms group** appearing in the existing `staff.hierarchy.protected-groups` list. `StaffHierarchyService.isStaff` reads the same configuration source used by staff-target protection; it adds no role store. The existing configured defaults include builder/moderator/admin/owner. Custom configured groups work through the same rule. An inherited staff group that is not the actual primary group does not independently establish roster eligibility.

| Boundary | Current authorization |
|---|---|
| Private panel and own status toggle | `vapeecore.staff.panel` **and** current staff eligibility; online exact viewer session |
| New panel node | Explicit conservative `default: false`, no children; no automatic moderation, teleport, OP or hierarchy bypass |
| Public `/staff list` | No permission gate; current eligible PUBLIC online staff only |
| Teleport | Existing `vapeecore.utility.teleport`; different live visible destination; the original executor checks actor/self-source and activity/teleport policy |
| History | Existing `vapeecore.moderation.history`; current target hierarchy; own history allowed |
| Warn / mute / kick | Their existing independent `vapeecore.moderation.*` capability, other live target and current staff-target hierarchy |

OP, name, display color, public rank weight and the panel node alone never establish staff eligibility. Conversely, eligible staff may appear in the public roster without being granted private-panel access. The operator must explicitly grant the panel node to staff who should use it. `docs/PERMISSIONS.md` now inventories 38 roots and 52 nodes: 11 default-true, 40 default-op, one default-false; the existing fifteen child edges are unchanged.

PUBLIC/HIDDEN is **session-only**. New sessions start PUBLIC; logout and module disable discard status. HIDDEN remains meaningful if only panel capability is revoked during that session, preventing permission revocation from unexpectedly publishing the member. A primary-group change immediately affects eligibility when the roster is next evaluated. Neither toggle changes LuckPerms data, game mode, flight, global online state, tablist, friends/clans, public profile policy or an external vanish plugin. The new lists respect existing `canSee`; they never call `hidePlayer`/`showPlayer`.

## Action behavior and existing policies

Teleport and history execute the actual owning plugin's currently type-checked PluginCommand executor with the original **Player sender**. A foreign/replaced executor is not invoked. Teleport retains existing self-source semantics: teleporting the actor to a protected staff destination is allowed when the actor has the existing capability; it does not change the destination player's state. Existing activity and cancellation rules remain in the executor.

Warn, mute and kick produce only a localized **SUGGEST_COMMAND** with the canonical target UUID and an empty input suffix. The player supplies reason and, for mute, duration through the existing validated command flow. Opening/clicking the panel records no punishment. Suggestions are not authority tokens: the later real command resolves identity and rechecks capability, hierarchy and its own target rules, including offline support where already implemented. No action runs as console and no moderation business logic is copied into the GUI.

## Ownership, lifecycle and updates

`StaffPanel` and `StaffPanelHolder` are presentation resources of the existing **ModerationModule**. The existing constructor dependencies and composition root stay unchanged: **27 modules / seven reload participants**. No new module, scheduler service, persistent setting or operator configuration is added.

Authorization requires exact viewer UUID/Player session, holder identity, once-bound inventory, active binding and actual current top inventory. Each rendered content/action snapshot is immutable. A queued action rechecks the exact binding, unchanged snapshot, runtime, current effective permission and original target session. A replacement/stale page, close, logout, reconnect or disabled module makes old work inert. A close hook cannot remove authority checks before the final command delegation.

All StaffPanel-holder click/Creative/drag routes are cancelled. Only an uncancelled ordinary left PICKUP_ALL/NOTHING on the current top inventory can enqueue an action. Shift, right, number-key, offhand, double-click/collect, drop, Creative injection, bottom and outside routes do not navigate or execute. Drag and hand/drop events remain read-only. New inventory transitions run on the next tick, following Paper's [InventoryClickEvent contract](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryClickEvent.html); [SkullMeta profile integration](https://jd.papermc.io/paper/1.21.11/org/bukkit/inventory/meta/SkullMeta.html) preserves already available textures.

Open/refresh, join/quit and status changes compute current list content. The existing locale observer updates items in the same inventory; titles change on the next open. World changes revalidate the viewer on the next tick. There is no LP polling job: a silent permission/group change is enforced on the next click/drag, refresh or those lifecycle events. Such interactions cannot execute with stale authority; revoked panel access closes its owned view safely on the next tick. Already displayed client data cannot be retroactively undisclosed. Blocked views require a new authorized open.

Module enable acquires the new command, listener and locale observer before publishing active state. Partial-start failure rolls them back. Disable deactivates command/runtime, closes only still-owned views, clears session status and removes only its locale observer; independent cleanup continues after a failure. Existing moderation-only test seams remain compatible; the new lifecycle harness separately exercises actual staff acquisition and rollback.

## Verification

Final complete command: `pwsh -File .\scripts\verify.ps1` — **Maven clean package PASS; Spotless PASS; Discovered=221, Executed=221, Passed=221, Failed=0, Missing/Invalid=0; 32,788 checks**. All **219 original harness identities** remain present and passing.

The +314 checks over baseline comprise 124 new panel checks, 51 new module/command integration checks, +93 catalog-usage checks, +31 localization checks, +12 descriptor checks and +3 source-discovered GUI audit checks. Other historical per-harness check counts are unchanged, including the existing moderation behavior harness and all Phase 04A controls.

Focused executable tests:

- `StaffPanelHarness`: **124 checks** covering explicit access, primary-group/config eligibility, OP/color/weight negative controls, PUBLIC/HIDDEN/privacy, typed ItemStack/SkullMeta/profile identity at controlled Paper boundaries, online/cache/fallback profiles, localization and fixed positions; every click/action class, mixed drag, forged viewer/holder/profile, stale pages, per-capability and contextual revocation, hierarchy changes, original target session, open/close hooks, quit/reconnect, shutdown and replacement inventories. Genuine CraftItemStack/SkullMeta behavior is checked separately in native acceptance.
- `StaffPanelLifecycleHarness`: **51 checks** using actual ModerationModule acquisition/rollback and existing History/Warn/Mute executors. It verifies real player actor identity, delayed suggestion permission/hierarchy denials, observer ownership, half-installed command/listener failure, locale-subscription failure, cleanup diagnostics, retry and stale runtime fencing.
- Existing descriptor/command inventories explicitly include the new 38th root and 52nd permission. Existing moderation nodes/defaults, all earlier harness identities and unrelated behavior remain covered.

The first post-edit full run detected a dynamic partial translation key and a pre-existing 37-root expectation in ModerationCommandHarness. Production captions now use five explicit catalog IDs; the root-count assertion precisely includes `/staff`. Neither harness was disabled, weakened or bypassed. The baseline itself was clean.

## Disposable native acceptance

Final run `native-02`: **Paper 1.21.11 build 132, Java 21.0.12.1, LuckPerms 5.5.84 — PASS**. The tested JAR matches the canonical verifier artifact byte-for-byte: SHA-256 `B475518D92F925AF842C0572613367BA0F847D5609900796E02C52E269C218BD`.

Results: **115 server assertions**, **seven connected-client assertions**, 59 acknowledged fixture steps; **29 actual network inventory clicks, all cancelled**, one cancelled native network drag, and two separately marked synthetic native API clicks. The observed number of synchronous inventory open/close transitions during click/drag handling was **zero**. The JVM exited **0**; no ERROR/SEVERE entries occurred, and the root reports that all module shutdown calls returned normally.

| Native boundary | Evidence/result |
|---|---|
| Command access | Ordinary default-group client denied; same client still denied after granting only panel node; eligible non-OP admin/moderator admitted. Root and namespaced staff command resolve to VapeeCore. |
| Roster visibility | Actual user client receives public list excluding HIDDEN admin and nonstaff user. Private moderator view retains hidden admin. Toggle keeps actual `canSee`, role, capability, OP and player inventory unchanged. |
| Heads/layout | Actual Paper ItemStack/SkullMeta contains authoritative UUID and available online profile texture in staff entries; textureless user target uses plain fallback. Fixed roster/target slots verified. Textures are prefilled fixture profiles, not a visual Mojang skin-rendering claim. |
| Read-only GUI | Connected shift/number/offhand/collect/drop/right-click routes preserve inventory and cursor; controlled mixed top/bottom drag preserves exact emerald quantities and destinations. |
| Retained authority | Actual LP node revocation, contextual node loss and primary-group loss deny next interaction and close the owned view. A synthetic native click followed by same-tick revocation also cannot execute its queued status action. |
| Hierarchy/actions | Actual target promotion to Owner hides history/punitive actions and creates no moderation record. Self-source teleport remains available. History executes read-only; teleport moves only actor; suggested warn/mute/kick are confirmed as SUGGEST_COMMAND in received client components and later run through real commands with PLAYER actor UUID. Warn submission after capability revocation is denied. |
| Lifecycle | Stale native close preserves replacement panel; pending click cannot mutate status or close unrelated replacement inventory; actual viewer disconnect/reconnect resets session visibility; actual ModerationModule disable/re-enable releases both owned panels, fences old executor and reloads existing moderation records. |

The native fixture uses newly generated server/world/player/LP data, three non-OP offline protocol clients and loopback port 25609. The test helper refuses to run outside its dedicated evidence server directory. Native API events and actual connected-client packets are labelled separately. This is not physical Minecraft-client or visual skin acceptance.

The initial helper compilation used an unavailable LP Group display setter; it was corrected before any first-attempt server start. Failed-attempt logs remain with the evidence; only a complete successful run is acceptance.

## Manual client checklist

- Genuine online account skin rendering on own/member/target heads; cache/fallback appearance when a texture is unavailable.
- Readability of both 27-slot screens, rank colors, grey lore, empty states and pagination with more than eighteen visible staff.
- PUBLIC/HIDDEN status explanation, localized EN/DE text and cursor/closing animations.
- Click a warn/mute/kick suggestion, enter reason/duration and observe the existing command feedback.
- Test with the deployment's actual external vanish plugin: existing `canSee` behavior is respected; no global vanish policy is provided.

## Protected files and exact paths

Read-only before/after snapshots hashed **952 files** under `dev-server`, `.run` and `scripts`: all files were readable at both observations, with **zero added/removed/hash-changed or locked/unreadable entries**. This compares observed snapshots; it does not assert that an operator server could never write between observations. No operator data or process was modified or stopped. Only immutable server binaries/libraries were copied into the disposable test directories.

The approved Run file's Git diff compares equal and its byte hash is unchanged. The tracked-file manifest detects only the nine intended existing-file changes; the six new intended files appear below. No unrelated changes were found. The native server ended normally; both disposable server trees (one never started after helper compilation failed) were removed after validating absolute paths, stopped PIDs where present and absence of reparse points. Sources, final tested JAR, logs and receipts remain available.

Run SHA-256 before/after: `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Its already approved diff stays local and is excluded from staging/commit.

Exact committed paths (explicit staging; approved Run file excluded):

```text
docs/DEVELOPER_GUIDE.md
docs/PERMISSIONS.md
reports/prototype/PHASE_05A_STAFF_PANEL_MVP.md
src/main/java/dev/vapee/core/moderation/ModerationModule.java
src/main/java/dev/vapee/core/rank/staff/StaffHierarchyService.java
src/main/java/dev/vapee/core/staff/StaffPanel.java
src/main/java/dev/vapee/core/staff/StaffPanelHolder.java
src/main/resources/lang/de.yml
src/main/resources/lang/en.yml
src/main/resources/plugin.yml
src/test/java/dev/vapee/core/moderation/StaffPanelLifecycleHarness.java
src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java
src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java
src/test/java/dev/vapee/core/staff/StaffPanelHarness.java
src/test/java/dev/vapee/core/staff/StaffPanelTestSupport.java
```

Evidence: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/phase05a-staff-panel-20261101/`. Includes preflight/manifests, baseline/final receipts, Run/protection comparisons, native helper/client sources, actual tested JAR hash, server/event logs and client transcript. Native tooling is not part of the shipped plugin.

Delivery: exactly one local commit, `Add secure Staff Panel MVP`; full SHA is reported with delivery. **Push: no. Merge: no. Operator deployment: no.** No vanish, staff chat, reports, surveillance, punishment redesign or unrelated module fixes are included.
