# VapeeCore – Codex Project Instructions

## Project

VapeeCore is a modular Minecraft core plugin built with Java, Maven and the Paper API.

Preserve the existing architecture, conventions and behavior.
Prefer small, maintainable changes over unnecessary abstractions.

## Development Rules

- Inspect relevant existing code before implementing changes.
- Reuse existing services, utilities and APIs where appropriate.
- Follow the project's established Java and Maven conventions.
- Avoid introducing new dependencies without a clear need.
- Do not implement unrelated features or speculative extensions.
- Preserve compatibility with the project's supported Paper versions.
- Do not silently change permissions, commands or player data.
- Support existing English and German localization conventions.
- Avoid duplicated implementations of shared functionality.

## Chat Design System

All player-facing system messages must follow VapeeCore's central chat design system.

### General Styling

- Use the existing central message formatting API.
- Do not introduce independent styling for new features.
- Main message text uses the central neutral text color.
- Secondary information uses the central muted text color.
- Important values use the configured accent color.
- Success, error, warning and status colors are semantic.
- Online and offline states use the shared status colors.
- Player names preserve their existing rank/name colors.
- Interactive chat actions must preserve click and hover behavior.
- Multi-line messages use consistent spacing and layout.
- English and German messages use equivalent visual structure.
- Do not hardcode shared colors inside feature implementations.
- Preserve `messages.accent-color`, including reload and fallback.

### Feature Prefix Styling

All system prefixes must use the central MessageStyle API.

- Prefixes follow the visual format `[Feature]`.
- A prefix consists of an opening bracket, localized feature label and closing bracket.
- Only the brackets `[` and `]` use the shared dark-gray bracket color.
- The label inside the brackets uses a feature-specific color.
- Bracket and label colors must be applied independently.
- Never color the entire prefix dark gray by default.
- Feature label colors must be centrally defined and reusable.
- Preserve existing feature identity colors unless explicitly changed.
- A feature label should remain visually distinguishable from its brackets.
- English and German labels of the same feature must use identical colors.
- Feature identity and color selection must not depend on translated label text.
- New features must use the central prefix composition API.
- Do not manually construct or color system prefixes inside feature classes.
- New feature colors must be consistent with the existing visual palette.
- Message body colors remain governed by the semantic palette.
- Interactive actions such as `[Accept]` and `[Deny]` are not system prefixes.
- Preserve existing exceptions where messages intentionally have no feature prefix.

When implementing new features, automatically apply the split-prefix styling without requiring additional instructions.

### Message Architecture

Use `docs/development/chat-design-system.md` as the detailed reference.

- Extend the existing MessageStyle / MessageService / SocialChannel architecture where appropriate.
- Do not introduce a second palette or competing formatting service.
- Use the central MessageStyle feature/prefix composition API.
- Ensure feature identity, localized label and label color are handled consistently.
- Keep feature-specific colors centrally defined rather than scattered across feature implementations.
- Do not rely on localized label strings to determine feature colors.
- Use MessageService.sendRawBlock for localized, padded message blocks.
- Supply feature headings explicitly and keep repeated status feedback compact.
- Bundled MiniMessage system styling uses `<accent>` and the `vc_*` semantic tags.
- Pass names and reasons as literal Components, never as MiniMessage source.
- Preserve interactive Component click and hover events.
- Keep the prefix-free TPA and friend Join Summary delivery contracts.
- Preserve existing operator-defined message overrides unless explicitly instructed otherwise.
- Staff HIDDEN affects the public staff roster only; it is not a disguise and must not change chat ranks, names, nametags, scoreboards or tablist appearance.

If the central message API is missing or insufficient, extend existing infrastructure conservatively rather than duplicating styling.

### Future Features

When introducing a new feature that produces chat messages:

- Reuse the existing central message system.
- Apply the global bracket styling automatically.
- Assign a centrally defined feature label color where a system prefix is required.
- Follow existing message formatting and semantic color conventions.
- Keep localization consistent between supported languages.
- Reuse existing message block and interaction utilities.
- Avoid introducing arbitrary colors or custom formatting implementations.
- Extend relevant behavior tests to cover the new feature's chat output.
- Update the chat design documentation when introducing new shared styling behavior.

These rules apply automatically to future modules and features.

## Quality Assurance

- Test observable behavior rather than implementation details.
- Add focused tests for new behavior and regressions.
- Run applicable existing tests and Maven quality checks.
- Use Spotless according to the repository configuration.
- Report checks that could not be completed.
- Never claim visual Minecraft validation without performing it.
- Verify prefix brackets and feature labels separately in relevant chat tests.
- Preserve existing name colors, message interactions and localization behavior.

## Git and Safety

- Inspect branch, working tree and existing changes first.
- Never discard unrelated or uncommitted work.
- Do not use destructive Git operations without approval.
- Do not push, merge or deploy without explicit authorization.
- Do not stop or modify an existing operator server.
- Prefer isolated test environments.
- Keep commits focused on the assigned task.

## Documentation

- Keep permanent technical documentation under docs/.
- Keep phase reports and audit artifacts under reports/.
- Use `reports/<phase>/` for phase-specific results.
- Document important architectural decisions.
- Keep documentation consistent with the actual implementation.
