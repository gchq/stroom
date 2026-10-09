package stroom.pipeline.xsltfunctions;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonValue;

public enum XsltDataType implements HasDisplayValue {
    BOOLEAN("Boolean"),
    DATE("Date"),
    DATE_TIME("Date-Time"),
    DECIMAL("Decimal"),
    /**
     * A sequence with nothing in it.
     */
    EMPTY_SEQUENCE("Empty Sequence"),
    INTEGER("Integer"),
    /**
     * Any sequence of nodes or atomic values, e.g. a single node,
     * a list of nodes or a list of strings.
     */
    SEQUENCE("Sequence"),
    STRING("String"),
    ;

    private final String displayValue;

    XsltDataType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
