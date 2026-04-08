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

public enum AnnotationTagType implements HasDisplayValue, HasPrimitiveValue {
    STATUS("Status", 0, false),
    LABEL("Label", 1, false),
    COLLECTION("Collection", 2, false),
    COMMENT("Comment", 3, true);

    public static final PrimitiveValueConverter<AnnotationTagType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AnnotationTagType.class, AnnotationTagType.values());
    private final String displayValue;
    private final byte primitiveValue;
    private final boolean hasTagText;

    AnnotationTagType(final String displayValue, final int primitiveValue, final boolean hasTagText) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
        this.hasTagText = hasTagText;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    public boolean hasTagText() {
        return hasTagText;
    }
}
