# Prototype Step 01B – Social Chat Styling and Finalization

Branch: `prototype/friend-presence-chat-actions`.
Unveränderte Vorgänger: `b66eaabe1b172311115a7087eed0eb4027d8f9d3` und `b0b00c2819f335ba61bc8551c4b5fea9e407209e`.
Diese Finalisierung übernimmt den zuvor uncommitteten Join-Summary-Diff und ergänzt Kopfprofile sowie den bestehenden Benachrichtigungs-Opt-out. Genau ein zusätzlicher lokaler Commit ist vorgesehen: `Finalize friend join summary and player head rendering`. Seine vollständige SHA wird im Abschluss und externen Schutzmanifest festgehalten; keine Änderung der Vorgänger, kein Push, Merge oder Deployment.

## Unveränderter Styling-Vertrag

Friends: Englisch `[Friends]`, Deutsch `[Freunde]`, Adventure Gold. Clan: `[Clan]` in beiden Sprachen, Dark Purple. Body Grau, kurze rote Fehlerlabels, grüne Accept-/rote Deny-Buttons. Spielername: tatsächliche UUID → aktueller geladener LuckPerms-Primary-Rank → `vapeecore.rank.color` → `RankInfo.effectiveColor()`, einschließlich Named/RGB und weißem Fallback. Keine Rank-Mutation, Namens-/LP-Präfixinterpretation als MiniMessage oder synchroner LP-Load.

Gewöhnliche Friend-/Clan-/Presence-Nachrichten behalten ihren lokalisierten Kanalpräfix und den konfigurierten globalen Präfix davor. Accept/Deny bleiben RUN_COMMAND mit kanonischen Spieler-/stabilen Clan-UUIDs; Cancel/Remove, destruktive Bestätigungen und editierbare Beispiele behalten ihren bisherigen Vertrag. 28 Module, sieben Reload-Teilnehmer, Domain-Services, Commands und Permissions bleiben unverändert.

## Echte Köpfe in den beiden Menüs

`ui.CachedPlayerHeads` dekoriert frisch gerenderte Einträge in FriendMenu FRIENDS/INCOMING/OUTGOING und VisiblePlayersMenu für manuell hinzugefügte Spieler. Die UUID kommt direkt aus dem Entry und entspricht dem bestehenden Holder-Target. Sichtbare Namen, Lore, Lokalisierung, Farben, Pagination und aktive Inventar-/Holder-Identität werden nicht geändert. UiItemSpec bleibt das bestehende Material-/Name-/Lore-Record.

1. Für einen passenden Online-Spieler wird sein vorhandenes Paper-Profil geklont.
2. Andernfalls wird über `Server.createProfile(UUID)` und ausschließlich `completeFromCache()` vorhandene Paper-Cacheinformation gelesen.
3. Nur identische Profil-UUID plus tatsächlich vorhandene Skin-URL werden auf SkullMeta gesetzt. Cache-/Profilfehler ergeben einen normalen Kopf.
4. Jeder Eintrag enthält außerdem die Ziel-UUID im transienten Item-PDC `vapeecore:head-player`. Dieses Tag ist reine Darstellung und niemals Klickautorisierung; Owner, Target und Rechte bleiben Holder-/Menüentscheidungen.

Es gibt kein `complete()`, `update()`, Netzwerk-Fetch, eigenes Cache-Repository, Task oder Callback des Features. Profilkopien schützen die ursprünglichen Online-/Cacheprofile. Die funktionale Dekorations-Seam erhält vorhandene Testkonstruktoren; die öffentlichen Produktionskonstruktoren verdrahten den echten Server.

Offline-Skins funktionieren nur, wenn die Texturen schon in Papers Laufzeitcache verfügbar sind. Nicht jeder jemals bekannte Offline-Spieler hat dort noch eine Skin. Dann bleibt der Kopf ohne Skull-Owner; UUID-Tag und Holder-Target bleiben gesetzt. Bewusst kein texturloser Owner: Paper 1.21.11 startet dafür beim Anwenden des ItemMeta selbst ein asynchrones Profilupdate. Ohne angehängtes texturloses Profil entstehen auch keine verspäteten Ergebnisse für geschlossene, ersetzte oder veraltete Inventare. Später verfügbare Skins erscheinen beim nächsten normalen Refresh/Reopen. Es werden keine Freundes-, Sichtbarkeits- oder Playerdateien dafür geändert.

