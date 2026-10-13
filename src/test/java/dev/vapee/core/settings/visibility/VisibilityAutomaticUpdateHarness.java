package dev.vapee.core.settings.visibility;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
public final class VisibilityAutomaticUpdateHarness{
    private static int checks;
    public static void main(String[] args)throws Exception{
        var f=new VisibilityMenuFixture(VisibilitySettingsMenu.DEFAULT_LAYOUT,VisiblePlayersMenu.DEFAULT_LAYOUT);var p=f.player("Owner",true);f.visibilityMenu.startUpdates();f.visibilityMenu.open(p.player);var top=p.open;
        var layout=f.visibilityMenu.layout();int icon=layout.slot(VisibilitySettingsMenu.Action.MASTER),status=layout.slot(VisibilitySettingsMenu.Action.MASTER,1);
        f.settings.setLobbyPlayersVisible(p.id,false);check(p.open==top&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(VisibilityMenuFixture.visibilitySpec(top,status).name()).equals("Filtered"),"committed external master update rerenders in place");
        f.visibilityClick(p,top,icon,ClickType.LEFT);check(p.open==top&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(VisibilityMenuFixture.visibilitySpec(top,status).name()).equals("All Players")&&f.applyCalls==1&&f.hotbarCalls==1,"GUI toggle applies visibility/hotbar and updates display");
        var before=top.getItem(status);f.repository.failNext=true;f.visibilityClick(p,top,icon,ClickType.LEFT);check(p.open==top&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(VisibilityMenuFixture.visibilitySpec(top,status).name()).equals("All Players"),"failed toggle keeps actual rolled-back state");
        f.visibilityClick(p,top,52,ClickType.LEFT);check(p.open==top&&f.applyCalls==1,"old Clock slot is inert");
        check(layout.actionSlot(VisibilitySettingsMenu.Action.REFRESH).isEmpty(),"production Visibility has no Clock");f.visibilityMenu.shutdown();
        System.out.println("VisibilityAutomaticUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
}
