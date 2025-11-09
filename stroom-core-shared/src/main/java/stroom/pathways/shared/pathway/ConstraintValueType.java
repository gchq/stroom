package stroom.pathways.shared.pathway;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum ConstraintValueType implements HasDisplayValue, HasPrimitiveValue {
    ANY("Any", 0),
    DURATION_VALUE("Duration", 1),
    DURATION_RANGE("Duration Range", 2),
    STRING("String", 3),
    STRING_SET("String Set", 4),
    REGEX("Regex", 5),
    BOOLEAN("Boolean", 6),
    ANY_BOOLEAN("Any Boolean", 7),
    INTEGER("Integer", 8),
    INTEGER_SET("Integer Set", 9),
    INTEGER_RANGE("Integer Range", 10),
    DOUBLE("Double", 11),
    DOUBLE_SET("Double Set", 12),
    DOUBLE_RANGE("Double Range", 13);

    public static final PrimitiveValueConverter<ConstraintValueType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(ConstraintValueType.class, ConstraintValueType.values());
    private final String displayValue;
    private final byte primitiveValue;

    ConstraintValueType(final String displayValue, final int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
