# Phase 25A – Moderation Commands and Ban Enforcement

Abschlussbericht vom 30.09.2026. Ein Worker; bestehendes lokales Projekt, kein neuer Checkout.

Baseline: `2a8644af655a84ca771fb1798bd2f5ae7339b67a` (`Merge Phase 24 - Moderation Foundation`).
Branch: `phase/25a-moderation-commands-ban-enforcement`.
Branch, vollständiger HEAD und sauberer Working Tree wurden vor der Implementierung geprüft.

Ergebnis: 26 Core Modules, 6 ReloadParticipants, 34 Root Commands; 84 separat gestartete Harnesses mit 5.487 Checks und 0 Fehlern. Finaler Clean-Build und Paper-Smoke erfolgreich. Keine echten Spieler sanktioniert. Kein Push.

## Command-Matrix

| Command | Permission | Verhalten |
|---|---|---|
| `/warn <player\|uuid> <reason...>` | `vapeecore.moderation.warn` | Known online/offline; Online-Notification erst nach Save. |
| `/ban <player\|uuid> <duration\|permanent> <reason...>` | `vapeecore.moderation.ban` | Known online/offline; Save vor Online-Disconnect; Login-Enforcement. |
| `/unban <player\|uuid> [reason...]` | `vapeecore.moderation.unban` | Aktiven Ban widerrufen; optionaler Reason. |
| `/kick <player\|uuid> <reason...>` | `vapeecore.moderation.kick` | Known online; History-Record vor tatsächlichem Disconnect. |
| `/history <player\|uuid> [page]` | `vapeecore.moderation.history` | Read-only; fünf Records pro Seite, neueste zuerst. |

Alle fünf Nodes sind unabhängig, `default: op`, ohne Children, Wildcard oder neue Aliases. Executor und Completion prüfen ihre eigene Permission. Player und Console sind erlaubt; andere Sender werden kontrolliert abgelehnt. Self-Warn/Ban/Unban/Kick ist gesperrt; Self-History mit Permission erlaubt.

Duration: case-insensitive `permanent`/`perm` oder eine positive Ganzzahl plus `s/m/h/d/w`. Beispiele: `30s`, `10m`, `2h`, `7d`, `2w`. Keine Vorzeichen, Dezimalzahlen, Exponenten, Whitespace, kombinierten oder Kalender-Einheiten; Multiplikations- und Timestamp-Overflow werden abgelehnt. Display ist normalisiert; Screens/History zeigen UTC.

## Dateiinventar

15 neue Dateien:

- `docs/MODERATION_TOOLS.md`
- `src/main/java/dev/vapee/core/moderation/ModerationComponents.java`
- `src/main/java/dev/vapee/core/moderation/ModerationLoginListener.java`
- `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/BanCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/HistoryCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/KickCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java`
- `src/main/java/dev/vapee/core/moderation/command/ModerationDurationParser.java`
- `src/main/java/dev/vapee/core/moderation/command/UnbanCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/WarnCommand.java`
- `src/test/java/dev/vapee/core/moderation/ModerationBanEnforcementHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationTestSupport.java`
- `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
- `src/test/java/dev/vapee/core/moderation/command/ModerationDurationHarness.java`

9 geänderte Dateien:

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/PERMISSIONS.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/command/CoreCommand.java`
- `src/main/java/dev/vapee/core/moderation/ModerationModule.java`
- `src/main/resources/plugin.yml`
- `src/test/java/dev/vapee/core/command/CoreCommandHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationLifecycleHarness.java`

0 entfernte Dateien. Build-/Server-Artefakte bleiben ignoriert und werden nicht committed.

## Architektur, Enforcement und Failure Boundary

ModerationModule bleibt alleiniger Owner von Service, fünf Commands und synchronem Login-Listener. Injection: JavaPlugin, IdentityModule, MessageService. Wiring bleibt Identity → Moderation → Friend. Kein zusätzliches CoreModule, Scheduler, ReloadParticipant oder Moderations-Config.

