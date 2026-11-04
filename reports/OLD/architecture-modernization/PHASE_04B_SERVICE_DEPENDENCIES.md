# Phase 4B — Service Boundaries & Dependency Reduction

## Baseline and boundaries

- Clean integrated local `master`: `f7d336101f7c934148074b8821e20f8a7bb73d1c`, also the locally observed `origin/master`.
- Git ancestry verified: Phase 4A `67f427cdd81e7fc3c4e500ffc6c891a6c3bb1fce` is an ancestor. No merge/rebase/reset/cherry-pick performed.
- Branch: `phase/service-dependency-boundaries`.
- Actual canonical baseline: **192 discovered/executed/passed harnesses, 29,808 checks, Failed=0, Missing/Invalid=0**; Maven clean package and Spotless PASS.
- Production pilot: PresenceModule and its explicit composition in VapeeCore only. FriendModule's Phase-4A implementation, other modules, domain services, persistence, commands, permissions, configuration, layouts, translations and reload participants are unchanged.

## Ownership and availability

| Borrowed service | Creator / lifecycle owner | Availability and restart | Presence operations |
|---|---|---|---|
| PlayerService | PlayerModule | Getter after enable; owner saves/clears/releases at disable; new runtime on enable | `isLoaded` during seed/join |
| PlayerSettingsService | PlayerModule | Shares PlayerModule activation; new object on enable | Recipient presence opt-in |
| SocialService | SocialModule | Getter after enable; owner clears snapshots/releases at disable; new runtime on enable | Known ignore lookup in both directions |
| FriendService | FriendModule | Public getter only after successful enable; unavailable after disable; fresh repository-backed service on enable | Friends of transition subject |

Presence borrows all four and never shuts them down. Its own runtime is PresenceService, FriendPresenceNotifier and PresenceListener, plus one listener registration containing two event handlers. The notifier holds existing narrow method-reference capabilities; it creates no subscription/task/command. MessageService and JavaPlugin retain their stable direct constructor boundaries.

ModuleManager starts Player/Social/Friend before Presence and stops consumers first in reverse order. Services can change state normally while active; replacing a service object underneath an active consumer is not supported hot-swap. References alone do not confer ownership.

## Related dependency inventory — inspection only

Classification: A = genuine module-level lifecycle capability; B = one-service getter; C = several runtime services; D = existing narrow capability; E = explicit cross-feature callback/provider. Dependency ordering alone is not evidence for A: none of these consumers directly calls an upstream module's enable/disable.

| Module | Actual dependencies and classification | Acquisition / ownership / follow-up |
|---|---|---|
| Friend | Player→Settings B; Social→Social B; Identity→Identity B; live limits and FriendRequestPolicy D; feedback's identity lookup E | Enable acquisition, existing policy and live supplier retained. Feedback also reaches identity through an explicit callback. Public signature and tested Phase-4A cleanup untouched. Later reduction must preserve current runtime acquisition. |
| Visibility | Player→Settings, Social, Friend, Lobby→LobbyService B; relationship callbacks D/E | Captures exact subscribed Friend/Social instances for removal. A later provider migration must unsubscribe the captured instances, not resolve a newer target during disable. Subscription failure boundaries deserve separate review, not migration here. |
| Identity | Player→PlayerService, Rank→RankService, Economy→EconomyService B | Late enable acquisition and owned profile/command runtime. Upstream runtime replacement while active would invalidate captured references; no supported live replacement is introduced. |
| Clan | Identity→IdentityService B; limits supplier D | Separate known late observer-start/publication/logging lifecycle issue remains deferred. Do not combine a provider migration with a silent Clan lifecycle overhaul. |
| Settings | Player→Settings, Presentation, Identity, Visibility, Lobby→ItemService B; local menu callbacks E | Late service acquisition; owns three menu/listener lifetimes. Its exported menu getters/back setters are real module-level integration capabilities consumed by LobbyExperience, not one interchangeable domain service. |
| LobbyExperience | Lobby and Player C; Warp/Visibility B; Settings/Friend menu providers and back callbacks E; Activity/Utility membership/flight seams D/E | Multiple lifetimes: item registry is intentionally used during construction before Lobby enable; menu providers activate later. Cleanup resets borrowed module callbacks and removes owned observers from original services. Highest migration complexity; not a mechanical Supplier conversion. |

