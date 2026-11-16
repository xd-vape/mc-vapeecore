# Step 09D · Glow Gadget

Basis ist `master` auf `26e9511254c4f9ab8c594b9ca58240c2e3a85f92`.

## Bedienung

Der bestehende Glow-Eintrag (Glow Ink Sac) schaltet den Leuchteffekt per Linksklick direkt im Gadgets-Menü um. Das Menü zeigt den Zustand und die nächste Aktion in zwei Zeilen. Die EN/DE-Rückmeldungen verwenden `MessageService`, die semantischen Farben und die zentrale Akzentfarbe. Standardtexte haben keine Feature-Präfixe. Betreiberpräfixe und Sprach-Overrides bleiben wirksam.

`vapeecore.gadgets.use` und `vapeecore.gadgets.glow` sind standardmäßig erlaubt. Das Menü zeigt eine fehlende Glow-Berechtigung als gesperrt. Die Aktion prüft den Lobbyzugang und beide Berechtigungen erneut. Glow benötigt keinen Hotbar-Platz und verbraucht keinen Ausrüstungsslot. Die Hotbar- und Auswahlmechanik der anderen Gadgets bleibt bestehen.

## Laufzeit und Grenzen

Das globale `Entity#setGlowing`-Flag wird in Paper 1.21.11 als `Glowing` gespeichert und hat keinen Besitzer. Das Gadget verwendet einen Vanilla-Trankeffekt `PotionEffectType.GLOWING` mit 100 Ticks Laufzeit. Ein einmalig geplanter Folgeaufruf erneuert ihn nach 60 Ticks, solange exakt dieselbe Spielersitzung aktiv und berechtigt ist. Für Spieler im Stillstand reichen Bewegungs- und Lifecycle-Ereignisse allein für lückenloses Glow nicht aus. Außerhalb aktiver Sitzungen gibt es keine Erneuerung. [Paper Entity](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html) · [Paper Trankeffekte](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/LivingEntity.html)

Vor dem Einschalten führen bestehende Glowing-Flags und gleichartige Trankeffekte zur Ablehnung. In Paper Build 132 meldet `isGlowing()` auch Trank-Glow. Die Methode dient daher nur zur Prüfung vor Übernahme. Der Trankeffekt wird nach einem beobachteten eigenen Schreibvorgang und erneutem Lesen als aktiv gewertet. Bei regulären Fremdänderungen über das Potion-Event beendet das Gadget seine Besitzverfolgung. Cleanup prüft die erwarteten Effekteigenschaften und verzichtet bei erkennbaren Überschreibungen, einschließlich eines versteckten Fallback-Effekts, auf eine Entfernung. Wird ein fremder Trankeffekt während des eigenen Entfernen-Callbacks angelegt und rechtzeitig beobachtet, wird das Entfernen abgebrochen.

Paper versieht Potion-Events und Effektwerte nicht mit einer Plugin-Identität. Nicht beobachtbare identische Fremdänderungen lassen sich mit der API nicht sicher unterscheiden. Ein fremdes Entity-Flag während der Laufzeit ist neben einem aktiven Trank nicht separat lesbar und bleibt beim Cleanup unangetastet. Falls ein anderes Plugin eine Entfernung unterbindet oder ein Effekt nach einem abrupten Abbruch aus einem Vanilla-Save geladen wird, läuft der verbliebene Effekt regulär innerhalb von höchstens 100 Spielerticks ab. VapeeCore legt dafür keine dauerhaften Profilfelder an und beansprucht gespeicherte Effekte beim Join nicht erneut.

Logout, Kick, Teleport, Portal, Weltwechsel, Tod, Respawn, BUILD, Activity, Reload, Rollback und Disable beenden die eigene Sitzung. Bei Rechteentzug greifen der nächste Menü-, Bewegungs- oder Hotbar-Abgleich und die fällige Erneuerungsprüfung. Glow ändert weder Scoreboards, Teams und Nametags noch die Sichtbarkeitsregeln. Weiß ist die Vanilla-Standardfarbe der Kontur. Bestehende Teamfarben können je nach Betrachter Vorrang haben.

## Verifikation

- Vollständiger Offline-Verifier, Maven `clean package` und Spotless — 248/248 Harnesses, 41.355 Checks, 0 Fehler und 0 fehlende oder ungültige Harnesses. Maven und Spotless bestanden.
- GadgetsFoundationHarness — 205 bestandene Checks. Geprüft wurden Menü, Berechtigungen, Fremdzustände, Erneuerung, Lifecycle, EN/DE, Betreiberpräfix und gleichzeitige Verwendung mit Size Changer und Riding.
- Isoliertes Paper 1.21.11 Build 132 mit LuckPerms — 29 bestandene Checks (24 vor und 5 nach dem Neustart der Testinstanz) mit bytegleicher Kopie des finalen Build-Artefakts. Die Probe prüfte den Vanilla-Leuchtzustand, unveränderte Team- und Sichtbarkeitsregeln, gleichzeitige Gadgets, Teleport-Cleanup sowie echte Vanilla-Serialisierung und Ablauf des gespeicherten Effekts nach erneutem Join.
- 31 Schutzdateien wurden per SHA-256 verglichen. Die Operator-JAR, `dev-server/plugins/VapeeCore/lobby.yml` und `dev-server/server.properties` wichen schon vor Step 09D vom historischen Step-09C-Beleg ab. Der Vergleich für 09D verwendet die vor Beginn gemessenen Werte. Die Operator-Dateien, die bestehenden Worktrees und der historische Beleg wurden während der Arbeit nicht verändert.

Die Darstellung der Kontur aus Eigen- und Fremdperspektive bleibt in echten Minecraft-Clients zu prüfen, einschließlich Teamfarben und versteckter Spieler.
