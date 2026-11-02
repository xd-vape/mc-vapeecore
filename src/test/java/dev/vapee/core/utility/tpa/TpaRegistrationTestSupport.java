package dev.vapee.core.utility.tpa;

import dev.vapee.core.module.ModuleRuntimeFixture;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;

/** Real PluginCommand leases and request runtime; controlled registration/observer adapters. */
public final class TpaRegistrationTestSupport implements AutoCloseable {
    public final ModuleRuntimeFixture paper;
    public final TpaTestSupport.Fixture feature = new TpaTestSupport.Fixture();
    public final Map<String, Command> routes = new LinkedHashMap<>();
    public final List<PluginCommand> commands = new ArrayList<>();
    public final CommandExecutor originalExecutor = (s,c,l,a) -> true;
    public final TabCompleter originalCompleter = (s,c,l,a) -> List.of();
    public final List<String> events = new ArrayList<>();
    public final RuntimeException fault = new IllegalStateException("controlled registration failure");
    public final TeleportRequestRegistration registration;
    public boolean listener, observer, foreignOwner;
    public String fail = "";
    public TpaRegistrationTestSupport() throws Exception {
        paper = new ModuleRuntimeFixture(); feature.requests.disable();
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class); constructor.setAccessible(true);
        for (String name : TeleportRequests.COMMANDS) {
            PluginCommand command = constructor.newInstance(name, paper.plugin);
            command.setExecutor(originalExecutor); command.setTabCompleter(originalCompleter); commands.add(command);
            routes.put(name, command); routes.put("vapeecore:" + name, command);
        }
        registration = new TeleportRequestRegistration(feature.requests, routes::get, command -> !foreignOwner && command.getPlugin() == paper.plugin,
                requests -> { listener=true; events.add("register"); fault("register"); },
                requests -> { listener=false; events.add("unregister"); fault("unregister"); },
                requests -> { observer=true; events.add("subscribe"); fault("subscribe"); },
                requests -> { observer=false; events.add("unsubscribe"); fault("unsubscribe"); });
    }
    private void fault(String stage) { if(fail.equals(stage)) throw fault; }
    public boolean original() {
        return commands.stream().allMatch(command -> command.getExecutor()==originalExecutor && command.getTabCompleter()==originalCompleter);
    }
    public void close() throws Exception { fail=""; registration.disable(); paper.close(); }
}
