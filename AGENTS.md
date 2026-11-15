# VapeeCore – Codex Project Instructions

## Scope and Priorities

VapeeCore is a modular Minecraft core plugin built with Java, Maven and Paper. Follow the versions and dependencies actually declared in the repository; do not silently upgrade or widen the supported version range.

Priorities, in order:
1. Correct gameplay, player safety and preservation of data.
2. Compatibility with existing VapeeCore modules, operator configuration and live-server behavior.
3. Clear, compact, consistent player-facing UX in English and German.
4. Small, maintainable code and meaningful regression tests.

Read relevant code and documentation before editing. Preserve existing architecture, permissions, public contracts and behavior unless the task explicitly changes them. Reuse established services instead of creating parallel implementations. Do not add speculative features or dependencies.

## Player-Facing Messages: No Automatic Feature Prefixes

**New or modified ordinary player-facing messages must be prefix-free by default.** Do not prepend `[Feature]`, `[Gadgets]`, `[Player Riding]`, the plugin name, or another feature label. A message should begin with its actual content.

Examples:
- Correct: `You are now carrying <player>.`
- Correct: `<player> must be sneaking to be picked up.`
- Incorrect: `[Player Riding] You are now carrying <player>.`

This applies to gadget feedback, interaction hints, menu actions, success/failure messages, and other new or revised gameplay feedback. Do not use `MessageStyle.feature(...)`, `MessageStyle.prefix(...)`, manually concatenated `[Feature]` text, or a social-channel wrapper solely to add a prefix to such messages.

- Preserve intentionally interactive UI labels such as `[Accept]` and `[Deny]`: these are **actions**, not prefixes.
- Preserve rank prefixes, player display names, clan tags, scoreboards, TAB and nametags. They are **identity presentation**, not system-message prefixes.
- Existing Friends/Clan/Staff messages may still have legacy, tested wrappers. **Do not silently rewrite unrelated legacy output** as part of a gadget or local UX task. Remove or migrate those prefixes only in an explicitly scoped change, with tests and operator-override compatibility.
- Do not forcibly suppress an operator's explicitly configured `messages.prefix` or customized language templates. The default is prefix-free; operator-owned overrides remain authoritative until explicitly changed.
- Player Riding and future gadgets send gameplay feedback without a feature prefix. Use the existing delivery so configured global prefixes and language overrides still apply.

## Central Chat Styling

Continue to use the established `MessageStyle`, `MessageService`, Adventure components, MiniMessage tags and localization. **Removing prefixes does not remove semantic formatting.**

- Main explanation text: shared neutral text style.
- Secondary instructions: shared muted style.
- Important values, actions and commands: configured `MessageStyle.accent()` / `<accent>`.
- Status words: semantic success, warning, error, online and offline colors.
- Player names: retain their existing rank/name formatting where available. Do not flatten or recolor a formatted name just to highlight it.
- `messages.accent-color` remains the central accent source, including reload and rollback behavior.
- No arbitrary hardcoded palette inside feature classes; no competing chat formatting utility.
- Keep feedback short and contextual. Avoid chat spam, redundant lines and repeating instructions on every valid interaction.
- Dynamic player names, reasons and untrusted values must be passed as literal Adventure `Component`s; do not concatenate them into MiniMessage source.
- Preserve hover/click interactions and their server-side authorization. No formatting operation may unintentionally execute a command.
- Use localized, padded `MessageService.sendRawBlock` for messages that genuinely need multiple lines; do not wrap routine success feedback in a block.
- Respect the existing `MessageService.send` versus `sendRaw` contracts. `send` may prepend a **configured operator prefix**. Select delivery intentionally; do not create double wrappers or bypass explicitly configured operator behavior by accident.
- Match EN/DE structure, placeholder contracts and semantic colors. Keep operator-customized catalog values intact.

`docs/development/chat-design-system.md` documents existing technical contracts, including historical prefix APIs. The no-automatic-feature-prefix rule above governs **new and modified gameplay feedback**. When changing shared styling, update that document to clearly distinguish legacy compatibility from the current UX standard, without deleting still-used legacy code as a side effect.

## Commands and Permissions

- Register real command permission nodes in `plugin.yml` and document them in `docs/PERMISSIONS.md`.
- Enforce permissions at execution and sensitive target/action boundaries; hiding commands or menu items is not authorization.
- Use `MessageService.commandDenied(recipient)` for ordinary command denials and preserve explicit legacy override behavior. Do not expose technical permission or group names in player-facing denials.
- Keep specialized target, player-only, security and staff-hierarchy failures distinct from generic permission errors.
- Use `MessageStyle.usage(command, arguments)` for simple usage messages: executable tokens separated from argument syntax, mandatory arguments neutral, optional arguments muted. Preserve suggestions (not execution) and meaningful complex help.
- Root command suggestions must follow actual effective permissions; preserve foreign/unknown commands rather than applying a global whitelist.
- `/staff` remains a private panel. Keep its descriptor ungated for localized executor denials, require `vapeecore.staff.panel` for suggestions, and recheck the eligible primary staff group at execution. Do not resurrect `/staff list` or PUBLIC/HIDDEN roster controls.
- Do not reintroduce removed `/rank` or `/ranks` commands. Preserve LuckPerms ranking and relevant existing quest compatibility.
- New gadgets should have feature-specific permission checks at selection **and use**, with sensible defaults. Register only permissions belonging to implemented features; do not invent paid/VIP dependencies.
- Do not introduce new public commands without explicit scope.

