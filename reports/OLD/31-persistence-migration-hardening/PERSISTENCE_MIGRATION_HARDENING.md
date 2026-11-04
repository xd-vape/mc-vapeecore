# Phase 31 – Persistence and Migration Hardening

## Baseline and scope

Verified before edits: `a3d77128ec153d5941e038572a5b93537226dfbf`, branch
`phase/31-persistence-migration-hardening`, clean working tree. Decorated last
15 commits inspected. One worker, existing checkout; no subagents, worktree,
branch creation, push, merge or rebase. Scope: PERSIST-001/002, report moves and
their active reference corrections only. Raw tracked-file hashes captured before
edits in ignored `dev-server/.vapeecore-dev/phase31-baseline-hashes.json`.

## Pre-implementation audit and ownership decision

All 19 docs Markdown files inventoried before production changes: 16 dated
phase/audit reports move to the requested phase directories under reports/.
DEVELOPER_GUIDE.md, FORMATTING.md and PERMISSIONS.md are ongoing guides and remain
in docs/. README remains at the root. Historic reports retain their exact bytes,
including historically recorded old paths; only active reference paths change.

Read the full 30A audit, 30F report, Developer Guide, player package, LobbyConfig,
LobbyService and SetSpawnCommand, with related persistence/lifecycle harnesses.
Compared Warp/Blackjack and Friend/Clan/Moderation save paths. Warp/Blackjack catch
all atomic IO errors and retry/sleep/copy; untouched (PERF-001). Other repositories
use AtomicMoveNotSupportedException-only fallback, with feature-specific schemas
and errors. They remain untouched. Lobby saves before config/service publication
but writes directly to live YAML. Player writes a fresh known-field YAML through
a sibling temp, then updates the name index; unload saves before removing cache.

### Complete baseline player schema inventory

UUID belongs to the canonical filename, not a YAML field. No persisted rank,
friendship list, clan membership, runtime presence, actual playtime statistic,
inventory, flight, lobby mode or visibility hide-pair state exists in this file.
There are no obsolete YAML aliases to remove; lobby-players-visible is still the
canonical master visibility key, despite Java compatibility accessors.

| Owned key(s) | Required / default / missing | Invalid input on read | Normal save / ownership |
|---|---|---|---|
| name | Required, no default | Existing Bukkit getString conversion; missing/blank rejected | Current nonblank name replaces whole value |
| first-join, last-join | Required integral epoch millis, no default | Non-integer rejected; last before first rejected by CorePlayer | Current epoch millis replace whole values |
| settings.scoreboard, sounds, private-messages, friend-requests | Optional, true each | Non-boolean warns/defaults; invalid settings section defaults | Canonical current booleans |
| settings.friend-presence-notifications | Optional false | Same boolean rule | Canonical current boolean |
| settings.lobby-players-visible | Optional true | Same boolean rule | Canonical current master; not removed as alias |
| settings.visibility.show-friends, show-staff, show-added-users, show-game-participants | Optional false each | Invalid section/boolean warns/defaults | Canonical current booleans |
| settings.visibility.added-players | Optional empty set | Invalid list defaults; bad/noncanonical/self/nonstring entries warn/skip; deduplicates | Whole sorted canonical UUID list |
| economy.coins | Optional zero if economy/key absent | Invalid section/type/negative rejects entire player | Canonical nonnegative long; never default-overwrite corrupt disk |
| social.ignored | Optional empty set if social/key absent | Wrong section/list, nonstring/invalid UUID/self rejects entire player; duplicates deduplicate | Whole sorted UUID list; Friend/Clan authority separate |
| rewards.online.processed-playtime-ticks | Optional uninitialized | Wrong sections/type/negative warn/uninitialize | Current nonnegative long or remove key when uninitialized |
| quests.active.&lt;id&gt;.progress | Optional empty assignments; per-entry required integral nonnegative long | Wrong sections or malformed entry warn/skip affected entry | Entire active map rebuilt from current aggregate, sorted IDs |
| quests.active.&lt;id&gt;.status | Per-entry required ACTIVE/REWARD_PENDING/COMPLETED | Invalid status or ID skips entry; valid unknown catalog IDs retained | Same whole-map ownership, no reward on load |
| quests.daily.cycle-id | Optional uninitialized | Invalid sections/type/noncanonical ISO date warn/uninitialize | Current canonical date string or remove key |

Baseline unknown keys are ignored on read and lost on whole-profile save. New
policy: root and the fixed container maps settings, settings.visibility, economy,
social, rewards, rewards.online, quests and quests.daily are open to unknown
siblings. Scalars/lists at owned keys are fully replaced. quests.active is a
closed dynamic assignment namespace, including each record: stale assignments,
invalid entries and unknown record members are not merged back. Valid unknown
quest IDs remain domain entries. Wrong-type optional containers are canonicalized
on normal save. Required/core corruption aborts load and fresh save-source read.
Preserve raw YAML map data (including unknown nulls and literal dotted keys),
overlay only the listed owned keys; formatting/comments/anchor identity are not
durable field values. No generic deep merge or universal repository abstraction.

### Version and file-commit design decided before implementation

Use existing store convention schema-version: legacy missing/explicit 0, current
1, higher integer refused. Invalid/negative/noninteger/explicit-null versions
refused. Local explicit sequential 0 -> 1 in-memory migration only. Reads never
write. Each save rereads the disk source and version before overlay, rather than
keeping a stale extension cache. Future data throws IllegalStateException, never
Optional.empty; PlayerService therefore cannot create a default or publish a new
cache entry. Existing PlayerListener catches load failure and disconnects with its
controlled data-load message. Save rechecks protect already loaded aggregates.

One small technical writer shared by exactly LobbyConfig and FilePlayerRepository:
serialize fully, unique VapeeCore sibling temp, close writer, feature validation,
optional player pre-migration protection, atomic replace attempt. Only explicit
atomic unsupported permits fallback. Fallback copies existing destination to one
bounded recovery file before non-atomic replace; failure attempts restore while
retaining the backup if restore fails. A leftover recovery file blocks subsequent
writes pending explicit operator reconciliation, preventing loss of the only good
copy. Reads never automatically restore. New destination has no invented backup.
Migration retains one separate pre-migration copy; an existing identical copy is
reusable, a different one blocks migration until operator reconciliation. Normal
current-version atomic saves do not produce a migration backup.

