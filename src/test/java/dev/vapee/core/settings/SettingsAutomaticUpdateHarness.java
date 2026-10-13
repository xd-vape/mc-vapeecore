package dev.vapee.core.settings;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
public final class SettingsAutomaticUpdateHarness{
    private static int checks;
    public static void main(String[] args)throws Exception{
        var layout=MenuLayout.builder(27,SettingsMenu.Action.class).setting(SettingsMenu.Action.SCOREBOARD,10,19).build();
        var f=new SettingsMenuFixture(layout);var owner=f.player("Owner");f.menu.startUpdates();f.menu.open(owner.player);var top=owner.open;
        var other=f.player("Other");var item=top.getItem(19);f.settings.setScoreboardEnabled(other.id,false);check(top.getItem(19)==item,"other owner's commit does not repaint menu");
        f.settings.setScoreboardEnabled(owner.id,false);check(owner.open==top && SettingsMenuFixture.text(top,19).equals("Disabled"),"external committed setting immediately rerenders same inventory");
        f.click(owner,top,10,ClickType.LEFT);check(SettingsMenuFixture.text(top,19).equals("Enabled"),"GUI toggle updates without manual Refresh");
        item=top.getItem(19);f.repository.failNext=true;try{f.settings.setScoreboardEnabled(owner.id,false);throw new AssertionError("save should fail");}catch(IllegalStateException expected){check(top.getItem(19)==item&&f.settings.isScoreboardEnabled(owner.id).orElseThrow(),"rollback is authoritative and emits no optimistic UI");}
        f.menu.stopUpdates();f.settings.setScoreboardEnabled(owner.id,false);check(SettingsMenuFixture.text(top,19).equals("Enabled"),"detached observer cannot update stale UI");
        f.menu.startUpdates();f.menu.refreshActive(owner.id);check(SettingsMenuFixture.text(top,19).equals("Disabled"),"internal rerender remains testable without Clock");
        f.menu.shutdown();check(f.menu.activeCount()==0,"shutdown detaches and clears active UI");
        check(SettingsMenu.DEFAULT_LAYOUT.actionSlot(SettingsMenu.Action.REFRESH).isEmpty(),"operator Settings has no manual Refresh");
        System.out.println("SettingsAutomaticUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
}
