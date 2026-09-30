# Phase 25B – Mute and Communication Enforcement

Stand: 2026-09-30. Baseline: `21877faa1f818a02dc40d80a810c7b2c8662045d` (Merge Phase 25A). Branch: `phase/25b-mute-communication-enforcement`.

## Abschlussbericht – 102 Prüfpunkte

1. Neue Dateien: sieben, einschließlich dieses Berichts; vollständiges Inventar unten.
2. Geänderte Dateien: 22 bestehende Dateien; vollständiges Inventar unten.
3. Entfernte Dateien: keine.
4. Baseline: geprüfter Branch `phase/25b-mute-communication-enforcement`, Full-HEAD `21877faa1f818a02dc40d80a810c7b2c8662045d`, initial sauberer Worktree. Die im Auftrag dokumentierte Baseline umfasst 84 Harnesses / 5.487 Checks / 0 Fehler; unten stehen die neu gemessenen finalen Werte.
5. Scope: ausschließlich Mute/Unmute, committed Mute-Projektion und Chat-/PM-Enforcement; exakt ein Worker, keine Delegation.
6. Module Count: unverändert 26; Source-Wiring und Paper-Enable/Disable bestätigt.
7. Reload Count: unverändert sechs; config, lobby, chat, private-messages, presentation, daily-quests.
8. Root Command Count: 36; Descriptor-Test und 36 tatsächliche Paper-Help-Einträge.
9. ModerationMuteProjection: konkrete lokale UUID→MUTE-Record-Listen-Projektion; keine zusätzliche Persistence oder generischer Bus.
10. Immutable State: `Map.copyOf`, sortierte `Stream.toList`-Listen und immutable Domain-Records; kein Boolean-only-Set.
11. Atomic Boundary: genau ein `volatile` Map-Referenzwechsel nach vollständiger Kandidatenkonstruktion.
12. Publication: `repository.save(next)` → Projektion → Service-State → Return → INFO-Audit → Feedback → Online-Notice.
13. Initial Publication: vollständig validierter Load wird im Service-Konstruktor publiziert, bevor Commands/Listener/Getter freigegeben werden.
14. Save Failure: kein neuer Service-State, keine neue Projektion, kein Erfolgs-Audit, keine Benachrichtigung.
15. Revoke Failure: vorheriger aktiver Mute bleibt in Service und Projektion aktiv; Chat und PM bleiben gesperrt.
16. Exact Expiry: Abfragen verwenden `isActiveAt(now)`; bei `now == expiresAt` bereits inactive, ohne Write.
17. Restart: persistierter Mute wird beim Enable sofort projiziert; File-Persistence-Roundtrips und Modul-Reenable geprüft.
18. Async Read Guarantee: Leser erfassen eine immutable State-Referenz und einen Clock-Zeitpunkt; konkurrierende Publikation testet echte Threads.
19. Kein async Servicezugriff: ModerationService bleibt vollständig Main-Thread-owned, inklusive Reads; Source-/Field-/Constructor-Assertions am Chat-Listener.
20. `/mute <player|uuid> <duration|permanent> <reason...>`: vorhandene AbstractModerationCommand-/Context-Gates, anschließend ausschließlich issueMute.
21. Mute-Permission: `vapeecore.moderation.mute`, unabhängig, default op; kein Alias, Child oder Wildcard.
22. Known Offline: gespeicherte Identitäten werden akzeptiert; keine Phantom-Identität, kein OfflinePlayer-Netzwerklookup.
23. UUID: Known-UUID-Auflösung über den vorhandenen Identity-Service; unbekannte UUID wird kontrolliert abgelehnt.
24. Ambiguity: doppelte Namen werden abgewiesen und UUID als eindeutige Alternative genannt.
25. Self Guard: Mute und Unmute auf eigene UUID beziehungsweise eigenen Namen gesperrt; bestehende Self-History-Regel bleibt.
26. Duration: unveränderter Parser für positive s/m/h/d/w und permanent/perm; explizite Command-Tests permanent, perm, 30s, 10m, 2h, 7d, 2w sowie Overflow/Invalid-Tests.
27. ALREADY_MUTED: konkrete Meldung, kein Save, keine Publikation, kein Success-Audit oder erneute Notice.
28. Online Mute Notification: erst nach erfolgreichem Commit/Publikation/Audit; Reason und Permanent/UTC. Offline keine Queue.
29. Literal Reason: Component.text für Feedback/Notices; Injection-Tests enthalten run_command-Tags, ohne Click/Hover-Events.
30. `/unmute <player|uuid> [reason...]`: ausschließlich revokeMute, ursprünglicher Record mit gleicher ID bleibt erhalten.
31. Unmute-Permission: `vapeecore.moderation.unmute`, unabhängig und default op; weder Mute noch Ban gewähren dieses Recht.
32. NOT_MUTED: konkrete normale No-op-Meldung für absent/expired/revoked, ohne Write oder Audit.
33. Optional Reason: absent/blank bedeutet Optional.empty; vorhandener Grund nutzt den unveränderten Validator und bleibt literal.
34. Online Unmute Notification: „Your mute has been removed.“ mit optionalem literal Reason. Runtime-Fehler loggen SEVERE und melden „Unmute was saved, but …“; kein Rollback.
35. Projection Update: erfolgreicher Widerruf wird vor Return publiziert; unmittelbar nächste Chat-/PM-Abfrage ist frei, sofern kein anderer aktiver überlappender Mute existiert.
36. Chat Enforcement: separater ModerationMuteChatListener; ChatModule, ChatListener und ChatService produktiv unverändert.
37. AsyncChatEvent: LOWEST, ignoreCancelled=true; bestehender Formatter NORMAL, ignoreCancelled=true.
38. Cancellation: aktive MUTE-Fakten canceln sofort; WARNING/BAN/KICK nicht. Pre-cancelled Events bleiben unberührt; Renderer wird vom Mute-Adapter nicht ersetzt.
39. Main-thread Feedback: einmaliges runTask, Capture von UUID/immutable Record, Player erst im Task neu auflösen und online prüfen; kein Player-Capture oder async send.
40. Chat Exact Expiry: vor Ablauf gesperrt, exakt am und nach Ablauf erlaubt, ohne Neu-Publikation/Reload.
41. Revoked Behavior: erfolgreicher Unmute gibt nächste Chat-Nachricht frei; widerrufene Historie bleibt unverändert erhalten.
42. Scheduler Failure: kontrolliertes SEVERE mit Target-UUID, Event bleibt cancelled; Disable deaktiviert pending Feedback.
43. PM Enforcement: finales Predicate<UUID> liest ausschließlich dieselbe Projektion; Service kennt keinen ModerationService/Module/Repository.
44. `/msg`: gemuteter Sender erhält „You cannot send private messages while muted.“; keine outgoing/incoming PM.
45. `/reply`: enabled/loaded/mute vor Partner-Auflösung und Cleanup; gleiche Meldung.
46. `/r`: unveränderter Alias desselben Reply-Executors; gleiche Mute-Policy und Tests.
47. Sender Blocked: enabled → sender loaded → sender muted → self → recipient online/loaded/settings → Ignore → Render/Send.
48. Muted Recipient: weiterhin PM empfangbar; recipient-disabled und Ignore bleiben autoritativ.
49. Conversation State: abgewiesene Muted-Sends/Replies ändern kein Mapping; Offline-Partner wird beim blockierten Reply nicht vorzeitig entfernt.
50. Reload Behavior: PM apply/rollback ersetzt nur RuntimeState, nicht das finale Predicate; Moderation ist nicht reloadbar und besitzt den separaten Chat-Listener.
51. PrivateMessageResult: ausschließlich SENDER_MUTED ergänzt; bestehende Results und Rückmeldungen bleiben erhalten.
52. Policy Injection: PrivateMessageModule erhält ModerationModule und injiziert getMuteProjection()::isMuted; alte Service-Konstruktoren delegieren auf id -> false.
53. plugin.yml: exakt zwei Commands und unabhängige op-Nodes ergänzt; 36 Roots, sieben Moderationsnodes, keine Children/Aliases/Migration.
54. Permissions: Syntax, Offline-/UUID-Scope, Moderator-Empfehlung und fehlende Runtime-Hierarchie dokumentiert.
55. Core Help: beide Einträge einzeln permission-gefiltert; Mute-only/Unmute-only zeigen keine gegenseitigen oder Ban-Rechte; alle Klicks bleiben Suggest.
56. Projection Harness: 20 Checks; Action-Filter, Zeitgrenzen, future-created, Revocation, Overlaps/UUID-Tie, stale Replacement, immutable Maps/Listen, alter Record, clear und parallele Publikation.
57. Service Publication Tests: 141 gesamte Service-Checks; committed-Marker und gespeicherter Snapshot vor Publisher, initialer Load, alle sechs Mutationen, No-ops, Fehler und Legacy-Konstruktor.
58. Command Tests: 913 Checks; sieben Command-Gates, Identitäten, Dauer, Fehler, No-ops, Audit, literal Notices, optionaler Widerrufsgrund und aktive Unmute-Completion.
59. Chat Enforcement Tests: 52 Checks; echte AsyncChatEvent-Objekte, Cancellation, Dispatch-Priority/ignoreCancelled-Regel, Main-Thread-Feedback, Expiry/Revocation, Scheduler-Failure, Offline/Disable.
60. PM Tests: 117 Checks; send/reply/r, kein Render/Send bei Mute, empfangender Mute, Settings/Ignore, Conversation, Policy-Erhalt bei State-Reload und beide Save-Failure-Richtungen.
61. Lifecycle Tests: 140 Checks; sieben Commands/zwei Listener, alle neun partielle Installationsgrenzen, Getter-Sperren, Clear, Retry/Reenable und fehlender Shutdown-Save.
62. Async-thread Test: Chat-Handler tatsächlich auf separatem Thread; Lookup/Message im danach ausgeführten Main-Thread-Task. Zusätzlich separater Projection-Reader bei wiederholter Publisher-Ersetzung.
63. Save-failure Projection Test: fehlgeschlagener Mute lässt Chat passieren und PM senden; erfolgreicher anschließender Mute sperrt sofort.
64. Unmute-failure Projection Test: fehlgeschlagener Widerruf hält Chat und PM blockiert; erfolgreicher Retry gibt ohne Reload frei.
65. Phase25A Regression: Warn/Ban/Unban/Kick/History, Duration und Login-Enforcement erneut grün; BanEnforcement 31 / Duration 53.
66. Phase24 Regression: Domain 112, Persistence 523, erweiterter Service 141, Lifecycle 140 grün; keine Invarianten-/Schema-Abschwächung.
67. Chat Regression: ChatHarness unverändert 17 Checks grün; kein Renderer-Caching und keine Produktionsänderung im Chat-Paket.
68. PM Regression: sämtliche vorherigen PM/Social-/Command-Gates bleiben Teil des erweiterten Harnesses grün.
69. Friend Regression: Domain, Service, Persistence, Integration, Command, Lifecycle und GUI/Security separat grün.
70. Clan Regression: Domain, Service, Persistence, Integration, Commands und GUI/Security separat grün.
71. Economy Regression: Service, Commands, Integration grün; kein Economy-Produktionsdiff.
72. Settings/Visibility Regression: Settings-, Persistence-, Menus/Security-, Policy-, Lifecycle- und Relationship-Refresh-Harnesses grün.
73. Teleport Regression: Parser, Commands und übrige Utility-/Inventory-/Lobby-Grenzen grün.
74. Reward Regression: Reward, OnlineReward und ihre Persistence-/Lifecycle-Harnesses grün.
75. Blackjack Regression: Domain, Fairness, physische Tische, Presentation/Preview separat grün.
76. Remaining Regression: Activity, Seat, WorldDisplay, Quest/DailyQuest, Rank, Identity/Profile, Config, Help und Presentation grün; vollständige Liste unten.
77. Total Harnesses: gemessen 86, jeder main-Harness einzeln in eigenem Java-Prozess gestartet.
78. Total Checks: gemessen 5.977.
79. Failures: gemessen null, alle Prozesse Exit 0.
80. Maven Build: finaler clean package über vorhandenes build-and-deploy.ps1, BUILD SUCCESS am 2026-09-30 um 20:43:06 CEST; Harness-main-Ausführung zusätzlich, nicht als Surefire-Ausführung ausgegeben.
81. JAR Bytes: target/vapeecore-1.0-SNAPSHOT.jar = 981.865 Bytes; Deploy gleiche Größe.
82. SHA-256: F4AA12D06811C872844ABC1CEDBFE27272FD3B475F4EEC7FA1210E0831537001; target und Deploy identisch.
83. Paper: 1.21.11-132, Commit c5eb079; final deployte JAR beim Smoke geladen/remapped.
84. Java: Oracle Java 21.0.12.1+1-LTS-4, Windows 11.
85. LuckPerms: Bukkit 5.5.84; verbunden und normal deaktiviert.
86. Paper Smoke: /core, version/help/reload; root und namespaced mute/unmute; bestehende fünf Moderationscommands, read-only history rxaq, PM-player-only, Plugin-Help mit 36 Roots, getrennte minecraft:ban/kick-Help-Topics. 26 Enable, sechs Prepare, 26 reverse Disable; null ERROR/SEVERE, Exit 0; fehlende moderation.yml bleibt fehlend. Keine künstlichen Sanktionen.
87. Live Client Status: Live chat client test: not performed. Live client test: not performed. Keine echten Chat-/PM-Pakete oder Zwei-Client-Session behauptet; list meldete 0 Online-Spieler.
88. README: aktueller 25B-Status, Commands, sender-only PM, immutable Projektion, Zeitgrenze und 36/26/6 ergänzt; 25A historisch eingeordnet.
89. Developer Guide: Ownership, save→publish→state, thread boundaries, Feedback, Policy-Injection, Lifecycle, Commandmatrix und Harnessliste aktualisiert.
90. Permissions: zwei unabhängige op-Nodes und Moderator-Empfehlung ergänzt; explizit keine Ranghierarchie/Berechtigungserbschaft/BYPASS.
91. Historical Docs: COMMAND_AUDIT.md, MODERATION_FOUNDATION.md, MODERATION_TOOLS.md byte-/Git-unverändert.
92. FORMATTING: unverändert; keine neue MiniMessage-Verarbeitung von untrusted Text.
93. New Phase Report: ausschließlich dieser MODERATION_MUTES.md-Bericht dokumentiert die 25B-Verifikation und das vollständige Änderungs-/Harness-Inventar.
94. No Rank Hierarchy: Staff-Target-/Group-/Weight-/Track-Auswertung bewusst nicht eingeführt.
95. No Freeze: keine Movement-/Inventory-/Gameplay-Sperre.
96. No Mute Bypass: auch OP/Staff-Permissions gewähren keine Kommunikationsausnahme.
97. No Command Blocking: kein PlayerCommandPreprocessEvent und keine pauschale Vanilla-/Fremdplugin-Command-Sperre; Enforcement nur VapeeCore-Chat/PM.
98. No Schema Migration: Moderation Schema 1 und alle bestehenden Player-/Social-/Friend-/Clan-Dateien unverändert.
99. No Recurring Scheduler: keine Expiry-/Cleanup-/Flush-Tasks; ausschließlich einzelne Main-thread-Feedback-Tasks, inaktiv nach Listener-Disable.
100. Phase26 Readiness: saubere Policy-/Projection-Grenze; Permissions & Rank Hardening muss eigene Staff-Target-Schutz-/Hierarchie-/Inheritance-Regeln festlegen, bis dahin gezielte Nodes als Trust Boundary.
101. Known Issues: keine bekannten neuen Produktionsfehler. Live-Client-Test bleibt offen. Bestehende API-Deprecation PlayerLoginEvent/Compiler-Hinweise und Paper-Versionshinweis außerhalb des 25B-Scope; Windows spark nutzt seinen Java-Fallback.
102. Merge Readiness: implementiert, vollständige Regression/Build/Paper-Smoke grün, Scope/Diff geprüft. Genau ein lokaler Commit „Phase 25B - Mute and Communication Enforcement“; kein Push. Endgültige Commit-ID und clean Git-Status werden in der Übergabe ausgegeben.