No fsync/directory durability, concurrent external-writer transaction, power-loss
or automatic startup recovery guarantee. No runtime publication before disk
commit. Post-commit cleanup errors must be logged without falsely reporting a
failed commit and triggering domain rollback against already committed disk.

## PERSIST-001 implementation and fault matrix

Resolved. LobbyConfig owns YAML and its spawn/config State; LobbyService owns the
resolved runtime spawn; SetSpawnCommand owns command feedback. The latter two
remain byte-identical. The candidate retains the existing configuration, updates
the six spawn fields, serializes before opening a file, writes a unique sibling
`.vapeecore-<filename>-*.tmp`, and closes the writer. The real YamlConfiguration
loader and the existing readState parsers validate the closed candidate with
strict spawn validation and exact requested-spawn equality. The shared writer
also compares the closed UTF-8 content to the complete serialized content.
Config state changes only after write returns; service publication follows.
Normal read/reload does not write. Default-resource initialization is unchanged.

| Fault / case | Proven outcome |
|---|---|
| Serialization failure, existing or absent target | No writer opened; previous bytes / absence retained |
| Partial temp write | Exception, old live bytes and both runtime states retained |
| Close failure after write | Exception, no replacement/publication, old bytes retained |
| Malformed YAML or invalid spawn candidate | Real parser/validator rejects before replacement |
| Atomic success | Closed valid candidate replaces destination; runtime publishes |
| Ordinary atomic IOException | Fails; never misclassified as unsupported or retried non-atomically |
| AtomicMoveNotSupportedException | Protect old destination then use explicit fallback |
| Fallback replacement fails after damaging destination | Restore exact backup; save fails, runtime stays old |
| Recovery backup creation fails | Replacement not attempted; original target retained |
| Restore also fails | SEVERE with cause; good backup retained; next write refused |
| Temp cleanup fails | WARNING; preserves original failure, or committed success if commit already happened |
| Backup cleanup fails after commit | WARNING; committed success publishes; leftover backup blocks next write |
| Missing destination | Atomic/fallback success creates it; failed fallback restores absence |

LobbyPersistenceHarness exercises real LobbyConfig/LobbyService for I/O and
publication, with narrow FileAccess faults against real isolated files. The
serialization exception is injected at the shared production Supplier boundary;
it is not a replacement YAML parser. Missing-destination tests also exercise the
shared technical boundary directly. Production parsing is used for readback.

### Bounded backup, recovery and interruption contract

Recovery copy: `<target>.vapeecore-recovery.bak`, at most one per target. Created
only when atomic replacement is explicitly unsupported and a target exists.
Copy uses no REPLACE_EXISTING, so an unresolved backup is never overwritten.
Successful fallback removes it; failed fallback restores by copying it over the
target, then removes it only if restoration succeeded. Failed restoration retains
it and propagates the original error with the restoration failure suppressed.
Cleanup failures log the exact artifact and cause. A failed backup copy may leave
a partial recovery artifact; it is not automatically trusted or overwritten.

| Interruption point | Contract and limit |
|---|---|
| Serialization / temp write / validation failure | Existing good destination untouched; own temp cleanup attempted |
| Process crash before replace | Existing destination unchanged; an orphan temp can remain |
| Atomic replace succeeds | Filesystem atomic name replacement; publish only after return |
| Crash during recovery-copy creation | Old target remains; incomplete backup may require reconciliation |
| Atomic unsupported / fallback failure | Completed backup protects previous bytes; exception triggers restore |
| Restore succeeds | Old bytes restored; failed save does not publish |
| Restore fails or process crashes during fallback | Target may be missing/partial; recovery copy retained, manual reconciliation needed |
| Post-commit cleanup fails | New disk state remains committed; no false failed-save rollback |

No forced fsync of file or directory, universal power-loss guarantee, multi-file
transaction, automatic recovery or periodic artifact scavenging is claimed.
There is no unbounded timestamped backup history. Operators must inspect target
and the bounded copy before restoring/retiring a leftover artifact. A recovery
marker blocks writes in both consumers. Player reads/indexing also refuse it,
including when the target is absent, to prevent accidental default creation.
Lobby reads keep their existing parsing policy; they do not automatically restore
or refuse solely because of this marker. No cleanup failure is silently swallowed.

## PERSIST-002 implementation and compatibility matrix

Resolved. PlayerFileSchema is a local explicit schema utility, not a repository
framework. SafeConstructor reads supported YAML data, rejects duplicate keys and
unsafe/custom object tags, and preserves raw unknown nulls, lists and dotted keys.
Domain validation remains the existing Bukkit YamlConfiguration/readPlayer path.
The overlay walks only the audited owned paths, copying ancestors to avoid YAML
alias mutations leaking into unknown siblings. The candidate must parse, have
current version, pass domain validation and equal canonical owned values. An
ambiguous literal key colliding with a Bukkit owned path is rejected if its parsed
meaning would differ. Comments, formatting and YAML alias identity are not retained.

| Case | Result |
|---|---|
| Missing schema-version / explicit 0 | Legacy accepted; explicit in-memory 0 -> 1 only; read/index never rewrites |
| schema-version: 1 | Current accepted; normal save preserves supported unknown data |
| Version above 1, including Long.MAX_VALUE | Load/save refusal; original bytes untouched; no downgrade/default aggregate |
| Negative, null, string, fractional, boolean or mapping version | Controlled refusal, no rewrite |
| Valid unknown root and open-container siblings | Retained across save/readback, including null and literal dotted keys |
| Unknown members inside quests.active records | Closed namespace canonicalized; invalid/stale records do not resurrect |
| Invalid required identity, coins or social core | Existing validation rejects; fresh save source also rejects |
| Missing optional state | Existing defaults/uninitialized semantics remain |
| Invalid optional known values | Existing warn/default/skip semantics; save replaces/removes owned invalid values |
| Legacy normal save | Writes current schema; one exact original pre-migration copy retained |
| Migration backup fails / differs from existing copy | Save fails before replacement; original target and prior backup retained |
| Migration replace fails / recovery needed | Same protected replacement contract as lobby; migration copy retained |
| Current save / new player | No migration backup creation; current version written |
| Disk extension edited after load | Fresh source read preserves latest unknown data |
| Disk changes during candidate preparation | Byte comparison aborts before migration backup/replace |
| Candidate changed to valid-looking different owned values | Validation rejects before commit |
| Successful save/unload | Name index publishes only after disk commit; unload then removes cache |
| Failed save/unload | Name index not newly published; cache retained for existing unload retry semantics |

