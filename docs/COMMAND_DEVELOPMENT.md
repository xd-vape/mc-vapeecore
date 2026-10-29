# Command development

This guide describes current implementation contracts. Start with the owning feature, not a new framework. `plugin.yml` remains the only root-command registry. [ARCHITECTURE.md](ARCHITECTURE.md) explains ownership; [PERMISSIONS.md](PERMISSIONS.md) is the public permission contract; [CODE_STYLE.md](CODE_STYLE.md) covers formatting and verification.

## Ownership and reference implementations

| Owner | Responsibility |
|---|---|
| `src/main/resources/plugin.yml` | Root name, aliases, usage, base permission; public permission defaults/children |
| Feature module | Acquire current services, install executor and completer, roll back partial starts, remove owned bindings |
| Feature command | Authorize, validate sender/arguments, route, call services, translate result/usage feedback |
| Feature service | Domain rules, state, persistence/rollback and successful mutation authority |
| `CommandHelpRenderer` with Entry/Page/Section | Localized, permission-filtered help with click-to-suggest syntax |
| `MessageService` / localization catalogs | Recipient-specific language, safe components/placeholders |
| Harnesses | Descriptor, behavior, permission, sender, completion, localization and lifecycle contracts |

Categories overlap; admin-only describes authority, not another parser abstraction.

| Category | Reference | Important differences |
|---|---|---|
| Simple root | `lobby/command/SpawnCommand`, `quest/daily/command/DailyQuestCommand` | `/quests` requires a permitted player and no arguments; returns an empty completion list |
| Root with subcommands | `lobby/warp/command/WarpCommand`; `friend/command/FriendCommand`; `clan/command/ClanCommand` | Warp is the bounded metadata pilot. Friends/clans have relationship/member-dependent choices and player-only flows; do not flatten these into Warp's model |
| Admin-only | `lobby/warp/command/WarpCommand` | Warp has one permission for all actions and only `set` needs a player's location |
| Delegated root action | `command/CoreCommand` -> `quest/daily/command/CoreQuestCommand` | `/core quest force` has explicit wiring in `VapeeCore#registerCommands`, argument slicing, its own authorization and target guard. Do not create a second root or change its semantics |
| Known online/offline target | `identity/command/ProfileCommand`, `economy/command/CoinsCommand`, moderation commands | Profile reads known identities, including offline data. Coins GET can read known offline balances; mutations require online targets and the existing staff policy |
| Online target / movement grammar | `utility/OnlinePlayerResolver`, `utility/command/TeleportCommand`, `command/OnlineStaffTargetGuard` | Exact online name resolution, source/destination distinction, Others capabilities, staff hierarchy and activity guards belong to the existing command/domain paths |

Runtime permission checks remain necessary even when Bukkit's descriptor checks permissions. Help/completion hiding is not execution authorization. `/core` intentionally has public general actions and restricted admin actions, so a root permission is not mandatory for every category.

Prototype Step 02 removes the `/blackjack` descriptor, executor, permission and help entries. Warp keeps its existing localized Information/Management titles under `warp.help.section.*`.

## Add a root only when justified

Prefer an existing root when domain, discoverability and help/authority fit. A new independent entry point such as `/quests` can justify a new root.

1. Declare its canonical name, aliases, usage and applicable base permission in `plugin.yml`. Do not infer runtime aliases from Java class names.
2. Add a command adapter to the owning feature and wire current services from that feature's module. Implement `TabExecutor`, or the existing separate executor/completer interfaces where appropriate. No registration, persistence implementation or service lookup in the handler.
3. Authorize execution before reading sensitive state or resolving targets. Validate player/console eligibility, argument counts and parsing before calling services. Preserve result-specific failures and report success only after the service commits.
4. Reuse Entry/Page/Section and `CommandHelpRenderer` for multi-action help. A short usage response may remain short. Use `Translations.tr`/MessageService and both existing EN/DE catalogs for actual new texts; never concatenate user input into MiniMessage.
5. Bind executor and completer as one owned acquisition in the module. Missing descriptors must identify the command and `plugin.yml`. Clean up partial binding, then publish runtime references. Include late startup steps such as logging in the protected boundary.
6. Extend descriptor, command, permission, completion, localization and lifecycle tests. A new declared root must update the independent descriptor oracle in `PermissionDescriptorHarness` intentionally; do not weaken it.

