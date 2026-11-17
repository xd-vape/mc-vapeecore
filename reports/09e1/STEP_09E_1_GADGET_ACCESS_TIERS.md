# Step 09E.1 · Gadget Access Tiers & Menu UX

Basis ist `master` auf `2add7b71e6dcab19e8c0a253d1666c9143e6adf5`.

## Menüs

Die sechs bisherigen Einträge behalten ihre Plätze im 27-Slot-Hauptmenü. Slot 22 zeigt die Ausrüstungskapazität. Slot 24 zeigt für Berechtigte die Team-Kategorie. Im 27-Slot-Team-Menü führt das blau gefärbte Lederstiefel-Icon zu Extreme Size mit Normal und Colossal. Die Rückwege führen von Extreme Size zu Team Gadgets und weiter zum Hauptmenü.

Der reguläre Size Changer nutzt 18 Slots. Tiny, Small, Normal, Tall und Giant stehen auf 1, 2, 4, 6 und 7 mit den bisherigen Faktoren. Huge (2×) und Massive (3×) stehen auf 11 und 15. Der Zurück-Pfeil steht auf 17. Aktive Größen sind hervorgehoben. Gesperrte Optionen sind markiert. Die Größenwahl benötigt keinen Hotbar-Slot. EN/DE-Texte verwenden `MessageService` und die zentralen Farben. Standardmeldungen sind präfixfrei. Betreiberpräfixe und Sprach-Overrides greifen weiterhin.

## Berechtigungen und Cleanup

`vapeecore.gadgets.use` schützt das Hauptmenü. `vapeecore.gadgets.size-changer` gilt für alle Größenwahlen. Zusätzlich prüft Huge oder Massive `vapeecore.gadgets.size.vip`. `vapeecore.gadgets.team.use` öffnet Team Gadgets und ist zusammen mit `vapeecore.gadgets.team.extreme-size` für Extreme Size und Colossal nötig. Die drei neuen Nodes sind standardmäßig `false`. Maßgeblich ist die effektive Berechtigung des Spielers.

Der vorhandene transiente `Attribute.SCALE`-Modifier trägt alle Größen. Wechsel verwenden denselben Schlüssel. Fremde Modifikatoren und der Basiswert werden erhalten. Rechteentzug für eine aktive Spezialgröße entfernt den eigenen Modifier beim nächsten Bewegungs-, Menü- oder Hotbar-Abgleich. Der jeweilige Lifecycle-Cleanup läuft unabhängig von Rechten. Normal bleibt im Haupt-Size-Changer für die Basisberechtigung verfügbar. Die Team-Seiten prüfen die aktuelle Session und beide Enden der Navigation. Eine Team-Ansicht, deren Rechteentzug erkannt wurde, wird sofort für Klicks ungültig und im nächsten Tick geschlossen.

## Kollisionsprüfung

Huge, Massive und Colossal prüfen den resultierenden Wert aus der effektiven aktuellen SCALE und dem Austausch des eigenen Multiplikators. Aktuelle oder projizierte Werte an oder außerhalb der nativen Clamp-Grenzen (0,0625 bis 16) werden konservativ abgelehnt. Die Prüfung verwendet die Vanilla-Stand-Hitbox am aktuellen Fußpunkt mit einem kleinen Rundungsspielraum. Geladene Chunks müssen die gesamte Prüfgeometrie abdecken. `Entity.wouldCollideUsing` prüft Blöcke, kollidierende Entities und die Weltgrenze. Die Größenwahl wird bei einer Kollision abgelehnt. Das Plugin führt keine Teleportation aus. Laufende Mounts, Flug und Glide verhindern die Auswahl. Colossal ist nach nativer Prüfung freigegeben. Bei unverändertem Basiswert maßen die Server-Hitboxen 3 × 9 Blöcke. [Paper-API für die Prüfung einer Ziel-Hitbox](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html#wouldCollideUsing(org.bukkit.util.BoundingBox))

Normal und der Lifecycle-Cleanup geben den eigenen Modifier frei. Für spätere, unabhängige Änderungen an der Welt oder an fremden Attributen bleibt die Vanilla-Kollisionsbehandlung maßgeblich.

## Verifikation

- Vollständiger Offline-Verifier mit 248/248 Harnesses, 41.567 Checks, 0 Fehlern und 0 fehlenden oder ungültigen Harnesses. Maven clean package und Spotless bestanden.
- GadgetsFoundationHarness. 317 bestandene Checks. Prüft feste Slots, Rollenunabhängigkeit der Rechte, Berechtigungsentzug, Schutz von Inventar und Navigation, fremde Modifier und bestehende Gadgets.
- Isolierte Paper-1.21.11-Abnahme mit LuckPerms. 35 bestandene Checks auf Build 132 und bytegleicher Kopie des Build-Artefakts. Geprüft wurden reale Menüs und Stiefel-Metadaten, die drei standardmäßig deaktivierten Zusatzrechte, die 2×-, 3×- und 5×-Hitboxen, Kollisionen mit Wand und Decke, die unveränderte Fußposition und der Erhalt fremder SCALE.
- 31 Schutzdateien, geschützter Worktree und bestehende Referenzen wurden mit dem Startzustand verglichen.

Die visuelle Minecraft-Client-Abnahme bleibt offen. Auszuprobieren sind die Darstellung aller Größen aus Eigen- und Fremdperspektive, das Verhalten in engen Laufwegen mit einer bereits angewandten Größe und die Darstellung der Team-Menüs.
