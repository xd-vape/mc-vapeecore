package dev.vapee.core.activity.blackjack.presentation;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.BlackjackPlayerRoundView;
import dev.vapee.core.activity.blackjack.BlackjackRoundPhase;
import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BlackjackInventoryService {

    public static final String ACTION_PDC = "blackjack_action";
    private static final String ITEM_PDC = "blackjack_item";
    public static final int PRIMARY_SLOT = 0;
    public static final int STAND_SLOT = 1;
    public static final int DOUBLE_SLOT = 2;
    public static final int STATUS_SLOT = 4;
    public static final int LEAVE_SLOT = 8;

    private final JavaPlugin plugin;
    private final dev.vapee.core.message.MessageService messages;
    private Player renderingFor;
    private final LobbyPlayerStateService lobbyPlayerStateService;
    private final NamespacedKey actionKey;
    private final NamespacedKey itemKey;
    private final Set<UUID> owners = new HashSet<>();

    public BlackjackInventoryService(JavaPlugin plugin, LobbyPlayerStateService lobbyPlayerStateService) {
        this(plugin, lobbyPlayerStateService, new dev.vapee.core.message.MessageService(() -> "", dev.vapee.core.i18n.LocalizationService.bundled(java.util.logging.Logger.getLogger(BlackjackInventoryService.class.getName()))));
    }
    public BlackjackInventoryService(JavaPlugin plugin, LobbyPlayerStateService lobbyPlayerStateService, dev.vapee.core.message.MessageService messages) {
        this.messages = Objects.requireNonNull(messages);
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyPlayerStateService = Objects.requireNonNull(lobbyPlayerStateService, "lobbyPlayerStateService");
        this.actionKey = new NamespacedKey(plugin, ACTION_PDC);
        this.itemKey = new NamespacedKey(plugin, ITEM_PDC);
    }

    public boolean takeOwnership(Player player) {
        Player validated = Objects.requireNonNull(player, "player");
        if (owners.contains(validated.getUniqueId())) return true;
        if (!lobbyPlayerStateService.relinquishNormalInventory(validated)) return false;
        clearInventory(validated);
        owners.add(validated.getUniqueId());
        return true;
    }

    public void refreshSession(BlackjackSession session) {
        for (var participant : Objects.requireNonNull(session, "session").getParticipants()) {
            Player player = plugin.getServer().getPlayer(participant.uniqueId());
            if (player != null && player.isOnline() && isOwner(participant.uniqueId())) {
                refreshPlayer(player, session);
            }
        }
    }

    public void refreshPlayer(Player player, BlackjackSession session) {
        if (!isOwner(player.getUniqueId())) return;
        Player previousViewer = renderingFor; renderingFor = player;
        try {
        removeManagedItems(player);
        PlayerInventory inventory = player.getInventory();
        if (session.getState() == ActivityState.AVAILABLE) {
            inventory.setItem(PRIMARY_SLOT, actionItem(Material.EMERALD, tr("blackjack.action.deal"), NamedTextColor.GREEN,
                    tr("blackjack.hint.deal"), BlackjackAction.DEAL));
        } else if (isOwnTurn(session, player.getUniqueId())) {
            inventory.setItem(PRIMARY_SLOT, actionItem(Material.IRON_SWORD, tr("blackjack.action.hit"), NamedTextColor.AQUA,
                    tr("blackjack.hint.hit"), BlackjackAction.HIT));
            inventory.setItem(STAND_SLOT, actionItem(Material.SHIELD, tr("blackjack.action.stand"), NamedTextColor.YELLOW,
                    tr("blackjack.hint.stand"), BlackjackAction.STAND));
            if (canDoubleDown(session, player.getUniqueId())) {
                inventory.setItem(DOUBLE_SLOT, actionItem(Material.GOLD_INGOT, tr("blackjack.action.double"), NamedTextColor.GOLD,
                        tr("blackjack.hint.double"), BlackjackAction.DOUBLE));
            }
        }
        inventory.setItem(STATUS_SLOT, statusItem(session, player.getUniqueId()));
        inventory.setItem(LEAVE_SLOT, actionItem(Material.BARRIER, tr("blackjack.action.leave"), NamedTextColor.RED,
                tr("blackjack.hint.leave"), BlackjackAction.LEAVE));
        } finally { renderingFor = previousViewer; }
    }

    public void release(UUID playerId, boolean restoreLobbyInventory) {
        UUID validated = Objects.requireNonNull(playerId, "playerId");
        owners.remove(validated);
        Player player = plugin.getServer().getPlayer(validated);
        if (player == null) return;
        removeManagedItems(player);
        if (restoreLobbyInventory && player.isOnline()) {
            lobbyPlayerStateService.synchronize(player);
        }
    }

    public boolean isOwner(UUID playerId) {
        return owners.contains(Objects.requireNonNull(playerId, "playerId"));
    }

    public boolean isManagedItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public Optional<BlackjackAction> getAction(ItemStack item) {
        if (!isManagedItem(item)) return Optional.empty();
        String value = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        return BlackjackAction.fromPersistentId(value);
    }

    public void removeManagedItems(Player player) {
        PlayerInventory inventory = Objects.requireNonNull(player, "player").getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isManagedItem(contents[slot])) inventory.setItem(slot, null);
        }
        if (isManagedItem(player.getItemOnCursor())) player.setItemOnCursor(null);
    }

    public void shutdown() {
        for (UUID owner : Set.copyOf(owners)) release(owner, false);
        owners.clear();
    }

    private boolean isOwnTurn(BlackjackSession session, UUID playerId) {
        return session.getState() == ActivityState.ACTIVE
                && session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS
                && session.getCurrentTurnPlayer().filter(playerId::equals).isPresent();
    }

    private boolean canDoubleDown(BlackjackSession session, UUID playerId) {
        if (!isOwnTurn(session, playerId)) return false;
        BlackjackPlayerRoundView round = session.getPlayerRoundView(playerId).orElse(null);
        return round != null && !round.finished() && !round.doubledDown()
                && round.hand().cards().size() == 2 && !round.hand().blackjack();
    }

    private ItemStack statusItem(BlackjackSession session, UUID playerId) {
        BlackjackPlayerRoundView round = session.getPlayerRoundView(playerId).orElse(null);
        Component state;
        if (session.getState() == ActivityState.AVAILABLE) {
            state = tr("blackjack.state.waiting");
        } else if (session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS) {
            state = session.getCurrentTurnPlayer().filter(playerId::equals).isPresent()
                    ? tr("blackjack.state.own-turn") : tr("blackjack.state.other-turn");
        } else if (session.getRoundPhase() == BlackjackRoundPhase.DEALER_TURN) {
            state = tr("blackjack.state.dealer");
        } else if (session.getRoundPhase() == BlackjackRoundPhase.SETTLED) {
            state = tr("blackjack.state.complete");
        } else {
            state = tr("blackjack.state.available");
        }
        List<Component> lore = round == null
                ? List.of(state, tr("blackjack.status.players", java.util.Map.of("count", Component.text(session.getParticipantCount()))))
                : List.of(state, tr("blackjack.status.value", java.util.Map.of("value", Component.text(round.hand().value()))));
        return item(Material.PAPER, tr("blackjack.status.title"), NamedTextColor.GOLD, lore, null);
    }

    private ItemStack actionItem(Material material, Component name, NamedTextColor color,
                                 Component lore, BlackjackAction action) {
        return item(material, name, color, List.of(lore, tr("blackjack.hint.use")), action);
    }

    private ItemStack item(Material material, Component name, NamedTextColor color,
                           List<Component> lore, BlackjackAction action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, color));
        meta.lore(lore.stream().map(line -> text(line, NamedTextColor.GRAY)).toList());
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.persistentId());
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component text(Component value, NamedTextColor color) {
        return messages.localize(renderingFor, value).applyFallbackStyle(net.kyori.adventure.text.format.Style.style(color)).decoration(TextDecoration.ITALIC, false);
    }

    private void clearInventory(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);
    }
}