Commands verwenden PlayerIdentityService.resolve und danach ausschließlich ModerationService. Vollständige bekannte Namen, bekannte UUIDs und bekannte Offline-Identitäten sind erlaubt; Ambiguität verlangt UUID. Keine Phantom-Targets, Bukkit-OfflinePlayer-, Mojang- oder Netzwerkanfragen. Kick verlangt zusätzlich tatsächliche Online-Präsenz.

Die Phase-24-Domain, Service-APIs, Schema-Version 1 und FileModerationRepository sind unverändert. Ein Snapshot bleibt Source of Truth. Kein separater Ban-/History-State, keine Bukkit BanList und keine Pardon-Integration.

Mutation-Reihenfolge: Domain-Validierung → Repository-Save → State-Swap → INFO-Audit → Sender-Feedback → externe Notification/Disconnect. Bei Save-Fehler bleibt der alte Zustand erhalten; keine Notification, kein Disconnect, kein Success-Audit. ALREADY_BANNED/NOT_BANNED sind normale No-ops ohne SEVERE oder Save. Nachträgliche externe Fehler erhalten SEVERE und ein „saved, but …“ Feedback; der gespeicherte Fakt wird weder zurückgerollt noch dupliziert.

INFO-Audit enthält Actor-Typ/UUID, Target-UUID/Name, Action, volle Record-ID und Expiry; Kontrollzeichen werden im Log-Target/Name neutralisiert. Fehlerdetails/Stacktraces erscheinen nur im Serverlog, mit Actor/Target/Action. Reasons werden nicht als MiniMessage geparst.

PlayerLoginEvent läuft synchron vor PlayerJoinEvent. Der Listener fragt getActiveBan(UUID) read-only mit HIGHEST ab; aktiver Ban führt zu KICK_BANNED mit Adventure-Component. Genau am expiresAt, danach oder nach Revocation gibt es keinen eigenen Deny. Kein Save/Expiry-Cleanup/PlayerService-Load, kein Async-Servicezugriff. Query-Ausnahmen loggen SEVERE mit UUID, erfinden keinen Ban und heben fremde Login-Rejections nicht auf.

Ban-Disconnect und Login verwenden denselben Ban-Screen: Ban-Text, literal Reason, Permanent oder UTC-Expiry. Kick hat einen eigenen Kick-Text und literal Reason, ohne Ban-Text. Full-Instant-Extremwerte erhalten einen kanonischen UTC-Fallback.

History zeigt Action, UTC-Erstellung, Actor (bekannter Name/Console/UUID-Fallback), Reason und Recorded/Active/Expired/Revoked/Inactive; für BAN/MUTE auch Expiry/Permanent, für Revocation zusätzlich Actor/Zeit/optional Reason. Volle Record-ID im Hover. Previous/Next verwenden ausschließlich SUGGEST_COMMAND mit UUID, niemals automatische Ausführung. Seitenzahlen werden positiv und overflow-sicher validiert; Reads speichern nichts.

Completion ist permission-aware und deterministisch prefix-gefiltert. Primär Online-Targets; Unban nutzt aktive Ban-Target-UUIDs aus vorhandenen Records. Mutierende Commands schließen Self aus. Ambiguous oder unsichere Namen ergeben UUID statt zufälligem Target. Kein Verzeichnis-/globaler Known-Player-Scan; keine freie Reason-Completion.

Enable lädt den Service zunächst lokal; Hook-/Listener-Fehler werden bereinigt, bevor ein Service veröffentlicht wird. Alle fünf Executor-/Completer-Paare und der Listener müssen erfolgreich installiert sein. Disable entfernt eigene Hooks/Listener und Runtime-Referenzen, ohne zusätzlichen Save. Partielle Registrierung, Retry und Reverse-Rollback sind im tatsächlichen Modul getestet.

## Die angeforderten 92 Abschluss-Punkte

