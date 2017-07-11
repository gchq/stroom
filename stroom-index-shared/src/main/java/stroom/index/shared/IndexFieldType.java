/*
 * Copyright 2017 Crown Copyright
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

package stroom.index.shared;

import stroom.util.shared.HasDisplayValue;

public enum IndexFieldType implements HasDisplayValue {
    FIELD("Text", 1, false), NUMERIC_FIELD("Number", 2, true), DATE_FIELD("Date", 3, false), ID("Id", 4, true);

    private final String displayValue;
    private final byte primitiveValue;
    private final boolean numeric;

    IndexFieldType(final String displayValue, final int primitiveValue, final boolean numeric) {
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
}
