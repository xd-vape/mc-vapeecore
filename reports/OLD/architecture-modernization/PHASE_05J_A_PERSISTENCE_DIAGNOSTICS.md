# Phase 5J-A — Persistence Diagnostic Non-Interference

## Baseline, contracts and scope

Clean integrated master `aac62b8b060e952bf8dc61e06bb1933a33775b4f`, branch
`phase/persistence-diagnostic-safety`, repository `xd-vape/mc-vapeecore`. Preflight checked status,
branch, HEAD, twelve commits and worktrees. No applicable AGENTS.md was present. No stash, recovery
branch/bundle, sync-conflict evidence or Git metadata was changed or repaired.

Baseline `scripts/verify.ps1`: **204 discovered/executed/passed harnesses, 32,814 checks,
Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**. Production edits
started only after successful baseline verification and unchanged-class reproductions.

Toolchain: Oracle JDK **21.0.12.1+1-LTS-4**, Maven **3.9.16** from IntelliJ IDEA 2026.2.3,
PowerShell **7.6.5**, Windows 11. Spotless **2.43.0**, Google Java Format **1.22.0/AOSP**.
The existing six-file pilot, Maven configuration and verification/discovery scripts are unchanged.
The three new Java test files were individually formatted with the pinned formatter.

Reviewed architecture, developer, lifecycle, persistence, dependency and code-style contracts,
README, Phase 5H G4/G5 and proposed 5J-A, Phase 5I and relevant 5B/5C/5F ownership contracts.
Current source, not historical assumptions, defines the measured behavior. Read-only review includes
Player/Economy/Reward/Quest/module owners, schema/Settings codec and existing persistence harnesses.

Only production changes: `persistence/SafeFileWriter.java` and
`player/repository/FilePlayerRepository.java`. No domain, schema, permission, command, config,
scheduler, reload-participant, module-order or global shutdown-policy change. No new production
repository, wallet, file format, journal, thread, transaction manager or generic logging framework.

## Unchanged-class reproductions

External `PersistenceBeforeProbe` compiled against the unchanged production classes, the existing
`PersistenceFaults` seam, actual PlayerService/EconomyService and real temporary files. Measurements
precede teardown; exact source, compile/output logs and all profile/recovery snapshots remain external.
The initial probe compilation used inaccessible CoinWallet mutation and was corrected to the public
Economy deferred API before execution. That setup failure is retained and is not reproduction evidence.
The successful unchanged-class probe executes **11 measured assertions**.

| Probe | Exact measured original result |
|---|---|
| G4 | Real atomic IOException `injected ordinary atomic error`; repository caller receives the identical injected logger IllegalStateException with null cause/empty suppressed list. Original IO is absent from its tree. Original target bytes unchanged, loaded A wallet 11; independent B later commits 22. |
| G5 temporary cleanup | Real validation and atomic replacement complete; cleanup IOException then WARNING logger throw, followed by repository SEVERE. Actual Economy add +9 returns logger failure, RAM rolls back to 0, file and independent cold repository read contain 9. No recovery marker. |
| G5 fallback backup cleanup | Atomic unsupported, exact backup, successful fallback replacement, backup deletion failure, WARNING logger throw, candidate cleanup and repository SEVERE. RAM rolls back to 0, physical file contains 9, old recovery backup remains. A normal cold repository read is correctly blocked by that marker. |
| Replacement + restore + logger failure | Fallback damages destination then throws original replacement IOException; restore throws a second IOException; original has restore suppressed. SEVERE logger then replaces the exception returned to the caller. Recovery bytes remain exact; writer and repository refuse subsequent access. |

G4 identity changes at `FilePlayerRepository.storageFailure`: it logs before creating the intended
IllegalStateException. The two adjacent invalid-error constructors have the same ordering hazard.
G5 changes an established success into failure at the WARNING inside `SafeFileWriter.cleanup`;
Economy correctly follows its existing failed-save rollback boundary and thereby diverges from the
already committed file. During failed fallback restoration, unguarded SEVERE logging masks the original
replacement failure. None of these observations claims unconditional Coin loss or power-loss safety.

## Result boundary and production correction

