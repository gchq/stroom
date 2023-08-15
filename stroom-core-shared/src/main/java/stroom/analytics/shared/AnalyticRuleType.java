package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

public enum AnalyticRuleType implements HasDisplayValue {
    STREAMING("Streaming"),
    TABLE_CREATION("Table Creation"),
    INDEX_QUERY("Index Query");

    private final String displayValue;

    AnalyticRuleType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
