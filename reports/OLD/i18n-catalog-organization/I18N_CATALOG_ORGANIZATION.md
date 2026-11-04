# Language catalog organization and developer experience

## Baseline and scope

Started from clean `master` at `796769448b65719d1fff0dda91603dc1e398b2e9` (`Merge Daily Quest Reset UI`), with `651e91793fabf48b11968f45db32df25b061807b` integrated. No concurrent DailyQuest work remained. Created `phase/i18n-catalog-organization` in the existing checkout; no worktree, push, merge, rebase or force update.

Before editing either catalog, parsed both through the actual production `TranslationCatalog.read`, saved every key/value pair and exact original source bytes, and validated complete key/placeholder parity. The real starting count is **933 English / 933 German**, including the reset clock and Core quest administration keys. Nothing is frozen at the historical 922 count.

Measured the existing canonical baseline: **179 harnesses passed / 27,716 checks, zero failures and zero missing/invalid**. The bootstrap snapshot helper was already present during this run: `GuiAuthoringAuditHarness` counts each Java test source, so it recorded 210 rather than the previous phase's 209 checks. This accounts for the single check above that phase's 27,715 receipt; no existing production, catalog or test behavior was changed for baseline measurement. Original-source copies, complete lossless semantic snapshots and baseline receipts are retained in the ignored `dev-server/.vapeecore-dev/i18n-catalog-organization/` directory.

Scope is the two resource catalogs, maintained documentation and explicit developer validation/adoption support. **No production Java, localization API, command, permission, quest behavior/layout, NPC, producer, style or dependency changes.** Historical reports remain unchanged.

## Organization

Previously the resources were mostly one alphabetical list of dotted keys with later features appended at the bottom. Now both languages share exactly the same feature sections, comments, subsection sequence, hierarchy and leaf ordering:

1. Common
2. Core / Help / Administration
3. Lobby and Spawn
4. Player Menu
5. Navigator and Warps
6. Visibility and Visible Players
7. Player Settings
8. Identity and Profile
9. Ranks
10. Social / Ignore
11. Friends
12. Clans
13. Private Messages
14. Economy and Rewards
15. Daily Quests
16. Activities and Blackjack
17. Moderation
18. Utility and Teleportation
19. Presentation

Only the existing `lang/en.yml` and `lang/de.yml` are canonical. Existing namespaces remain distinct; no `warp`→`navigator` or `clan`/`clans` rename. New Core force/help texts, lobby/player-menu texts and visibility modes are in their owning sections rather than appended.

Feature-local comments group navigation, actions, states, requests/notifications, administration, help and errors. DailyQuest has General/Commands, Progress, Rewards, Status, Reset Countdown and Errors. Blackjack has Actions, Gameplay Hints, Round Messages/States, Results, Joining, Physical Table Setup, Preview, Table Administration, Information, Help and Validation. No empty decorative categories.

Resources use UTF-8, two-space indentation, no tabs and the original double-quoted scalar representations. Nesting stops after three section levels (maximum six-space scalar indentation); longer suffixes stay dotted. This permits IDE folding without deeply nested command-help paths. Each original quoted value was carried intact, including MiniMessage, escaped characters, Unicode, punctuation and intentional leading/trailing spaces. The semantic comparison below is authoritative.

## Scalar/section collisions

The complete actual baseline has five scalar keys with descendants:

| Scalar key | Existing descendants |
|---|---|
| `blackjack.info.join-mode` | `.invalid`, `.legacy`, `.modern` |
| `clan.owner` | `.transferred` |
| `clan.role` | `.member`, `.owner` |
| `common.player-only` | `.message` |
| `common.profile-unavailable` | `.message` |

Each scalar and its dotted descendants remain separate leaves at the appropriate section, e.g. `clan: {owner: "…", owner.transferred: "…"}`. No key becomes both a string and a YAML map. Production flattening still returns every original runtime key. Duplicate YAML keys and duplicate flattened keys are rejected by the unchanged production reader.

## Exact semantic comparison

