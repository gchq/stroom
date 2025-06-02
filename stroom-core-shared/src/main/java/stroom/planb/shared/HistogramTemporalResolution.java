package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum HistogramTemporalResolution implements HasDisplayValue {
    YEAR("Year"),
    MONTH("Month"),
    DAY("Day"),
    HOUR("Hour"),
    MINUTE("Minute"),
    SECOND("Second");

    public static final List<HistogramTemporalResolution> ORDERED_LIST = List.of(
            YEAR,
            MONTH,
            DAY,
            HOUR,
            MINUTE,
            SECOND);

    private final String displayValue;

    HistogramTemporalResolution(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
