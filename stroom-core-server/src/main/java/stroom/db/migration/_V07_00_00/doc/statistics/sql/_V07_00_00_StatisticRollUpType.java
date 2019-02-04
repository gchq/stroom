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

package stroom.db.migration._V07_00_00.doc.statistics.sql;

import stroom.db.migration._V07_00_00.docref._V07_00_00_HasDisplayValue;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_HasPrimitiveValue;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_PrimitiveValueConverter;

import java.io.Serializable;

public enum _V07_00_00_StatisticRollUpType implements _V07_00_00_HasDisplayValue, _V07_00_00_HasPrimitiveValue, Serializable {
    NONE("None", 1),
    ALL("All", 2),
    CUSTOM("Custom", 3);

    public static final _V07_00_00_PrimitiveValueConverter<_V07_00_00_StatisticRollUpType> PRIMITIVE_VALUE_CONVERTER
            = new _V07_00_00_PrimitiveValueConverter<>(_V07_00_00_StatisticRollUpType.values());
    private final String displayValue;
    private final byte primitiveValue;

    _V07_00_00_StatisticRollUpType(final String displayValue, final int primitiveValue) {
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
