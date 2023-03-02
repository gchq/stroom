package stroom.dashboard.expression.v1;

import java.time.Duration;

public class FieldOffset {
    private final String field;
    private final Duration offset;

    private int fieldIndex;

    public FieldOffset(final String field, final Duration offset) {
        this.field = field;
        this.offset = offset;
    }

    public String getField() {
        return field;
    }

    public Duration getOffset() {
        return offset;
    }

    public void setFieldIndex(final int fieldIndex) {
        this.fieldIndex = fieldIndex;
    }

    public int getFieldIndex() {
        return fieldIndex;
    }
}
