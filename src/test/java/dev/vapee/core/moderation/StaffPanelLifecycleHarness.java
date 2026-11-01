package dev.vapee.core.moderation;

import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.moderation.command.ModerationCommandContext;
import dev.vapee.core.staff.StaffPanel;
import dev.vapee.core.staff.StaffPanelTestSupport;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/** Actual ModerationModule acquisition/rollback and actual moderation executors behind the panel flow. */
public final class StaffPanelLifecycleHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        normalAndCommands();
        for (String stage : List.of("factory", "staff.install", "panel.register", "locale.add")) rollback(stage);
        cleanupFailure();
        System.out.println("StaffPanelLifecycleHarness passed " + checks + " checks.");
    }
    private static void normalAndCommands() throws Exception {
        Fixture f = new Fixture(); f.module.enable();
        check(f.executors.keySet().equals(new LinkedHashSet<>(List.of("warn", "ban", "unban", "kick", "history", "mute", "unmute", "staff"))), "eight commands, existing seven preserved");
        check(f.listeners.size() == 3 && f.listeners.contains(f.ui.panel) && f.executors.get("staff") == f.ui.panel, "real panel registered as command and listener");
        check(f.locales.size() == 2 && f.locales.contains(f.foreignLocale), "owned locale subscription preserves foreign observer");
        f.ui.execute = (viewer, request) -> {
            check(viewer == f.viewer.player && request.target() == f.target.player && request.action() == StaffPanel.Action.HISTORY,
                    "panel delegates history with original Player sender and target session");
            f.executors.get("history").onCommand(viewer, null, "history", new String[]{request.target().getUniqueId().toString()});
        };
        f.ui.open(f.viewer); f.ui.select(f.viewer, f.target); f.ui.click(f.viewer, 11); f.ui.drain();
        check(f.viewer.text().contains("No moderation history") && f.backend.repository.saves == 0, "real HistoryCommand executes read-only through panel");
        String suggestion = suggestion(f, StaffPanel.Action.WARN);
        check(suggestion.equals("/warn " + f.target.id + " ") && f.backend.repository.saves == 0, "warning UI suggests text input with no premature mutation");
        f.viewer.permissions.remove("vapeecore.moderation.warn"); executeSuggested(f, suggestion, "test reason");
        check(f.backend.repository.saves == 0, "real WarnCommand rechecks permission after suggestion");
        f.viewer.permissions.add("vapeecore.moderation.warn"); f.ui.groups.put(f.target.id, "owner"); executeSuggested(f, suggestion, "test reason");
        check(f.backend.repository.saves == 0, "real WarnCommand rechecks current staff hierarchy after suggestion");
        f.ui.groups.put(f.target.id, "default"); executeSuggested(f, suggestion, "test reason");
        ModerationRecord warning = f.module.getModerationService().getHistory(f.target.id).getFirst();
        check(warning.action() == ModerationAction.WARNING && warning.actor().type() == ModerationActorType.PLAYER
                && warning.actor().playerId().orElseThrow().equals(f.viewer.id), "valid warning is recorded by existing service as player, never console");
        String mute = suggestion(f, StaffPanel.Action.MUTE); executeSuggested(f, mute, "5m quiet please");
        check(f.module.getModerationService().getHistory(f.target.id).stream().anyMatch(r -> r.action() == ModerationAction.MUTE && r.actor().playerId().orElseThrow().equals(f.viewer.id)),
                "real duration/reason flow records player mute through existing executor");
        f.ui.open(f.viewer); f.ui.select(f.viewer, f.target); f.ui.click(f.viewer, 12);
        StaffPanel old = f.ui.panel; f.module.disable(); f.module.enable(); f.ui.open(f.viewer);
        var inventory = f.viewer.view.getTopInventory(); int saves = f.backend.repository.saves;
        f.ui.drain();
        check(f.viewer.view.getTopInventory() == inventory && f.backend.repository.saves == saves, "old queued GUI work fenced across actual module disable/re-enable");
        f.viewer.messages.clear(); old.onCommand(f.viewer.player, null, "staff", new String[]{"list"});
        check(f.viewer.messages.isEmpty(), "old command instance inert across runtime replacement");
        f.module.disable(); check(f.listeners.isEmpty() && f.executors.isEmpty() && f.locales.equals(Set.of(f.foreignLocale)), "normal shutdown releases all owned UI/command/listener state");
        f.module.disable(); check(f.locales.equals(Set.of(f.foreignLocale)), "repeat disable preserves foreign locale subscription");
    }
    private static String suggestion(Fixture f, StaffPanel.Action action) {
        f.viewer.messages.clear(); f.ui.open(f.viewer); f.ui.select(f.viewer, f.target);
        f.ui.click(f.viewer, StaffPanel.TARGET_LAYOUT.slot(action)); f.ui.drain();
        return f.viewer.messages.stream().flatMap(c -> clicks(c).stream()).filter(c -> c.action() == ClickEvent.Action.SUGGEST_COMMAND).findFirst().orElseThrow().value();
    }
    private static void executeSuggested(Fixture f, String command, String input) {
        String[] parts = (command.substring(1) + input).split(" ");
        f.executors.get(parts[0]).onCommand(f.viewer.player, null, parts[0], Arrays.copyOfRange(parts, 1, parts.length));
    }
    private static void rollback(String stage) throws Exception {
        Fixture f = new Fixture(); f.fail = stage;
        if (stage.equals("locale.add")) f.locales.failAdd = 2;
        RuntimeException caught = failure(f.module::enable);
        check(caught == (stage.equals("locale.add") ? f.locales.startupFailure : f.failure), "original startup failure retained " + stage);
        check(f.executors.isEmpty() && f.listeners.isEmpty() && f.locales.equals(Set.of(f.foreignLocale)), "all partial UI acquisitions released " + stage);
        check(failure(f.module::getModerationService) instanceof IllegalStateException, "failed module publishes no moderation service " + stage);
        if (!stage.equals("factory")) {
            f.viewer.messages.clear(); f.ui.panel.onCommand(f.viewer.player, null, "staff", new String[]{"list"});
            check(f.viewer.messages.isEmpty() && !f.ui.panel.canOpen(f.viewer.player), "failed panel instance inert " + stage);
        }
        f.fail = ""; f.module.enable(); f.ui.open(f.viewer);
        check(f.viewer.view.getTopInventory().getHolder() != null && f.executors.size() == 8 && f.listeners.size() == 3, "same module retries with fresh panel " + stage);
        f.module.disable(); check(f.locales.equals(Set.of(f.foreignLocale)), "retry shutdown preserves foreign ownership " + stage);
    }
    private static void cleanupFailure() throws Exception {
        Fixture f = new Fixture(); f.module.enable(); f.ui.open(f.viewer);
        f.locales.failRemove = 1; // Removal takes effect, then throws.
        RuntimeException caught = failure(f.module::disable);
        check(caught == f.locales.cleanupFailure && f.listeners.isEmpty() && f.executors.isEmpty(), "locale removal failure does not skip later module cleanup");
        check(!(f.viewer.view.getTopInventory().getHolder() instanceof dev.vapee.core.staff.StaffPanelHolder)
                && f.locales.equals(Set.of(f.foreignLocale)), "owned inventory closes and foreign observer survives failed cleanup");
        f.module.enable(); f.module.disable(); check(f.locales.equals(Set.of(f.foreignLocale)), "module recovers after cleanup diagnostic");
    }
    private static final class Fixture implements ModerationModule.Hooks {
        final StaffPanelTestSupport.Fixture ui = new StaffPanelTestSupport.Fixture();
        final StaffPanelTestSupport.Probe viewer = ui.admin(), target = ui.player("Target", "default");
        final ModerationTestSupport.Fixture backend = new ModerationTestSupport.Fixture();
        final Map<String, TabExecutor> executors = new LinkedHashMap<>();
        final Set<Listener> listeners = new LinkedHashSet<>();
        final Consumer<Player> foreignLocale = p -> { };
        final ModuleRuntimeFixture.FaultSet<Consumer<Player>> locales = new ModuleRuntimeFixture.FaultSet<>();
        final RuntimeException failure = new IllegalStateException("injected staff UI acquisition failure");
        final ModerationModule module;
        String fail = "";
        Fixture() throws Exception {
            ui.panel.disable();
            var field = dev.vapee.core.i18n.LocalizationService.class.getDeclaredField("localeListeners"); field.setAccessible(true);
            field.set(ui.messages.localization(), locales); locales.add(foreignLocale);
            backend.addKnown(viewer.id, viewer.name); backend.addKnown(target.id, target.name);
            module = new ModerationModule(() -> backend.repository, backend.clock, UUID::randomUUID,
                    service -> new ModerationCommandContext(service, backend.identities, ui.messages,
                            id -> ui.players.containsKey(id) ? ui.players.get(id).player : null,
                            () -> ui.players.values().stream().map(p -> p.player).toList(), backend.clock, ui.logger,
                            ui.hierarchy, ui.queued::add, () -> false), this, ui.queued::add,
                    context -> { if (fail.equals("factory")) throw failure; return ui.createPanel(context.active()); });
        }
        public void install(String name, TabExecutor executor) {
            executors.put(name, executor);
            if (name.equals("staff")) {
                check(!ui.panel.canOpen(viewer.player), "panel cannot open before actual module publication");
                if (fail.equals("staff.install")) throw failure;
            }
        }
        public void register(Listener listener) { listeners.add(listener); if (listener instanceof StaffPanel && fail.equals("panel.register")) throw failure; }
        public void clearCommands() { executors.clear(); }
        public void unregister(Listener listener) { listeners.remove(listener); }
    }
    private static List<ClickEvent> clicks(Component c) { List<ClickEvent> result = new ArrayList<>(); if (c.clickEvent() != null) result.add(c.clickEvent()); for (Component child : c.children()) result.addAll(clicks(child)); return result; }
    private static RuntimeException failure(Runnable action) { try { action.run(); } catch (RuntimeException exception) { return exception; } throw new AssertionError("Expected failure"); }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}
