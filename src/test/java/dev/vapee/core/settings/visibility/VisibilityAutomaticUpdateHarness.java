package dev.vapee.core.settings.visibility;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
public final class VisibilityAutomaticUpdateHarness{
    private static int checks;
    public static void main(String[] args)throws Exception{
        var f=new VisibilityMenuFixture(VisibilitySettingsMenu.DEFAULT_LAYOUT,VisiblePlayersMenu.DEFAULT_LAYOUT);var p=f.player("Owner",true);f.visibilityMenu.startUpdates();f.visibilityMenu.open(p.player);var top=p.open;
        var layout=f.visibilityMenu.layout();int icon=layout.slot(VisibilitySettingsMenu.Action.MASTER);
        check(layout.placement(VisibilitySettingsMenu.Action.MASTER).size()==2,"default mode has a visible status pane as requested in the client test");
        f.settings.setLobbyPlayersVisible(p.id,false);check(p.open==top&&lore(top,icon).contains("Current: Filtered")
                && VisibilityMenuFixture.visibilitySpec(top,layout.slot(VisibilitySettingsMenu.Action.MASTER,1)).material()==org.bukkit.Material.YELLOW_STAINED_GLASS_PANE,"committed external mode update rerenders icon and pane in place");
        f.visibilityClick(p,top,icon,ClickType.LEFT);check(p.open==top&&lore(top,icon).contains("Current: Hidden")&&f.applyCalls==1&&f.hotbarCalls==1,"GUI cycle applies visibility/hotbar and updates display");
        f.repository.failNext=true;f.visibilityClick(p,top,icon,ClickType.LEFT);check(p.open==top&&lore(top,icon).contains("Current: Hidden"),"failed cycle keeps exact actual rolled-back mode");
        int emptySlot=java.util.stream.IntStream.range(0,layout.size()).filter(slot->layout.actionAt(slot).isEmpty()).findFirst().orElseThrow();
        f.visibilityClick(p,top,emptySlot,ClickType.LEFT);check(p.open==top&&f.applyCalls==1,"empty unplaced control slot is inert");
        check(layout.actionSlot(VisibilitySettingsMenu.Action.REFRESH).isEmpty(),"production Visibility has no Clock");f.visibilityMenu.shutdown();
        System.out.println("VisibilityAutomaticUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
    private static String lore(org.bukkit.inventory.Inventory inventory,int slot){return VisibilityMenuFixture.visibilitySpec(inventory,slot).lore().stream().map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).collect(java.util.stream.Collectors.joining("\n"));}
}
