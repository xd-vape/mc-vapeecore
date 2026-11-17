# Step 09E.2 · Manuelle Gadget-Client-Abnahme

**Gesamtstatus** PENDING · 14 von 14 Fällen offen.

## Prüfumgebung und Protokoll

- Separater Paper-1.21.11-Testserver mit LuckPerms, Java gemäß Repository und frischen Testwelten sowie Testprofilen. Zwei echte Minecraft-1.21.11-Clients mit verschiedenen Konten A und B verwenden. Beide Konten spielen ohne OP. Admin-Eingriffe erfolgen an der Konsole der isolierten Instanz.
- VapeeCore-Artefakt aus dem integrierten Gameplay-Stand `139b8c8dacba4eacd31c724fa6c3360190c18336` oder aus dem aktuellen Stand dieses reinen Dokumentationsschritts verwenden. Quelle und SHA-256 der tatsächlich geladenen JAR erfassen. Versionsnummern von Paper, Java und LuckPerms, Clientversionen, Datum und Prüfer ergänzen.
- Eine freie Lobbyfläche mit mindestens 12 × 12 × 16 Blöcken, einen zweiten Testwelt-Zielpunkt sowie markierte Wand-, Decken- und Engstellen vorbereiten. Einen ungefährlichen Auslauf für Würfe freihalten. Die Gadgets-Kiste in der isolierten `lobby.yml` aktivieren. Für die Standardprüfung Slot 4 und das Standardlimit 3 verwenden. Slotangaben sind nullbasiert.
- Ausgangsberechtigungen beider Konten und wirksame LuckPerms-Kontexte festhalten. Für den Standardnutzer müssen `vapeecore.gadgets.use` und die jeweiligen Gadget-Basisrechte wirksam sein. Sonderrechte nur für den jeweiligen Fall schalten. Zwischen Fällen Größe auf Normal setzen, Glow und Trails ausschalten, Riding lösen und bei Bedarf abrüsten. Sichtbarkeit, Ignore, Testteams und Rechte auf den jeweiligen Ausgangszustand zurückstellen.
- Bei jedem Durchlauf beide Perspektiven prüfen, soweit angegeben. `PASS` setzt die vollständige Beobachtung des erwarteten Verhaltens voraus. Bei `FAIL` das Konto, den genauen Schritt, Soll/Ist und die Reproduzierbarkeit unter Abweichungen eintragen. Ausgelassene Schritte bleiben `PENDING`. Belege optional als Link oder Dateipfad mit Perspektive und Zeitmarke ergänzen.
- Dieser Schritt prüft Markdown-Diffs und UTF-8. Maven, Verifier und native Paper-Prüfungen werden für die beiden Dokumentationsänderungen nicht neu ausgeführt. Die historischen Harness- und Paper-Nachweise der Gadget-Berichte sind separat von der Client-Abnahme.

| Feld | Eintrag beim Testlauf |
| --- | --- |
| Datum und Prüfer | — |
| Paper-Build, Java, LuckPerms | — |
| VapeeCore-Commit, JAR-Quelle und SHA-256 | — |
| Client A und B, Version, Sprache, Partikeleinstellung | — |
| Testwelten, Messpunkte und Testkonfiguration | — |

## QA-01 · Gadgets-Kiste und Hotbar

- **Voraussetzungen** A in der Lobby mit aktivierter Gadgets-Kiste, Riding-Recht und mindestens einem freien, unreservierten Hotbar-Slot. Normales Testitem in einem anderen Slot.
- **Durchführung** Goldene Kiste auf Slot 4 prüfen und rechtsklicken. Player Riding ausrüsten, Position von Kiste und Sattel sowie Status auf Slot 22 notieren. Für beide verwalteten Items Drop, Offhand-Swap, Verschieben im Inventar und Ziehen testen. Riding wieder ablegen.
- **Erwartet** Das 27-Slot-Menü öffnet sich. Die Kiste bleibt als `CHEST` namens `Gadgets` in Gold auf Slot 4. Der Sattel nutzt einen separaten freien Slot, die Kapazität wechselt von 0/3 auf 1/3 und beim Ablegen zurück. Verwaltete Items werden weder verschoben noch dupliziert. Das normale Testitem bleibt erhalten.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-02 · Player-Riding-Gesten