1. Neue Dateien: 15; vollständige Liste oben.
2. Geänderte Dateien: 9; vollständige Liste oben.
3. Entfernte Dateien: 0.
4. Phase Split: 25A umfasst Warn/Ban/Unban/Kick/History und synchrones Ban-Enforcement; Mute/Async-Projektion bleibt 25B.
5. ModerationModule: besitzt Commands/Listener, Dependency-Injection und rollback-sicheren Hook-Lifecycle.
6. Module Count: unverändert 26, in Source/Harness und Paper geprüft.
7. Reload Count: unverändert 6, Moderation ist kein ReloadParticipant.
8. Warn: verpflichtender Reason, Mutation über issueWarning, konkrete Usage/Feedback.
9. Warn Permission: vapeecore.moderation.warn, unabhängig, default op, Executor/Completion geprüft.
10. Offline Warn: bekannte Offline-Identitäten erlaubt; keine queued Notification.
11. Warning Notification: online erst nach erfolgreichem Save; Reason literal; externe Fehler behalten den Record.
12. Ban: explizite Duration und verpflichtender Reason, Mutation über issueBan.
13. Ban Permission: vapeecore.moderation.ban, unabhängig, default op.
14. Permanent Ban: permanent/perm case-insensitive, fehlende Expiry.
15. Temporary Ban: positive Dauer mit s/m/h/d/w, absolute UTC-Expiry.
16. Duration Parser: strikt, eigene kleine Klasse, normalisiertes Display, geteilte Clock.
17. Invalid Duration: kontrolliertes Feedback, keine Mutation/externe Aktion; inklusive Integer-/Multiplikations-/Instant-Overflow.
18. Already Banned: normaler No-op, kein Save, Success-Audit oder erneuter Disconnect.
19. Online Ban Ordering: Persistenz und Audit/Feedback vor Disconnect; durch Reihenfolge-Probes geprüft.
20. Offline Ban: bekannter Target wird gespeichert, kein OfflinePlayer/BanList-Aufruf.
21. Login Enforcement: aktiver BAN blockiert, WARNING/KICK/MUTE nicht.
22. PlayerLoginEvent: synchroner HIGHEST-Listener vor Join; kein AsyncPlayerPreLoginEvent-Servicezugriff.
23. Exact Expiry: genau auf expiresAt kein Ban-Deny; Phase-24-Semantik unverändert.
24. Revoked Ban: kein Ban-Deny, Record verbleibt in History.
25. Ban Screen: gemeinsame Adventure-Component für Login und Online-Ban, literal Reason und Permanent/UTC.
26. Unban: revokeBan des aktuell aktiven Bans, bekanntes Offline-Target erlaubt.
27. Unban Permission: vapeecore.moderation.unban; nicht implizit durch Ban gewährt.
28. Revoke Reason: optional, blank als absent; voller literal Reason in History.
29. Not Banned: normaler No-op ohne Save/Audit/SEVERE, einschließlich abgelaufen/widerrufen.
30. Kick: bekannte Online-Identität erforderlich; offline keine Kick-History.
31. Kick Permission: vapeecore.moderation.kick, independently default op.
32. Record Before Disconnect: recordKick und Persistenz vor tatsächlichem Kick; Ordering-Probes grün.
33. Kick Screen: eigener Kick-Text und literal Reason, kein Ban-Text.
34. Kick Save Failure: SEVERE und kontrolliertes Feedback, kein Disconnect; nachträglicher Disconnect-Fehler behält Record.
35. History: read-only Service-History, konkrete Empty-/Invalid-Page-Meldungen.
36. History Permission: vapeecore.moderation.history; gewährt keine Mutation.
37. Pagination: fünf Records pro Seite, newest-first, positive ganzzahlige Seiten, Overflow-/Bounds-Checks.
38. Actor Rendering: Runtime-Identityname, Console oder UUID-Fallback; keine Namen neu persistent gespeichert.
39. Status Rendering: Recorded/Active/Expired/Revoked/Inactive, keine Active-Semantik für WARNING/KICK.
40. Navigation: Previous/Next mit SUGGEST_COMMAND und Target-UUID; nie RUN_COMMAND.
41. Literal Reasons: Component.text in Feedback/Notification/History/Screens; Click-Tag-Injection bleibt Text.
42. Known Identity Resolution: ausschließlich PlayerIdentityService.resolve, keine Phantom-Identitäten/Netzwerk-Lookups.
43. UUID Support: bekannte UUIDs in allen fünf Commands, offline wo sinnvoll.
44. Ambiguity: konkrete UUID-Aufforderung statt zufälliger Auswahl; sichere Completion.
45. Self Guard: Player kann sich nicht warnen/bannen/unbannen/kicken; eigene History mit Permission erlaubt.
46. Audit Logging: INFO erst nach erfolgreicher Mutation; Actor/Target/Action/Record-ID/Expiry.
47. Persistence Failure: COW unverändert, SEVERE mit Kontext, kein false success und keine externe Aktion.
48. plugin.yml: genau fünf zusätzliche Commands und fünf unabhängige op-Nodes, Usage/Description, keine neuen Aliases.
49. Root Count: 29 → 34; Descriptor-Harness und Bukkit-Help im Paper-Smoke stimmen überein.
50. Core Help: permission-gefilterte Moderation-Section, nur die fünf 25A-Commands.
51. Permissions Doc: Nodes, unabhängige Rechte, Self Guard und Staff-Empfehlungen ergänzt.
52. ModerationCommandHarness: neu, 593 Checks; echte Domain/Service/Identity, Bukkit nur an der Boundary ersetzt.
53. ModerationDurationHarness: neu, 53 Checks; Syntax, Grenzen, Display und Expiry-Sicherheit.
54. ModerationBanEnforcementHarness: neu, 31 Checks; echte PlayerLoginEvents, Active/Expiry/Revocation/Literal/Read-only/Failure.
55. ModerationLifecycleHarness: erweitert von 44 auf 92 Checks; tatsächliches Modul, partielle Hook-Fehler, Cleanup/Retry.
56. Phase-24 Regression: Domain 112, Service 121, Persistence 523, Lifecycle 92 Checks; alle PASS, Domain-Invariants unverändert.
57. Command Regression: CoreCommandHarness 68 (vorher 57), CommandHelpHarness 85; übrige Command-Harnesses PASS.
58. Player Regression: Identity/Profile/Player-Persistence/Settings-Harnesses PASS; Production Join/Load unverändert.
59. Friend Regression: sämtliche Friend- und Friend-Visibility-Harnesses PASS; Production unverändert.
60. Clan Regression: sämtliche Clan-Domain/Service/Persistence/Integration/GUI-Harnesses PASS.
61. Economy Regression: Coins/Economy/Reward-Harnesses PASS.
62. Settings/Visibility Regression: sämtliche Settings-/Visibility-/Security-Harnesses PASS.
63. Teleport Regression: TeleportCommandHarness 62 und TeleportParserHarness 58 Checks PASS; Utility unverändert.
64. Chat Regression: ChatHarness 17 Checks PASS; Chat-Production unverändert, kein Async-Moderation-Zugriff.
65. PM Regression: PrivateMessageSocialHarness 91 Checks PASS; PM-Production unverändert.
66. Blackjack Regression: alle sechs Activity/Blackjack-bezogenen Harnesses PASS; Production unverändert.
67. Remaining Regression: Config, Lobby/Warp, Rank, Rewards/Quest/DailyQuest, Presentation, Seat, WorldDisplay und übrige Utility-Harnesses PASS; komplette Tabelle unten.
68. Total Harnesses: 84, jeder in eigenem Java-Prozess gestartet; alle ursprünglichen 81 plus drei neue main-Harnesses.
69. Total Checks: 5.487, gegenüber Baseline 4.751 um 736 erhöht.
70. Failures: 0 im finalen Gesamtlauf.
71. Build: mvn clean package über das bestehende Build-and-Deploy-Script, BUILD SUCCESS; Maven 3 aus IntelliJ, Java 21.
72. JAR Bytes: 968.858; Build-JAR und eingesetzte Dev-Server-JAR identisch.
73. SHA-256: A403C396D18E75F13364547305BC3515D83EC595831501A50DFE2B41EAD352BC.
74. Paper: 1.21.11-132-ver/1.21.11@c5eb079, API 1.21.11-R0.1-SNAPSHOT.
75. Java: Oracle 21.0.12.1+1-LTS-4, Windows 11 amd64.
76. LuckPerms: 5.5.84, erfolgreich connected.
77. Paper Smoke: finale JAR, 26 Module/6 Reload-Dateien/34 Roots, Commands/Core Help/Reload/Unknown Targets/Reverse Shutdown; keine ERROR/SEVERE.
78. Vanilla Namespace: unnamespaced ban/kick demonstrieren VapeeCore-Usage/Description; minecraft:ban/kick bleiben Mojang-Commands und zeigen native Incomplete-Syntax.
79. Live Client Status: Live client test: not performed. Screens sind Harness-getestet, nicht manuell visuell geprüft.
80. README: aktueller Phase-25A-Stand, Commands/Enforcement/Counts und 25B/26-Grenzen ergänzt.
81. Developer Guide: Ownership, Actor/Identity, Duration, Ordering, Sync-Login, History, Completion, Lifecycle und Future Boundaries ergänzt.
82. Permissions: Moderator warn/kick/history, Admin zusätzlich ban/unban, Owner mindestens Admin; Empfehlung, keine Runtime-Hierarchie.
83. COMMAND_AUDIT: unverändert, historischer Phase-23-Bericht.
84. MODERATION_FOUNDATION: unverändert, historischer Phase-24-Bericht.
85. FORMATTING: unverändert; nur lokaler ModerationComponents-Helper, kein neues globales Framework.
86. No Mute Command: weder mute noch unmute registriert; vapeecore:mute/unmute im Paper-Smoke unbekannt.
87. No Chat Mute Enforcement: ausdrücklich nicht implementiert; keine direkten AsyncChatEvent-Servicezugriffe.
88. No PM Mute Enforcement: ausdrücklich nicht implementiert.
89. No Rank Hierarchy: kein Group-/Weight-/Prefix-/Track-Raten. Future Finding Phase 26: Permission Assignment ist die primäre administrative Trust Boundary.
90. Phase 25B Readiness: issueMute/revokeMute/isMuted unverändert vorhanden; sichere immutable Mute-Projektion mit Veröffentlichung erst nach Save muss gezielt entworfen werden.
91. Known Issues: keine bekannten Produktionsfehler im geprüften Scope. PlayerLoginEvent ist in Paper seit 1.21.6 deprecated, auf Zielversion weiterhin vorhanden; künftige API-Modernisierung muss Thread Ownership erhalten. Live-Client-Validierung und Phase-26-Hierarchie stehen aus.
92. Merge Readiness: für den definierten 25A-Scope verifiziert und zum lokalen Commit bereit; kein automatischer Merge/Push. Exakt ein lokaler Commit mit dem vorgegebenen Titel; Commit-ID und finaler Git-Status werden im Chat berichtet.

