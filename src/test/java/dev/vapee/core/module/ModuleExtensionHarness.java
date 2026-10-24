package dev.vapee.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Synthetic composition proof; real Friend lifecycle is tested independently. */
public final class ModuleExtensionHarness {
    private static int checks;

    public static void main(String[] args) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var manager = new ModuleManager(logger);
        var events = new ArrayList<String>();
        var resources = new AtomicInteger();
        var dependency = new Extension("Dependency", null, events, resources);
        var extension = new Extension("TEST_EXTENSION_MODULE", dependency, events, resources);
        var later = new Extension("Later", extension, events, resources);
        manager.register(dependency);
        manager.register(extension);
        manager.register(later);
        check(
                manager.getModules().equals(List.of(dependency, extension, later)),
                "explicit production manager registration order");
        try {
            manager.register(new Extension("test_extension_module", null, events, resources));
            throw new AssertionError("duplicate name accepted");
        } catch (IllegalArgumentException expected) {
            check(true, "duplicate module name rejected case-insensitively");
        }
        later.failEnable = true;
        RuntimeException original = later.startupFailure;
        try {
            manager.enableAll();
            throw new AssertionError("later startup failure missing");
        } catch (RuntimeException actual) {
            check(actual == original, "manager retains failing module exception");
        }
        check(
                events.equals(
                        List.of(
                                "+Dependency",
                                "+TEST_EXTENSION_MODULE",
                                "+Later",
                                "-Later",
                                "-TEST_EXTENSION_MODULE",
                                "-Dependency")),
                "failed module owns partial cleanup; successful predecessors roll back in reverse order");
        check(
                resources.get() == 0 && manager.getEnabledModules().isEmpty(),
                "failed startup leaves no synthetic registrations");
        check(
                dependency.runtime == null && extension.runtime == null && later.runtime == null,
                "no failed runtime is published");
        later.failEnable = false;
        events.clear();
        manager.enableAll();
        Object previous = extension.runtime;
        check(
                previous != null && resources.get() == 3 && manager.getEnabledModules().size() == 3,
                "retry creates usable runtime only after dependencies are enabled");
        extension.failDisable = true;
        manager.disableAll();
        check(
                events.equals(
                        List.of(
                                "+Dependency",
                                "+TEST_EXTENSION_MODULE",
                                "+Later",
                                "-Later",
                                "-TEST_EXTENSION_MODULE",
                                "-Dependency")),
                "one shutdown failure cannot skip remaining predecessor");
        check(
                resources.get() == 0 && manager.getEnabledModules().isEmpty(),
                "all successful modules receive cleanup despite failure");
        extension.failDisable = false;
        manager.enableAll();
        check(
                extension.runtime != previous && extension.dependency.runtime != null,
                "re-enable obtains fresh dependency-bound runtime");
        manager.disableAll();
        int eventCount = events.size();
        manager.disableAll();
        check(
                resources.get() == 0 && events.size() == eventCount,
                "repeated shutdown has no resource effects");
        System.out.println("ModuleExtensionHarness passed " + checks + " checks.");
    }

    private static final class Extension implements CoreModule {
        final String name;
        final Extension dependency;
        final List<String> events;
        final AtomicInteger resources;
        final RuntimeException startupFailure =
                new IllegalStateException("controlled later module failure");
        Object runtime;
        boolean ownsRegistration;
        boolean failEnable;
        boolean failDisable;

        Extension(String name, Extension dependency, List<String> events, AtomicInteger resources) {
            this.name = name;
            this.dependency = dependency;
            this.events = events;
            this.resources = resources;
        }

        public String getName() {
            return name;
        }

        public void enable() {
            if (dependency != null && dependency.runtime == null)
                throw new IllegalStateException("dependency unavailable");
            events.add("+" + name);
            try {
                resources.incrementAndGet();
                ownsRegistration = true;
                if (failEnable) throw startupFailure;
                runtime = new Object();
            } catch (RuntimeException failure) {
                disable();
                throw failure;
            }
        }

        public void disable() {
            events.add("-" + name);
            if (ownsRegistration) resources.decrementAndGet();
            ownsRegistration = false;
            runtime = null;
            if (failDisable)
                throw new IllegalStateException("controlled cleanup failure after release");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
