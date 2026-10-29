# Prototype Step 03 — Navigator V2

Completed 2026-10-29 on `prototype/navigator-v2`. Exactly one local commit uses the message `Implement configurable Navigator V2 with submenus and local player counts`; the full SHA is supplied in the completion message and external post-commit receipt. No push, merge or deployment.

Baseline: `ec7d163a1b8879f2618b120726f23fd181a03981`, integrated master including Step 02 and the CRLF-safe verification repair. Fresh baseline: 206 harnesses, 31,166 checks, zero failures/missing results, Maven and Spotless PASS. Branch: `prototype/navigator-v2`.

## Configuration and compatibility contract

`plugins/VapeeCore/navigator.yml` is copied from the bundled disabled default only when absent. It is read at startup and is not a ReloadService participant or ConfigEvolution file. Restart after edits; `/core reload` still has the same seven participants. Existing operator files are never rewritten. The Core still has 27 modules.

An absent file creates the disabled default. An absent/false `enabled` flag in valid YAML retains the existing flat, paginated Warp Navigator and its existing WarpNavigation semantics. Malformed YAML or invalid active configuration fails closed to a safe empty Navigator with a detailed log, retaining the exact file. It must not expose previously gated destinations through a legacy fallback. Deliberately disabling V2 deliberately restores the old flat view; V2 entry permissions do not change WarpService or administrative Warp authorization. Review globally navigable warps before switching back to legacy.

V2 extends the existing NavigatorMenu, NavigatorInventoryHolder, NavigatorListener, access policy and active inventory registry. It does not create a parallel menu runtime. Warps remain authoritative by stable ID; Navigator stores no coordinates and creates no warps.

## Full example

```yaml
config-version: 1
enabled: true
root: root
menus:
  root:
    title: {en: Navigator, de: Navigator}
    entries:
      spawn:
        type: WARP
        slot: 20
        icon: COMPASS
        name: {en: Spawn, de: Spawn}
        warp: spawn
      activities:
        type: MENU
        slot: 22
        icon: NETHER_STAR
        name: {en: Activities, de: Aktivitäten}
        menu: activities
      builder:
        type: UNAVAILABLE
        slot: 24
        icon: DIAMOND_PICKAXE
        name: {en: Builder, de: Bauserver}
        permission: vapeecore.navigator.builder
  activities:
    title: {en: Activities, de: Aktivitäten}
    entries:
      parkour:
        type: WARP
        slot: 20
        icon: FEATHER
        name: Parkour
        lore:
          - {en: Local parkour course., de: Lokaler Parkour.}
        warp: parkour
        count:
          world: your_real_world
          min: {x: 0, y: 60, z: 0}
          max: {x: 20, y: 100, z: 20}
          stack-amount: true
      another_page:
        type: UNAVAILABLE
        page: 1
        slot: 20
        icon: GRAY_DYE
        name: {en: Future activity, de: Zukünftige Aktivität}
```

This example does not create spawn/parkour destinations. Configure existing local warps deliberately. Builder can later use `type: WARP` plus `warp: <existing-local-id>` instead of UNAVAILABLE; keep its permission. There is no remote server transfer or status query.

## Schema and validation

- Entry types: WARP (required `warp`), MENU (required `menu`), UNAVAILABLE (no target). No command execution entry type exists.
- IDs match `[a-z0-9][a-z0-9_-]{0,63}`. Warp targets use existing WarpPoint ID validation. Permission nodes are lowercase letters/numbers/dots/underscores/hyphens, without whitespace/wildcards.
- Each menu has a localized `title` and an `entries` mapping. Each entry needs `type`, `slot`, `icon`, `name`; optional `page`, `lore`, `permission`, and a WARP-only `count` section.
- Titles/names/lore are literal strings or existing LocalizedText EN/DE maps with English fallback. Lore is a list of those values; blank lore lines are allowed. Neither player text nor these config strings are interpreted as MiniMessage commands.
- V2 inventories are 54 slots. Content uses 0–44; the complete bottom row is reserved (Previous 45, Back 48, Page 49, Close 50, Next 53). Duplicate YAML keys and duplicate page/slot placements, unknown fields/types, invalid materials/ranges/references and all menu cycles are rejected.
- `page` defaults to 0, range 0–999. Configured page groups sort numerically; groups with no permitted entries are omitted, preserving the configured slot within each visible group. Back restores the actual parent and its page, including when menus have several possible parents. Empty menus have a compact empty state and Close/Back where applicable.
- Limits are 128 menus and 4096 entries per menu. Config version is 1. Disabled composition is not interpreted; malformed YAML or invalid active configuration yields the safe unavailable view and a startup warning, not a partial menu.

