package dev.vapee.core.lobby.warp.command;

import dev.vapee.core.lobby.warp.WarpPoint;

import java.util.Arrays;
import java.util.Locale;

/** Warp-local discovery and presentation metadata; execution stays in WarpCommand. */
enum WarpSubcommand {
    HELP("help", "", null, null, false),
    SET("set", " <id>", "warp.help.warp.set.description", Section.CREATE_EDIT, false),
    NAME(
            "name",
            " <id> <display name...>",
            "warp.help.warp.name.name.description",
            Section.CREATE_EDIT,
            true),
    ICON("icon", " <id> <material>", "warp.help.warp.icon.description", Section.CREATE_EDIT, true),
    INFO("info", " <id>", "warp.help.warp.info.description", Section.INFORMATION, true),
    LIST("list", "", "warp.help.warp.list.description", Section.INFORMATION, false),
    SHOW("show", " <id>", "warp.help.warp.show.description", Section.MANAGEMENT, true),
    HIDE("hide", " <id>", "warp.help.warp.hide.description", Section.MANAGEMENT, true),
    ORDER("order", " <id> <order>", "warp.help.warp.order.description", Section.MANAGEMENT, true),
    REMOVE("remove", " <id>", "warp.help.warp.remove.description", Section.MANAGEMENT, true);

    enum Section {
        CREATE_EDIT("warp.help.section.create-edit"),
        INFORMATION("blackjack.help.section.information"),
        MANAGEMENT("blackjack.help.section.management");

        final String titleKey;

        Section(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    private final String token;
    private final String arguments;
    final String descriptionKey;
    final Section section;
    final boolean completesWarpId;

    WarpSubcommand(
            String token,
            String arguments,
            String descriptionKey,
            Section section,
            boolean completesWarpId) {
        this.token = token;
        this.arguments = arguments;
        this.descriptionKey = descriptionKey;
        this.section = section;
        this.completesWarpId = completesWarpId;
    }

    String token() {
        return token;
    }

    String command() {
        return "/warp " + token;
    }

    String syntax() {
        return command() + arguments;
    }

    static WarpSubcommand find(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(action -> action.token.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    boolean suggests(WarpPoint warp) {
        return completesWarpId
                && switch (this) {
                    case SHOW -> !warp.navigation().visible();
                    case HIDE -> warp.navigation().visible();
                    default -> true;
                };
    }
}
