# Step 08F.2 – Daily Quest Completion Feedback and Player-Skin NPC

The local feature branch `codex/step08f2-quest-feedback-npc` is based on
`7dfbec104be07b8eef5be25c8b9c229137892a3e`.

## Completion feedback

QuestService delivers one completion notification for each result containing newly completed IDs. Both numeric/distinct progress and successful pending-reward retries use that boundary. DailyQuest observes only IDs from its configured catalog. The observer presents the result after the reward transition and never grants rewards itself. The numeric HUD continues to use the existing progress observer.

The defaults in `daily-quests.yml` play `minecraft:ui.toast.challenge_complete` once per completion batch. The sound can be disabled or changed. The optional menu opens after 10 ticks by default, with a configurable range of 1–100 ticks. Auto-open is disabled by default.

A pending menu belongs to the exact current Bukkit player and loaded profile. The callback and the existing menu recheck the session and available server-side container immediately before opening. A second batch can share an already pending open. Logout uses the existing menu quit listener. Logout, reload and disable invalidate the old authority before attempting cancellation. Auto-open uses the existing renderer without its intentional root-open signal, so it never advances `lobby:quests_open`.

The vanilla player inventory screen is client-side and shares the default server view. Paper can distinguish an open server container from that default view.

## Quest NPC

An administrator can run these commands under `vapeecore.admin`.

- `/core quest npc set` places the NPC at the sender's location in game.
- `/core quest npc name <display name>` sets the literal display name. The default is Quest Master.
- `/core quest npc skin <Minecraft username>` saves the selected skin account.
- `/core quest npc remove` clears the placement.

The module stores placement, name and skin in `quest-npc.yml` through the existing safe YAML writer. It spawns one nonpersistent Paper Mannequin in the configured chunk when its entities are available. Chunk notifications coalesce into one next-tick reconciliation. The module removes its exact instance on disable, restores ownership after a failed reload and retains failed cleanup for the existing disable retry. The saved placement recreates the entity on restart. [Paper Mannequin API](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Mannequin.html)

The Mannequin uses Paper's default appearance while a requested ResolvableProfile is unavailable. A cached online profile with textures can be used immediately by the resolver. Other accounts are resolved asynchronously. The current lookup owns the profile update on the main thread. Reload and disable revoke that ownership. [Paper ResolvableProfile API](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/datacomponent/item/ResolvableProfile.html)

Damage, displacement and native use are blocked by the entity flags and scoped event handlers. A permitted main-hand click opens the existing DailyQuest menu with its existing `vapeecore.quest.use` and loaded-profile checks. The paired interaction notifications keep a single active view. An intentional successful opening retains the existing quest-open signal.

## Verification

The final `pwsh -NoProfile -File .\scripts\verify.ps1` run exited 0. Maven clean package and Spotless passed. All 247 discovered harnesses executed and passed with 40,950 checks and zero missing or invalid harnesses. The existing Quest, HUD, Reward, DailyQuest and persistence tests were included.

The focused completion harness exercises the default sound, batched payouts, pending retries, configurable delay, the existing menu and its progress observer, logout, replacement session, permission changes, reload and disable. It also checks a container opened during menu preparation. The NPC harness exercises admin dispatch, persistence, nonpersistent entity protection, skin fallback and stale callbacks, paired clicks, chunk reconciliation, startup failure and reload rollback. The existing DailyQuest module lifecycle harness remains in the full verifier.

- The focused harnesses passed 25 completion checks, 23 NPC checks and 354 DailyQuest module lifecycle checks.
- Isolated Paper 1.21.11 build 132 with LuckPerms 5.5.84 and JDK 21 passed 33 native checks across two JVMs. Both exited normally. The probe used real command dispatch, Mannequin and CraftPlayer objects, Bukkit interaction events, native sound packets and a real scheduled inventory open. It applied a synthetic cached profile with a texture property and verified the default profile. It then verified placement/name after restart and one entity after reload and reenable.
- The first native probe stopped at the manual quest-open progress assertion because its synthetic player skipped the lobby producer's login initialization. The successful run explicitly initialized that existing session.
- The first full verifier flagged a second logout handler under the existing DailyQuest lifecycle assertion. The final code folds callback cancellation into the existing menu logout handler, retaining the original lifecycle check.
- The native probe ran with controlled online registration and without a connected Minecraft client. It verified the skin property through Paper, with neither external texture fetching nor rendering in the fixture.
- The verifier build and the final native probe used the same JAR bytes, SHA-256 `9A35B895882D2F0AC8535C37AB61F2130034FF0820E0F13D7B8826C5F0E589BA`.

Real Minecraft-client acceptance is open for the visible Mannequin, fetched skin and name rendering, sound playback and HUD/menu presentation.

The 23-path scope covers the quest result observer, existing DailyQuest runtime and menu, admin dispatch, bundled config and localization, focused tests and documentation. The Run configurations, Receipts, operator artifacts, historical protection evidence and protected worktree passed the preservation check. Native tests ran on loopback in separate disposable data directories.

Verification logs and the build and protection receipts are under
`C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08f2-20261111-002337/`.