## Lobby, Inventories and Gadget UX

- Keep the existing `LobbyItemRegistry`, `LobbyItemService`, PDC ownership, menu-holder identity and lobby/player-state lifecycle as the authority. No competing hotbar or inventory manager.
- Preserve configured hotbar positions, enabled/disabled items, foreign items and explicit operator choices. Never overwrite foreign inventory content, duplicate managed items or resurrect disabled features.
- The Gadgets entry item is a configurable **CHEST** named **Gadgets** in Minecraft gold/orange; its default hotbar slot is **4**. No gold ingot default.
- Gadget menus should have **compact lore**: what the gadget does and the next available action, not an entire instruction manual. Put longer gesture instructions on the equipped item or in contextual feedback.
- Distinguish item-based gadgets (need a real managed hotbar item) from direct/menu features (no hotbar item). Do not spend hotbar slots for a simple setting or toggle.
- The Step 09B baseline still replaces the Gadgets chest with the Riding saddle. Separate slots for multiple equipped item gadgets and a permanently accessible chest are the scope of Step 09B.1. Treat those capabilities as pending on branches that do not include that step.
- Step 09B.1 sets the default to **3** equipped item gadgets, with a configurable limit of **1–3**. Allocate only genuinely free and unreserved slots. A smaller physical free-slot count takes precedence over the configured limit.
- Keep gadget runtime state temporary and independently owned, with explicit item/session identity and permission checks. Do not persist arbitrary active mounts or temporary effect state to player profiles.
- Gadgets are available to regular users by default unless an explicit permission/configuration denies them. Avoid automatic rank, currency or paywall dependencies.
- Keep existing player-head and visibility gestures intact. Incompatible flight, builds, activities, other mounts and ignore/visibility policy must remain authoritative for Player Riding.
- Clean up only effects, items, passengers, attributes and state owned by VapeeCore. Respect foreign plugins and external entity ownership.

## Lifecycle and Safety

- Handle enable, partial-enable failure, reload, rollback and disable without orphaned listeners, tasks or state.
- Handle logout, kick, world changes, teleport, death, respawn, build/normal mode and activity transitions when relevant to the feature.
- Do not mutate player state asynchronously if the Paper API requires the main thread.
- Validate menu ownership, session identity, raw slot, click type and current permissions at execution time. Cancel unsafe inventory moves, drags and drops.
- Respect Paper/Bukkit event cancellation and avoid duplicate processing or unintended damage. Do not uncancel external actions unless narrowly justified for state you own.
- A feature must never silently overwrite or remove unrelated inventory items, vehicles, passengers, equipment or player data.
- Preserve compatibility with operator-defined config and language files; document necessary manual changes for existing installations rather than rewriting them unprompted.
- Avoid invasive fixes to the user's actual running server. Use isolated Paper/LuckPerms environments for integration tests.

## Testing and Evidence

- Test observable behavior, denial paths, lifecycle and regressions rather than merely implementation details.
- Add focused harness tests for each new feature or changed behavior, including authorization, item identity, double events, inventory safety, reload and cleanup when applicable.
- Test prefix-free output for newly added or modified gameplay messages **without changing unrelated legacy prefix contracts**.
- Verify both English and German localization, placeholders, accent semantics, names and interactive Component events.
- Run Maven, Spotless and the existing offline verifier as applicable to the change. Preserve existing harnesses rather than rewriting tests solely to make them pass.
- Add native Paper/LuckPerms validation when Paper events, mounts, movement or client-sensitive behavior justify it.
- Distinguish local harness checks, native Paper checks, and real Minecraft-client visual/gesture acceptance. Never claim client validation from synthetic events.
- Report exact executed tests, results, failures, skipped checks and unresolved manual acceptance items. Never label historical test evidence as newly executed.

## Git and Operator Boundaries

- Inspect the branch, HEAD, working tree, status and relevant existing edits first.
- Work on a focused local feature branch unless explicitly told otherwise.
- Preserve unrelated changes, protected files, worktrees, run configurations, receipts, operator JARs, worlds and player profiles.
- Do not discard uncommitted work or use destructive Git commands, reset/rebase, force-push or automatic cleanup without authorization.
- Do not merge, push, deploy, restart or modify an operator server unless explicitly authorized for that action.
- Create exactly one focused local feature commit for a completed feature step, unless the task specifies another commit strategy. Report its SHA and the actual working-tree status.
- Integrate to `master` only in a separate, explicitly authorized integration step with fast-forward checks where required.
- Avoid unexplained generated files, unrelated refactors and bulk formatting changes.

## Documentation and Reports

- Permanent developer/operator documentation belongs in `docs/`.
- Phase reports and audit artifacts belong in `reports/<phase>/`, **not** `docs/`.
- Update relevant permission, configuration and UX documentation alongside behavioral changes.
- Keep docs aligned with actual implemented behavior, and clearly mark future plans versus shipped functionality.
- Preserve the original intent of historical reports; new reports should record their own evidence rather than rewriting old results.