## Add a Warp subcommand

The authoritative discovery list is package-local `WarpSubcommand`. Each row owns one canonical token, argument syntax suffix, existing description key, help section and whether it completes existing Warp IDs. `HELP` is deliberately a top-level action without its own help entry. All ten existing actions appear in root completion; the other nine appear in help. Enum declaration order determines order within the three existing help sections. Root suggestions are sorted separately.

For a hypothetical ID-based action (not implemented in production):

1. Add one enum row, for example `INSPECT("inspect", " <id>", "warp.help.warp.inspect.description", Section.INFORMATION, true)`. Add EN/DE descriptions only if a new user-visible description is needed.
2. Add an explicit `case INSPECT` to WarpCommand's exhaustive switch expression and route it to a named `inspect(sender, args)` method. Missing execution handling is a compiler error. Keep validation and domain-specific routing readable; no anonymous handler registry.
3. Implement that method: validate the existing syntax grammar, use `WarpSubcommand.INSPECT.syntax()` for usage, call the appropriate service and report its actual result. Business rules and file writes belong to WarpService, not this enum or command.
4. Extend the frozen public-contract expectations and behavior cases in `WarpCommandContractHarness`; retain/update `WarpCommandHarness` and related help tests. Verify help, completion, console/player contracts, invalid arguments, permission denial, persisted success and save failure. Do not simply derive all test expectations from the same enum.

For an ordinary ID action, help and ID/root completion follow the row automatically. An action requiring genuinely new context completion (like the existing ICON material argument) also needs a small explicit branch in `onTabComplete`; document and test that extra location. SHOW/HIDE intentionally filter current visibility in the enum's Warp-specific candidate predicate. Syntax metadata does not replace argument validation.

### Measured Java edit locations

For an ordinary hypothetical `<id>` action, before: top-level token list, help entry/syntax, string dispatch case, action method including usage, and the ID-completion token list (**5 locations**, token spelled in **5 definitions**). Now: enum row, typed dispatch case, named action method (**3 locations**, canonical token string in **1 definition**). Translation and behavior-test edits are required in both designs and are excluded from these Java counts. The enum constant is still deliberately referenced by dispatch/usage; symbolic references are not duplicate token definitions.

The tradeoff is one small Warp-local metadata type and an explicit dispatch case. Dispatch is not hidden behind callbacks. The exhaustive switch forces a routing decision, and independent runtime tests detect incorrect routing, completion omissions and help drift. Nothing requires other feature commands to adopt this enum.

## Permissions and targets

A genuinely new capability may require a new node; a new spelling for an existing operation does not automatically require one. If adding a node, update `plugin.yml` defaults/children, runtime authorization, `docs/PERMISSIONS.md`, help/completion visibility and independent descriptor/behavior tests together. Choose public vs operator defaults deliberately; do not widen existing child grants.

Warp keeps `vapeecore.warp.admin` centrally enforced before execution and completion; generated help entries also reference that same node. All current actions share it. Adding a distinct capability later requires an explicit reviewed policy; the metadata enum is not a new permission authority.

Use existing identity/target services. `OnlinePlayerResolver` accepts a complete case-insensitive online name, rejects partial/offline fallback and offers filtered online suggestions. Profile/known-identity commands can have different UUID/offline semantics; Friend and Clan also resolve domain-known identities. Do not add network/offline-player lookups to tab completion. For guarded online mutations, preserve `OnlineStaffTargetGuard` after capability and exact target resolution. Moderation's offline policy remains its own existing authority. Teleport source and destination are not interchangeable staff targets.

## Completion conventions

