# Step 08A.1 — Configurable Daily Quest Selection

## Scope and baseline

Branch: `codex/step08a1-daily-quest-selection`.
Verified clean master and fetched `origin/master`:
`0f5ef0c47aeb0373d202e82844823d5642433d26`.
Before snapshots cover 1,488 files across both existing worktrees, including the
two explicitly protected ignored receipts. The foreign worktree already contains
the untracked `reports/CHAT_PRESENTATION_POLISH.md`; it is preserved.

No new gameplay producer, HUD, message styling, module, scheduler, permission,
command, player schema, database or reward implementation is introduced.
Movement measurement rules, Economy and RewardService are unchanged.

## Configuration contract

| YAML field | Default | Validation / meaning |
| --- | --- | --- |
| `quests.<id>.enabled` | `true` | Actual Boolean; eligibility for future selection only |
| `quests.<id>.category` | absent | Technical ID; only explicit categories share a limit |
| `quests.<id>.group` | absent | Technical ID; same groups share a catalog-wide limit |
| `selection.max-per-category` | unrestricted | Positive whole number up to Long.MAX_VALUE |
| `selection.max-per-group` | unrestricted | Positive whole number up to Long.MAX_VALUE |

Category and group IDs match `[a-z0-9][a-z0-9_-]{0,63}`. There is no category,
group or progress-key allowlist. Missing metadata does not imply a synthetic
shared bucket. IDs are neither trimmed nor inferred from translated text.
Quoted Boolean/numeric strings, invalid identifiers, lists/maps in scalar fields,
zero/negative/fractional/overflowing limits and explicit YAML nulls fail prepare
with the exact YAML path. The existing ConfigService convention of inspecting
SnakeYAML nodes distinguishes explicit nulls from fields erased by Bukkit;
there is no new dependency. General root-setting fallback behavior is unchanged.

`DailyQuestSelection` contains only immutable configuration metadata; it is not
another quest registry. The existing six-argument `DailyQuestConfig.State`
constructor and four-argument `DailyQuestSelector.select` remain available with
legacy defaults. QuestDefinition and existing dependent domain APIs are retained.

## Deterministic algorithm

The pre-existing SHA-256 input remains exactly UUID + newline + Cycle-ID +
newline + Quest-ID, sorted by unsigned digest bytes, then Quest-ID. In that order
the selector skips disabled IDs, checks both explicit metadata limits against
already selected entries, and selects at most `quests-per-day` different IDs.
Skipped entries consume no limit. No global random source, map iteration order,
backtracking or silent relaxation affects the result.

With all optional fields omitted, selection matches the frozen previous
algorithm exactly. YAML order and selector recreation do not change results.
Limits can underfill slots; the algorithm does not promise a globally maximal
matching when category/group constraints overlap. A service warning reports an
actual constrained shortfall once while a configuration snapshot stays active,
rather than logging for every player or sync. Reload may publish a fresh snapshot
and permit a new diagnostic. The existing small/empty-catalog behavior remains.

## Assignment, pending and reload behavior

Disabled definitions remain in the registry and undergo complete validation.
Current-cycle IDs, progress, completion, GUI views and pending rewards retain
their existing domain meaning. Ordinary reload does not reroll same-cycle
assignments. A disabled current task can finish and earn its configured reward
through the existing QuestService/RewardService path.

At a new cycle, pending rewards are retried before rotation; unresolved pending
states block replacement. Once paid, the new selection excludes disabled IDs.
No additional reward retry or coin writer is introduced. Explicit Force refuses
pending states, adopts current selection metadata immediately, preserves progress
for overlapping IDs, and retains its existing atomic save/rollback contract.

If the eligible selection is empty, ordinary sync returns `NO_DEFINITIONS` and
preserves assignments/cycle without dirty writes, matching the existing empty
catalog contract. This can preserve old tasks beyond a cycle boundary until
eligibility changes. Explicit Force with a nonempty definition catalog can adopt
the empty selection and commit its cycle, provided no pending reward blocks it.

The unchanged DailyQuestModule reload plan publishes the complete Config State
and the complete definition catalog; rollback restores both. Invalid metadata
fails prepare before either is published. No live operator YAML is rewritten.
Metadata never enters player YAML, so no player migration is necessary.

## Operator examples

### Legacy definition: identical behavior without new fields

```yaml
enabled: true
quests-per-day: 4
quests:
  play_30:
    name: {en: Regular Player, de: Stammspieler}
    description: {en: Play for 30 minutes., de: Spiele 30 Minuten.}
    progress-key: playtime:minute
    target: 30
    reward-coins: 25
```

### Diverse movement variants: at most one walking task

```yaml
enabled: true
quests-per-day: 4
selection:
  max-per-category: 3
  max-per-group: 1
quests:
  walk_100:
    enabled: true
    category: movement
    group: walking
    name: {en: Small Walk, de: Kleiner Spaziergang}
    description: {en: Walk 100 blocks., de: Laufe 100 Blöcke.}
    progress-key: movement:walk
    target: 100
    reward-coins: 10
  walk_500:
    category: movement
    group: walking
    name: {en: Long Walk, de: Langer Spaziergang}
    description: {en: Walk 500 blocks., de: Laufe 500 Blöcke.}
    progress-key: movement:walk
    target: 500
    reward-coins: 30
```

