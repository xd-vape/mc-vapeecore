package dev.vapee.core.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class QuestDefinitionRegistry {

    private Map<String, QuestDefinition> definitions = Map.of();

    public Optional<QuestDefinition> findById(String questId) {
        return Optional.ofNullable(definitions.get(QuestDefinition.requireValidId(questId)));
    }

    public List<QuestDefinition> snapshot() {
        return List.copyOf(definitions.values());
    }

    public Map<String, QuestDefinition> snapshotById() {
        return definitions;
    }

    public int size() {
        return definitions.size();
    }

    public void replaceAll(Collection<QuestDefinition> newDefinitions) {
        Objects.requireNonNull(newDefinitions, "definitions");
        TreeMap<String, QuestDefinition> validated = new TreeMap<>();
        for (QuestDefinition definition : newDefinitions) {
            QuestDefinition validatedDefinition = Objects.requireNonNull(definition, "definition");
            QuestDefinition duplicate = validated.putIfAbsent(
                    validatedDefinition.id(),
                    validatedDefinition
            );
            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "Duplicate quest definition ID: " + validatedDefinition.id()
                );
            }
        }

        LinkedHashMap<String, QuestDefinition> deterministic = new LinkedHashMap<>(validated);
        definitions = Collections.unmodifiableMap(deterministic);
    }
}