- Authorize and apply sender restrictions before inspecting candidate state. Return an empty list on denied/inapplicable contexts; returning null may enable Bukkit fallback suggestions.
- Normalize command/prefix comparisons using `Locale.ROOT`; preserve canonical candidate spelling. Preserve stable feature ordering and avoid duplicate suggestions where the candidate source can duplicate them.
- Warp prefix filtering is case-insensitive, sorted with `String.CASE_INSENSITIVE_ORDER`, capped at its existing 100 suggestions. Warp IDs come from the unique service IDs; material names come from the Material enum filtered through `WarpPoint.isDisplayableItem`.
- Warp only offers existing IDs to remove/info/name/icon/show/hide/order. Show offers hidden IDs, hide visible IDs; ICON's third argument offers materials. No fabricated order/name suggestions.
- Candidate eligibility belongs to the feature: Friend/Clan relationship state, loaded identity, exact online presence, Others permission and staff rules must not be replaced by a global flat list.
- `NameSuggestions` is an existing utility-local filter used at multiple real Utility call sites; `OnlinePlayerResolver.suggest` also excludes null/offline/self candidates where requested and deduplicates names. Preserve each caller's authority and existing limits rather than introducing a new suggestion engine.

## Help and localization

Help metadata retains stable literal syntax; descriptions/titles/footer use existing translation templates. `CommandHelpRenderer` filters entry permissions, omits empty sections, creates SUGGEST_COMMAND click events and resolves the recipient's language. Deny a protected help invocation at the command boundary too. The Warp HELP action is intentionally not repeated inside the nine-entry help page.

Use safe `Component.text` values in translated placeholders for names, IDs and arbitrary input. Existing operator text/translation overrides are not overwritten by a Java extension. New translated text requires EN/DE key/placeholder parity; syntax tokens and permission nodes are not translated. Do not add a second help renderer or localization layer.

## Binding lifecycle and current limits

WarpModule owns one command pair and its current WarpService. It tracks the full start through binding, publication and logging. Failed enable restores Bukkit's plugin fallback executor, clears the completer and releases the new service; cleanup attempts independent steps and preserves the original exception with suppressed cleanup errors. Disable clears only owned bindings/runtime; repeated disable is safe and re-enable acquires a fresh executor/service. Root declarations remain in Bukkit/plugin.yml. No global unregister or duplicate runtime registry is needed.

For several roots, consult UtilityModule's existing module-local acquisition/cleanup list; it owns command pairs before either setter. SettingsModule additionally owns three menus/listeners/observers and protects their complete startup boundary. These are responsibility patterns, not a new shared binding API.

FriendModule/ClanModule currently publish some menu-observer startup after their registration catch; those late-start paths remain future audit work. The Core root is owned by the composition root and delegates Quest administration. Do not copy incomplete rollback as a recommended pattern or migrate those features incidentally with a command change.

Warp's package-local BindingStage hook lets lifecycle tests fail immediately after a real executor or completer binding. Production's public constructor supplies a no-op. Test the real module/PluginCommand, not a mock cleanup implementation; preserve fallback semantics and prove retry binds the current runtime. A controlled post-publication logging fault is tested too.

## Verify and finish

For focused work, compile with Maven and run the relevant harnesses with the classpath generated by `scripts/run-harnesses.ps1`. Warp references: WarpCommandContractHarness, WarpCommandHarness, WarpModuleLifecycleHarness, CommandHelpHarness, PermissionDescriptorHarness and Warp/Navigator domain/security harnesses. Compilation/Maven alone does not execute the standalone behavior suite.

Always finish with:

```powershell
pwsh -File .\scripts\verify.ps1
# Existing optional offline/custom repository flags:
pwsh -File .\scripts\verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
```

This runs clean package, the unchanged limited Spotless check and every discovered harness. Command-facing production changes also need an isolated native Paper smoke: start with LuckPerms, verify authorization/help/completion/sender behavior, stop cleanly, inspect ERROR/SEVERE. Use disposable files for mutations; never change operator warps, profiles, languages or deployed JAR. An automated probe is not visual client acceptance.

Contributor checklist:

- Correct owner/category, smallest necessary root/subcommand change.
- Descriptor/aliases/permissions/children and runtime authorization agree.
- Sender/target/argument rules preserved; state and persistence remain in services.
- Existing help/localization reused, stable syntax and safe input components.
- Authorized, contextual, stable completion; no denied information leak.
- Complete binding rollback, safe disable and fresh re-enable proven.
- Independent positive/negative contracts, full verification and appropriate native smoke passed.
- Diff contains only intended source/docs/tests; no operator data, temporary artifacts or broad formatting.