## Datei-Inventar / Name-only-Diff zur Baseline

Neue Dateien:

- `docs/MODERATION_MUTES.md`
- `src/main/java/dev/vapee/core/moderation/ModerationMuteChatListener.java`
- `src/main/java/dev/vapee/core/moderation/ModerationMuteProjection.java`
- `src/main/java/dev/vapee/core/moderation/command/MuteCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/UnmuteCommand.java`
- `src/test/java/dev/vapee/core/moderation/ModerationMuteEnforcementHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationMuteProjectionHarness.java`

Geänderte bestehende Dateien:

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/PERMISSIONS.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/command/CoreCommand.java`
- `src/main/java/dev/vapee/core/moderation/ModerationComponents.java`
- `src/main/java/dev/vapee/core/moderation/ModerationModule.java`
- `src/main/java/dev/vapee/core/moderation/ModerationService.java`
- `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java`
- `src/main/java/dev/vapee/core/privatemessage/PrivateMessageModule.java`
- `src/main/java/dev/vapee/core/privatemessage/PrivateMessageResult.java`
- `src/main/java/dev/vapee/core/privatemessage/PrivateMessageService.java`
- `src/main/java/dev/vapee/core/privatemessage/command/MessageCommand.java`
- `src/main/java/dev/vapee/core/privatemessage/command/ReplyCommand.java`
- `src/main/resources/plugin.yml`
- `src/test/java/dev/vapee/core/command/CoreCommandHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationLifecycleHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationServiceHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationTestSupport.java`
- `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
- `src/test/java/dev/vapee/core/privatemessage/PrivateMessageSocialHarness.java`

