package stroom.util.shared.scheduler;

import stroom.docref.HasDisplayValue;

public enum ScheduleType implements HasDisplayValue {
    INSTANT("Instant"),
    CRON("Cron"),
    FREQUENCY("Frequency");
    private final String displayValue;

    ScheduleType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
