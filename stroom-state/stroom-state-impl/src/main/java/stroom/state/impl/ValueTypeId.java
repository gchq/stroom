package stroom.state.impl;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum ValueTypeId implements HasDisplayValue, HasPrimitiveValue {
    UNKNOWN("Unknown", -1),
    STRING("String", 0),
    FAST_INFOSET("Fast Infoset", 1),
    NULL("Null", 3); // Represents an entry with a null/empty/blank value.

    public static final PrimitiveValueConverter<ValueTypeId> PRIMITIVE_VALUE_CONVERTER =
            new PrimitiveValueConverter<>(ValueTypeId.values());

    private final String displayValue;
    private final byte primitiveValue;

    ValueTypeId(final String displayValue, final int primitiveValue) {
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
