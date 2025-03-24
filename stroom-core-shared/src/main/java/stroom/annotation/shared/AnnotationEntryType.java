package stroom.annotation.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.HashSet;
import java.util.Set;

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
    DELETE("Delete", "deleted", 99);

    public static final Set<AnnotationEntryType> NON_REPLACING = new HashSet<>();

    static {
        NON_REPLACING.add(LINK);
        NON_REPLACING.add(UNLINK);
        NON_REPLACING.add(ADD_TO_COLLECTION);
        NON_REPLACING.add(REMOVE_FROM_COLLECTION);
        NON_REPLACING.add(ADD_LABEL);
        NON_REPLACING.add(REMOVE_LABEL);
        NON_REPLACING.add(DELETE);
    }

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