Migration copy: `<uuid>.yml.vapeecore-pre-migration.bak`. Only an actual save of an
existing older supported file creates it, after candidate validation and before
replacement. Copy bytes are checked. It is retained after success and failure;
an existing exact original copy is reusable for retry, a differing/non-regular
copy refuses migration pending operator reconciliation. A partially created copy
can likewise require reconciliation. Current-version saves leave this copy alone.
No startup bulk migration, timestamped copies, new dependency or database added.

Future-version findByUniqueId throws a logged IllegalStateException with the
version refusal as cause, rather than Optional.empty. PlayerService.load therefore
does not create/cache a default aggregate; PlayerListener's existing catch produces
a controlled login/load failure. Startup indexing skips the file with a warning.
An already cached aggregate is not reread on each cache access, but its normal
save rechecks disk version and cannot overwrite a newer file. Immediate settings
and economy mutations keep their existing rollback behavior; buffered reward/quest
flows retain their existing dirty/retry behavior. Failed unload keeps the cache;
saveAll catches failures per player and continues. Shutdown cache clearing is
unchanged. No new aggregate read-only mode is invented. The byte recheck detects
preparation-time edits, not every possible concurrent external write after it.

PlayerSchemaPersistenceHarness covers real repository/service/settings paths,
all compatibility cases above, real faulting I/O, long-range economy, social and
visibility UUID lists, online rewards, daily cycle, pending/unknown-ID quests and
load-save-load equivalence. Existing domain harnesses additionally remain intact.

## Regression and negative controls

Every executable *Harness.java was discovered and run as its own Java process;
Maven package was not used as a substitute. Focused: **66 harnesses / 10,045 checks /
0 failures / 0 skipped**. Full final source regression: **118 harnesses / 18,897
checks / 0 failures / 0 skipped**. All 115 baseline harnesses remain; none lost
checks. PermissionDescriptorHarness rises from 840 to 842 because it checks each
main Java source and two production files were added. Permissions did not change.
New LobbyPersistenceHarness: 112; PlayerSchemaPersistenceHarness: 124;
RepoLinkHarness: 22. Thus 18,637 + 112 + 124 + 22 + 2 = 18,897.

Focused coverage includes lobby config/service/commands, every player repository
harness, Identity/Profile, Economy, Settings/Visibility, Quest/DailyQuest,
OnlineReward/Reward, config helpers/defaults, Core reload/feature plans,
PermissionDescriptor, all presentation regressions and ModerationCommandHarness.
The final full run includes the null-safe test assertion correction used by the
negative controls. No existing harness source was changed.

| Isolated mutation | Expected detected failure | Exit |
|---|---|---:|
| Write directly into destination | AssertionError: WRITE old file preserved | 1 |
| Delete target in unprotected fallback | AssertionError: REPLACE old file preserved | 1 |
| Discard unknown subtree | AssertionError: unknown root subtree including null/dotted key preserved | 1 |
| Allow future downgrade | AssertionError: normal PlayerService refuses version 2 | 1 |

All four controls detected; excluded from regression totals. Mutated sources and
classes were isolated under the ignored dev evidence directory and completely
removed. Only their logs/result manifest remain there. No live file fault injection.

Contract remains **37 root commands, 50 permissions, 15 positive child edges,
27 modules, 6 reload participants**. PermissionDescriptorHarness passes 842 checks;
plugin.yml and all resource defaults are unchanged. Phase-30F presentation source
and every baseline test are byte-identical.

## Report migration and active reference audit

All 16 moves used git mv. The SHA-256 column below is both the before-move and
after-move hash (equality verified independently for every file). Historical
statements, counts, commits and old cross-reference text are untouched.

| Original path | New path | SHA-256 before = after |
|---|---|---|
| docs/COMMAND_AUDIT.md | reports/23-core-commands-completion/COMMAND_AUDIT.md | `F04E0A57594F227109587F8A5C7085CCD19371035832FC7B0DE357B5AA5C00E0` |
| docs/MODERATION_FOUNDATION.md | reports/24-moderation-foundation/MODERATION_FOUNDATION.md | `4661C492DBF0FC1ED3356080F8947E0FC2F42B192C818CC05A6B74D245065211` |
| docs/MODERATION_TOOLS.md | reports/25a-moderation-tools/MODERATION_TOOLS.md | `BAB7ECD6652BC58C72FA169DA2BD0A33AA72D136C06D981299739434FF30174B` |
| docs/MODERATION_MUTES.md | reports/25b-moderation-mutes/MODERATION_MUTES.md | `48C4975F0B568A78876C7594A74AA2EE20822E3316A4EE4D905AE3F5971943BC` |
| docs/STAFF_HIERARCHY.md | reports/26a-staff-hierarchy/STAFF_HIERARCHY.md | `719340C19B60CF6009CD9DE5ECBD5E1FEC6E5D4B27037E2FEB866F2E0EAC8B20` |
| docs/PERMISSION_HARDENING.md | reports/26b-permission-hardening/PERMISSION_HARDENING.md | `9866B0A1AE8F00494B1951CFE082E95DA4803EACE163FA1858A3EB7CF8AF039B` |
| docs/NOTIFICATIONS_PRESENCE.md | reports/27-notifications-presence/NOTIFICATIONS_PRESENCE.md | `930454F6647F021FE80342CA2F501C0A5DC1B38DAA4A168210F55553D93AA3E4` |
| docs/LOBBY_NAVIGATION.md | reports/28-lobby-navigation/LOBBY_NAVIGATION.md | `62E58913B45DD885A00198FD02F293E8FEDB714410A7886CE457D003614AA363` |
| docs/QUEST_COMPLETION.md | reports/29-quest-completion/QUEST_COMPLETION.md | `2EDA67499B42AED0B20C5C5CA4B8B58932572CE2CA0209784A7D95D5D76F5834` |
| docs/FULL_CODEBASE_AUDIT.md | reports/30a-full-codebase-audit/FULL_CODEBASE_AUDIT.md | `73F7FBF38E4DD01F5A3328275A8045C9FB3812D4B4BF6BA4D817458F1FFEA2CC` |
| docs/MODERATION_AUTHORIZATION_HOTFIX.md | reports/30a1-moderation-authorization-hotfix/MODERATION_AUTHORIZATION_HOTFIX.md | `99735909CC7A5B05F5CBE1DA6D01E563061988EC98F6A16B6F8B3D84BA09982A` |
| docs/ARCHITECTURE_REFACTOR.md | reports/30b-architecture-refactor/ARCHITECTURE_REFACTOR.md | `0D0DC4032A1C0CAA95855DC7DBB39BE1D794E5029C973B638F7D54B6683A404A` |
| docs/GUI_INVENTORY_ITEM_CLEANUP.md | reports/30c-gui-inventory-item-cleanup/GUI_INVENTORY_ITEM_CLEANUP.md | `42E21FD11F804DDDB50E69DF9CEAEB78742DA3C21DD448148F67F53B6D9672A5` |
| docs/CONFIG_REGISTRATION_MODULE_CLEANUP.md | reports/30d-config-registration-module-cleanup/CONFIG_REGISTRATION_MODULE_CLEANUP.md | `351F9AE8CA6E3DE6C5964A0A408583C18F4637947C402E4AD16B92443C9F1B24` |
| docs/DUPLICATION_MAINTAINABILITY_CLEANUP.md | reports/30e-duplication-maintainability-cleanup/DUPLICATION_MAINTAINABILITY_CLEANUP.md | `B395ACB7474FFDEAC2ED2493D185D9AE298E1CC8E0AA55C4C470DD020F8D50EA` |
| docs/PRESENTATION_NAMETAG_POLISH.md | reports/30f-presentation-nametag-polish/PRESENTATION_NAMETAG_POLISH.md | `742C243B68110FED5BE77941D29EAF340E5AE3F389959B69F251BAD0006E21BB` |

