package dev.vapee.core.i18n;

public enum Language {
    ENGLISH("en"), GERMAN("de");
    private final String code;
    Language(String code) { this.code = code; }
    public String code() { return code; }
}
