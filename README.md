# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt stellt aktuell ein kleines, erweiterbares Grundgerüst mit zentraler Konfiguration, MiniMessage-Nachrichten und einem internen Modul-Extension-Point bereit.

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

Größere Funktionen werden erst bei konkretem Bedarf als klar abgegrenzte Features ergänzt. Minigames können später als eigenständige Module oder separate Plugins entwickelt werden.
