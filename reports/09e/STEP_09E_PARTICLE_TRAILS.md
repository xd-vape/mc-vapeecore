# Step 09E · Particle Trails

Basis ist `master` auf `e1f8fac6d68748deb371a3bf4a1939fb96dc23f0`.

## Bedienung

Der Eintrag „Partikelspuren“ im Gadgets-Menü öffnet eine eigene geschützte 27-Slot-Ansicht. Zur Auswahl stehen Herzen, Flamme, Wolke, Endstab, Fröhlicher Dorfbewohner und Keine. Das Menü hebt die aktuelle Wahl hervor. Die Zurück-Schaltfläche führt ins Gadgets-Hauptmenü. Die EN/DE-Texte nutzen `MessageService` und die zentrale Akzentfarbe. Betreiberpräfixe und Sprach-Overrides bleiben wirksam. Das Gadget benötigt `vapeecore.gadgets.use` und `vapeecore.gadgets.trails`, beide standardmäßig erlaubt. Es belegt keinen Hotbar-Slot und verbraucht keine Ausrüstungskapazität.

## Bewegung und Sitzung

Der Listener verarbeitet gültige `PlayerMoveEvent`-Bewegungen auf dem Serverthread. Eine Bewegung braucht mindestens 0,08 Blöcke horizontale Strecke und darf insgesamt höchstens 1,5 Blöcke umfassen. Bei einer gültigen Bewegung erscheinen zwei Partikel knapp über der vorherigen Fußposition. Die erste Emission wartet vier Serverticks ab. Ein Effektwechsel während derselben Sitzung behält die Tick-Begrenzung bei. Stillstand, Kopfdrehungen, große Positionssprünge und abgebrochene Ereignisse bleiben ohne Emission. Reiten, Tragen, Fliegen und Gleiten pausieren die gewählte Spur. Partikelspuren verwenden keine periodischen Tasks, Marker oder gespeicherten Bewegungspfade.

Keine, Rechteentzug, Logout, Kick, Teleport, Weltwechsel, Tod, Respawn, BUILD, Activity, Lobby-Reload, Rollback und Disable geben die Sitzung mit ihrem Tick-Stand frei. Ein Sitzungswechsel übernimmt keine alte Auswahl. Glow, Size Changer und Riding behalten ihre eigenen Zustände.

## Empfänger und Sichtbarkeit

Der Besitzer sieht seine eigene Spur. Für andere Spieler liefert die räumliche Paper-Abfrage Kandidaten. Die Emission prüft deren aktuelle Welt, den normalen Lobbyzustand mit geladenem Profil, die tatsächliche Entfernung von höchstens 32 Blöcken und `VisibilityService.canSee(viewer, source)`. Der Dienst berücksichtigt die aktuelle Einstellung des Betrachters, Ignore in beiden Richtungen und Paper-Hides. Schlägt eine Prüfung für einen Empfänger fehl, fällt dieser aus der Liste. Die Entscheidung wird synchron pro Emission getroffen.

`World.spawnParticle` erhält die explizite Empfängerliste, den Trail-Besitzer als Quellspieler und `force=false`. Paper 1.21.11 Build 132 beschränkt den Versand auf die Liste und prüft zusätzlich für den Quellspieler `CraftPlayer.canSee`. Der Client kann die Anzeige über seine Partikeleinstellungen weiter begrenzen. [Paper-API für den Versand an Empfänger und mit Quellspieler](https://jd.papermc.io/paper/1.21.11/org/bukkit/World.html)

## Verifikation

- Vollständiger Offline-Verifier, Maven `clean package` und Spotless. 248/248 Harnesses, 41.491 Checks, 0 Fehler und 0 fehlende oder ungültige Harnesses. Maven und Spotless bestanden.
- GadgetsFoundationHarness. 288 bestandene Checks. Enthält Menü, alle fünf Effekte, Tick-Begrenzung, Empfängerselektion, Sichtbarkeitswechsel, Ignore, Rechte und Lifecycle sowie die gleichzeitige Nutzung vorhandener Gadgets.
- Isolierte native Paper-1.21.11-Abnahme. 22 bestandene Checks auf Build 132 mit LuckPerms und bytegleicher Kopie des finalen Artefakts. Kontrollierte Verbindungen erfassten echte Paper-Partikelpakete für Besitzer und sichtbare Empfänger. Geprüft wurden außerdem Reichweite, Hidden/Ignore, die sofortige Wirksamkeit der Sichtbarkeitseinstellung vor dem Bukkit-Refresh, die zusätzliche Paper-Sichtbarkeitsprüfung, Paketgröße und Teleport-Cleanup.
- Die 31 Schutzdateien und der geschützte Worktree wurden gegen den Startzustand geprüft. Der SHA-256 der Operator-JAR wich bereits vor Step 09E vom historischen Step-09D-Beleg ab. Für 09E gilt der vor Arbeitsbeginn gemessene Hash.

Die Minecraft-Client-Abnahme für das Erscheinungsbild der fünf Spuren, die Sichtbarkeit bei zwei echten Clients und unterschiedliche Client-Partikeleinstellungen bleibt offen.
