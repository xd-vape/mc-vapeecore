# Step 09B · Classic Player Riding

Basis ist `master` auf `96110da462a50dabc547fc8914f670a8c642fc79`.

## Bedienung

Linksklick auf **Player Riding** im Gadgets-Menü schaltet das Gadget um. Der bestehende Gadgets-Slot zeigt den verwalteten Sattel anstelle der konfigurierten Kiste. Sneak plus Rechtsklick ins Leere öffnet das Menü mit aktivem Sattel. Bei der Deaktivierung kehrt die konfigurierte Darstellung zurück. Die übrigen fünf Kategorien bleiben inaktiv.

- Rechtsklick auf einen sneakenden Spieler nimmt ihn auf.
- Linksklick auf einen sneakenden Spieler setzt den Sattelbesitzer auf ihn.
- Ein neuer Sneak-Tastendruck des Reiters beendet die Verbindung. Wer bei der Zustimmung bereits Sneak hält, lässt die Taste zuerst los.
- Linksklick ins Leere löst den getragenen Spieler und gibt ihm in Blickrichtung einen horizontalen Impuls von 0,75 und eine Aufwärtskomponente von 0,32.

## Implementierung

Das Gadget nutzt die vorhandene Hotbar-Registry und ihre PDC- und Inventarschutzregeln. Der Sattel benötigt zusätzlich die zufällige Aktivierungskennung des Spielers, die Haupthand und den aufgelösten Gadgets-Slot. Aktivieren setzt voraus, dass dieser Slot bereits das Gadgets-Item enthält. Es gibt keine zusätzlichen konfigurierten Plätze oder Spielerprofileinträge.

Paper verarbeitet den Linksklick vor dem Angriff. Das Gadget bricht den Angriff ab und verwaltet ausschließlich die selbst erzeugte Träger-Reiter-Verbindung. Beide Teilnehmer müssen für die normale Lobby berechtigt und gegenseitig sichtbar sein. Ignore, fremde Mounts, Passagiere, Glide, Flug und weitere inkompatible Zustände blockieren den Einstieg. Ein Sneak-Flag aus der Zustimmung wird nach dem Mount bereinigt, weil Vanilla es sonst beim nächsten Ride-Tick zum Absteigen verwendet. Der Dismount reagiert auf einen neuen Sneak-Eingang. Beim Entfernen prüft das Gadget beide Enden der konkreten Verbindung und entfernt keinen fremden Passagier. [Paper-Angriff und Ride-Tick](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/patches/sources/net/minecraft/world/entity/player/Player.java.patch) · [Paper-Passagiermethoden](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java#L468-L485)

Logout, Kick, Weltwechsel, Teleport, Respawn, Tod, Lobby-Zustandswechsel, Activity, Flugwechsel und ein externer Dismount lösen die Verbindung. Der Lobby-Reload und Modul-Disable setzen alle Sitzungen zurück. Papier-Events und bestehende Listener liefern die Übergänge ohne eigenen Scheduler. Zur Einrichtung genügt der bestehende Gadgets-Eintrag aus Step 09A.

## Abnahme

Der Gadgets-Harness bestand mit 74 Checks, darunter Aktivierung und Wiederherstellung im konfigurierten Slot, doppelte Events, fehlende Zustimmung, geschützte Inventare, falsche Sattelkennung, Ignore, fremde Mounts sowie Weltwechsel und Reload mit aktiver Verbindung. Die gezielten Regressionen für Spielerkopf, Sichtbarkeitsumschalter und Glide bestanden ebenfalls.

Die isolierte Paper-1.21.11-Instanz mit LuckPerms bestand 13 Checks mit dem verifizierten Build-Artefakt. Die Probe nutzte kontrollierte CraftPlayer-Sitzungen, den tatsächlichen Paper-Angriff, den Vanilla-Ride-Tick und den Paper-Eingabepfad für Sneak. Inventarklick, Entity-Rechtsklick und Luftklick wurden über Bukkit-Ereignisse ausgelöst. Im ersten Lauf setzte die Wurfprüfung fälschlich eine feste Blickrichtung voraus. Mit der tatsächlichen Blickrichtung bestanden Impuls und Schadensprüfung. Die Minecraft-Client-Abnahme für Klickgesten, Darstellung des Reiters und sichtbare Flugbahn bleibt offen.

Der vollständige Offline-Verifier bestand mit 247 von 247 Harnesses und 41.060 Checks. Maven und Spotless bestanden. Es gab keine fehlenden, ungültigen oder fehlgeschlagenen Harnesses.