## Vollständiger finaler Harness-Lauf

Nach dem finalen Clean-Build wurden alle Klassen mit `public static void main(` unter `src/test/java` separat gestartet. Der gemeinsame Test-Support hat kein main und zählt nicht als zusätzlicher Harness.

| Harness | Checks | Ergebnis |
|---|---:|---|
| `dev.vapee.core.activity.ActivityHarness` | 153 | PASS |
| `dev.vapee.core.activity.blackjack.BlackjackFairnessHarness` | 393 | PASS |
| `dev.vapee.core.activity.blackjack.BlackjackHarness` | 218 | PASS |
| `dev.vapee.core.activity.blackjack.BlackjackPresentationHarness` | 43 | PASS |
| `dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness` | 16 | PASS |
| `dev.vapee.core.activity.blackjack.table.BlackjackTableHarness` | 90 | PASS |
| `dev.vapee.core.chat.ChatHarness` | 17 | PASS |
| `dev.vapee.core.clan.ClanDomainHarness` | 52 | PASS |
| `dev.vapee.core.clan.ClanIntegrationHarness` | 15 | PASS |
| `dev.vapee.core.clan.ClanPersistenceHarness` | 39 | PASS |
| `dev.vapee.core.clan.ClanServiceHarness` | 136 | PASS |
| `dev.vapee.core.clan.gui.ClanCommandHarness` | 83 | PASS |
| `dev.vapee.core.clan.gui.ClanMenuHarness` | 84 | PASS |
| `dev.vapee.core.clan.gui.ClanMenuSecurityHarness` | 37 | PASS |
| `dev.vapee.core.command.CoreCommandHarness` | 68 | PASS |
| `dev.vapee.core.config.ConfigServiceHarness` | 34 | PASS |
| `dev.vapee.core.config.DefaultConsistencyHarness` | 11 | PASS |
| `dev.vapee.core.economy.command.CoinsCommandHarness` | 208 | PASS |
| `dev.vapee.core.economy.EconomyIntegrationHarness` | 24 | PASS |
| `dev.vapee.core.economy.EconomyServiceHarness` | 44 | PASS |
| `dev.vapee.core.friend.FriendCommandHarness` | 80 | PASS |
| `dev.vapee.core.friend.FriendDomainHarness` | 29 | PASS |
| `dev.vapee.core.friend.FriendIntegrationHarness` | 28 | PASS |
| `dev.vapee.core.friend.FriendLifecycleHarness` | 12 | PASS |
| `dev.vapee.core.friend.FriendPersistenceHarness` | 28 | PASS |
| `dev.vapee.core.friend.FriendServiceHarness` | 79 | PASS |
| `dev.vapee.core.friend.gui.FriendMenuHarness` | 159 | PASS |
| `dev.vapee.core.friend.gui.FriendMenuSecurityHarness` | 37 | PASS |
| `dev.vapee.core.identity.IdentityHarness` | 26 | PASS |
| `dev.vapee.core.identity.ProfileHarness` | 19 | PASS |
| `dev.vapee.core.lobby.command.LobbyCommandHarness` | 26 | PASS |
| `dev.vapee.core.lobby.player.LobbyHarness` | 53 | PASS |
| `dev.vapee.core.lobby.warp.WarpHarness` | 45 | PASS |
| `dev.vapee.core.message.CommandHelpHarness` | 85 | PASS |
| `dev.vapee.core.moderation.command.ModerationCommandHarness` | 593 | PASS |
| `dev.vapee.core.moderation.command.ModerationDurationHarness` | 53 | PASS |
| `dev.vapee.core.moderation.ModerationBanEnforcementHarness` | 31 | PASS |
| `dev.vapee.core.moderation.ModerationDomainHarness` | 112 | PASS |
| `dev.vapee.core.moderation.ModerationLifecycleHarness` | 92 | PASS |
| `dev.vapee.core.moderation.ModerationPersistenceHarness` | 523 | PASS |
| `dev.vapee.core.moderation.ModerationServiceHarness` | 121 | PASS |
| `dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness` | 42 | PASS |
| `dev.vapee.core.onlinereward.OnlineRewardServiceHarness` | 42 | PASS |
| `dev.vapee.core.player.repository.PlayerQuestPersistenceHarness` | 21 | PASS |
| `dev.vapee.core.player.repository.PlayerRewardPersistenceHarness` | 11 | PASS |
| `dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness` | 12 | PASS |
| `dev.vapee.core.player.settings.PlayerSettingsServiceHarness` | 24 | PASS |
| `dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness` | 11 | PASS |
| `dev.vapee.core.presentation.PlaytimeFormatterHarness` | 14 | PASS |
| `dev.vapee.core.presentation.PresentationHarness` | 10 | PASS |
| `dev.vapee.core.privatemessage.PrivateMessageSocialHarness` | 91 | PASS |
| `dev.vapee.core.quest.daily.DailyQuestConfigHarness` | 13 | PASS |
| `dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness` | 18 | PASS |
| `dev.vapee.core.quest.daily.DailyQuestLifecycleHarness` | 14 | PASS |
| `dev.vapee.core.quest.DailyQuestServiceHarness` | 20 | PASS |
| `dev.vapee.core.quest.QuestDefinitionHarness` | 29 | PASS |
| `dev.vapee.core.quest.QuestLifecycleHarness` | 42 | PASS |
| `dev.vapee.core.quest.QuestServiceHarness` | 44 | PASS |
| `dev.vapee.core.rank.RankCommandHarness` | 16 | PASS |
| `dev.vapee.core.rank.RanksCommandHarness` | 12 | PASS |
| `dev.vapee.core.rank.RankServiceHarness` | 23 | PASS |
| `dev.vapee.core.reward.RewardLifecycleHarness` | 40 | PASS |
| `dev.vapee.core.reward.RewardServiceHarness` | 126 | PASS |
| `dev.vapee.core.seat.SeatHarness` | 52 | PASS |
| `dev.vapee.core.settings.command.SettingsCommandHarness` | 25 | PASS |
| `dev.vapee.core.settings.SettingsMenuHarness` | 56 | PASS |
| `dev.vapee.core.settings.SettingsMenuSecurityHarness` | 34 | PASS |
| `dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness` | 23 | PASS |
| `dev.vapee.core.settings.visibility.SettingsNavigationHarness` | 17 | PASS |
| `dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness` | 41 | PASS |
| `dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness` | 29 | PASS |
| `dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness` | 74 | PASS |
| `dev.vapee.core.utility.command.BuildCommandHarness` | 17 | PASS |
| `dev.vapee.core.utility.command.TeleportCommandHarness` | 62 | PASS |
| `dev.vapee.core.utility.command.UtilityCommandHarness` | 152 | PASS |
| `dev.vapee.core.utility.TeleportParserHarness` | 58 | PASS |
| `dev.vapee.core.utility.UtilityInventoryHarness` | 36 | PASS |
| `dev.vapee.core.utility.UtilityServiceHarness` | 35 | PASS |
| `dev.vapee.core.visibility.FriendVisibilityRefreshHarness` | 11 | PASS |
| `dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness` | 7 | PASS |
| `dev.vapee.core.visibility.VisibilityModuleHarness` | 22 | PASS |
| `dev.vapee.core.visibility.VisibilityPolicyHarness` | 28 | PASS |
| `dev.vapee.core.visibility.VisibilityServiceHarness` | 10 | PASS |
| `dev.vapee.core.worlddisplay.WorldDisplayHarness` | 27 | PASS |

