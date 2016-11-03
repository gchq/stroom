/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.shared;

import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.util.shared.HasDisplayValue;

public enum StreamAttributeFieldUse implements HasDisplayValue,HasPrimitiveValue {
    FIELD("Text", 1, false), NUMERIC_FIELD("Number", 2, true), DATE_FIELD("Date", 3, false), ID("Id", 4,
            true), COUNT_IN_DURATION_FIELD("Count in Duration", 5, true), SIZE_FIELD("Size", 6,
                    true), DURATION_FIELD("Duration", 7, true);

    private final String displayValue;
    private final byte primitiveValue;
    private final boolean numeric;

    public static final PrimitiveValueConverter<StreamAttributeFieldUse> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<StreamAttributeFieldUse>(
            StreamAttributeFieldUse.values());

    StreamAttributeFieldUse(final String displayValue, final int primitiveValue, final boolean numeric) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
        this.numeric = numeric;
    }

    public boolean isNumeric() {
        return numeric;
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
