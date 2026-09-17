package dev.vapee.core.activity.blackjack.ui;

import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.BlackjackOutcome;
import dev.vapee.core.activity.blackjack.BlackjackPlayerRound;
import dev.vapee.core.activity.blackjack.BlackjackRoundPhase;
import dev.vapee.core.activity.blackjack.BlackjackService;
import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BlackjackTableMenu {

    public static final int INVENTORY_SIZE = 54;
    public static final int INFO_SLOT = 4;
    public static final int STATUS_SLOT = 40;
    public static final int DEAL_SLOT = 45;
    public static final int HIT_SLOT = 46;
    public static final int STAND_SLOT = 48;
    public static final int LEAVE_SLOT = 53;

    private static final int DEALER_START_SLOT = 9;
    private static final int DEALER_END_SLOT = 17;
    private static final int PLAYER_START_SLOT = 27;
    private static final int PLAYER_END_SLOT = 38;
    private static final Component TITLE = Component.text("Blackjack Table", NamedTextColor.DARK_GRAY);

    private final JavaPlugin plugin;
    private final BlackjackService blackjackService;

    public BlackjackTableMenu(JavaPlugin plugin, BlackjackService blackjackService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
    }

    public void open(Player player, BlackjackSession session) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        BlackjackSession validatedSession = Objects.requireNonNull(session, "session");
        if (!validatedSession.hasParticipant(validatedPlayer.getUniqueId())) {
            throw new IllegalArgumentException("Player is not a participant of this blackjack table");
        }
        BlackjackTableInventoryHolder holder = new BlackjackTableInventoryHolder(
                validatedPlayer.getUniqueId(),
                validatedSession.getSessionId()
        );
        Inventory inventory = plugin.getServer().createInventory(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);
        render(validatedPlayer, validatedSession, inventory);
        validatedPlayer.openInventory(inventory);
    }

    public void refreshSession(BlackjackSession session) {
        BlackjackSession validatedSession = Objects.requireNonNull(session, "session");
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (!(inventory.getHolder() instanceof BlackjackTableInventoryHolder holder)
                    || holder.getInventory() != inventory
                    || !holder.getOwnerUniqueId().equals(player.getUniqueId())
                    || !holder.getSessionId().equals(validatedSession.getSessionId())) {
                continue;
            }
            if (!validatedSession.hasParticipant(player.getUniqueId())) {
                player.closeInventory();
                continue;
            }
            render(player, validatedSession, inventory);
        }
    }

    public void closeOpenInventories() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackjackTableInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private void render(Player player, BlackjackSession session, Inventory inventory) {
        inventory.clear();
        inventory.setItem(INFO_SLOT, createInfoItem(player.getUniqueId(), session));
        renderDealer(session, inventory);
        renderPlayerHand(player.getUniqueId(), session, inventory);
        renderParticipantSummaries(player.getUniqueId(), session, inventory);
        inventory.setItem(STATUS_SLOT, createStatusItem(player.getUniqueId(), session));

        if (session.getState() == ActivityState.AVAILABLE) {
            inventory.setItem(DEAL_SLOT, createActionItem(
                    Material.LIME_CONCRETE,
                    "Deal",
                    NamedTextColor.GREEN,
                    "Start immediately with everyone currently at this table."
            ));
        }
        boolean ownTurn = session.getState() == ActivityState.ACTIVE
                && session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS
                && session.getCurrentTurnPlayer().filter(player.getUniqueId()::equals).isPresent();
        if (ownTurn) {
            inventory.setItem(HIT_SLOT, createActionItem(
                    Material.GREEN_CONCRETE,
                    "Hit",
                    NamedTextColor.GREEN,
                    "Draw one card."
            ));
            inventory.setItem(STAND_SLOT, createActionItem(
                    Material.RED_CONCRETE,
                    "Stand",
                    NamedTextColor.RED,
                    "End your turn."
            ));
        }
        inventory.setItem(LEAVE_SLOT, createActionItem(
                Material.OAK_DOOR,
                "Leave Table",
                NamedTextColor.YELLOW,
                "Leave this blackjack activity."
        ));
    }

    private void renderDealer(BlackjackSession session, Inventory inventory) {
        List<BlackjackCard> cards = session.getDealerHand().getCards();
        boolean hideHoleCard = session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS;
        int slot = DEALER_START_SLOT;
        for (int index = 0; index < cards.size() && slot <= DEALER_END_SLOT; index++, slot++) {
            if (hideHoleCard && index == 1) {
                inventory.setItem(slot, hiddenCard());
            } else {
                inventory.setItem(slot, cardItem(cards.get(index)));
            }
        }
    }

    private void renderPlayerHand(UUID playerId, BlackjackSession session, Inventory inventory) {
        Optional<BlackjackPlayerRound> optionalRound = session.getPlayerRound(playerId);
        if (optionalRound.isEmpty()) {
            return;
        }
        int slot = PLAYER_START_SLOT;
        for (BlackjackCard card : optionalRound.get().getHand().getCards()) {
            if (slot > PLAYER_END_SLOT) {
                break;
            }
            inventory.setItem(slot++, cardItem(card));
        }
    }

    private void renderParticipantSummaries(UUID viewerId, BlackjackSession session, Inventory inventory) {
        int slot = 18;
        for (var participant : session.getParticipants()) {
            if (slot > 22) {
                break;
            }
            Optional<BlackjackPlayerRound> optionalRound = session.getPlayerRound(participant.uniqueId());
            List<Component> lore = new ArrayList<>();
            if (optionalRound.isPresent()) {
                lore.add(text("Hand Value: " + optionalRound.get().getHand().getValue(), NamedTextColor.GRAY));
                optionalRound.get().getOutcome().ifPresent(outcome ->
                        lore.add(text("Result: " + outcomeLabel(outcome), outcomeColor(outcome)))
                );
            } else {
                lore.add(text("Ready for the next deal.", NamedTextColor.GRAY));
            }
            String suffix = participant.uniqueId().equals(viewerId) ? " (You)" : "";
            inventory.setItem(slot++, createItem(
                    Material.PLAYER_HEAD,
                    blackjackService.getPlayerName(participant.uniqueId()) + suffix,
                    NamedTextColor.AQUA,
                    lore
            ));
        }
    }

    private ItemStack createInfoItem(UUID playerId, BlackjackSession session) {
        String seat = blackjackService.getSeatNumber(playerId)
                .map(String::valueOf)
                .orElse("-");
        return createItem(
                Material.PAPER,
                "Table Information",
                NamedTextColor.GOLD,
                List.of(
                        text("Table: " + session.getVenue().id(), NamedTextColor.GRAY),
                        text("Seat: " + seat, NamedTextColor.GRAY),
                        text("Players: " + session.getParticipantCount() + "/"
                                + blackjackService.getTableCapacity(session),
                                NamedTextColor.GRAY)
                )
        );
    }

    private ItemStack createStatusItem(UUID playerId, BlackjackSession session) {
        List<Component> lore = new ArrayList<>();
        NamedTextColor color = NamedTextColor.YELLOW;
        String status;
        switch (session.getRoundPhase()) {
            case IDLE -> status = "Ready to deal";
            case PLAYER_TURNS -> {
                String currentName = session.getCurrentTurnPlayer()
                        .map(blackjackService::getPlayerName)
                        .orElse("-");
                status = "Player turns";
                lore.add(text("Current Turn: " + currentName, NamedTextColor.AQUA));
            }
            case DEALER_TURN -> status = "Dealer turn";
            case SETTLED -> {
                status = "Round complete";
                session.getPlayerRound(playerId)
                        .flatMap(BlackjackPlayerRound::getOutcome)
                        .ifPresent(outcome -> lore.add(text(
                                "Your Result: " + outcomeLabel(outcome),
                                outcomeColor(outcome)
                        )));
            }
            default -> throw new IllegalStateException("Unknown blackjack round phase");
        }
        session.getPlayerRound(playerId).ifPresent(playerRound ->
                lore.add(text("Hand Value: " + playerRound.getHand().getValue(), NamedTextColor.GRAY))
        );
        int visibleDealerValue = visibleDealerValue(session);
        if (session.getDealerHand().size() > 0) {
            lore.add(text("Dealer Value: " + visibleDealerValue, NamedTextColor.GRAY));
        }
        return createItem(Material.CLOCK, status, color, lore);
    }

    private int visibleDealerValue(BlackjackSession session) {
        List<BlackjackCard> cards = session.getDealerHand().getCards();
        if (cards.isEmpty()) {
            return 0;
        }
        if (session.getRoundPhase() != BlackjackRoundPhase.PLAYER_TURNS) {
            return session.getDealerHand().getValue();
        }
        dev.vapee.core.activity.blackjack.card.BlackjackHand visibleHand =
                new dev.vapee.core.activity.blackjack.card.BlackjackHand(List.of(cards.getFirst()));
        return visibleHand.getValue();
    }

    private ItemStack cardItem(BlackjackCard card) {
        NamedTextColor color = card.suit().isRed() ? NamedTextColor.RED : NamedTextColor.DARK_GRAY;
        return createItem(Material.PAPER, card.getDisplayText(), color, List.of());
    }

    private ItemStack hiddenCard() {
        return createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "Hidden Card",
                NamedTextColor.GRAY,
                List.of()
        );
    }

    private ItemStack createActionItem(
            Material material,
            String name,
            NamedTextColor color,
            String description
    ) {
        return createItem(material, name, color, List.of(text(description, NamedTextColor.GRAY)));
    }

    private ItemStack createItem(
            Material material,
            String name,
            NamedTextColor color,
            List<Component> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, color));
        if (!lore.isEmpty()) {
            meta.lore(List.copyOf(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private String outcomeLabel(BlackjackOutcome outcome) {
        return switch (outcome) {
            case BLACKJACK -> "Blackjack";
            case WIN -> "Win";
            case PUSH -> "Push";
            case LOSS -> "Loss";
            case BUST -> "Bust";
        };
    }

    private NamedTextColor outcomeColor(BlackjackOutcome outcome) {
        return switch (outcome) {
            case BLACKJACK -> NamedTextColor.GOLD;
            case WIN -> NamedTextColor.GREEN;
            case PUSH -> NamedTextColor.YELLOW;
            case LOSS, BUST -> NamedTextColor.RED;
        };
    }
}
