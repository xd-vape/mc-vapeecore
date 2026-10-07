package dev.vapee.core.config;

import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** Real bundled schema and production disk evolution, including the operator's manually added friends case. */
public final class LobbyConfigUpgradeHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("vapeecore-lobby-upgrade-");
        try {
            Logger logger=Logger.getAnonymousLogger();logger.setUseParentHandlers(false);
            var evolution=new ConfigEvolution(root,logger,name -> LobbyConfigUpgradeHarness.class.getResourceAsStream("/"+name));
            check(ConfigEvolution.MANAGED_CONFIGS.get("lobby.yml")==2,"explicit lobby schema version raised to 2");
            check(ConfigEvolution.MANAGED_CONFIGS.entrySet().stream().filter(e -> !e.getKey().equals("lobby.yml")).allMatch(e -> e.getValue()==1),"other five schemas remain 1");
            var file=root.resolve("lobby.yml");
            String original="""
                    # Custom phase36 lobby
                    config-version: 1
                    items:
                      navigator:
                        slot: 6
                        material: CLOCK # keep my clock
                    operator-extra: [unchanged]
                    """;
            Files.writeString(file,original);
            check(evolution.evolve("lobby.yml"),"v1 installation really evolves");
            var yaml=new Yaml();Map<?,?> actual=yaml.load(Files.readString(file));
            check(actual.get("config-version").equals(2),"live schema becomes 2");
            var items=(Map<?,?>)actual.get("items");var navigator=(Map<?,?>)items.get("navigator");var friends=(Map<?,?>)items.get("friends");
            check(navigator.get("slot").equals(6)&&navigator.get("material").equals("CLOCK"),"existing navigator slot6/CLOCK preserved");
            check(friends.get("slot").equals(1)&&friends.get("material").equals("PLAYER_HEAD")&&friends.get("head-owner").equals("self"),"missing friends presentation added");
            check(actual.get("operator-extra").equals(List.of("unchanged"))&&Files.readString(file).contains("keep my clock"),"unknown list and inline comment preserved");
            var backups=backups(root);check(backups.size()==1&&Arrays.equals(Files.readAllBytes(backups.getFirst()),original.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"exact v1 backup exists");
            byte[] first=Files.readAllBytes(file);check(!evolution.evolve("lobby.yml")&&Arrays.equals(first,Files.readAllBytes(file))&&backups(root).size()==1,"v2 second startup identical bytes and no new backup");
            Files.writeString(file,"config-version: 1\nitems:\n  friends:\n    slot: 7\n    name: '<aqua>Operator Friends'\n");
            check(evolution.evolve("lobby.yml"),"manual phase36 friends YAML also migrates");
            actual=yaml.load(Files.readString(file));friends=(Map<?,?>)((Map<?,?>)actual.get("items")).get("friends");
            check(friends.get("slot").equals(7)&&friends.get("name").equals("<aqua>Operator Friends")&&friends.get("material").equals("PLAYER_HEAD"),"manual friends values retained and missing presentation added");
            Path fresh=Files.createDirectory(root.resolve("fresh"));
            try(var resource=LobbyConfigUpgradeHarness.class.getResourceAsStream("/lobby.yml")){Files.copy(resource,fresh.resolve("lobby.yml"));}
            byte[] freshBytes=Files.readAllBytes(fresh.resolve("lobby.yml"));
            var freshEvolution=new ConfigEvolution(fresh,logger,name -> LobbyConfigUpgradeHarness.class.getResourceAsStream("/"+name));
            check(!freshEvolution.evolve("lobby.yml")&&backups(fresh).isEmpty()&&Arrays.equals(freshBytes,Files.readAllBytes(fresh.resolve("lobby.yml"))),"fresh bundled v2 gets no migration backup/rewrite");
            var freshYaml=(Map<?,?>)yaml.load(new String(freshBytes,java.nio.charset.StandardCharsets.UTF_8));
            check(freshYaml.get("config-version").equals(2)&&((Map<?,?>)freshYaml.get("items")).containsKey("friends"),"fresh actual resource is v2 with friends");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("LobbyConfigUpgradeHarness passed "+checks+" checks.");
    }
    private static List<Path> backups(Path root)throws Exception{Path p=root.resolve("backups/config");if(!Files.exists(p))return List.of();try(var files=Files.list(p)){return files.sorted().toList();}}
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
}
