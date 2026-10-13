package dev.vapee.core.i18n;

import java.util.Locale;

public final class LanguageResolver {
    private LanguageResolver() { }
    public static Language resolve(Locale locale) {
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage()) ? Language.GERMAN : Language.ENGLISH;
    }
}