Retained documents: docs/DEVELOPER_GUIDE.md is the current architecture and
extension guide; docs/FORMATTING.md is the durable formatting standard;
docs/PERMISSIONS.md is the current permission reference. README.md stays in root.
Exactly 23 active lines have path-only corrections: README 10, Developer Guide
12, Permissions 1. Labels that themselves contained the literal old path were
corrected along with their target. No historical counts or other prose changed.
An exact replacement comparison against baseline verified these are only paths.

Actual active references changed (report names; repeated occurrences are counted
in the line totals above):

| Active file | Reports referenced with corrected paths |
|---|---|
| README.md | QUEST_COMPLETION, LOBBY_NAVIGATION, NOTIFICATIONS_PRESENCE, PERMISSION_HARDENING, STAFF_HIERARCHY, MODERATION_MUTES, MODERATION_TOOLS, MODERATION_FOUNDATION, COMMAND_AUDIT |
| docs/DEVELOPER_GUIDE.md | See exact changed-line inventory below |
| docs/PERMISSIONS.md | PERMISSION_HARDENING |

The repository-wide search was classified conservatively: old paths in historical
reports are retained as historical evidence; no active Markdown link points to a
missing moved report. RepoLinkHarness checks README and active docs links plus
explicit stale root/docs report paths; 22 checks pass. DOC-001 remains open.

### Exact active changed-line inventory

| Active file | Current line | Corrected reference(s) on that line |
|---|---:|---|
| README.md | 11 | reports/29-quest-completion/QUEST_COMPLETION.md |
| README.md | 13 | reports/29-quest-completion/QUEST_COMPLETION.md |
| README.md | 23 | reports/28-lobby-navigation/LOBBY_NAVIGATION.md |
| README.md | 33 | reports/27-notifications-presence/NOTIFICATIONS_PRESENCE.md |
| README.md | 41 | reports/26b-permission-hardening/PERMISSION_HARDENING.md |
| README.md | 49 | reports/26a-staff-hierarchy/STAFF_HIERARCHY.md |
| README.md | 61 | reports/25b-moderation-mutes/MODERATION_MUTES.md |
| README.md | 67 | reports/25a-moderation-tools/MODERATION_TOOLS.md |
| README.md | 73 | reports/24-moderation-foundation/MODERATION_FOUNDATION.md |
| README.md | 77 | reports/23-core-commands-completion/COMMAND_AUDIT.md |
| docs/DEVELOPER_GUIDE.md | 121 | reports/30d-config-registration-module-cleanup/CONFIG_REGISTRATION_MODULE_CLEANUP.md |
| docs/DEVELOPER_GUIDE.md | 241 | reports/30f-presentation-nametag-polish/PRESENTATION_NAMETAG_POLISH.md |
| docs/DEVELOPER_GUIDE.md | 307 | reports/25a-moderation-tools/MODERATION_TOOLS.md |
| docs/DEVELOPER_GUIDE.md | 337 | reports/26a-staff-hierarchy/STAFF_HIERARCHY.md |
| docs/DEVELOPER_GUIDE.md | 349 | reports/25b-moderation-mutes/MODERATION_MUTES.md |
| docs/DEVELOPER_GUIDE.md | 371 | reports/27-notifications-presence/NOTIFICATIONS_PRESENCE.md |
| docs/DEVELOPER_GUIDE.md | 527 | reports/29-quest-completion/QUEST_COMPLETION.md |
| docs/DEVELOPER_GUIDE.md | 621 | reports/30c-gui-inventory-item-cleanup/GUI_INVENTORY_ITEM_CLEANUP.md |
| docs/DEVELOPER_GUIDE.md | 664 | reports/28-lobby-navigation/LOBBY_NAVIGATION.md |
| docs/DEVELOPER_GUIDE.md | 807 | reports/30b-architecture-refactor/ARCHITECTURE_REFACTOR.md |
| docs/DEVELOPER_GUIDE.md | 953 | reports/23-core-commands-completion/COMMAND_AUDIT.md |
| docs/DEVELOPER_GUIDE.md | 1108 | reports/26b-permission-hardening/PERMISSION_HARDENING.md |
| docs/PERMISSIONS.md | 259 | reports/26b-permission-hardening/PERMISSION_HARDENING.md |

## Final build and real Paper smoke

After the complete regression, the exact requested offline Maven invocation ran:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

BUILD SUCCESS, exit 0, 2026-10-05 19:47:18 Europe/Berlin.
Artifact: target/vapeecore-1.0-SNAPSHOT.jar, **1,074,244 bytes**.
SHA-256: `162D058FC9585E39EFD3F7BDC0301737040539AE36436034D89E5CE7762DDCAE`.
The deployed dev-server/plugins JAR hash matches exactly. No production source
changed after the complete regression or build; only this report was completed.

