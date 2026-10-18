package dev.vapee.core.quest.daily.command;

import static dev.vapee.core.i18n.Translations.tr;
import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.quest.daily.DailyQuestService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import dev.vapee.core.utility.OnlinePlayerResolver;

/** Explicit operator testing action, delegated only below /core quest. */
public final class CoreQuestCommand implements TabExecutor {
    public static final String PERMISSION = "vapeecore.admin";
    private final DailyQuestService daily;
    private final PlayerService players;
    private final MessageService messages;
    private final Supplier<? extends Collection<? extends Player>> online;
    private final Supplier<Instant> now;
    private final OnlineStaffTargetGuard targets;

    public CoreQuestCommand(DailyQuestService daily, PlayerService players, MessageService messages,
                            Supplier<? extends Collection<? extends Player>> online, Supplier<Instant> now,
                            OnlineStaffTargetGuard targets) {
        this.daily=Objects.requireNonNull(daily); this.players=Objects.requireNonNull(players);
        this.messages=Objects.requireNonNull(messages); this.online=Objects.requireNonNull(online);
        this.now=Objects.requireNonNull(now); this.targets=Objects.requireNonNull(targets);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { messages.send(sender,tr("core.quest.denied")); return true; }
        if (args.length<1 || args.length>2 || !args[0].equalsIgnoreCase("force")) {
            messages.send(sender,tr("core.quest.usage")); return true;
        }
        Player player;
        if (args.length==1) {
            if (!(sender instanceof Player self)) { messages.send(sender,tr("core.quest.player-required")); return true; }
            player=self;
        } else player=new OnlinePlayerResolver(online).resolveExact(args[1]);
        if (player==null || !player.isOnline()) {
            messages.send(sender,tr("core.quest.player-not-found",Map.of("player",Component.text(args.length==2?args[1]:"",MessageStyle.HIGHLIGHT))));return true;
        }
        if (!targets.authorize(sender,player,"core quest force",messages::send)) return true;
        if (!players.isLoaded(player.getUniqueId())) {messages.send(sender,tr("core.quest.profile-unavailable"));return true;}
        var result=daily.forcePlayer(player.getUniqueId(),now.get());
        switch(result) {
            case ROTATED -> messages.send(sender,tr("core.quest.success",Map.of(
                    "player",Component.text(player.getName(),MessageStyle.HIGHLIGHT),
                    "count",Component.text(players.getPlayer(player.getUniqueId()).orElseThrow().getQuestState().snapshot().size(),MessageStyle.HIGHLIGHT))));
            case DISABLED -> messages.send(sender,tr("daily-quests.disabled"));
            case NO_DEFINITIONS -> messages.send(sender,tr("daily-quests.no-definitions"));
            case PLAYER_NOT_LOADED -> messages.send(sender,tr("core.quest.profile-unavailable"));
            case BLOCKED_PENDING_REWARD -> messages.send(sender,tr("core.quest.pending"));
            default -> messages.send(sender,tr("core.quest.failed"));
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if(args.length==1) return "force".startsWith(args[0].toLowerCase(Locale.ROOT))?List.of("force"):List.of();
        if(args.length==2 && args[0].equalsIgnoreCase("force"))
            return new OnlinePlayerResolver(()->targets.suggestiblePlayers(sender,online.get())).suggest(args[1],null);
        return List.of();
    }
}
