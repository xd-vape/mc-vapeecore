# Phase 26A – Staff Hierarchy and Moderation Protection

Stand: 30.09.2026. Umsetzung durch genau einen Worker, ohne Delegation. Repository: xd-vape/mc-vapeecore. Branch: `phase/26a-staff-hierarchy-moderation-protection`.

Ausgangsbasis: `f13d55643aae47024d31e83c948b17d240688d99` — Merge Phase 25B - Mute and Communication Enforcement. Branch, exakter HEAD und leerer `git status --short` wurden vor der ersten Änderung geprüft.

Ergebnis: **26 Module, sechs ReloadParticipants, 36 Root-Commands; 88 separat ausgeführte Harnesses, 8.620 Checks, final 0 Fehler.** Staff-Schutz ergänzt bestehende Permissions, gewährt aber selbst keine Fähigkeiten. Kein Push, Reset, Rebase oder History Rewrite.

## Architektur und Sicherheitsgrenze

`RankModule` besitzt den separaten `StaffHierarchyService`. Der unveränderte `RankService` dient weiter nur der Darstellung. `ConfigService` hält die Staff-Konfiguration im bestehenden immutable, volatile publizierten CoreConfigState. Es gibt keinen neuen Reload-Teilnehmer und keine zyklische Moderation-Abhängigkeit.

```yaml
staff:
  hierarchy:
    protected-groups:
      - builder
      - moderator
      - admin
      - owner
```

LOW→HIGH ist die explizite Sicherheitsreihenfolge. Nur die aktuelle primäre LuckPerms-Gruppe zählt. Gruppen werden trim/case-insensitive normalisiert; null/empty/Whitespace/Controls/Duplikate sind ungültig. Fehlend lädt sichere Defaults; explizit ungültig warnt und lädt dieselben Defaults, ohne Datei-Rewrite. Leere Listen deaktivieren den Schutz ausdrücklich nicht.

| Actor, Command-Permission vorhanden | Target | Entscheidung |
|---|---|---|
| Beliebiger Player | Gruppe nicht geschützt | ALLOW |
| Nicht geschützter Player | Geschützt | DENY_ACTOR_NOT_PROTECTED |
| Geschützter Player | Strikt niedrigeres Level | ALLOW |
| Geschützter Player | Gleiches oder höheres Level | DENY_SAME_OR_HIGHER |
| Player | Unsichere notwendige Gruppenauflösung | UNAVAILABLE, fail closed |
| Console | Bekanntes Ziel | Hierarchie erlaubt; Permission bleibt erforderlich |

Self-Warn/Mute/Unmute/Ban/Unban/Kick bleibt vor Hierarchie gesperrt. Self-History ist mit Permission erlaubt. Owner→anderer Owner bleibt verboten. OP, LP-Weight, Prefix/Suffix, Farben, Display Names, öffentlicher Track und geerbte Gruppen liefern keine zusätzliche Ausnahme.

Befehlsreihenfolge: Permission → Sender/Actor → Arity → Known-Identity → Self-Regel → Staff-Hierarchie → execute → Domain-Save → Audit/Feedback/externe Aktion. Alle sieben Commands verwenden dieselbe lokale Grenze in AbstractModerationCommand. Die einzelnen Executors und die Moderations-Domain wurden nicht umgeschrieben.

## Offline-Auflösung und Thread-Grenze

Für geladene User liest LuckPermsService die aktuelle Primary Group ohne Storage-Load. Für einen bekannten ungecacheten Target startet `loadPrimaryGroup(UUID)` die LP-API `loadUser(UUID)` und mappt ihr Ergebnis auf Optional<String>. Kein `join()` oder `get()` auf Load-Futures im Produktionsfluss, kein Sleep/Poller und kein LP-Save. Eine Offline-Identität allein darf niemals den Staff-Schutz umgehen.

