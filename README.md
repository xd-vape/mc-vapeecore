# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt stellt aktuell ein kleines, erweiterbares Grundgerüst mit zentraler Konfiguration, MiniMessage-Nachrichten, einem internen Modul-Extension-Point und einer lokalen Player Foundation bereit.

## Voraussetzungen

- Java 21
- Paper 1.21.11
- Maven 3

## Build

```bash
mvn clean package
```

Die fertige Plugin-JAR wird unter `target/vapeecore-1.0-SNAPSHOT.jar` erzeugt.

## Architektur

- `command`: Commands und deren Subcommands
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `listener`: schlanke Paper-Event-Listener
- `message`: Adventure- und MiniMessage-Ausgabe
- `module`: kleiner Lifecycle-Extension-Point für zukünftige Systeme
- `player`: Player-Domainmodell, aktiver Cache und Join-/Quit-Lifecycle
- `player.repository`: austauschbare Persistence mit lokaler YAML-Implementierung

Aktive Spieler werden als `CorePlayer` im Speicher gehalten. Die lokale Persistence legt pro UUID eine Datei unter `plugins/VapeeCore/players/<uuid>.yml` an. Bukkit-`Player`-Instanzen werden nicht im Domainmodell gespeichert.

Größere Funktionen werden erst bei konkretem Bedarf als klar abgegrenzte Features ergänzt. Minigames können später als eigenständige Module oder separate Plugins entwickelt werden.
