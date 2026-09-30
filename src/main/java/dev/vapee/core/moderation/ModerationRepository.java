package dev.vapee.core.moderation;

public interface ModerationRepository {
    ModerationSnapshot initialize();
    void save(ModerationSnapshot snapshot);
}
