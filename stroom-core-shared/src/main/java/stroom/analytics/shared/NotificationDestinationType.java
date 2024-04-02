package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

public enum NotificationDestinationType implements HasDisplayValue {
    STREAM("Stream"),
    EMAIL("Email");

    private final String displayValue;

    NotificationDestinationType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