## Permissions, authority and lifecycle

`vapeecore.navigator.builder` is declared with default `op`, without children. Production checks Bukkit permissions, never LuckPerms group names. Gated entries are omitted before pagination/rendering. Click-time checks cover the selected entry and every permission-bearing MENU link on its actual breadcrumb path. Denial reports a generic unavailable message, without target IDs or destination names.

Owner UUID, exact bound inventory/holder, current top inventory, active registry, online/loaded/lobby/NORMAL/no-Activity eligibility remain required. Shift/number/offhand/drop/extraction and unsafe drags remain blocked. Old pages and forged holders cannot act; late close of an old view cannot remove a newer view. New active configuration snapshots invalidate old actions before execution.

WARP actions call WarpService.teleportFromNavigator, rechecking current existence, navigability, loaded world and teleport result. Success closes the menu and keeps existing movement reset behavior. Missing targets get localized Navigator feedback; missing worlds and teleport failures retain existing Warp feedback. MENU and UNAVAILABLE never dispatch a command or transfer a player.

Existing committed Warp changes and locale observers refresh active views; their inventory/holder identity remains stable during an ordinary refresh. Config replacement opens a new view. As before, native inventory titles change on the next open, while names/lore/buttons refresh in place. Quit/world leave and the existing LobbyExperience rollback/disable release owned inventories and listeners. No new polling task or reload participant exists.

## Local player count semantics

Only a configured WARP `count` enables a counter. World names follow existing local Warp conventions; no world is auto-loaded. Bounds are finite doubles, normalized per axis and inclusive. Count only online players in that actual world and cuboid, deduplicated by UUID. Paper viewer.canSee, invisibility and conventional truthy `vanished`/`vanish` metadata exclude hidden targets, including staff-visible vanish. No offline lookup, profile scan, WorldGuard, remote population query or persistent cache is used. Other plugins must expose visibility through Paper or these metadata conventions; arbitrary proprietary vanish state cannot be inferred.

Counts are computed on menu open, page/back navigation or the existing explicit view refresh (for example committed Warp/locale updates). There is no high-frequency refresh task. Reopen to see population changes. Missing count configuration omits the count entirely. Unavailable Warps do not display population figures. Zero uses localized `0 players` lore and a visible amount 1; optional stack amount is clamped to 1–64 and the actual item's maximum, while lore retains the full measured number. EN singular/plural and DE text use the existing catalog with gray body and green count.

## Verification and native acceptance

Full canonical command: `pwsh -File .\scripts\verify.ps1`. Fresh pre-edit baseline: **206 harnesses / 31,166 checks**, Maven and Spotless PASS. Final: **209 discovered/executed/passed harnesses / 31,554 checks**, **Failed=0, Missing/Invalid=0**, Maven clean package PASS, Spotless six-file pilot PASS and git diff --check PASS. All 206 original harness identities remain; no harness was removed or bypassed. Source assertions were not added. The CRLF-safe Step-02 removal verifier remains unchanged and passes.

New executable harnesses:

| Harness | Checks | Behavior |
|---|---:|---|
| NavigatorConfigHarness | 43 | Bundled default/opt-in, strict schema, LF/CRLF, immutable state, invalid types/materials/slots/duplicates/refs/cycles/regions, exact file retention and safe fallback |
| NavigatorRegionHarness | 28 | Zero/one/many, inclusive normalized bounds, world/offline/privacy/vanish/invisibility filters, UUID deduplication and numeric/material stack limits |
| NavigatorV2Harness | 62 | Root/child/deeper menus, real parent page, configured pagination, local Warp execution, removed/hidden/world/cancel results, Builder visibility/revocation, parent permission gates, config changes, stale/forged holders, unsafe clicks/drags, zero/one/many count lore/amount, EN/DE, literal text and lifecycle |

