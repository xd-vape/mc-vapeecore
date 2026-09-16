package dev.vapee.core.activity.navigation;

import dev.vapee.core.activity.ActivityService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ActivityCatalog {

    private final ActivityService activityService;
    private final Map<String, ActivityEntryPoint> entries = new LinkedHashMap<>();

    public ActivityCatalog(ActivityService activityService) {
        this.activityService = Objects.requireNonNull(activityService, "activityService");
    }

    public void register(ActivityEntryPoint entryPoint) {
        ActivityEntryPoint validatedEntryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
        String key = lookupKey(validatedEntryPoint.getActivityKey());
        if (activityService.getActivityType(key).isEmpty()) {
            throw new IllegalArgumentException("Activity type '" + key + "' must be registered first");
        }
        if (entries.putIfAbsent(key, validatedEntryPoint) != null) {
            throw new IllegalArgumentException("An activity entry point for '" + key + "' is already registered");
        }
    }

    public Optional<ActivityEntryPoint> unregister(String activityKey) {
        return Optional.ofNullable(entries.remove(lookupKey(activityKey)));
    }

    public Optional<ActivityEntryPoint> get(String activityKey) {
        return Optional.ofNullable(entries.get(lookupKey(activityKey)));
    }

    public List<ActivityEntryPoint> getEntries() {
        return List.copyOf(entries.values());
    }

    public void clear() {
        entries.clear();
    }

    private static String lookupKey(String key) {
        String value = Objects.requireNonNull(key, "activityKey").trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("activityKey must not be blank");
        }
        return value;
    }
}
