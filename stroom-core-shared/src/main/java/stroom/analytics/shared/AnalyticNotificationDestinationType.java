package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

public enum AnalyticNotificationDestinationType implements HasDisplayValue {
    STREAM("Stream"),
    EMAIL("Email");

    private final String displayValue;

    AnalyticNotificationDestinationType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
