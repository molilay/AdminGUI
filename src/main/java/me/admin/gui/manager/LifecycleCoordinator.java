package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Starts and stops background components in a deterministic order with rollback on failure. */
public final class LifecycleCoordinator {

    public enum State { REGISTERED, RUNNING, DISABLED, STOPPED, FAILED }
    public record ComponentState(String name, State state, String error) {}
    private static final class Component {
        private final String name;
        private final Runnable start;
        private final Runnable stop;
        private final java.util.function.BooleanSupplier enabled;
        private State state = State.REGISTERED;
        private String error = "";

        private Component(String name, java.util.function.BooleanSupplier enabled, Runnable start, Runnable stop) {
            this.name = name;
            this.enabled = enabled;
            this.start = start;
            this.stop = stop;
        }
    }

    private final AdvancedModeratorGUI plugin;
    private final Map<String, Component> components = new LinkedHashMap<>();

    public LifecycleCoordinator(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public synchronized void register(String name, Runnable start, Runnable stop) {
        registerConditional(name, () -> true, start, stop);
    }

    public synchronized void registerConditional(String name, java.util.function.BooleanSupplier enabled,
                                                 Runnable start, Runnable stop) {
        if (components.containsKey(name)) throw new IllegalArgumentException("Duplicate lifecycle component: " + name);
        components.put(name, new Component(name, enabled, start, stop));
    }

    public synchronized void startAll() {
        for (Component component : components.values()) {
            if (!component.enabled.getAsBoolean()) {
                component.state = State.DISABLED;
                component.error = "";
                continue;
            }
            try {
                component.start.run();
                component.state = State.RUNNING;
                component.error = "";
            } catch (RuntimeException error) {
                component.state = State.FAILED;
                component.error = safeMessage(error);
                plugin.getLogger().severe("Failed to start component " + component.name + ": " + component.error);
                stopRunningReverse();
                throw error;
            }
        }
    }

    public synchronized boolean restart(String name) {
        Component component = components.get(name);
        if (component == null) return false;
        stop(component);
        if (!component.enabled.getAsBoolean()) {
            component.state = State.DISABLED;
            component.error = "";
            return true;
        }
        try {
            component.start.run();
            component.state = State.RUNNING;
            component.error = "";
            return true;
        } catch (RuntimeException error) {
            component.state = State.FAILED;
            component.error = safeMessage(error);
            plugin.getLogger().severe("Failed to restart component " + name + ": " + component.error);
            return false;
        }
    }

    public synchronized void stopAll() { stopRunningReverse(); }

    public synchronized List<ComponentState> snapshot() {
        return components.values().stream()
                .map(value -> new ComponentState(value.name, value.state, value.error)).toList();
    }

    private void stopRunningReverse() {
        List<Component> reverse = new ArrayList<>(components.values());
        Collections.reverse(reverse);
        reverse.forEach(this::stop);
    }

    private void stop(Component component) {
        if (component.state != State.RUNNING) return;
        try {
            component.stop.run();
            component.state = State.STOPPED;
        } catch (RuntimeException error) {
            component.state = State.FAILED;
            component.error = safeMessage(error);
            plugin.getLogger().warning("Failed to stop component " + component.name + ": " + component.error);
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