Der LP-Callback übergibt nur Ergebnis/Fehler und einen Runnable an den injizierten Main-Thread-Consumer. Keine Player-/CommandSender-/MessageService-/ModerationService-Operation im Callback. Production verwendet einen einmaligen Scheduler.runTask. Erst der Runnable prüft aktive Enable-Generation, ursprüngliche Online-Session und aktuelle Permission. Actor-Gruppe wird spät gelesen; genau ein aktueller immutable Config-Snapshot bestimmt beide Levels. Target-Gruppe ist der erlaubte Snapshot des Load-Ergebnisses. Der ursprüngliche Args-Array wird kopiert.

Logout, Reconnect, Permission-Entzug oder Disable/Re-enable führt zu keiner Mutation. Auch bereits queued alte Runnables bleiben nach Re-enable ungültig. Unavailable/invalid/failed Load loggt SEVERE mit Kontext und zeigt kontrolliertes Feedback ohne Gruppen-/Level-/Stacktrace-Leak. Scheduling-Fehler loggt ausschließlich, ohne Async-Player-Feedback.

Completion verwendet nur synchron sichere Online-Auflösung und verbirgt equal/higher/unsichere Targets. Aktive Offline-Unban/Unmute-Kandidaten bleiben aus dem committed Snapshot verfügbar; keine Massen-Loads. Ausführung bleibt autoritativ.

## Vollständiges Datei-Inventar

### Neu (6)

- `docs/STAFF_HIERARCHY.md`
- `src/main/java/dev/vapee/core/rank/staff/StaffHierarchyConfig.java`
- `src/main/java/dev/vapee/core/rank/staff/StaffHierarchyService.java`
- `src/main/java/dev/vapee/core/rank/staff/StaffTargetDecision.java`
- `src/test/java/dev/vapee/core/permission/LuckPermsAsyncHarness.java`
- `src/test/java/dev/vapee/core/rank/staff/StaffHierarchyServiceHarness.java`

