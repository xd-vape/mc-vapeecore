package dev.vapee.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ModuleManager {

    private final Logger logger;
    private final List<CoreModule> modules = new ArrayList<>();
    private final List<CoreModule> enabledModules = new ArrayList<>();
    private boolean enabled;

    public ModuleManager(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register(CoreModule module) {
        Objects.requireNonNull(module, "module");

        if (enabled) {
            throw new IllegalStateException("Modules cannot be registered after startup");
        }

        String moduleName = requireModuleName(module);
        boolean duplicateName = modules.stream()
                .map(this::requireModuleName)
                .anyMatch(moduleName::equalsIgnoreCase);

        if (duplicateName) {
            throw new IllegalArgumentException("A module named '" + moduleName + "' is already registered");
        }

        modules.add(module);
    }

    public void enableAll() {
        if (enabled) {
            throw new IllegalStateException("Modules are already enabled");
        }

        logger.info("Enabling " + modules.size() + " module(s).");

        try {
            for (CoreModule module : modules) {
                String moduleName = requireModuleName(module);
                logger.info("Enabling module " + moduleName + ".");
                module.enable();
                enabledModules.add(module);
            }
            enabled = true;
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Module startup failed; rolling back enabled modules.", exception);
            disableEnabledModules("rollback");
            throw exception;
        }

        logger.info("Enabled " + enabledModules.size() + " module(s).");
    }

    public void disableAll() {
        logger.info("Disabling " + enabledModules.size() + " module(s).");
        disableEnabledModules("shutdown");
        enabled = false;
        logger.info("Disabled all modules.");
    }

    public List<CoreModule> getModules() {
        return List.copyOf(modules);
    }

    public List<CoreModule> getEnabledModules() {
        return List.copyOf(enabledModules);
    }

    private void disableEnabledModules(String phase) {
        for (int index = enabledModules.size() - 1; index >= 0; index--) {
            CoreModule module = enabledModules.get(index);
            String moduleName = requireModuleName(module);

            try {
                logger.info("Disabling module " + moduleName + ".");
                module.disable();
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Could not disable module " + moduleName + " during " + phase + ".", exception);
            }
        }

        enabledModules.clear();
    }

    private String requireModuleName(CoreModule module) {
        String moduleName = Objects.requireNonNull(module.getName(), "module name").trim();
        if (moduleName.isEmpty()) {
            throw new IllegalArgumentException("Module names must not be blank");
        }
        return moduleName;
    }
}
