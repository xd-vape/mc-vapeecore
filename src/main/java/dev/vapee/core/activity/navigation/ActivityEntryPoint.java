package dev.vapee.core.activity.navigation;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public interface ActivityEntryPoint {

    String getActivityKey();

    Component getDisplayName();

    Material getIcon();

    List<Component> getDescription();

    void open(Player player);
}
