package dev.vapee.core.friend;

public interface FriendRepository {

    FriendSnapshot initialize();

    void save(FriendSnapshot snapshot);
}
