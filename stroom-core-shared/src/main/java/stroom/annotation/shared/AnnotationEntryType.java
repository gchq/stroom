/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    LINK_EVENT("Link Event", "linked event", 5),
    UNLINK_EVENT("Unlink Event", "unlinked event", 6),
    RETENTION_PERIOD("Retention Period", "retention period", 7),
    DESCRIPTION("Description", "description", 8),
    ADD_TO_COLLECTION("Add To Collection", "added to collection", 9),
    REMOVE_FROM_COLLECTION("Remove From Collection", "removed from collection", 10),
    ADD_LABEL("Add Label", "added label", 11),
    REMOVE_LABEL("Remove Label", "removed label", 12),
    ADD_TABLE_DATA("Add Rows", "added rows", 13),
    LINK_ANNOTATION("Link Annotation", "linked annotation", 14),
    UNLINK_ANNOTATION("Unlink Annotation", "unlinked annotation", 15),
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
