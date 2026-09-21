package dev.vapee.core.rank;

import dev.vapee.core.permission.LuckPermsService;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class RankServiceHarness {

    private static int checks;

    private RankServiceHarness() {
    }

    public static void main(String[] args) {
        UUID playerId = UUID.randomUUID();
        AtomicReference<String> trackName = new AtomicReference<>("ranks");
        Gateway gateway = new Gateway();
        gateway.primaryGroups.put(playerId, "vip");
        gateway.groups.put("vip", group(
                "vip", "VIP", "Supporter rank.", "gold", OptionalInt.of(50)
        ));
        gateway.groups.put("default", group(
                "default", "User", null, "gray", OptionalInt.empty()
        ));
        gateway.groups.put("premium", group(
                "premium", null, null, null, OptionalInt.empty()
        ));
        gateway.groups.put("developer", group(
                "developer", "Developer", "Development Team", "#c35cff", OptionalInt.of(80)
        ));
        gateway.groups.put("event_host", group(
                "event_host", "Event Host", null, "banana", OptionalInt.empty()
        ));
        gateway.tracks.put("ranks", List.of("default", "vip", "premium", "developer", "event_host"));
        gateway.tracks.put("empty", List.of());

        RankService service = new RankService(trackName::get, gateway);
        RankInfo primary = service.getPrimaryRank(playerId).orElseThrow();
        check(primary.id().equals("vip"), "primary group is resolved from the loaded user");
        check(primary.displayName().equals("VIP"), "LuckPerms display name is resolved");
        check(primary.description().orElseThrow().equals("Supporter rank."),
                "rank description meta is resolved");
        check(primary.color().orElseThrow().equals(NamedTextColor.GOLD),
                "named rank color is normalized to Adventure TextColor");
        check(primary.weight().orElseThrow() == 50, "group weight is exposed when present");

        RankInfo member = service.getPublicRanks().ranks().getFirst();
        check(member.description().isEmpty(), "missing rank description stays empty");
        check(member.displayName().equals("User") && member.color().orElseThrow().equals(NamedTextColor.GRAY),
                "friendly default display name and color both come from LuckPerms");
        check(member.weight().isEmpty(), "missing group weight stays empty");
        check(service.getPrimaryRank(UUID.randomUUID()).isEmpty(),
                "unloaded user returns an empty primary rank");

        gateway.primaryGroups.put(playerId, "missing-group");
        RankInfo missing = service.getPrimaryRank(playerId).orElseThrow();
        check(missing.id().equals("missing-group")
                        && missing.displayName().equals("Missing Group")
                        && missing.description().isEmpty()
                        && missing.color().isEmpty()
                        && missing.weight().isEmpty(),
                "missing group data uses a controlled generic fallback");
        gateway.primaryGroups.put(playerId, "vip");

        RankService.RankTrackResult ranks = service.getPublicRanks();
        check(ranks.status() == RankService.RankTrackStatus.AVAILABLE
                        && ranks.trackName().equals("ranks"),
                "configured track is used");
        check(ranks.ranks().stream().map(RankInfo::id).toList()
                        .equals(List.of("default", "vip", "premium", "developer", "event_host")),
                "LuckPerms track order is preserved");
        check(ranks.ranks().get(2).displayName().equals("Premium"),
                "missing display name uses a generic ID fallback");
        check(RankService.fallbackDisplayName("vip").equals("VIP")
                        && RankService.fallbackDisplayName("default").equals("Default")
                        && RankService.fallbackDisplayName("senior_builder").equals("Senior Builder"),
                "fallback normalization has no rank-name mapping");
        RankInfo developer = ranks.ranks().get(3);
        check(developer.displayName().equals("Developer")
                        && developer.description().orElseThrow().equals("Development Team")
                        && developer.color().orElseThrow().equals(TextColor.color(0xc35cff))
                        && developer.weight().orElseThrow() == 80,
                "an arbitrary future rank resolves display, hex color, description, and weight");
        check(ranks.ranks().get(2).color().isEmpty()
                        && ranks.ranks().get(2).effectiveColor().equals(NamedTextColor.WHITE),
                "missing color uses the neutral component fallback");
        check(ranks.ranks().get(4).color().isEmpty()
                        && ranks.ranks().get(4).effectiveColor().equals(NamedTextColor.WHITE),
                "invalid color is safely discarded and uses the neutral fallback");
        check(List.of("gray", "gold", "aqua", "green", "red", "dark_red", "light_purple").stream()
                        .allMatch(value -> RankService.parseColor(value).isPresent()),
                "all required named Adventure colors are accepted generically");
        check(RankService.parseColor("#55ffaa").orElseThrow().equals(TextColor.color(0x55ffaa)),
                "six-digit hex rank colors are accepted");
        check(RankService.parseColor("banana").isEmpty()
                        && RankService.parseColor("#abcd").isEmpty()
                        && RankService.parseColor(" ").isEmpty(),
                "invalid, malformed, and blank colors return empty");

        trackName.set("missing");
        RankService.RankTrackResult missingTrack = service.getPublicRanks();
        check(missingTrack.status() == RankService.RankTrackStatus.MISSING_TRACK
                        && missingTrack.ranks().isEmpty(),
                "missing track has a controlled result state");
        trackName.set("empty");
        check(service.getPublicRanks().status() == RankService.RankTrackStatus.AVAILABLE
                        && service.getPublicRanks().ranks().isEmpty(),
                "existing empty track is distinct from a missing track");
        trackName.set("ranks");
        check(service.getPublicRanks().trackName().equals("ranks"),
                "track supplier is read again after a configuration change");

        System.out.println("RankServiceHarness passed " + checks + " checks.");
    }

    private static LuckPermsService.GroupInformation group(
            String id,
            String displayName,
            String description,
            String color,
            OptionalInt weight
    ) {
        return new LuckPermsService.GroupInformation(
                id,
                Optional.ofNullable(displayName),
                Optional.ofNullable(description),
                Optional.ofNullable(color),
                weight
        );
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Gateway implements RankService.Gateway {

        private final Map<UUID, String> primaryGroups = new HashMap<>();
        private final Map<String, LuckPermsService.GroupInformation> groups = new HashMap<>();
        private final Map<String, List<String>> tracks = new HashMap<>();

        @Override
        public Optional<String> getPrimaryGroup(UUID uniqueId) {
            return Optional.ofNullable(primaryGroups.get(uniqueId));
        }

        @Override
        public Optional<LuckPermsService.GroupInformation> getGroupInformation(String groupId) {
            return Optional.ofNullable(groups.get(groupId));
        }

        @Override
        public Optional<List<String>> getTrackGroups(String trackName) {
            return Optional.ofNullable(tracks.get(trackName));
        }
    }
}
