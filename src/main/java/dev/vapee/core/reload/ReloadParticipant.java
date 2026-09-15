package dev.vapee.core.reload;

public interface ReloadParticipant {

    String getReloadName();

    ReloadPlan prepareReload();
}