| Outcome | Meaning and retained behavior |
|---|---|
| A: successful commit | Candidate serialization/validation, exact-text verification, source protection and replacement returned successfully; repository may publish its name index. |
| B: failed before commit | Genuine validation, source, backup or replacement error propagates. Existing immediate-domain rollback and deferred dirty retention remain. |
| C: uncertain external result | File operation throws after possibly taking effect. Independent new bytes are evidence of an effect, not proof of successful return. No success or name-index publication is manufactured. |
| D: successful commit, incomplete cleanup | Replacement returned; obsolete artifact deletion failed. Cleanup WARNING is attempted, but a logger RuntimeException cannot cause failed-save return, monetary replay or false rollback. |

`SafeFileWriter.cleanup` still catches the existing IOException/RuntimeException deletion failures.
It narrowly guards that WARNING and attaches a diagnostic RuntimeException to the cleanup exception
when safe. It neither retries replacement nor maintains a second diagnostic cache. Artifacts that
actually remain are not deleted through an alternate path. A successful real move usually consumed
the candidate already, so an injected later cleanup failure need not leave a temporary file.

Failed fallback restoration still preserves the original replacement exception as primary, with
restoration suppressed and the recovery copy retained. Its SEVERE diagnostic is guarded and a throwing
diagnostic becomes another suppressed exception. Identity checks avoid self-suppression. No recovery
copy is deleted or overwritten after failed restoration. Backup copy without REPLACE_EXISTING also
protects evidence appearing after the initial recovery check.

FilePlayerRepository's storage/invalid-storage/invalid-player constructors now construct the intended
IllegalStateException first. A narrow repository-local reporting helper attempts the same contextual
SEVERE message with the original cause and attaches diagnostic failure secondarily. Existing public
classification, operation/path context and validation refusal are preserved; no profile contents are
added to logs. The name index remains after writer return, including confirmed cleanup-warning commits.

Actual file transaction steps, FileAccess interface, candidate validation, exact-text check,
beforeReplace/source-change check, preferred atomic move, fallback only on AtomicMoveNotSupportedException,
backup/restoration, marker refusal and legacy migration protection are unchanged. Fatal JVM Errors
are not swallowed; these guards address ordinary external diagnostic RuntimeExceptions.

## Domain and recovery compatibility

Economy add/remove/set keep their actual algorithms and persistence boundaries. Confirmed D saves
retain the new wallet and independent file value; no monetary call is replayed. Genuine B failures
restore the prior wallet, preserve prior bytes, return failure and retain original IO/diagnostic causes.
The C test deliberately observes file 17 with rolled-back RAM 10 after a move takes effect then throws;
that uncertainty remains a separate reconciliation problem, not a successful commit inferred from bytes.

Settings and Social keep save/rollback and post-commit notification rules. Failed Settings saves emit
no successful observer event. Successful cleanup-warning saves keep the value and notify once after
committed bytes are visible. Social ignore/unignore likewise preserves exact membership, derived
snapshot publication and notification boundaries. No domain code changes.

Real Reward/Quest services and modules use the corrected repository boundary. A genuine A failure
still permits B to save, retains A dirty and reports the original IO plus diagnostic causes through
strict shutdown. An explicit inactive retry saves the original aggregate without reward replay.
Confirmed D saves do not invent strict-flush failure or dirty retention. Public Reward log-and-continue,
Quest completion states, shared Player aggregates and Player strict fallback remain intact.

A backup that cannot be deleted after a successful fallback is obsolete evidence, but its filename
still marks unresolved recovery under the existing contract. The current save/index succeeds; later
reads/writes remain blocked until deliberate reconciliation. Tests first assert exact backup bytes
and refusal, then explicitly remove only their disposable known-obsolete marker to permit a cold read.
They never silently remove a failed-restoration marker to claim successful persistence.

Legacy/current schema remains 0/1. Migration copies preserve exact original bytes; conflicting backups
still refuse replacement. Unknown YAML extensions survive. Invalid/future schema and malformed files
remain refused. Player unload keeps the same cached aggregate after a genuine failure and evicts only
after established success. The global module manager and its fallback/retry policy remain unchanged.

## Behavioral coverage

