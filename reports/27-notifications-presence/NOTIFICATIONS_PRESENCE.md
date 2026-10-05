# Phase 27 – Notifications & Presence

## Scope

Phase 27 ergänzt genau ein `PresenceModule` und eine opt-in Friend-Presence-Benachrichtigung. Der bestehende Lobby-Owner für globale Join-/Quit-Meldungen bleibt unverändert. Es gibt keine AFK-Erkennung, Cross-Server-Presence, Offline-Queue, Permission, Command, neue Configdatei, neue Friends-Datei, Schema-Version oder Reload-Teilnahme.

Der Modulstand ist 27, die Zahl der Reload-Teilnehmer bleibt 6 und `plugin.yml` behält 36 Root-Commands sowie den vorhandenen Permission-Baum.

## Runtime ownership

`PresenceService` besitzt ein main-thread-geführtes `HashSet<UUID>`. `markOnline` und `markOffline` geben nur bei einem tatsächlichen Zustandswechsel `true` zurück; doppelte Events erzeugen deshalb keine zweite Benachrichtigung. `getOnlinePlayers` liefert eine defensive unveränderliche Kopie. Der Service kennt weder Bukkit noch Player-Persistence und wird beim Disable geleert.

`PresenceModule` startet in der Reihenfolge Friend → Presence → Clan. Es konsumiert Player, Social, Friend und Message, veröffentlicht seinen Getter erst nach vollständigem Enable und ist bei Listener-/Seed-Fehlern rollback-sicher. Bereits beim Enable online befindliche Player werden nur übernommen, wenn sie wirklich online und im `PlayerService` geladen sind; der Seed benachrichtigt niemanden. Rückwärts wird Presence vor Friend, Social und Player deaktiviert.

## Event flow

- Join: `MONITOR`; zuerst muss `PlayerService#isLoaded` wahr sein, dann wird Presence gesetzt und nur beim ersten Übergang gemeldet.
- Quit: `LOWEST`; Presence und Meldung laufen, solange Player-Settings und Social-Snapshot noch verfügbar sind.
- World Change, Respawn und Teleport sind keine Presence-Events.
- `PlayerJoinEvent#joinMessage` und `PlayerQuitEvent#quitMessage` bleiben ausschließlich beim vorhandenen Lobby-Experience-Flow.

Ein unerwarteter Notification-Fehler entfernt oder invertiert den bereits erfassten Übergang nicht.

## Recipient policy

`FriendPresenceNotifier` verwendet ausschließlich die aktuelle akzeptierte Liste aus `FriendService#getFriends(subject)`. Für jeden Eintrag gelten folgende Gates:

1. `Server#getPlayer(UUID)` liefert einen Player und `isOnline()` ist wahr.
2. Der Empfänger ist geladen und `settings.friend-presence-notifications` ist ausdrücklich `true`.
3. `SocialService` meldet in keiner der beiden Richtungen eine Ignore-Beziehung.

Die Reihenfolge des FriendService wird beibehalten. Es gibt keine Offline-Zustellung und keine zweite Empfängerliste. Ein Fehler beim initialen Friend-Lookup wird mit Subject-UUID gewarnt. Ein Lookup-, Settings-, Social- oder Sendefehler für einen Empfänger wird mit Subject- und Recipient-UUID gewarnt; die übrigen Freunde werden weiterhin geprüft.

Die literal Adventure-Body-Texte lauten:

- `Friend Alice is now online.`
- `Friend Alice went offline.`

`Friend ` ist grau, der Spielername aqua, der Online-Suffix grün und der Offline-Suffix grau. Namen werden nie als MiniMessage geparst. `MessageService` ergänzt den normalen Serverprefix; ein zusätzlicher Sound wird nicht abgespielt.

## Player setting and GUI

`PlayerSettings.friendPresenceNotificationsEnabled` startet als bewusstes Opt-in mit `false`. `PlayerSettingsService` bietet einen loaded-only `Optional<Boolean>`-Read und einen Setter über die bestehende Ganzprofil-Save-/Rollback-Grenze.

`FilePlayerRepository` schreibt `settings.friend-presence-notifications`. Alte Dateien ohne Key bleiben gültig und laden `false`; ein ungültiger Typ erzeugt eine Warnung und fällt ebenfalls auf `false` zurück. Beim Laden findet kein Eager-Rewrite statt.

Das bestehende 54-Slot-Settings-Root erhält:

| Feature | Material | Icon | Status |
|---|---|---:|---:|
| Friend Presence | `BELL` | 33 | 42 |

Icon und Status toggeln denselben Wert. Default ist ein rotes `Disabled`, aktiv ist grün `Enabled`. Die vorhandenen Owner-/Holder-/Inventory-Grenzen, Click-Cancellation, Save-Fehlerbehandlung, UI-Refreshes und das normale Settings-Soundverhalten bleiben erhalten.

## Verification contract

Die Phase wird durch die neuen Harnesses `PresenceServiceHarness`, `FriendPresenceNotifierHarness` und `PresenceLifecycleHarness` sowie erweiterte Settings-, Persistence-, GUI-, Lifecycle- und Descriptor-Harnesses abgedeckt. Sie prüfen Transition-Deduplizierung, immutable Snapshots, Opt-in-/Online-/Ignore-Filter, deterministische Reihenfolge, exakte Texte, Fehlerisolation, Event-Prioritäten, Seed-/Cleanup-Wiring, Legacy-/Invalid-Fallback, Save-Rollback, beide GUI-Slots und 27 Module bei unverändert sechs Reload-Teilnehmern.

Final gemessen wurden 93/93 separat gestartete Harnesses mit 14.098 Checks und 0 Fehlern. `mvn clean package` war erfolgreich. Der reale Smoke lief auf Java 21.0.12.1, Paper 1.21.11 Build 132 und LuckPerms 5.5.84: 27 Module wurden in korrekter Reihenfolge aktiviert, `/core` meldete Running/27/LuckPerms Connected, `/core reload` bereitete und aktivierte exakt sechs Teilnehmer, Profile/Friend/Clan-Commands waren registriert und der Shutdown deaktivierte alle 27 Module sauber rückwärts einschließlich Presence vor Friend. Live client test: not performed.