API-/Implementierungsgrundlagen: [Paper PlayerProfile](https://jd.papermc.io/paper/1.21.11/com/destroystokyo/paper/profile/PlayerProfile.html), [Paper Server](https://jd.papermc.io/paper/1.21.11/org/bukkit/Server.html), [CraftMetaSkull 1.21.11](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaSkull.java). Das native Testfixture darf Papers vorhandenen Cache mit synthetischen Profilen befüllen; Produktionscode enthält keinen NMS-Zugriff.

## Join Summary und Benachrichtigungseinstellung

Eine private Chatzeile beim eigenen echten, geladenen Join; weder Friends-/Freunde-Präfix noch globaler Server-/Operator-Präfix oder Dekoration. `MessageService.sendRaw()` behält die Empfängerlokalisierung. Grau für Body, Grün für Onlinezahl, Weiß für Gesamtzahl.

| Online | Deutsch | Englisch |
|---|---|---|
| 3/12 | Aktuell sind 3 von 12 Freunden online. | Currently, 3 of your 12 friends are online. |
| 1/12 | Aktuell ist 1 von 12 Freunden online. | Currently, 1 of your 12 friends is online. |
| 0, auch ohne Freunde | Momentan ist keiner deiner Freunde online. | None of your friends are currently online. |

Keys: `friends.join-summary.none/one/many`. FriendJoinSummary liest jetzt denselben geladenen `PlayerSettingsService.areFriendPresenceNotificationsEnabled`-Wert wie die Statusmeldungen: deaktiviert oder nicht geladen → keine Zusammenfassung. Beim nächsten echten Join wird die aktuelle Einstellung neu gelesen; kein neuer persistenter Setting-Key. Die frühere uncommittete Variante war vom Opt-out unabhängig; die Finalisierung ersetzt ausschließlich diese Präferenzentscheidung.

Bestehende Ignore-Prüfungen in beiden Richtungen bleiben bestehen; die Gesamtzahl ist die tatsächliche Freundeszahl. Duplicate Join, nicht geladenes Profil, Quit und Enable-Seeding erzeugen keine weitere Zusammenfassung. Status- und Summary-Sendefehler sind voneinander isoliert. Kein zusätzlicher Scheduler, Listener oder Onlinecache; Friend-/Presence-/Settings-Services bleiben an dieselbe Modulruntime gebunden.

## Verifikation

Frische Baseline vor Änderungen: vollständiges `scripts/verify.ps1`, 209/209 Harnesses, 33.587 Checks, Failed=0, Missing/Invalid=0, Maven und Spotless PASS. Nur die genehmigte Run-Änderung und der vorher vorhandene Join-Summary-Diff lagen vor; beide wurden außerhalb des Repositories gesichert.

Erfolgreiche Harnesses: CachedPlayerHeads (16), FriendHeadRendering (18), VisiblePlayerHeads (14), FriendJoinSummary (68), PresenceLifecycle (42), PresenceDependencyBoundary (308), FriendCommand (133), ClanCommand (130), SocialChatStyling (49). Die Kopfprüfungen kontrollieren Profile/UUID/Skin, Metadaten/Lore und Cachezugriffe, nicht nur Materialtypen. Gegenproben verbieten Netzwerk-/Update-Aufrufe; manipulierte Item-UUID-Tags ändern das echte Holder-Target nicht. Stale Page, geschlossene/ersetzte Inventare, Reopen/Refresh, fremde Viewer und Permissions sind abgedeckt. Die 209 vorher vorhandenen Harnesses sind vollständig erhalten.

Finale kanonische Verifikation: `scripts/verify.ps1` vollständig erfolgreich, Discovered=Executed=Passed=212, Checks=33.644, Failed=0, Missing/Invalid=0. Maven `clean package`, Spotless und `git diff --check` PASS. Drei neue Harnesses ergänzen 48 Kopf-Verhaltenschecks; die Summary erhält vier zusätzliche Checks. Fünf weitere Checks entstehen aus den bestehenden dynamischen Permission-/GUI-Quellinventaren.

Native Acceptance: Paper 1.21.11 Build 132 + LuckPerms 5.5.84 in einer neu angelegten separaten Instanz, 201 Checks (192 + 9) in zwei getrennten JVMs inklusive Kaltstart. Beide Exit 0, keine ERROR/SEVERE, normaler Root-Shutdown. Echte Paper-Inventare, CraftItemStacks, SkullMeta und Profile mit synthetischen Textureproperties; kontrollierte Player-/Inventar-Transporte und Testseiten ersetzen Netzwerk-Clients. Geprüft: Onlinequelle, tatsächlich befüllter Paper-Offlinecache, UUID-Identität, ownerloser Fallback, alle drei Friend-Ansichten, hinzugefügte sichtbare Spieler, Name/Lore-Farben, unveränderte Freundes-/Visibility-Dateibytes, Holderidentität und geschlossene/ersetzte Inventare nach acht weiteren Ticks und Cacheänderungen. Join-Summary-Texte/-Farben und Opt-out, bestehende Rang-/RUN_COMMAND-/Privacy-/Cold-Persistenz-Prüfungen bleiben enthalten.

Getesteter JAR-SHA-256: `6D8D9C7B3866DC8D40ABBF6FFBE59515333FE9274ADA5DF9CB834B1DE1FD0614`, bytegleich zum finalen Maven-Artefakt. JAR, Helper, Logs, Receipts und synthetische persistierte Profile sind extern unter `native-finalization/` gesichert. Erst danach und nach bestätigtem Prozessende wurde ausschließlich das separate Testserver-Verzeichnis entfernt. API-/Transporttests sind keine visuelle Minecraft-Client-Bestätigung.

## Schutz und Nachweise

Die vorhandene lokale Änderung `.run/Start VapeeCore Dev Server.run.xml` wird vollständig erhalten, niemals bearbeitet, gestagt, committet oder bereinigt. Ausgangs-SHA-256: `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Vollständiger Git-Diff und Hash werden vor/nach der Finalisierung und nach dem Commit verglichen. Nur die unten aufgeführten Pfade werden explizit gestagt. Keine pauschalen Add-, Reset- oder Clean-Befehle.

Der installierte Operator-Server wurde nicht gestartet, gestoppt oder beschrieben. Er läuft und kann seinen Bestand selbst ändern. Lesender Vorher-/Nachher-Vergleich: jeweils 917 Dateien, jeweils 15 gesperrt, keine neuen/entfernten Pfade. 902 Dateien waren in beiden Inventaren hashbar: 889 identisch, 13 extern verändert (ein aktives Plugin-Playerprofil sowie Welt-/Spielerstände). Es wird ausdrücklich kein unveränderter Gesamtbestand behauptet. Alle 26 Run-/Scriptdateien sind zum Startinventar bytegleich. Weitere unerwartete lokale Quelldateien wurden nicht festgestellt oder verändert. Gesicherte bestehende Join-Diffs, Datei-/Run-Inventare, Testlogs, native Receipts und der Abschlussvergleich liegen unter `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/prototype-step01b-finalization-20261028/`.

## Exakte Commit-Pfade

Production:

- `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java`
- `src/main/java/dev/vapee/core/ui/CachedPlayerHeads.java` (neu)
- `src/main/java/dev/vapee/core/presence/FriendJoinSummary.java` (vorher uncommittet)
- `src/main/java/dev/vapee/core/presence/PresenceListener.java` (vorheriger Join-Diff)
- `src/main/java/dev/vapee/core/presence/PresenceModule.java`

Tests:

- `src/test/java/dev/vapee/core/ui/HeadTestSupport.java` (neu)
- `src/test/java/dev/vapee/core/ui/CachedPlayerHeadsHarness.java` (neu)
- `src/test/java/dev/vapee/core/friend/gui/FriendHeadRenderingHarness.java` (neu)
- `src/test/java/dev/vapee/core/friend/gui/FriendMenuFixture.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisiblePlayerHeadsHarness.java` (neu)
- `src/test/java/dev/vapee/core/settings/visibility/VisibilityMenuFixture.java`
- `src/test/java/dev/vapee/core/presence/FriendJoinSummaryHarness.java` (vorher uncommittet)
- `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java`
- `src/test/java/dev/vapee/core/presence/PresenceDependencyBoundaryHarness.java`

Resources und Dokumentation:

- `src/main/resources/lang/en.yml`
- `src/main/resources/lang/de.yml`
- `docs/SERVICE_DEPENDENCIES.md`
- `reports/prototype/STEP_01B_SOCIAL_CHAT_STYLING.md`

Die bestehende Run-Änderung gehört nicht zum Commit. Weitere unerwartete lokale Änderungen werden nicht verändert.

## Noch offene Minecraft-Client-Prüfung

- Friend-Liste, eingehende/ausgehende Anfragen und hinzugefügte sichtbare Spieler mit zwei realen verschiedenen Skins prüfen; Offline-Cache und normalen Fallback beachten.
- Kopftext, Lore, Rangfarben, Sprache, Seitenwechsel, Close/Reopen und Klickrechte im normalen Client prüfen.
- Eigener Join auf DE/EN mit 3/12, 1/12 und 0 Onlinefreunden: exakt eine präfixfreie Zeile und die angegebenen Farben; mit deaktivierter Benachrichtigung gar keine Zeile.
- Fremder Freund-Join/Quit und Anfragen behalten Friends/Freunde; Friend-/Clan-Accept/Deny ohne Enter, stale/copied Actions und nächste Nachricht nach Rangänderung prüfen.
- Lesbarkeit und tatsächliche Skin-Darstellung ohne Resource-Pack/Mods bestätigen. Erst danach Merge-Entscheidung; dieser Auftrag führt keinen Merge aus.