- **Voraussetzungen** A hat den verwalteten Sattel ausgerüstet und ausgewählt. B steht sichtbar und ohne Ignore, andere Mounts oder Flug in Reichweite. Beide sind im Survival-kompatiblen Lobbyzustand. Auslauf freihalten.
- **Durchführung** A versucht Sneak und Rechtsklick in die Luft mit ausgewähltem Sattel, wechselt danach zur Kiste, öffnet damit das Menü und wählt nach dem Schließen erneut den Sattel. B sneakt, A nimmt B per Rechtsklick auf. B löst Sneak kurz und sneakt als Reiter erneut zum Absteigen. Aufnahme wiederholen und mit Linksklick in die Luft werfen. Danach lässt B Sneak los und beginnt erneut zu sneaken, damit A per Linksklick auf B aufsitzt. A sneakt zum Absteigen. Beide Klickgesten auch an einem stehenden, nicht sneakenden B versuchen. Herzen und Sitzposition aus zwei Perspektiven beobachten.
- **Erwartet** Das Menü öffnet sich über die Kiste. Der frühere Sneak-Rechtsklick-Shortcut am Sattel bleibt wirkungslos. Beide Montagerichtungen, das Absteigen und der sichtbare Wurf funktionieren. Der Wurf löst die Verbindung. Nicht sneakende Ziele werden abgelehnt. Kein Angriffsschaden durch die Interaktion, keine doppelte Verbindung und kein verbleibender Reiter.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-03 · Standard-Size-Changer

- **Voraussetzungen** A hat die Gadgets- und Size-Changer-Basisrechte. Standard-SCALE ohne fremde Effekte. B beobachtet von der Seite. Kiste und gegebenenfalls separat ausgerüsteten Sattel protokollieren.
- **Durchführung** Size Changer im Hauptmenü öffnen und Tiny 0,5×, Small 0,75×, Normal 1×, Tall 1,25× und Giant 1,5× einzeln auswählen. Markierung und Modellgröße nach jeder Wahl vergleichen. Über Slot 17 zurückgehen, erneut öffnen und zuletzt Normal wählen.
- **Erwartet** Das Untermenü hat 18 Slots. Die fünf Standardoptionen stehen auf 1, 2, 4, 6 und 7. Die jeweils aktive Wahl wird angezeigt. Zurück führt ins Hauptmenü, Normal stellt die Ausgangsgröße her und Hotbar sowie Ausrüstungskapazität behalten ihren vorherigen Zustand.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-04 · VIP-Größen Huge und Massive

- **Voraussetzungen** A hat die beiden Basisrechte für Gadgets und Size Changer. `vapeecore.gadgets.size.vip` ist zunächst effektiv verweigert. Genug Freiraum, B beobachtet.
- **Durchführung** Huge auf Slot 11 und Massive auf Slot 15 ohne Sonderrecht anklicken. Isoliert nur `vapeecore.gadgets.size.vip` freigeben. Menü neu öffnen, Huge 2×, Massive 3× und Normal wählen. Auswahlmarkierung, Ausrüstung und Darstellung vergleichen.
- **Erwartet** Beide Zusatzgrößen sind ohne Recht sichtbar gesperrt und ändern das Modell nicht. Mit dem wirksamen Sonderrecht lassen sie sich auswählen und aus B-Perspektive unterscheiden. Normal entfernt die Gadget-Skalierung. Die Wahl benötigt keinen Hotbar-Slot.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-05 · Team Gadgets und Colossal