Two new canonical entries use real temporary FilePlayerRepository/SafeFileWriter/NIO operations:
`SafeFileWriterDiagnosticHarness` and `PlayerPersistenceDiagnosticHarness`. Support-only
`PersistenceDiagnosticFixture` composes the existing unchanged `PersistenceFaults` injector, recording
actual IO exception identity, operation sequence, commits, diagnostics and bytes. Controlled Bukkit
module cases reuse the existing RewardShutdownFixture. Observations precede fixture safety deletion.

| Cases | Executed coverage |
|---|---|
| D1–D2 | writer commits, repository index/economy: actual atomic and unsupported-only fallback |
| D3–D4 | writer precommit and repository schema: semantic/corrupt candidate refusal, exact-text check and actual IO propagation |
| D5–D6 | writer precommit / repository schema: source conflict, future/invalid/malformed schema with throwing diagnostics |
| D7–D10 | writer commits / economy / index: temp and backup cleanup with normal/throwing WARNING; IO and runtime deletion failures |
| D11–D17 | writer restoration/recovery plus repository schema: successful/failed restore, original/secondary causes, exact recovery and blocked access; concurrent marker protection |
| D18–D19 | repository storage: actual IO, invalid path/domain validation, exact cause/context and secondary logger; invalidStorage constructor also directly invoked because its post-create race is not manufactured |
| D20–D24 | repository economy: add/remove/set, prior 10, normal/fallback/cleanup commits, genuine precommit rollback, cold reads and single monetary operation |
| D25–D26 | repository index: canonical filename, stored name, case-insensitive index, confirmed commit and failed rename |
| D27–D28 | repository schema: exact migration bytes, current-save preservation and conflicting evidence refusal |
| D29–D31 | repository settingsSocial: real aggregate rollback/retention, committed observer timing, ignore/unignore snapshot and notification behavior |
| D32–D34 | repository deferred/modules: public/strict Reward, strict Quest, real module shutdown, independent UUIDs, original errors, retained dirty work and explicit retry |
| D35–D36 | repository unload: failed cache retention, successful save-before-eviction and Player strict fallback |
| D37–D39 | repository schema / existing contracts: unknown extensions, unchanged schema/file set and existing FileAccess implementation compatibility |
| D40 | writer/repository uncertain: real move then exception, physical new bytes, failed return, unchanged index publication boundary and no manufactured success |

Focused final counts: **129 writer checks + 233 repository/domain checks = 362 new behavioral checks**.
No source-string assertions substitute for persistence behavior. Existing 204 harness entry points and
all prior behavioral checks remain; no discovery rule or previous assertion was removed or weakened.

`scripts/tests/persistence-diagnostics.Tests.ps1`: **14/14 compiled negative controls detected**.
All variants compile with exit 0, execute the intended scenario and fail its named AssertionError
with exit 1. Temporary class overlays never replace production source or canonical class files.
Positive controls pass before and after: **13 actual harnesses / 2,030 checks in each round**.
Complete compile/behavior/positive logs and the result receipt are preserved externally.

| Control | Detected defect |
|---|---|
| N1 | Cleanup diagnostic escapes a confirmed successful replacement |
| N2 | Repository returns the logger error instead of its original IO cause |
| N3 | Restore diagnostic masks original replacement failure |
| N4 | Restoration exception is discarded |
| N5 | Failed restoration deletes its recovery evidence |
| N6 | Genuine repository save failure is returned as success |
| N7 | Candidate semantic validation is skipped |
| N8 | Attempted name is indexed before commit |
| N9 | Ordinary atomic IOException is treated as unsupported atomic move |
| N10 | Source-protection callback is skipped |
| N11 | Exact serialized-text verification is disabled |
| N12 | Confirmed successful name-index publication is lost |
| N13 | Concurrent unresolved backup is overwritten |
| N14 | Initial recovery-marker refusal is bypassed |

## Native Paper acceptance

Native Paper **1.21.11 build 132**, LuckPerms **5.5.84**, Java 21 and the final built JAR ran
on loopback in two separate fresh disposable compositions. Each composition executes two separate
JVMs, established by persisted first-process markers and distinct runtime tokens. Tested JAR SHA-256:
`359a827bf499ee2fb9932022fd07f69f877e3a4da6261206171ab3f906d12994`.