### Geändert (16)

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/PERMISSIONS.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/config/ConfigService.java`
- `src/main/java/dev/vapee/core/moderation/ModerationModule.java`
- `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java`
- `src/main/java/dev/vapee/core/permission/LuckPermsService.java`
- `src/main/java/dev/vapee/core/rank/RankModule.java`
- `src/main/resources/config.yml`
- `src/test/java/dev/vapee/core/config/ConfigServiceHarness.java`
- `src/test/java/dev/vapee/core/config/DefaultConsistencyHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationLifecycleHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationTestSupport.java`
- `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`

### Entfernt

Keine Dateien entfernt. Runtime-/Build-Artefakte unter target/ und dev-server/ sind nicht Teil des Commits.

### Explizit unverändert

`plugin.yml`, `docs/FORMATTING.md`, `docs/COMMAND_AUDIT.md`, `docs/MODERATION_FOUNDATION.md`, `docs/MODERATION_TOOLS.md`, `docs/MODERATION_MUTES.md`; RankService/RankInfo/RankCommands; ModerationService/ModerationRecord/Repository/MuteProjection/Login-/Chat-Enforcement; Chat/PM, Utility, Teleport, Inventory und Economy.

Kein neues Permission-Node/Root-Command/Alias, keine Player-YAML- oder moderation.yml-Schemaänderung, keine Migration historischer Sanktionen, kein zusätzlicher periodischer Task.

## Alle 103 angeforderten Abschluss-Punkte

| Nr. | Thema | Ergebnis / Nachweis |
|---:|---|---|
| 1 | Neue Dateien | Sechs Dateien, vollständig unten aufgelistet: drei Staff-Klassen, zwei Harnesses und dieser Bericht. |
| 2 | Geänderte Dateien | 16 bestehende Dateien; vollständiges Inventar unten. Keine außerhalb des vereinbarten Scope. |
| 3 | Entfernte Dateien | Keine. |
| 4 | Baseline | f13d55643aae47024d31e83c948b17d240688d99, „Merge Phase 25B - Mute and Communication Enforcement“; initial sauberer Working Tree auf phase/26a-staff-hierarchy-moderation-protection. |
| 5 | Scope | Staff-Hierarchie, read-only LP-Auflösung, zentrale Autorisierung aller sieben Moderationsbefehle, sichere Completion, Config/Tests/Dokumentation. |
| 6 | 26A/26B-Split | 26A nur Moderation; Utilities/Teleport/Inventory, Economy und finaler Permission-/Inheritance-Audit erst 26B. |
| 7 | Module count | Unverändert 26; tatsächlicher Paper-Start und Shutdown jeweils 26, Lifecycle-Harness prüft genaue Registrierungsreihenfolge. |
| 8 | Reload count | Unverändert sechs: config.yml, lobby.yml, chat.yml, private-messages.yml, presentation.yml, daily-quests.yml. |
| 9 | Root command count | Unverändert 36; Descriptor-Test und tatsächliche Paper-Help-Ausgabe bestätigen dies. |
| 10 | StaffHierarchyConfig | Immutable Record; normalisierte, defensiv kopierte Liste; eindeutige LOW→HIGH-Reihenfolge. |
| 11 | Default groups | builder, moderator, admin, owner; Java-Fallback und Resource identisch. |
| 12 | LOW→HIGH | Index ist Schutzlevel; ungeschützte Gruppen ergeben -1. Reihenfolge ist sicherheitsrelevant. |
| 13 | Config validation | Nicht leere Liste aus Strings; null, empty/blank, interne Whitespace-/Space-Zeichen, Controls und normalisierte Duplikate ungültig. WARNING + vollständige Defaults, Datei unverändert. |
| 14 | Missing config fallback | Fehlende Legacy-Keys verwenden sichere Defaults ohne Rewrite. Explizites YAML-null wird vom fehlenden Key unterschieden. |
| 15 | Config reload | Bestehender ConfigService-ReloadParticipant: Prepare ohne Mutation, atomarer volatile State-Swap, exakter Rollback. |
| 16 | StaffHierarchyService | Read-only, Bukkit-frei; injizierte Config-/Loaded-Group-/Async-Load-Quellen, kein eigener Cache. |
| 17 | Ownership | RankModule erstellt und veröffentlicht den Service erst nach erfolgreichem Enable; Disable nullt Referenzen. Moderation erhält RankModule ausdrücklich; keine Rückabhängigkeit. |
| 18 | Primary group source | Nur aktuelle LuckPerms-Primary-Group-ID; keine Player-YAML-/Identity-Rangquelle oder geerbtes Maximum. |
| 19 | No weight auth | LP-Weight bleibt Präsentationsmetadatum; nicht für Schutzlevel gelesen. |
| 20 | No prefix auth | Prefix/Suffix/Display Name/Color haben keine Autorisierungswirkung. |
| 21 | No rank-track auth | ranks.track und Track-Position bleiben ausschließlich öffentliches Presentation-Modell. |
| 22 | Normal target semantics | Sicher aufgelöste Gruppe außerhalb der Liste ist mit Command-Permission moderierbar, auch durch nicht geschützten Actor. |
| 23 | Protected target semantics | Geschützter Actor UND strikt höheres Schutzlevel erforderlich; Command-Permission bleibt Pflicht. |
| 24 | Strictly-greater rule | actorLevel > targetLevel, niemals >=. |
| 25 | Same-level deny | Alle gleichen Staff-Level inklusive Owner→anderer Owner verboten; Self-History ist die ausdrückliche Ausnahme. |
| 26 | Higher-level deny | Niedrigerer Actor darf höheres geschütztes Ziel nicht moderieren oder dessen History lesen. |
| 27 | Lower-level allow | Mit passender Command-Permission darf strikt höherer Staff ein niedrigeres geschütztes Ziel moderieren. |
| 28 | Unprotected actor | default/vip/custom außerhalb der Liste darf geschütztes Staff-Ziel auch mit OP/Command-Recht nicht angreifen. |
| 29 | Console semantics | Console umgeht ausschließlich Hierarchie; Sender-Type-/Syntax-/Known-Identity-/Command-Permission-Regeln bleiben. |
| 30 | OP semantics | Kein isOp-Check, kein OP-Hierarchie-Bypass; Bukkit-OP-Defaults in plugin.yml unverändert. |
| 31 | Builder | Default-Level 0, geschützt; keine Moderationsfähigkeit durch Hierarchie oder automatische Permission-Vergabe. |
| 32 | Moderator | Default-Level 1; Builder ist niedriger, Moderator/Admin/Owner nicht. |
| 33 | Admin | Default-Level 2; Builder/Moderator niedriger, Admin/Owner nicht. |
| 34 | Owner | Default-Level 3; keine globale Ausnahme und kein Owner→Owner-Zugriff. |
| 35 | Loaded resolution | LuckPerms UserManager#getUser und aktuelle Primary Group; loadPrimaryGroup liefert für geladenen User completedFuture. |
| 36 | Offline async load | Für bekannte ungecachete Ziele UserManager#loadUser(UUID).thenApply auf Optional<String>; keine Synchron-Warteoperation. |
| 37 | No main-thread blocking | Im LP-Load-/Command-Fluss kein Future.join()/Future.get(), Sleep oder Polling. String.join für Gründe ist ausdrücklich keine Future-Warteoperation. |
| 38 | Failure behavior | Fehlendes/blankes/invalides Ergebnis oder LP-Exception bricht ohne Save/Success-Audit/Notice/Kick ab. Kontext im SEVERE-Serverlog, kontrollierte literal Rückmeldung ohne Stacktrace/Rangdetails. |
| 39 | Main-thread continuation | Nur der injizierte Consumer<Runnable> wird im Callback angesprochen; Production Scheduler.runTask. Bukkit/Domain/MessageService erst im Hauptthread-Runnable. |
| 40 | Permission recheck | Aktuelle spezifische Moderationspermission nach Load erneut geprüft, bevor irgendeine Domain-Aktion ausgeführt wird. |
| 41 | Actor logout recheck | Originaler Player muss online und identisch mit aktuellem Online-Lookup sein; Logout/Reconnect verwirft. Disable/Re-enable invalidiert alte Generation einschließlich bereits queued Runnable. |
| 42 | Config snapshot consistency | Pro Entscheidung genau ein immutable Snapshot für beide Levels; Actor-Gruppe spät gelesen, Zielgruppe Snapshot des Load-Ergebnisses. |
| 43 | Warn protection | Zentrale Hierarchie vor issueWarning und Notification. |
| 44 | Mute protection | Zentrale Hierarchie vor issueMute, Projektion, Audit und Notice. |
| 45 | Unmute protection | Zentrale Hierarchie vor revokeMute und Communication-Freigabe/Notice. |
| 46 | Ban protection | Zentrale Hierarchie vor issueBan und Disconnect. |
| 47 | Unban protection | Zentrale Hierarchie vor revokeBan, auch bekannte Offline-Ziele. |
| 48 | Kick protection | Zentrale Hierarchie vor Online-Check/recordKick/Disconnect; bekannte Offline-Spieler bleiben nicht kickbar. |
| 49 | History protection | Fremde geschützte History verlangt strikt höheren Actor; read-only API und Paging bleiben unverändert. |
| 50 | Self-history | Mit History-Permission erlaubt, auch ohne LP-Gruppe; kein Hierarchie-Load. |
| 51 | Self-mutation | Warn/Mute/Unmute/Ban/Unban/Kick bleiben vor Hierarchie verboten; kein Save oder Load. |
| 52 | Completion filtering | Synchron verfügbare Online-Gruppen werden geschützt gefiltert; equal/higher/unprotected Actor/unknown group verborgen. Console und Self-History berücksichtigt. |
| 53 | Offline completion boundary | Aktive committed Offline-Unban/Unmute-Kandidaten bleiben sichtbar. Kein N+1-LP-Load; Ausführung bleibt autoritativ. |
| 54 | Hierarchy harness | StaffHierarchyServiceHarness: 109 Checks, Default-/Custom-/Normalisierung-/Immutability-/Matrix-/Unknown-/Reload-/Snapshot-/Source-Grenzen. |
| 55 | LP async harness | LuckPermsAsyncHarness: 16 Checks, loaded/pending/worker/null/blank/failure/UUID/read-only API; Proxy weist andere LP-User-/Manager-APIs zurück. |
| 56 | Moderation command hierarchy tests | ModerationCommandHarness: 3.381 Checks; vollständige 7×7×7-Actor/Target/Command-Matrix plus Console/Self, Async-Szenarien und Completion. |
| 57 | Config tests | ConfigServiceHarness 74, DefaultConsistencyHarness 12; missing/custom/invalid/null/empty/case/dup/prepare/apply/rollback, Datei unverändert. |
| 58 | Rank regressions | RankServiceHarness 23, RankCommandHarness 16, RanksCommandHarness 12; Presentation bleibt unverändert. |
| 59 | Phase25B regression | MuteProjection 20, MuteEnforcement 52, Command/Mute-Fälle und PM 117 bestanden; keine Produktionsänderung an Enforcement. |
| 60 | Phase25A regression | BanEnforcement 31, Duration 53, bestehende Warn/Ban/Unban/Kick/History- und Literal-/Save-Failure-Fälle bestanden. |
| 61 | Phase24 regression | Domain 112, Persistence 523, Service 141, Lifecycle 149; Storage/Records/Schema unverändert. |
| 62 | Chat regression | ChatHarness 17; Chat-Produktionscode unverändert. |
| 63 | PM regression | PrivateMessageSocialHarness 117; sender-only Mute, Ignore/Settings und Conversation-State unverändert. |
| 64 | Friend regression | Alle Friend-Domain/Service/Persistence/Integration/Lifecycle/Command/Menu/Security/Visibility-Harnesses bestanden. |
| 65 | Clan regression | Alle Clan-Domain/Service/Persistence/Integration/Command/Menu/Security-Harnesses bestanden. |
| 66 | Economy regression | CoinsCommand 208, EconomyIntegration 24, EconomyService 44 bestanden; keine 26A-Target-Integration. |
| 67 | Utility regression | UtilityCommand 152, UtilityInventory 36, UtilityService 35 und BuildCommand 17 bestanden; unveränderte Produktionspfade. |
| 68 | Settings/visibility | Sämtliche Settings-, Menu-, Navigation-, Player-Visibility- und Visibility-Harnesses bestanden. |
| 69 | Teleport | TeleportCommand 62 und TeleportParser 58 bestanden; keine neue Hierarchieprüfung in 26A. |
| 70 | Reward/quest | Alle Reward/OnlineReward/Quest/DailyQuest-/Player-Persistence-Harnesses bestanden. |
| 71 | Blackjack | Fairness 393, Domain 218, Presentation 43, Preview 16, Table 90 bestanden. |
| 72 | Remaining regression | Activity, Core/Help, Identity/Profile, Lobby/Warp, Playtime/Presentation, Seat/WorldDisplay und übrige Harnesses bestanden; vollständige Ergebnisse unten. |
| 73 | Total harnesses | 88 separat gestartete Java-Prozesse (86 Baseline + zwei neue Main-Harnesses), alle final erfolgreich. |
| 74 | Total checks | 8.620; Baseline 5.977, Zuwachs 2.643 tatsächliche Checks. |
| 75 | Failures | Final 0. Ein vorläufiger Source-Check verwechselte String.join(...) mit Future.join(); Prüfbedingung korrigiert, vollständige Regression erneut ausgeführt. |
| 76 | Maven build | build-and-deploy.ps1 führte mvn clean package erfolgreich aus (22:13:48 CEST). Nach ausschließlich Test-Assertion-Korrektur test-compile erneut erfolgreich (22:17:06), Produktionsklassen unverändert. |
| 77 | JAR bytes | 993.132 Bytes, target/vapeecore-1.0-SNAPSHOT.jar; deployed Datei identisch. |
| 78 | SHA-256 | 42D38C0355822AD0CB4A1AFE928A16380EC6156A4AA2C291E9445F12951653F9 für Build- und Dev-Server-JAR. |
| 79 | Paper | 1.21.11-132 / c5eb079, bestehende lokale Testinstallation. |
| 80 | Java | 21.0.12.1+1-LTS-4 (Oracle), Windows 11 amd64. |
| 81 | LuckPerms | 5.5.84, connected; read-only lp listgroups zeigte sieben vorhandene Gruppen. Keine Gruppen-/User-Mutation. |
| 82 | Paper smoke | 30.09.2026 22:14–22:16 CEST: Start, core/version/help, alle sieben Roots und Namespaces ohne Args, bekannte read-only History, PM Player-only, 36 Roots, Vanilla-Help, lp listgroups, list, regulärer Stop. |
| 83 | Reload smoke | core reload erfolgreich in 14 ms, genau sechs vorbereitete Teilnehmer. Bestehende Legacy-Config ohne staff-Key lädt sichere Defaults; kein Config-Warning, keine Dateiüberschreibung. |
| 84 | Live client status | Live client test: not performed. Async-Offline-/Command-Autorisierung ist Harness-getestet, kein Live-Spieler-/Bukkit-Client-Sicherheitsversuch. |
| 85 | README | Aktueller 26A-Stand, LOW→HIGH, Offline-Thread-Grenze, Reload, Permission/OP/Self/Console und 26B-Split ergänzt. |
| 86 | Developer Guide | Ownership, Supplier-Snapshot, LP-API, Hauptthread-Fortsetzung, Lifecycle-Invaliderung, Completion und Domain-/Schema-Grenzen aktualisiert. |
| 87 | Permissions | Kanonische Moderations-Hierarchie und konservative Parent-Matrix dokumentiert; alte aktuelle „keine Hierarchie“-Aussagen entfernt. |
| 88 | New report | Dieser vollständige STAFF_HIERARCHY.md-Bericht mit allen 103 angeforderten Punkten, Inventar und individuellen Ergebnissen. |
| 89 | Historical docs | COMMAND_AUDIT.md, MODERATION_FOUNDATION.md, MODERATION_TOOLS.md und MODERATION_MUTES.md unverändert. |
| 90 | FORMATTING | Unverändert; keine Player-facing MiniMessage-/Presentation-Template-Änderungen. |
| 91 | plugin.yml status | Unverändert einschließlich sieben unabhängiger Moderations-OP-Nodes und bestehender Children. |
| 92 | No new permissions | Keine Hierarchie-/Bypass-/Wildcard-Permissions oder Änderungen am Permission-Tree. |
| 93 | No new commands | Keine neuen Root-Commands, Aliases oder Dispatcher. |
| 94 | No schema migration | moderation.yml Schema 1 und Player-YAML unverändert; historische Sanktionen bleiben bestehen. |
| 95 | No automatic LP group mutations | Kein saveUser, Parent-/Group-/Track-/Permission-Write oder automatische Setup-Migration. |
| 96 | No weight hierarchy | Explizite Config-Reihenfolge, nicht LP-Weight; LP-Service und Presentation-Metadaten bleiben ansonsten kompatibel. |
| 97 | No prefix hierarchy | Keine Prefix/Suffix/Text-/Farbinterpretation als Schutzlevel; literal Feedback ohne Gruppenleak. |
| 98 | No OP bypass | OP verleiht keine Ausnahme von same/higher; nur Actor-Typ Console umgeht die Hierarchie. |
| 99 | No utility integration yet | Utility/Teleport/Inventory-Produktionsdateien unverändert; diese Umsetzung ist ausschließlich 26B. |
| 100 | No economy integration yet | Economy-Produktionsdateien unverändert; Target-Hardening ausschließlich 26B. |
| 101 | Phase26B readiness | Separater Rank-owned read-only Service und immutable Config stehen für gezielte Utility/Economy-Adapter bereit; keine Vorwegnahme. |
| 102 | Known issues | Keine offenen 26A-Testfehler. Live-Client nicht durchgeführt. Paper meldet neuere Minecraft-Releases; spark verwendet unter Windows den Java-Fallback. Unveränderte javac-Annotation-/Deprecated-API-Hinweise; kein ERROR/SEVERE im Paper-Smoke. |
| 103 | Merge readiness | 26A isoliert mergebereit nach vollständiger Regression und Paper-Smoke; ein lokaler Commit mit vereinbartem Titel, kein Push. 26B bleibt nächste getrennte Phase. |

## Vollständige Einzel-Harness-Ergebnisse

Jeder unten genannte Main-Harness wurde separat als eigener Java-21-Prozess ausgeführt; jeweils Exit-Code 0. Das Maven-Package allein ersetzt diese Ausführung nicht. Der finale Lauf enthält genau 88 Erfolge und summiert exakt 8.620 Checks. Ergebnisse: `target/phase26a-final-harness-results.txt` (generiertes, nicht versioniertes Prüfprotokoll).

| Harness | Checks | Ergebnis |
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
| ConfigServiceHarness | 74 | PASS |
| DefaultConsistencyHarness | 12 | PASS |
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
| ModerationCommandHarness | 3381 | PASS |
| ModerationDurationHarness | 53 | PASS |
| ModerationBanEnforcementHarness | 31 | PASS |
| ModerationDomainHarness | 112 | PASS |
| ModerationLifecycleHarness | 149 | PASS |
| ModerationMuteEnforcementHarness | 52 | PASS |
| ModerationMuteProjectionHarness | 20 | PASS |
| ModerationPersistenceHarness | 523 | PASS |
| ModerationServiceHarness | 141 | PASS |
| OnlineRewardLifecycleHarness | 42 | PASS |
| OnlineRewardServiceHarness | 42 | PASS |
| LuckPermsAsyncHarness | 16 | PASS |
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
| StaffHierarchyServiceHarness | 109 | PASS |
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
| **Gesamt (88 Einzelprozesse)** | **8620** | **0 Fehler** |

Die erweiterten Command-Tests decken eine vollständige 7×7×7-Matrix (Command × Actor-Gruppe × Target-Gruppe) ab. Zusätzliche Async-Fälle für jeden Command: normal/lower/equal/higher, VIP, Permission-Entzug, Logout/Reconnect, Actor-Demotion/Promotion, LP-Failure/empty/invalid, Disable und Configwechsel während Load. Worker-Thread-Abschluss speichert/sendet/kickt nichts; erst die explizit geleerte Main-Queue führt erlaubte Arbeit aus. Bukkit-Proxy-Access und Repository-Save werden auf den ursprünglichen Thread geprüft. Bekannte echte Offline-Fixtures, geladene ungültige Gruppen und sämtliche Completion-Regeln sind enthalten. Lifecycle-Harness prüft sowohl pending als auch bereits queued Antworten über Disable/Re-enable.

Ein vorläufiger Source-Assertion-Lauf hatte einen falsch positiven Fehler, weil `String.join(...)` für Gründe mit einer Future-Warteoperation verwechselt wurde. Nur die Assertion wurde korrigiert; danach Test-Compile und sämtliche 88 Harnesses vollständig erneut ausgeführt. Kein offener Fehler und keine Produktionsänderung durch diese Korrektur.

## Build, Deployment und Paper-Smoke

Build: `scripts/build-and-deploy.ps1` → `mvn clean package` → **BUILD SUCCESS** (30.09.2026, 22:13:48 CEST). Nach der ausschließlich Test-Assertion betreffenden Korrektur: `mvn -o test-compile` → **BUILD SUCCESS** (22:17:06); Maven bestätigt unveränderte Produktionsklassen. Der bereits gebaute und Paper-getestete Produktions-JAR bleibt byte-identisch.

- Build-JAR: `target/vapeecore-1.0-SNAPSHOT.jar`
- Deployment: `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar`
- Beide: **993132 Bytes**
- Beide SHA-256: `42D38C0355822AD0CB4A1AFE928A16380EC6156A4AA2C291E9445F12951653F9`

Tatsächlich ausgeführt: Paper 1.21.11-132/c5eb079, Java 21.0.12.1, LuckPerms 5.5.84; Start 22:14:30, ready 22:14:47, Reload 22:15:45, kontrollierter Stop 22:16:52/53. Keine künstlichen Sanktionen.

- 26 reale Modul-Enable- und 26 rückwärts geordnete Disable-Zeilen.
- `core`, `core version`, `core help`: laufendes Plugin, 26 Module, LP connected.
- `core reload`: erfolgreich in 14 ms; genau sechs Prepared-Konfigurationen einschließlich config.yml.
- Alle sieben Moderations-Roots und `vapeecore:`-Namespaces ohne Args: kontrollierte VapeeCore-Syntaxhinweise.
- `history rxaq`: bekannte reale Offline-Identität, keine Moderationshistorie, read-only.
- `msg`, `reply`, `r` über Console: unveränderte Player-only-Grenze.
- `bukkit:help VapeeCore`: 36 tatsächlich registrierte Roots.
- Vanilla-`minecraft:ban`/`minecraft:kick`-Help weiterhin Mojang-Commands.
- `lp listgroups`: sieben vorhandene Gruppen (owner, admin, moderator, builder, vip, default, supporter), nur gelesen; deren vorhandene Weights/Tracks haben keine Hierarchiewirkung.
- `list`: 0 Online-Spieler. `stop`: Exit-Code 0, managed PID-Datei entfernt.
- Finale aktuelle Paper-Logprüfung: **0 ERROR/SEVERE**, **0 Config-Warnings**. Fehlende `moderation.yml` bleibt nach Start/Reload/Stop fehlend.
- Die vorhandene Dev-Config enthält noch keinen staff-Key; ihre unveränderte Legacy-Konfiguration lädt den sicheren Default über den neuen ConfigService. Custom-Reihenfolge/Invalid-Fallback/Apply/Rollback sind zusätzlich im Harness verifiziert, nicht als Live-Spieler-Test behauptet.
- Bestehende Umgebungshinweise: Paper meldet neuere Minecraft-Releases; spark verwendet unter Windows den Java-Profiler-Fallback. Vorbestehende javac-Annotation-/Deprecated-API-Hinweise; keine neuen Build-Fehler.

**Live client test: not performed.**

## Phase 26B und Betreiberverantwortung

Phase 26B kann den vorhandenen Rank-owned Service gezielt für Utility-/Teleport-/Inventory- sowie Economy-Target-Adapter verwenden. Diese Integration, OP-/Children-Aufräumen und finaler Permission-/Inheritance-Audit sind ausdrücklich noch nicht Bestandteil von 26A.

Empfohlene LP-Parents: vip→default, builder→default, moderator→default, admin→moderator, owner→admin. VIP erbt keine Staff-Rolle. Permission-Vererbung ist unabhängig vom expliziten LOW→HIGH-Target-Schutz; Betreiber müssen die Primary-Group-Quelle und ihre Schutzliste konsistent konfigurieren. VapeeCore führt keine LP-Gruppen-, Parent-, Track-, Permission- oder User-Mutation aus.

## Lokaler Git-Abschluss

Genau ein lokaler Commit mit Titel `Phase 26A - Staff Hierarchy and Moderation Protection`; nicht pushen. Final werden leerer `git status --short`, `git log -1 --oneline`, genau ein Commit seit Baseline und das vollständige `git diff f13d55643aae47024d31e83c948b17d240688d99..HEAD --name-only`-Inventar geprüft. Commit-ID wird in der abschließenden Rückmeldung angegeben.
