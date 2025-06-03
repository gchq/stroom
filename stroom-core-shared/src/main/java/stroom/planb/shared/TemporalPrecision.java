package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum TemporalPrecision implements HasDisplayValue {
    DAY("Day"),
    HOUR("Hour"),
    MINUTE("Minute"),
    SECOND("Second"),
    MILLISECOND("Millisecond"),
    NANOSECOND("Nanosecond");

    public static final List<TemporalPrecision> ORDERED_LIST = List.of(
            DAY,
            HOUR,
            MINUTE,
            SECOND,
            MILLISECOND,
            NANOSECOND);

    private final String displayValue;

    TemporalPrecision(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
