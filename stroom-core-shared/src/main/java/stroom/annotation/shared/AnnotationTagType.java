package stroom.annotation.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum AnnotationTagType implements HasDisplayValue, HasPrimitiveValue {
    STATUS("Status", 0),
    LABEL("Label", 1),
    COLLECTION("Collection", 2);

    public static final PrimitiveValueConverter<AnnotationTagType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AnnotationTagType.class, AnnotationTagType.values());
    private final String displayValue;
    private final byte primitiveValue;

    AnnotationTagType(final String displayValue, final int primitiveValue) {
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
