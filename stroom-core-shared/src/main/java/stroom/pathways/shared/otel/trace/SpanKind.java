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

package stroom.pathways.shared.otel.trace;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum SpanKind implements HasPrimitiveValue, HasDisplayValue {
    SPAN_KIND_UNSPECIFIED(0, "UNSPECIFIED"),
    SPAN_KIND_INTERNAL(1, "INTERNAL"),
    SPAN_KIND_SERVER(2, "SERVER"),
    SPAN_KIND_CLIENT(3, "CLIENT"),
    SPAN_KIND_PRODUCER(4, "PRODUCER"),
    SPAN_KIND_CONSUMER(5, "CONSUMER");

    public static final PrimitiveValueConverter<SpanKind> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(SpanKind.class, SpanKind.values());
    public static final Map<String, SpanKind> DISPLAY_VALUE_CONVERTER =
            new HashMap<>();

    static {
        for (final SpanKind value : SpanKind.values()) {
            DISPLAY_VALUE_CONVERTER.put(value.name().toLowerCase(Locale.ROOT), value);
            DISPLAY_VALUE_CONVERTER.put(value.getDisplayValue().toLowerCase(Locale.ROOT), value);
        }
    }

    private final byte primitiveValue;
    private final String displayValue;

    SpanKind(final int primitiveValue, final String displayValue) {
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

    public static SpanKind fromString(final String string) {
        if (string == null) {
            return null;
        }
        return DISPLAY_VALUE_CONVERTER.get(string.toLowerCase(Locale.ROOT));
    }
}