Priority: retain the new Presence pattern as a bounded reference; address Clan's already-known lifecycle defect separately; assess simple B-only getters next; audit Visibility's exact subscription ownership; leave LobbyExperience's mixed construction/runtime/callback boundaries for a deliberately scoped design. No other module dependency migration was implemented.

## Preferred boundary and compatibility

Before: Presence stored PlayerModule, SocialModule and FriendModule, then fetched PlayerService, PlayerSettingsService, SocialService and FriendService during enable.

After: Presence stores four typed `Supplier<Service>` fields. VapeeCore explicitly passes `playerModule::getPlayerService`, `playerModule::getPlayerSettingsService`, `socialModule::getSocialService`, `friendModule::getFriendService`. The registration list remains unchanged: **28 production modules and seven existing reload participants**; Presence remains CoreModule, not ReloadParticipant.

The original public five-argument module constructor remains and delegates through lazy validated method references. The preferred public six-argument constructor validates provider references but does not evaluate them. Each enable resolves and validates one result for each service. Null results identify the missing service; original provider exceptions propagate without masking. No fake fallback dependency is supplied.

An activation captures its four results into the actual listener/notifier. Provider changes are not consulted per event. Disable releases only Presence runtime; the next enable resolves all four again. Compatibility and composition method references still indirectly capture three module objects; this is reduced operational type coupling, not elimination of heap references or new ownership. No performance benefit is claimed.

## Directly affected lifecycle correction

Before editing production, a disposable baseline probe executed actual PresenceModule enable with a failing final startup logger. It confirmed **two leaked handlers and all three published runtime fields** after the original exception. Probe cleanup occurred only after observing/asserting the defect. The result/source are retained under `C:/Users/mehdi/AppData/Local/Temp/vapeecore-phase04b-evidence/pre-fix/`; its temporary compiled class directory was removed after consolidation.

The small correction moves publication and final logging inside the existing registration/seeding recovery boundary, clears owned runtime references on failure, and preserves the original exception. Duplicate enable of an active Presence runtime is rejected before acquiring replacement services. The ownership model and listener priorities remain unchanged. No general lifecycle framework or unrelated module fix was added.

## Actual generation A/B behavior

PresenceDependencyBoundaryHarness uses the real module, PresenceService, PresenceListener, FriendPresenceNotifier, PlayerService, PlayerSettingsService, SocialService and FriendService. Generations A/B have separate real FilePlayerRepository/FileFriendRepository files in disposable directories. Only provider targets and external Paper player/event boundaries are controlled.

- Construct with unavailable provider targets: zero resolutions. The legacy constructor also works before owner services are active.
- Enable A: one resolution per dependency; only loaded online profiles seeded, no synthetic notification.
- A has an A-only friend; a shared recipient opts out; another ignores the subject; a reverse-ignored recipient remains allowed. Expected recipients and exact existing localized messages are observed through MessageService.
- While active, provider targets change to B; notifications and loaded-player decisions still use captured A. Duplicate enable cannot replace the valid runtime.
- Disable: old handlers disappear, owned cache clears, notifier/listener/service fields release; A/B durable files and borrowed in-memory state remain identical.
- Enable B: fresh owned objects; B-only friendship, changed opt-in and both directions of ignore policy determine different actual recipients. A B-only loaded profile proves the new PlayerService is used. Changing provider targets back to A while B is active does not change those decisions.
- Only currently registered production listeners receive fixture-dispatched events. The old Presence state remains untouched. This proves active registration isolation, not magical revocation of arbitrary retained Java objects.

The harness also covers all four null results, all four provider exceptions with exact exception identity, fail-fast constructor arguments, registration after-effect failure, initial seeding failure, final log failure and corrected retry. It checks byte-for-byte persistence isolation and borrowed loaded/settings/social/friend state. No fixture removes owned registrations before assertions.

## Quantified change