Existing dev server, 2026-10-05 19:48–19:50 Europe/Berlin: Oracle Java
21.0.12.1+1-LTS-4, Paper 1.21.11 build 132 (c5eb079), LuckPerms 5.5.84.
Startup reached Done; 27 modules enabled. Console version, lp info, list, core,
core version, core help, core reload and bukkit:help VapeeCore were executed.
Core reported Running, 27 active modules, LuckPerms connected, zero loaded players.
Reload prepared exactly config.yml, lobby.yml, chat.yml, private-messages.yml,
presentation.yml and daily-quests.yml; completed successfully in 17 ms. Bukkit
help listed exactly the descriptor's 37 roots. Clean stop disabled all 27 modules
in reverse order, saved zero loaded players and exited 0. **ERROR/SEVERE: 0**.
Paper's newer-Minecraft-release warning and spark's Windows Java-engine selection
were observed; neither is a plugin failure. LuckPerms' existing H2 is its own
storage and is unrelated to VapeeCore's unchanged YAML architecture.

No clients were online, no setspawn or player mutation was performed, no live
migration is claimed. Fault injection and migration proofs used isolated harness
directories. All **22 operational YAMLs**, including **10 VapeeCore YAMLs** (eight
configs and two player files), remained byte-identical. The wider recursive scan
also covered 22 historical dev backups: **44/44 unchanged**. No new live YAML or
VapeeCore temp/backup artifacts were introduced by the smoke.

### Live before/after SHA-256

Paths are relative to dev-server/. Each row shows the independently measured
before and after value; equality is not inferred from timestamps.

