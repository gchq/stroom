package stroom.annotation.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum AnnotationEntryType implements HasDisplayValue, HasPrimitiveValue {
    TITLE("Title", "title", 0),
    SUBJECT("Subject", "subject", 1),
    STATUS("Status", "status", 2),
    ASSIGNED("Assigned", "assigned", 3),
    COMMENT("Comment", "commented", 4),
    LINK("Link", "linked", 5),
    UNLINK("Unlink", "unlinked", 6),
    RETENTION_PERIOD("Retention Period", "retention period", 7),
    DESCRIPTION("Description", "description", 8),
    ADD_TO_COLLECTION("Add To Collection", "added to collection", 9),
    REMOVE_FROM_COLLECTION("Remove From Collection", "removed from collection", 10),
    ADD_LABEL("Add Label", "added label", 11),
    REMOVE_LABEL("Remove Label", "removed label", 12),
    ADD_TABLE_DATA("Add Rows", "added rows", 13),
    DELETE("Delete", "deleted", 99);

    public static final PrimitiveValueConverter<AnnotationEntryType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AnnotationEntryType.class, AnnotationEntryType.values());

    private final String displayValue;
    private final String actionText;
    private final byte primitiveValue;

    AnnotationEntryType(final String displayValue,
                        final String actionText,
                        final int primitiveValue) {
        this.displayValue = displayValue;
        this.actionText = actionText;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getActionText() {
        return actionText;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
