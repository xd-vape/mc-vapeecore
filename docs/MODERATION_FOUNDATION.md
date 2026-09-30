# Phase 24 – Moderation Foundation

Abschluss vom 2026-09-30. Baseline `c70ba412c8daf4edb2ccd6364dfb22aa8cc1a065` (Merge Phase 23), Branch `phase/24-moderation-foundation`; Arbeitsbaum zu Beginn sauber. Genau ein Worker, keine Delegation. Alle 81 Harnesses wurden nach dem letzten Clean-Build separat ausgeführt, ohne gleichzeitige Neukompilierung.

Ergebnis: **81 Harnesses, 4751 Checks, 0 Failures**. Vollständige bisherige Regression: 77 / 3951; neue Moderation: 4 / 800. Finaler Clean-Build und Smoke auf der identischen deployten JAR erfolgreich. Foundation-Scope merge-ready; keine Commands oder Enforcement.

## Die 105 Abschluss-Punkte

1. Neue Dateien: 15 Moderation-Produktionsklassen, vier Harnesses und dieser Bericht; vollständiges Inventar unten.
2. Geänderte Dateien: VapeeCore.java, README, Developer Guide und neun bestehende Module-Count-Prüfungen; Inventar unten.
3. Entfernte Dateien: keine.
4. ModerationModule: tatsächliches CoreModule, lokaler Enable-Load vor Veröffentlichung, Getter nur wenn enabled; Disable ohne Save.
5. Module Order: Identity → Moderation → Friend; vollständige 26er-Reihenfolge im Developer Guide und im Paper-Log bestätigt.
6. Module Count: 26 beim Start und Stop, exakt rückwärts deaktiviert.
7. Reload Count: unverändert 6; Moderation ist kein ReloadParticipant.
8. Datei: plugins/VapeeCore/moderation.yml, vollständig getrennt von players/<uuid>.yml und Bukkit-Banlisten.
9. Schema: Version 1, exakte Pflichtkeys einschließlich expliziter nullable Felder.
10. Missing file: leerer Snapshot, keinerlei eager Write; auch Parent wird beim Load nicht angelegt.
11. First write: erster erfolgreicher Service-Save erstellt Parent und Datei.
12. ModerationAction: ausschließlich WARNING, MUTE, BAN, KICK.
13. WARNING: historischer Eintrag, nie active, kein Ablauf oder Widerruf.
14. MUTE: permanent/temporär, active ab Erstellung bis vor Ablauf, widerrufbar.
15. BAN: dieselben Active-/Expiry-/Revocation-Regeln wie MUTE, eigenständiger Typ.
16. KICK: ausschließlich History; kein tatsächlicher Disconnect.
17. ModerationActor: immutable Typ plus Optional<UUID>, ohne Namen oder Bukkit-Referenz.
18. PLAYER: genau eine verpflichtende Actor-UUID.
19. CONSOLE: ausdrücklich kein Player-UUID-Wert.
20. ModerationRecord: id, action, targetId, actor, reason, createdAt, expiresAt, revocation; immutable und nullsafe.
21. Record UUID: eigene ID aus Supplier, Kollision scheitert ohne Save.
22. Target UUID: getrennte UUID; mehrere Facts pro Target, ohne Player-Cache-/Online-Abhängigkeit.
23. Reason: strip, nonempty, maximal 256 Unicode-Codepoints; keine Controls/Newlines/kaputten Surrogates; literal Text.
24. createdAt: exaktes Instant aus injiziertem Clock, inklusive Nanosekunden.
25. expiry: Optional<Instant>, wenn present strikt nach Erstellung.
26. revocation: Optional<ModerationRevocation>, Actor, Zeitpunkt >= Erstellung und optionaler validierter Grund.
27. Permanent: MUTE/BAN mit absent expiresAt.
28. Temporary: MUTE/BAN mit future expiresAt, keine Scheduler.
29. Exact expiry boundary: t == expiresAt ist inactive; Domain, Service und Roundtrip geprüft.
30. Active-state: MUTE/BAN, createdAt <= t, kein Widerruf, absent Ablauf oder t < Ablauf. Future-created vorher inactive.
31. issueWarning: neues gespeichertes WARNING-Fact mit eigener ID.
32. recordKick: neues gespeichertes KICK-Fact, ohne Enforcement.
33. issueMute: neues MUTE-Fact nach Input-/Duplicate-Validierung.
34. issueBan: neues BAN-Fact nach Input-/Duplicate-Validierung.
35. Duplicate active mute: ALREADY_MUTED, kein Save oder ID-Verbrauch.
36. Duplicate active ban: ALREADY_BANNED, kein Save oder ID-Verbrauch.
37. Expired reissue: für MUTE und BAN geprüft; alte History bleibt erhalten.
38. Revoked reissue: neue ID, alte widerrufene History bleibt erhalten, auch nach Load.
39. revokeMute: ersetzt genau denselben Record durch immutable widerrufene Kopie.
40. revokeBan: derselbe Ablauf; Original-ID, Actor, Reason und Creation bleiben erhalten.
41. NOT_MUTED: absent, expired oder revoked → leerer Result-Record, kein Write.
42. NOT_BANNED: dieselbe No-op-Regel für BAN.
43. History: createdAt DESC, kanonischer UUID-String ASC als Tie-Break.
44. Reads: immutable Listen/Records, alte Views unverändert, keine Query-Writes.
45. Snapshot: Nullwerte und doppelte IDs abgewiesen, defensive immutable Kopie.
46. Deterministic order: Storage createdAt ASC, UUID-String ASC; YAML unabhängig von Input-Reihenfolge.
47. Duplicate ID: sowohl Snapshot/Load als auch Issue-Supplier-Kollision ohne Publikation abgewiesen.
48. Save-before-swap: repository.save(next) strikt vor state = next, durch Save-Callback sichtbar geprüft.
49. Failure atomicity: alle sechs Mutationen mit injiziertem Save-Fehler geprüft; exakt alter Snapshot/History/Active-State bleibt bestehen.
50. No-save failure results: ALREADY_* und NOT_* schreiben nie; Exceptions bleiben Exceptions.
51. UTF-8: Akzente, Emoji und literal Tags/Farbcodes verlustfrei roundtripped.
52. SafeConstructor: SnakeYAML SafeConstructor, kein Object Construction; kleine lokale Merge-Key-Sperre.
53. Duplicate YAML keys: allowDuplicateKeys=false, Root/Record/Actor/Revocation-Korruption getestet.
54. Unknown/missing keys: bei Root und allen verschachtelten Maps abgewiesen, inklusive Nicht-String-Keys und << Merge.
55. Invalid schema: missing, falscher Typ und andere Version → ModerationRepositoryException, kein Fallback.
56. Invalid enum: unbekannte Action/ActorType und falsche Großschreibung abgewiesen.
57. Invalid UUID: falsche Typen, Short-UUIDs, Whitespace und ungültige Strings abgewiesen; canonical case-insensitive erlaubt.
58. Invalid timestamps: nur exakte kanonische UTC-ISO-8601-Strings, Instant.parse plus Canonical-Vergleich; volle Instant-/Nanosekunden-Präzision.
59. Load invariants: Reason, Actor-Kombination, Action/Expiry/Revoke und Reihenfolge der Zeitpunkte validiert; Quelldatei byte-identisch.
60. Atomic move: geschlossene Sibling-Tempdatei mit ATOMIC_MOVE + REPLACE_EXISTING.
61. Fallback: ausschließlich bei AtomicMoveNotSupportedException, kontrolliertes REPLACE_EXISTING.
62. Temp cleanup: Erfolg und drei fehlgeschlagene Move-Modi geprüft; Cleanup best-effort mit injiziertem Logger.
63. Clock injection: Service-/Lifecycle-Tests deterministisch; Runtime Clock.systemUTC(), kein Instant.now() im Service.
64. UUID supplier injection: deterministische Issue-IDs im Test; Runtime UUID::randomUUID.
65. Main-thread ownership: gesamte Service-API inklusive Reads; kein Async-Cache, Scheduler, Event oder globale Singleton-Registry.
66. ModerationDomainHarness: 112 tatsächlich ausgeführte Checks.
67. ModerationServiceHarness: 121 tatsächlich ausgeführte Checks.
68. ModerationPersistenceHarness: 523 tatsächlich ausgeführte Checks.
69. ModerationLifecycleHarness: 44 tatsächlich ausgeführte Checks, tatsächlicher Module-Enable/Disable und ModuleManager-Rollback.
70. Bisherige 77 Harnesses: vollständig separat erneut ausgeführt, 3951 Checks; nur neun notwendige Count-Erwartungen 25 → 26 angepasst.
71. Harnesses insgesamt: 81, alle einzeln gestarteten Ergebnisse unten.
72. Checks insgesamt: 4751, davon 800 neue Moderation-Checks.
73. Failures: 0 im finalen vollständigen Lauf.
74. Player regression: alle Player-Persistence-/Settings-Harnesses grün, Player-Produktion und YAML-Schema unverändert.
75. Identity regression: Identity und Profile grün; keine neue Namensauflösung/Persistence.
76. Friend regression: alle Domain/Service/Persistence/Command/GUI/Lifecycle-/Visibility-Harnesses grün, Produktion unverändert.
77. Clan regression: alle Domain/Service/Persistence/Integration/Command/GUI-Harnesses grün, Produktion unverändert.
78. Economy regression: Service, Integration und 208 Coins-Command-Checks grün, Produktion unverändert.
79. Reward regression: Reward/OnlineReward/Quest/DailyQuest und Persistence/Lifecycle vollständig grün.
80. Settings/Visibility regression: alle Menu-, Security-, Navigation-, Policy-, Service- und Live-Refresh-Harnesses grün.
81. Command regression: Core, Lobby, Rank, Profile, Coins, Friend, Clan, PM/Social und Help grün.
82. Teleport regression: Parser, Command, Build/Utility-Guards und Inventory-Sicherheit grün; Produktion unverändert.
83. Blackjack regression: Gameplay, Fairness, Tables, World-Presentation und Preview grün; keine Gameplay-Änderung.
84. Remaining regression: Activity, Seat, WorldDisplay, Chat, Config/Defaults, Warp, Lobby und Presentation ebenfalls grün.
85. mvn clean package: BUILD SUCCESS; final 280 Produktions- und 86 Test-Source-Dateien kompiliert. Main-Harnesses separat gestartet, nicht als Surefire behauptet.
86. JAR: target/vapeecore-1.0-SNAPSHOT.jar, 935760 Bytes.
87. SHA-256: 118B880F5A88C2E24798D76FBD2F2E475E225413809F8B6CB1926A02BE09E372; deployte JAR identisch.
88. Paper: 1.21.11-132-ver/1.21.11@c5eb079, Minecraft 1.21.11.
89. Java: Oracle 21.0.12.1+1-LTS-4.
90. LuckPerms: 5.5.84.
91. Paper Smoke: Start 26 Module, Core/Help/Version, Reload 6 in 15 ms, vorhandene Commands, Namespace-Negativtests, Clean Stop Exit 0; 0 ERROR/SEVERE.
92. Live client test: not performed. Foundation besitzt noch keine Player-facing Moderation-UX; 0 Spieler online.
93. plugin.yml: byte-identisch zur Baseline; weiterhin 29 Root-Commands, kein neuer Moderations-Command.
94. README: Phase-24-Status, Scope, Ownership und 26/6 aktualisiert.
95. Developer Guide: Module-Wiring, Domain, API, Snapshot/COW, Actor, genaues Schema, Thread-Grenze und Phase 25 dokumentiert.
96. PERMISSIONS: unverändert, keine neuen Nodes oder geänderten Children.
97. COMMAND_AUDIT: unverändert, historischer Phase-23-Bericht.
98. FORMATTING: unverändert, keine Player-facing Templates.
99. Keine Staff Commands: /warn, Moderations-/kick, /mute, /unmute, /ban, /unban, /history, /freeze nicht implementiert; VapeeCore-Namespace im Smoke negativ geprüft.
100. Kein Ban Enforcement: kein Join/Login-Listener, keine Bukkit-Banlisten-Mutation.
101. Kein Mute Enforcement: Chat und PM bleiben vollständig unverändert, keine Filter oder Enforcement-Listener.
102. Keine Moderation GUI, Staff Notes, Evidence, IPs, Scope, Dauerparser, Auto-Eskalation oder Eventbus.
103. Phase 25 readiness: stabile UUID-basierte Service-/Persistence-Grenze; Commands/Permissions/Identity-Adapter und Enforcement separat mit expliziter Async-Strategie ergänzen.
104. Known issues: keine bekannten Produktionsfehler im geprüften Scope. Replace-Fallback garantiert keine Atomicität bei hartem OS/JVM-Abbruch; externe parallele Writer nicht unterstützt. Kein Live-Client-Test; Paper-Versionswarnung betrifft neuere Release-Linie, kein Scope-Wechsel.
105. Merge Readiness: bereit im geprüften Foundation-Scope nach grünem vollständigem Lauf, Clean-Build, Paper-Smoke und Diff-Audit; genau ein lokaler Commit, kein Push.

