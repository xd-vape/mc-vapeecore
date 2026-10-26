# Module entwickeln und sicher betreiben

Diese Anleitung beschreibt das vorhandene explizite Modulmodell und die abgesicherten Friend- und Clan-Beispiele. Sie behauptet nicht, dass alle älteren Module bereits jeden späten Fehler behandeln. Einstieg: [Architektur](ARCHITECTURE.md), [Code-Konventionen](CODE_STYLE.md), [Validierung](../README.md#entwicklung).

## Wann ein Modul sinnvoll ist

Ein `CoreModule` besitzt die Lifecycle-Integration eines Features: Services erzeugen, Commands binden, Listener/Observer installieren und aktive Views oder Tasks freigeben. Ein Algorithmus, zusätzlicher Command-Zweig oder weiterer Anwendungsfall eines bestehenden Services benötigt gewöhnlich kein weiteres Modul. Keine gemeinsame Basisklasse, Scanner oder Service-Locator einführen.

`CoreModule` hat nur `getName()`, `enable()` und `disable()`. `ModuleManager` startet in Registrierungsreihenfolge und beendet erfolgreiche Module rückwärts. Er nimmt ein Modul **erst nach zurückgekehrtem `enable()`** in seine gestartete Liste auf. Das fehlgeschlagene Modul muss seinen Teilstart selbst zurücknehmen; der Manager errät dessen erworbene Ressourcen nicht.

## Dependencies im Composition Root verdrahten

Für direkte Services, typisierte Provider, vorhandene fachliche Capabilities und Callback-Grenzen siehe [SERVICE_DEPENDENCIES.md](SERVICE_DEPENDENCIES.md). Presence zeigt den späten Erwerb mit vier typisierten Providern und weiterhin kompatiblem Modul-Konstruktor.

`VapeeCore.onEnable()` konstruiert und registriert die Module ausdrücklich. Konstruktoren speichern stabile Abhängigkeiten; fremde Runtime-Services werden während `enable()` bezogen. Deren Module müssen vorher starten und bleiben dadurch bis nach dem Consumer-Disable verfügbar. Die konkrete Produktionsliste ist maßgeblich, kein automatisch berechneter Dependency-Graph.

Der Friend-Konstruktor bleibt unverändert:

```java
friendModule = new FriendModule(this, configService, playerModule, socialModule,
        identityModule, messageService, commandHelpRenderer);
```

Friend startet nach Localization, Player, Social und Identity; Presence und andere Friend-Konsumenten danach. Neue Zusammenarbeit soll möglichst die kleinste vorhandene fachliche Schnittstelle verwenden. Ganze Module nicht nur aus Bequemlichkeit weiterreichen. Eine Reduktion bestehender Konstruktorabhängigkeiten muss auch Erwerbszeitpunkt und Retry berücksichtigen; kosmetische Signaturänderungen helfen nicht.

### Vier Dependency-Ebenen des Friend-Piloten

| Ebene | Tatsächlicher Inhalt |
|---|---|
| Construction | JavaPlugin, ConfigService, PlayerModule, SocialModule, IdentityModule, MessageService, CommandHelpRenderer |
| Runtime-Erwerb | PlayerSettingsService, SocialService, PlayerIdentityService; Server, Logger, Command-Descriptor und Datenpfad aus dem Plugin |
| Fachlicher Betrieb | Live-Limits über `configService::getFriendLimits`; FriendRequestPolicy konsultiert Ignore-Zustand und Friend-Request-Privacy; Identitäten für Command-Ziele und GUI-Namen; MessageService/Localization für empfängerbezogene Texte |
| Lifecycle | Listener-Registrierung, Executor-/Completer-Bindung, zwei Menü-Observer, aktive Inventory-Bindungen und eigene Runtime-Referenzen |

Die bestehende `FriendRequestPolicy` bleibt die schmale Autorisierungsgrenze zu Social/Settings. Weder Menü noch Modul führen eine zweite Friendship-/Privacy-Wahrheit ein.

## Ownership: Referenz ist nicht Eigentum

| Ressource | Erzeugt / besitzt | Erwerb und Nutzung | Freigabe |
|---|---|---|---|
| ConfigService, MessageService, HelpRenderer | Composition Root; LocalizationModule betreibt Lokalisierung | Konstruktorreferenzen; aktiver Feature-Betrieb | Root beziehungsweise zuständiger Modul-Owner |
| PlayerSettingsService | PlayerModule | Friend erwirbt während Enable | PlayerModule; Friend deaktiviert ihn nicht |
| SocialService | SocialModule | Friend erwirbt während Enable | SocialModule |
| PlayerIdentityService | IdentityModule | Friend erwirbt während Enable | IdentityModule |
| FriendService | FriendModule | Lokal konstruieren; öffentliche Getter erst nach erfolgreichem Start | FriendModule entfernt Referenzen; keine Shutdown-Neuschreibung |
| FileFriendRepository, Policy, Clock und Domain-Snapshots | Friend-Runtime, beim FriendService gehalten | Initiales Laden / fachlicher Commit | Runtime-Lebensdauer; dauerhaftes YAML bleibt bestehen |
| FriendMenu, FriendMenuListener, FriendCommand und GUI-Feedback | FriendModule | Lokal konstruieren, anschließend registrieren/binden | Menü herunterfahren, Handler/Bindings entfernen, Runtime freigeben |
| PluginCommand-Descriptor | Bukkit | `plugin.getCommand("friend")` | Descriptor bleibt; Friend entfernt nur seine Executor-/Completer-Bindungen |
| Locale-/FriendChange-Subscription | FriendMenu | Innerhalb geschütztem Module-Enable | Dasselbe Menü meldet seine Callback-Instanzen ab |
| Aktive Inventare, Target-Mappings, Render-Zwischenzustand | FriendMenu / exakt gebundener Holder | Autorisierte aktuelle Views | Owned Views schließen/detachen; Registry und Zwischenzustand leeren |
| `friends.yml` | FriendRepository / FriendService als Schreibgrenze | Laden beim Service-Neustart; Schreiben bei erfolgreichen Mutationen | Nicht beim Disable löschen, leeren oder neu schreiben |

Ein Consumer darf keine alte FriendService-Referenz über Disable/Re-enable hinweg behalten. Nach Neustart die aktuelle Runtime beziehen. ModuleManager sichert die geordnete Lebensdauer der Produktions-Konsumenten; beliebige externe Java-Referenzen werden nicht widerrufen.

## Enable: lokal erwerben, gemeinsam schützen, spät veröffentlichen

FriendModule führt Dependency-Erwerb und Runtime-Konstruktion innerhalb derselben Fehlergrenze wie diese beobachtbaren Schritte aus:

1. Echten FriendMenuListener registrieren.
2. FriendCommand als Executor und TabCompleter binden.
3. Beide Menü-Observer über `startUpdates()` anmelden.
4. Runtime-Referenzen veröffentlichen.
5. Startup-Logging abschließen und den öffentlichen Runtime-Zugriff freigeben.

Die Getter schlagen bis zum erfolgreichen Abschluss fehl. Ein Guard weist doppeltes oder reentrantes Enable zurück, **bevor** Ressourcen erworben oder gültige Bindings verändert werden. Dessen Fehler darf den schon laufenden Modulzustand nicht aufräumen.

Bei einem `RuntimeException`-Fehler verwendet Friend denselben privaten `cleanup(...)`-Pfad wie Disable, gibt veröffentlichte Referenzen frei und wirft die ursprüngliche Exception erneut. Keine zusätzliche Lifecycle-Transaktion einführen. Die package-lokalen `EnableStage`-Checkpoints liegen nach echten Operationen; der öffentliche Konstruktor verwendet einen No-op.

## Cleanup und Observer

Zuerst läuft Menü-Shutdown, solange Close-Handler noch registriert sind. Danach entfernt FriendModule seinen Listener, setzt den Executor auf `null` und unabhängig davon den Completer auf `null`. Bukkit stellt beim Executor den Plugin-Fallback her; der Descriptor bleibt registriert. Abschließend werden Runtime-Referenzen freigegeben.

Jeder Freigabeschritt hat eine eigene Fehlergrenze. Beim fehlgeschlagenen Enable bleibt die Startup-Exception primär; weitere Fehler hängen mit Ressourcenkontext als suppressed Exceptions daran. Normales Disable wirft einen gesammelten Cleanup-Fehler an ModuleManager, der protokolliert und mit anderen Modulen fortfährt. Erfolgreiches Shutdown ist still; wiederholtes Disable hat keine weiteren Ressourceneffekte.

`FriendMenu.startUpdates()` verwendet die bestehenden `addLocaleListener(localeChanges)`- und `addChangeListener(committedChanges)`-APIs. Ein Boolean beschreibt den erfolgreichen Start, beweist aber bei einer Exception nicht, dass keine Registrierung stattfand. Deshalb versucht `stopUpdates()` **beide** Abmeldungen auch nach unvollständigem Start. Die tatsächlichen Callback-Sets bleiben maßgeblich; kein zweites Ownership-Register anlegen. Wiederholter erfolgreicher Start installiert keine Duplikate.

Schlägt die zweite Registrierung nach ihrem Effekt fehl, nimmt das Menü seinen Teilstart selbst zurück; das Modul räumt anschließend die übrigen Ressourcen auf. Beide Unsubscribe-Schritte laufen unabhängig. Views werden im `finally` geschlossen. Diese Reihenfolge sichert Close-Handler und verhindert, dass eine Abmeldung alle weitere Freigabe blockiert; sie ist keine mechanische Umkehr jeder Java-Zuweisung.

Entfernt eine API ihren Eintrag und wirft danach, bleibt der Fehler sichtbar und der Eintrag ist weg. Verweigert eine externe API die Freigabe **vor** ihrem Effekt, darf man keinen vollständigen Erfolg behaupten: Shutdown meldet den Fehler und räumt unabhängige Ressourcen weiter auf. Ein erneuter Menü-Shutdown kann eine zuvor verweigerte Abmeldung wiederholen. Eine dauerhaft verweigernde externe API kann der Owner nicht durch eine Erfolgsmeldung reparieren.

## GUI-Lebensdauer und Sicherheit

Shutdown verarbeitet jede eigene aktive View einzeln und schließt nur bei identischem aktuellem Top-Inventory. Fremde inzwischen geöffnete Inventare bleiben unangetastet. Ein einzelner Close-Fehler wird mit UUID protokolliert, andere Spieler werden weiter verarbeitet; die aktive Registry wird im `finally` geleert.

Owner-UUID, exakter Holder-/Inventory-Bind, aktive Registry, aktuelles Top-Inventory, aktuelle Permission, erlaubte Klicktypen und UUID-Target-Mapping bleiben gemeinsam notwendig. Titel, Material, Name und Lore verleihen keine Autorität. Alte oder gefälschte Friend-Inventare dürfen auch nach Re-enable keine Mutation auslösen. Layout, Pagination und Navigation bleiben beim Menü unverändert.

## Commands, Config, Reload und persistenter Zustand

[COMMAND_DEVELOPMENT.md](COMMAND_DEVELOPMENT.md) beschreibt Syntax, Autorisierung, Help/Completion und Command-Lifecycle. FriendCommand wird durch den Lifecycle-Piloten nicht umgebaut; `/friend`, `/friends` und die bestehende Permission bleiben identisch.

[CONFIG_DEVELOPMENT.md](CONFIG_DEVELOPMENT.md) beschreibt Defaults, Schema-Versionen und vorbereitete Reload-Pläne. `CoreModule` besitzt die Runtime-Lebensdauer; `ReloadParticipant` besitzt einen ausdrücklichen Prepare-/Apply-/Rollback-Vertrag. Friend ist **kein** ReloadParticipant: Limits kommen bereits über den Live-Supplier aus ConfigService, Beziehungen sind persistenter Domain-Zustand.

Persistenz gehört in Repository und Domain-Service, nicht in Command, GUI oder einen Modul-State-Cache. `friends.yml` ist Runtime-Datenbestand ohne gebündelte Defaults. Disable speichert keine leere Ersatzdatei; Re-enable lädt einen neuen FriendService aus dem aktuellen Repository. Bestehende Commit-/Rollback-Regeln und Schema bleiben maßgeblich.

## Praktische Erweiterungs- und Testfolge

1. Feature-Owner und benötigte vorher aktive Services benennen; Modul nur bei eigener Lifecycle-Verantwortung ergänzen.
2. Konstruktorabhängigkeiten im Composition Root übergeben, ausdrücklich hinter den Dependencies registrieren.
3. Runtime lokal in Enable erwerben; jede Registrierung, Observer-Anmeldung, Veröffentlichung und finales Logging schützen.
4. Einen eigenen Cleanup-Pfad für Teilstart und Disable verwenden; Originalfehler erhalten und unabhängige Freigaben fortsetzen.
5. Bestehende Command-, GUI-, Domain- und Persistenz-Owner beibehalten; Reload nur bei tatsächlicher eigener Verantwortung ergänzen.
6. Vor dem Commit die folgende Contributor-Checkliste durchführen.

| Prüfung | Konkreter Friend-Nachweis |
|---|---|
| Normal Enable, fehlende Dependency / Command | `FriendModuleLifecycleHarness`: echtes Modul, PluginCommand und HandlerLists |
| Failed Enable / Partial Registration | Fehler nach Listener, Executor, Completer, Observern, Veröffentlichung und finalem Log |
| Cleanup-Fehler / Originalexception | Unabhängige Freigaben, suppressed Ursachen, kein still behaupteter Erfolg |
| Disable / wiederholtes Disable / Re-enable | Frische Services/Menüs/Listener/Executor, genau eine Anmeldung je Owner |
| Permissions / Inventory Ownership | `FriendMenuLifecycleHarness` und bestehende Friend-GUI-Security-Harnesses |
| Persistenzisolation / Save-Rollback | Bestehende FriendPersistence-/Service-/Domain-Harnesses, nur temporäre Dateien |
| Synthetische Komposition | `ModuleExtensionHarness` mit echtem ModuleManager; getrennt vom echten Friend-Beweis |
| Absichtliche Defekte | `scripts/tests/friend-lifecycle.Tests.ps1`: temporär kompilierte Varianten, positive Kontrollen vorher/nachher |
| Vollständige Validierung | `scripts/verify.ps1`, optional `-Offline -MavenRepository ...`; Inventare vergleichen |
| Native Paper Smoke | Wegwerfserver mit LuckPerms, isolierten Daten/JAR; Startup/Commands/GUI/Shutdown |

Paper-API-/Console-Probes sind kein Minecraft-Clienttest. Nur tatsächlich ausgeführte Prüfungen und deren Grenzen berichten. Bestehende Module schrittweise nach realem Risiko verbessern; aus diesem Piloten keine projektweite Migration ableiten.

## Clan als zweites geprüftes Lifecycle-Beispiel

`ClanModule` behält seinen öffentlichen Konstruktor mit JavaPlugin, ConfigService, IdentityModule, MessageService und CommandHelpRenderer. Der aktuelle IdentityService wird einmal je Enable bezogen; es gibt keinen aktiven Service-Hot-Swap. Die 28 Produktionsmodule und sieben Reload-Teilnehmer bleiben unverändert: Clan startet nach Friend/Presence und vor Reward. Clan ist kein ReloadParticipant; `config::getClanLimits` bleibt ein Live-Supplier.

| Ressource | Clan-Ownership |
|---|---|
| ClanService und FileClanRepository | Neue Runtime je erfolgreichem Start, aktuelles `clans.yml` lesen |
| ClanMenu und ClanMenuListener | Eigene Views, Render-Kontext und genau eine Bukkit-Registrierung mit fünf Handlern |
| ClanCommand | Eigene Executor-/Completer-Bindungen auf dem geliehenen Bukkit-Descriptor für `/clan` und `/clans` |
| Locale- und ClanChange-Callbacks | Das Menü meldet genau seine beiden Callback-Instanzen an/ab |
| Config, Identity, Messages/Localization, Help, Plugin/Server | Geliehen; Clan beendet oder leert diese Services nicht |
| `clans.yml` | Domain/Repository-Commit, kein Disable-Save und kein Leeren der Datei |

Wie bei Friend umfasst der geschützte Start Konstruktion, tatsächliche Listener-/Command-Bindung, Observer-Start, Veröffentlichung und finales Logging. `active` wird erst danach gesetzt; der öffentliche Service-Getter bleibt davor, bei fehlgeschlagenem Start und nach Disable gesperrt. Double-/Reentrant-Enable wird vor jedem neuen Erwerb zurückgewiesen. Ein package-lokaler No-op-Checkpoint im Produktionskonstruktor erlaubt Tests unmittelbar nach realen Registrierungseffekten; ein echter Logging-Handler prüft den späten Logfehler.

### Unsichere Teilanmeldung und verweigerte Freigabe

ClanMenu markiert jede Observer-Anmeldung **vor** dem externen Add als möglicherweise erworben. Das deckt sowohl Fehler vor als auch nach dessen Wirkung ab. Erst ein erfolgreich zurückgekehrtes Remove setzt diese Markierung zurück. Die tatsächlichen Callback-Sets bleiben die Wahrheit; die Markierung behauptet bei einer Exception nicht, dass ein Callback entfernt wurde. Ein erfolgreicher wiederholter Start fügt nichts doppelt hinzu.

Beide Observer werden unabhängig abgemeldet. Shutdown schließt/detacht anschließend die eigenen aktuell angezeigten Inventare, auch wenn eine Abmeldung scheitert. Fremde Ersatzinventare bleiben offen. Ein einzelner Close-Fehler wird mit UUID/Ursache protokolliert; andere Viewer werden weiter bearbeitet und die Active-Map wird geleert. Render-/Rerender-/Presence-Kontext wird zurückgesetzt. Alte Holder, Klicks und Close-Events erhalten dadurch keine neue Autorität.

Ein fehlgeschlagenes Observer-Remove bleibt retrybar. Vor einer neuen Anmeldung reconciliert das Menü ausstehende Freigaben. Das Modul verwendet einen gemeinsamen privaten Cleanup-Pfad: Menü-Shutdown bei noch registriertem Close-Listener → Handler-Abmeldung → Executor-Freigabe → Completer-Freigabe → veröffentlichte Runtime-Referenzen freigeben. Jeder Schritt besitzt eine eigene Fehlergrenze. Executor und Completer werden nur zurückgesetzt, solange sie noch der eigenen ClanCommand-Instanz gehören; fremde Ersatzbindungen werden nicht überschrieben.

Gegenüber Friend ergänzt Clan einen kleinen privaten Cleanup-Retry-Datensatz. Nach fehlgeschlagener Freigabe hält er die bisherigen Ressourcen für einen erneuten Disable-/Enable-Versuch, während Service-Getter und veröffentlichte Runtime gesperrt bleiben. Ein neuer Start muss zuerst dieses Cleanup erfolgreich abschließen, bevor er neue Ressourcen erwirbt. Das ist kein zweiter Observer-Registry, Domain-Cache oder globales Lifecycle-Framework. Nach erfolgreichem Cleanup wird auch der Retry-Datensatz freigegeben.

Startup-Exceptions bleiben dasselbe Objekt; Cleanup-Fehler kommen mit Ressourcenkontext als suppressed Ursachen hinzu. Normaler Disable aggregiert Fehler für den vorhandenen ModuleManager-Logging-Vertrag. Eine dauerhaft verweigernde externe Abmeldung kann Clan nicht erzwingen: verbleibende Registrierung und unvollständiges Cleanup müssen ausdrücklich gemeldet werden. Der Manager wird dafür nicht verändert. Bewusst extern gehaltene Java-Referenzen auf einen alten Service werden nicht widerrufen; seine ehemaligen Listener-/Menüregistrierungen verlieren ihre aktive Anbindung.

### Clan-Nachweise für Änderungen

- `ClanModuleLifecycleHarness`: echtes Modul/PluginCommand/HandlerLists, fünf Start-Checkpoints, fehlende Dependencies/Descriptor, partielle Observer-Anmeldung, echtes finales Logging, originale Exception, unabhängige Freigaben, Double-/Reentrant-Enable, Retry und frische Runtime mit aktuellem IdentityService und erneut gelesenen Clans.
- `ClanMenuLifecycleHarness`: real gerenderte und exakt gebundene Inventare, Add/Remove-Fehler vor/nach Wirkung, sichere Mehrspieler-Schließung, verweigerte Freigabe/Retry, Locale-/Commit-Refresh, ausbleibender Refresh nach Save-Fehler, Render-Kontext und stale/forged/permission-revoked Views.
- Bestehende Clan-Domain-, Service-, Persistenz-, Command-, Layout-, Localization-, Pagination- und Security-Harnesses bleiben maßgeblich. `ClanIntegrationHarness` verwendet für Cleanup das echte Modulverhalten statt bestimmter Quelltextschreibweisen.

```powershell
pwsh -File ./scripts/tests/clan-lifecycle.Tests.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
```

Die zehn temporär kompilierten Gegenproben prüfen echte Verhaltensfehler; positive Modul-/Menü-/ModuleExtension-Kontrollen laufen davor und danach. Receipts entstehen unter `target/verification/clan-lifecycle/`. Danach den vollständigen kanonischen Verifier ausführen. Nur Wegwerfdaten verwenden; weder `clans.yml`-Schema noch Gameplay, Layout, Übersetzungen oder Live-Installation gehören zum Lifecycle-Refactoring.

## Task-Owner mit ausstehender Persistenz: Reward

Der [repositoryweite Lifecycle-Audit](../reports/architecture-modernization/PHASE_05B_MODULE_LIFECYCLE_AUDIT.md) trennt bestätigte Defekte, starke Verdachtsfälle und Architekturbeobachtungen für alle 28 Module. Nur Reward wurde in dieser Phase geändert. Der Audit ist kein Nachweis, dass die übrigen Owner gegen alle Fehler abgesichert sind.

`RewardModule` besitzt RewardService samt Dirty-UUIDs, einen LOWEST-Quit-Listener und genau einen synchronen 20-Tick-Flush-Task. EconomyService und PlayerService sind geliehen. Es besitzt weder Commands noch Menü-Observer oder eine eigene Persistenzdatei. Ein Fehler beim finalen Startup-Logging gehört nach dem Task-Erwerb weiterhin zum Teilstart und muss Listener und Task zurücknehmen.

Ein fehlgeschlagenes `cancel()` darf weder den vorgesehenen Shutdown-Flush noch die unabhängige Listener-Freigabe blockieren. Reward deaktiviert zuerst seine Runtime-Autorität und versucht Cancellation, Flush, Unregister und eigenes Dirty-Cleanup einzeln. Startup behält die ursprüngliche Exception; Freigabefehler werden mit Ressourcenkontext angehängt. Verweigerte externe Freigaben bleiben sichtbar und retrybar. Ein neuer Start erwirbt erst nach erfolgreicher Bereinigung neue Ressourcen.

Der Task prüft zusätzlich seine erfasste Service-Identität gegen die aktive Runtime. Ein alter Callback darf nach einem Neustart nicht über ein Modulfeld die neue Service-Generation flushen. Das ergänzt die tatsächliche Cancellation; es behauptet bei einem verweigerten Cancel keine erfolgreiche Abmeldung. Getter bleiben während Teilstart und nach Disable gesperrt; Double-/Reentrant-Enable wird vor Erwerb zurückgewiesen.

Die bestehende Persistenzpolitik bleibt erhalten: Grants sind deferred, reguläre Batch-Fehler bleiben dirty für Retry, normaler Shutdown flusht vor Listener-Abmeldung. PlayerModule bleibt der spätere Persistenz-Fallback und wird nicht vom Consumer beendet. Ein sauberer Lifecycle ohne Dirty-State schreibt Profile nicht; ausstehende Rewards werden beim Shutdown ausdrücklich gespeichert. Extern gehaltene alte Service-Referenzen werden nicht widerrufen.

`RewardModuleLifecycleHarness` prüft echte HandlerLists, ausführbare Task-Callbacks, temporäre Profil-Commits, verweigerte Freigaben, originale Exceptions, Guard-/Restart-Verhalten und Reverse-Rollback mit dem echten ModuleManager. `RewardLifecycleHarness` verwendet für Shutdown-Reihenfolge und Task-Anzahl ebenfalls das tatsächliche Modulverhalten. Die neun kompilierten Gegenproben laufen mit portablen Maven-Defaults:

```powershell
pwsh -File ./scripts/tests/reward-lifecycle.Tests.ps1
pwsh -File ./scripts/verify.ps1
```

Receipts liegen unter `target/verification/reward-lifecycle/`. Ein nativer Neustart muss alle Consumer vorher stoppen und anschließend die Komposition mit aktuellen Services aufbauen; ein isolierter Reward-Neustart unter laufendem OnlineReward/Quest ist kein unterstützter Hot-Swap.

## Player: Lifecycle mit eigenem Aggregate-Cache und Save-Verantwortung

`PlayerModule` besitzt FilePlayerRepository, PlayerService mit geladenen CorePlayer-Aggregaten,
PlayerSettingsService und genau einen Join-/Quit-Listener. ConfigService und MessageService sind
geliehen. Repository, PlayerFileSchema und SafeFileWriter behalten Datei-, Schema-, Index- und
Commit-Ownership. Es gibt weder einen zweiten Cache noch eine eigene Settings-/Notfalldatei.

Der geschützte Start umfasst Storage-Initialisierung, Service-Konstruktion, tatsächliche
Listener-Registrierung und Runtime-Veröffentlichung. Double-/Reentrant-Enable wird vor Erwerb
abgewiesen. Getter sind auch während Veröffentlichung noch gesperrt. Ein Registrierungsfehler
nach seiner Wirkung wird mit derselben Listener-Instanz zurückgenommen; die ursprüngliche
Startup-Exception bleibt erhalten, weitere Cleanup-Ursachen werden angehängt.

Beim Disable werden zuerst die öffentlichen Runtime-Referenzen und die Event-Autorität der
exakten Service-Generation entzogen. Listener-Unregister und Profil-Speicherung haben unabhängige
Fehlergrenzen. Auch wenn Bukkit eine Registrierung nicht freigibt, dürfen alte Join-/Quit-Callbacks
keine Profile mehr laden, verändern oder entladen. Eine verweigerte Abmeldung wird weiterhin als
Fehler gemeldet und bleibt für einen ausdrücklichen erneuten `disable()`-Aufruf erhalten.

Anders als Friend/Clan besitzt Player einen noch ungespeicherten Aggregate-Cache. Anders als
Reward besitzt es die Profile selbst und ist der späte Persistenz-Fallback nach seinen Consumers.
`saveAll()` behält seinen öffentlichen void-/Log-and-continue-Vertrag. Der package-lokale strikte
Shutdown-Aufruf versucht ebenfalls jedes Profil, meldet aber unvollständige Saves gesammelt mit
exakten UUIDs und Originalursachen. Logging-Fehler dürfen spätere Save-Versuche nicht überspringen.
Erst nach vollständigem Erfolg darf das Modul den Cache leeren.

Bei einem Fehler hält PlayerModule denselben bisherigen PlayerService privat für einen Retry;
Settings-/Repository-Veröffentlichung bleibt gesperrt. Ein neues `enable()` ist bis zum erfolgreichen
expliziten `disable()`-Retry blockiert. Erfolgreich gespeicherte Profile sind bereits dauerhaft
committed und können bei einem Retry nochmals gespeichert werden. Es gibt keine Transaktion über
alle Spieler. Settings/Social/Economy behalten ihre bisherigen direkten Save-/Rollback-Grenzen;
Reward/Quest ihre Dirty-/Flush-Verträge. Nicht alle Domain-Schreibvorgänge werden zum Shutdown
verschoben, und die Reihenfolge der 28 Module sowie sieben Reload-Teilnehmer bleibt unverändert.

**Grenze:** Ein privater In-Memory-Retry ist keine dauerhafte Crash-Recovery. ModuleManager meldet
Disable-Fehler, fährt mit anderen Modulen fort und leert seine Enabled-Liste; er plant keinen Retry.
Ein späteres `manager.disableAll()` wiederholt Player daher nicht automatisch. Ein schwerwiegender
Shutdown-Hinweis nennt betroffene UUIDs, notwendigen Betreibereingriff und den Verlust ungespeicherter
Änderungen beim JVM-Ende. Bei vollständigem Server-Shutdown, Crash oder Stromausfall kann der nur
im Speicher gehaltene Stand verloren gehen. Verbraucher müssen vor einem modulweisen Neustart
beendet und danach mit neuen Services verdrahtet werden; laufender Service-Hot-Swap ist nicht
zugesichert.

`PlayerModuleLifecycleHarness` prüft diese Grenzen mit dem echten Modul, Services, Listener,
FilePlayerRepository, realen HandlerLists und Wegwerf-Profilen. Future-Schema-Konflikte und
vorhandene Recovery-Marker werden niemals automatisch überschrieben oder gelöscht. Zehn
temporär kompilierte Gegenproben ergänzen die kanonische Prüfung:

```powershell
pwsh -File ./scripts/tests/player-lifecycle.Tests.ps1
pwsh -File ./scripts/verify.ps1
```

Receipts liegen unter `target/verification/player-lifecycle/`. Befunde und nachgewiesene Grenzen
stehen im [Phase-5C-Bericht](../reports/architecture-modernization/PHASE_05C_PLAYER_LIFECYCLE.md).

## OnlineReward: ein Task mit exakter Service-Generation

`OnlineRewardModule` besitzt genau einen OnlineRewardService, einen MessageRenderer und einen
synchronen Bukkit-Verarbeitungstask mit 20 Ticks Verzögerung und Periode. PlayerService,
RewardService, ConfigService und MessageService sind geliehen. Das Modul besitzt weder einen
weiteren Profil-Cache noch Repository, Quit-Listener, Command, Menü oder Reward-Flush-Task.

Enable weist aktive, reentrante und während Cleanup eintreffende Aufrufe vor dem Erwerb ab.
Dependency-Erwerb, lokale Service-/Renderer-Konstruktion, Task-Anmeldung und finales Logging
liegen in derselben Fehlergrenze. Erst danach werden Runtime und aktiver Getter freigegeben.
Bei einem Fehler bleibt dasselbe Exception-Objekt primär; Cancellation-/Statusfehler werden
mit Kontext als suppressed Ursachen angehängt. Die fehlgeschlagene Runtime verarbeitet nichts.

Der Callback erfasst seinen eigenen Service und Renderer. Er darf nur arbeiten, solange `active`
gesetzt ist und der veröffentlichte Service exakt dieser Instanz entspricht. Diese Prüfung gilt
auch nach dem externen Statistikzugriff und vor Benachrichtigungen. Alte Tasks erhalten beim
Re-enable keine Autorität über die neue Generation. Das bleibt das vorhandene Main-Thread-Modell,
keine asynchrone Verarbeitung und kein zweiter Scheduler.

Disable sperrt zuerst Getter und Callback-Autorität und gibt die veröffentlichten Referenzen
frei. Anschließend wird ausschließlich das eigene Task-Handle abgebrochen und `isCancelled()`
geprüft. Auch eine Cancellation, die erst wirkt und danach wirft, bleibt als Fehler sichtbar;
bestätigte Freigabe benötigt aber keinen Retry. Bei verweigerter oder nicht verifizierbarer
Cancellation bleibt nur das genaue Task-Handle als `pendingCancellation` erhalten. Der verbliebene
Callback ist inert. Ein weiteres Disable oder Enable versucht diese Freigabe erneut; vor einem
erfolgreichen Abschluss wird kein neuer Task erworben. Wiederholtes erfolgreiches Disable ist still.

Eine Scheduler-Implementierung, die einen Task registriert und **vor Rückgabe des Handles** wirft,
liefert dem Modul keine exakt bekannte Ressource für Cancellation. Der erfasste Callback bleibt
auch dann inaktiv. Das Modul verwendet ausdrücklich kein `cancelTasks(plugin)`, weil dies fremde
Tasks desselben Plugins betreffen würde. Dieser kontrolliert reproduzierte Außenstellenfehler ist
keine Zusage vollständiger Freigabe ohne Handle. Native Paper-Akzeptanz prüft den normalen Pfad.

OnlineReward-Disable grantet, speichert und flusht nichts. Wallet und OnlineRewardProgress bleiben
im bestehenden CorePlayer-Aggregat. RewardModule besitzt Dirty-UUIDs, Quit-/Batch-Flush und seinen
Shutdown-Flush; PlayerModule besitzt die Profile und den späteren Aggregate-Save. Rückwärts stoppt
OnlineReward vor Reward vor Player, nachdem Quest/DailyQuest beendet wurden. Consumer müssen vor
einem vollständigen Neustart beendet und mit den neuen Services verdrahtet werden. Bewusst extern
gehaltene alte Java-Service-Referenzen werden nicht widerrufen. Crash oder fehlgeschlagene Saves
besitzen weiterhin keine allgemeine Exactly-once-/Mehrdatei-Transaktion.

`OnlineRewardModuleLifecycleHarness` prüft die echten Module/Services mit ausführbaren Scheduler-
Callbacks, Before-/After-effect-Cancellation, Startup-Logging, Generationen und temporären echten
Spielerdateien. Die vorhandene OnlineRewardLifecycleHarness behält Cadence, Modulreihenfolge und
Spieler-Isolation; nur zwei Schreibweisen-Assertions verwenden jetzt diesen Verhaltensnachweis.
Zehn kompilierte Gegenproben laufen separat:

```powershell
pwsh -File ./scripts/tests/online-reward-lifecycle.Tests.ps1
pwsh -File ./scripts/verify.ps1
```

Receipts: `target/verification/online-reward-lifecycle/`. Reproduktionen, Grenzen und native
Akzeptanz stehen im [Phase-5E-Bericht](../reports/architecture-modernization/PHASE_05E_ONLINE_REWARD_LIFECYCLE.md).
