# Module entwickeln und sicher betreiben

Diese Anleitung beschreibt das vorhandene explizite Modulmodell und den abgesicherten Friend-Piloten. Sie behauptet nicht, dass alle älteren Module bereits jeden späten Fehler behandeln. Einstieg: [Architektur](ARCHITECTURE.md), [Code-Konventionen](CODE_STYLE.md), [Validierung](../README.md#entwicklung).

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
