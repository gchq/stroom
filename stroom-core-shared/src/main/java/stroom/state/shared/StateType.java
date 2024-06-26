package stroom.state.shared;

import stroom.docref.HasDisplayValue;

public enum StateType implements HasDisplayValue {
    STATE("State"),
    TEMPORAL_STATE("Temporal State"),
    RANGED_STATE("Ranged State"),
    TEMPORAL_RANGED_STATE("Temporal Ranged State"),
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
