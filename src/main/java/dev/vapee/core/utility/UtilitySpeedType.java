package dev.vapee.core.utility;

public enum UtilitySpeedType {
    WALK("Walk"),
    FLY("Flight");

    private final String displayName;

    UtilitySpeedType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
