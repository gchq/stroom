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

package stroom.index.shared;

import stroom.docref.HasDisplayValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Deprecated
public enum OldIndexFieldType implements HasDisplayValue {
    ID("Id", true),
    BOOLEAN_FIELD("Boolean", false),
    INTEGER_FIELD("Integer", true),
    LONG_FIELD("Long", true),
    FLOAT_FIELD("Float", true),
    DOUBLE_FIELD("Double", true),
    DATE_FIELD("Date", false),
    FIELD("Text", false),
    NUMERIC_FIELD("Number", true); // Alias for LONG_FIELD.

    public static final Map<String, OldIndexFieldType> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put(ID.displayValue.toLowerCase(Locale.ROOT), ID);
        TYPE_MAP.put(BOOLEAN_FIELD.displayValue.toLowerCase(Locale.ROOT), BOOLEAN_FIELD);
        TYPE_MAP.put(INTEGER_FIELD.displayValue.toLowerCase(Locale.ROOT), INTEGER_FIELD);
        TYPE_MAP.put(LONG_FIELD.displayValue.toLowerCase(Locale.ROOT), LONG_FIELD);
        TYPE_MAP.put(FLOAT_FIELD.displayValue.toLowerCase(Locale.ROOT), FLOAT_FIELD);
        TYPE_MAP.put(DOUBLE_FIELD.displayValue.toLowerCase(Locale.ROOT), DOUBLE_FIELD);
        TYPE_MAP.put(DATE_FIELD.displayValue.toLowerCase(Locale.ROOT), DATE_FIELD);
        TYPE_MAP.put(FIELD.displayValue.toLowerCase(Locale.ROOT), FIELD);
        TYPE_MAP.put(NUMERIC_FIELD.displayValue.toLowerCase(Locale.ROOT), NUMERIC_FIELD);
    }

    private final String displayValue;
    private final boolean numeric;

    OldIndexFieldType(final String displayValue, final boolean numeric) {
        this.displayValue = displayValue;
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
