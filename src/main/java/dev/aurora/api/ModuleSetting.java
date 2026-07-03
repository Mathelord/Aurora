package dev.aurora.api;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class ModuleSetting {
    private final String id;
    private final String displayName;
    private final Kind kind;
    private final List<String> options;
    private final double min;
    private final double max;
    private final double step;
    private final double defaultValue;
    private double value;
    private String description = "";
    private BooleanSupplier visibility = () -> true;

    public ModuleSetting(String id, String displayName, double value, double min, double max, double step) {
        this(id, displayName, Kind.NUMBER, List.of(), value, min, max, step);
    }

    public ModuleSetting(String id, String displayName, Kind kind, List<String> options, double value, double min, double max, double step) {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
        this.options = List.copyOf(options);
        this.min = min;
        this.max = max;
        this.step = step;
        setValue(value);
        this.defaultValue = this.value;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Kind kind() {
        return kind;
    }

    public List<String> options() {
        return options;
    }

    public double value() {
        return value;
    }

    public double defaultValue() {
        return defaultValue;
    }

    public void setValue(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        this.value = Math.max(min, Math.min(max, value));
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public String description() {
        return description;
    }

    public ModuleSetting description(String description) {
        this.description = description == null ? "" : description.strip();
        return this;
    }

    public boolean visible() {
        return visibility.getAsBoolean();
    }

    public ModuleSetting visibleWhen(BooleanSupplier visibility) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        return this;
    }

    public enum Kind {
        NUMBER,
        BOOLEAN,
        OPTION,
        COLOR
    }
}
