package dev.vapee.core.ui;

import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.clan.gui.ClanMenu;
import dev.vapee.core.friend.gui.FriendMenu;
import dev.vapee.core.settings.visibility.VisiblePlayersMenu;
import dev.vapee.core.settings.visibility.VisibilitySettingsMenu;
import dev.vapee.core.settings.SettingsMenu;
import java.util.*;

/** Full-feature behavior fixtures. Production defaults may expose any subset. */
public final class MenuTestLayouts {
    private MenuTestLayouts() { }
    public static final MenuLayout<SettingsMenu.Action> SETTINGS = MenuLayout.builder(54, SettingsMenu.Action.class)
            .setting(SettingsMenu.Action.SCOREBOARD, 10, 19)
            .setting(SettingsMenu.Action.SOUNDS, 11, 20)
            .setting(SettingsMenu.Action.PRIVATE_MESSAGES, 12, 21)
            .setting(SettingsMenu.Action.FRIEND_REQUESTS, 13, 22)
            .setting(SettingsMenu.Action.VISIBILITY, 31, 40)
            .setting(SettingsMenu.Action.FRIEND_PRESENCE, 33, 42)
            .button(SettingsMenu.Action.CLOSE, 49)
            .button(SettingsMenu.Action.REFRESH, 52)
            .build();
    public static final MenuLayout<VisibilitySettingsMenu.Action> VISIBILITY = MenuLayout.builder(54, VisibilitySettingsMenu.Action.class)
            .setting(VisibilitySettingsMenu.Action.MASTER, 9, 18)
            .setting(VisibilitySettingsMenu.Action.FRIENDS, 11, 20)
            .setting(VisibilitySettingsMenu.Action.STAFF, 13, 22)
            .setting(VisibilitySettingsMenu.Action.ADDED_USERS, 15, 24)
            .setting(VisibilitySettingsMenu.Action.GAME_PARTICIPANTS, 17, 26)
            .button(VisibilitySettingsMenu.Action.MANAGE_PLAYERS, 31)
            .button(VisibilitySettingsMenu.Action.BACK, 45)
            .button(VisibilitySettingsMenu.Action.CLOSE, 49)
            .button(VisibilitySettingsMenu.Action.REFRESH, 52)
            .build();
    public static final MenuLayout<VisiblePlayersMenu.Action> VISIBLE_PLAYERS = MenuLayout.builder(54, VisiblePlayersMenu.Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(VisiblePlayersMenu.Action.PREVIOUS, 45)
            .button(VisiblePlayersMenu.Action.ADD, 46)
            .button(VisiblePlayersMenu.Action.BACK, 48)
            .button(VisiblePlayersMenu.Action.CLOSE, 49)
            .button(VisiblePlayersMenu.Action.PAGE_INFO, 51)
            .button(VisiblePlayersMenu.Action.REFRESH, 52)
            .button(VisiblePlayersMenu.Action.NEXT, 53)
            .build();
    public static final MenuLayout<FriendMenu.Action> FRIEND = MenuLayout.builder(54, FriendMenu.Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(FriendMenu.Action.PREVIOUS, 45)
            .button(FriendMenu.Action.ADD, 46)
            .button(FriendMenu.Action.FRIENDS, 47)
            .button(FriendMenu.Action.INCOMING, 48)
            .button(FriendMenu.Action.CLOSE, 49)
            .button(FriendMenu.Action.OUTGOING, 50)
            .button(FriendMenu.Action.PAGE_INFO, 51)
            .button(FriendMenu.Action.REFRESH, 52)
            .button(FriendMenu.Action.NEXT, 53)
            .build();
    public static final ClanMenu.Layout CLAN = new ClanMenu.Layout(
            MenuLayout.builder(54, ClanMenu.Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(ClanMenu.Action.PREVIOUS, 45)
            .button(ClanMenu.Action.OVERVIEW, 46)
            .button(ClanMenu.Action.MEMBERS, 47)
            .button(ClanMenu.Action.INVITES, 48)
            .button(ClanMenu.Action.CLOSE, 49)
            .button(ClanMenu.Action.ACTION, 50)
            .button(ClanMenu.Action.PAGE_INFO, 51)
            .button(ClanMenu.Action.REFRESH, 52)
            .button(ClanMenu.Action.NEXT, 53)
            .build(), 20, 22, 24);
    public static final MenuLayout<NavigatorMenu.Action> NAVIGATOR = MenuLayout.builder(54, NavigatorMenu.Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(NavigatorMenu.Action.PREVIOUS, 45)
            .button(NavigatorMenu.Action.PAGE_INFO, 49)
            .button(NavigatorMenu.Action.CLOSE, 50)
            .button(NavigatorMenu.Action.NEXT, 53)
            .build();
    public static final MenuLayout<DailyQuestMenu.Action> QUEST = MenuLayout.builder(54, DailyQuestMenu.Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(DailyQuestMenu.Action.PREVIOUS, 45)
            .button(DailyQuestMenu.Action.PAGE_INFO, 49)
            .button(DailyQuestMenu.Action.CLOSE, 50)
            .button(DailyQuestMenu.Action.RESET_INFO, 51)
            .button(DailyQuestMenu.Action.REFRESH, 52)
            .button(DailyQuestMenu.Action.NEXT, 53)
            .build();
}
