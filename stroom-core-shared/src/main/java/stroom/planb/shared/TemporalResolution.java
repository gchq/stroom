package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

/**
 * Determines how big a temporal bucket ought to be to store data.
 */
public enum TemporalResolution implements HasDisplayValue {
    YEAR("Year"),
    MONTH("Month"),
    DAY("Day"),
    HOUR("Hour"),
    MINUTE("Minute"),
    SECOND("Second");

    public static final List<TemporalResolution> ORDERED_LIST = List.of(
            YEAR,
            MONTH,
            DAY,
            HOUR,
            MINUTE,
            SECOND);

    private final String displayValue;

    TemporalResolution(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
