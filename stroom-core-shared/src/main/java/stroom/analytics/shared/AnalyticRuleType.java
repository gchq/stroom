package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

public enum AnalyticRuleType implements HasDisplayValue {
    EVENT("Event"),
    AGGREGATE("Aggregate");

    private final String displayValue;

    AnalyticRuleType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
