package dev.vapee.core.clan;

public interface ClanRepository {
    ClanSnapshot initialize();
    void save(ClanSnapshot snapshot);
}
