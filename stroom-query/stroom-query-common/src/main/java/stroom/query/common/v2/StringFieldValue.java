package stroom.query.common.v2;

public class StringFieldValue {
    private final String fieldName;
    private final String fieldValue;

    public StringFieldValue(final String fieldName,
                            final String fieldValue) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }
}
