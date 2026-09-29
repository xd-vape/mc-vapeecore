package dev.vapee.core.friend;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.social.SocialModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FriendLifecycleHarness {

    private static int checks;

    public static void main(String[] args) throws Exception {
        check(CoreModule.class.isAssignableFrom(FriendModule.class)
                        && !ReloadParticipant.class.isAssignableFrom(FriendModule.class),
                "Friend is one CoreModule, not a ReloadParticipant");
        check(List.of(FriendModule.class.getConstructor(JavaPlugin.class, ConfigService.class,
                        PlayerModule.class, SocialModule.class, IdentityModule.class,
                        MessageService.class, CommandHelpRenderer.class).getParameterTypes())
                        .equals(List.of(JavaPlugin.class, ConfigService.class, PlayerModule.class,
                                SocialModule.class, IdentityModule.class,
                                MessageService.class, CommandHelpRenderer.class)),
                "FriendModule uses explicit constructor dependencies");
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(core.indexOf("moduleManager.register(identityModule)")
                        < core.indexOf("moduleManager.register(friendModule)")
                        && core.indexOf("moduleManager.register(friendModule)")
                        < core.indexOf("moduleManager.register(rewardModule)"),
                "Friend starts after Identity and before Reward, so reverse shutdown is safe");
        check(core.split("moduleManager.register\\(", -1).length - 1 == 25,
                "exactly twenty-five modules register");
        check(core.contains("presentationModule, dailyQuestModule)")
                        && !core.contains("List.of(configService, friendModule"),
                "Friend does not join six reload participants");
        String source = Files.readString(Path.of("src/main/java/dev/vapee/core/friend/FriendModule.java"));
        check(source.contains("resolve(\"friends.yml\")")
                        && source.contains("new FileFriendRepository(file, plugin.getLogger())")
                        && source.contains("configService::getFriendLimits"),
                "Friend uses central runtime data path and live config limit supplier");
        check(source.contains("new FriendCommand(")
                        && source.contains("new FriendMenu(")
                        && source.contains("new FriendMenuListener(")
                        && source.contains("registerEvents(listener, plugin)")
                        && source.contains("command.setExecutor(executor)")
                        && source.contains("command.setTabCompleter(executor)"),
                "enable registers menu listener, command executor and tab completer");
        check(source.contains("command.setExecutor(null)")
                        && source.contains("command.setTabCompleter(null)")
                        && source.contains("HandlerList.unregisterAll(listener)")
                        && source.contains("HandlerList.unregisterAll(friendMenuListener)")
                        && source.contains("friendMenu.closeOpenInventories()")
                        && source.contains("friendService = null")
                        && source.contains("requireNonNull(friendService"),
                "disable and failed enable clean up GUI, listener and command");
        check(!source.contains("runTask") && !source.contains("save("),
                "FriendModule owns no scheduler or shutdown rewrite");
        check(!Files.exists(Path.of("src/main/resources/friends.yml")),
                "friends.yml is runtime data, not a bundled config resource");
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        check(plugin.contains("  friend:\n") || plugin.contains("  friend:\r\n"),
                "friend command is registered in plugin.yml");
        check(plugin.contains("vapeecore.friend.use:")
                        && plugin.contains("- friends"),
                "public friend permission and /friends alias are registered");
        System.out.println("FriendLifecycleHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
