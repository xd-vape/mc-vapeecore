package dev.vapee.core.utility.tpa;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.PlayerNameFormatter;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.IgnoreRelationshipListener;
import dev.vapee.core.social.SocialService;
import dev.vapee.core.utility.OnlinePlayerResolver;
import dev.vapee.core.utility.UtilityService;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Main-thread, session-only consent. Chat selectors identify one request, never confer authority. */
public final class TeleportRequests implements TabExecutor, Listener, ActivityMembershipListener,
        LobbyPlayerStateListener, IgnoreRelationshipListener {
    public static final String PERMISSION = "vapeecore.tpa.use";
    public static final long EXPIRY_NANOS = java.time.Duration.ofSeconds(60).toNanos();
    public static final long COOLDOWN_NANOS = java.time.Duration.ofSeconds(15).toNanos();
    public static final int MAX_INCOMING = 5;
    public static final List<String> COMMANDS = List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel");

    private final BooleanSupplier primaryThread;
    private final LongSupplier clock;
    private final Function<UUID, Player> online;
    private final Supplier<? extends Collection<? extends Player>> players;
    private final Predicate<UUID> loaded, activity, build;
    private final BiPredicate<UUID, UUID> ignores;
    private final Function<UUID, World> worlds;
    private final UtilityService utility;
    private final MessageService messages;
    private final PlayerNameFormatter names;
    private final Logger logger;
    private final Map<UUID, Request> outgoing = new LinkedHashMap<>();
    private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
    private final Set<UUID> teleporting = new HashSet<>();
    private boolean active;

    private record Request(UUID id, Player sender, Player recipient, boolean here, long created,
                           World senderWorld, World recipientWorld) { }
    private record Cooldown(Player player, long created) { }

    public TeleportRequests(Server server, UtilityService utility, ActivityService activity,
                            LobbyPlayerStateService states, SocialService social,
                            PlayerSettingsService settings, MessageService messages,
                            PlayerNameFormatter names, Logger logger) {
        this(server::isPrimaryThread, System::nanoTime, server::getPlayer, server::getOnlinePlayers,
                id -> settings.getSettings(id).isPresent(), activity::isParticipating, states::isBuildMode,
                social::isIgnoring, server::getWorld, utility, messages, names, logger);
    }

    TeleportRequests(BooleanSupplier primaryThread, LongSupplier clock, Function<UUID, Player> online,
                     Supplier<? extends Collection<? extends Player>> players, Predicate<UUID> loaded,
                     Predicate<UUID> activity, Predicate<UUID> build, BiPredicate<UUID, UUID> ignores,
                     Function<UUID, World> worlds, UtilityService utility, MessageService messages,
                     PlayerNameFormatter names, Logger logger) {
        this.primaryThread = Objects.requireNonNull(primaryThread);
        this.clock = Objects.requireNonNull(clock);
        this.online = Objects.requireNonNull(online);
        this.players = Objects.requireNonNull(players);
        this.loaded = Objects.requireNonNull(loaded);
        this.activity = Objects.requireNonNull(activity);
        this.build = Objects.requireNonNull(build);
        this.ignores = Objects.requireNonNull(ignores);
        this.worlds = Objects.requireNonNull(worlds);
        this.utility = Objects.requireNonNull(utility);
        this.messages = Objects.requireNonNull(messages);
        this.names = Objects.requireNonNull(names);
        this.logger = Objects.requireNonNull(logger);
    }

    void enable() { requireThread(); active = true; }
    void disable() {
        requireThread(); active = false; outgoing.clear(); cooldowns.clear(); teleporting.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        requireThread();
        if (!(sender instanceof Player player)) {
            messages.sendRaw(sender, tr("common.player-only")); return true;
        }
        if (!active || !current(player)) { return true; }
        prune();
        if (!player.hasPermission(PERMISSION)) { tell(player, messages.commandDenied(player, "tpa.permission")); return true; }
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "tpa", "tpahere" -> {
                if (args.length != 1) tell(player, messages.usage(player, "tpa.usage-send", "/" + name, "<player>"));
                else send(player, args[0], name.equals("tpahere"));
            }
            case "tpaccept", "tpdeny" -> {
                if (args.length > 1) tell(player, messages.usage(player, "tpa.usage-answer", "/" + name, "[player]"));
                else answer(player, args.length == 0 ? null : args[0], name.equals("tpaccept"));
            }
            case "tpcancel" -> {
                if (args.length != 0) tell(player, messages.usage(player, "tpa.usage-cancel", "/" + name, ""));
                else {
                    Request request = outgoing.get(player.getUniqueId());
                    if (request == null || request.sender != player) tell(player, tr("tpa.none-outgoing"));
                    else { outgoing.remove(player.getUniqueId()); notifyBoth(request, tr("tpa.cancelled")); }
                }
            }
            default -> { }
        }
        return true;
    }

    private void send(Player sender, String input, boolean here) {
        Player target = new OnlinePlayerResolver(players).resolveExact(input);
        if (!pair(sender, target)) { tell(sender, tr("tpa.unavailable")); return; }
        if (outgoing.containsKey(sender.getUniqueId())) { tell(sender, tr("tpa.pending")); return; }
        if (cooldowns.containsKey(sender.getUniqueId())) { tell(sender, tr("tpa.cooldown")); return; }
        if (incoming(target).size() >= MAX_INCOMING) { tell(sender, tr("tpa.unavailable")); return; }
        Request request = new Request(UUID.randomUUID(), sender, target, here, clock.getAsLong(),
                sender.getWorld(), target.getWorld());
        outgoing.put(sender.getUniqueId(), request);
        cooldowns.put(sender.getUniqueId(), new Cooldown(sender, request.created));
        tell(sender, tr("tpa.sent", Map.of("player", names.format(target.getUniqueId(), target.getName()))));
        Component player = names.format(sender.getUniqueId(), sender.getName());
        Component direction = here ? tr("tpa.request-here", Map.of("player", player))
                : tr("tpa.request-to", Map.of("player", player));
        // A nonce prevents an old chat button accepting a later request from this same sender.
        String selector = sender.getUniqueId() + ":" + request.id;
        tell(target, direction.append(Component.space())
                .append(tr("tpa.accept").clickEvent(ClickEvent.runCommand("/vapeecore:tpaccept " + selector))));
    }

    private void answer(Player recipient, String selector, boolean accept) {
        List<Request> candidates = incoming(recipient);
        Request request = null;
        if (selector == null) {
            if (candidates.size() > 1) { tell(recipient, tr("tpa.multiple")); return; }
            if (candidates.size() == 1) request = candidates.getFirst();
        } else if (selector.contains(":")) {
            String[] parts = selector.split(":", -1);
            if (parts.length == 2) {
                try {
                    UUID senderId = UUID.fromString(parts[0]), requestId = UUID.fromString(parts[1]);
                    request = candidates.stream().filter(r -> r.sender.getUniqueId().equals(senderId)
                            && r.id.equals(requestId)).findFirst().orElse(null);
                } catch (IllegalArgumentException ignored) { /* Invalid selector has no authority. */ }
            }
        } else {
            // Optional manual selection is an exact current name or UUID, never a partial match.
            Player selected;
            try { selected = online.apply(UUID.fromString(selector)); }
            catch (IllegalArgumentException ignored) { selected = new OnlinePlayerResolver(players).resolveExact(selector); }
            Player sender = selected;
            request = candidates.stream().filter(r -> r.sender == sender).findFirst().orElse(null);
        }
        if (request == null || !outgoing.remove(request.sender.getUniqueId(), request)) {
            tell(recipient, tr("tpa.none-incoming")); return;
        }
        if (!accept) { notifyBoth(request, tr("tpa.denied")); return; }
        // Consume before any teleport/event callback, including failed or cancelled teleports.
        if (expired(request, clock.getAsLong()) || !valid(request)) {
            notifyBoth(request, tr("tpa.unavailable")); return;
        }
        Player mover = request.here ? request.recipient : request.sender;
        Player destinationPlayer = request.here ? request.sender : request.recipient;
        Location destination = destinationPlayer.getLocation().clone();
        if (!safeDestination(destination) || !valid(request) || expired(request, clock.getAsLong())) {
            notifyBoth(request, tr("tpa.failed")); return;
        }
        boolean success;
        teleporting.add(request.sender.getUniqueId()); teleporting.add(request.recipient.getUniqueId());
        try {
            success = utility.teleport(mover, destination);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Consented player teleport failed", failure); success = false;
        } finally {
            teleporting.remove(request.sender.getUniqueId()); teleporting.remove(request.recipient.getUniqueId());
        }
        notifyBoth(request, success ? tr("tpa.success") : tr("tpa.failed"));
    }

    /** Only the target's current, loaded, supported standing position. No chunk/world loads or searches. */
    boolean safeDestination(Location destination) {
        World world = destination.getWorld();
        if (world == null || worlds.apply(world.getUID()) != world
                || !Double.isFinite(destination.getX()) || !Double.isFinite(destination.getY())
                || !Double.isFinite(destination.getZ()) || !Float.isFinite(destination.getYaw())
                || !Float.isFinite(destination.getPitch()) || Math.abs(destination.getX()) > 29_999_984
                || Math.abs(destination.getZ()) > 29_999_984
                || destination.getY() < world.getMinHeight() + 1
                || destination.getY() + 1.8 >= world.getMaxHeight()
                || !world.getWorldBorder().isInside(destination)) return false;
        // Inspect all blocks touched by the player's 0.6 x 1.8 bounding box, including negative coordinates.
        int minX = (int) Math.floor(destination.getX() - .3), maxX = (int) Math.floor(destination.getX() + .3);
        int minZ = (int) Math.floor(destination.getZ() - .3), maxZ = (int) Math.floor(destination.getZ() + .3);
        int feet = destination.getBlockY(), head = (int) Math.floor(destination.getY() + 1.8);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
            var floor = world.getBlockAt(x, feet - 1, z);
            if (!floor.isSolid() || hazardous(floor.getType())) return false;
            for (int y = feet; y <= head; y++) {
                var block = world.getBlockAt(x, y, z);
                if (!block.isPassable() || block.isLiquid() || hazardous(block.getType())) return false;
            }
        }
        return true;
    }

    private static boolean hazardous(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, CACTUS,
                    SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE, POINTED_DRIPSTONE,
                    NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        requireThread();
        if (!active || !(sender instanceof Player player) || !eligible(player) || args.length != 1) return List.of();
        prune();
        String name = command.getName().toLowerCase(Locale.ROOT);
        Collection<Player> candidates;
        if (name.equals("tpa") || name.equals("tpahere"))
            candidates = players.get().stream().filter(target -> pair(player, target)).map(target -> (Player) target).toList();
        else if (name.equals("tpaccept") || name.equals("tpdeny"))
            candidates = incoming(player).stream().map(Request::sender).toList();
        else return List.of();
        return new OnlinePlayerResolver(() -> candidates).suggest(args[0], player.getUniqueId());
    }

    private void invalidate(Player player) {
        requireThread();
        outgoing.values().removeIf(r -> r.sender == player || r.recipient == player);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
        Cooldown cooldown = cooldowns.get(event.getPlayer().getUniqueId());
        if (cooldown != null && cooldown.player == event.getPlayer()) cooldowns.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { requireThread(); if (active) prune(); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { invalidate(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) { invalidate(event.getPlayer()); }
    @Override public void beforeJoin(Player player) { invalidate(player); }
    @Override public void membershipChanged(UUID id) { requireThread(); if (active) prune(); }
    @Override public void beforeStateChange(Player player) { /* NORMAL refreshes must not cancel consent. */ }
    @Override public void afterStateChange(UUID id) { requireThread(); if (active) prune(); }
    @Override public void onIgnoreRelationshipChanged(UUID owner, UUID target) { requireThread(); if (active) prune(); }

    private boolean current(Player player) {
        return player != null && player.isOnline() && online.apply(player.getUniqueId()) == player;
    }

    private boolean eligible(Player player) {
        return current(player) && player.hasPermission(PERMISSION) && loaded.test(player.getUniqueId())
                && !activity.test(player.getUniqueId()) && !build.test(player.getUniqueId())
                && !teleporting.contains(player.getUniqueId()) && !player.isDead()
                && !player.isInsideVehicle() && player.getPassengers().isEmpty();
    }

    private boolean pair(Player sender, Player recipient) {
        return eligible(sender) && eligible(recipient) && sender != recipient
                && !sender.getUniqueId().equals(recipient.getUniqueId())
                && sender.canSee(recipient) && recipient.canSee(sender)
                && !ignores.test(sender.getUniqueId(), recipient.getUniqueId())
                && !ignores.test(recipient.getUniqueId(), sender.getUniqueId());
    }

    private boolean valid(Request request) {
        return pair(request.sender, request.recipient) && request.sender.getWorld() == request.senderWorld
                && request.recipient.getWorld() == request.recipientWorld;
    }

    private boolean expired(Request request, long now) { return now - request.created >= EXPIRY_NANOS; }

    private void prune() {
        long now = clock.getAsLong();
        List<Request> expired = new ArrayList<>();
        outgoing.values().removeIf(request -> {
            if (expired(request, now)) { expired.add(request); return true; }
            return !valid(request);
        });
        cooldowns.values().removeIf(cooldown -> !current(cooldown.player)
                || now - cooldown.created >= COOLDOWN_NANOS);
        expired.forEach(request -> notifyBoth(request, tr("tpa.expired")));
    }

    private List<Request> incoming(Player recipient) {
        return outgoing.values().stream().filter(request -> request.recipient == recipient).toList();
    }

    private void tell(Player player, Component text) {
        if (current(player)) messages.sendRaw(player, text.colorIfAbsent(NamedTextColor.GRAY));
    }

    private void notifyBoth(Request request, Component text) { tell(request.sender, text); tell(request.recipient, text); }
    private void requireThread() {
        if (!primaryThread.getAsBoolean()) throw new IllegalStateException("Teleport requests require the primary server thread");
    }
}
