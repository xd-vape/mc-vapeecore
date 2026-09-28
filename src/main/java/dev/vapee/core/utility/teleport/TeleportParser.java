package dev.vapee.core.utility.teleport;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Arrays;
import java.util.Objects;

/** Command grammar and source-relative coordinate mathematics, without CommandSender state. */
public final class TeleportParser {

    public record Request(String source, String target, String world, String[] coordinates, boolean other) {
        public boolean playerTarget() { return target != null; }
        public boolean explicitWorld() { return world != null; }
    }

    private TeleportParser() { }

    public static Request parse(String[] args) {
        Objects.requireNonNull(args, "args");
        if (args.length == 1) return new Request(null, args[0], null, null, false);
        if (args.length == 2) return new Request(args[0], args[1], null, null, true);
        if (args.length < 3 || args.length > 8) throw new IllegalArgumentException("Invalid teleport usage.");
        boolean selfWorld = args[0].equalsIgnoreCase("world") && (args.length == 5 || args.length == 7);
        boolean other = !selfWorld && ((args.length > 1 && args[1].equalsIgnoreCase("world"))
                || args.length == 4 || args.length == 6 || args.length == 8);
        int offset = other ? 1 : 0;
        boolean explicitWorld = args[offset].equalsIgnoreCase("world");
        int coordinateStart = offset + (explicitWorld ? 2 : 0);
        int remaining = args.length - coordinateStart;
        if (remaining != 3 && remaining != 5) throw new IllegalArgumentException("Invalid teleport usage.");
        if (explicitWorld && args[offset + 1].isBlank())
            throw new IllegalArgumentException("Invalid teleport usage.");
        return new Request(other ? args[0] : null, null,
                explicitWorld ? args[offset + 1] : null,
                Arrays.copyOfRange(args, coordinateStart, args.length), other);
    }

    public static Location destination(Request request, Location source, World world) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(world, "world");
        String[] tokens = Objects.requireNonNull(request.coordinates(), "coordinates");
        if (tokens.length != 3 && tokens.length != 5)
            throw new IllegalArgumentException("Invalid teleport usage.");
        boolean local = tokens[0].startsWith("^") || tokens[1].startsWith("^") || tokens[2].startsWith("^");
        if (local && !(tokens[0].startsWith("^") && tokens[1].startsWith("^") && tokens[2].startsWith("^")))
            throw new IllegalArgumentException("Local coordinates cannot be mixed with world/relative coordinates.");
        double x, y, z;
        if (local) {
            double left = number(tokens[0].substring(1), "coordinate", true);
            double up = number(tokens[1].substring(1), "coordinate", true);
            double forward = number(tokens[2].substring(1), "coordinate", true);
            double yaw = Math.toRadians(source.getYaw());
            double pitch = Math.toRadians(source.getPitch());
            double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
            double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
            x = source.getX() + left * cosYaw + up * sinYaw * sinPitch - forward * sinYaw * cosPitch;
            y = source.getY() + up * cosPitch - forward * sinPitch;
            z = source.getZ() + left * sinYaw - up * cosYaw * sinPitch + forward * cosYaw * cosPitch;
        } else {
            x = coordinate(tokens[0], source.getX());
            y = coordinate(tokens[1], source.getY());
            z = coordinate(tokens[2], source.getZ());
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Invalid coordinate: destination must be finite.");
        float finalYaw = source.getYaw(), finalPitch = source.getPitch();
        if (tokens.length == 5) {
            finalYaw = rotation(tokens[3], finalYaw);
            finalPitch = rotation(tokens[4], finalPitch);
        }
        if (!Float.isFinite(finalYaw) || !Float.isFinite(finalPitch))
            throw new IllegalArgumentException("Invalid rotation: value must be finite.");
        finalPitch = Math.max(-90.0F, Math.min(90.0F, finalPitch));
        return new Location(world, x, y, z, finalYaw, finalPitch);
    }

    private static double coordinate(String token, double base) {
        if (token.startsWith("~")) return base + number(token.substring(1), "coordinate", true);
        return number(token, "coordinate", false);
    }

    private static float rotation(String token, float base) {
        double result = token.startsWith("~")
                ? base + number(token.substring(1), "rotation", true)
                : number(token, "rotation", false);
        if (!Double.isFinite(result) || result > Float.MAX_VALUE || result < -Float.MAX_VALUE)
            throw new IllegalArgumentException("Invalid rotation: value must be finite.");
        return (float) result;
    }

    private static double number(String token, String kind, boolean emptyMeansZero) {
        if (emptyMeansZero && token.isEmpty()) return 0.0D;
        if (token.isEmpty() || token.isBlank()) throw new IllegalArgumentException("Invalid " + kind + ": " + token);
        try {
            double value = Double.parseDouble(token);
            if (Double.isFinite(value)) return value;
        } catch (NumberFormatException ignored) {
            // One controlled error for invalid text and overflow alike.
        }
        throw new IllegalArgumentException("Invalid " + kind + ": " + token);
    }
}
