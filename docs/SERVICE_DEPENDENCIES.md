# Service-Abhängigkeiten und Runtime-Lebensdauer

Diese Anleitung ergänzt [MODULE_DEVELOPMENT.md](MODULE_DEVELOPMENT.md) und die [Architektur](ARCHITECTURE.md). Der begrenzte Pilot ist PresenceModule; andere vorhandene Modulabhängigkeiten sind damit nicht automatisch migriert.

## Ownership zuerst bestimmen

Eine gespeicherte Referenz macht den Consumer nicht zum Owner. Der Owner erzeugt den Service und verantwortet seinen Lifecycle. Der Consumer gibt nur seine eigenen Registrierungen, Views, Tasks und Runtime-Referenzen frei.

| Presence-Abhängigkeit | Owner / Verfügbarkeit | Verwendung durch Presence |
|---|---|---|
| PlayerService | PlayerModule; nach dessen Enable bis zum Disable | Geladene Profile bei Seeding und Join prüfen |
| PlayerSettingsService | PlayerModule; dieselbe Modul-Lebensdauer | Aktuelle Benachrichtigungseinstellung des Empfängers lesen |
| SocialService | SocialModule; nach dessen Enable bis zum Disable | Ignore-Beziehungen in beiden Richtungen lesen |
| FriendService | FriendModule; Getter erst nach erfolgreichem Enable, nach Disable gesperrt | Aktuelle Freunde des Subjects lesen |

Alle vier Services werden geliehen. Presence darf sie weder deaktivieren noch ihre Caches, Einstellungen oder Repository-Daten leeren. Presence besitzt nur PresenceService, FriendPresenceNotifier und die Listener-Registrierung. Der Notifier registriert selbst keine zusätzlichen Observer.

### Gemeinsame Darstellung von Friend, Clan und Presence

Prototype Step 01B ergänzt `message.PlayerNameFormatter`: Der Composition Root injiziert denselben schmalen, zustandslosen Formatter in FriendModule, ClanModule und PresenceModule. Sein typisierter `rankModule::getRankService`-Provider wird erst beim Formatieren eines Namens gelesen. Rank ist dann bereits gestartet. Bei einem erneuten Modulstart wird dadurch die aktuelle RankService-Runtime verwendet. Die vier oben beschriebenen Presence-Service-Provider behalten dagegen ihren beim Enable gebundenen Vertrag.

`format(UUID, String)` nutzt bei jeder neuen Nachricht `RankService.getPrimaryRank(UUID)` und `RankInfo.effectiveColor()`. LuckPerms liest dabei ausschließlich geladene User und Gruppen; es gibt keinen Load, Cache, Task, Observer oder Schreibzugriff des Formatters. Fehlende User, Gruppen oder gültige Farbmetadaten ergeben Weiß. Namen bleiben `Component.text` und werden nie als MiniMessage interpretiert. Reale Actor-/Target-UUIDs werden bis zur Messaging-Grenze weitergereicht; ein beliebiger Name wird für die Darstellung nicht aufgelöst. Kompatible alte Konstruktoren liefern ohne Rank-Provider neutrale weiße Namen.

`message.SocialChannel` ergänzt einmalig die übersetzbaren Präfixe `[Friends]` / `[Freunde]` in Gold beziehungsweise `[Clan]` in Dark Purple. Der Body bleibt grau, Fehler besitzen ein kurzes rotes lokalisiertes Label, Presence-Online behält seinen begrenzten grünen Status. Clan-Namen bleiben Gold und Tags Aqua. `MessageService.send()` liefert davor weiterhin den unveränderten konfigurierten globalen Präfix; `sendRaw()` wird hierfür nicht eingesetzt. Mehrzeilige Einladungen und Listen haben einen Kanalpräfix an der Überschrift; folgende Zeilen erhalten keinen weiteren. Help und editierbare Beispiele behalten ihr bestehendes Verhalten.

