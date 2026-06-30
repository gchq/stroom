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

package stroom.meta.impl.db;

import stroom.util.shared.HasPrimitiveValue;

enum MetaType implements HasPrimitiveValue {
    FIELD(1, false),
    NUMERIC_FIELD(2, true),
    DATE_FIELD(3, false),
    ID(4, true),
    COUNT_IN_DURATION_FIELD(5, true),
    SIZE_FIELD(6, true),
    DURATION_FIELD(7, true);

    private final byte primitiveValue;
    private final boolean numeric;

    MetaType(final int primitiveValue, final boolean numeric) {
        this.primitiveValue = (byte) primitiveValue;
        this.numeric = numeric;
    }

    public boolean isNumeric() {
        return numeric;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