This intentionally underfills four slots: the two definitions share one group.
Add unrelated definitions to increase variety; limits are never auto-relaxed.

### Keep a future integration defined but out of future daily selection

```yaml
quests:
  future_community:
    enabled: false
    category: community
    name: {en: Future Task, de: Zukünftige Aufgabe}
    description: {en: Requires a future producer., de: Benötigt eine spätere Quelle.}
    progress-key: community:future
    target: 1
    reward-coins: 5
```

This is an operator example, not a bundled quest or fabricated producer. Disabling
does not delete an already assigned task. To defer pending work, retain its valid
definition; removing/renaming an ID remains different from disabling selection.

## Defaults and evolution

Bundled active values remain `config-version: 1`, `enabled: false`,
`quests-per-day: 4`, midnight/system reset, and `quests: {}`. Only explanatory
comments are added. Optional absent fields already have precise legacy defaults;
no ConfigEvolution version bump, injected selection section, new quest catalog
or 47-quest activation is needed.

## Verification

The complete canonical command `pwsh -NoProfile -File .\scripts\verify.ps1`
passed in this implementation run:

| Check | Actual result |
| --- | --- |
| Maven clean package | PASS |
| Spotless | PASS (existing repository pilot allowlist) |
| Discovered / executed / passed harnesses | 237 / 237 / 237 |
| Total checks | 35,292 |
| Failed / missing-invalid | 0 / 0 |
| Original harness inventory | All 237 retained, none removed or bypassed |

The existing Config, selection, service/persistence and reload harnesses were
extended: DailyQuestConfigHarness 148 checks, DailyQuestCycleSelectorHarness 250,
DailyQuestServiceHarness 48, DailyQuestLifecycleHarness 20. Tests cover
legacy compatibility, exact historical SHA ordering, disabled/mixed catalogs,
both limits, absent metadata, shortages, UUID/cycle/order stability, strict types,
same-cycle retention, disabled completion/pending retry, Force and save rollback,
real file persistence without migration, and reload apply/rollback.

### Native acceptance

**56 native checks passed** on disposable Paper 1.21.11 build 132 (`c5eb079`),
LuckPerms 5.5.84 and JDK 21. The own loopback server used port 25661, exited with
code 0, and all VapeeCore module shutdown calls returned normally. No operator
server or real player/world data was used.

The test used actual module/config/registry/service instances and console reload
dispatch, eight definitions in the fixture's own YAML, and the real player file
repository. It verified legacy assignments, same-cycle reload retention, disabled
completion through real RewardService/EconomyService, no double rewards,
category/group shortages, unclassified entries, YAML reorder invariance,
direct domain Force API with overlap preservation, an all-disabled selection,
one diagnostic across repeated syncs, empty Force, real balance-overflow pending
retry, next-cycle filtering and persisted profile reload without migration.

Two deliberately induced error logs are expected test outcomes: the real reload
rejects quoted `quests.walk_b.enabled`, and an isolated test ReloadService with
the actual DailyQuestModule plus a failing probe exercises apply/rollback. The
production seven-participant composition is unchanged. The failed prepare leaves
both active snapshots and the fixture YAML bytes unchanged; rollback restores
metadata/catalog together and retains assigned player progress. No unexpected
plugin exception or incomplete shutdown appeared.

The test makes explicit QuestProgressReporter API calls and does not simulate a
connected player, mouse/keyboard input or rendered inventory. It verifies the
changed configuration/service/persistence boundaries, not Minecraft-client UX or
new producer behavior.

Artifact SHA-256 (build and tested copy match):
`D0FE3B0C71F902DC27594BB552EC0E6E7EFB3A34EB8BF1A43F3FD99B9FEC5839`.
Helper, script, logs, artifact receipt, results and process-exit evidence are
outside the repository under
`step08a1-daily-quest-selection-20261106-172439/native-01/`.

Real Minecraft-client acceptance remains open: review an existing disabled task
in `/quests`, complete it, reload within the same cycle, exercise authorized
Force, then inspect a new-cycle constrained selection and a partially filled
menu. API tests cannot confirm visible client rendering.

## Changed paths

- `src/main/java/dev/vapee/core/quest/daily/DailyQuestSelection.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestConfig.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestSelector.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestService.java`
- `src/main/resources/daily-quests.yml`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestConfigHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestCycleSelectorHarness.java`
- `src/test/java/dev/vapee/core/quest/DailyQuestServiceHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestLifecycleHarness.java`
- `docs/DEVELOPER_GUIDE.md`
- `reports/08a1/STEP_08A_1_DAILY_QUEST_SELECTION.md`

Delivery consists of one local implementation commit; its full SHA is supplied
in the completion response and external receipt. No push, merge, PR or deployment.
Run files, receipts, Recovery branches, the protected foreign worktree and all
operator/server/player/world data are preserved. Evidence stays outside the repo.

The before/after comparison of all 1,488 original tracked/nonignored files and
explicitly included receipts found changes only in the nine intended existing
paths above; the metadata type and this report are new. Protected Run files and
both receipts retain their SHA-256 values. The foreign worktree retains its
original HEAD, branch, status and file hashes. No operator process was stopped or
modified; ignored live logs/world/player files are outside the hash comparison,
and no byte-identity claim is made for externally changing operator files.
