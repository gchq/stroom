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

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum SpanKind implements HasPrimitiveValue {
    SPAN_KIND_UNSPECIFIED(0),
    SPAN_KIND_INTERNAL(1),
    SPAN_KIND_SERVER(2),
    SPAN_KIND_CLIENT(3),
    SPAN_KIND_PRODUCER(4),
    SPAN_KIND_CONSUMER(5);

    public static final PrimitiveValueConverter<SpanKind> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(SpanKind.class, SpanKind.values());

    private final byte primitiveValue;

    SpanKind(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
