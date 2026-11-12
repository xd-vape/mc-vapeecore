# Step 08F.6 · Sichtbarkeitsinteraktionen

Basis ist `master` auf `81c655a71a12077519529b6993f14cb850c67e42`.

Der verwaltete Spieler-Kopf nutzt in der Haupthand Linksklick auf einen Spieler, um dessen UUID der vorhandenen Sichtbarkeitsliste hinzuzufügen oder aus ihr zu entfernen. Papers `PrePlayerAttackEntityEvent` wird vor der Schadensberechnung abgebrochen. Bereits abgebrochene Angriffe, unverwaltete Items, fehlende Settings-Berechtigung und unzulässige Lobby- oder Spielerzustände ändern die Liste nicht. Die bestehende Persistenz und die anschließende Visibility-Aktualisierung bleiben zuständig. Die Liste wirkt über den bereits vorhandenen Filter für hinzugefügte Spieler. [Paper-API](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/PrePlayerAttackEntityEvent.html)

Ein Linksklick ins Leere zeigt den Bedienungshinweis in der Spielersprache. Rechtsklick öffnet das Spielermenü. Zwei Paper-Ereignisse für denselben Rechtsklick auf einen Spieler ersetzen das bereits geöffnete Menü nicht. Der Sichtbarkeitsumschalter verwendet eine interne Sperre von drei Serverticks. Bukkit-Item-Cooldowns entfallen. Beim Abschalten der LobbyExperience wird das auslaufende Aktionsobjekt für verzögerte Aufrufe gesperrt.

## Betrieb

Die mitgelieferte Lore verwendet jetzt Linksklick. Konfigurationen mit dem vorhandenen Übersetzungsschlüssel übernehmen die aktualisierte Lore beim Laden. Bei selbst hinterlegter Lore kann der Betreiber den Bedienungstext freiwillig anpassen. Eine Datenmigration ist nicht erforderlich.

## Verifikation

Der erste Harness- und Paper-Lauf entdeckte, dass der Registry-Adapter den Linksklick ins Leere noch filterte. Der Adapter akzeptiert diese Geste jetzt ausdrücklich. Die vier fokussierten Harnesses bestanden anschließend separat.

Der fokussierte Test für die Drei-Tick-Entprellung prüft die geplante Freigabe, doppelte Block-Events, den vollständigen Moduszyklus, ausstehende Aktionen nach Deaktivierung und das Ausbleiben eines Bukkit-Item-Cooldowns. Er bestand mit 29 Checks und schlug gegen die vorherige Zehn-Tick-Version erwartungsgemäß fehl. Die zugehörigen Modul- und Lobby-Lifecycle-Harnesses bestanden mit 22 und 548 Checks.

Der vollständige Lauf mit `pwsh -NoProfile -File .\scripts\verify.ps1 -Offline` bestand mit 246 von 246 Harnesses und 40.885 Checks. Maven und Spotless bestanden. Es gab keine Fehler, fehlenden oder ungültigen Harnesses.

Vor der Verkürzung der Entprellung bestanden elf native Checks auf einer isolierten Paper-1.21.11-Instanz. Der native Angriffspfad fügte die Ziel-UUID hinzu und entfernte sie beim zweiten Angriff wieder. Die Gesundheit des Ziels blieb gleich. Ausgelöste Bukkit-Interaktionsereignisse bestätigten den Hinweis, ein einziges gebundenes Spielermenü bei zwei Rechtsklick-Ereignissen, die Entprellung und das Ausbleiben eines Client-Cooldown-Pakets. Der Test verwendete kontrollierte Spielersitzungen ohne Minecraft-Client.

Die Schutzprüfung erfasste 31 Dateien per SHA-256 und den separaten Worktree. Die Operator-Konfiguration, Run-Konfigurationen, Receipts und Operator-JAR blieben erhalten. Der native Test verwendete seine eigene Welt und eigene Spielerprofile.
