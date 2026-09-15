package dev.vapee.core.privatemessage;

public enum PrivateMessageResult {
    SUCCESS,
    FEATURE_DISABLED,
    SENDER_NOT_LOADED,
    RECIPIENT_NOT_LOADED,
    RECIPIENT_DISABLED,
    TARGET_OFFLINE,
    CANNOT_MESSAGE_SELF,
    NO_REPLY_TARGET
}
