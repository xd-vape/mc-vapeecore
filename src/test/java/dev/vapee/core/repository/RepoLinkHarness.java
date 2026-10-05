package dev.vapee.core.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Active Markdown links only; historical reports intentionally retain historical path prose. */
public final class RepoLinkHarness {
    private static int checks;
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)]+\\.md)(?:#[^)]*)?\\)");

    public static void main(String[] args) throws Exception {
        List<Path> active = new ArrayList<>(List.of(Path.of("README.md")));
        try (var paths = Files.walk(Path.of("docs"))) {
            active.addAll(paths.filter(p -> p.toString().endsWith(".md")).sorted().toList());
        }
        for (Path file : active) {
            for (Path target : targets(file, Files.readString(file))) {
                check(Files.isRegularFile(target), file + " links to missing " + target);
            }
        }
        check(targets(Path.of("README.md"), "[old](docs/FULL_CODEBASE_AUDIT.md)")
                .stream().noneMatch(Files::exists), "detect removed root-relative report link");
        check(targets(Path.of("docs/DEVELOPER_GUIDE.md"), "[old](FULL_CODEBASE_AUDIT.md)")
                .stream().noneMatch(Files::exists), "detect removed docs-relative report link");
        check(Files.isRegularFile(Path.of("docs/DEVELOPER_GUIDE.md"))
                && Files.isRegularFile(Path.of("docs/FORMATTING.md"))
                && Files.isRegularFile(Path.of("docs/PERMISSIONS.md")), "durable guides remain under docs");
        try (var files = Files.walk(Path.of("reports"))) {
            check(files.filter(p -> p.toString().endsWith(".md")).count() >= 17, "migrated reports plus current phase report");
        }
        System.out.println("RepoLinkHarness passed " + checks + " checks.");
    }

    private static List<Path> targets(Path source, String text) {
        List<Path> paths = new ArrayList<>(); var matcher = LINK.matcher(text);
        while (matcher.find()) {
            String link = matcher.group(1);
            if (link.contains("://")) continue;
            Path parent = source.getParent() == null ? Path.of("") : source.getParent();
            paths.add(parent.resolve(link).normalize());
        }
        return paths;
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
