package dev.vapee.core.permission;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Frozen audited descriptor contract; supplements, not substitutes, real executor behavior tests. */
public final class PermissionDescriptorHarness {
    private static int checks;
    private record Root(String permission, List<String> aliases, String usage) { }
    private record Node(PermissionDefault defaultValue, Set<String> children) { }
    private static final Map<String, Root> ROOTS = Map.ofEntries(
            Map.entry("quests", new Root("vapeecore.quest.use", List.of("quest"), "/quests")),
            Map.entry("mute", new Root("vapeecore.moderation.mute", List.of(), "/mute <player|uuid> <duration|permanent> <reason...>")),
            Map.entry("unmute", new Root("vapeecore.moderation.unmute", List.of(), "/unmute <player|uuid> [reason...]")),
            Map.entry("warn", new Root("vapeecore.moderation.warn", List.of(), "/warn <player|uuid> <reason...>")),
            Map.entry("ban", new Root("vapeecore.moderation.ban", List.of(), "/ban <player|uuid> <duration|permanent> <reason...>")),
            Map.entry("unban", new Root("vapeecore.moderation.unban", List.of(), "/unban <player|uuid> [reason...]")),
            Map.entry("kick", new Root("vapeecore.moderation.kick", List.of(), "/kick <player|uuid> <reason...>")),
            Map.entry("history", new Root("vapeecore.moderation.history", List.of(), "/history <player|uuid> [page]")),
            Map.entry("vapeecore", new Root("", List.of("core"), "/core help")),
            Map.entry("spawn", new Root("vapeecore.lobby.spawn", List.of(), "/spawn")),
            Map.entry("setspawn", new Root("vapeecore.lobby.setspawn", List.of(), "/setspawn")),
            Map.entry("coins", new Root("vapeecore.economy.coins", List.of(), "/coins help")),
            Map.entry("profile", new Root("vapeecore.profile.view", List.of(), "/profile [player|uuid]")),
            Map.entry("friend", new Root("vapeecore.friend.use", List.of("friends"), "/friend help")),
            Map.entry("clan", new Root("vapeecore.clan.use", List.of("clans"), "/clan help")),
            Map.entry("msg", new Root("vapeecore.message.use", List.of(), "/msg <player> <message>")),
            Map.entry("reply", new Root("vapeecore.message.use", List.of("r"), "/reply <message>")),
            Map.entry("settings", new Root("vapeecore.settings.use", List.of(), "/settings [visibility ...]")),
            Map.entry("ignore", new Root("vapeecore.social.ignore", List.of(), "/ignore <player>")),
            Map.entry("unignore", new Root("vapeecore.social.ignore", List.of(), "/unignore <player|uuid>")),
            Map.entry("ignorelist", new Root("vapeecore.social.ignore", List.of(), "/ignorelist")),
            Map.entry("blackjack", new Root("vapeecore.blackjack.admin", List.of(), "/blackjack help")),
            Map.entry("warp", new Root("vapeecore.warp.admin", List.of(), "/warp help")),
            Map.entry("build", new Root("vapeecore.utility.build", List.of(), "/build")),
            Map.entry("fly", new Root("vapeecore.utility.fly", List.of(), "/fly [player]")),
            Map.entry("speed", new Root("vapeecore.utility.speed", List.of(), "/speed <1-10> [player]")),
            Map.entry("gamemode", new Root("vapeecore.utility.gamemode", List.of("gm"), "/gamemode <mode> [player]")),
            Map.entry("tp", new Root("vapeecore.utility.teleport", List.of("teleport"), "/tp <player> | /tp <x> <y> <z> [yaw pitch] | /tp <source> <target> | /tp <source> <x> <y> <z> [yaw pitch] | /tp world <world> <x> <y> <z> [yaw pitch] | /tp <source> world <world> <x> <y> <z> [yaw pitch]")),
            Map.entry("tphere", new Root("vapeecore.utility.teleport.here", List.of(), "/tphere <player>")),
            Map.entry("heal", new Root("vapeecore.utility.heal", List.of(), "/heal [player]")),
            Map.entry("feed", new Root("vapeecore.utility.feed", List.of(), "/feed [player]")),
            Map.entry("ping", new Root("vapeecore.utility.ping", List.of(), "/ping [player]")),
            Map.entry("clear", new Root("vapeecore.utility.clear", List.of(), "/clear [player]")),
            Map.entry("invsee", new Root("vapeecore.utility.invsee", List.of(), "/invsee <player>")),
            Map.entry("enderchest", new Root("vapeecore.utility.enderchest", List.of(), "/enderchest [player]")),
            Map.entry("rank", new Root("vapeecore.rank.view", List.of(), "/rank [player]")),
            Map.entry("ranks", new Root("vapeecore.ranks.view", List.of(), "/ranks"))
    );
    private static final Map<String, Node> NODES = Map.ofEntries(
            Map.entry("vapeecore.quest.use", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.moderation.mute", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.moderation.unmute", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.moderation.warn", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.moderation.ban", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.moderation.unban", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.moderation.kick", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.moderation.history", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.admin", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.lobby.spawn", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.lobby.setspawn", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.lobby.build", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.build"))),
            Map.entry("vapeecore.utility.build", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.fly", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.fly.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.fly"))),
            Map.entry("vapeecore.utility.speed", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.speed.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.speed"))),
            Map.entry("vapeecore.utility.gamemode", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.gamemode.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.gamemode"))),
            Map.entry("vapeecore.utility.teleport", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.teleport.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.teleport"))),
            Map.entry("vapeecore.utility.teleport.world", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.teleport"))),
            Map.entry("vapeecore.utility.teleport.others.world", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.teleport.others", "vapeecore.utility.teleport.world"))),
            Map.entry("vapeecore.utility.teleport.bypass", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.teleport.here", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.heal", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.heal.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.heal"))),
            Map.entry("vapeecore.utility.feed", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.feed.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.feed"))),
            Map.entry("vapeecore.utility.ping", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.ping.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.ping"))),
            Map.entry("vapeecore.utility.clear", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.clear.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.clear"))),
            Map.entry("vapeecore.utility.invsee", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.invsee.modify", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.invsee"))),
            Map.entry("vapeecore.utility.enderchest", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.utility.enderchest.others", new Node(PermissionDefault.OP, Set.of("vapeecore.utility.enderchest"))),
            Map.entry("vapeecore.economy.coins", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.profile.view", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.friend.use", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.clan.use", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.visibility.staff", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.economy.admin", new Node(PermissionDefault.OP, Set.of("vapeecore.economy.coins"))),
            Map.entry("vapeecore.message.use", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.settings.use", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.social.ignore", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.blackjack.admin", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.warp.admin", new Node(PermissionDefault.OP, Set.of())),
            Map.entry("vapeecore.rank.view", new Node(PermissionDefault.TRUE, Set.of())),
            Map.entry("vapeecore.ranks.view", new Node(PermissionDefault.TRUE, Set.of()))
    );

    public static void main(String[] args) throws Exception {
        PluginDescriptionFile descriptor;
        try (var stream = PermissionDescriptorHarness.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            if (stream == null) throw new AssertionError("missing descriptor");
            descriptor = new PluginDescriptionFile(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        check(descriptor.getCommands().keySet().equals(ROOTS.keySet()) && ROOTS.size() == 37, "exact 37 command roots");
        check(descriptor.getDepend().equals(List.of("LuckPerms")), "required LP dependency");
        Set<String> identifiers = new HashSet<>(ROOTS.keySet());
        for (var entry : ROOTS.entrySet()) {
            String root = entry.getKey();
            Root expected = entry.getValue();
            Map<String, Object> actual = descriptor.getCommands().get(root);
            check(expected.permission().equals(actual.getOrDefault("permission", "")), root + " descriptor gate");
            check(expected.usage().equals(actual.get("usage")), root + " exact syntax");
            check(expected.aliases().equals(actual.getOrDefault("aliases", List.of())), root + " exact aliases");
            check(actual.get("description") instanceof String value && !value.isBlank(), root + " description");
            for (String alias : expected.aliases()) check(identifiers.add(alias), "no alias/root collisions " + alias);
            check(expected.permission().isEmpty() || NODES.containsKey(expected.permission()), root + " gate node registered");
        }
        Map<String, Permission> permissions = new HashMap<>();
        descriptor.getPermissions().forEach(permission -> permissions.put(permission.getName(), permission));
        check(permissions.keySet().equals(NODES.keySet()) && NODES.size() == 50, "all 50 nodes exactly inventoried");
        for (var entry : NODES.entrySet()) {
            Permission actual = permissions.get(entry.getKey());
            check(actual.getDefault() == entry.getValue().defaultValue(), entry.getKey() + " explicit default");
            check(actual.getChildren().keySet().equals(entry.getValue().children())
                    && actual.getChildren().values().stream().allMatch(Boolean.TRUE::equals), entry.getKey() + " exact children");
            check(actual.getChildren().keySet().stream().allMatch(NODES::containsKey), "all child nodes exist");
            check(!actual.getName().contains("*"), "no wildcard");
            walk(actual.getName(), permissions, new HashSet<>());
        }
        check(NODES.values().stream().filter(node -> node.defaultValue() == PermissionDefault.TRUE).count() == 11, "eleven player-base defaults");
        check(NODES.values().stream().mapToInt(node -> node.children().size()).sum() == 15, "same fifteen child edges");
        for (String action : List.of("mute", "unmute", "warn", "ban", "unban", "kick", "history"))
            check(permissions.get("vapeecore.moderation." + action).getChildren().isEmpty(), "independent moderation " + action);
        for (String node : List.of("vapeecore.admin", "vapeecore.visibility.staff"))
            check(permissions.get(node).getChildren().isEmpty(), node + " not capability bundle");
        for (var entry : NODES.entrySet()) for (String child : entry.getValue().children())
            check(!reachable(child, entry.getKey(), permissions, new HashSet<>()), "no reverse child inheritance");
        String invsee = Files.readString(Path.of("src/main/java/dev/vapee/core/utility/command/InvseeCommand.java"));
        check(invsee.contains("Reserved") || invsee.split("MODIFY_PERMISSION", -1).length == 2,
                "invsee modify defined but not an execution capability");
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(core.split("moduleManager.register\\(", -1).length - 1 == 27, "27 modules");
        check(core.contains("List.of(configService, lobbyModule, chatModule, privateMessageModule,")
                && core.contains("presentationModule, dailyQuestModule)"), "same six reload participants");
        String utility = Files.readString(Path.of("src/main/java/dev/vapee/core/utility/UtilityModule.java"));
        String economy = Files.readString(Path.of("src/main/java/dev/vapee/core/economy/EconomyModule.java"));
        check(utility.contains("rankModule.getStaffHierarchyService()") && economy.contains("rankModule.getStaffHierarchyService()"),
                "both modules explicitly reuse RankModule hierarchy service");
        check(core.indexOf("moduleManager.register(rankModule)") < core.indexOf("moduleManager.register(economyModule)")
                && core.indexOf("moduleManager.register(rankModule)") < core.indexOf("moduleManager.register(utilityModule)"),
                "dependency enable order unchanged");
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                check(!source.contains("hasPermission(\"vapeecore.lobby.build\")"), "legacy build never checked directly");
                if (file.toString().contains("utility") || file.toString().contains("economy"))
                    check(!source.contains("loadPrimaryGroup(") && !source.contains("loadUser("), "no online utility/economy asynchronous LP loads");
            }
        }
        System.out.println("PermissionDescriptorHarness passed " + checks + " checks.");
    }

    private static void walk(String node, Map<String, Permission> permissions, Set<String> path) {
        check(path.add(node), "acyclic children " + node);
        for (String child : permissions.get(node).getChildren().keySet()) walk(child, permissions, path);
        path.remove(node);
    }

    private static boolean reachable(String source, String target, Map<String, Permission> permissions, Set<String> visited) {
        if (source.equals(target)) return true;
        if (!visited.add(source)) return false;
        return permissions.get(source).getChildren().keySet().stream()
                .anyMatch(child -> reachable(child, target, permissions, visited));
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