Die Friend- und Clan-Accept/Deny-Buttons bleiben `RUN_COMMAND` mit kanonischen Spieler- beziehungsweise Clan-UUIDs. Cancel, Remove, Beispiele und destruktive Bestätigungen behalten ihre bisherigen Suggest-/Bestätigungsregeln. Modulreihenfolge, Reload, Permissions, Domain-Services und persistente Schemas ändern sich nicht. Ausführliche Nachweise und der noch offene Client-Test stehen im [Step-01B-Bericht](../reports/prototype/STEP_01B_SOCIAL_CHAT_STYLING.md).

## Die passende Grenze wählen

| Form | Wann sie sinnvoll ist | Beispiel |
|---|---|---|
| Direkter Service | Bereits bei Konstruktion vorhanden und während der gesamten Consumer-Lebensdauer gültig | MessageService im Presence-Konstruktor |
| Typisierter `Supplier<Service>` | Modul wird vor seinem Service konstruiert; ein neuer Start muss die aktuelle Runtime beziehen | Vier Presence-Provider |
| Schmale fachliche Capability | Nur ein kleiner, sinnvoll abgrenzbarer Teil einer größeren API wird benötigt | FriendRequestPolicy; interne Lookup-/Preference-Seams im FriendPresenceNotifier |
| Feature-Callback | Eine konkrete Aktion mit ausdrücklich verdrahtetem Owner | Menü öffnen / Back-Navigation |
| Ganzes Modul | Tatsächliche modulweite Capability nötig oder bestehende API muss kompatibel bleiben | Settings-Back-Setter in LobbyExperience; alter Presence-Konstruktor |

Nicht jede Referenz braucht einen Supplier. Der Live-Supplier `configService::getFriendLimits` liest bewusst aktuelle Konfigurationswerte; ein Presence-Service-Provider wird dagegen nur beim Start aufgelöst. Diese unterschiedlichen Verträge nicht verwechseln. Für Config-Snapshots und Reload siehe [CONFIG_DEVELOPMENT.md](CONFIG_DEVELOPMENT.md).

## Konstruktion ist nicht Aktivierung

`VapeeCore.onEnable()` konstruiert die Modulobjekte vor `ModuleManager.enableAll()`. Daher dürfen die Presence-Provider während der Konstruktion keine noch inaktiven Owner-Getter aufrufen.

Der Composition Root verwendet ausdrücklich:

```java
presenceModule = new PresenceModule(this, playerModule::getPlayerService,
        playerModule::getPlayerSettingsService, socialModule::getSocialService,
        friendModule::getFriendService, messageService);
```

Die Registrierung bleibt Player / Social / Friend vor Presence. ModuleManager berechnet keinen Dependency-Graph. Ein Method-Reference-Provider hält weiterhin seinen Modul-Empfänger indirekt fest; er übernimmt nicht dessen Service-Ownership und beseitigt keine Objektverbindung aus dem Heap.

## Pro Aktivierung genau einen Satz Services erfassen

Presence speichert vier typisierte Provider und prüft die Provider-Referenzen sofort auf null. Erst `enable()` ruft jeden Provider einmal auf und validiert jedes Ergebnis. Aus diesen vier lokalen Referenzen werden der neue Notifier und Listener konstruiert. Danach folgen Listener-Registrierung, initiales Seeding, Veröffentlichung und abschließendes Logging innerhalb der geschützten Startup-Grenze.

Während dieser Aktivierung behalten Listener/Notifier genau diese geliehenen Services. Normale fachliche Änderungen **innerhalb** derselben Services bleiben sichtbar. Ein später geänderter Provider-Zielwert schaltet eine aktive Runtime dagegen nicht auf einen anderen Service um. Es gibt kein Provider-Polling und keinen versteckten Service-Cache über Aktivierungen hinweg.

```text
Konstruktor: Provider speichern, keine Services beziehen
Enable A:    aktuelle vier Services einmal erfassen
Betrieb A:   A-Referenzen verwenden, normale Domain-Änderungen lesen
Disable:    eigene Handler / Presence-Zustand / Runtime-Referenzen freigeben
Enable B:   Provider erneut auflösen und neue Runtime aus B erzeugen
```

