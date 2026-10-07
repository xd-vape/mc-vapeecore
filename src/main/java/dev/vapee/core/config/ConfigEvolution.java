package dev.vapee.core.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

/** Startup-only evolution of operator configuration; never discovers or migrates persistence files. */
public final class ConfigEvolution {
    public static final String VERSION_KEY = "config-version";
    // All six files were unversioned before this first operator-schema release.
    // A subsequent defaults/schema change must deliberately bump the affected file's version here and in its resource.
    public static final Map<String, Integer> MANAGED_CONFIGS = Map.of(
            "config.yml", 1, "lobby.yml", 1, "chat.yml", 1,
            "private-messages.yml", 1, "presentation.yml", 1, "daily-quests.yml", 1);

    private final Path directory;
    private final Logger logger;
    private final Function<String, InputStream> resources;
    private final FileAccess files;

    public ConfigEvolution(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath(), plugin.getLogger(), plugin::getResource);
    }

    public ConfigEvolution(Path directory, Logger logger, Function<String, InputStream> resources) {
        this(directory, logger, resources, new FileAccess());
    }

    ConfigEvolution(Path directory, Logger logger, Function<String, InputStream> resources, FileAccess files) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger);
        this.resources = Objects.requireNonNull(resources);
        this.files = Objects.requireNonNull(files);
    }

    public void evolveAll() {
        for (String name : MANAGED_CONFIGS.keySet().stream().sorted().toList()) evolve(name);
    }

    public boolean evolve(String name) {
        Integer supported = MANAGED_CONFIGS.get(name);
        if (supported == null) throw new IllegalArgumentException("Not an operator configuration: " + name);
        Path target = directory.resolve(name);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false; // The owning loader creates new installs.
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Operator configuration must be a regular file: " + target);
        }
        try {
            byte[] original = Files.readAllBytes(target);
            Yaml yaml = yaml();
            MappingNode current = mapping(yaml.compose(new StringReader(new String(original, StandardCharsets.UTF_8))));
            Map<String, NodeTuple> entries = entries(current);
            NodeTuple versionEntry = entries.get(VERSION_KEY);
            int version = 0;
            if (versionEntry != null) {
                Node value = versionEntry.getValueNode();
                if (!(value instanceof ScalarNode scalar) || !Tag.INT.equals(value.getTag())
                        || !scalar.getValue().matches("[0-9]+")) {
                    logger.warning(name + ": invalid config-version; file left unchanged.");
                    return false;
                }
                try { version = Integer.parseInt(scalar.getValue()); }
                catch (NumberFormatException invalid) {
                    logger.warning(name + ": unsupported config-version; file left unchanged.");
                    return false;
                }
            }
            if (version > supported) {
                logger.warning(name + ": future config-version " + version + " (supported " + supported
                        + "); no downgrade or rewrite. Runtime uses its supported keys.");
                return false;
            }
            if (version == supported) return false;

            validateAcyclic(current, new IdentityHashMap<>());

            MappingNode defaults;
            try (InputStream resource = resources.apply(name)) {
                if (resource == null) throw new IOException("Missing default resource " + name);
                defaults = mapping(yaml.compose(new StringReader(new String(resource.readAllBytes(), StandardCharsets.UTF_8))));
            }
            NodeTuple defaultVersion = entries(defaults).get(VERSION_KEY);
            if (defaultVersion == null || !Tag.INT.equals(defaultVersion.getValueNode().getTag())
                    || !Integer.toString(supported).equals(((ScalarNode) defaultVersion.getValueNode()).getValue())) {
                throw new IOException("Resource config-version does not match registry: " + name);
            }
            Object before = yaml.load(new String(original, StandardCharsets.UTF_8));
            List<String> added = new ArrayList<>();
            merge(current, defaults, "", added, name);
            // config-version alone is owned by evolution; no operator value is replaced.
            if (versionEntry != null) {
                int index = current.getValue().indexOf(versionEntry);
                Node replacement = defaultVersion.getValueNode();
                replacement.setBlockComments(versionEntry.getValueNode().getBlockComments());
                replacement.setInLineComments(versionEntry.getValueNode().getInLineComments());
                current.getValue().set(index, new NodeTuple(versionEntry.getKeyNode(), replacement));
            }
            StringWriter writer = new StringWriter();
            yaml.serialize(current, writer);
            byte[] candidate = writer.toString().getBytes(StandardCharsets.UTF_8);
            Object after = yaml.load(writer.toString());
            if (!preserved(before, after, true)
                    || !(after instanceof Map<?, ?> map) || !Integer.valueOf(supported).equals(map.get(VERSION_KEY))) {
                throw new IOException("Candidate failed preservation/version validation for " + name);
            }
            // A permanent, unique, exact-byte backup exists before any candidate write.
            Path backupDirectory = directory.resolve("backups/config");
            Files.createDirectories(backupDirectory);
            Path backup = backupDirectory.resolve(name + ".v" + version + "-to-v" + supported + "-"
                    + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".bak");
            Files.write(backup, original, java.nio.file.StandardOpenOption.CREATE_NEW);
            Path temporary = Files.createTempFile(directory, "." + name + "-", ".tmp");
            try {
                files.write(temporary, candidate);
                if (!Arrays.equals(candidate, Files.readAllBytes(temporary))) throw new IOException("Candidate write mismatch");
                yaml.load(Files.readString(temporary));
                if (!Arrays.equals(original, Files.readAllBytes(target))) {
                    throw new IOException("Configuration changed during migration; refusing to replace " + target);
                }
                try { files.replace(temporary, target, true); }
                catch (AtomicMoveNotSupportedException unsupported) {
                    logger.warning(name + ": atomic replacement unavailable; using recoverable replacement with " + backup);
                    try { files.replace(temporary, target, false); }
                    catch (IOException failure) {
                        try { Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING); }
                        catch (IOException restoreFailure) { failure.addSuppressed(restoreFailure); }
                        throw failure;
                    }
                }
            } finally { Files.deleteIfExists(temporary); }
            logger.info("Evolved " + name + " from config-version " + version + " to " + supported
                    + "; added " + added + "; backup: " + backup);
            return true;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not evolve operator configuration " + target
                    + ". Original/backup must be reviewed before retrying startup.", failure);
        }
    }

    private void merge(MappingNode current, MappingNode defaults, String prefix, List<String> added, String name) {
        Map<String, NodeTuple> existing = entries(current);
        for (var entry : entries(defaults).entrySet()) {
            String key = entry.getKey();
            String path = prefix + key;
            NodeTuple present = existing.get(key);
            Node defaultValue = entry.getValue().getValueNode();
            if (present == null) {
                current.getValue().add(entry.getValue());
                added.add(path);
            } else if (!path.equals(VERSION_KEY)) {
                Node value = present.getValueNode();
                if (value instanceof MappingNode nested && defaultValue instanceof MappingNode nestedDefaults) {
                    merge(nested, nestedDefaults, path + ".", added, name);
                } else if (!value.getTag().equals(defaultValue.getTag())) {
                    logger.warning(name + ": type conflict at '" + path + "'; existing value preserved, loader fallback applies.");
                }
            }
        }
    }

    private static MappingNode mapping(Node node) {
        if (node == null) return new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
        if (node instanceof MappingNode mapping) return mapping;
        throw new IllegalArgumentException("Expected a YAML mapping at configuration root");
    }

    private static Map<String, NodeTuple> entries(MappingNode mapping) {
        Map<String, NodeTuple> result = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key) || !Tag.STR.equals(key.getTag())) {
                throw new IllegalArgumentException("Operator config mappings require string keys; YAML merge keys are unsupported");
            }
            if (result.putIfAbsent(key.getValue(), tuple) != null) throw new IllegalArgumentException("Duplicate key " + key.getValue());
        }
        return result;
    }

    private static boolean preserved(Object before, Object after, boolean root) {
        if (before == null && root) return true; // Empty legacy file.
        if (before instanceof Map<?, ?> oldMap && after instanceof Map<?, ?> newMap) {
            for (var entry : oldMap.entrySet()) {
                if (root && VERSION_KEY.equals(entry.getKey())) continue;
                if (!newMap.containsKey(entry.getKey()) || !preserved(entry.getValue(), newMap.get(entry.getKey()), false)) return false;
            }
            return true;
        }
        return Objects.equals(before, after); // Lists, scalars and null are atomic existing values.
    }

    private static void validateAcyclic(Node node, IdentityHashMap<Node, Boolean> ancestors) {
        if (ancestors.put(node, true) != null) {
            throw new IllegalArgumentException("Recursive YAML aliases cannot be evolved safely");
        }
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                validateAcyclic(tuple.getKeyNode(), ancestors);
                validateAcyclic(tuple.getValueNode(), ancestors);
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node child : sequence.getValue()) validateAcyclic(child, ancestors);
        }
        ancestors.remove(node);
    }

    private static Yaml yaml() {
        LoaderOptions loader = new LoaderOptions();
        loader.setProcessComments(true);
        loader.setAllowDuplicateKeys(false);
        DumperOptions dumper = new DumperOptions();
        dumper.setProcessComments(true);
        return new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
    }

    /** Filesystem boundary for exercising failed writes and atomic-move fallback without replacing merge logic. */
    static class FileAccess {
        void write(Path target, byte[] content) throws IOException { Files.write(target, content); }
        void replace(Path temporary, Path target, boolean atomic) throws IOException {
            if (atomic) Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