| Metric | EN | DE |
|---|---:|---:|
| Keys before | 933 | 933 |
| Keys after | 933 | 933 |
| Missing | 0 | 0 |
| Added | 0 | 0 |
| Changed parsed string values | 0 | 0 |
| Duplicate YAML / flattened keys | 0 | 0 |
| Placeholder mismatches | 0 | 0 |

Deterministic SHA-256 fingerprints are identical before/after:

- EN: `3f53841a19b36b901a4b46a4f436908e32f516a32d5a43dc9f1787908da7cdb3`
- DE: `b2b8eb0386535b9ea2b66915d6338af3411c81710312d0dbe4ae0e0b6f1cf2ec`

Fingerprint input is a Java `TreeMap` ordered stream of UTF-8 Base64(key), TAB, UTF-8 Base64(value), LF, hashed as UTF-8. Base64 makes newlines, escapes and intentional whitespace unambiguous. `LanguageCatalogFiles` reads YAML only through production `TranslationCatalog`; it does not contain another YAML flattener. The complete `.before.snapshot` and `.after.snapshot` streams compare byte-for-byte equal. Evidence: `semantic-results.json`, both complete snapshot pairs and original `.before.yml` files in the ignored task directory.

No historical commit SHA or historical key count is hardcoded into production or the new harness. The comparison takes the real pre-edit snapshots; the permanent harness derives current key sets dynamically. Java translation lookup strings remain unchanged because no production Java file changed.

## Live files and optional adoption

Inspected `LocalizationModule.enable`, `ConfigFiles.copyDefault`, `LocalizationService.prepare/lookup` and reload plans. Startup copies a bundled language resource only when its live file is missing. Existing flat/mixed live overrides, partial catalogs, unknown/custom keys and independent EN/DE values remain supported and authoritative. Existing locale detection, Console English, per-recipient rendering, safe Component placeholders, bundled/English fallback, atomic reload and locale-view refresh are untouched.

**No customized live language file was reorganized or migrated.** The new layout is bundled-resource organization. Existing flat live files remain compatible and are never replaced on startup, reload or deployment.

The documented optional workflow is a manually organized candidate per language, rather than an automatic YAML migrator. `scripts/adopt-language-catalog.ps1` builds the existing parser and test-only `LanguageCatalogFiles` helper. Default invocation is preview: validate both files, compare complete key sets and exact values, display the candidate key count/fingerprint, and leave live/backup bytes untouched. `-Apply` is explicit; it validates again, writes/verifies an exact original-byte backup beside the live file and atomically replaces the live file with validated candidate bytes. It checks for intervening changes to the live bytes before replacement. No unsupported-filesystem copy fallback. Unknown keys, custom values and partial override membership must remain exact, or adoption is rejected. Ambiguous/duplicate YAML, invalid UTF-8 and non-string values are rejected by the production reader.

Candidates are prepared by copying the actual live file, never by replacing it with defaults. Operators manually retain/reposition comments; the tool copies candidate bytes without YAML reserialization, so candidate comments are preserved and the backup retains all original comments. The semantic validator does not claim to compare comment meaning. Pause other editors/deployments and stop the dev server during adoption; EN and DE are handled independently. The procedure, exact commands, backup recovery and `/core reload` behavior are in `docs/FORMATTING.md`. There is no automatic migration framework or startup hook.

The PowerShell wrapper itself was exercised on isolated files, including paths with spaces, customized values and an unknown key. Preview made no live/backup change; explicit apply preserved exact original backup bytes and candidate comments. The permanent Java harness also covers mismatched/missing custom values, attempted EN→custom-DE replacement, invalid UTF-8, duplicates and same-file rejection. Actual operator files were not used for adoption tests.

Future messages go to the owning feature, at the equivalent position in both languages, with unchanged key namespace and matching placeholders. No random end-of-file additions. Both maintained documents explain this convention and safe MiniMessage/whitespace editing.

## Validation