- **Voraussetzungen** A hat die Gadgets- und Size-Changer-Basisrechte. `vapeecore.gadgets.team.use` und `vapeecore.gadgets.team.extreme-size` sind zunächst effektiv verweigert. B dient als unberechtigter Vergleich und als Beobachter auf freier Fläche.
- **Durchführung** Hauptmenü ohne Team-Recht prüfen. Nur Team-Recht für A freigeben und das Team-Menü über Slot 24 öffnen. Blaue Stiefel auf Slot 13 anklicken. Anschließend auch Extreme-Size-Recht freigeben, Menü neu öffnen, Colossal 5× und dann Normal wählen. Rückwege von Extreme Size über Team Gadgets bis ins Hauptmenü prüfen.
- **Erwartet** Slot 24 zeigt den Team-Eintrag nur mit Team-Recht. Das 27-Slot-Team-Menü öffnet sich, Extreme Size bleibt bis zur zweiten Freigabe gesperrt. Mit beiden Basisrechten und beiden Team-Rechten ist Colossal auswählbar und markiert. Normal setzt die Größe zurück. Zurück führt jeweils auf die richtige Ebene. B erhält keinen Team-Eintrag.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-06 · Größenwechsel und Hitbox-Kollisionen

- **Voraussetzungen** A darf 2×, 3× und 5× wählen. Markierte Wand- und Deckenpositionen mit genug Platz für Normal, aber zu wenig Raum für die jeweils getestete Zielgröße. Ausgangs-SCALE ohne fremden Modifier. B kann optional die Debug-Hitbox anzeigen.
- **Durchführung** Huge, Massive und Colossal jeweils an einer passenden Wand- und Deckenposition versuchen. Fußposition sowie vorherige Größe und Auswahlmarkierung festhalten. Danach dieselben drei Größen mit ausreichend Platz anwenden und vom zweiten Client vergleichen. Zum Schluss auf Normal zurückschalten.
- **Erwartet** Bei Kollision wird die Zusatzgröße abgelehnt. Fußposition, vorherige Größe und Markierung bleiben erhalten. Im freien Bereich gelingt derselbe Wechsel. Modell und sichtbare Hitboxanzeige soweit der Client sie darstellt protokollieren. Die native 3 × 9-Blöcke-Referenz für Colossal bei Standard-SCALE dient als Vergleichswert.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-07 · Kamera und Bewegung in Engstellen

- **Voraussetzungen** Freie Aufstellfläche, markierter Durchgang mit niedriger Decke und schmaler Laufweg. A darf mindestens eine Zusatzgröße wählen. B beobachtet aus sicherem Abstand.
- **Durchführung** Größe auf der freien Fläche wählen. Zuerst mit Normal, dann mit bereits angewandtem Giant, Massive und Colossal auf dieselben Engstellen zulaufen. In erster und dritter Person gehen, drehen und zurückweichen. B protokolliert Position und sichtbare Modellbewegung.
- **Erwartet** Die größere Figur reagiert an engen Stellen entsprechend ihrer Kollision. A kann aus freiem Gelände zurückweichen. Das Gadget teleportiert den Spieler dabei nicht. Clipping, Sichtverlust, Kamerasprünge oder anhaltendes Rubberbanding je Perspektive als Abweichung erfassen.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-08 · Glow aus Eigen- und Fremdperspektive

- **Voraussetzungen** A hat Glow-Rechte. A und B sind gegenseitig sichtbar und bleiben in Reichweite. Keine fremden Glow-Quellen aktiv.
- **Durchführung** A schaltet Glow im Menü ein. B beobachtet die Kontur; A wechselt zwischen erster und dritter Person und protokolliert sichtbare Änderungen je Perspektive. Mehr als zehn Sekunden auf aktivem Glow bleiben und danach im Menü ausschalten.
- **Erwartet** Aktiver Zustand und Ausschalten sind im Menü nachvollziehbar. B sieht während der gültigen Sitzung eine kontinuierliche Kontur ohne Erneuerungsflackern. A prüft das eigene sichtbare Modell in dritter Person; aus der ersten Person ist keine eigene Ganzkörperkontur vorausgesetzt. Nach dem Abschalten verschwindet die Gadget-Kontur.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-09 · Glow und Teamfarben

