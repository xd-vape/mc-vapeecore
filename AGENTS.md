
# VapeeCore – Codex Project Instructions

## Project

VapeeCore is a modular Minecraft core plugin built with Java,
Maven and the Paper API.

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

All player-facing system messages must follow VapeeCore's
central chat design system.

- Use the existing central message formatting API.
- Do not introduce independent styling for new features.
- System prefixes use a consistent dark-gray style.
- Prefixes follow the format [Feature].
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
- Preserve messages.accent-color, including reload and fallback.

When implementing a new feature with chat messages or prefixes,
automatically apply these rules without requiring an explicit
styling request.

Use docs/development/chat-design-system.md as the detailed reference.

- Extend MessageStyle / MessageService / SocialChannel; do not add a second palette.
- New features use MessageStyle.feature with a bare localized feature label.
- Use MessageService.sendRawBlock for localized, padded message blocks; supply
  the feature heading explicitly and keep repeated status feedback compact.
- Bundled MiniMessage system styling uses <accent> and the vc_* semantic tags.
  Pass names/reasons as literal Components, never as MiniMessage source.
- Keep the prefix-free TPA and friend Join Summary delivery contracts.
- Staff HIDDEN affects the public staff roster only; it is not a disguise and
  must not change chat ranks, names, nametags, scoreboards or tablist appearance.

If the central message API is missing, extend existing
infrastructure conservatively rather than duplicating styling.

## Quality Assurance

- Test observable behavior rather than implementation details.
- Add focused tests for new behavior and regressions.
- Run applicable existing tests and Maven quality checks.
- Use Spotless according to the repository configuration.
- Report checks that could not be completed.
- Never claim visual Minecraft validation without performing it.

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
- Use reports/<phase>/ for phase-specific results.
- Document important architectural decisions.
- Keep documentation consistent with the actual implementation.