Focused: **16 harnesses / 5,057 checks passed**. Covers every current localization harness, complete catalog loading/rendering/Java references, fallback/live overrides/atomic reload, Friend/Clan/Settings/Visibility/VisiblePlayers/Navigator/Quest localization, DailyQuest countdown/force, GUI authoring audit and the new `CatalogOrganizationHarness` (**43 checks**). Receipts: `focused-results.json`, `focused.log`.

Five actual resource mutations were tested through the production reader/validator, with exact raw-byte restoration and a successful semantic comparison after each:

| Control | Temporary mutation | Detected failure |
|---|---|---|
| A | Remove the English reset description | Missing key: `missing=1` |
| B | Change only the reset countdown color | Exact parsed-value change: `changed=1` |
| C | Nest `clan.owner.transferred` as another `owner` section beside scalar `owner` | Invalid language catalog |
| D | Add top-level `common.close` beside nested `common: close` | Duplicate flattened `common.close` |
| E | Remove German `<time>` from the reset countdown | Placeholder mismatch for `daily-quests.reset-countdown` |

All five mutations were restored; no weakened test or changed text remains. Evidence: `negative-results.json`, `negative-A.log` through `negative-E.log`, semantic snapshots and `wrapper-results.json`.

Full canonical regression: **180 discovered/executed/passed / 27,761 checks, zero failures and zero missing/invalid**. All **179/179** baseline entrypoints retained. One new entrypoint contributes 43 checks; the existing source-file audit and repository/report-link audit add one check each, accounting for the measured baseline-to-final increase of 45. Receipts: `full-results.json`, `full-inventory.txt`, `baseline-inventory.txt`, `full-regression.log`.

Offline Maven **clean package: BUILD SUCCESS**, 24.434 seconds, after canonical regression. Used the existing project stop/build/atomic-copy deployment sequence. Target and deployed JAR SHA-256 match: `2E007F1B8CDE315EF4843282E9FA4E3104DE85E4291E302995792F392BDD9863`. The JAR contains exactly the two current language resources byte-identical to source and no developer validator/native probe classes. Receipt: `package.log`, `deployed-sha256.txt`.

Native **Paper 1.21.11-132 smoke: 25 checks passed**. The production bundled reader loaded both organized catalogs with the exact baseline fingerprints/key counts; complete parity passed. Existing flat live values remained authoritative. MessageService rendered both English/German recipient locales, reset-clock `<time>` and safe literal Component placeholders correctly before and after an actual successful console `/core reload`. The reload published a fresh localization snapshot. Clean enable/shutdown, exit 0; **0 VapeeCore/CatalogProbe ERROR or SEVERE**. Actual counts: **28 modules, 7 reload participants, 37 VapeeCore command roots, 50 permissions**.

All **10 operator YAMLs and 2 live language files** retained their original hashes across the smoke. No catalog adoption, profile mutation, default-file overwrite or operator text change was performed. The temporary probe JAR/remapped copy was removed; server is stopped. This is a real Paper process with virtual Player locale boundaries, not a real Minecraft client or claimed visual client acceptance. All player-facing parsed values are unchanged by this phase. Evidence: `paper-results.json`, `paper-smoke.log`, `operator-before.json` and ignored probe source.

## Exact changed files and Git

- `src/main/resources/lang/en.yml`
- `src/main/resources/lang/de.yml`
- `src/test/java/dev/vapee/core/i18n/LanguageCatalogFiles.java`
- `src/test/java/dev/vapee/core/i18n/CatalogOrganizationHarness.java`
- `scripts/adopt-language-catalog.ps1`
- `docs/DEVELOPER_GUIDE.md`
- `docs/FORMATTING.md`
- `reports/i18n-catalog-organization/I18N_CATALOG_ORGANIZATION.md`

Final delivery is one local commit, `Organize English and German translation catalogs`, after final validation and scope/whitespace checks. Full SHA and verified working-tree state are returned in the closing response. No push, merge or Quest NPC work. Ignored task snapshots/logs, isolated adoption fixtures and native probe artifacts are not shipped in the production JAR.
