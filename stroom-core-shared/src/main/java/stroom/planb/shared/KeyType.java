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

package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum KeyType implements HasDisplayValue {
    // Treat all keys as booleans.
    BOOLEAN("Boolean"), // 1 byte true/false
    // Treat all keys as bytes.
    BYTE("Byte"), // 1 byte from +127 to -128
    // Treat all keys as shorts.
    SHORT("Short"), // 2 bytes from +32,767 to -32,768
    // Treat all keys as integers.
    INT("Integer"), // 4 bytes from +2,147,483,647 to -2,147,483,648
    // Treat all keys as longs.
    LONG("Long"), // 8 bytes from +9,223,372,036,854,775,807 to -9,223,372,036,854,775,808
    // Treat all keys as floats.
    FLOAT("Float"), // 4 bytes from 3.402,823,5 E+38 to 1.4 E-45
    // Treat all keys as doubles.
    DOUBLE("Double"), // 8 bytes from 1.797,693,134,862,315,7 E+308 to 4.9 E-324
    // Treat all keys as bytes but with a max length of 511.
    STRING("String"), // max 511 bytes
    // Always use a UID lookup table to store all keys.
    UID_LOOKUP("UID lookup table"), // max 511 bytes, deduplicated data
    // Always use a lookup table to store all keys. The key is a hash plus a sequence number.
    // Lookups deduplicate data and reduce storage requirements but impact performance.
    HASH_LOOKUP("Hash lookup table"), // unlimited bytes, deduplicated data
    // Use string, UID lookup or hash lookup depending on the length of the string.
    VARIABLE("Variable"),
    // Use a set of tags to describe the key.
    TAGS("Tags");

    public static final List<KeyType> ORDERED_LIST = List.of(
            BOOLEAN,
            BYTE,
            SHORT,
            INT,
            LONG,
            FLOAT,
            DOUBLE,
            STRING,
            UID_LOOKUP,
            HASH_LOOKUP,
            VARIABLE,
            TAGS);

    private final String displayValue;

    KeyType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
