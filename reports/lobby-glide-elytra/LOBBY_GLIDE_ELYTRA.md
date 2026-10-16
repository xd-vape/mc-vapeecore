# Temporary Elytra during lobby glide

## Authorization and baseline

The operator explicitly approved a **separate follow-up** after armor-free Glide: temporary Elytra during the feature's glide **only with an empty chest slot**, with existing chest armor untouched. The operator accepted the increased height and requested a little more forward push with authorization to continue. The finished armor-free foundation is `3f687e15727d08667584b8f1acf8e9afac2a3d9d` (`Add lobby double jump glide`). Its historical report remains unchanged.

Branch `codex/lobby-glide-elytra`, same checkout, clean when created, no worktree/rebase/force/push/merge. The immediately completed canonical validation of that identical baseline before any follow-up production edits was **175 discovered/executed/passed, 27,176 checks, 0 failures, 0 missing/invalid**. Receipts were copied to local ignored `dev-server/.vapeecore-dev/lobby-glide-elytra/baseline-results.json` and `baseline-inventory.txt` before edits. No test entrypoint is removed.

## Policy and implementation

- Physics and existing eligibility/flight owners stay with LobbyGlideService and the existing NavigatorAccessPolicy. Launch stays **0.85 up / 0.85 forward**; no tuning in this follow-up.
- LobbyGlideEquipment creates one unbreakable Elytra **after confirmed active glide**, never on arming or in the deferred launch window. An occupied chest, including a real Elytra, is left unchanged and the already verified armor-free movement remains available. No real armor is borrowed, snapshotted, escrowed or restored.
- Strict `vapeecore:glide_elytra` string metadata contains canonical owner UUID and a random per-flight UUID. Material, display text, malformed data, wrong PDC primitive type and foreign namespaces cannot establish generated-item identity. Runtime equipment leases store only UUID/generation/token, never Player/ItemStack/Inventory references.
- Landing, liquid/interruption, explicit release, native mode, teleport/world, Utility `/fly`, BUILD, generic Activity, death, quit and disable remove only the exact generated owner/token. An old generation cannot remove new wings or a foreign replacement chestplate. Best-effort cleanup logs UUID, continues with remaining players and clears runtime maps. Partial equip failure removes its own item and safely retains armor-free glide.
- Join/enable/respawn discard persisted disposable orphan markers while preserving any current live token and all normal items. This is orphan removal, not restoration of a captured armor snapshot. There is no added Player YAML/file schema.
- The existing scoped pending-task mechanism coalesces a one-shot equipment-recovery flag. Rare canceled generated-item actions and successful ownership changes request it; vertical landing observations do not request equipment scans. It catches inventory restoration applied after an event (including canceled drop followed by `/fly`, or post-respawn kept items) without overriding a newer lease or flight owner. No repeating task, global scan or event framework.

The Glide listener protects only strictly generated markers against clicks (including shift, number-key, offhand and drop variants), drags, drops, hand swap, item use/damage, armor stands, automated inventory transfers and entity/hopper pickup. Death filters both ordinary drops and Paper's itemsToKeep. Unmarked real Elytras and other items remain usable.

Module ownership remains LobbyExperienceModule; the existing observer direction and shutdown ordering remain intact. New startup availability performs scoped recovery before rearming. No new module, root command, permission, setting, GUI, localization text, particle or sound. Phase 38 remains out of scope.

## Paper API authority and integrity

Used the locally resolved Paper 1.21.11 API sources. PlayerInventory getContents/setItem share the same native inventory mapping, so cleanup can remove only marked copies without replacing other armor/storage/offhand entries. Actual setChestplate, ItemMeta/PDC, unbreakable flag, PlayerDeathEvent itemsToKeep and cancellation events are used. Normal inventory actions are never inferred from material or display name.

The existing Glide fixture now models real ItemStack-like contents and metadata with a pre-existing chestplate by default. The two baseline harness entrypoints remain with their 60 and 43 checks; their integrity assertion is strengthened around exact occupied-armor/storage/offhand/cursor values and **zero equipment writes**, allowing the new legitimate read of the chest slot. New empty-chest tests exercise the real production equipment controller; they do not substitute a no-op controller. Native Paper additionally verifies actual ItemStack serialization.

## Validation

Current focused result: **3 Glide harnesses, 195 checks passed** (60 baseline movement, 43 domain handoffs, 92 equipment/security checks). Covers empty/occupied chest, unmarked real Elytra, unchanged other items, landing/water, all ownership exits, generic Activity before-hook release, stale generation, foreign replacement, partial setter/metadata/glide failure, bulk cleanup failure, orphan recovery, malicious/malformed metadata, event transfer protection, post-event restoration and live-token preservation.

