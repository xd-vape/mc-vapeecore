package dev.vapee.core.utility;

import dev.vapee.core.utility.teleport.TeleportParser;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Proxy;

public final class TeleportParserHarness {
    private static int checks;
    private static final World WORLD = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, args) -> method.getName().equals("getName") ? "world" : null);

    public static void main(String[] args) {
        grammar();
        coordinates();
        localMath();
        rotation();
        System.out.println("TeleportParserHarness passed " + checks + " checks.");
    }

    private static void grammar() {
        check(!request("Steve").other() && request("Steve").playerTarget(), "self player");
        check(request("Steve", "Alex").other() && request("Steve", "Alex").playerTarget(), "other player");
        check(!request("1", "2", "3").other(), "self coordinates");
        check(request("Steve", "1", "2", "3").other(), "other coordinates");
        check(request("world", "world", "1", "2", "3").explicitWorld(), "self world");
        check(request("Steve", "world", "world", "1", "2", "3").other(), "other world");
        check(request("1", "2", "3", "4", "5").coordinates().length == 5, "self rotation");
        check(request("Steve", "1", "2", "3", "4", "5").coordinates().length == 5, "other rotation");
        check(request("world", "world", "1", "2", "3", "4", "5").coordinates().length == 5, "world rotation");
        check(request("Steve", "world", "world", "1", "2", "3", "4", "5").coordinates().length == 5,
                "other world rotation");
        check(request("world", "1", "2", "3").other(), "player named world can be an explicit source");
        check(request("world", "world", "world", "1", "2", "3").other(),
                "player named world can target an explicit world");
        rejectGrammar();
    }

    private static void rejectGrammar() {
        for (String[] tokens : new String[][]{{}, {"1", "2", "3", "4", "5", "6", "7", "8", "9"},
                {"world", "world", "1"}, {"Steve", "world", "world", "1", "2"}}) {
            try { TeleportParser.parse(tokens); throw new AssertionError("invalid grammar accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
    }

    private static void coordinates() {
        Location base = new Location(WORLD, 100, 64, -20, 45, 10);
        assertPos(destination(base, "0", "12.5", "-42.75"), 0, 12.5, -42.75, "absolute decimals");
        assertPos(destination(base, "~", "~0", "~"), 100, 64, -20, "bare relative");
        assertPos(destination(base, "~5", "~10", "~-2"), 105, 74, -22, "positive negative relative");
        assertPos(destination(base, "100", "~5.5", "-200"), 100, 69.5, -200, "absolute relative mix");
        for (String token : new String[]{"NaN", "Infinity", "-Infinity", "1e999999", "garbage", ""})
            reject(base, "Invalid coordinate", token, "0", "0");
        reject(base, "Local coordinates cannot be mixed", "^", "~", "10");
        reject(base, "Local coordinates cannot be mixed", "10", "^", "^");
        reject(base, "Local coordinates cannot be mixed", "~", "^", "~");
        reject(new Location(WORLD, 1e308, 0, 0), "Invalid coordinate", "~1e308", "0", "0");
    }

    private static void localMath() {
        Location base = new Location(WORLD, 100, 64, 100);
        assertPos(destination(base, "^", "^", "^5"), 100, 64, 105, "yaw zero forward");
        assertPos(destination(base, "^", "^", "^-5"), 100, 64, 95, "backward");
        assertPos(destination(base, "^2", "^", "^"), 102, 64, 100, "positive left axis");
        assertPos(destination(base, "^-2", "^", "^"), 98, 64, 100, "negative left axis");
        assertPos(destination(base, "^", "^2", "^"), 100, 66, 100, "up");
        assertPos(destination(base, "^1.5", "^2", "^3"), 101.5, 66, 103, "combined decimal");
        base.setYaw(90);
        assertPos(destination(base, "^", "^", "^5"), 95, 64, 100, "yaw 90");
        base.setYaw(180);
        assertPos(destination(base, "^", "^", "^5"), 100, 64, 95, "yaw 180");
        base.setYaw(-90);
        assertPos(destination(base, "^", "^", "^5"), 105, 64, 100, "yaw minus 90");
        base.setYaw(0); base.setPitch(30);
        assertPos(destination(base, "^", "^", "^2"), 100, 63, 100 + Math.sqrt(3), "positive pitch");
        base.setPitch(-30);
        assertPos(destination(base, "^", "^", "^2"), 100, 65, 100 + Math.sqrt(3), "negative pitch");
        base.setPitch(30);
        assertPos(destination(base, "^", "^2", "^"), 100, 64 + Math.sqrt(3), 99, "pitched up axis");
    }

    private static void rotation() {
        Location base = new Location(WORLD, 0, 0, 0, 45, 20);
        Location unchanged = destination(base, "0", "0", "0");
        check(unchanged.getYaw() == 45 && unchanged.getPitch() == 20, "rotation retained");
        Location absolute = destination(base, "0", "0", "0", "90", "-30");
        check(absolute.getYaw() == 90 && absolute.getPitch() == -30, "absolute rotation");
        Location relative = destination(base, "0", "0", "0", "~180", "~-10");
        check(relative.getYaw() == 225 && relative.getPitch() == 10, "relative rotation");
        check(destination(base, "0", "0", "0", "~", "~").getYaw() == 45, "bare relative rotation");
        check(destination(base, "0", "0", "0", "0", "100").getPitch() == 90, "pitch upper clamp");
        check(destination(base, "0", "0", "0", "0", "-100").getPitch() == -90, "pitch lower clamp");
        for (String invalid : new String[]{"NaN", "Infinity", "-Infinity", "1e999999", "bad"}) {
            reject(base, "Invalid rotation", "0", "0", "0", invalid, "0");
            reject(base, "Invalid rotation", "0", "0", "0", "0", invalid);
        }
    }

    private static TeleportParser.Request request(String... tokens) { return TeleportParser.parse(tokens); }
    private static Location destination(Location base, String... tokens) {
        return TeleportParser.destination(request(tokens), base, WORLD);
    }
    private static void reject(Location base, String message, String... tokens) {
        try { destination(base, tokens); throw new AssertionError("Accepted invalid tokens"); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(message), "distinct " + message);
        }
    }
    private static void assertPos(Location actual, double x, double y, double z, String message) {
        check(Math.abs(actual.getX() - x) < 0.000001 && Math.abs(actual.getY() - y) < 0.000001
                && Math.abs(actual.getZ() - z) < 0.000001, message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
