package dev.vapee.core.quest;

import static dev.vapee.core.quest.QuestCompletionFixture.*;

import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.i18n.LocalizedText;
import dev.vapee.core.message.MessageService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

final class QuestHudTestSupport {
    static final World WORLD =
            proxy(
                    World.class,
                    (name, args) ->
                            switch (name) {
                                case "getUID" ->
                                        UUID.nameUUIDFromBytes(
                                                "hud-test-world"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8));
                                case "getName" -> "world";
                                default -> DEFAULT;
                            });

    record Xp(float fraction, int level) {}

    static final class Fixture implements AutoCloseable {
        final QuestCompletionFixture domain = new QuestCompletionFixture();
        final MessageService messages =
                new MessageService(() -> "[Test]", LocalizationService.bundled(domain.logger));
        final Map<UUID, Actor> online = new HashMap<>();
        final List<Component> chat = new ArrayList<>();
        int tick = 100, nextEntity;
        boolean active = true;
        final QuestProgressHud hud =
                new QuestProgressHud(
                        domain.players,
                        domain.quests,
                        domain.registry,
                        messages,
                        id -> online.containsKey(id) ? online.get(id).player : null,
                        id -> online.containsKey(id) && online.get(id).context,
                        () -> tick,
                        () -> active,
                        domain.logger);
        final Consumer<Player> foreground = hud::foreground;
        final QuestProgressReporter reporter =
                new QuestProgressReporter(
                        domain.quests, (id, text) -> chat.add(text), domain.logger);

        Fixture() {
            domain.quests.addProgressListener(hud);
            messages.addActionBarObserver(foreground);
        }

        Actor actor() {
            return actor(domain.load());
        }

        Actor actor(UUID id) {
            Actor actor = new Actor(id, ++nextEntity);
            online.put(id, actor);
            return actor;
        }

        void define(String id, String key, long target, long coins) {
            var definitions = new ArrayList<>(domain.registry.snapshot());
            definitions.removeIf(q -> q.id().equals(id));
            definitions.add(
                    new QuestDefinition(id, id, "Test", QuestProgressKey.of(key), target, coins));
            domain.registry.replaceAll(definitions);
        }

        void localized(String id, long target) {
            domain.registry.replaceAll(
                    List.of(
                            new QuestDefinition(
                                    id,
                                    LocalizedText.parse(
                                            Map.of("en", "Spin 200 times", "de", "Drehwurm 200")),
                                    LocalizedText.literal("Test"),
                                    QuestProgressKey.of("movement:rotation_full"),
                                    target,
                                    7)));
        }

        void assign(Actor actor, String... ids) {
            domain.quests.replaceAssignments(actor.id, List.of(ids));
        }

        QuestProgressResult report(Actor actor, String key, long amount) {
            return reporter.report(actor.id, QuestProgressKey.of(key), amount);
        }

        void pump() {
            hud.tick();
        }

        void advance(int ticks) {
            tick += ticks;
            hud.tick();
        }

        public void close() {
            active = false;
            hud.close();
            domain.quests.removeProgressListener(hud);
            messages.removeActionBarObserver(foreground);
        }
    }

    static final class Actor {
        final UUID id;
        final int entity;
        final Player player;
        final List<Component> bars = new ArrayList<>(), chat = new ArrayList<>();
        final List<Xp> xp = new ArrayList<>();
        Locale locale = Locale.US;
        GameMode mode = GameMode.SURVIVAL;
        Location at = new Location(WORLD, 0, 64, 0);
        boolean online = true, context = true, dead, sprinting;
        float realExp = .25F, clientExp = realExp;
        int realLevel = 12, realTotal = 999, clientLevel = realLevel, mutations, statTicks;
        int xpFailures, actionFailures;
        boolean xpAfter, actionAfter;

        Actor(UUID id, int entity) {
            this.id = id;
            this.entity = entity;
            Input input =
                    proxy(Input.class, (name, args) -> name.equals("isForward") ? true : DEFAULT);
            player =
                    proxy(
                            Player.class,
                            (name, args) ->
                                    switch (name) {
                                        case "getUniqueId" -> id;
                                        case "getEntityId" -> entity;
                                        case "getName" -> "HudPlayer";
                                        case "isOnline" -> online;
                                        case "isDead" -> dead;
                                        case "getGameMode" -> mode;
                                        case "locale" -> locale;
                                        case "getLocale" ->
                                                locale.toLanguageTag().replace('-', '_');
                                        case "getExp" -> realExp;
                                        case "getLevel" -> realLevel;
                                        case "getTotalExperience" -> realTotal;
                                        case "setExp", "setLevel", "giveExp", "giveExpLevels" -> {
                                            mutations++;
                                            throw new AssertionError("Server XP mutation " + name);
                                        }
                                        case "sendActionBar" -> {
                                            boolean fail = actionFailures != 0;
                                            if (actionFailures > 0) actionFailures--;
                                            if (fail && !actionAfter)
                                                throw new IllegalStateException(
                                                        "actionbar before effect");
                                            bars.add((Component) args[0]);
                                            if (fail)
                                                throw new IllegalStateException(
                                                        "actionbar after effect");
                                            yield null;
                                        }
                                        case "sendExperienceChange" -> {
                                            boolean fail = xpFailures != 0;
                                            if (xpFailures > 0) xpFailures--;
                                            if (fail && !xpAfter)
                                                throw new IllegalStateException("XP before effect");
                                            clientExp = (float) args[0];
                                            clientLevel = (int) args[1];
                                            xp.add(new Xp(clientExp, clientLevel));
                                            if (fail)
                                                throw new IllegalStateException("XP after effect");
                                            yield null;
                                        }
                                        case "sendMessage" -> {
                                            if (args[0] instanceof Component component)
                                                chat.add(component);
                                            yield null;
                                        }
                                        case "isOnGround" -> true;
                                        case "getCurrentInput" -> input;
                                        case "getLocation" -> at.clone();
                                        case "getWorld" -> WORLD;
                                        case "isSprinting" -> sprinting;
                                        case "getStatistic" -> statTicks;
                                        default -> DEFAULT;
                                    });
        }

        String bar() {
            return bars.isEmpty() ? "" : plain(bars.getLast());
        }
    }

    static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    static boolean span(Component component, String needle, TextColor expected) {
        StringBuilder text = new StringBuilder();
        List<TextColor> colors = new ArrayList<>();
        collect(component, null, text, colors);
        int index = text.indexOf(needle);
        if (index < 0) return false;
        for (int i = index; i < index + needle.length(); i++)
            if (!expected.equals(colors.get(i))) return false;
        return true;
    }

    private static void collect(
            Component component, TextColor parent, StringBuilder text, List<TextColor> colors) {
        TextColor color = component.color() == null ? parent : component.color();
        if (component instanceof net.kyori.adventure.text.TextComponent literal) {
            text.append(literal.content());
            for (int i = 0; i < literal.content().length(); i++) colors.add(color);
        }
        for (var child : component.children()) collect(child, color, text, colors);
    }
}
