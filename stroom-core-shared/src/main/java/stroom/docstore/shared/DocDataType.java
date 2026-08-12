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

package stroom.docstore.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/**
 * The type of data stored in a {@code doc_data} or {@code doc_data_snapshot} row.
 * Determines which sparse column ({@code json_data}, {@code text_data}, or {@code bin_data})
 * is populated. The serialiser sets this on each {@code ImportExportAsset} it produces.
 */
public enum DocDataType implements HasDisplayValue, HasPrimitiveValue {

    JSON("JSON", 1),
    TEXT("Text", 2),
    BINARY("Binary", 3);

    public static final PrimitiveValueConverter<DocDataType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(DocDataType.class, DocDataType.values());

    private final String displayValue;
    private final byte primitiveValue;

    DocDataType(final String displayValue, final int primitiveValue) {
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
