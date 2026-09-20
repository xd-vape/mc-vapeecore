package dev.vapee.core.rank;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.permission.LuckPermsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class RankService {

    public static final String DESCRIPTION_META_KEY = "vapeecore.rank.description";

    private final Supplier<String> trackNameSupplier;
    private final Gateway gateway;

    public RankService(ConfigService configService, LuckPermsService luckPermsService) {
        this(
                Objects.requireNonNull(configService, "configService")::getRankTrack,
                new LuckPermsGateway(Objects.requireNonNull(luckPermsService, "luckPermsService"))
        );
    }

    RankService(Supplier<String> trackNameSupplier, Gateway gateway) {
        this.trackNameSupplier = Objects.requireNonNull(trackNameSupplier, "trackNameSupplier");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public Optional<RankInfo> getPrimaryRank(UUID uniqueId) {
        return gateway.getPrimaryGroup(Objects.requireNonNull(uniqueId, "uniqueId"))
                .map(this::resolveRank);
    }

    public RankTrackResult getPublicRanks() {
        String trackName = currentTrackName();
        Optional<List<String>> groupIds = gateway.getTrackGroups(trackName);
        if (groupIds.isEmpty()) {
            return new RankTrackResult(trackName, RankTrackStatus.MISSING_TRACK, List.of());
        }

        List<RankInfo> ranks = new ArrayList<>(groupIds.orElseThrow().size());
        for (String groupId : groupIds.orElseThrow()) {
            if (groupId == null || groupId.isBlank()) {
                continue;
            }
            ranks.add(resolveRank(groupId));
        }
        return new RankTrackResult(trackName, RankTrackStatus.AVAILABLE, ranks);
    }

    private RankInfo resolveRank(String groupId) {
        String validatedGroupId = requireNonBlank(groupId, "groupId");
        return gateway.getGroupInformation(validatedGroupId)
                .map(information -> new RankInfo(
                        validatedGroupId,
                        information.displayName().filter(value -> !value.isBlank())
                                .orElseGet(() -> fallbackDisplayName(validatedGroupId)),
                        information.description(),
                        information.weight()
                ))
                .orElseGet(() -> new RankInfo(
                        validatedGroupId,
                        fallbackDisplayName(validatedGroupId),
                        Optional.empty(),
                        java.util.OptionalInt.empty()
                ));
    }

    private String currentTrackName() {
        String configuredTrack = trackNameSupplier.get();
        if (configuredTrack == null || configuredTrack.isBlank()) {
            return ConfigService.DEFAULT_RANK_TRACK;
        }
        return configuredTrack.trim();
    }

    static String fallbackDisplayName(String groupId) {
        String[] words = requireNonBlank(groupId, "groupId").trim().split("[-_.\\s]+");
        List<String> formatted = new ArrayList<>(words.length);
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (word.length() <= 3 && word.equals(word.toLowerCase(Locale.ROOT))) {
                formatted.add(word.toUpperCase(Locale.ROOT));
            } else if (word.equals(word.toLowerCase(Locale.ROOT))) {
                formatted.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            } else {
                formatted.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return formatted.isEmpty() ? groupId : String.join(" ", formatted);
    }

    private static String requireNonBlank(String value, String name) {
        String validatedValue = Objects.requireNonNull(value, name);
        if (validatedValue.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return validatedValue;
    }

    interface Gateway {

        Optional<String> getPrimaryGroup(UUID uniqueId);

        Optional<LuckPermsService.GroupInformation> getGroupInformation(String groupId);

        Optional<List<String>> getTrackGroups(String trackName);
    }

    private record LuckPermsGateway(LuckPermsService luckPermsService) implements Gateway {

        private LuckPermsGateway {
            Objects.requireNonNull(luckPermsService, "luckPermsService");
        }

        @Override
        public Optional<String> getPrimaryGroup(UUID uniqueId) {
            return luckPermsService.getPrimaryGroup(uniqueId);
        }

        @Override
        public Optional<LuckPermsService.GroupInformation> getGroupInformation(String groupId) {
            return luckPermsService.getGroupInformation(groupId, DESCRIPTION_META_KEY);
        }

        @Override
        public Optional<List<String>> getTrackGroups(String trackName) {
            return luckPermsService.getTrackGroups(trackName);
        }
    }

    public enum RankTrackStatus {
        AVAILABLE,
        MISSING_TRACK
    }

    public record RankTrackResult(String trackName, RankTrackStatus status, List<RankInfo> ranks) {

        public RankTrackResult {
            trackName = requireNonBlank(trackName, "trackName");
            Objects.requireNonNull(status, "status");
            ranks = List.copyOf(Objects.requireNonNull(ranks, "ranks"));
        }
    }
}
