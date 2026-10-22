/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/// How much of a category's tools a tool policy grants.
public enum AiToolAccess implements HasPrimitiveValue, HasDisplayValue {
    /// Only the tools, and the calls of a tool, that cannot change anything.
    READ_ONLY(0, "Read-only"),
    /// Every tool in the category, including those that change things.
    READ_WRITE(1, "Read and write");

    public static final PrimitiveValueConverter<AiToolAccess> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiToolAccess.class, AiToolAccess.values());

    private final byte primitiveValue;
    private final String displayValue;

    AiToolAccess(final int primitiveValue, final String displayValue) {
        this.primitiveValue = (byte) primitiveValue;
        this.displayValue = displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /// @param other Another access, or null.
    /// @return The wider of the two: read and write if either is.
    public AiToolAccess or(final AiToolAccess other) {
        return this == READ_WRITE || other == READ_WRITE
                ? READ_WRITE
                : READ_ONLY;
    }
}