| Measure | Before | After |
|---|---|---|
| Foreign whole-module types directly stored by Presence | 3 | 0 |
| Typed runtime service providers | 0 | 4 |
| Runtime service acquisition location | enable | enable |
| Required service resolutions per successful enable | 4 | 4, independently verified once each |
| Original public compatibility constructors retained | 1 | 1 |
| New preferred service constructor | 0 | 1 |
| Foreign modules indirectly retained by compatibility/root method references | 3 | 3; ownership unchanged |
| Production files changed | — | 2 |
| New framework abstractions | 0 | 0 |

Previously correct late acquisition was preserved and made independently testable; acquisition timing is not claimed as a newly invented improvement. Stale-capture and wrong-service failures are demonstrated by N2/N3; retained notifier ownership by N6. The generation proof tests outcomes, not only provider call counts.

## Tests and negative controls

Existing PresenceLifecycleHarness retains all **24 checks**, including old constructor reflection and event priorities. Its real lifecycle fixture now exercises the preferred typed constructor; the new harness independently proves the compatibility adapter through two service generations. PresenceServiceHarness and FriendPresenceNotifierHarness retain their actual domain/notification checks.

New PresenceDependencyBoundaryHarness: **292 checks passed**. All **17 focused existing harnesses passed (1,344 checks)**: PresenceLifecycle 24, PresenceService 15, FriendPresenceNotifier 8, FriendModuleLifecycle 310, FriendMenuLifecycle 31, FriendLifecycle 10, ModuleExtension 11, ModerationLifecycle 149, UtilityModuleLifecycle 452, PlayerSettingsService 28, FriendService 79, FriendDomain 29, FriendIntegration 28, FriendPersistence 28, IgnoreVisibilityRefresh 7, FriendVisibilityRefresh 11, PrivateMessageSocial 124. These executions preserve Phase-4A lifecycle evidence and existing privacy/ignore behavior.

`scripts/tests/presence-dependencies.Tests.ps1` compiles isolated temporary overlays. Each variant must compile successfully and fail its named behavior; original class files and operator data are untouched. The unchanged positive harness passes before and after all variants.

| Control | Compiled defect | Intended detected contract |
|---|---|---|
| N1 | Resolve provider in preferred constructor | Constructor laziness |
| N2 | Memoize first FriendService across activations | Actual generation-B recipients |
| N3 | Discard resolved FriendService and use an empty wrong instance | Actual generation-A friend lookup |
| N4 | Publish an owned runtime despite null PlayerService | Required dependency must fail before publication |
| N5 | Omit listener unregistration | Real HandlerList leak |
| N6 | Keep old notifier field during clearRuntime | Owned notifier release |
| N7 | Ignore false recipient opt-in in notifier | Actual generation-A authorization recipients |
| N8 | Eager Friend getter in legacy adapter | Compatibility constructor laziness |

Executed result: **8/8 detected; compile exits all 0; intended behavior exits all 1; positives 292 before and after**. N4 deliberately models a half-published runtime: simply deleting a null check would still fail downstream constructor validation and would not be meaningful evidence. N7 modifies only a temporary notifier overlay; production FriendPresenceNotifier is unchanged. Receipt: `target/verification/presence-dependencies/results.json` (saved outside target before clean).

## Full validation and native Paper

The complete repeated `scripts/verify.ps1 -Offline -MavenRepository 'C:/Users/mehdi/.m2/repository'` passed on the final source:

| Final validation | Actual result |
|---|---|
| Maven clean package | PASS |
| Spotless, unchanged six-file pilot | PASS |
| Discovered / executed / passed harnesses | 193 / 193 / 193 |
| Total checks | 30,109 |
| Failures | 0 |
| Missing/invalid | 0 |
| Baseline entry points retained | All 192 |
| New entry point | PresenceDependencyBoundaryHarness, 292 checks |

Actual receipt comparison accounts for **+1 harness and +301 checks**: new boundary harness +292; GuiAuthoringAuditHarness 224→225 for one new Java test source; RepoLinkHarness 59→67 for eight maintained documentation links. Every other baseline entry point retains its count, including the 24 PresenceLifecycle checks. No historical reference count was used as the pass criterion.

Final canonical receipt/inventory: `target/verification/harnesses/results.json` and `inventory.txt`. Baseline/final receipts, inventories, logs and supplemental controls are also preserved outside the clean-build tree under `C:/Users/mehdi/AppData/Local/Temp/vapeecore-phase04b-evidence/` and `vapeecore-phase04b-*` evidence files.

