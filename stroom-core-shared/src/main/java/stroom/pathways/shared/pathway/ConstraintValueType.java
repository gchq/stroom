package stroom.pathways.shared.pathway;

import stroom.docref.HasDisplayValue;

public enum ConstraintValueType implements HasDisplayValue {
    ANY("Any"),
    DURATION_VALUE("Duration"),
    DURATION_RANGE("Duration Range"),
    STRING("String"),
    STRING_SET("String Set"),
    REGEX("Regex"),
    BOOLEAN("Boolean"),
    ANY_BOOLEAN("Any Boolean"),
    INTEGER("Integer"),
    INTEGER_SET("Integer Set"),
    INTEGER_RANGE("Integer Range"),
    DOUBLE("Double"),
    DOUBLE_SET("Double Set"),
    DOUBLE_RANGE("Double Range");

    private final String displayValue;

    ConstraintValueType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
