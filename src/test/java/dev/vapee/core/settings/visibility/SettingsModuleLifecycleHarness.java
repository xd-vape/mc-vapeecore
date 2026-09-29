package dev.vapee.core.settings.visibility;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.settings.SettingsModule;
import dev.vapee.core.settings.SettingsInventoryHolder;
import org.bukkit.inventory.Inventory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Module wiring follows the existing lifecycle harness convention; menu cleanup is exercised live. */
public final class SettingsModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(CoreModule.class.isAssignableFrom(SettingsModule.class)
                        && !ReloadParticipant.class.isAssignableFrom(SettingsModule.class),
                "Settings remains CoreModule without reload ownership");
        String module = Files.readString(Path.of("src/main/java/dev/vapee/core/settings/SettingsModule.java"));
        check(module.contains("new SettingsMenu(") && module.contains("new VisibilitySettingsMenu(")
                        && module.contains("new VisiblePlayersMenu("), "enable owns all three menu types");
        for (String name : new String[]{"Settings", "VisibilitySettings", "VisiblePlayers"}) {
            check(module.contains("registerEvents(new" + name + "Listener, plugin)"),
                    "enable registers " + name + " listener");
        }
        check(module.contains("plugin.getCommand(\"settings\")")
                        && module.contains("newSettingsCommand.setExecutor(newSettingsExecutor)")
                        && module.contains("newSettingsCommand.setTabCompleter(newSettingsExecutor)"),
                "enable registers settings command and completion");
        check(module.contains("settingsMenu.closeOpenInventories()")
                        && module.contains("visibilitySettingsMenu.closeOpenInventories()")
                        && module.contains("visiblePlayersMenu.closeOpenInventories()")
                        && module.indexOf("closeVisibilityMenus();") < module.indexOf("if (settingsListener != null)"),
                "disable closes all own menus before unregistering listeners");
        for (String field : new String[]{"settingsListener", "visibilitySettingsListener", "visiblePlayersListener"}) {
            check(module.contains("HandlerList.unregisterAll(" + field + ")")
                            && module.contains("HandlerList.unregisterAll(new"
                            + Character.toUpperCase(field.charAt(0)) + field.substring(1) + ")"),
                    "disable and failed enable unregister " + field);
        }
        check(module.contains("settingsCommand.setExecutor(null)")
                        && module.contains("settingsCommand.setTabCompleter(null)"), "command cleanup");
        for (String field : new String[]{"settingsMenu", "visibilitySettingsMenu", "visiblePlayersMenu",
                "settingsListener", "visibilitySettingsListener", "visiblePlayersListener", "settingsCommand",
                "playerSettingsService", "presentationService"}) {
            check(module.contains(field + " = null;"), "clears runtime reference " + field);
        }
        var f = new VisibilityMenuFixture();
        var root = f.player("Root", true);
        var visibility = f.player("Visibility", true);
        var manage = f.player("Manage", true);
        f.rootMenu.open(root.player);
        f.visibilityMenu.open(visibility.player);
        f.visiblePlayersMenu.open(manage.player);
        Inventory rootInventory = root.open, visibilityInventory = visibility.open, manageInventory = manage.open;
        f.visibilityMenu.closeOpenInventories();
        f.visiblePlayersMenu.closeOpenInventories();
        f.rootMenu.closeOpenInventories();
        check(root.open == root.bottom && visibility.open == visibility.bottom && manage.open == manage.bottom,
                "cleanup closes three concurrently open own menu types");
        root.open = rootInventory; visibility.open = visibilityInventory; manage.open = manageInventory;
        check(!f.rootMenu.isActive(root.player, rootInventory, (SettingsInventoryHolder) rootInventory.getHolder())
                        && !f.visibilityMenu.isActive(visibility.player, visibilityInventory,
                        (VisibilitySettingsHolder) visibilityInventory.getHolder())
                        && !f.visiblePlayersMenu.isActive(manage.player, manageInventory,
                        (VisiblePlayersHolder) manageInventory.getHolder()), "all active registries empty after cleanup");
        int saves = f.repository.saves;
        f.rootClick(root, rootInventory, 10, org.bukkit.event.inventory.ClickType.LEFT);
        f.visibilityClick(visibility, visibilityInventory, 9, org.bukkit.event.inventory.ClickType.LEFT);
        check(f.repository.saves == saves, "stale inventories remain inert after module-style cleanup");
        System.out.println("SettingsModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
