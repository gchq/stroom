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

package stroom.data.store.impl.fs.s3v2;


import stroom.docref.HasDisplayValue;
import stroom.util.shared.CaseInsensitiveDisplayValueConverter;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.Objects;

public enum ZstdDictionaryStatus implements HasDisplayValue, HasPrimitiveValue {

    /**
     * This dictionary is currently being trained so is not available for use.
     */
    IN_TRAINING("In Training", 0),
    /**
     * This dictionary has been trained and is available for use.
     */
    TRAINED("Trained", 1),
    /**
     * This dictionary has been deprecated so is not available for use.
     */
    DEPRECATED("Deprecated", 2),
    ;

    public static final PrimitiveValueConverter<ZstdDictionaryStatus> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(ZstdDictionaryStatus.class);
    public static final CaseInsensitiveDisplayValueConverter<ZstdDictionaryStatus> DISPLAY_VALUE_CONVERTER =
            CaseInsensitiveDisplayValueConverter.create(ZstdDictionaryStatus.class);

    private final String displayValue;
    private final byte primitiveValue;

    ZstdDictionaryStatus(final String displayValue, final int primitiveValue) {
        this.displayValue = Objects.requireNonNull(displayValue);
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