The original NavigatorSecurityHarness is byte-for-byte unchanged in Git and passes **78 checks**. Existing flat NavigatorMenu (113), layout (36), composition (37), pagination/update (72) and locale (8) suites also pass. Module lifecycle coverage retains all original cases and adds enabled-V2 failure/rollback/retry/disable stages. Its Paper fixture now supplies its own disposable plugin data folder and cleans that folder; production startup is not bypassed.

Count changes in retained harnesses:

| Harness | Before | After | Delta |
|---|---:|---:|---:|
| `dev.vapee.core.i18n.CatalogUsageHarness` | 2,706 | 2,735 | +29 |
| `dev.vapee.core.i18n.LocalizationHarness` | 782 | 788 | +6 |
| `dev.vapee.core.lobby.experience.LobbyExperienceLifecycleHarness` | 340 | 548 | +208 |
| `dev.vapee.core.permission.PermissionDescriptorHarness` | 848 | 855 | +7 |
| `dev.vapee.core.repository.RepoLinkHarness` | 94 | 96 | +2 |
| `dev.vapee.core.ui.GuiAuthoringAuditHarness` | 242 | 245 | +3 |

Count equation: 31,166 + 133 new-harness checks + 255 additional existing-harness/audit checks = **31,554**. There are no placeholder or invalid receipts.

### Disposable native Paper acceptance

**Paper 1.21.11 build 132, LuckPerms 5.5.84, Java 21.0.12.1**, two independent JVMs, fresh worlds and disposable profiles/configuration. Main/API checks: **273 + 89 = 362**; after-stop checks: **4 + 4 = 8**; total **370 PASS**. Both processes exit 0 after normal console `/stop`, all 27 module shutdown calls return normally and both clean final logs contain **zero ERROR/SEVERE**. Runtime tokens differ, proving separate JVMs.

Tested final canonical JAR SHA-256: `236D65E0B6CC6CD1765228E1E570D0896B6025E6E7BF7122F97DD6F1B92802AA`, matching target and preserved tested artifact.

The registered LobbyItemListener consumes an actual native Compass ItemStack with the configured `navigator` PDC and opens the actual LobbyExperience-owned V2 menu. Tests execute real Inventory/SkullMeta/ItemStack objects and registered Navigator listener paths: root/child/Back/pagination, placeholders, native WarpService targets/close, stale clicks, Builder visibility and permission revocation/recovery, actual `/warp hide/show/remove` command updates, native world/Location boundaries, 0/1/71 count lore and amount 64, visibility/vanish/wrong-world filters and German controls. Settings opens through the existing actual SettingsMenu. The retained native social suite also exercises Friend/Clan UUID chat actions, rank styling, Ignore/privacy, Join Summary settings and player heads. Existing generic Activity/Seat/WorldDisplay, Reward/Quest/Daily persistence checks remain.

Native players have synthetic transport/permission/online-collection boundaries; no Minecraft client connected. Native counts first verify the actual empty server pool, then inject only the online-player collection for positive region/privacy cases. Teleport targets and accepted calls are asserted at the transport boundary, not as physical network-client movement. Vanish/visibility responses are controlled, while world/Location/metadata/item objects are real. Native menu lookup is supplied for those synthetic UUIDs so the real module can close their views during shutdown.

After-stop receipts confirm all four independent outcomes: actor inventory closed, Navigator active registry empty, Navigator listener handlers absent, ModuleManager empty with an all-normal shutdown report. Cold restart retains exact operator Navigator YAML, local Warp admin state, friends/clans/settings and quest/reward data without replay. Seven ReloadService participants still prepare/apply successfully; navigator.yml is not reloaded or rewritten.

