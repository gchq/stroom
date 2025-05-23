package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

public enum StateType implements HasDisplayValue {
    STATE("State"),
    TEMPORAL_STATE("Temporal State"),
    RANGE_STATE("Range State"),
    TEMPORAL_RANGE_STATE("Temporal Range State"),
    SESSION("Session");

    private final String displayValue;

    StateType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
