package dev.vapee.core.clan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClanPersistenceHarness {
    private static int checks;
    private static final Instant NOW = Instant.ofEpochMilli(1000);

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-clan-harness-");
        try {
            roundTrip(directory);
            corruption(directory);
            atomicSave(directory);
            System.out.println("ClanPersistenceHarness passed " + checks + " checks.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void roundTrip(Path directory) throws IOException {
        Path path = directory.resolve("missing").resolve("clans.yml");
        FileClanRepository repository = new FileClanRepository(path);
        check(repository.initialize().equals(ClanSnapshot.empty()), "missing file loads empty snapshot");
        check(Files.notExists(path), "missing file is not written on initialize");
        Clan first = clan(2, "Café Clan", "CAFÉ", owner(5), member(3));
        Clan second = clan(1, "Emoji 😀", "EM", owner(4));
        ClanSnapshot snapshot = new ClanSnapshot(List.of(first, second),
                List.of(invite(2, 9), invite(1, 8)));
        repository.save(snapshot);
        String saved = Files.readString(path, StandardCharsets.UTF_8);
        check(saved.contains("schema-version: 1"), "schema version 1 written");
        check(saved.contains("Café Clan") && saved.contains("Emoji 😀"), "UTF-8 Unicode names written");
        check(saved.contains("role: OWNER") && saved.contains("role: MEMBER"), "member roles written");
        check(saved.indexOf(id(1).toString()) < saved.indexOf(id(2).toString()), "clans saved in UUID order");
        check(saved.indexOf(id(3).toString()) < saved.indexOf(id(5).toString()), "members saved in UUID order");
        check(repository.initialize().equals(snapshot), "valid save/load roundtrip");
        repository.save(new ClanSnapshot(List.of(second, first), List.of(invite(1, 8), invite(2, 9))));
        check(Files.readString(path, StandardCharsets.UTF_8).equals(saved), "deterministic YAML independent of input order");
        check(tempFiles(path).isEmpty(), "successful save leaves no temporary files");
    }

    private static void corruption(Path directory) throws IOException {
        Path path = directory.resolve("corrupt").resolve("clans.yml");
        Files.createDirectories(path.getParent());
        String valid = yaml(clanEntry(1, "First Clan", "FC", 2), "invites: []\n");
        assertCorrupt(path, "", "empty file");
        assertCorrupt(path, valid.replace("schema-version: 1\n", ""), "missing schema");
        assertCorrupt(path, valid.replace("schema-version: 1", "schema-version: 2"), "unsupported schema");
        assertCorrupt(path, valid.replace(id(1).toString(), "not-a-uuid"), "invalid UUID");
        assertCorrupt(path, valid.replace("created-at: 1000", "created-at: broken"), "invalid timestamp");
        assertCorrupt(path, valid.replace("role: OWNER", "role: OFFICER"), "invalid role");
        assertCorrupt(path, valid + "schema-version: 1\n", "duplicate YAML key");
        assertCorrupt(path, valid.replace("invites: []", ""), "missing invites list");
        assertCorrupt(path, valid.replace("name: First Clan", "name: \"\""), "invalid name");
        assertCorrupt(path, valid.replace("tag: FC", "tag: \"F C\""), "invalid tag");
        assertCorrupt(path, valid.replace("schema-version: 1", "schema-version: 1\nextra: value"),
                "unexpected root key");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2)
                + clanEntry(1, "Other Clan", "OC", 3), "invites: []\n"), "duplicate clan ID");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2)
                + clanEntry(2, "first clan", "OC", 3), "invites: []\n"), "duplicate clan name");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2)
                + clanEntry(2, "Other Clan", "fc", 3), "invites: []\n"), "duplicate clan tag");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2, 2), "invites: []\n"),
                "duplicate member");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2, 4).replace("role: MEMBER", "role: OWNER")
                + clanEntry(2, "Other Clan", "OC", 3), "invites: []\n"), "multiple owners");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2)
                + clanEntry(2, "Other Clan", "OC", 2), "invites: []\n"), "cross-clan duplicate membership");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2),
                "invites:\n" + inviteEntry(1, 3) + inviteEntry(1, 3)), "duplicate invite");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2),
                "invites:\n" + inviteEntry(99, 3)), "invite unknown clan");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2),
                "invites:\n" + inviteEntry(1, 2)), "invite current member");
        assertCorrupt(path, yaml(clanEntry(1, "First Clan", "FC", 2)
                + clanEntry(2, "Other Clan", "OC", 3),
                "invites:\n" + inviteEntry(1, 3)), "invite member of other clan");
        Files.delete(path);
        Files.createDirectory(path);
        try {
            new FileClanRepository(path).initialize();
            throw new AssertionError("directory path accepted as file");
        } catch (ClanRepositoryException expected) { checks++; }
        Files.delete(path);
    }

    private static void atomicSave(Path directory) throws IOException {
        Path path = directory.resolve("atomic").resolve("clans.yml");
        ClanSnapshot initial = new ClanSnapshot(List.of(clan(1, "First Clan", "FC", owner(2))), List.of());
        ClanSnapshot changed = new ClanSnapshot(List.of(clan(1, "New Clan", "NC", owner(2))), List.of());
        AtomicInteger atomicCalls = new AtomicInteger();
        FileClanRepository normal = new FileClanRepository(path, (source, target, options) -> {
            if (List.of(options).contains(StandardCopyOption.ATOMIC_MOVE)) atomicCalls.incrementAndGet();
            Files.move(source, target, options);
        });
        normal.save(initial);
        check(atomicCalls.get() == 1 && normal.initialize().equals(initial), "atomic move success");
        AtomicInteger fallbackCalls = new AtomicInteger();
        FileClanRepository fallback = new FileClanRepository(path, (source, target, options) -> {
            fallbackCalls.incrementAndGet();
            if (List.of(options).contains(StandardCopyOption.ATOMIC_MOVE))
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "simulated");
            Files.move(source, target, options);
        });
        fallback.save(changed);
        check(fallbackCalls.get() == 2 && fallback.initialize().equals(changed), "atomic fallback replaces file");
        check(tempFiles(path).isEmpty(), "fallback cleans temporary file");
        String before = Files.readString(path, StandardCharsets.UTF_8);
        FileClanRepository failing = new FileClanRepository(path, (source, target, options) -> {
            throw new IOException("simulated I/O failure");
        });
        try {
            failing.save(initial);
            throw new AssertionError("I/O failure was swallowed");
        } catch (ClanRepositoryException expected) { checks++; }
        check(Files.readString(path, StandardCharsets.UTF_8).equals(before), "I/O failure preserves old file");
        check(tempFiles(path).isEmpty(), "I/O failure removes temporary file");
        Path invalidParent = directory.resolve("ordinary-file");
        Files.writeString(invalidParent, "not a directory", StandardCharsets.UTF_8);
        try {
            new FileClanRepository(invalidParent.resolve("clans.yml")).save(initial);
            throw new AssertionError("invalid parent accepted");
        } catch (ClanRepositoryException expected) { checks++; }
    }

    private static void assertCorrupt(Path path, String yaml, String message) throws IOException {
        Files.writeString(path, yaml, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(path);
        try {
            new FileClanRepository(path).initialize();
            throw new AssertionError(message + " was accepted");
        } catch (ClanRepositoryException expected) {
            check(java.util.Arrays.equals(before, Files.readAllBytes(path)), message + " preserved source file");
        }
    }

    private static List<Path> tempFiles(Path path) throws IOException {
        try (var files = Files.list(path.getParent())) {
            return files.filter(file -> file.getFileName().toString().startsWith(path.getFileName() + "-"))
                    .filter(file -> file.getFileName().toString().endsWith(".tmp")).toList();
        }
    }

    private static String yaml(String clans, String invites) {
        return "schema-version: 1\nclans:\n" + clans + invites;
    }

    private static String clanEntry(long clan, String name, String tag, long owner, long... members) {
        StringBuilder text = new StringBuilder();
        text.append("  - id: \"").append(id(clan)).append("\"\n")
                .append("    name: ").append(name).append("\n")
                .append("    tag: ").append(tag).append("\n")
                .append("    created-at: 1000\n")
                .append("    members:\n")
                .append("      - player: \"").append(id(owner)).append("\"\n")
                .append("        role: OWNER\n")
                .append("        joined-at: 1000\n");
        for (long member : members) text.append("      - player: \"").append(id(member)).append("\"\n")
                .append("        role: MEMBER\n")
                .append("        joined-at: 1000\n");
        return text.toString();
    }

    private static String inviteEntry(long clan, long recipient) {
        return "  - clan-id: \"" + id(clan) + "\"\n"
                + "    recipient: \"" + id(recipient) + "\"\n"
                + "    created-at: 1000\n";
    }

    private static Clan clan(long id, String name, String tag, ClanMember... members) {
        return new Clan(id(id), name, tag, NOW, List.of(members));
    }
    private static ClanMember owner(long player) { return new ClanMember(id(player), ClanRole.OWNER, NOW); }
    private static ClanMember member(long player) { return new ClanMember(id(player), ClanRole.MEMBER, NOW); }
    private static ClanInvite invite(long clan, long recipient) { return new ClanInvite(id(clan), id(recipient), NOW); }
    private static UUID id(long value) { return new UUID(0, value); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