- **Voraussetzungen** Zwei verschieden gefärbte Testteams auf dem isolierten Server. Wirksame Teams und Farben pro Beobachter festhalten; bestehende Nametag-/Scoreboard-Darstellung vor dem Test dokumentieren.
- **Durchführung** A und B jeweils in verschiedenen Teamfarben gegenseitig mit aktivem Glow beobachten. Falls die Darstellung im Lobby-Scoreboard anders als erwartet ist, die tatsächlich für den jeweiligen Client wirksame Teamzuordnung kontrollieren. Glow abschalten und Teamzuordnung sowie Nametag/TAB mit dem Einstieg vergleichen.
- **Erwartet** Die Kontur übernimmt die beim jeweiligen Beobachter wirksame Teamfarbe. Glow legt keine eigene Teamfarbe fest und ändert keine bestehenden Teammitgliedschaften, Nametags oder TAB-Darstellungen.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-10 · Particle Trails und Partikeleinstellungen

- **Voraussetzungen** A hat Trails-Rechte. Beide Clients beginnen mit voller Partikelanzeige. Markierungen für deutlich unter und deutlich über 32 Blöcke Abstand, etwa 24 und 40 Blöcke, liegen in derselben geladenen Lobbywelt.
- **Durchführung** A wählt Hearts, Flame, Cloud, End Rod und Happy Villager nacheinander und läuft jeweils dieselbe Strecke. B prüft die nahe und ferne Position. Im Stand nur den Kopf drehen und anschließend `None` wählen. Kamerabeobachtung mit reduzierter und minimaler Partikelanzeige auf B wiederholen.
- **Erwartet** Bei gültiger Bewegung erscheinen die gewählten Partikel für nahe berechtigte Beobachter. Außerhalb der Reichweite, bei Stillstand, reiner Kopfdrehung und nach `None` entstehen keine neuen Spurpartikel. Reduzierte Clienteinstellungen dürfen die sichtbare Darstellung beeinflussen; Beobachtungen getrennt von serverseitiger Reichweite erfassen.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-11 · Sichtbarkeit, Hidden-Modus und Ignore

- **Voraussetzungen** Beide stehen sichtbar in der Lobby. A verwendet Trails und Glow. Beide Clients zeigen alle Partikel; Riding kann bei A für die Interaktionsprüfung ausgerüstet werden.
- **Durchführung** B prüft A zunächst im Modus ALL, dann im Modus HIDDEN und wieder in ALL. Anschließend getrennt B ignoriert A und A ignoriert B testen, mit Aufhebung zwischen den Varianten. Während jedes Zustands bewegt sich A neu. Riding bei passender Sneak-Geste versuchen, soweit das Ziel sichtbar und anwählbar ist.
- **Erwartet** In HIDDEN sieht B den ausgeblendeten A weder als Modell noch als Glow-Kontur oder neue Partikelspur. Trails respektieren Ignore in beiden Richtungen. Riding umgeht weder Sichtbarkeit noch Ignore. Bereits gerenderte Partikel dürfen clientseitig auslaufen. Nach Wiederherstellung der beiderseitigen Sichtbarkeit und Entfernen von Ignore sind neue Emissionen wieder sichtbar.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-12 · Berechtigungsentzug bei aktiven Gadgets