Entfernt: keine. Zusammen 29 Dateien. Dieses Inventar entspricht dem Name-only-Diff `git diff 21877faa1f818a02dc40d80a810c7b2c8662045d..HEAD --name-only` nach dem lokalen Commit.

## Vollständige separat ausgeführte Harnesses

Klassen wurden aus `src/test/java/**/*Harness.java` anhand ihrer `public static void main` ermittelt und einzeln mit Java 21, `target/test-classes`, `target/classes` und Maven-Test-Classpath gestartet. Ausgabe der finalen Ausführung: lokales ignoriertes `target/phase25b-harness-results.txt`.

| Harness | Checks | Result |
|---|---:|---|
| ActivityHarness | 153 | PASS |
| BlackjackFairnessHarness | 393 | PASS |
| BlackjackHarness | 218 | PASS |
| BlackjackPresentationHarness | 43 | PASS |
| BlackjackPreviewHarness | 16 | PASS |
| BlackjackTableHarness | 90 | PASS |
| ChatHarness | 17 | PASS |
| ClanDomainHarness | 52 | PASS |
| ClanIntegrationHarness | 15 | PASS |
| ClanPersistenceHarness | 39 | PASS |
| ClanServiceHarness | 136 | PASS |
| ClanCommandHarness | 83 | PASS |
| ClanMenuHarness | 84 | PASS |
| ClanMenuSecurityHarness | 37 | PASS |
| CoreCommandHarness | 72 | PASS |
| ConfigServiceHarness | 34 | PASS |
| DefaultConsistencyHarness | 11 | PASS |
| CoinsCommandHarness | 208 | PASS |
| EconomyIntegrationHarness | 24 | PASS |
| EconomyServiceHarness | 44 | PASS |
| FriendCommandHarness | 80 | PASS |
| FriendDomainHarness | 29 | PASS |
| FriendIntegrationHarness | 28 | PASS |
| FriendLifecycleHarness | 12 | PASS |
| FriendPersistenceHarness | 28 | PASS |
| FriendServiceHarness | 79 | PASS |
| FriendMenuHarness | 159 | PASS |
| FriendMenuSecurityHarness | 37 | PASS |
| IdentityHarness | 26 | PASS |
| ProfileHarness | 19 | PASS |
| LobbyCommandHarness | 26 | PASS |
| LobbyHarness | 53 | PASS |
| WarpHarness | 45 | PASS |
| CommandHelpHarness | 85 | PASS |
| ModerationCommandHarness | 913 | PASS |
| ModerationDurationHarness | 53 | PASS |
| ModerationBanEnforcementHarness | 31 | PASS |
| ModerationDomainHarness | 112 | PASS |
| ModerationLifecycleHarness | 140 | PASS |
| ModerationMuteEnforcementHarness | 52 | PASS |
| ModerationMuteProjectionHarness | 20 | PASS |
| ModerationPersistenceHarness | 523 | PASS |
| ModerationServiceHarness | 141 | PASS |
| OnlineRewardLifecycleHarness | 42 | PASS |
| OnlineRewardServiceHarness | 42 | PASS |
| PlayerQuestPersistenceHarness | 21 | PASS |
| PlayerRewardPersistenceHarness | 11 | PASS |
| PlayerVisibilityPersistenceHarness | 12 | PASS |
| PlayerSettingsServiceHarness | 24 | PASS |
| PlayerVisibilitySettingsHarness | 11 | PASS |
| PlaytimeFormatterHarness | 14 | PASS |
| PresentationHarness | 10 | PASS |
| PrivateMessageSocialHarness | 117 | PASS |
| DailyQuestConfigHarness | 13 | PASS |
| DailyQuestCycleSelectorHarness | 18 | PASS |
| DailyQuestLifecycleHarness | 14 | PASS |
| DailyQuestServiceHarness | 20 | PASS |
| QuestDefinitionHarness | 29 | PASS |
| QuestLifecycleHarness | 42 | PASS |
| QuestServiceHarness | 44 | PASS |
| RankCommandHarness | 16 | PASS |
| RanksCommandHarness | 12 | PASS |
| RankServiceHarness | 23 | PASS |
| RewardLifecycleHarness | 40 | PASS |
| RewardServiceHarness | 126 | PASS |
| SeatHarness | 52 | PASS |
| SettingsCommandHarness | 25 | PASS |
| SettingsMenuHarness | 56 | PASS |
| SettingsMenuSecurityHarness | 34 | PASS |
| SettingsModuleLifecycleHarness | 23 | PASS |
| SettingsNavigationHarness | 17 | PASS |
| VisibilityMenuSecurityHarness | 41 | PASS |
| VisibilitySettingsMenuHarness | 29 | PASS |
| VisiblePlayersMenuHarness | 74 | PASS |
| BuildCommandHarness | 17 | PASS |
| TeleportCommandHarness | 62 | PASS |
| UtilityCommandHarness | 152 | PASS |
| TeleportParserHarness | 58 | PASS |
| UtilityInventoryHarness | 36 | PASS |
| UtilityServiceHarness | 35 | PASS |
| FriendVisibilityRefreshHarness | 11 | PASS |
| IgnoreVisibilityRefreshHarness | 7 | PASS |
| VisibilityModuleHarness | 22 | PASS |
| VisibilityPolicyHarness | 28 | PASS |
| VisibilityServiceHarness | 10 | PASS |
| WorldDisplayHarness | 27 | PASS |
| **Gesamt: 86** | **5.977** | **0 Fehler** |

## Build / Dev-Artefakt

- Build: `mvn clean package` über das bestehende Build-&-Deploy-Skript; BUILD SUCCESS.
- JAR: `target/vapeecore-1.0-SNAPSHOT.jar`, 981865 Bytes.
- Deploy: `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar`, 981865 Bytes.
- Beide SHA-256: `F4AA12D06811C872844ABC1CEDBFE27272FD3B475F4EEC7FA1210E0831537001`.
- Finaler Paper-Smoke: Start 20:44:44 CEST, Console-Smoke 20:45:27–20:45:43 CEST, anschließender sauberer Shutdown. Log: ignoriertes `dev-server/logs/latest.log`.
- Live chat client test: not performed.
- Live client test: not performed.

## Git-Übergabe

Commit ausschließlich lokal und genau einmal: `Phase 25B - Mute and Communication Enforcement`.
Kein Push, Reset, Rebase oder History-Rewrite. Die Übergabe enthält `git log -1 --oneline`, bestätigt leeres `git status --short` und verifiziert das obige vollständige Name-only-Inventar gegen die Baseline.
