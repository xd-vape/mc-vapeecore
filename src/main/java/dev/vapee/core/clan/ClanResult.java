package dev.vapee.core.clan;

/** Expected domain outcomes. Storage failures deliberately remain exceptions. */
public enum ClanResult {
    SUCCESS,
    ALREADY_IN_CLAN,
    CLAN_NOT_FOUND,
    NOT_IN_CLAN,
    NOT_OWNER,
    TARGET_NOT_MEMBER,
    TARGET_ALREADY_MEMBER,
    NAME_INVALID,
    NAME_ALREADY_USED,
    TAG_INVALID,
    TAG_ALREADY_USED,
    MEMBER_LIMIT_REACHED,
    INVITE_ALREADY_EXISTS,
    INVITE_NOT_FOUND,
    OUTGOING_INVITE_LIMIT_REACHED,
    INCOMING_INVITE_LIMIT_REACHED,
    OWNER_CANNOT_LEAVE,
    CANNOT_TARGET_SELF
}