## Vollständiges Datei-Inventar

Neue Dateien (20):

- `docs/MODERATION_FOUNDATION.md`
- `src/main/java/dev/vapee/core/moderation/FileModerationRepository.java`
- `src/main/java/dev/vapee/core/moderation/ModerationAction.java`
- `src/main/java/dev/vapee/core/moderation/ModerationActor.java`
- `src/main/java/dev/vapee/core/moderation/ModerationActorType.java`
- `src/main/java/dev/vapee/core/moderation/ModerationFileMover.java`
- `src/main/java/dev/vapee/core/moderation/ModerationModule.java`
- `src/main/java/dev/vapee/core/moderation/ModerationRecord.java`
- `src/main/java/dev/vapee/core/moderation/ModerationRepository.java`
- `src/main/java/dev/vapee/core/moderation/ModerationRepositoryException.java`
- `src/main/java/dev/vapee/core/moderation/ModerationResult.java`
- `src/main/java/dev/vapee/core/moderation/ModerationRevocation.java`
- `src/main/java/dev/vapee/core/moderation/ModerationService.java`
- `src/main/java/dev/vapee/core/moderation/ModerationSnapshot.java`
- `src/main/java/dev/vapee/core/moderation/ModerationStatus.java`
- `src/main/java/dev/vapee/core/moderation/ModerationText.java`
- `src/test/java/dev/vapee/core/moderation/ModerationDomainHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationLifecycleHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationPersistenceHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationServiceHarness.java`

