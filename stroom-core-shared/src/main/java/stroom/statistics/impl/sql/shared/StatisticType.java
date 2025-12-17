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

package stroom.statistics.impl.sql.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.io.Serializable;

/**
 * Used to distinguish the type of a statistic event. COUNT type events can be
 * summed, e.g. the number of bytes written however VALUE events cannot, e.g.
 * cpu%.
 */
public enum StatisticType implements HasDisplayValue, HasPrimitiveValue, Serializable {
    COUNT("Count", 1),
    VALUE("Value", 2);

    public static final PrimitiveValueConverter<StatisticType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(StatisticType.class, StatisticType.values());
    private final String displayValue;
    private final byte primitiveValue;

    StatisticType(final String displayValue, final int primitiveValue) {
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
