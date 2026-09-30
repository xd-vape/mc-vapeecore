package dev.vapee.core.config;

import dev.vapee.core.clan.ClanLimits;
import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.rank.staff.StaffHierarchyConfig;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import dev.vapee.core.onlinereward.OnlineRewardConfig;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

public final class ConfigService implements ReloadParticipant {

    private static final String RESOURCE_NAME = "config.yml";
    private static final String DEFAULT_SERVER_NAME = "Vapee Community";
    private static final String DEFAULT_MESSAGE_PREFIX = "<gray>[<aqua>VapeeCore</aqua>]</gray> ";
    public static final String DEFAULT_RANK_TRACK = "ranks";

    private final Runnable defaultConfigSaver;
    private final Logger logger;
    private final Path configFile;

    private volatile CoreConfigState state = CoreConfigState.defaults();

    public ConfigService(JavaPlugin plugin) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        this.defaultConfigSaver = validatedPlugin::saveDefaultConfig;
        this.logger = validatedPlugin.getLogger();
        this.configFile = validatedPlugin.getDataFolder().toPath()
                .resolve(RESOURCE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    ConfigService(Path configFile, Logger logger) {
        this.defaultConfigSaver = () -> { };
        this.logger = Objects.requireNonNull(logger, "logger");
        this.configFile = Objects.requireNonNull(configFile, "configFile")
                .toAbsolutePath()
                .normalize();
    }

    public void load() {
        defaultConfigSaver.run();
        state = readState();
    }

    @Override
    public String getReloadName() {
        return RESOURCE_NAME;
    }

    @Override
    public ReloadPlan prepareReload() {
        CoreConfigState previousState = state;
        CoreConfigState preparedState = readState();
        return ReloadPlan.of(
                () -> state = preparedState,
                () -> state = previousState
        );
    }

    public String getServerName() {
        return state.serverName();
    }

    public String getMessagePrefix() {
        return state.messagePrefix();
    }

    public boolean isDebugEnabled() {
        return state.debugEnabled();
    }

    public String getRankTrack() {
        return state.rankTrack();
    }

    public OnlineRewardConfig getOnlineRewardConfig() {
        return state.onlineRewardConfig();
    }

    public FriendLimits getFriendLimits() {
        return state.friendLimits();
    }

    public ClanLimits getClanLimits() {
        return state.clanLimits();
    }

    public StaffHierarchyConfig getStaffHierarchyConfig() { return state.staffHierarchyConfig(); }

    private CoreConfigState readState() {
        YamlConfiguration configuration = loadConfiguration();
        return new CoreConfigState(
                readString(configuration, "server.name", DEFAULT_SERVER_NAME),
                readString(configuration, "messages.prefix", DEFAULT_MESSAGE_PREFIX),
                readBoolean(configuration, "settings.debug", false),
                readNonBlankString(configuration, "ranks.track", DEFAULT_RANK_TRACK),
                readOnlineRewardConfig(configuration),
                readFriendLimits(configuration),
                readClanLimits(configuration),
                readStaffHierarchy(configuration)
        );
    }

    private FriendLimits readFriendLimits(YamlConfiguration configuration) {
        return new FriendLimits(
                readPositiveInt(configuration, "friends.limits.max-friends", FriendLimits.DEFAULT_MAX_FRIENDS),
                readPositiveInt(configuration, "friends.limits.max-incoming-requests",
                        FriendLimits.DEFAULT_MAX_INCOMING_REQUESTS),
                readPositiveInt(configuration, "friends.limits.max-outgoing-requests",
                        FriendLimits.DEFAULT_MAX_OUTGOING_REQUESTS)
        );
    }

    private StaffHierarchyConfig readStaffHierarchy(YamlConfiguration configuration) {
        String path = "staff.hierarchy.protected-groups";
        Object value = configuration.get(path);
        if (value == null && !configuration.contains(path)) {
            if ((configuration.contains("staff") && !configuration.isConfigurationSection("staff"))
                    || (configuration.contains("staff.hierarchy") && !configuration.isConfigurationSection("staff.hierarchy"))) {
                warnInvalidValue(path, "a non-empty ordered group list", StaffHierarchyConfig.DEFAULT_GROUPS);
            }
            return StaffHierarchyConfig.defaults();
        }
        try {
            if (!(value instanceof java.util.List<?> groups)
                    || groups.stream().anyMatch(group -> !(group instanceof String))) {
                throw new IllegalArgumentException("group list expected");
            }
            return new StaffHierarchyConfig(groups.stream().map(String.class::cast).toList());
        } catch (IllegalArgumentException | NullPointerException exception) {
            warnInvalidValue(path, "a non-empty ordered list of unique non-blank group IDs without controls or whitespace",
                    StaffHierarchyConfig.DEFAULT_GROUPS);
            return StaffHierarchyConfig.defaults();
        }
    }

    private ClanLimits readClanLimits(YamlConfiguration configuration) {
        ClanLimits defaults = ClanLimits.defaults();
        int minName = readPositiveInt(configuration, "clans.limits.name.min-length", defaults.minNameLength());
        int maxName = readPositiveInt(configuration, "clans.limits.name.max-length", defaults.maxNameLength());
        int minTag = readPositiveInt(configuration, "clans.limits.tag.min-length", defaults.minTagLength());
        int maxTag = readPositiveInt(configuration, "clans.limits.tag.max-length", defaults.maxTagLength());
        if (minName > maxName) {
            logger.warning("Invalid clan name length range; using default name limits.");
            minName = defaults.minNameLength();
            maxName = defaults.maxNameLength();
        }
        if (minTag > maxTag) {
            logger.warning("Invalid clan tag length range; using default tag limits.");
            minTag = defaults.minTagLength();
            maxTag = defaults.maxTagLength();
        }
        return new ClanLimits(
                readPositiveInt(configuration, "clans.limits.max-members", defaults.maxMembers()),
                readPositiveInt(configuration, "clans.limits.max-outgoing-invites", defaults.maxOutgoingInvites()),
                readPositiveInt(configuration, "clans.limits.max-incoming-invites", defaults.maxIncomingInvites()),
                minName, maxName, minTag, maxTag);
    }

    private int readPositiveInt(YamlConfiguration configuration, String path, int defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }
        Object value = configuration.get(path);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long candidate = ((Number) value).longValue();
            if (candidate > 0L && candidate <= Integer.MAX_VALUE) {
                return (int) candidate;
            }
        }
        warnInvalidValue(path, "a positive integer", defaultValue);
        return defaultValue;
    }

    private OnlineRewardConfig readOnlineRewardConfig(YamlConfiguration configuration) {
        boolean enabled = readBoolean(
                configuration,
                "online-rewards.enabled",
                OnlineRewardConfig.DEFAULT_ENABLED
        );
        long intervalMinutes = readPositiveLong(
                configuration,
                "online-rewards.interval-minutes",
                OnlineRewardConfig.DEFAULT_INTERVAL_MINUTES,
                true
        );
        long coins = readPositiveLong(
                configuration,
                "online-rewards.coins",
                OnlineRewardConfig.DEFAULT_COINS,
                false
        );
        boolean messageEnabled = readBoolean(
                configuration,
                "online-rewards.message.enabled",
                OnlineRewardConfig.DEFAULT_MESSAGE_ENABLED
        );
        String messageFormat = readOnlineRewardMessageFormat(configuration);
        return new OnlineRewardConfig(
                enabled,
                intervalMinutes,
                coins,
                messageEnabled,
                messageFormat
        );
    }

    private YamlConfiguration loadConfiguration() {
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalStateException("Required configuration file does not exist: " + configFile);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            String source = Files.readString(configFile);
            configuration.loadFromString(source);
            retainExplicitStaffNull(configuration, source);
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load configuration at " + configFile + ".", exception);
        }
    }

    private static void retainExplicitStaffNull(YamlConfiguration configuration, String source) {
        // Bukkit removes explicit YAML nulls. Preserve only this security setting's distinction
        // between a missing legacy key and an explicitly invalid null (including null parents).
        org.yaml.snakeyaml.nodes.Node node = new org.yaml.snakeyaml.Yaml().compose(new java.io.StringReader(source));
        String path = "";
        for (String key : java.util.List.of("staff", "hierarchy", "protected-groups")) {
            if (!(node instanceof org.yaml.snakeyaml.nodes.MappingNode mapping)) return;
            node = mapping.getValue().stream()
                    .filter(tuple -> tuple.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode scalar
                            && scalar.getValue().equals(key))
                    .map(org.yaml.snakeyaml.nodes.NodeTuple::getValueNode).findFirst().orElse(null);
            if (node == null) return;
            path = path.isEmpty() ? key : path + "." + key;
            if (node.getTag().equals(org.yaml.snakeyaml.nodes.Tag.NULL)) {
                configuration.set(path, false); // Non-list sentinel triggers the usual warning/default path.
                return;
            }
        }
    }

    private String readString(YamlConfiguration configuration, String path, String defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (value instanceof String stringValue) {
            return stringValue;
        }

        warnInvalidValue(path, "a string", defaultValue);
        return defaultValue;
    }

    private boolean readBoolean(YamlConfiguration configuration, String path, boolean defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        warnInvalidValue(path, "a boolean", defaultValue);
        return defaultValue;
    }

    private String readNonBlankString(YamlConfiguration configuration, String path, String defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }

        warnInvalidValue(path, "a non-blank string", defaultValue);
        return defaultValue;
    }

    private long readPositiveLong(
            YamlConfiguration configuration,
            String path,
            long defaultValue,
            boolean validateTickConversion
    ) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            warnInvalidValue(path, "a positive integer", defaultValue);
            return defaultValue;
        }

        long longValue = ((Number) value).longValue();
        if (longValue <= 0L) {
            warnInvalidValue(path, "a positive integer", defaultValue);
            return defaultValue;
        }
        if (validateTickConversion) {
            try {
                Math.multiplyExact(longValue, OnlineRewardConfig.TICKS_PER_MINUTE);
            } catch (ArithmeticException exception) {
                warnInvalidValue(path, "a positive integer that safely converts to ticks", defaultValue);
                return defaultValue;
            }
        }
        return longValue;
    }

    private String readOnlineRewardMessageFormat(YamlConfiguration configuration) {
        String path = "online-rewards.message.format";
        if (!configuration.contains(path)) {
            return OnlineRewardConfig.DEFAULT_MESSAGE_FORMAT;
        }

        Object value = configuration.get(path);
        if (value instanceof String stringValue
                && !stringValue.isBlank()
                && OnlineRewardConfig.isValidMessageFormat(stringValue)) {
            return stringValue;
        }

        warnInvalidValue(path, "a valid non-blank MiniMessage string", OnlineRewardConfig.DEFAULT_MESSAGE_FORMAT);
        return OnlineRewardConfig.DEFAULT_MESSAGE_FORMAT;
    }

    private void warnInvalidValue(String path, String expected, Object fallback) {
        logger.warning("Invalid core setting '" + path + "' in " + configFile
                + ": expected " + expected + "; using '" + fallback
                + "'. The file was left unchanged."
        );
    }

    private record CoreConfigState(
            String serverName,
            String messagePrefix,
            boolean debugEnabled,
            String rankTrack,
            OnlineRewardConfig onlineRewardConfig,
            FriendLimits friendLimits,
            ClanLimits clanLimits,
            StaffHierarchyConfig staffHierarchyConfig
    ) {

        private CoreConfigState {
            Objects.requireNonNull(serverName, "serverName");
            Objects.requireNonNull(messagePrefix, "messagePrefix");
            Objects.requireNonNull(rankTrack, "rankTrack");
            Objects.requireNonNull(onlineRewardConfig, "onlineRewardConfig");
            Objects.requireNonNull(friendLimits, "friendLimits");
            Objects.requireNonNull(clanLimits, "clanLimits");
            Objects.requireNonNull(staffHierarchyConfig, "staffHierarchyConfig");
        }

        private static CoreConfigState defaults() {
            return new CoreConfigState(
                    DEFAULT_SERVER_NAME,
                    DEFAULT_MESSAGE_PREFIX,
                    false,
                    DEFAULT_RANK_TRACK,
                    OnlineRewardConfig.defaults(),
                    FriendLimits.defaults(),
                    ClanLimits.defaults(),
                    StaffHierarchyConfig.defaults()
            );
        }
    }
}
