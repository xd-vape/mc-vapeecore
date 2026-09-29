package dev.vapee.core.visibility;

import java.nio.file.Files;
import java.nio.file.Path;

public final class VisibilityModuleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        int friend = core.indexOf("moduleManager.register(friendModule)");
        int clan = core.indexOf("moduleManager.register(clanModule)");
        int lobby = core.indexOf("moduleManager.register(lobbyModule)");
        int visibility = core.indexOf("moduleManager.register(visibilityModule)");
        int chat = core.indexOf("moduleManager.register(chatModule)");
        check(friend >= 0 && friend < clan, "Friend remains before Clan");
        check(lobby >= 0 && lobby < visibility && visibility < chat,
                "Visibility registers after Lobby and before Chat");
        check(core.split("moduleManager.register\\(", -1).length - 1 == 25,
                "twenty-five modules register");
        check(core.contains("presentationModule, dailyQuestModule")
                && !core.contains("visibilityModule, dailyQuestModule"),
                "reload participant list remains six without Visibility");

        String module = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/visibility/VisibilityModule.java"));
        check(module.contains("VisibilityService newService = new VisibilityService(")
                        && module.contains("service = newService"),
                "enable creates and publishes service");
        check(module.contains("service.restoreAll()") && module.contains("service = null"),
                "disable restores state and clears runtime reference");
        check(!module.contains("ReloadParticipant") && !module.contains("runTask"),
                "Visibility has no reload participant or scheduler");
        check(module.contains("(UUID viewer, UUID target) -> false"),
                "production game-participant provider is explicit false boundary");
        check(module.contains("friendService.addRelationshipListener(newFriendListener)")
                        && module.contains("socialService.addRelationshipListener(newIgnoreListener)")
                        && module.contains("activeService.refreshPair(first, second)"),
                "Visibility owns friend and ignore live-refresh subscriptions");
        check(module.contains("subscribedFriends.removeRelationshipListener(friendRelationshipListener)")
                        && module.contains("subscribedSocial.removeRelationshipListener(ignoreRelationshipListener)"),
                "Visibility removes both relationship listeners during disable");

        String settings = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/settings/SettingsModule.java"));
        check(settings.contains("IdentityModule identityModule")
                        && settings.contains("VisibilityModule visibilityModule")
                        && settings.contains("LobbyModule lobbyModule"),
                "Settings visibility UX uses explicit module dependencies");
        check(settings.contains("new VisibilitySettingsMenu(")
                        && settings.contains("new VisiblePlayersMenu(")
                        && settings.contains("new SettingsCommand(plugin"),
                "Settings owns visibility menus and command UX");
        check(settings.contains("closeVisibilityMenus()")
                        && settings.contains("HandlerList.unregisterAll(newVisibilitySettingsListener)")
                        && settings.contains("HandlerList.unregisterAll(newVisiblePlayersListener)"),
                "Settings failure and disable paths clean visibility GUI lifecycle");

        String experience = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java"));
        check(experience.contains("visibilityModule.getVisibilityService()")
                && !experience.contains("new VisibilityService("),
                "LobbyExperience consumes the module-owned service");
        check(!experience.contains("restoreAll()"),
                "LobbyExperience no longer owns global visibility cleanup");
        check(!Files.exists(Path.of(
                "src/main/java/dev/vapee/core/lobby/experience/LobbyVisibilityService.java")),
                "legacy competing service is removed");

        String experienceListener = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceListener.java"));
        check(experienceListener.contains("scheduleSynchronization(event.getPlayer(), 2L)")
                        && experienceListener.contains("scheduleSynchronization(event.getPlayer(), 1L)")
                        && experienceListener.contains("visibilityService.synchronizePlayer(player)"),
                "join, respawn, and lobby entry keep event-driven synchronization");
        check(experienceListener.contains("visibilityService.restorePlayer(event.getPlayer())")
                        && experienceListener.contains("visibilityService.restorePlayer(player)"),
                "quit and lobby exit restore plugin-owned visibility");

        String itemListener = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/lobby/experience/LobbyItemListener.java"));
        check(itemListener.contains("setLobbyPlayersVisible(uniqueId, !currentValue.get())")
                        && itemListener.contains("visibilityService.applyViewerPreference(player)")
                        && itemListener.contains("lobbyItemService.refreshVisibilityItem(player)"),
                "hotbar master toggle persists and refreshes visibility plus item immediately");
        check(itemListener.contains("VISIBILITY_TOGGLE_COOLDOWN_TICKS = 10L")
                        && itemListener.contains("playFeedbackSound(player)"),
                "hotbar cooldown and sound feedback remain in place");

        String itemService = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/lobby/item/LobbyItemService.java"));
        check(itemService.contains("Players: Visible")
                        && itemService.contains("Right-click to use your visibility filters.")
                        && itemService.contains("Players: Filtered")
                        && itemService.contains("Right-click to show all lobby players."),
                "hotbar labels describe visible and filtered master states");

        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml")).replace("\r\n", "\n");
        check(plugin.contains("  vapeecore.visibility.staff:\n")
                && plugin.contains("Marks a player as staff for visibility filtering; grants no admin actions\n")
                && plugin.substring(plugin.indexOf("  vapeecore.visibility.staff:\n")).contains("    default: op"),
                "staff marker permission is declared with op default");
        System.out.println("VisibilityModuleHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
