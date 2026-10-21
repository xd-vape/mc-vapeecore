# Architektur und Erweiterungspfade

Diese Referenz beschreibt den aktuellen modularen Monolithen. Der [Developer Guide](DEVELOPER_GUIDE.md) enthält Feature-Details; das [README](../README.md#entwicklung) den Einstieg und die Validierung. `docs/` beschreibt gepflegte Architektur, `reports/` historische Nachweise. Bevorzugte Konventionen sind ausdrücklich keine Behauptung, dass jede vorhandene Klasse ihnen bereits vollständig folgt.

[CODE_STYLE.md](CODE_STYLE.md) ergänzt die Java-/Package-Konventionen. Der deterministische Maven-Formatter prüft aktuell ausschließlich die explizite Pilot-Allowlist; Formatierung ist keine Änderung fachlicher Ownership oder eine Package-Migration.

## Composition Root und Lifecycle

**Aktuell:** `VapeeCore.java#onEnable` erstellt Konfiguration, Nachrichten und Module mit expliziten Konstruktorabhängigkeiten. Danach registriert es die Module bei `ModuleManager`. Die Registrierungsreihenfolge ist die Startreihenfolge, kein automatisch berechneter Dependency-Graph. Die konkrete Liste dort ist maßgeblich: Localization vor den Nachrichtenkonsumenten, Permission/Rank vor ihren Konsumenten, Player vor profilabhängigen Features, Quest vor DailyQuest, LobbyExperience nach seinen Menü-/Lobby-/Activity-/Warp-Abhängigkeiten.

`CoreModule` besitzt `getName`, `enable`, `disable`. `ModuleManager` verweigert doppelte Namen und spätere Registrierung nach erfolgreichem Start. Erst nach erfolgreichem `enable` wird ein Modul in die Liste gestarteter Module aufgenommen. Schlägt ein Start fehl, deaktiviert der Manager bereits gestartete Module rückwärts. **Das fehlgeschlagene Modul muss seine eigenen teilweise registrierten Ressourcen selbst zurücknehmen.** Beim Shutdown läuft dieselbe umgekehrte Reihenfolge; ein Disable-Fehler wird protokolliert und verhindert die übrigen Aufräumarbeiten nicht.

Ein Modul besitzt die Integration seines Features: Service-Erzeugung, Commands/Completer, Bukkit-Listener, schmale Domain-Observer, Tasks und aktive Views. Es entfernt diese beim Disable und nach teilweisem Enable wieder. Services dürfen keine weiterverwendbaren Runtime-Referenzen nach ihrem Lebensende anbieten. Nicht jedes Modul ist bereits gegen jeden denkbaren späten Enable-Fehler abgesichert; konkrete Lifecycle-Harnesses schützen ausgewählte Fehlerstellen, keinen universellen Beweis.

`SettingsModule` schützt Listener-/Command-Bindung, alle drei Menü-Observer und die Veröffentlichung seiner Runtime-Referenzen gemeinsam. Schlägt einer dieser Schritte fehl, schließt es nur seine exakt gebundenen aktiven Inventare, entfernt seine Observer und Handler und setzt Executor/Completer auf den Bukkit-Fallback zurück. `LobbyExperienceModule` besitzt beide Back-Callbacks in Settings und Visibility: Rollback und Disable setzen beide über die bestehenden Setter auf `Player::closeInventory` zurück. Dazu deaktiviert es Registry und Item-Refresh, stoppt seine Menüs und Glide-Runtime und entfernt ausschließlich eigene Subscriptions/Handler.

Unabhängige Cleanup-Schritte laufen auch nach einem Runtime-Fehler weiter. Beim fehlgeschlagenen Enable bleibt die ursprüngliche Exception primär; Cleanup-Fehler stehen mit Ressourcenkontext in `getSuppressed()`. Beim normalen Disable werden gesammelte Fehler protokolliert. Menü-Shutdown schließt eigene Views auch dann, wenn eine Observer-Abmeldung wirft. Ein zweites Disable ist sicher; ein erneutes Enable erzeugt genau einen neuen Satz Ressourcen. Die Java-Feature-Registrierungen der Lobby bleiben dabei bestehen, ihre Runtime-Aktivierung wird zurückgenommen. Das ist modul-lokales Cleanup, kein zweiter Rollback-Owner neben `ModuleManager`.

**Bevorzugt:** Ein weiterer `CoreModule` ist für eine eigene Lifecycle-Verantwortung sinnvoll. Ein reiner Algorithmus oder ein zusätzlicher Anwendungsfall gehört gewöhnlich in das bestehende Feature. Mehrere heutige Module erhalten ganze andere Module und holen deren Services während `enable`. Neue fachliche Zusammenarbeit soll das kleinste praktikable Service-Interface verwenden, sofern dessen Lebensdauer sauber verdrahtet werden kann. Kein Service-Locator, automatisches Scanning oder DI-Container.

## Zuständigkeiten

| Baustein | Verantwortung | Konkreter Einstieg |
|---|---|---|
| Module | Integration und Ressourcen-Lebensdauer | `settings/SettingsModule`, `presence/PresenceModule` |
| Service | Fachliche Regeln, Mutationsergebnis und Veröffentlichungszeitpunkt | `friend/FriendService`, `player/settings/PlayerSettingsService` |
| Repository | Feature-Schema, Laden und persistente Schreibgrenze | `player/repository/FilePlayerRepository`, `friend/FileFriendRepository` |
| Command | Syntax, Sender/Permission/Zielprüfung, Service-Aufruf und Rückmeldung | `settings/command/SettingsCommand`, `quest/daily/command/CoreQuestCommand` |
| Listener | Bukkit-Ereignis in einen geschützten Anwendungsfall übersetzen | `settings/SettingsListener`, `presence/PresenceListener` |
| GUI/Menu | Darstellung, aktive Inventory-Bindung und Ziel-Mapping | `settings/SettingsMenu`, `friend/gui/FriendMenu` |
| Configuration | Betreiberwerte lesen/validieren und Runtime-Snapshot bereitstellen | `config/ConfigService`, `quest/daily/DailyQuestConfig` |
| Localization | Empfängersprache, sichere Templates und gemeinsame Katalog-Snapshots | `i18n/LocalizationModule`, `message/MessageService` |

Commands und Listener bleiben Adapter. Eine Permission-Prüfung am Einstieg ersetzt keine fachlichen Regeln des Services. Ein Menü darf keinen zweiten Freundschafts-, Wallet- oder Quest-Zustand verwalten.

## State und Persistenz

| State | Eigentümer und Mutation | Persistenz / Commit-Verhalten |
|---|---|---|
| Geladene Profile | `PlayerService` verwaltet `CorePlayer` nach UUID, Load/Save/Unload | `PlayerRepository` / `FilePlayerRepository`, Schema in `PlayerFileSchema`; Unload speichert vor Entfernen |
| Coins | `EconomyService` mutiert die `CoinWallet` im Profil; kein zweiter Kontostand | Direkte Economy-Operationen speichern mit Rücknahme bei Fehler; deferred Reward-Mutationen bleiben ausdrücklich dem Reward-Flush zugeordnet |
| Rewards | `RewardService` koordiniert Grants und eigene `dirtyPlayers` | Fehlgeschlagener Flush bleibt dirty und wird später wiederholt; kein falscher Commit-Observer |
| Quest-Fortschritt | `QuestService`, `PlayerQuestState`, Definitionen aus `QuestDefinitionRegistry` | Quest besitzt eigenes Dirty-Tracking/Flush; ausstehende Rewards sind ein expliziter Zustand. Profil-Save ist die gemeinsame Dateigrenze |
| Daily-Auswahl | `DailyQuestService`, `PlayerDailyQuestState`, `DailyQuestSelector` | Regulärer Sync bewahrt denselben Cycle. Explizites Force reconciliert Auswahl und Cycle gemeinsam; Fehler nehmen beide Runtime-Snapshots zurück |
| Freunde / Clans | `FriendService` / `ClanService`, eigene immutable veröffentlichte Snapshots | Feature-eigene Repositories und Dateien. Neue Beziehung wird erst nach erfolgreichem Save publiziert; keine Profilkopie als zweite Wahrheit |
| Settings / Ignore | `PlayerSettingsService` / `SocialService` mutieren die Profilbereiche | Save über `PlayerService`, Rücknahme bei Fehler, danach schmale Change-/Relationship-Observer. Ignore-Snapshots sind abgeleitete Leseransichten |
| Lobby | `LobbyService` für Spawn/Regeln; `LobbyPlayerStateService` für NORMAL/BUILD und Inventarbesitz | `LobbyConfig` speichert den Spawn geschützt. Hotbar-Präsentation kommt aus Live-`lobby.yml`; PDC-Identität und Verhalten aus `LobbyItemRegistry` |
| Activities | `ActivityService` besitzt Sessions, Mitgliedschaft und Reservierungen; `BlackjackService` die Spielrunde | Session-/Runden-State ist Runtime. Persistente Tischdefinitionen gehören dem Blackjack-Feature, nicht einem GUI oder dem Profil |
| Betreiberkonfiguration | `ConfigService` bzw. Feature-Config und koordinierter `ReloadService` | Validierte Snapshots; kein Menü schreibt YAML. Startup-Evolution und Runtime-Reload sind getrennt |
| Sonstiger Runtime-State | Das Feature, das ihn erzeugt: aktive GUI-Inventare, Page/UUID/ID-Mappings, Tasks, Nametag-Teams, Glide-Berechtigung/temporäre Elytra | Kein automatischer Persistenzanspruch. Besitzer muss Listener/Tasks/Views/temporäre Zustände beim Verlassen und Shutdown freigeben |

Nicht jeder Service nutzt dasselbe Transaktionsmodell: Settings speichern synchron und rollen zurück; Reward/Quest unterstützen bewusst Dirty-State und Retry. Diese Grenzen dürfen nicht durch einen zusätzlichen generischen Cache oder eine zweite Save-Schleife verdeckt werden.

`persistence/SafeFileWriter` ist der gemeinsame technische Schreibbaustein für **Player- und Lobby-YAML**, kein Schema-Eigentümer aller Dateien. Er serialisiert vor dem Öffnen, schreibt und validiert einen Kandidaten und ersetzt atomar. Falls das Dateisystem atomaren Ersatz nicht unterstützt, schützt ein Recovery-Backup den vorherigen Stand. Nicht auflösbare Wiederherstellung bleibt sichtbar und blockiert weiteres Schreiben. Andere Repositories besitzen ihre bestehenden eigenen Save-/Backup-Verträge; diese Phase vereinheitlicht sie nicht.

## Zusammenarbeit und Reload

- **Direkter Service-Aufruf:** Für eine synchrone Operation mit eindeutigem Besitzer, z. B. Settings-Mutation oder Quest-Fortschritt. Thread-Vertrag beachten; viele Domains sind ausdrücklich Main-Thread-owned.
- **Kleines Interface:** Für eine fachliche Grenze wie `FriendRequestPolicy`, `PlayerRepository` oder die Testseams an externen Bukkit-/Dateigrenzen.
- **Callback:** Für eine einzelne verdrahtete Fähigkeit, z. B. Back-Opener zum Player Menu oder Clan-Tag-Leser für Presentation. Keine zweite Domain-Authority im Callback.
- **Vorhandener Change-Listener:** Wenn mehrere betroffene aktive Views bzw. Policies nach einem erfolgreichen Commit aktualisiert werden müssen: Friend/Social-Relationships, `PlayerSettingsChangeListener`, Warp- und Quest-Change-Observer. Beim Disable abmelden. Domain emittiert die Änderung; sie importiert dafür keine Bukkit-Menüs.

Es gibt keinen globalen Eventbus. Automatische GUI-Updates verwenden Ereignisse/Commit-Observer, keine zusätzliche globale Polling-Schleife. Narrow Observer sind nicht automatisch dauerhafte Ereignislogs oder Transaktionen über mehrere Repositories.

`VapeeCore` erstellt die aktuelle Teilnehmerliste des `ReloadService` ausdrücklich. Alle Teilnehmer bereiten zuerst vor; Prepare darf den aktiven Zustand nicht verändern. Apply schaltet die vorbereiteten Snapshots nacheinander um. Bei einem Fehler werden der fehlgeschlagene Plan und vorher angewendete Pläne rückwärts zurückgenommen. Unvollständiger Rollback wird ausdrücklich gemeldet. Neue reloadbare Werte müssen dieses Verfahren verwenden; nicht jeder Modulzustand ist reloadbar.

`ConfigEvolution` arbeitet ausschließlich beim Startup auf `MANAGED_CONFIGS`: exaktes Backup vor ergänzten Defaults, Erhalt von Betreiberwerten/unbekannten Keys, keine Downgrades zukünftiger Versionen. Sprachkataloge und persistente Feature-Dateien sind ausgeschlossen. `/core reload` migriert oder überschreibt keine Betreiberdateien.

## Standardänderungen

### A. Eine Spielereinstellung ergänzen

1. Fachlichen Default und State in `player/settings/PlayerSettings` definieren; Visibility-Unteroptionen gehören zu `PlayerVisibilitySettings`.
2. Profil-Parsing, Legacy-Default und Serialisierung in `FilePlayerRepository` prüfen; Schemaänderungen mit `PlayerFileSchema` abstimmen. Es gibt keine separate Settings-Datei als konkurrierende Authority.
3. Zugriff und Mutation in `PlayerSettingsService`: vorherigen Wert sichern, speichern, bei Fehler zurücknehmen, erst danach Change-Observer informieren.
4. Sichtbare Einstellung über `SettingsMenuEntry` / `SettingsMenuEntries`, `SettingsMenu.Action` und das owning `DEFAULT_LAYOUT` ergänzen. Der vorhandene Listener delegiert an den Service; neue fachliche Aktionen benötigen tatsächlich einen Dispatch-Pfad. Verhalten wie Sidebar/Sound bleibt beim jeweiligen Besitzer.
5. Texte an derselben Feature-Stelle in `src/main/resources/lang/en.yml` und `de.yml` ergänzen; `Translations.tr` mit sicheren Component-Platzhaltern verwenden.
6. `PlayerSettingsServiceHarness`, Persistenz-Harnesses und passende Settings-Kompositions-/Security-/Update-Harnesses erweitern. Save-Fehler und unveränderte nicht betroffene Werte prüfen.

### B. Einen Command ergänzen

1. Bestehenden Root bevorzugen, wenn derselbe Anwendungsbereich und dieselbe Help-/Permission-Struktur passen: `/core quest force` wird von `CoreCommand` an `CoreQuestCommand` delegiert. Ein eigenständig auffindbarer Einstieg wie `/quests` hat seinen eigenen Root.
2. Neue Roots/Permissions in `src/main/resources/plugin.yml` deklarieren; Permission-Defaults und Children bewusst wählen. Bestehende Nodes nicht beiläufig erweitern.
3. Executor/Completer beim owning Modul registrieren und beim Disable/fehlgeschlagenen Enable zurücksetzen. `SettingsModule` zeigt dieses Binding; der Core-Root wird in `VapeeCore#registerCommands` verdrahtet.
4. Syntax, Player/Console, Permission und Zielautorität am Adapter prüfen. Für administrative Online-Ziele bestehenden `OnlineStaffTargetGuard` verwenden; keinen zweiten Hierarchiealgorithmus schaffen. Danach Service-Aufruf, erst nach Erfolg Erfolgsmeldung.
5. `CommandHelpRenderer` bzw. bestehenden kurzen Usage-Pfad, Tab Completion, EN/DE-Keys und [PERMISSIONS.md](PERMISSIONS.md) aktualisieren. Eingaben bleiben literale Components.
6. Descriptor- und Command-Harnesses für Denial, Console, Completion, stale/unverfügbare Ziele, Mutationserfolg und Save-Fehler erweitern. `PermissionDescriptorHarness` schützt die öffentlichen Metadaten bewusst strikt.

### C. Einen Config-Wert ergänzen

1. Passende Resource unter `src/main/resources/` ergänzen. `ConfigService`/`CoreConfigState` besitzt Core-Werte; Feature-Werte gehören dem vorhandenen Parser, z. B. `DailyQuestConfig`.
2. Typ, Bereich und fehlenden Legacy-Wert validieren; die bestehende Feature-Regel für Warnung/Fallback oder striktes Prepare-Failure beibehalten. Nur validierte Werte in den Runtime-Snapshot aufnehmen.
3. Bestehenden Reload-Plan um den vollständigen Snapshot erweitern. Neue Teilnehmer nur bei eigener Config-Verantwortung ausdrücklich in `VapeeCore` verdrahten. Vorherigen Snapshot für Rollback behalten.
4. Müssen bestehende Betreiberdateien einen neuen Default erhalten, betroffene Version in `ConfigEvolution.MANAGED_CONFIGS` und Resource bewusst erhöhen; kein manuelles Überschreiben im Parser.
5. Parser-, Evolution- und Reload-Tests mit fehlenden/ungültigen Werten, erfolgreichem Wechsel und fehlgeschlagenem Prepare/Apply ergänzen. `warps.yml`, Profile, Freunde, Clans und Tischdateien sind **Persistence**, keine Operator-Evolution.

### D. Ein Feature-Modul ergänzen

`CoreModule` im Feature-Package implementieren und die benötigten Abhängigkeiten im Composition Root ausdrücklich übergeben. Vor Registrierung prüfen:

- Startet jede benötigte Authority vorher? Wird nur eine Fähigkeit benötigt oder wirklich das gesamte Modul?
- Werden Services zuerst lokal aufgebaut und Runtime-Referenzen erst nach erfolgreicher Registrierung veröffentlicht?
- Werden Commands/Completer, Bukkit-Listener, Domain-Observer, Tasks und Views bei teilweisem Start sowie Disable freigegeben?
- Bleiben Disable und erneutes Enable sicher? Werden nur eigene aktive Ressourcen geschlossen?
- Sind Getter außerhalb der aktiven Lebensdauer geschützt? Sind Fehler bei einzelnen Cleanup-Schritten isoliert?
- Gibt es Lifecycle-Harnesses für normale Registrierung, erzwungenen Teilfehler, Shutdown und stale Zugriffe? `SettingsModuleLifecycleHarness` und `PresenceLifecycleHarness` führen echte Module gegen eine kontrollierte Paper-Grenze aus.

Der Manager räumt ausschließlich **erfolgreich gestartete** Vorgänger auf. Diese Pflicht darf nicht an ihn abgeschoben werden.

### E. Einen GUI-Button ändern

**Vorhandene unterstützte Action bewegen/einblenden:** Das owning `Menu.DEFAULT_LAYOUT` ändern. `MenuLayout` definiert Größe, Komposition und Positionen; bei listartigen Menüs bestimmen geordnete `contentSlots` die Seitenkapazität. Settings-Icon und Statuspane können unabhängig platziert werden. Zahlen im Layout sind Präsentation, kein globaler Funktionsvertrag.

**Neue Action:** Lokale `Action`, Item-Rendering im Menü und Dispatch im Listener ergänzen, den tatsächlichen Domain-Anwendungsfall beim Service belassen. Gegebenenfalls neues Holder-UUID-/ID-Mapping, EN/DE-Text und Tests hinzufügen. Ein Layout-Eintrag allein implementiert keine neue Aktion. Shared `ui/UiItemSpec` / `UiItems` rendern Items, `ui/Pagination` berechnet Seiten; sie kennen keine Friend-/Clan-/Quest-Regeln.

Holder-Besitzer, exakt gebundenes Inventory, Active-Registry, aktuelles Top-Inventory, Permission, geladenes Profil und Feature-/Activity-Guards vor der Aktion prüfen. Content-Ziele stammen aus dem aktuellen Slot→UUID/ID-Mapping, niemals aus Material, Lore oder Name. Nur tatsächlich gerenderte/zulässige Page-Actions sind aktiv. Seiten nach Änderungen klemmen und Mappings neu aufbauen. DailyQuest besitzt seine eigene adaptive Anordnung; nicht auf Listenmenüs übertragen.

Für Lobby-Hotbar-Items ist das ein anderer Pfad: Verhalten einmal unter stabiler Java-ID in `LobbyItemRegistrations` / `LobbyItemRegistry` registrieren, Präsentation über `LobbyItemsConfig` aus Live-`lobby.yml` lesen. Unbekannte YAML-IDs erzeugen keine Aktion. Ein bestehendes Item umbenennen bedeutet `name` ändern, nicht die Registry-ID.

### F. Persistente Daten ergänzen

Zuerst entscheiden, ob der Wert Teil eines `CorePlayer` oder eines eigenständig gespeicherten Features ist. Owner-State und fachliche Mutation dort ergänzen, dann Parser/Serialisierung/Schema des **einen** zuständigen Repositorys erweitern. Fehlende Legacy-Werte, ungültige und zukünftige Schemas, unbekannte Daten, Dateitypen und Wiederherstellung berücksichtigen. Die vorhandenen featureabhängigen Verträge unterscheiden sich; nicht alles wird über `ConfigEvolution` oder `SafeFileWriter` gespeichert.

An der vorhandenen Commit-Grenze Kandidat vollständig validieren, alte Datei schützen und erst nach erfolgreichem Save State publizieren; bei synchronen Mutationen Runtime-Rollback testen. Bei bewusst deferred State das vorhandene Dirty-/Retry-Verfahren erweitern. Tests müssen echte Schreib-/Replace-/Recovery-Fehler an isolierten Dateien abdecken. Keine zweite Repository-Instanz mit konkurrierendem Profilcache und keine GUI-eigene Save-Schleife.

## Guardrails und ihre tatsächliche Absicherung

| Regel | Bestehende Absicherung / Grenze |
|---|---|
| Domain-Service bleibt Mutationseigentümer | Service-/Rollback-Harnesses, z. B. `FriendServiceHarness`, `RewardServiceHarness`, `PlayerSettingsServiceHarness`; keine allgemeine statische Schichtanalyse |
| Öffentliche Rechte und Staff-Ziele bleiben geschützt | `PermissionDescriptorHarness`, Command-/Target-Guard-Harnesses; einige Wiring-/Verbotsprüfungen lesen weiterhin Source |
| Reload bereitet vor und nimmt fehlgeschlagene Applies zurück | `ReloadServiceHarness`, `DailyQuestLifecycleHarness`, `LocalizationReloadHarness` |
| Persistenz schützt den bisherigen Stand | `PlayerSchemaPersistenceHarness`, `PlayerQuestPersistenceHarness`, `LobbyPersistenceHarness` und Feature-Persistenz-Harnesses |
| Menüs schützen Owner/Inventory/Active-Registry/Zielidentität | Settings-/Friend-/Clan-/Navigator-/Quest-Security-Harnesses; `GuiAuthoringAuditHarness` schützt Layout-Entkopplung |
| Modulstart und Cleanup werden geprüft | Konkrete Settings-/Presence-Lifecycle-Tests, keine flächendeckende Garantie für alle Module |
| Kleine Abhängigkeiten und dünne Adapter bevorzugen | Review-Konvention; große vorhandene Commands/Repositories und Modul-zu-Modul-Abhängigkeiten bleiben technische Schulden |
| Mechanische Java-Formatierung reproduzierbar halten | Spotless-Check in Verify, ausschließlich die Pilot-Allowlist in `pom.xml`; keine Behauptung projektweiter Konformität |

Vor einer Änderung erst Owner und Commit-Grenze bestimmen, dann fokussiert testen, betroffene Pilotdateien bewusst formatieren und abschließend `pwsh -File ./scripts/verify.ps1` ausführen. Verify prüft Build, Pilot-Formatierung und vollständige Regression. Die vollständige Discovery und die aktuellen Zahlen stehen in `target/verification/harnesses/results.json`; Maven allein führt die eigenständigen Harnesses nicht aus.