| Composition | First JVM | Separate second JVM | Diagnostics |
|---|---|---|---|
| Clean | 25 checks, exit 0, final A=41/B=0 | 28 checks, exit 0; cold A=41 then normal operations finish A=34/B=0 | Zero ERROR/SEVERE in both consoles |
| Controlled faults, separate fresh data | 27 checks, exit 0, final reconciled A=14/B=0 | 16 checks, exit 0, cold A=14/B=0 | Nine expected throwing diagnostics captured by the test-only logger in JVM 1; zero in JVM 2; zero unexpected console ERROR/SEVERE |

Totals: **53 clean + 43 controlled-fault checks, four successful process exits**. All JVMs assert
all **28 exact active production modules/order**, **seven reload participants**, one actual Reward
LOWEST handler, native synchronous Reward **20** / Quest **100** tick periods and five base tasks.

Clean acceptance calls the actual Economy service: add 10, set 30, remove 4, with independent file
reads at each step. A deferred Reward +5 leaves file 26 until the existing real CraftTask executes
its unchanged production Runnable on Paper's main thread, committing 31. An observing test wrapper
then repeats that Runnable without changing bytes or granting again. Actual DailyQuest sync installs
the cycle/assignment, Quest completion grants +7 once, and pending Reward +3 commits on shutdown.
JVM 2 loads completed Quest/cycle and wallet 41, repeats normal Economy/timer operations and confirms
Quest cannot re-grant its completed reward; shutdown finishes at 34.

The controlled composition injects FileAccess and Logger only into the **existing** FilePlayerRepository
instance, preserving PlayerService, aggregate identity and name-index ownership. Original files/writer/
logger references are restored before ordinary shutdown; no owner is hot-reloaded or replaced. Actual
atomic IO refusal with throwing SEVERE preserves the exact IOException and rolls add back to 10.
Confirmed temp-cleanup/WARNING failure leaves add committed at 17; confirmed fallback plus backup
cleanup/WARNING failure leaves remove committed at 14. Exact obsolete backup bytes are preserved,
and subsequent repository reads/writes correctly refuse the marker.

Actual fallback replacement damage followed by failed restoration and throwing SEVERE retains the
original replacement cause, suppressed restore/diagnostic exceptions, wallet 14 and exact recovery
bytes. Repository reads/writes remain blocked. The test explicitly saves both obsolete/failed-restore
backup evidence outside the profile directory, reconciles only its known disposable data with exact
recovery bytes and performs no repeated monetary mutation. It checks independent B bytes are unchanged.
The cold second JVM reads that deliberately reconciled 14; this is not automatic production recovery.

Exactly **two WARNING and seven SEVERE diagnostic attempts** throw in the controlled test logger:
precommit storage report, temp/backup cleanup warnings, blocked obsolete-marker read/write, restore
and repository failure reports, blocked failed-recovery read/write. Records are retained in
`phase05ja-controlled-diagnostics-1.txt`; the exception count is asserted in the receipt. The real
plugin/Paper logger is not globally filtered or disabled. Both deliberately faulted and clean
compositions have zero unexpected console ERROR/SEVERE, with expected injected diagnostics reported
separately rather than mislabeled absent.

A logger observer at actual reverse ModuleManager shutdown confirms consumers/Reward are retired,
exact Reward resources are released and expected Coins are stored while original Player remains
usable. All core handlers/tasks are gone before Paper-wide safety cleanup. Normal Player fallback
then agrees. Schema remains 1, the unknown extension survives and exactly two synthetic UUID YAMLs
exist. No extra persistence file or unintended independent profile mutation is introduced.

These are native API/service/NIO assertions with directly loaded synthetic profiles, reflection at
test-only external boundaries and an observing scheduler wrapper. There is no connected Minecraft
client, network login/quit acceptance or visual-client claim. Uncertain-after-effect move and invalid
constructor corner cases are additionally covered in the executable non-native integrations. No
plugin hot reload or global disable/enable substitutes for a new JVM.

