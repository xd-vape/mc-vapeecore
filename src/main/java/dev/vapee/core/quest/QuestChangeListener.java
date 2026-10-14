package dev.vapee.core.quest;

/** Main-thread notification after successful persistence; observer failures cannot undo the commit. */
@FunctionalInterface
public interface QuestChangeListener {
    void onQuestsCommitted(java.util.UUID owner);
}
