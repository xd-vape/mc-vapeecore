package dev.vapee.core.quest;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.reward.RewardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.*;

/** Uses the real quest/reward/economy services; only persistence and Bukkit surfaces are substituted. */
public final class QuestCompletionFixture {
    public final Repository repository = new Repository();
    public final Logger logger = Logger.getAnonymousLogger();
    public final List<LogRecord> logs = new ArrayList<>();
    public final PlayerService players = new PlayerService(repository, logger);
    public final EconomyService economy = new EconomyService(players);
    public final RewardService rewards = new RewardService(economy, players, logger);
    public final QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
    public final QuestService quests = new QuestService(registry, players, rewards, logger);

    public QuestCompletionFixture() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logs.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }
    public UUID load() {
        UUID id = UUID.randomUUID();
        players.loadPlayer(id, "Tester");
        return id;
    }
    public void define(QuestDefinition... definitions) { registry.replaceAll(List.of(definitions)); }
    public static QuestDefinition definition(String id, String key, long target, long coins) {
        return new QuestDefinition(id, "<click:run_command:'/op @s'>" + id + "</click>",
                "<red>Literal description</red>", QuestProgressKey.of(key), target, coins);
    }
    public static MessageService messages() throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance((Supplier<String>) () -> "");
    }
    public static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    public static boolean hasEvent(Component component) {
        return component.clickEvent() != null || component.hoverEvent() != null
                || component.children().stream().anyMatch(QuestCompletionFixture::hasEvent);
    }
    public static final Object DEFAULT = new Object();
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(instance);
                case "equals" -> instance == args[0];
                case "toString" -> type.getSimpleName() + "QuestFixture";
                default -> null;
            };
            Object value = action.invoke(method.getName(), args == null ? new Object[0] : args);
            if (value != DEFAULT) return value;
            Class<?> result = method.getReturnType();
            if (!result.isPrimitive()) return null;
            if (result == boolean.class) return false;
            if (result == char.class) return '\0';
            if (result == byte.class) return (byte) 0;
            if (result == short.class) return (short) 0;
            if (result == int.class) return 0;
            if (result == long.class) return 0L;
            if (result == float.class) return 0F;
            if (result == double.class) return 0D;
            return null;
        });
    }
    @FunctionalInterface public interface ProxyAction { Object invoke(String method, Object[] args); }
    public static final class Repository implements PlayerRepository {
        public final Map<UUID, CorePlayer> data = new HashMap<>();
        public final Map<UUID, Map<String, PlayerQuestProgress>> persisted = new HashMap<>();
        public int saves;
        public boolean fail;
        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
        @Override public void save(CorePlayer player) {
            if (fail) throw new IllegalStateException("synthetic save failure");
            saves++;
            data.put(player.getUniqueId(), player);
            persisted.put(player.getUniqueId(), Map.copyOf(player.getQuestState().snapshot()));
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
    }
}
