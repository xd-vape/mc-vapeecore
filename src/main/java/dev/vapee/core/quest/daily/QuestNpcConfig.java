package dev.vapee.core.quest.daily;

import dev.vapee.core.persistence.SafeFileWriter;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/** The placement is persistent. The in-world Mannequin is a disposable runtime entity. */
final class QuestNpcConfig {
    private static final State DEFAULT = new State("Quest Master", "", null);
    private final Path file;
    private final SafeFileWriter writer;

    QuestNpcConfig(Path file, Logger logger) {
        this.file = file;
        this.writer = new SafeFileWriter(logger);
    }

    State read() {
        try {
            writer.requireReconciled(file);
            return parse(load(file));
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Could not read quest NPC configuration at " + file, failure);
        }
    }

    void save(State previous, State next) {
        try {
            YamlConfiguration yaml = load(file);
            if (!parse(yaml).equals(previous))
                throw new IOException("Quest NPC settings changed on disk; reload first");
            yaml.set("name", next.name());
            yaml.set("skin", next.skin());
            Position position = next.position();
            if (position == null) {
                yaml.set("location", null);
            } else {
                yaml.set("location.world", position.world());
                yaml.set("location.x", position.x());
                yaml.set("location.y", position.y());
                yaml.set("location.z", position.z());
                yaml.set("location.yaw", position.yaw());
                yaml.set("location.pitch", position.pitch());
            }
            writer.write(
                    file,
                    yaml::saveToString,
                    candidate -> {
                        if (!parse(load(candidate)).equals(next))
                            throw new IOException(
                                    "Quest NPC candidate differs from requested state");
                    },
                    target -> {
                        if (!parse(load(target)).equals(previous))
                            throw new IOException("Quest NPC settings changed during save");
                    });
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Could not save quest NPC configuration at " + file, failure);
        }
    }

    private static YamlConfiguration load(Path path) throws IOException {
        var yaml = new YamlConfiguration();
        if (Files.notExists(path)) return yaml;
        try {
            yaml.load(path.toFile());
            return yaml;
        } catch (InvalidConfigurationException failure) {
            throw new IOException("Invalid quest NPC YAML", failure);
        }
    }

    private static State parse(YamlConfiguration yaml) {
        var section = yaml.getConfigurationSection("location");
        if (section == null && yaml.contains("location"))
            throw new IllegalArgumentException("Expected a location section");
        var position =
                section == null
                        ? null
                        : new Position(
                                string(section, "world", ""),
                                number(section, "x"),
                                number(section, "y"),
                                number(section, "z"),
                                (float) number(section, "yaw"),
                                (float) number(section, "pitch"));
        return new State(
                string(yaml, "name", DEFAULT.name()),
                string(yaml, "skin", DEFAULT.skin()),
                position);
    }

    private static String string(ConfigurationSection section, String key, String fallback) {
        if (section.get(key, fallback) instanceof String value) return value;
        throw new IllegalArgumentException("Expected a string for " + key);
    }

    private static double number(ConfigurationSection section, String key) {
        if (section.get(key) instanceof Number value) return value.doubleValue();
        throw new IllegalArgumentException("Expected a coordinate for " + key);
    }

    record State(String name, String skin, Position position) {
        State {
            if (name.isBlank()
                    || name.length() > 64
                    || name.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid quest NPC name");
            if (!skin.isEmpty() && !skin.matches("[A-Za-z0-9_]{1,16}"))
                throw new IllegalArgumentException("Invalid Minecraft username");
        }
    }

    record Position(String world, double x, double y, double z, float yaw, float pitch) {
        Position {
            if (world.isBlank()
                    || !coordinate(x)
                    || !coordinate(y)
                    || !coordinate(z)
                    || !Float.isFinite(yaw)
                    || !Float.isFinite(pitch)
                    || Math.abs(pitch) > 90)
                throw new IllegalArgumentException("Invalid quest NPC location");
        }

        Position(Location location) {
            this(
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch());
        }

        int chunkX() {
            return ((int) Math.floor(x)) >> 4;
        }

        int chunkZ() {
            return ((int) Math.floor(z)) >> 4;
        }

        private static boolean coordinate(double value) {
            return Math.abs(value) < 30_000_000;
        }
    }
}