Five actual temporary production negative controls passed. Each mutated source was compiled, the equipment harness failed at the intended assertion, original source bytes were restored exactly, the class recompiled and the positive 92-check harness passed. Receipts: `negative-results.json` with restored SHA-256 hashes; `negative-A.log` through `negative-E.log`.

| Control | Temporary defect | Expected failing behavior test |
|---|---|---|
| A | Remove occupied-chest guard | occupied chest never overwritten |
| B | Remove lease generation guard | stale equipment generation cannot remove newer wings |
| C | Omit landing equipment release | landing removes wings and restores empty chest exactly |
| D | Omit owned current-item/cursor click guard | managed armor cannot be moved via LEFT |
| E | Omit deferred orphan recovery | post-event item restoration cannot leak wings or overwrite new fly owner |

No mutation remains.

Canonical full regression: **176 discovered/executed/passed, 27,271 checks, 0 failures, 0 missing/invalid**. All **175/175** baseline entrypoints retained; only LobbyGlideElytraHarness added. The extra three checks beyond the new 92-check harness are automatic source/test-file audit coverage (+1 PermissionDescriptor, +2 GuiAuthoring). Receipts: `full-results.json`, `full-inventory.txt`, `full-regression.log`.

Required offline Maven **clean package: BUILD SUCCESS** (22.493 s), after the full canonical regression. Receipt: `package.log`. Deployed JAR SHA-256: `DDD157D75E86CFB20E87AEB301E86BC3D545E28EAE1CC417B37674911FB086A7`; target/deployed bytes match. No production changes followed final validation.

Paper **1.21.11-132** final smoke: **27 native checks passed**, clean startup/Join path, Utility and BUILD ownership, occupied chest preservation, real generated Elytra/PDC/unbreakable metadata, byte-equivalent unchanged other equipment/storage/offhand/cursor, landing removal/rearm/reuse, `/fly` removal and marked orphan recovery with foreign replacement armor preserved. `/core reload` succeeded; clean shutdown exit 0. **0 VapeeCore/GlideProbe ERROR/SEVERE**. Actual measured counts: **28 modules, 7 reload participants, 37 command roots, 50 permissions**. All **10 operator YAMLs and 2 existing language files** SHA-256 unchanged; no new language files. Receipts: `paper-smoke.log`, `paper-receipt.json`.

The native probe uses real Paper ItemStack/PDC serialization, event dispatch, scheduler, profile repository and owning services with a virtual Player/Inventory boundary. It is not a CraftPlayer entity or an actual Minecraft client; separate operator client acceptance is recorded below. The isolated probe's lookup is substituted for only its virtual UUID; production lookup is unchanged. Temporary probe JAR/remapped copy were removed after smoke, its fresh profile file deleted and all probe observers/tasks detached. The project Dev-Server was stopped cleanly before deployment and after smoke. The operator subsequently completed the actual Minecraft client acceptance below; native virtual players do not substitute for that test.

## Git and real client

Automated regression, negative controls, build and native Paper validation are complete. On 2026-10-17 the operator confirmed actual Minecraft client acceptance: "Alle genannten Punkte tatsächlich geprüft und passend". The confirmed checks were F5 wings during glide with an empty chest slot, removal on landing and reuse on the next glide, blocked moving/dropping of generated Elytra, cleanup on `/fly`, BUILD, world change and Quit/Rejoin, unchanged existing chest armor in NORMAL, and satisfactory movement at **0.85 up / 0.85 forward**. Actual client Activity/Blackjack testing was not separately confirmed and is not claimed; generic Activity handoff is covered by automated domain tests.

This completed follow-up is recorded as one separate local commit on `codex/lobby-glide-elytra`. No push, merge or Phase 38. The first armor-free commit already exists separately; its report remains historical.

## Changed files

- `docs/DEVELOPER_GUIDE.md`
- `reports/lobby-glide-elytra/LOBBY_GLIDE_ELYTRA.md`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/glide/LobbyGlideEquipment.java`
- `src/main/java/dev/vapee/core/lobby/experience/glide/LobbyGlideListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/glide/LobbyGlideService.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/GlideTestItem.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/LobbyGlideElytraHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/LobbyGlideFixture.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/LobbyGlideHandoffHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/glide/LobbyGlideHarness.java`

Ignored probe sources/receipts stay under the local dev-server task directory and are removed from server plugins after smoke.
