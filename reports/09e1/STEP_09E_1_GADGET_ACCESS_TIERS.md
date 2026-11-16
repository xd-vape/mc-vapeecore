# Step 09E.1 Â· Gadget Access Tiers & Menu UX

Basis ist `master` auf `2add7b71e6dcab19e8c0a253d1666c9143e6adf5`.

## MenÃ¼s

Die sechs bisherigen EintrÃ¤ge behalten ihre PlÃ¤tze im 27-Slot-HauptmenÃ¼. Slot 22 zeigt die AusrÃ¼stungskapazitÃ¤t. Slot 24 zeigt fÃ¼r Berechtigte die Team-Kategorie. Im 27-Slot-Team-MenÃ¼ fÃ¼hrt das blau gefÃ¤rbte Lederstiefel-Icon zu Extreme Size mit Normal und Colossal. Die RÃ¼ckwege fÃ¼hren von Extreme Size zu Team Gadgets und weiter zum HauptmenÃ¼.

Der regulÃ¤re Size Changer nutzt 18 Slots. Tiny, Small, Normal, Tall und Giant stehen auf 1, 2, 4, 6 und 7 mit den bisherigen Faktoren. Huge (2Ã—) und Massive (3Ã—) stehen auf 11 und 15. Der ZurÃ¼ck-Pfeil steht auf 17. Aktive GrÃ¶ÃŸen sind hervorgehoben. Gesperrte Optionen sind markiert. Die GrÃ¶ÃŸenwahl benÃ¶tigt keinen Hotbar-Slot. EN/DE-Texte verwenden `MessageService` und die zentralen Farben. Standardmeldungen sind prÃ¤fixfrei. BetreiberprÃ¤fixe und Sprach-Overrides greifen weiterhin.

## Berechtigungen und Cleanup

`vapeecore.gadgets.use` schÃ¼tzt das HauptmenÃ¼. `vapeecore.gadgets.size-changer` gilt fÃ¼r alle GrÃ¶ÃŸenwahlen. ZusÃ¤tzlich prÃ¼ft Huge oder Massive `vapeecore.gadgets.size.vip`. `vapeecore.gadgets.team.use` Ã¶ffnet Team Gadgets und ist zusammen mit `vapeecore.gadgets.team.extreme-size` fÃ¼r Extreme Size und Colossal nÃ¶tig. Die drei neuen Nodes sind standardmÃ¤ÃŸig `false`. MaÃŸgeblich ist die effektive Berechtigung des Spielers.

Der vorhandene transiente `Attribute.SCALE`-Modifier trÃ¤gt alle GrÃ¶ÃŸen. Wechsel verwenden denselben SchlÃ¼ssel. Fremde Modifikatoren und der Basiswert werden erhalten. Rechteentzug fÃ¼r eine aktive SpezialgrÃ¶ÃŸe entfernt den eigenen Modifier beim nÃ¤chsten Bewegungs-, MenÃ¼- oder Hotbar-Abgleich. Der jeweilige Lifecycle-Cleanup lÃ¤uft unabhÃ¤ngig von Rechten. Normal bleibt im Haupt-Size-Changer fÃ¼r die Basisberechtigung verfÃ¼gbar. Die Team-Seiten prÃ¼fen die aktuelle Session und beide Enden der Navigation. Eine Team-Ansicht, deren Rechteentzug erkannt wurde, wird sofort fÃ¼r Klicks ungÃ¼ltig und im nÃ¤chsten Tick geschlossen.

## KollisionsprÃ¼fung

Huge, Massive und Colossal prÃ¼fen den resultierenden Wert aus der effektiven aktuellen SCALE und dem Austausch des eigenen Multiplikators. Aktuelle oder projizierte Werte an oder auÃŸerhalb der nativen Clamp-Grenzen (0,0625 bis 16) werden konservativ abgelehnt. Die PrÃ¼fung verwendet die Vanilla-Stand-Hitbox am aktuellen FuÃŸpunkt mit einem kleinen Rundungsspielraum. Geladene Chunks mÃ¼ssen die gesamte PrÃ¼fgeometrie abdecken. `Entity.wouldCollideUsing` prÃ¼ft BlÃ¶cke, kollidierende Entities und die Weltgrenze. Die GrÃ¶ÃŸenwahl wird bei einer Kollision abgelehnt. Das Plugin fÃ¼hrt keine Teleportation aus. Laufende Mounts, Flug und Glide verhindern die Auswahl. Colossal ist nach nativer PrÃ¼fung freigegeben. Bei unverÃ¤ndertem Basiswert maÃŸen die Server-Hitboxen 3 Ã— 9 BlÃ¶cke. [Paper-API fÃ¼r die PrÃ¼fung einer Ziel-Hitbox](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html#wouldCollideUsing(org.bukkit.util.BoundingBox))

Normal und der Lifecycle-Cleanup geben den eigenen Modifier frei. FÃ¼r spÃ¤tere, unabhÃ¤ngige Ã„nderungen an der Welt oder an fremden Attributen bleibt die Vanilla-Kollisionsbehandlung maÃŸgeblich.

## Verifikation

- Vollständiger Offline-Verifier mit 248/248 Harnesses, 41.567 Checks, 0 Fehlern und 0 fehlenden oder ungültigen Harnesses. Maven clean package und Spotless bestanden.
- GadgetsFoundationHarness. 317 bestandene Checks. PrÃ¼ft feste Slots, RollenunabhÃ¤ngigkeit der Rechte, Berechtigungsentzug, Schutz von Inventar und Navigation, fremde Modifier und bestehende Gadgets.
- Isolierte Paper-1.21.11-Abnahme mit LuckPerms. 35 bestandene Checks auf Build 132 und bytegleicher Kopie des Build-Artefakts. GeprÃ¼ft wurden reale MenÃ¼s und Stiefel-Metadaten, die drei standardmäßig deaktivierten Zusatzrechte, die 2Ã—-, 3Ã—- und 5Ã—-Hitboxen, Kollisionen mit Wand und Decke, die unverÃ¤nderte FuÃŸposition und der Erhalt fremder SCALE.
- 31 Schutzdateien, geschÃ¼tzter Worktree und bestehende Referenzen wurden mit dem Startzustand verglichen.

Die visuelle Minecraft-Client-Abnahme bleibt offen. Auszuprobieren sind die Darstellung aller GrÃ¶ÃŸen aus Eigen- und Fremdperspektive, das Verhalten in engen Laufwegen mit einer bereits angewandten GrÃ¶ÃŸe und die Darstellung der Team-MenÃ¼s.
