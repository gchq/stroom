package stroom.alert.rule.shared;

import stroom.docref.HasDisplayValue;

public enum AlertRuleType implements HasDisplayValue {
    EVENT("Event"),
    THRESHOLD("Threshold");

    private final String displayValue;

    AlertRuleType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
