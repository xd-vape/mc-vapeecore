# Step 08F.3 · Persönliches Daily-Quest-Hologramm

## Verhalten

Der bestehende Quest-NPC zeigt standardmäßig zwei zentrierte Zeilen. Englisch erscheint als „Daily Quests“ und beispielsweise „0/3 Completed“, Deutsch als „Tagesquests“ und „0/3 abgeschlossen“. Der Titel ist goldfarben. Die graue Fortschrittszeile verwendet die neutrale Textfarbe des Nachrichtensystems. Das Display hat einen transparenten Hintergrund, Textschatten und richtet sich zum Betrachter aus. Bei aktivem Hologramm ist das Namensschild des Mannequins ausgeblendet. Bei deaktiviertem Hologramm erscheint der konfigurierte Name wieder. Skin-Auflösung, NPC-Schutz und das bestehende Daily-Quest-Menü beim Rechtsklick bleiben erhalten.

Der Nenner stammt aus denselben aktuellen Questansichten wie im Daily-Quest-Menü. Der Zähler umfasst ausschließlich den Status `COMPLETED`. Eine Quest mit `REWARD_PENDING` zählt nach erfolgreicher Nachzahlung. Ohne geladenes Profil, aktuelle Zuweisungen oder aktivierte Daily Quests erscheint kein Zähler.

## Betrieb und Konfiguration

Alte Quest-NPC-Dateien bleiben gültig. Der optionale Abschnitt in `quest-npc.yml` kann die folgenden Standardwerte für Schalter, Höhe und Farben explizit setzen.

~~~yaml
hologram:
  enabled: true
  height-offset: 2.45
  title-color: gold
  progress-color: gray
~~~

Die Höhe wird in Blöcken ab Fußposition des NPCs gemessen. Die optionalen Schlüssel `title` und `progress-format` überschreiben die Übersetzung für alle Betrachter. Beide Werte sind einzeiliger Text. Das Fortschrittsformat benötigt die Platzhalter `<completed>` und `<total>`. Beide Farben können auch als Hexwerte in Anführungszeichen angegeben werden. Adminbefehle übernehmen den Hologrammabschnitt beim Speichern. Die bestehende sichere Konfigurationsverwaltung validiert die Einstellungen beim Laden und bei Reload.

## Zuständigkeit und Lebenszyklus

Pro Sitzung, für die Paper den Quest-NPC trackt, besitzt der NPC höchstens ein nicht persistentes `TextDisplay`. Das Display ist bereits beim Spawn standardmäßig unsichtbar. Ausschließlich der zugehörige aktuelle Spieler erhält die Sichtbarkeitsfreigabe über die Paper-API.

Join, Weltwechsel, Tracking, Chunk- und NPC-Lebenszyklus sowie die vorhandenen Quest-Observer lösen einen zusammengefassten Abgleich aus. Änderungen durch Initialzuweisung, Reset, Force, Abschluss und erfolgreiche Nachzahlung werden dabei aus dem aktuellen QuestService gelesen. Entity-Erzeugung und -Entfernung erfolgen außerhalb der Paper-Tracking-Events. Bei Logout, Weltwechsel, Entladen oder Entfernen des NPCs wird die Anzeige ausgeräumt. Reload und Disable invalidieren alte Callback-Generationen. Scheitert die Entity-Entfernung, bleibt der Eintrag in Besitz des Controllers und wird bei einem späteren Abgleich erneut entfernt. So kann derselben Sitzung kein zweites Display zugewiesen werden.

- [Paper PlayerTrackEntityEvent](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/PlayerTrackEntityEvent.html)
- [Paper PlayerUntrackEntityEvent](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/PlayerUntrackEntityEvent.html)
- [Paper TextDisplay](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/TextDisplay.html)

## Verifikation

Der erweiterte `QuestNpcHarness` prüft getrennte Texte, Farben und Eigentümer, Initialzuweisung, echte Statusübergänge, Nachzahlungen, Force und Tagesrotation, Reconnect mit derselben UUID, Welt- und Chunk-Wechsel, Reload, Konfigurationswerte, Entfernen sowie injizierte Fehler. Bei Anzeigefehlern vergleicht er die Questansichten, den Coin-Stand und die gespeicherten Spielerdateibytes. Bestehende NPC- und Daily-Quest-Lifecycle-Prüfungen bleiben im Verifier enthalten.

Der vollständige Lauf von `pwsh -NoProfile -File .\scripts\verify.ps1` mit Java 21 bestand alle 247 Harnesses mit 40.988 Checks, null Fehlern und null fehlenden oder ungültigen Harnesses. Maven `clean package` und Spotless bestanden. Der `QuestNpcHarness` bestand 50 Checks. Zwei vorangegangene Build-Versuche meldeten Java-Aufrufstellenfehler beim Eingrenzen des Formatierungsdiffs. Die Zahlen stammen aus dem anschließenden vollständigen Lauf mit den korrigierten Aufrufen.

Die isolierte Instanz mit Paper 1.21.11 Build 132 und LuckPerms 5.5.84 bestand 67 native Prüfungen. Zwei kontrollierte serverseitige Spielersitzungen nutzten Papers tatsächlichen Entity-Tracker. Nach der Zuweisung und zwei Abschlüssen sah die erste Sitzung `0/3 Completed`, die zweite `2/3 Completed`. Für jede Sitzung wurden `canSee`, die Trackerliste sowie eigene und fremde Spawn- und Metadatenpakete geprüft. Untracking, Wiederannäherung, Reload, tatsächliches Chunk-Unload und erneutes Laden sowie NPC-Entfernung hinterließen keine doppelten oder verwaisten Anzeigen. Paper behielt beim schnellen Chunk-Wechsel dieselbe Mannequin-Instanz. Das vorhandene Menü öffnete sich im nativen Test über die Haupthand.

Die visuelle Abnahme im echten Minecraft-Client bleibt offen. Zu prüfen sind Höhe, Zeilenabstand, Schatten, Farben, Lesbarkeit aus mehreren Blickwinkeln und das fehlende Namensschild beim Anvisieren. Die native Probe hat serverseitige Sichtbarkeit und versendete Pakete geprüft.

## Schutzprüfung

Der native Test lief auf einer eigenen Loopback-Instanz mit separatem Welt- und Spielerverzeichnis. 31 Schutzdateien wurden per SHA-256 mit dem Stand vor Step 08F.3 verglichen, darunter beide Run-Konfigurationen, die Receipts, die Operator-JAR und historische Schutzbelege. Die frühere Operator-JAR-Abweichung war bereits vor diesem Step vorhanden. Der geschützte Worktree und die bestehenden Branch-Referenzen wurden separat geprüft.