- **Voraussetzungen** A hat alle getesteten Rechte wirksam; B ist Beobachter und bei Bedarf Reiter. Einen freien Standort und eine unveränderte Vergleichsberechtigung vorsehen.
- **Durchführung** Getrennt für aktive VIP-Größe und Colossal das jeweilige Zusatzrecht entziehen. Bei offener Team-Ansicht zusätzlich Team-Recht entziehen und unverzüglich einen Klick versuchen. Aktive Riding-, Glow- und Trails-Funktionen jeweils mit entzogener Feature-Permission und in einer eigenen Runde mit entzogener Basis-Permission prüfen. Je Runde einen relevanten Bewegungs-, Menü- oder Hotbar-Abgleich auslösen und das Recht erst nach der Beobachtung wieder gewähren.
- **Erwartet** Ein betroffener Effekt endet am vorgesehenen Abgleich, eine unberechtigte Aktion kann ihn nicht erneut anwenden. Eine ungültig gewordene Team-Ansicht akzeptiert keine Aktion und schließt nach erkanntem Entzug. Riding löst die eigene Verbindung und die entzogene Ausstattung, Glow wird nicht weiter erneuert, Trails emittieren nicht weiter. Beim Entzug eines einzelnen Rechts bleiben unabhängig berechtigte Gadgets nutzbar. Erneute Freigabe stellt entfernte aktive Effekte oder alte Ansichten nicht von selbst wieder her.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-13 · Logout, Teleport, Weltwechsel und Reload

- **Voraussetzungen** Erstes Ziel in derselben Lobbywelt, zweites Ziel in einer anderen Testwelt außerhalb der Lobby. A und B können alle freigegebenen Effekte für einzelne Durchläufe neu aktivieren.
- **Durchführung** Je eine frische Runde für Logout mit Rejoin, erfolgreichen Teleport innerhalb der Lobbywelt, erfolgreichen Weltwechsel und `/core reload` auf der isolierten Instanz. Vor jeder Runde Standort, Auswahl und Hotbar notieren, Riding-Verbindung beziehungsweise Effekte einsetzen und in einer getrennten Wiederholung ein Gadget-Untermenü offen lassen. Nach dem Ereignis die verwalteten Items, Effekte und erneute Benutzbarkeit kontrollieren.
- **Erwartet** Alte Verbindungen und Gadget-Effekte enden. Aus einer alten Untermenüsitzung lässt sich nichts mehr anwenden. Der Wechsel aus der Lobby räumt verwaltete Lobby-Items auf. Nach Rejoin, Rückkehr oder Reload werden berechtigte Lobby-Items gemäß Konfiguration bereitgestellt, ohne Duplikate oder verdrängte Fremditems. Beim Teleport innerhalb derselben Lobby darf das weiterhin gültig ausgerüstete Riding-Item bleiben; die alte Verbindung endet.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —

## QA-14 · EN/DE, kompakte Lore und präfixfreie Nachrichten

- **Voraussetzungen** Client A mit Englisch, B mit Deutsch, serverseitige Lokalisierung ohne Sprachzwang und mit den Standardkatalogen. Beide Konten haben dieselben Basisrechte. Ein optionaler Override-Durchlauf erfolgt ausschließlich mit Testkonfiguration.
- **Durchführung** Mit beiden Konten dieselben Menüs öffnen und Standard-, Sperr-, Erfolgs- und Fehlertexte vergleichen. Riding aufnehmen, absteigen und an einem stehenden Ziel versuchen. Größenoptionen, Glow und Trails ansehen und umschalten. Falls gewünscht im getrennten Testlauf ein explizites Betreiberpräfix und einen Sprach-Override setzen, danach Testkonfiguration wiederherstellen.
- **Erwartet** EN und DE zeigen passende kompakte Lore, verständliche Status und konsequente Farben. Spielernamen und Werte sind korrekt eingesetzt. Mit Standardkonfiguration beginnen Nachrichten direkt mit ihrem Inhalt, ohne automatische Labels wie `[Player Riding]`. Explizite Betreiber-Präfixe und Sprach-Overrides bleiben im optionalen Durchlauf wirksam.
- **Ergebnis** PENDING
- **Screenshot/Video (optional)** —
- **Abweichungen** —
