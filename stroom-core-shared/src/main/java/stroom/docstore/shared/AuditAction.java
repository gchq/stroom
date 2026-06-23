/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.docstore.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/**
 * The type of audit action recorded in the {@code doc_audit} table.
 * Each document lifecycle operation produces a corresponding audit entry.
 */
public enum AuditAction implements HasDisplayValue, HasPrimitiveValue {

    CREATE("Created", 1),
    UPDATE("Updated", 2),
    DELETE("Deleted", 3),
    IMPORT("Imported", 4),
    EXPORT("Exported", 5),
    COPY("Copied", 6),
    MOVE("Moved", 7),
    RENAME("Renamed", 8);

    public static final PrimitiveValueConverter<AuditAction> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AuditAction.class, AuditAction.values());

    private final String displayValue;
    private final byte primitiveValue;

    AuditAction(final String displayValue, final int primitiveValue) {
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

    /**
     * @return true if this action represents an update to an existing document
     *         (as opposed to creating a new one)
     */
    public boolean isUpdate() {
        return this == UPDATE || this == RENAME || this == MOVE;
    }

    /**
     * @return true if this action represents a creation of a new document
     */
    public boolean isCreate() {
        return this == CREATE || this == COPY;
    }
}
