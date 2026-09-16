package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.ui.BlackjackModeMenu;
import dev.vapee.core.activity.blackjack.ui.BlackjackTableMenu;
import dev.vapee.core.activity.navigation.ActivityEntryPoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public final class BlackjackEntryPoint implements ActivityEntryPoint {

    private final BlackjackService blackjackService;
    private final BlackjackModeMenu modeMenu;
    private final BlackjackTableMenu tableMenu;

    public BlackjackEntryPoint(
            BlackjackService blackjackService,
            BlackjackModeMenu modeMenu,
            BlackjackTableMenu tableMenu
    ) {
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
        this.modeMenu = Objects.requireNonNull(modeMenu, "modeMenu");
        this.tableMenu = Objects.requireNonNull(tableMenu, "tableMenu");
    }

    @Override
    public String getActivityKey() {
        return BlackjackActivityType.KEY;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("Blackjack", NamedTextColor.GOLD);
    }

    @Override
    public Material getIcon() {
        return Material.GOLD_INGOT;
    }

    @Override
    public List<Component> getDescription() {
        return List.of(
                Component.text("Play solo or with other players.", NamedTextColor.GRAY),
                Component.text("No other players are required.", NamedTextColor.GREEN),
                Component.text("Free Play — no coin bets.", NamedTextColor.YELLOW)
        );
    }

    @Override
    public void open(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        var existingSession = blackjackService.getSessionForPlayer(validatedPlayer);
        if (existingSession.isPresent()) {
            tableMenu.open(validatedPlayer, existingSession.get());
            return;
        }
        if (blackjackService.canOpenModeMenu(validatedPlayer)) {
            modeMenu.open(validatedPlayer);
        }
    }
}
