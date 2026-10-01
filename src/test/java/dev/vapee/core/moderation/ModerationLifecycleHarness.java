package dev.vapee.core.moderation;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.reload.ReloadParticipant;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.Listener;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.rank.RankModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class ModerationLifecycleHarness {
    private static int checks;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-moderation-lifecycle-");
        try {
            lifecycle(directory);
            startupRollback(directory);
            hookLifecycle();
            pendingCommands();
            wiring();
            System.out.println("ModerationLifecycleHarness passed " + checks + " checks.");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void lifecycle(Path directory) throws Exception {
        Path file = directory.resolve("moderation.yml");
        CounterRepository repository = new CounterRepository(new FileModerationRepository(file, logger()));
        ModerationModule module = module(() -> repository);
        check(module.getName().equals("Moderation"), "module name");
        disabled(module);
        module.disable();
        disabled(module);
        module.enable();
        ModerationService service = module.getModerationService();
        check(repository.loads == 1 && repository.saves == 0 && service.getAllRecords().isEmpty(), "actual enable loads only");
        check(Files.notExists(file), "missing data not created by enable");
        try { module.enable(); throw new AssertionError("double enable accepted"); }
        catch (IllegalStateException expected) { checks++; }
        check(module.getModerationService() == service && repository.loads == 1, "double enable does not replace active service");
        module.disable();
        disabled(module);
        check(repository.saves == 0 && Files.notExists(file), "empty disable performs no save");
        module.enable();
        ModerationRecord warning = module.getModerationService().issueWarning(new UUID(0, 90),
                ModerationActor.console(), "Lifecycle test").record().orElseThrow();
        check(repository.loads == 2 && repository.saves == 1 && Files.isRegularFile(file), "first domain mutation writes once");
        byte[] before = Files.readAllBytes(file);
        module.disable();
        disabled(module);
        check(repository.saves == 1 && java.util.Arrays.equals(before, Files.readAllBytes(file)), "disable no history rewrite");
        module.enable();
        check(module.getModerationService().getRecord(warning.id()).orElseThrow().equals(warning), "re-enable loads history");
        check(module.getModerationService() != service && repository.loads == 3 && repository.saves == 1, "fresh service only on enable");
        module.disable();
        disabled(module);
    }

    private static void startupRollback(Path directory) throws Exception {
        Path file = directory.resolve("corrupt.yml");
        Files.writeString(file, "schema-version: 99\nrecords: []\n");
        byte[] before = Files.readAllBytes(file);
        ModerationModule module = module(() -> new FileModerationRepository(file, logger()));
        ModuleManager manager = new ModuleManager(logger());
        List<String> events = new ArrayList<>();
        manager.register(tracker("Permission", events));
        manager.register(tracker("Identity", events));
        manager.register(module);
        manager.register(tracker("Friend", events));
        try { manager.enableAll(); throw new AssertionError("corrupt startup succeeded"); }
        catch (ModerationRepositoryException expected) { checks++; }
        disabled(module);
        check(manager.getEnabledModules().isEmpty(), "failed startup leaves no enabled modules");
        check(events.equals(List.of("enable Permission", "enable Identity", "disable Identity", "disable Permission")),
                "actual ModuleManager rolls back predecessors backwards and never starts Friend");
        check(java.util.Arrays.equals(before, Files.readAllBytes(file)), "startup failure never overwrites source");
        manager.disableAll();
        disabled(module);
        Files.writeString(file, "schema-version: 1\nrecords: []\n");
        manager.enableAll();
        check(manager.getEnabledModules().size() == 4 && module.getModerationService().getAllRecords().isEmpty(),
                "same manager/module recovers only after valid source");
        manager.disableAll();
        disabled(module);
        check(events.subList(events.size() - 3, events.size()).equals(
                List.of("disable Friend", "disable Identity", "disable Permission")), "normal reverse shutdown");
    }

    private static void wiring() throws Exception {
        check(CoreModule.class.isAssignableFrom(ModerationModule.class)
                && !ReloadParticipant.class.isAssignableFrom(ModerationModule.class), "one CoreModule, no reload participant");
        check(List.of(ModerationModule.class.getConstructor(JavaPlugin.class, IdentityModule.class, RankModule.class, MessageService.class).getParameterTypes())
                .equals(List.of(JavaPlugin.class, IdentityModule.class, RankModule.class, MessageService.class)), "explicit plugin/identity/rank/message dependencies");
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        List<String> expected = List.of("permissionModule", "rankModule", "playerModule", "socialModule", "economyModule",
                "identityModule", "moderationModule", "friendModule", "presenceModule", "clanModule", "rewardModule", "onlineRewardModule",
                "questModule", "dailyQuestModule", "lobbyModule", "visibilityModule", "chatModule", "privateMessageModule",
                "presentationModule", "settingsModule", "activityModule", "utilityModule", "seatModule", "worldDisplayModule",
                "blackjackModule", "warpModule", "lobbyExperienceModule");
        var matcher = Pattern.compile("moduleManager\\.register\\((\\w+)\\);").matcher(core);
        List<String> actual = new ArrayList<>();
        while (matcher.find()) actual.add(matcher.group(1));
        check(actual.equals(expected), "exactly 27 registrations, Identity -> Moderation -> Friend -> Presence");
        check(core.indexOf("identityModule = new IdentityModule") < core.indexOf("moderationModule = new ModerationModule(this, identityModule, rankModule, messageService)")
                && core.indexOf("moderationModule = new ModerationModule(this, identityModule, rankModule, messageService)") < core.indexOf("friendModule = new FriendModule"),
                "module constructed in intended dependency order");
        var reload = Pattern.compile("List\\.of\\(configService,\\s*lobbyModule,\\s*chatModule,\\s*privateMessageModule,\\s*"
                + "presentationModule,\\s*dailyQuestModule\\)").matcher(core);
        check(reload.find(), "exact six original reload participants");
        String module = Files.readString(Path.of("src/main/java/dev/vapee/core/moderation/ModerationModule.java"));
        check(module.contains("resolve(\"moderation.yml\")") && module.contains("plugin.getLogger()")
                && module.contains("Clock.systemUTC()") && module.contains("UUID::randomUUID"), "runtime file, logger, clock and UUID sources");
        check(module.contains("getCommand(") && module.contains("registerEvents(") && module.contains("runTask(plugin, task)")
                && !module.contains(".save(") && !module.contains("runTaskTimer"), "one-shot feedback only, no shutdown save/poller");
        check(module.contains("command.setExecutor(null)") && module.contains("command.setTabCompleter(null)")
                && module.contains("HandlerList.unregisterAll(listener)"), "real Bukkit hook cleanup");
        check(!Files.exists(Path.of("src/main/resources/moderation.yml")), "runtime data not a bundled resource");
        try (var paths = Files.list(Path.of("src/main/java/dev/vapee/core/moderation"))) {
            StringBuilder production = new StringBuilder();
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) production.append(Files.readString(path));
            for (String forbidden : List.of("dev.vapee.core.player", "dev.vapee.core.chat",
                    "dev.vapee.core.privatemessage", "AsyncPlayerPreLoginEvent", "Bukkit.getBanList",
                    "getBanList(", "getOfflinePlayer(", "runTaskTimer", "MiniMessage", "ReloadParticipant")) {
                check(!production.toString().contains(forbidden), "no forbidden moderation dependency/feature: " + forbidden);
            }
        }
        for (String domain : List.of("ModerationService", "ModerationRecord", "ModerationActor", "ModerationSnapshot", "FileModerationRepository", "ModerationMuteProjection")) {
            String source = Files.readString(Path.of("src/main/java/dev/vapee/core/moderation/" + domain + ".java"));
            check(!source.contains("org.bukkit") && !source.contains("dev.vapee.core.identity"), "domain stays Bukkit/identity-free " + domain);
        }
    }

    private static ModerationModule module(java.util.function.Supplier<ModerationRepository> factory) throws Exception {
        var fixture = new ModerationTestSupport.Fixture();
        return new ModerationModule(factory, CLOCK, UUID::randomUUID, fixture::context, new TestHooks());
    }

    private static void hookLifecycle() throws Exception {
        var fixture = new ModerationTestSupport.Fixture();
        TestHooks hooks = new TestHooks();
        ModerationModule module = new ModerationModule(() -> fixture.repository, fixture.clock, UUID::randomUUID, fixture::context, hooks);
        int initialLoads = fixture.repository.loads;
        module.enable();
        check(hooks.names.equals(List.of("warn", "ban", "unban", "kick", "history", "mute", "unmute")), "seven commands in owned lifecycle");
        check(hooks.executors.size() == 7 && hooks.completers.equals(hooks.executors), "same executor/completer installed");
        check(hooks.listeners.size() == 2, "login and chat listeners registered");
        check(module.getModerationService() != null && fixture.repository.loads == initialLoads + 1, "service published after successful hooks");
        var oldProjection = module.getMuteProjection();
        module.getModerationService().issueMute(ModerationTestSupport.TARGET, ModerationActor.console(), "Restart mute", Optional.empty());
        check(oldProjection.isMuted(ModerationTestSupport.TARGET), "successful mute immediately published");
        module.disable(); disabled(module);
        check(!oldProjection.isMuted(ModerationTestSupport.TARGET), "disabled projection cleared");
        check(hooks.executors.isEmpty() && hooks.completers.isEmpty() && hooks.listeners.isEmpty(), "disable clears all command/listener hooks");
        check(fixture.repository.saves == 1, "disable no extra persistence");
        module.enable();
        check(module.getMuteProjection().isMuted(ModerationTestSupport.TARGET), "restart loaded mute immediately active");
        module.disable();
        for (int boundary = 1; boundary <= 9; boundary++) {
            TestHooks failing = new TestHooks(); failing.failAt = boundary;
            ModerationModule broken = new ModerationModule(() -> fixture.repository, fixture.clock, UUID::randomUUID, fixture::context, failing);
            try { broken.enable(); throw new AssertionError("partial hook failure accepted"); }
            catch (IllegalStateException expected) { checks++; }
            disabled(broken);
            check(failing.executors.isEmpty() && failing.completers.isEmpty() && failing.listeners.isEmpty(),
                    "cleanup every partial enable boundary " + boundary);
            check(fixture.repository.saves == 1, "partial failure never writes " + boundary);
            failing.failAt = 0;
            broken.enable(); check(broken.getModerationService() != null, "retry after hook cleanup " + boundary);
            broken.disable(); disabled(broken);
        }
    }

    private static void pendingCommands() throws Exception {
        for (boolean completeBeforeDisable : List.of(false, true)) {
            var fixture = new ModerationTestSupport.Fixture();
            fixture.primaryGroups.remove(ModerationTestSupport.OFFLINE);
            var future = new java.util.concurrent.CompletableFuture<Optional<String>>();
            fixture.groupLoads.put(ModerationTestSupport.OFFLINE, future);
            var hooks = new TestHooks();
            var module = new ModerationModule(() -> fixture.repository, fixture.clock, UUID::randomUUID,
                    fixture::context, hooks, fixture.mainTasks::add);
            module.enable();
            var oldExecutor = hooks.executors.get("warn");
            oldExecutor.onCommand(fixture.staff.sender, null, "warn", new String[]{"Offline", "pending"});
            check(fixture.groupLoadCalls == 1 && fixture.repository.saves == 0, "module-owned pending resolution");
            if (completeBeforeDisable) {
                future.complete(Optional.of("default"));
                check(fixture.mainTasks.size() == 1, "module queues main continuation");
            }
            module.disable();
            module.enable();
            if (!completeBeforeDisable) future.complete(Optional.of("default"));
            Runnable task;
            while ((task = fixture.mainTasks.poll()) != null) task.run();
            check(fixture.repository.saves == 0 && fixture.staff.output.isEmpty() && fixture.info() == 0,
                    "old pending/queued command cancelled across disable and re-enable");
            oldExecutor.onCommand(fixture.staff.sender, null, "warn", new String[]{"Alex", "obsolete"});
            check(fixture.repository.saves == 0, "obsolete executor cannot use replacement module state");
            hooks.executors.get("warn").onCommand(fixture.staff.sender, null, "warn", new String[]{"Alex", "current"});
            check(fixture.repository.saves == 1, "new enable generation operates normally");
            module.disable();
        }
    }

    private static final class TestHooks implements ModerationModule.Hooks {
        final List<String> names = new ArrayList<>();
        final java.util.Map<String, TabExecutor> executors = new java.util.LinkedHashMap<>(), completers = new java.util.LinkedHashMap<>();
        final List<Listener> listeners = new ArrayList<>();
        int failAt, calls;
        public void install(String name, TabExecutor executor) {
            names.add(name); executors.put(name, executor);
            if (++calls == failAt) throw new IllegalStateException("injected setter failure");
            completers.put(name, executor);
        }
        public void register(Listener listener) {
            listeners.add(listener);
            if (++calls == failAt) throw new IllegalStateException("injected listener registration failure");
        }
        public void clearCommands() { executors.clear(); completers.clear(); calls = 0; }
        public void unregister(Listener listener) { listeners.remove(listener); }
    }

    private static CoreModule tracker(String name, List<String> events) {
        return new CoreModule() {
            public String getName() { return name; }
            public void enable() { events.add("enable " + name); }
            public void disable() { events.add("disable " + name); }
        };
    }
    private static final class CounterRepository implements ModerationRepository {
        private final ModerationRepository delegate;
        int loads, saves;
        CounterRepository(ModerationRepository delegate) { this.delegate = delegate; }
        public ModerationSnapshot initialize() { loads++; return delegate.initialize(); }
        public void save(ModerationSnapshot snapshot) { saves++; delegate.save(snapshot); }
    }
    private static void disabled(ModerationModule module) {
        boolean failed = false;
        try { module.getModerationService(); } catch (IllegalStateException expected) { failed = true; }
        check(failed, "disabled/failed module exposes no service");
        failed = false;
        try { module.getMuteProjection(); } catch (IllegalStateException expected) { failed = true; }
        check(failed, "disabled/failed module exposes no projection");
    }
    private static Logger logger() {
        Logger logger = Logger.getLogger("ModerationLifecycleHarness-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        return logger;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
