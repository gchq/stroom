package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

public enum StateType implements HasDisplayValue {
    STATE("State"),
    TEMPORAL_STATE("Temporal State"),
    RANGED_STATE("Range State"),
    TEMPORAL_RANGED_STATE("Temporal Range State"),
    SESSION("Session"),
    HISTOGRAM("Histogram"),
    METRIC("Metric"),
    TRACE("Trace");

    private final String displayValue;

    StateType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