Summe: 84 Harnesses, 5.487 Checks, 0 Fehler. Lokales ausführliches Laufartefakt: `target/phase25a-harness-results.txt` (ignoriert).

## Finales Artefakt und Paper-Verifikation

- Build: `mvn clean package`, BUILD SUCCESS am 30.09.2026 um 19:17:19.
- JAR: `target/vapeecore-1.0-SNAPSHOT.jar`, 968.858 Bytes.
- Dev-Deployment: `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar`, identische Größe und SHA-256.
- SHA-256: `A403C396D18E75F13364547305BC3515D83EC595831501A50DFE2B41EAD352BC`.
- Paper: `1.21.11-132-ver/1.21.11@c5eb079`; Java `21.0.12.1+1-LTS-4`; LuckPerms `5.5.84`.
- Enable: 19:18:58; sauberer Stop: 19:21:01/02.
- 26 Enable-Einträge, 26 Disable-Einträge in exakt umgekehrter Reihenfolge; keine ERROR/SEVERE im aktuellen Serverlog.
- Reload vorbereitet/applied: config.yml, lobby.yml, chat.yml, private-messages.yml, presentation.yml, daily-quests.yml; Erfolg in 22 ms. Keine Moderation-Reload-Teilnahme.
- Bukkit Help VapeeCore: 34 Root-Einträge einschließlich der fünf neuen Commands.
- `core`, `core help`, `core version`, `core reload`: aktuelle Anzeige/Moderation-Help und erfolgreiche Reload-Rückmeldung.
- Alle fünf unnamespaced und `vapeecore:`-Commands ohne Argumente: konkrete VapeeCore-Usage. Unbekannte Targets: kontrollierte bekannte-Identity-Ablehnung.
- Help und leere Aufrufe von `ban`/`kick` identifizieren VapeeCore; Help für `minecraft:ban`/`minecraft:kick` identifiziert Mojang-Commands, deren leere Aufrufe native Incomplete-Syntax zeigen. Kein Namespace-Lockdown.
- `vapeecore:mute`/`vapeecore:unmute`: unbekannt.
- `history 0d9efbe6-33bf-4cf7-ae67-61d6238b2561`: bestehende bekannte Offline-Identität rxaq, konkrete leere History. Read-only, keine Sanktion.
- Keine Spieler online/geladen; keine künstlichen Warnungen/Bans/Kicks oder Benachrichtigungen. `moderation.yml` war vor/nach Enable, Reload und Shutdown nicht vorhanden; kein eager Save.
- Der über das bestehende Start-Script gestartete Prozess ist beendet, kein verbleibender Prozessmarker.
- Hook-/Listener-Cleanup ist durch tatsächlichen Lifecycle-Harness und Source geprüft; im Server-Smoke erfolgte sauberer Modul-Shutdown. Keine Behauptung einer nach Shutdown introspektierten Executor-Instanz.
- Paper meldet die Zielversion 1.21.11 als aktuell für ihre Minecraft-Linie, aber älter als die neuere Minecraft-Linie 26.2. Diese Versionswarnung ist kein VapeeCore-ERROR und führte zu keinem Versionswechsel.
- Live client test: not performed. Keine reale Login-/Disconnect-Session; Screens und Enforcement wurden vollständig an den Harness-Boundaries geprüft.

