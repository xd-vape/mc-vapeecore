package dev.vapee.core.settings;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SettingsMenu {

    public static final int INVENTORY_SIZE = 27;
    public static final int SCOREBOARD_SLOT = 11;
    public static final int SOUNDS_SLOT = 13;
    public static final int PRIVATE_MESSAGES_SLOT = 15;
    public static final int CLOSE_SLOT = 22;

    private static final Component TITLE = Component.text("Player Settings", NamedTextColor.DARK_GRAY);

    private final JavaPlugin plugin;
    private final PlayerSettingsService playerSettingsService;
    private final MessageService messageService;

    public SettingsMenu(
            JavaPlugin plugin,
            PlayerSettingsService playerSettingsService,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    public void open(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        UUID uniqueId = validatedPlayer.getUniqueId();
        if (playerSettingsService.getSettings(uniqueId).isEmpty()) {
            messageService.send(validatedPlayer, "<red>Your player profile is not available.</red>");
            return;
        }

        SettingsInventoryHolder holder = new SettingsInventoryHolder(uniqueId);
        Inventory inventory = plugin.getServer().createInventory(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);
        if (!refresh(validatedPlayer, inventory)) {
            messageService.send(validatedPlayer, "<red>Your player profile is not available.</red>");
            return;
        }
        validatedPlayer.openInventory(inventory);
    }

    public boolean refresh(Player player, Inventory inventory) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (!(validatedInventory.getHolder() instanceof SettingsInventoryHolder holder)
                || !holder.getOwnerUniqueId().equals(validatedPlayer.getUniqueId())
                || holder.getInventory() != validatedInventory) {
            throw new IllegalArgumentException("Inventory is not this player's settings menu");
        }

        Optional<PlayerSettings> optionalSettings = playerSettingsService.getSettings(validatedPlayer.getUniqueId());
        if (optionalSettings.isEmpty()) {
            return false;
        }

        PlayerSettings settings = optionalSettings.get();
        validatedInventory.setItem(
                SCOREBOARD_SLOT,
                createToggleItem(
                        Material.MAP,
                        "Scoreboard",
                        List.of("Show or hide the lobby scoreboard."),
                        settings.isScoreboardEnabled()
                )
        );
        validatedInventory.setItem(
                SOUNDS_SLOT,
                createToggleItem(
                        Material.NOTE_BLOCK,
                        "Sounds",
                        List.of(
                                "Enable or disable VapeeCore",
                                "interface feedback sounds."
                        ),
                        settings.isSoundsEnabled()
                )
        );
        validatedInventory.setItem(
                PRIVATE_MESSAGES_SLOT,
                createToggleItem(
                        Material.WRITABLE_BOOK,
                        "Private Messages",
                        List.of(
                                "Choose whether other players can send",
                                "private messages to you."
                        ),
                        settings.isPrivateMessagesEnabled()
                )
        );
        validatedInventory.setItem(CLOSE_SLOT, createCloseItem());
        return true;
    }

    private ItemStack createToggleItem(
            Material material,
            String name,
            List<String> description,
            boolean enabled
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(uiText(name, NamedTextColor.AQUA));

        List<Component> lore = new java.util.ArrayList<>();
        for (String line : description) {
            lore.add(uiText(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(uiText("Status: ", NamedTextColor.GRAY).append(uiText(
                enabled ? "Enabled" : "Disabled",
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED
        )));
        lore.add(uiText(
                enabled ? "Click to disable." : "Click to enable.",
                NamedTextColor.YELLOW
        ));
        meta.lore(List.copyOf(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(uiText("Close", NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private Component uiText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
