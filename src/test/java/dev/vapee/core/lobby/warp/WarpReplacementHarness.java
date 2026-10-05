package dev.vapee.core.lobby.warp;

import dev.vapee.core.config.ReplacementFaultProbe;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public final class WarpReplacementHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (var mode : ReplacementFaultProbe.Mode.values()) {
            for (boolean unsupported : new boolean[]{false, true}) test(mode, unsupported);
        }
        System.out.println("WarpReplacementHarness passed " + checks + " checks.");
    }

    private static void test(ReplacementFaultProbe.Mode mode, boolean unsupported) throws Exception {
        Path root = Path.of("target/harness-temp");
        Files.createDirectories(root);
        Path file = Files.createTempDirectory(root, "warp-replacement-").resolve("warps.yml");
        String original = "# operator comment\nforeign: keep-on-read\nwarps: {}\n";
        Files.writeString(file, original);
        ReplacementFaultProbe probe = new ReplacementFaultProbe(mode, unsupported);
        WarpConfig config = new WarpConfig(file, Logger.getAnonymousLogger(), new WarpConfig.ReplacementIO() {
            @Override void move(Path source, Path target, CopyOption... options) throws IOException { probe.move(source, target, options); }
            @Override void copy(Path source, Path target) throws IOException { probe.copy(source, target); }
        });
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (p, m, a) -> m.getName().equals("getName") ? "world" : null);
        WarpService service = new WarpService(config, name -> world, config.initialize());
        check(Files.readString(file).equals(original), "Warp load never rewrites operator YAML");
        var stateField = WarpService.class.getDeclaredField("warps");
        stateField.setAccessible(true);
        Object before = stateField.get(service);
        probe.beforeOperation = () -> check(!service.hasWarp("new"), "Warp candidate remains unpublished during replacement");
        probe.afterCommit = () -> check(!service.hasWarp("new"), "Warp publication follows completed disk commit");
        RuntimeException failure = null;
        try { service.setWarp("new", new Location(world, 1, 2, 3)); }
        catch (RuntimeException exception) { failure = exception; }
        check((failure != null) == (mode == ReplacementFaultProbe.Mode.FAIL), "Warp replacement controlled outcome");
        check(probe.waits.stream().distinct().count() == 1, "Warp replacement performs zero deliberate waits");
        List<String> expected = mode == ReplacementFaultProbe.Mode.ATOMIC ? List.of("atomic")
                : mode == ReplacementFaultProbe.Mode.REPLACE ? List.of("atomic", "replace") : List.of("atomic", "replace", "copy");
        check(probe.calls.equals(expected), "Warp has one attempt per supported replacement stage");
        check(probe.threads.stream().allMatch(id -> id == Thread.currentThread().threadId()), "Warp replacement stays on caller thread");
        if (failure != null) {
            check(stateField.get(service) == before && !service.hasWarp("new"), "failed Warp save retains exact current map");
            check(Files.readString(file).equals(original), "injected failed Warp replacement preserves prior destination");
            check(failure.getCause() == probe.faults.getLast()
                    && Arrays.asList(failure.getCause().getSuppressed()).containsAll(probe.faults.subList(0, 2)),
                    "Warp failure retains atomic move and copy causes");
            check(probe.commits == 0, "failed Warp save performs no successful commit");
        } else {
            check(stateField.get(service) != before && service.hasWarp("new") && probe.commits == 1,
                    "successful Warp save publishes one committed candidate");
            check(new WarpConfig(file, Logger.getAnonymousLogger()).loadWarps().get("new").equals(service.getWarp("new").orElseThrow()),
                    "Warp replacement has unchanged persisted domain roundtrip");
            int calls = probe.calls.size();
            service.setNavigatorOrder("new", 0);
            check(probe.calls.size() == calls, "Warp same-value mutation performs no extra save");
        }
        try (var files = Files.list(file.getParent())) {
            check(files.count() == 1, "Warp replacement removes its temporary file without new backup/schema artifacts");
        }
    }

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
}