The first final attempt built successfully but Spotless rejected the pilot PresenceModule import grouping produced by the direct formatter CLI. No canonical harness run was started by that failed attempt. The authoritative existing six-file Spotless apply corrected the imports; no additional pilot file or enforcement scope was changed. The new test's imports were also put in the documented lexical order. The complete verification is repeated on that final source; the failed formatting attempt is not reported as passing.

Protected installed file paths and hashes were captured before baseline. Native acceptance used a disposable copied Paper/LuckPerms server and separate profiles/world/data. It is an API/console probe, not a Minecraft-client visual test. No real dev-server deployment took place.

Native acceptance executed on **2026-10-25, Europe/Berlin**, using Paper **1.21.11-132**, LuckPerms **5.5.84** and JDK 21. The separate server bound only to `127.0.0.1:25590`. The API/packet probe passed **39 checks at 01:57:43**. All 28 modules started in the existing order and received clean reverse shutdown. The server process exited; combined stdout/stderr contained **0 ERROR/SEVERE**. Offline-mode/version notices remain expected warnings.

The probe used four disposable native ServerPlayers with embedded network channels. It verified actual `/friend add`, `/friend accept` and `/friends list`, friendship commits, join/quit cache transitions, duplicate-transition suppression, unloaded-profile rejection, exact existing localized system-chat packet contents, recipient opt-in and both ignore directions. It invoked the actual registered PresenceListener with native join/quit event objects; it was not a client network login/visual test.

Inspection found no production consumer retaining getPresenceService results outside Presence itself, so an isolated **Presence-owned runtime** disable/re-enable was safe while upstream owners remained active. The probe verified fresh PresenceService/notifier/listener objects, exactly one two-handler registration, no notification during seeding, continued correct packet delivery, old owned state remaining empty, and unchanged borrowed profile/friend bytes during this consumer restart. This native check reused the same upstream service objects; the independent four-service **A/B replacement proof is the standalone harness**, not a claimed native hot-swap.

Actual final built and natively tested JAR SHA-256 (compared equal): `2a70865d1c825a1b884040b8fe6e80ce9d9d02989a444daa27405be6116ec2a7`. Native logs, probe source, compile log and result JSON are saved under `C:/Users/mehdi/AppData/Local/Temp/vapeecore-phase04b-evidence/native/`. The disposable server/worlds/data and ignored workspace probe were removed after process exit. The full protected installed file set and hashes of **all 22 files, including the deployed JAR, remain unchanged**.

## Exact intended changed files

- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/presence/PresenceModule.java`
- `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java`
- `src/test/java/dev/vapee/core/presence/PresenceDependencyBoundaryHarness.java` (new)
- `scripts/tests/presence-dependencies.Tests.ps1` (new)
- `docs/SERVICE_DEPENDENCIES.md` (new)
- `README.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPER_GUIDE.md`, `docs/MODULE_DEVELOPMENT.md`
- This report (new). Historical reports are unchanged.

Formatter versions and the six-file enforcement scope are unchanged. PresenceModule/PresenceLifecycleHarness remain in that existing pilot; the new Java test is formatted individually. Final pre-commit review: `git diff --check` PASS, exactly **11 intended paths** (seven modified, four new) listed above. No operator files, profiles, generated JARs, temporary worlds, compiled fault overlays or unrelated formatting enter the diff. The one local commit uses `Reduce presence module coupling through typed service providers`; its full SHA and post-commit working-tree status are returned in the completion response. No push, merge, gameplay change or Phase 4C implementation was performed.

## Deferred risks / Phase 4C handoff

1. ClanModule's late observer-start/publication/logging issue remains a separate targeted lifecycle task.
2. Other modules still use module-to-module dependencies. Prioritize actual service-only B boundaries and preserve exact observer-instance ownership in any later migration.
3. Do not apply the Presence recipe blindly to LobbyExperience's registry construction, menu callbacks and several service lifetimes.
4. A provider is not live hot-swap and the compatibility constructor still captures owner modules indirectly. Keep ordered shutdown/restart and consumer lifetime tests as requirements for any next phase.