Sources, runner, compilation logs, both consoles/receipts per composition, exact profiles/configs,
controlled diagnostics, recovery evidence and binary hashes remain external. The two newly created
server directories were removed only after confirmed exits, hash-verified profile/evidence copies
and saved configurations/source/manifests. `native-cleanup.json` records the resolved checked paths.

## Full verification and protected operator files

Final `pwsh -File ./scripts/verify.ps1`: **206 discovered/executed/passed harnesses, 33,180 checks,
Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**. All **204** baseline
entries remain, with no existing behavioral check removed or weakened. The increase of **366** is
the two new harnesses' **362** behavioral checks, three existing GUI source-audit checks for their
three new Java files (239 to 242), and one existing repository-link check (86 to 87).
Complete inventories, receipts and the per-entry comparison are retained externally.

Both new harnesses and their support fixture pass pinned GJF 1.22.0/AOSP dry-run formatting.
Mutation positives and the complete canonical run test the same final production source; a hash
comparison of every tracked production Java source confirms zero changes after final verification
started. Both native compositions' JAR hashes match the final artifact. `git diff --check` passes.

A fresh full before-inventory measured **934 operator server files** and **nine Run/top-level
development-tool files**, with relative/absolute path respectively, length and SHA-256. Worlds,
profiles, balances, Quest/DailyQuest state, installed JARs, LuckPerms, operator configuration,
migration/recovery evidence and sync-conflict files are not mutable fixtures. No installed server or
deployment script is executed. Native binaries are read-only copy sources for disposable environments.

Complete final inventories match exactly: **934/934 operator files** and **9/9 Run/development-tool
files**, with **0 added, 0 removed, 0 changed and 0 inaccessible files** in both protected sets.
All relative paths, sizes and SHA-256 values remain in the before/after JSONs and
`operator-comparison.json`. The new supplemental script is under `scripts/tests/`; no existing
top-level development/deployment script or IntelliJ run configuration changes.

Evidence root:
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase5ja-evidence-20261027`.
Retained evidence includes unchanged-class probes/compile logs/snapshots, baseline/final receipts,
formatter logs, mutation compile/behavior/positive logs, native sources/runner/consoles/receipts,
configs/profile bytes/JAR hashes and complete protected before/after comparisons.

Exact intended changed paths:

1. `src/main/java/dev/vapee/core/persistence/SafeFileWriter.java`.
2. `src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java`.
3. `src/test/java/dev/vapee/core/persistence/SafeFileWriterDiagnosticHarness.java`.
4. `src/test/java/dev/vapee/core/player/repository/PlayerPersistenceDiagnosticHarness.java`.
5. `src/test/java/dev/vapee/core/player/repository/PersistenceDiagnosticFixture.java`.
6. `scripts/tests/persistence-diagnostics.Tests.ps1`.
7. `docs/PERSISTENCE_DEVELOPMENT.md`.
8. This `reports/architecture-modernization/PHASE_05J_A_PERSISTENCE_DIAGNOSTICS.md`.

## Remaining limits and next phase

Optional-state warnings in the codec/repository, startup indexing/INFO diagnostics and unrelated
repositories keep their existing behavior. A throwing optional warning may still abort an otherwise
permissive read; that is intentionally outside these primary-error/post-commit guards. Logger failure
is secondary evidence where a Throwable is available, not a promise that an unavailable logger emitted
a visible diagnostic. No global logging disable or failure-retention infrastructure was added.

Uncertain effectful external failures remain failed returns and can still produce immediate-domain
RAM/disk disagreement. They require deliberate reconciliation; no unconditional retry, re-grant or
claim of known failure-before-effect is safe. Obsolete retained backups also require reconciliation.
The correction proves neither fsync/directory-sync nor survival of power loss/hardware failure.
Same-JVM pending owners remain memory, not durable crash recovery.

Recommended next phase: **5J-B, incomplete reporting and logger-safe manager iteration**, separately
scoped; it is not started. No ModuleManager policy, global retry, shutdown veto or unsupported hot reload.
Exactly one local commit is required: `Harden persistence diagnostics without altering commit outcomes`.
Its actual SHA and clean-tree status are returned separately. No push, merge, deployment, stash/recovery
modification or gameplay change.
