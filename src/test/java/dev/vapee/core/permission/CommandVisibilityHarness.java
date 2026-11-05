package dev.vapee.core.permission;

import static dev.vapee.core.module.ModuleRuntimeFixture.*;

import net.luckperms.api.LuckPerms;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.command.defaults.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.permissions.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/** Real descriptor, Bukkit effective permissions, events and module lifecycle; no rank-name oracle. */
public final class CommandVisibilityHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (var f = new Fixture()) {
            permissions(f);
            cosmetics(f);
            runtime(f);
            lifecycle(f);
        }
        System.out.println("CommandVisibilityHarness passed " + checks + " checks.");
    }

    private static void permissions(Fixture f) throws Exception {
        var user = f.user();
        Set<String> publicRoots =
                Set.of(
                        "tpa",
                        "tpahere",
                        "tpaccept",
                        "tpdeny",
                        "tpcancel",
                        "quests",
                        "vapeecore",
                        "core",
                        "spawn",
                        "coins",
                        "friend",
                        "friends",
                        "clan",
                        "clans",
                        "msg",
                        "reply",
                        "r",
                        "settings",
                        "ignore",
                        "unignore",
                        "ignorelist",
                        "rank",
                        "ranks");
        var visible = f.filtered(user);
        check(f.descriptor.getCommands().size() == 43, "all 43 actual descriptor roots are loaded");
        for (String label : f.vapeeLabels)
            check(
                    visible.contains(label) == publicRoots.contains(label),
                    "independent default-player visibility: " + label);
        check(
                !visible.contains("profile")
                        && visible.containsAll(Set.of("core", "vapeecore", "coins"))
                        && !visible.contains("staff"),
                "private staff root is hidden while public core and coins remain visible");
        user.attachment.setPermission("vapeecore.utility.fly", true);
        visible = f.filtered(user);
        check(
                visible.contains("fly")
                        && !visible.contains("heal")
                        && !visible.contains("gamemode"),
                "extra fly permission adds only its capability, regardless of group label: effective="
                        + user.player.hasPermission("vapeecore.utility.fly")
                        + "; roots="
                        + visible);
        user.attachment.setPermission("vapeecore.moderation.warn", true);
        user.attachment.setPermission("vapeecore.moderation.mute", true);
        visible = f.filtered(user);
        check(
                visible.containsAll(Set.of("warn", "mute")) && !visible.contains("ban"),
                "moderator sees only explicitly granted moderation capabilities");
        user.attachment.setPermission("vapeecore.utility.fly", false);
        check(
                !f.filtered(user).contains("fly"),
                "explicit negative effective permission removes a previously granted root");
        user.attachment.setPermission("vapeecore.utility.heal.others", true);
        check(
                user.player.hasPermission("vapeecore.utility.heal")
                        && f.filtered(user).contains("heal"),
                "actual descriptor permission child grants the existing base command");
        user.attachment.setPermission("vapeecore.admin", true);
        user.attachment.setPermission("vapeecore.warp.admin", true);
        user.attachment.setPermission("vapeecore.navigator.admin", true);
        visible = f.filtered(user);
        check(
                visible.containsAll(Set.of("warp", "navigator", "profile"))
                        && !visible.contains("ban"),
                "core admin is a cosmetic operator indicator, not a foreign or moderation wildcard");
        var op = f.user();
        op.op = true;
        op.permissions.recalculatePermissions();
        check(
                f.filtered(op).containsAll(Set.of("ban", "warp", "fly")),
                "OP uses actual Bukkit permission defaults");
        op.attachment.setPermission("vapeecore.moderation.ban", false);
        check(!f.filtered(op).contains("ban"), "explicit Bukkit denial overrides the OP default");
        check(!f.filtered(op).contains("staff"), "OP alone does not imply the explicit panel permission");
        user.attachment.setPermission(dev.vapee.core.staff.StaffPanel.PERMISSION, true);
        check(f.filtered(user).contains("staff"), "panel permission offers staff; group eligibility stays at execution");
        user.attachment.setPermission(dev.vapee.core.staff.StaffPanel.PERMISSION, false);
        check(!f.filtered(user).contains("staff"), "panel permission revoke removes the root without a descriptor gate");
        var future = f.command("future", f.plugin, "future.permission");
        f.commands.put("future", future);
        user.attachment.setPermission("future.permission", true);
        check(
                f.filtered(user).contains("future"),
                "new owned commands are not blocked by a fixed root whitelist");
        user.attachment.setPermission("future.permission", false);
        check(
                !f.filtered(user).contains("future"),
                "future owned commands follow their real descriptor permission");
        check(
                f.executions == 0 && user.messages == 0,
                "visibility does not execute commands or send denial chat");
    }

    private static void cosmetics(Fixture f) throws Exception {
        var user = f.user();
        f.commands.put("help", new HelpCommand());
        f.commands.put("?", f.commands.get("help"));
        var version = new VersionCommand("version");
        for (String alias : List.of("version", "ver", "about")) f.commands.put(alias, version);
        var plugins = new PluginsCommand("plugins");
        for (String alias : List.of("plugins", "pl")) f.commands.put(alias, plugins);
        f.permissions.put(
                "bukkit.command.help",
                new Permission("bukkit.command.help", PermissionDefault.TRUE));
        f.permissions.put(
                "bukkit.command.version",
                new Permission("bukkit.command.version", PermissionDefault.TRUE));
        f.permissions.put(
                "bukkit.command.plugins",
                new Permission("bukkit.command.plugins", PermissionDefault.TRUE));
        var lp = f.command("luckperms", f.foreign("LuckPerms"), "test.lp.root");
        for (String label :
                List.of("luckperms", "lp", "perm", "perms", "permission", "permissions"))
            f.commands.put(label, lp);
        user.attachment.setPermission("test.lp.root", true);
        user.permissions.recalculatePermissions();
        for (String label :
                List.of(
                        "help",
                        "?",
                        "version",
                        "ver",
                        "about",
                        "plugins",
                        "pl",
                        "luckperms",
                        "lp",
                        "perm",
                        "perms",
                        "permission",
                        "permissions"))
            check(
                    !f.filtered(user).contains(label),
                    "ordinary player technical entry hidden by actual owner: " + label);
        user.attachment.setPermission("vapeecore.admin", true);
        check(
                f.filtered(user).containsAll(Set.of("help", "version", "plugins", "luckperms")),
                "technical operator exception retains existing command permission gates");
        user.attachment.setPermission("test.lp.root", false);
        check(
                !f.filtered(user).contains("luckperms"),
                "core operator marker never overrides LuckPerms' own root permission");
        var foreign = f.foreign("OtherPlugin");
        for (String label : List.of("help", "plugins", "profile", "lp", "w")) {
            f.commands.put(label, f.command(label, foreign, null));
            user.attachment.setPermission("vapeecore.admin", false);
            check(
                    f.filtered(user).contains(label),
                    "foreign same-name command preserved: " + label);
        }
        f.commands.put(
                "about",
                new BukkitCommand("about") {
                    public boolean execute(CommandSender sender, String label, String[] args) {
                        throw new AssertionError();
                    }
                });
        check(
                f.filtered(user).contains("about"),
                "unknown platform-like command is not assumed to be a builtin owner");
        f.commands.put("foreignfuture", f.command("foreignfuture", foreign, null));
        var labels = new LinkedHashSet<>(f.commands.keySet());
        labels.add("unknown-future");
        var before = new LinkedHashSet<>(labels);
        f.listener.onCommands(new PlayerCommandSendEvent(user.player, labels));
        check(
                before.containsAll(labels)
                        && labels.containsAll(Set.of("unknown-future", "foreignfuture")),
                "event only removes; future and unresolved foreign roots survive");
        var hidden = f.command("hidden", foreign, null);
        f.commands.put("hidden", hidden);
        labels = new LinkedHashSet<>(Set.of("core"));
        f.listener.onCommands(new PlayerCommandSendEvent(user.player, labels));
        check(labels.equals(Set.of("core")), "filter never re-adds upstream omitted commands");
        int beforeExecution = f.executions;
        f.commands.get("profile").execute(user.player, "profile", new String[0]);
        check(
                f.executions == beforeExecution + 1,
                "cosmetic same-name visibility never changes manual execution");
    }

    private static void runtime(Fixture f) throws Exception {
        var user = f.user();
        user.attachment.setPermission("vapeecore.utility.fly", true);
        user.player.updateCommands();
        check(
                user.last.contains("fly") && user.updates == 1,
                "permission grant becomes visible on the next standard command update");
        user.attachment.setPermission("vapeecore.utility.fly", false);
        user.player.updateCommands();
        check(
                !user.last.contains("fly") && user.updates == 2,
                "permission revoke becomes invisible on the next command update");
        f.filtered(user);
        check(user.updates == 2, "command event itself never requests another update");
        f.config.set(new CommandVisibilityConfig(false, false));
        check(
                f.filtered(user).equals(f.commands.keySet()),
                "disabled filter preserves exactly the incoming collection");
        f.config.set(new CommandVisibilityConfig(true, false));
        f.commands.put("profile", f.command("profile", f.plugin, "vapeecore.profile.view"));
        check(
                f.filtered(user).contains("profile"),
                "profile cosmetic option can be disabled without granting permissions");
        f.config.set(CommandVisibilityConfig.defaults());
        user.online = false;
        int reads = f.lookups;
        var labels = new LinkedHashSet<>(f.commands.keySet());
        f.listener.onCommands(new PlayerCommandSendEvent(user.player, labels));
        check(
                f.lookups == reads && labels.equals(f.commands.keySet()),
                "logged-out player is not consulted for permissions or commands");
        user.online = true;
    }

    private static void lifecycle(Fixture f) throws Exception {
        int before = HandlerList.getRegisteredListeners(f.plugin).size();
        var module = new PermissionModule(f.plugin, f.config::get);
        module.enable();
        check(
                module.getLuckPermsService() != null
                        && HandlerList.getRegisteredListeners(f.plugin).size() == before + 1,
                "existing module owns exactly one command-send listener");
        var old = f.lastRegistered;
        boolean refused = false;
        try {
            module.enable();
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused && f.lastRegistered == old, "double enable cannot register a second handler");
        for (int i = 0; i < 3; i++) {
            f.config.set(new CommandVisibilityConfig(true, i % 2 == 0));
            check(
                    HandlerList.getRegisteredListeners(f.plugin).size() == before + 1,
                    "policy refresh creates no duplicate handler");
        }
        module.disable();
        module.disable();
        check(
                HandlerList.getRegisteredListeners(f.plugin).size() == before,
                "disable and repeated disable remove only owned handler");
        var user = f.user();
        var labels = new LinkedHashSet<>(f.commands.keySet());
        old.onCommands(new PlayerCommandSendEvent(user.player, labels));
        check(labels.equals(f.commands.keySet()), "stopped listener references are inert");
        module.enable();
        check(
                HandlerList.getRegisteredListeners(f.plugin).size() == before + 1,
                "re-enable acquires one fresh listener");
        module.disable();
        f.failRegistration = true;
        boolean failed = false;
        try {
            module.enable();
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(
                failed && HandlerList.getRegisteredListeners(f.plugin).size() == before,
                "partial external registration failure rolls back its handler");
        boolean unavailable = false;
        try {
            module.getLuckPermsService();
        } catch (NullPointerException expected) {
            unavailable = true;
        }
        check(unavailable, "failed startup does not publish its service");
        f.failRegistration = false;
        module.enable();
        module.disable();
        check(
                HandlerList.getRegisteredListeners(f.plugin).size() == before,
                "successful restart after failed acquisition leaves no leaked handler");
        f.config.set(CommandVisibilityConfig.defaults());
    }

    private static final class Fixture implements AutoCloseable {
        final Logger logger = Logger.getAnonymousLogger();
        final JavaPlugin plugin = allocate(Shell.class);
        final PluginDescriptionFile descriptor;
        final Map<String, Command> commands = new LinkedHashMap<>();
        final Map<String, Permission> permissions = new LinkedHashMap<>();
        final Set<String> vapeeLabels = new LinkedHashSet<>();
        final AtomicReference<CommandVisibilityConfig> config =
                new AtomicReference<>(CommandVisibilityConfig.defaults());
        final CommandVisibilityListener listener;
        final Server previous;
        final Server server;
        int executions, lookups;
        boolean failRegistration;
        CommandVisibilityListener lastRegistered;

        Fixture() throws Exception {
            logger.setUseParentHandlers(false);
            try (var input = Files.newInputStream(Path.of("src/main/resources/plugin.yml"))) {
                descriptor = new PluginDescriptionFile(input);
            }
            set(plugin, "description", descriptor);
            set(plugin, "pluginMeta", descriptor);
            set(plugin, "logger", logger);
            set(plugin, "isEnabled", true);
            for (Permission permission : descriptor.getPermissions())
                permissions.put(permission.getName(), permission);
            LuckPerms lp = proxy(LuckPerms.class, (m, a) -> null);
            var services =
                    proxy(
                            ServicesManager.class,
                            (m, a) ->
                                    m.equals("getRegistration")
                                            ? new RegisteredServiceProvider<>(
                                                    LuckPerms.class,
                                                    lp,
                                                    ServicePriority.Normal,
                                                    plugin)
                                            : null);
            var manager =
                    proxy(
                            PluginManager.class,
                            (m, a) ->
                                    switch (m) {
                                        case "getPermission" -> permissions.get(a[0]);
                                        case "getDefaultPermissions" ->
                                                new HashSet<>(
                                                        permissions.values().stream()
                                                                .filter(
                                                                        p ->
                                                                                p.getDefault()
                                                                                        .getValue(
                                                                                                (boolean)
                                                                                                        a[
                                                                                                                0]))
                                                                .toList());
                                        case "subscribeToPermission",
                                                        "unsubscribeFromPermission",
                                                        "subscribeToDefaultPerms",
                                                        "unsubscribeFromDefaultPerms" ->
                                                null;
                                        case "registerEvents" -> {
                                            lastRegistered = (CommandVisibilityListener) a[0];
                                            PlayerCommandSendEvent.getHandlerList()
                                                    .register(
                                                            new RegisteredListener(
                                                                    lastRegistered,
                                                                    (l, e) ->
                                                                            ((CommandVisibilityListener)
                                                                                            l)
                                                                                    .onCommands(
                                                                                            (PlayerCommandSendEvent)
                                                                                                    e),
                                                                    EventPriority.HIGHEST,
                                                                    plugin,
                                                                    false));
                                            if (failRegistration)
                                                throw new IllegalStateException(
                                                        "injected late registration failure");
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected plugin manager call: " + m);
                                    });
            CommandMap commandMap =
                    proxy(
                            CommandMap.class,
                            (m, a) -> m.equals("getCommand") ? commands.get(a[0]) : null);
            server =
                    proxy(
                            Server.class,
                            (m, a) ->
                                    switch (m) {
                                        case "getPluginManager" -> manager;
                                        case "getServicesManager" -> services;
                                        case "getCommandMap" -> commandMap;
                                        case "isPrimaryThread" -> true;
                                        default -> null;
                                    });
            set(plugin, "server", server);
            Field global = Bukkit.class.getDeclaredField("server");
            global.setAccessible(true);
            previous = (Server) global.get(null);
            global.set(null, server);
            for (var entry : descriptor.getCommands().entrySet()) {
                var command =
                        command(
                                entry.getKey(),
                                plugin,
                                (String) entry.getValue().get("permission"));
                commands.put(entry.getKey(), command);
                Object aliases = entry.getValue().get("aliases");
                if (aliases instanceof List<?> list)
                    for (Object alias : list) commands.put((String) alias, command);
            }
            vapeeLabels.addAll(commands.keySet());
            listener =
                    new CommandVisibilityListener(
                            plugin,
                            config::get,
                            label -> {
                                lookups++;
                                return commands.get(label);
                            });
        }

        Plugin foreign(String name) {
            return proxy(
                    Plugin.class,
                    (m, a) -> m.equals("getName") ? name : m.equals("isEnabled") ? true : null);
        }

        PluginCommand command(String name, Plugin owner, String permission) throws Exception {
            var ctor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            ctor.setAccessible(true);
            var command = ctor.newInstance(name, owner);
            command.setPermission(permission);
            command.setExecutor(
                    (sender, c, label, args) -> {
                        executions++;
                        return true;
                    });
            return command;
        }

        User user() {
            return new User(this);
        }

        Set<String> filtered(User user) {
            var roots = new LinkedHashSet<>(commands.keySet());
            listener.onCommands(new PlayerCommandSendEvent(user.player, roots));
            return roots;
        }

        public void close() throws Exception {
            HandlerList.unregisterAll(plugin);
            Field global = Bukkit.class.getDeclaredField("server");
            global.setAccessible(true);
            global.set(null, previous);
        }
    }

    private static final class User {
        boolean op, online = true;
        int updates, messages;
        Set<String> last = Set.of();
        final Player player;
        PermissibleBase permissions;
        final PermissionAttachment attachment;

        User(Fixture f) {
            player =
                    proxy(
                            Player.class,
                            (m, a) ->
                                    switch (m) {
                                        case "isOnline" -> online;
                                        case "isOp" -> op;
                                        case "getServer" -> f.server;
                                        case "hasPermission" -> {
                                            if (!online)
                                                throw new AssertionError(
                                                        "Offline permission access");
                                            yield a[0] instanceof Permission p
                                                    ? permissions.hasPermission(p)
                                                    : permissions.hasPermission((String) a[0]);
                                        }
                                        case "recalculatePermissions" -> {
                                            permissions.recalculatePermissions();
                                            yield null;
                                        }
                                        case "updateCommands" -> {
                                            updates++;
                                            if (updates > 5)
                                                throw new AssertionError("Recursive update");
                                            last = f.filtered(this);
                                            yield null;
                                        }
                                        case "sendMessage" -> {
                                            messages++;
                                            yield null;
                                        }
                                        default -> null;
                                    });
            permissions = new PermissibleBase(player);
            attachment = permissions.addAttachment(f.plugin);
        }
    }

    private static final class Shell extends JavaPlugin {}

    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }
}
