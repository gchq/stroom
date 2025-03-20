package stroom.annotation.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum AnnotationEntryType implements HasDisplayValue, HasPrimitiveValue {
    TITLE("Title", 0),
    SUBJECT("Subject", 1),
    STATUS("Status", 2),
    ASSIGNED("Assigned", 3),
    COMMENT("Comment", 4),
    LINK("Link", 5),
    UNLINK("Unlink", 6),
    RETENTION_PERIOD("Retention Period", 7),
    DESCRIPTION("Description", 8),
    ADD_TO_COLLECTION("Add to collection", 9),
    REMOVE_FROM_COLLECTION("Remove from collection", 10),
    ADD_LABEL("Add label", 11),
    REMOVE_LABEL("Remove label", 12),
    DELETE("Delete", 99);

    public static final PrimitiveValueConverter<AnnotationEntryType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AnnotationEntryType.class, AnnotationEntryType.values());

    private final String displayValue;
    private final byte primitiveValue;

    AnnotationEntryType(final String displayValue, final int primitiveValue) {
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