Geänderte Dateien (12):

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/test/java/dev/vapee/core/clan/ClanIntegrationHarness.java`
- `src/test/java/dev/vapee/core/economy/EconomyIntegrationHarness.java`
- `src/test/java/dev/vapee/core/friend/FriendLifecycleHarness.java`
- `src/test/java/dev/vapee/core/identity/IdentityHarness.java`
- `src/test/java/dev/vapee/core/onlinereward/OnlineRewardLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/reward/RewardLifecycleHarness.java`
- `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java`

Entfernte Dateien: keine. Production außerhalb `moderation/**` ausschließlich die vier Wiring-Zeilen in `VapeeCore.java`. Bestehende Tests ändern ausschließlich neun Modulzahl-Erwartungen von 25 auf 26 und deren Beschriftung; keine bestehenden fachlichen Checks entfernt oder abgeschwächt. Player-/Identity-/Friend-/Clan-/Economy-/Reward-/Chat-/PM-/Settings-/Visibility-/Utility-/Blackjack-Produktion, sämtliche Resource-YAMLs, PERMISSIONS, COMMAND_AUDIT und FORMATTING sind zur Baseline unverändert.

## Alle tatsächlich separat ausgeführten Harnesses

| Harness | Checks | Result |
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
| `dev.vapee.core.command.CoreCommandHarness` | 57 | PASS |
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
| `dev.vapee.core.moderation.ModerationDomainHarness` | 112 | PASS |
| `dev.vapee.core.moderation.ModerationLifecycleHarness` | 44 | PASS |
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

## Paper-Smoke und Git-Grenze

Die finale JAR wurde über das bestehende Clean-Stop-/Build-/Deployment-Skript erzeugt und kopiert. Quelle und Deployment besitzen SHA-256 `118B880F5A88C2E24798D76FBD2F2E475E225413809F8B6CB1926A02BE09E372`, jeweils 935760 Bytes. Anschließend Paper-Start 17:33:33, 26 Module enabled; `Identity → Moderation → Friend` sichtbar. `/core` meldet Running / Active Modules 26 / LuckPerms Connected. `/core help` und `/core version` korrekt. `/core reload` bereitet exakt config, lobby, chat, private-messages, presentation und daily-quests vor, erfolgreicher Apply in 15 ms; danach weiterhin 26 Module. `/profile`, `/friend`, `/clan`, `/coins`, `/rank`, `/ranks`, `/settings`, `/warp list` und `/blackjack setup list` liefern erwartete Console-/Empty-State-Ausgaben. Bukkit-Help indexiert unverändert 29 Root-Commands. `vapeecore:warn/mute/unmute/ban/unban/kick/history/freeze` sind unbekannt; Vanilla-Commands wurden nicht als neue Plugin-Commands verwechselt oder abgefangen.

`moderation.yml` vor Start, nach Enable, nach Reload und nach Stop jeweils fehlend. Keine synthetischen Sanktionen/Playerdaten im Dev-Server angelegt; alle Persistence-/Lifecycle-Harnesses verwenden aufgeräumte Temp-Verzeichnisse. `list`: 0 Spieler. Clean Stop 17:35:32, 26 Module exakt rückwärts deaktiviert, Exitcode 0, kein PID-Marker. Gesamtes aktuelles Paper-Log: 0 ERROR/SEVERE. Vorhandene Paper-Warnung weist auf eine neuere Minecraft-Release-Linie hin; ein Versionswechsel ist nicht Teil dieser Phase. **Live client test: not performed.**

`git diff --check` erfolgreich. Vorgeschriebener einzelner lokaler Commit: `Phase 24 - Moderation Foundation`; kein Push. Der finale Commit-Hash und der saubere Arbeitsbaum werden nach dem Commit im Chat bestätigt. Vollständiges Inventar gegen die Baseline: `git diff c70ba412c8daf4edb2ccd6364dfb22aa8cc1a065..HEAD --name-only` (32 Dateien, identisch zum obigen Inventar).