Two exploratory runs completed their main checks but their additional test-helper shutdown receipt could not lazily resolve ShutdownReport after Paper closed the helper classloader. The final helper resolves that type before closing; no production change was made to accommodate the fixture. Failed logs are retained as evidence. Final two-JVM acceptance above is clean. This is API/runtime evidence, not real-client acceptance.

## Operator data and Git protection

Preflight confirmed integrated master `ec7d163a1b8879f2618b120726f23fd181a03981`, including removal `00ac19133d7e3aa805ad135388119cf8e4e67b3a`, merge `fb09a1605b0e4aeda5d6a9c497816e51f171515a` and verification repair. No applicable AGENTS.md was found. Existing master/history/stashes were not rewritten. Only the approved Run change existed before work.

Read-only before/after protection inventories record path, size, SHA-256 and errors. **918 operator-server files before/after; 918 comparable and identical; 0 locked/unreadable, 0 externally changed, no added/removed files at these measurement points.** This describes measured snapshots, not a promise that an independently running server could never change between reads. All **24 scripts and two Run files** also retain their exact original bytes. No operator server was stopped/restarted, used as a mutable fixture or deployed to. Player files/balances/relationships/quests, worlds, LP data, recovery evidence and existing Blackjack table data were not modified. The native environments copy only immutable Paper/runtime/JAR dependencies, then use their own worlds and configuration.

`.run/Start VapeeCore Dev Server.run.xml` remains the sole approved unrelated local modification. Full Git diff and SHA-256 `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F` match before/after. It is never rewritten, reverted, staged or committed. No unexpected local source changes were found. No blanket git add, reset, clean, rebase, stash operation, previous commit amendment, push, merge or deployment occurred.

External evidence: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/prototype-step03-navigator-v2-20261029/`. It retains baseline/final receipts/logs, focused logs, count ledger, native helper/source/JARs, native receipts and copied disposable plugin data, tested Core artifact, Run diff/hash, operator inventories, exact path allowlist and post-commit receipt. Disposable server trees are removed only after confirmed JVM exits and preserving evidence.

## Exact commit paths

No production file is deleted. WarpService/WarpConfig/Warp commands, the lobby item registry/hotbar logic, Core composition/reload list, Player/Reward/Quest schemas and Blackjack removal remain unchanged. Only the intended paths below are explicitly staged.

### Production

- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorConfig.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorInventoryHolder.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegion.java`

### Tests

- `src/test/java/dev/vapee/core/lobby/experience/LobbyExperienceLifecycleHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorConfigHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegionHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorV2Harness.java`
- `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java`

### Resources

- `src/main/resources/lang/de.yml`
- `src/main/resources/lang/en.yml`
- `src/main/resources/navigator.yml`
- `src/main/resources/plugin.yml`

### Documentation/report

- `docs/CONFIG_DEVELOPMENT.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/PERMISSIONS.md`
- `reports/prototype/STEP_03_NAVIGATOR_V2.md`



## Manual Minecraft-client checklist

- Open the configured lobby Compass; confirm readable main/Activities titles, icons, lore, pages, Back and Close in EN/DE.
- Test actual local teleports, cancellation, removed/hidden warps and missing worlds; inspect same-world and world-leave cleanup.
- Grant/revoke the dedicated Builder permission with real LuckPerms players; check hidden entries and old inventory clicks.
- Put real online players on both sides and exactly on count boundaries, including hidden/vanished users; check 0/1/many counts and stack limits after reopening.
- Confirm Friends/Clan/Settings/Social and the configurable hotbar remain usable, then stop/restart the disposable server with the same operator YAML.

Native API/transport tests are not physical client acceptance. No Staff Panel, Extras, TPA, Housing, proxy messaging, Player#transfer or network infrastructure is part of this step.

## Deferred scope and recommendation

Builder is a permission-gated local Warp or an unavailable placeholder. Velocity/BungeeCord, remote population/status, plugin messaging, Paper transfer, a network API and new persistence/lifecycle infrastructure are absent. These require a separately defined future phase.

Recommended next step: perform the manual Minecraft-client checklist on an explicitly approved disposable installation and tune real operator menu/region values before selecting another feature phase. Staff Panel, Extras, TPA and Housing have not been started.