## Future Boundaries und Diff-Audit

25B muss eine ausdrücklich entworfene threadsichere/immutable Mute-Projektion für AsyncChatEvent besitzen. ModerationService einschließlich Reads bleibt main-thread-owned. Projektion darf einen noch nicht erfolgreich gespeicherten Zustand nicht vorzeitig veröffentlichen. Kein Nebenbei-Async-Zugriff, keine 25A-Chat-/PM-Änderung.

Phase 26 entscheidet die Staff-Target-Hierarchie. Bis dahin ist gezielte Permission-Vergabe die primäre administrative Trust Boundary; op-Defaults sind keine Hierarchie. Keine erfundenen Rollen-/LuckPerms-Weight-Regeln in 25A.

Diff-Audit: nur das oben gelistete Inventar; historische Berichte und Foundation-Domain/Service/Repository unverändert, kein Schema-/Config-Wechsel. Production von chat, privatemessage, player, identity, friend, clan, economy, settings, visibility, utility und activity unverändert. Source-Review, Descriptor-/Lifecycle-Prüfungen, `git diff --check`, finaler Clean-Build, Gesamtregression und Paper-Smoke grün.

Git-Abschlussvorgabe: genau ein lokaler Commit `Phase 25A - Moderation Commands and Ban Enforcement`; kein Push, Rebase, Reset oder History Rewrite. Der abschließende Chat enthält `git log -1 --oneline`, sauberen Git-Status und die Änderungsliste gegenüber der bestätigten Baseline.