Das simuliert einen geordneten vollständigen Modul-Lifecycle. Ein Owner-Neustart unter einem weiterhin aktiven Consumer ist **kein unterstützter Hot-Swap-Vertrag**. Alle betroffenen Consumer müssen vorher deaktiviert und anschließend in Dependency-Reihenfolge neu gestartet werden. Alte externe Java-Referenzen werden nicht magisch widerrufen.

## Öffentliche Konstruktoren kompatibel halten

Der bisherige öffentliche Konstruktor mit PlayerModule, SocialModule und FriendModule bleibt als Adapter erhalten. Er validiert die Modulargumente sofort und delegiert ihre Getter als Method References an den bevorzugten Konstruktor. Er ruft diese Getter nicht bei der Konstruktion auf.

Damit bleibt die bestehende API nutzbar. Die Kompatibilitätssignatur erwähnt weiterhin Modultypen und ihre Method References halten die Owner indirekt fest. Die messbare Verbesserung sind **null gespeicherte fremde Modulfelder statt drei** und vier ausdrückliche Service-Grenzen. Keine vollständige Entkopplung, geringerer Speicherverbrauch oder Performance-Steigerung daraus ableiten.

## Fehlende Dependencies und fehlgeschlagener Start

Alle vier Services sind erforderlich. Ein null-Ergebnis wird vor der Runtime-Erzeugung mit dem Service-Namen abgelehnt. Eine vom Provider geworfene RuntimeException bleibt unverändert erkennbar. Keine leere Ersatzimplementierung einsetzen, damit ein halb aktiver Consumer scheinbar startet.

Bei Registrierungs-, Seeding- oder spätem Logging-Fehler entfernt Presence seinen eigenen Listener, leert seinen eigenen Presence-Zustand und gibt veröffentlichte Runtime-Felder frei. Ein anschließender gültiger Start ist möglich. Das ist kein Shutdown der bereits bezogenen fremden Services. Doppeltes Enable eines aktiven Moduls wird vor erneutem Dependency-Erwerb abgelehnt.

## Contributor-Checkliste

1. Owner, verfügbare Lebensdauer und tatsächlich benötigte Operationen des neuen Services benennen.
2. Direkte Referenz, typisierten Provider, vorhandene Capability oder konkreten Callback nach diesem Vertrag wählen.
3. Im Composition Root ausdrücklich verdrahten und erforderliche Start-/Shutdown-Reihenfolge erhalten.
4. Konstruktion ohne aktive Dependencies prüfen; null-Referenzen sofort und fehlende Runtime-Ergebnisse beim Erwerb ablehnen.
5. Pro Enable einen konsistenten Servicesatz erfassen. Keine ereignisweise Umschaltung oder aktivierungsübergreifende Memoisierung einführen.
6. Teilstart, unabhängige Provider-Fehler, Disable und Retry mit dem echten Consumer testen. Fremde Services und persistente Daten müssen unberührt bleiben.
7. Zwei unterscheidbare Service-Generationen durch tatsächliches Verhalten prüfen; bloße Supplier-Aufrufzähler reichen nicht.
8. Kompatible Konstruktoren und reale Autorisierungs-/Benachrichtigungsregeln erhalten. Stale-Handler und alte Runtime-Referenzen prüfen.
9. Kompilierte Gegenproben, vollständige [Validierung](../README.md#entwicklung) und isolierten Paper-Smoke durchführen.

`PresenceDependencyBoundaryHarness` zeigt diesen Pfad mit echten Player-/Settings-/Social-/Friend-Services und getrennten temporären Dateien. `scripts/tests/presence-dependencies.Tests.ps1` prüft acht absichtliche Defekte über temporäre Klassen-Overlays. Kein zweiter Test- oder DI-Rahmen ist erforderlich.

Statische Service-Registries, untypisierte Service-Maps, eager Modul-Getter in Konstruktoren, dauerhaft memoisiert geliehene Runtime-Services und globale Proxy-/Eventbus-Lösungen lösen dieses Ownership-Problem nicht. Weitere Modul-Migrationen bleiben eigene, begrenzte Aufgaben.
