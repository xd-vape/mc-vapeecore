package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;
import dev.vapee.core.lobby.experience.navigator.NavigatorFixture.TestPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;

/** Executes the real editor commands, drafts and listener against identity-preserving inventory transport. */
public final class NavigatorEditorHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        workflow(); security(); conflictsAndDiscard();
        System.out.println("NavigatorEditorHarness passed " + checks + " checks.");
    }
    private static final class Fixture implements AutoCloseable {
        final NavigatorFixture f = new NavigatorFixture();
        final Path dir = Files.createTempDirectory(Path.of("target/harness-temp"), "navigator-editor-");
        final Path file = dir.resolve("navigator.yml");
        final NavigatorConfig config;
        final NavigatorEditor editor;
        Fixture() throws Exception {
            config = new NavigatorConfig(file, f.logger); config.initialize(); f.configured.set(config.getState()); f.destinations(3);
            var store = new NavigatorStore(config, f.logger, state -> { f.configured.set(state); f.menu.closeOpenInventories(); });
            editor = new NavigatorEditor(f.warps, f.menu, store, f.messages, (holder,size,title) -> inventory(holder,size),
                    SpecItem::new, id -> f.players.containsKey(id) ? f.players.get(id).player : null);
        }
        TestPlayer admin() { var p = f.player(); p.permissions.add(NavigatorEditor.PERMISSION); return p; }
        void cmd(TestPlayer p, String command) { editor.onCommand(p.player, null, "navigator", command.split(" ")); }
        NavigatorEditor.Session s(TestPlayer p) throws Exception {
            var field = NavigatorEditor.class.getDeclaredField("sessions"); field.setAccessible(true);
            return (NavigatorEditor.Session) ((Map<?, ?>) field.get(editor)).get(p.id);
        }
        InventoryClickEvent click(TestPlayer p, Inventory top, int slot, ClickType click) {
            var event = new InventoryClickEvent(view(p, top), InventoryType.SlotType.CONTAINER, slot, click,
                    click == ClickType.SHIFT_LEFT ? InventoryAction.MOVE_TO_OTHER_INVENTORY : InventoryAction.PICKUP_ALL);
            editor.click(event); return event;
        }
        InventoryDragEvent drag(TestPlayer p, Inventory top, int slot) {
            var event = new InventoryDragEvent(view(p, top), null, null, false, Map.of(slot, new org.bukkit.inventory.ItemStack() { }));
            editor.drag(event); return event;
        }
        NavigatorConfig.Entry entry(TestPlayer p) throws Exception { var s = s(p); return s.draft.menus().get(s.menu).entries().get(s.selected); }
        @Override public void close() throws Exception {
            editor.shutdown(); f.menu.shutdown();
            try (var paths = Files.walk(dir)) { for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static void workflow() throws Exception {
        try (var t = new Fixture()) {
            var p = t.admin(); byte[] original = Files.readAllBytes(t.file);
            t.cmd(p, "edit");
            check(t.s(p).menu.equals("root") && t.s(p).draft.menus().get("root").inventorySize() == 27, "authorized editor begins compact root");
            var initial = p.open;
            t.click(p, initial, 11, ClickType.LEFT);
            check(text(p.open,11).equals("Add Warp") && text(p.open,13).equals("Link existing submenu"), "empty slot offers Warp and submenu choices");
            t.cmd(p,"menu create welten Welten");
            check(t.entry(p).type() == NavigatorConfig.Type.MENU && t.entry(p).slot() == 11 && t.entry(p).target().equals("welten"), "create submenu places link in selected slot");
            check(t.s(p).draft.menus().get("welten").inventorySize() == 27, "new submenu has own compact size");
            t.cmd(p,"entry icon GRASS_BLOCK"); t.cmd(p,"entry name de Welten DE");
            check(t.entry(p).icon() == Material.GRASS_BLOCK && t.entry(p).name().english().equals("Welten") && t.entry(p).name().values().get("de").equals("Welten DE"), "entry icon and localized name preserve English");
            t.cmd(p,"menu select welten"); t.cmd(p,"menu rename de Spielwelten"); t.cmd(p,"menu icon FEATHER");
            check(t.s(p).draft.menus().get("welten").title().values().get("de").equals("Spielwelten") && t.s(p).draft.menus().get("welten").icon() == Material.FEATHER, "menu title and picker icon editable");
            t.click(p,p.open,13,ClickType.LEFT); t.click(p,p.open,11,ClickType.LEFT);
            check(text(p.open,0).contains("custom_000"), "Warp picker uses existing WarpService destinations");
            t.click(p,p.open,1,ClickType.LEFT);
            check(t.entry(p).type() == NavigatorConfig.Type.WARP && t.entry(p).target().equals("custom_001") && t.entry(p).slot() == 13, "picker assigns actual selected Warp and slot");
            t.cmd(p,"entry name all Parkour"); t.cmd(p,"entry lore en Jump here | Have fun"); t.cmd(p,"entry lore de Spring hier | Viel Spaß");
            check(t.entry(p).name().english().equals("Parkour") && t.entry(p).lore().size() == 2 && t.entry(p).lore().get(0).english().equals("Jump here") && t.entry(p).lore().get(1).values().get("de").equals("Viel Spaß"), "localized literal names and lore editable");
            t.cmd(p,"entry permission example.navigator.parkour");
            check(t.entry(p).permission().equals("example.navigator.parkour") && t.f.warps.getWarp("custom_001").orElseThrow().navigation().visible(), "entry permission never changes global Warp navigation");
            var before = t.s(p).draft; t.cmd(p,"entry permission admin or owner");
            check(t.s(p).draft == before, "invalid permission rejected without partial mutation");
            var warp = t.f.warps.getWarp("custom_001").orElseThrow();
            p.x = 10; p.y = 80; p.z = 10; t.cmd(p,"region pos1"); t.cmd(p,"save");
            check(Arrays.equals(original,Files.readAllBytes(t.file)) && t.s(p).first != null, "incomplete region blocks Save");
            p.currentWorld = t.f.outside; t.cmd(p,"region pos2");
            check(t.entry(p).region() == null && t.s(p).first != null, "cross-world pos2 rejected while pos1 retained");
            p.currentWorld = t.f.lobby; p.x = 0; p.y = 60; p.z = 0; t.cmd(p,"region pos2"); t.cmd(p,"entry stack true");
            check(t.entry(p).region().minX() == 0 && t.entry(p).region().maxY() == 80 && t.entry(p).stackAmount(), "same-world region normalizes and enables safe stack counting");
            check(t.f.warps.getWarp("custom_001").orElseThrow().equals(warp), "region never modifies authoritative Warp position or metadata");
            t.cmd(p,"region info"); check(p.output().contains("lobby-world") && p.output().contains("Count: ON"), "region info displays world bounds enabled state");
            t.cmd(p,"region clear"); check(t.entry(p).region() == null && !t.entry(p).stackAmount(), "clear removes optional region and stack display");
            p.x=0; p.y=60; p.z=0; t.cmd(p,"region pos1"); p.x=10; p.y=80; p.z=10; t.cmd(p,"region pos2");
            t.cmd(p,"menu create events Events"); t.cmd(p,"menu select events"); t.cmd(p,"add warp custom_002");
            check(t.entry(p).target().equals("custom_002") && t.s(p).draft.menus().get("welten").entries().values().stream().anyMatch(e -> e.target().equals("events")), "multiple nested menu levels supported");
            before = t.s(p).draft; t.cmd(p,"add menu root 15"); check(t.s(p).draft == before, "cycle rejected, whole prior draft preserved");
            t.cmd(p,"entry move welten 15 0"); check(t.s(p).menu.equals("welten") && t.entry(p).slot() == 15 && t.s(p).draft.menus().get("events").entries().isEmpty(), "entry moves between menus without duplicating Warp data");
            t.cmd(p,"menu delete events"); check(!t.s(p).draft.menus().containsKey("events") && t.s(p).draft.menus().values().stream().flatMap(m -> m.entries().values().stream()).noneMatch(e -> e.type() == NavigatorConfig.Type.MENU && e.target().equals("events")), "delete removes all incoming links without orphaning");
            t.cmd(p,"menu delete root"); check(t.s(p).draft.menus().containsKey("root"), "root deletion rejected");
            t.cmd(p,"menu select root"); t.cmd(p,"add warp custom_000");
            check(t.entry(p).slot() == 13 && Arrays.equals(original,Files.readAllBytes(t.file)) && !t.config.getState().enabled(), "provisional edits never publish or write");
            t.cmd(p,"preview");
            check(p.open.getSize()==27 && p.open.getHolder() instanceof NavigatorInventoryHolder, "preview reuses actual player menu renderer");
            int teleports=p.teleports; t.f.click(p,p.open,13,ClickType.LEFT);
            check(p.teleports==teleports && !t.config.getState().enabled() && Arrays.equals(original,Files.readAllBytes(t.file)), "preview cannot teleport or commit draft");
            t.cmd(p,"edit"); t.cmd(p,"save");
            check(t.s(p)==null && t.config.getState().enabled() && NavigatorConfig.parse(Files.readString(t.file)).equals(t.config.getState()), "Save persists and live-applies full validated document then releases session");
            try(var backups=Files.list(t.dir.resolve("navigator-backups"))) {
                var list=backups.toList(); check(list.size()==1 && Arrays.equals(original,Files.readAllBytes(list.getFirst())), "unique protected backup retains exact prior file bytes");
            }
            var viewer=t.f.player(); t.f.menu.open(viewer.player); check(viewer.open.getSize()==27 && ((NavigatorInventoryHolder)viewer.open.getHolder()).entryAt(11).isPresent(), "ordinary Compass/menu reopen sees committed submenu without restart");
            t.f.click(viewer,viewer.open,11,ClickType.LEFT);
            check(((NavigatorInventoryHolder)viewer.open.getHolder()).menuId().equals("welten") && viewer.open.getItem(13)==null && viewer.open.getItem(15)!=null, "entry permissions hide restricted Warp while ordinary nested entry remains");
            var old=viewer.open; t.cmd(p,"edit"); t.cmd(p,"menu select welten"); t.cmd(p,"select custom_002"); t.cmd(p,"entry remove"); t.cmd(p,"save");
            check(viewer.open==viewer.bottom, "committed edit invalidates existing public inventory");
            t.f.click(viewer,old,15,ClickType.LEFT); check(viewer.teleports==0, "old removed entry cannot execute after Save");
            var cold=new NavigatorConfig(t.file,t.f.logger);cold.initialize();check(cold.getState().equals(t.config.getState()),"cold startup loads the same committed menu graph");
        }
    }
    private static void security() throws Exception {
        try(var t=new Fixture()) {
            var p=t.f.player(); byte[] original=Files.readAllBytes(t.file);t.cmd(p,"edit");
            check(t.s(p)==null && p.open==p.bottom,"unprivileged editor rejected");
            check(t.editor.onTabComplete(p.player,null,"navigator",new String[]{""}).isEmpty(),"unauthorized completion leaks no commands or menus");
            p.permissions.add(NavigatorEditor.PERMISSION);t.cmd(p,"edit");var top=p.open;var other=t.admin();
            for(var click:List.of(ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.MIDDLE,ClickType.DROP,ClickType.CONTROL_DROP,ClickType.SWAP_OFFHAND))
                check(t.click(p,top,49,click).isCancelled() && Arrays.equals(original,Files.readAllBytes(t.file)),"unsafe editor click cannot Save: "+click);
            check(t.click(other,top,49,ClickType.LEFT).isCancelled() && t.s(other)==null && Arrays.equals(original,Files.readAllBytes(t.file)),"foreign UUID cannot use editor Save");
            check(t.click(p,top,54,ClickType.SHIFT_LEFT).isCancelled(),"bottom inventory transfer canceled");
            check(t.drag(p,top,11).isCancelled() && !t.drag(p,top,54).isCancelled(),"editor drag protects top while allowing current bottom-only drag");
            t.click(p,top,11,ClickType.LEFT);var next=p.open;
            t.click(p,top,49,ClickType.LEFT);check(p.open==next && Arrays.equals(original,Files.readAllBytes(t.file)),"stale editor inventory cannot Save");
            t.editor.close(new InventoryCloseEvent(view(p,top)));t.click(p,next,18,ClickType.LEFT);check(p.open!=next,"late Close does not invalidate new active editor view");
            var forged=new NavigatorEditor.Holder(t.s(p));forged.inventory=inventory(forged,54);forged.actions.put(49,actor->{throw new AssertionError("forged action executed");});p.open=forged.inventory;
            check(t.click(p,p.open,49,ClickType.LEFT).isCancelled() && Arrays.equals(original,Files.readAllBytes(t.file)),"forged bound holder lacks registry authority");
            t.cmd(p,"edit");top=p.open;top.setItem(0,new SpecItem(dev.vapee.core.ui.UiItems.literal(Material.LIME_DYE,net.kyori.adventure.text.Component.text("Save"),net.kyori.adventure.text.format.NamedTextColor.GREEN,List.of())));
            t.click(p,top,0,ClickType.LEFT);check(Arrays.equals(original,Files.readAllBytes(t.file)) && t.s(p)!=null,"item name/lore cannot grant Save authority");
            t.cmd(p,"edit");top=p.open;p.permissions.clear();t.click(p,top,49,ClickType.LEFT);
            check(t.s(p)==null && p.open==p.bottom && Arrays.equals(original,Files.readAllBytes(t.file)),"revoked permission closes editor and destroys draft before actions");
            p.permissions.add(NavigatorEditor.PERMISSION);t.cmd(p,"edit");t.cmd(p,"preview");top=p.open;p.permissions.clear();t.f.click(p,top,0,ClickType.RIGHT);
            check(!t.f.menu.isActive(p.player,top,(NavigatorInventoryHolder)top.getHolder()) && p.teleports==0,"permission-revoked preview loses draft authority");
            p.permissions.add(NavigatorEditor.PERMISSION);t.cmd(p,"edit");top=p.open;t.editor.shutdown();
            check(t.s(p)==null && p.open==p.bottom,"shutdown closes owned editor and clears sessions");
            t.click(p,top,49,ClickType.LEFT);t.cmd(p,"edit");check(t.s(p)==null && Arrays.equals(original,Files.readAllBytes(t.file)),"shutdown queued clicks and commands remain inert");
        }
    }
    private static void conflictsAndDiscard() throws Exception {
        try(var t=new Fixture()) {
            var a=t.admin();var b=t.admin();byte[] original=Files.readAllBytes(t.file);t.cmd(a,"edit");t.cmd(b,"edit");
            check(t.s(a)!=t.s(b),"sessions isolated by actual administrator UUID");
            t.cmd(a,"add warp custom_000");check(t.s(b).draft.menus().get("root").entries().isEmpty(),"one administrator's provisional edits never leak into another");
            t.cmd(a,"discard");check(t.s(a)==null && Arrays.equals(original,Files.readAllBytes(t.file)) && !t.config.getState().enabled(),"Discard leaves disk/runtime unchanged");
            t.cmd(a,"edit");t.cmd(a,"add warp custom_001");t.cmd(a,"save");byte[] saved=Files.readAllBytes(t.file);t.cmd(b,"add warp custom_002");t.cmd(b,"save");
            check(t.s(b)!=null && Arrays.equals(saved,Files.readAllBytes(t.file)) && b.output().contains("another administrator"),"competing Save rejected without overwriting committed state");
            t.cmd(b,"discard");t.cmd(b,"edit");Files.writeString(t.file,Files.readString(t.file)+"\n# operator external edit\n");byte[] external=Files.readAllBytes(t.file);t.cmd(b,"save");
            check(t.s(b)!=null && Arrays.equals(external,Files.readAllBytes(t.file)),"external comment-only file edit also blocks Save");
            check(t.config.getState().menus().get("root").entries().size()==1,"external manual file edit does not silently replace current runtime");
        }
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}
