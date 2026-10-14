package dev.vapee.core.utility.teleport;

/** Stable rejection reason plus literal offending value; diagnostics stay English and Bukkit-free. */
public final class TeleportParseException extends IllegalArgumentException {
    public enum Reason { USAGE, MIXED_COORDINATES, NONFINITE_DESTINATION, NONFINITE_ROTATION, INVALID_COORDINATE, INVALID_ROTATION }
    private final Reason reason;
    private final String value;
    TeleportParseException(Reason reason, String diagnostic, String value) {
        super(diagnostic); this.reason = reason; this.value = value;
    }
    public Reason reason() { return reason; }
    public String value() { return value; }
}