| Live file | SHA-256 before | SHA-256 after |
|---|---|---|
| bukkit.yml | `A72396BBD1B69636B63F990995ED1CE31DF7183AF275868B4D638071FD4C866A` | `A72396BBD1B69636B63F990995ED1CE31DF7183AF275868B4D638071FD4C866A` |
| commands.yml | `44AFDF9B41EEA9F2D8B9AE41D3C07443EC78A4962C6B7AE33FB3097D6C94E3B9` | `44AFDF9B41EEA9F2D8B9AE41D3C07443EC78A4962C6B7AE33FB3097D6C94E3B9` |
| help.yml | `CBF20F5A7B16475AD3C584C98937015D1E71D9D4A8A7F1C3BB96A26A39087A5D` | `CBF20F5A7B16475AD3C584C98937015D1E71D9D4A8A7F1C3BB96A26A39087A5D` |
| permissions.yml | `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855` | `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855` |
| spigot.yml | `D28BB141ABB67302A1916566DB381CD33B6A9918CD240919D585C7E7EE9B282D` | `D28BB141ABB67302A1916566DB381CD33B6A9918CD240919D585C7E7EE9B282D` |
| config/paper-global.yml | `3E0F45BFEF618906ACFAE48E06D556778700817593A1D811098144FBFD4503BC` | `3E0F45BFEF618906ACFAE48E06D556778700817593A1D811098144FBFD4503BC` |
| config/paper-world-defaults.yml | `E93396F82046B1061EA15219106BD225A395912C96078CFE29DCB588531DC6AC` | `E93396F82046B1061EA15219106BD225A395912C96078CFE29DCB588531DC6AC` |
| plugins/bStats/config.yml | `1EBD306C4177C878598D5D03AF2244E2821A848F501EA923CA872557838DF11B` | `1EBD306C4177C878598D5D03AF2244E2821A848F501EA923CA872557838DF11B` |
| plugins/LuckPerms/config.yml | `F6A86FB8FA2DA4BEC37D8AD2E5C995444D22E743EBF043754825541698EBA5F8` | `F6A86FB8FA2DA4BEC37D8AD2E5C995444D22E743EBF043754825541698EBA5F8` |
| plugins/VapeeCore/blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| plugins/VapeeCore/chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| plugins/VapeeCore/config.yml | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` |
| plugins/VapeeCore/daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| plugins/VapeeCore/lobby.yml | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` |
| plugins/VapeeCore/presentation.yml | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` |
| plugins/VapeeCore/private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| plugins/VapeeCore/warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |
| plugins/VapeeCore/players/0d9efbe6-33bf-4cf7-ae67-61d6238b2561.yml | `B133BFDD5875ED00C3B6B18EDDBEE4B1B126336202732037F4E994F0367FA5E1` | `B133BFDD5875ED00C3B6B18EDDBEE4B1B126336202732037F4E994F0367FA5E1` |
| plugins/VapeeCore/players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `6DFE9926FADA32EA3B60DADD401A71E78072C5DEC8E0EFEF78021A7D980DF511` | `6DFE9926FADA32EA3B60DADD401A71E78072C5DEC8E0EFEF78021A7D980DF511` |
| world/paper-world.yml | `964751ED6030C8FE4F3C09C3B9716C11E36F524B58ECB348749C195A2D5C1E13` | `964751ED6030C8FE4F3C09C3B9716C11E36F524B58ECB348749C195A2D5C1E13` |
| world_nether/paper-world.yml | `BD1C28336734A24BB7A0556710122B4F549A2A8BF9510F299B686F5C42EFE20B` | `BD1C28336734A24BB7A0556710122B4F549A2A8BF9510F299B686F5C42EFE20B` |
| world_the_end/paper-world.yml | `A4B974DE1D171B08B7675542AA667B76A97E8A4DB325FC7014CE168DCDEFC608` | `A4B974DE1D171B08B7675542AA667B76A97E8A4DB325FC7014CE168DCDEFC608` |

### Additional historical dev backup parity

These are not active player/config files. Each hash was measured before and after
and found equal; they were included in the conservative recursive snapshot.

| Backup file | SHA-256 before = after |
|---|---|
| backups/phase10-pretest/VapeeCore-data/chat.yml | `A0A665DB4138E66FAE765AF2DCE65A22AE887660A85F76D464B6CDE360DF8469` |
| backups/phase10-pretest/VapeeCore-data/config.yml | `C0FF8C0111414AB51D86D772353ED9F7A384B7B58040951832F808AF91EE9F44` |
| backups/phase10-pretest/VapeeCore-data/lobby.yml | `CE4DBD050CE27B927D0DC1F54E3BC1204E666BA0540B21C59D9FD6C0D492810C` |
| backups/phase10-pretest/VapeeCore-data/presentation.yml | `BB52F4632478BCEE4B87F062EDCC81CC92E8DC1B238DA4FB8667C83FA331B731` |
| backups/phase10-pretest/VapeeCore-data/players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `648CA9DE88A7555A472BA92B598826F77317DB1C60696C496EB978AA35DD8086` |
| backups/phase5-pretest/VapeeCore/config.yml | `C0FF8C0111414AB51D86D772353ED9F7A384B7B58040951832F808AF91EE9F44` |
| backups/phase5-pretest/VapeeCore/players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `AA1EE9CD2620A2474584942FA8DB4A2FEEA2CD69351B298C37A7028AA5EAA015` |
| backups/phase6-pretest/VapeeCore/config.yml | `C0FF8C0111414AB51D86D772353ED9F7A384B7B58040951832F808AF91EE9F44` |
| backups/phase6-pretest/VapeeCore/lobby.yml | `CE4DBD050CE27B927D0DC1F54E3BC1204E666BA0540B21C59D9FD6C0D492810C` |
| backups/phase6-pretest/VapeeCore/players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `6FF0C5ACEF3978F4669FF7BEC68F80DB67E3726C7AA2724B27DB5B468FD045DF` |
| backups/phase7-pretest/VapeeCore/config.yml | `C0FF8C0111414AB51D86D772353ED9F7A384B7B58040951832F808AF91EE9F44` |
| backups/phase7-pretest/VapeeCore/lobby.yml | `CE4DBD050CE27B927D0DC1F54E3BC1204E666BA0540B21C59D9FD6C0D492810C` |
| backups/phase7-pretest/VapeeCore/players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `6FF0C5ACEF3978F4669FF7BEC68F80DB67E3726C7AA2724B27DB5B468FD045DF` |
| backups/phase8-pretest/VapeeCore/chat.yml | `A0A665DB4138E66FAE765AF2DCE65A22AE887660A85F76D464B6CDE360DF8469` |
| backups/phase8-pretest/VapeeCore/config.yml | `C0FF8C0111414AB51D86D772353ED9F7A384B7B58040951832F808AF91EE9F44` |
| backups/phase8-pretest/VapeeCore/lobby.yml | `CE4DBD050CE27B927D0DC1F54E3BC1204E666BA0540B21C59D9FD6C0D492810C` |
| backups/phase8-pretest/VapeeCore/players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `6FF0C5ACEF3978F4669FF7BEC68F80DB67E3726C7AA2724B27DB5B468FD045DF` |
| backups/phase9-pretest/chat.yml | `A0A665DB4138E66FAE765AF2DCE65A22AE887660A85F76D464B6CDE360DF8469` |
| backups/phase9-pretest/config.yml | `C0FF8C0111414AB51D86D772353ED9F7A384B7B58040951832F808AF91EE9F44` |
| backups/phase9-pretest/lobby.yml | `CE4DBD050CE27B927D0DC1F54E3BC1204E666BA0540B21C59D9FD6C0D492810C` |
| backups/phase9-pretest/presentation.yml | `BB52F4632478BCEE4B87F062EDCC81CC92E8DC1B238DA4FB8667C83FA331B731` |
| backups/phase9-pretest/players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `648CA9DE88A7555A472BA92B598826F77317DB1C60696C496EB978AA35DD8086` |

## File inventory and protected scope

**7 added / 5 modified / 0 removed / 16 renamed**, 28 changed paths with rename
detection. The 16 exact renames are listed in the migration inventory above.

| Change | Path | Scope |
|---|---|---|
| Added | src/main/java/dev/vapee/core/persistence/SafeFileWriter.java | PERSIST-001/002 technical replacement boundary, used by exactly the two affected stores |
| Added | src/main/java/dev/vapee/core/player/repository/PlayerFileSchema.java | PERSIST-002 local schema/version/owned overlay |
| Added | src/test/java/dev/vapee/core/lobby/config/LobbyPersistenceHarness.java | Tests: lobby disk/runtime faults |
| Added | src/test/java/dev/vapee/core/player/repository/PlayerSchemaPersistenceHarness.java | Tests: version, ownership, migration, replacement and service guards |
| Added | src/test/java/dev/vapee/core/persistence/PersistenceFaults.java | Tests: narrow real file I/O fault seam |
| Added | src/test/java/dev/vapee/core/repository/RepoLinkHarness.java | Tests: active links affected by report moves |
| Added | reports/31-persistence-migration-hardening/PERSISTENCE_MIGRATION_HARDENING.md | Phase-31 report |
| Modified | src/main/java/dev/vapee/core/lobby/config/LobbyConfig.java | PERSIST-001 safe candidate validation/commit |
| Modified | src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java | PERSIST-002 guarded read/save, preserved source, migration protection |
| Modified | README.md | Active link correction caused by report migration |
| Modified | docs/DEVELOPER_GUIDE.md | Active link correction caused by report migration |
| Modified | docs/PERMISSIONS.md | Active link correction caused by report migration |

Baseline inventory: 484 tracked files. Comparing their current paths (accounting
for the moves) gives **479 byte-identical, 5 deliberately modified**. All baseline
tests, all production sources except the two modified consumers, pom.xml, every
resource/default, FORMATTING.md, and Phase-30F presentation ownership remain
unchanged. LobbyService, SetSpawnCommand, PlayerService/listener/models/settings,
Identity, Economy, Social, Friend, Clan, Moderation, Reward, OnlineReward, Quest,
DailyQuest, Warp, Blackjack, GUI, registration and reload coordination are protected.
No resource, descriptor, command, permission, module, dependency or build rule edit.
No PERF retry/sleep work, no universal repository framework, no general runner.

### Selected protected file hashes

Each value is the baseline and final SHA-256; the full 484-entry comparison is in
the local evidence manifest. All omitted baseline production/test files outside
the two stated modifications also have exact parity.

| Protected file | SHA-256 baseline = final |
|---|---|
| docs/FORMATTING.md | `5469B2DA2363781EA9C403A04658D04671C0BC65926855D5CC113270FBED1235` |
| pom.xml | `4410C0CAE35B81F3CF251D566B913947E71F0DA8EBFD7D9178900CF79D20BC5C` |
| src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableConfig.java | `DF495F05B8EC48055990CA15196DCF55E86C4CAE5DEB7D03F6C1FBC4A5D1D3C4` |
| src/main/java/dev/vapee/core/lobby/LobbyService.java | `493B999913B2460596214A05BEDA561DF3ECB50FB8604759F9DB8E933C3B6A7E` |
| src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java | `C811C58710D59EE3AC08626B94344FA9DC77D413418D2B08F35DDCB65A21F2B8` |
| src/main/java/dev/vapee/core/lobby/warp/WarpConfig.java | `7E99E338F5ED53FAFBDC92063DD2316C7F5483911A44E20673CE94B5F527FC35` |
| src/main/java/dev/vapee/core/player/PlayerListener.java | `8A5566F9BCE92955EACBF57AAF205D8764F8C584C2A14CF04A9D884534B1E9DB` |
| src/main/java/dev/vapee/core/player/PlayerService.java | `D4A418698B6AC87CDAFF0FAE40C1CE88072DC7383FD17CA0D198B6FD32133A3F` |
| src/main/java/dev/vapee/core/presentation/PlaytimeFormatter.java | `C85CCC0B804F8C0BA59899AEB9ECB0A12A8ADA862BE9CC87CBE38869A907705D` |
| src/main/java/dev/vapee/core/presentation/PresentationListener.java | `91A6D55030E224D9686ABE1F99DE7933E7407B9400DDCB9F086745C6FADE4601` |
| src/main/java/dev/vapee/core/presentation/PresentationModule.java | `34FD723B9B509CF81FFEE7448576B2D82163432AE3208A221677FF1CDCCA6F57` |
| src/main/java/dev/vapee/core/presentation/PresentationRenderer.java | `00F911E014AF4866A0BA1272DCC4C3AE88B32314F901780485584AD56E1C6625` |
| src/main/java/dev/vapee/core/presentation/PresentationService.java | `CDB165CA860F21A163E980A57B376DAC0327A8D8AF595544F470C0FEA4D06261` |
| src/main/java/dev/vapee/core/presentation/config/PresentationConfig.java | `86C755BF7D9D431652A2725FBB50E0CC9C77620FB90C5027A273BAF42C0D33A2` |
| src/main/java/dev/vapee/core/presentation/scoreboard/ScoreboardService.java | `529268814D1688D2A52E7AAB9A1C7BAC94B8EE298D7BF11F539228CDD0720B46` |
| src/main/java/dev/vapee/core/presentation/tablist/TablistService.java | `2526E5F3A9B7D0AFC07D05BDB69B1F853A487B7B7530428335082A0F8B8CD595` |
| src/main/resources/blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| src/main/resources/chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| src/main/resources/config.yml | `37ECEFAC7B0EFCFB006718D4A350066138733809DD53C4B2FD2DA5E585566DC4` |
| src/main/resources/daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| src/main/resources/lobby.yml | `AC6B3FD2FD2840E41564F521E83571CD22CE429DC41EDF33CE47B33812B82F6C` |
| src/main/resources/plugin.yml | `1E4A9591559E669CD5753812885B0604A03CE070F48BD1DE39AD95B505D9DF5C` |
| src/main/resources/presentation.yml | `9EB8A3362622549C1228DBDDF8ED0D17ABB19A3AA7DFD13E5C4D7F5F1284BE2C` |
| src/main/resources/private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| src/main/resources/warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |

## Per-process regression inventory

Every row exited 0 with the stated check count; no skipped process. Focused marks
membership of the earlier 66-process run (also all exit 0). Package prefix
dev.vapee.core. is omitted for readability.

| Harness | Full checks | Focused |
|---|---:|---|
| activity.ActivityHarness | 153 |  |
| activity.blackjack.BlackjackFairnessHarness | 393 |  |
| activity.blackjack.BlackjackHarness | 317 |  |
| activity.blackjack.BlackjackPresentationHarness | 123 | yes |
| activity.blackjack.BlackjackReadViewHarness | 21 |  |
| activity.blackjack.presentation.BlackjackPreviewHarness | 16 | yes |
| activity.blackjack.table.BlackjackTableHarness | 90 |  |
| chat.ChatHarness | 17 |  |
| chat.config.ChatConfigHarness | 30 | yes |
| clan.ClanDomainHarness | 52 |  |
| clan.ClanIntegrationHarness | 15 |  |
| clan.ClanPersistenceHarness | 39 |  |
| clan.ClanServiceHarness | 136 |  |
| clan.gui.ClanCommandHarness | 99 |  |
| clan.gui.ClanMenuHarness | 84 |  |
| clan.gui.ClanMenuSecurityHarness | 114 |  |
| command.CoreCommandHarness | 75 | yes |
| command.OnlineStaffTargetGuardHarness | 268 |  |
| config.ConfigHelpersHarness | 33 | yes |
| config.ConfigServiceHarness | 93 | yes |
| config.DefaultConsistencyHarness | 12 | yes |
| economy.command.CoinsCommandHarness | 1951 | yes |
| economy.EconomyIntegrationHarness | 27 | yes |
| economy.EconomyServiceHarness | 44 | yes |
| friend.FriendCommandHarness | 96 |  |
| friend.FriendDomainHarness | 29 |  |
| friend.FriendIntegrationHarness | 28 |  |
| friend.FriendLifecycleHarness | 12 |  |
| friend.FriendPersistenceHarness | 28 |  |
| friend.FriendServiceHarness | 79 |  |
| friend.gui.FriendMenuHarness | 159 |  |
| friend.gui.FriendMenuSecurityHarness | 114 |  |
| identity.IdentityCommandArgumentHarness | 47 | yes |
| identity.IdentityHarness | 26 | yes |
| identity.ProfileHarness | 19 | yes |
| lobby.command.LobbyCommandHarness | 26 | yes |
| lobby.config.LobbyConfigHarness | 45 | yes |
| lobby.config.LobbyPersistenceHarness | 112 | yes |
| lobby.experience.navigator.NavigatorMenuHarness | 113 | yes |
| lobby.experience.navigator.NavigatorSecurityHarness | 82 | yes |
| lobby.player.LobbyHarness | 53 | yes |
| lobby.warp.command.WarpCommandHarness | 86 | yes |
| lobby.warp.WarpHarness | 118 | yes |
| message.CommandHelpHarness | 85 |  |
| moderation.command.ModerationCommandHarness | 4414 | yes |
| moderation.command.ModerationDurationHarness | 53 |  |
| moderation.ModerationBanEnforcementHarness | 31 |  |
| moderation.ModerationDomainHarness | 112 |  |
| moderation.ModerationLifecycleHarness | 149 |  |
| moderation.ModerationMuteEnforcementHarness | 52 |  |
| moderation.ModerationMuteProjectionHarness | 20 |  |
| moderation.ModerationPersistenceHarness | 523 |  |
| moderation.ModerationServiceHarness | 141 |  |
| onlinereward.OnlineRewardLifecycleHarness | 42 | yes |
| onlinereward.OnlineRewardServiceHarness | 42 | yes |
| permission.LuckPermsAsyncHarness | 16 |  |
| permission.PermissionDescriptorHarness | 842 | yes |
| player.repository.PlayerQuestPersistenceHarness | 21 | yes |
| player.repository.PlayerRewardPersistenceHarness | 11 | yes |
| player.repository.PlayerSchemaPersistenceHarness | 124 | yes |
| player.repository.PlayerVisibilityPersistenceHarness | 17 | yes |
| player.settings.PlayerSettingsServiceHarness | 28 | yes |
| player.settings.PlayerVisibilitySettingsHarness | 11 | yes |
| presence.FriendPresenceNotifierHarness | 8 |  |
| presence.PresenceLifecycleHarness | 14 |  |
| presence.PresenceServiceHarness | 15 |  |
| presentation.config.PresentationConfigHarness | 32 | yes |
| presentation.PlaytimeFormatterHarness | 14 | yes |
| presentation.PresentationHarness | 10 | yes |
| presentation.PresentationLifecycleHarness | 38 | yes |
| presentation.scoreboard.ScoreboardOwnershipHarness | 17 | yes |
| presentation.tablist.TablistOwnershipHarness | 58 | yes |
| privatemessage.config.PrivateMessageConfigHarness | 26 | yes |
| privatemessage.PrivateMessageSocialHarness | 117 |  |
| quest.daily.command.QuestCommandHarness | 45 | yes |
| quest.daily.DailyQuestConfigHarness | 35 | yes |
| quest.daily.DailyQuestCycleSelectorHarness | 18 | yes |
| quest.daily.DailyQuestLifecycleHarness | 16 | yes |
| quest.daily.menu.QuestMenuHarness | 20 | yes |
| quest.daily.menu.QuestMenuSecurityHarness | 45 | yes |
| quest.DailyQuestServiceHarness | 20 | yes |
| quest.QuestDefinitionHarness | 29 | yes |
| quest.QuestLifecycleHarness | 42 | yes |
| quest.QuestPlaytimeProducerHarness | 18 | yes |
| quest.QuestProgressReporterHarness | 18 | yes |
| quest.QuestServiceHarness | 44 | yes |
| rank.RankCommandHarness | 16 |  |
| rank.RanksCommandHarness | 12 |  |
| rank.RankServiceHarness | 23 |  |
| rank.staff.StaffHierarchyServiceHarness | 109 |  |
| repository.RepoLinkHarness | 22 | yes |
| reward.RewardLifecycleHarness | 40 | yes |
| reward.RewardServiceHarness | 126 | yes |
| seat.SeatHarness | 52 |  |
| settings.command.SettingsCommandHarness | 36 | yes |
| settings.SettingsMenuHarness | 66 | yes |
| settings.SettingsMenuSecurityHarness | 111 | yes |
| settings.visibility.SettingsModuleLifecycleHarness | 23 | yes |
| settings.visibility.SettingsNavigationHarness | 17 | yes |
| settings.visibility.VisibilityMenuSecurityHarness | 195 | yes |
| settings.visibility.VisibilitySettingsMenuHarness | 29 | yes |
| settings.visibility.VisiblePlayersMenuHarness | 74 | yes |
| ui.PaginationHarness | 1409 |  |
| ui.UiItemsHarness | 17 |  |
| utility.command.BuildCommandHarness | 17 |  |
| utility.command.NameSuggestionsHarness | 8 |  |
| utility.command.TeleportCommandHarness | 894 |  |
| utility.command.UtilityCommandHarness | 1302 |  |
| utility.TeleportParserHarness | 58 |  |
| utility.UtilityInventoryHarness | 742 |  |
| utility.UtilityModuleLifecycleHarness | 452 |  |
| utility.UtilityServiceHarness | 35 |  |
| visibility.FriendVisibilityRefreshHarness | 11 | yes |
| visibility.IgnoreVisibilityRefreshHarness | 7 | yes |
| visibility.VisibilityModuleHarness | 22 | yes |
| visibility.VisibilityPolicyHarness | 28 | yes |
| visibility.VisibilityServiceHarness | 10 | yes |
| worlddisplay.WorldDisplayHarness | 27 |  |

## Remaining findings and delivery contract

PERSIST-001 and PERSIST-002 are resolved with the explicit interruption limits
above. Exactly seven audit findings remain open: **SEC-001, DISPLAY-001, LIFE-002,
PERF-001, TEST-001, TEST-002, DOC-001**. Phase 32 was not started. Previously closed
MOD-001, API-001, GUI-001/002, LIFE-001, CONFIG-001, CMD-001, DUP-001, LEGACY-001 and
PRES-001/002 are protected by unchanged source and the complete regression.

The final diff is audited using status, diff --check, stat, name-status and full
diff including staged renames and new files. Each change belongs to one of the
six requested scope categories in the inventories above. Evidence scripts, logs,
JARs, live data and mutation controls remain ignored and are not committed.

Exactly one local commit is required after this report and the completed checks:
**Phase 31 - Persistence and Migration Hardening**, on the original
phase/31-persistence-migration-hardening branch, with baseline
a3d77128ec153d5941e038572a5b93537226dfbf as its parent. Post-commit verification
records the commit SHA, distance 1, clean tracked/untracked working tree and no push
in the final delivery and ignored phase31-commit-receipt.json. The containing
commit's SHA cannot be embedded in its own report without changing that SHA.
No push, merge, rebase, branch creation or worktree creation is performed.

Local evidence lives under ignored dev-server/.vapeecore-dev/: phase31 baseline,
report migration, final hash, focused/full per-process results and logs, four
negative-control results/logs, final Maven log, artifact hash, full Paper log,
before/after live hashes and smoke summary. The tables above preserve the essential
results in the committed report independently of that local evidence.
