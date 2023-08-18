package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

public enum AnalyticProcessType implements HasDisplayValue {
    STREAMING("Streaming"),
    TABLE_BUILDER("Table Builder"),
    SCHEDULED_QUERY("Scheduled Query");

    private final String displayValue;

    AnalyticProcessType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
