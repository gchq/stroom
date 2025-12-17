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

public enum RangeType implements HasDisplayValue {
    // Treat all ranges as bytes.
    BYTE("Unsigned Byte"), // 1 byte from 0 to 255
    // Treat all ranges as shorts.
    SHORT("Unsigned Short"), // 2 bytes from 0 to 65,535
    // Treat all ranges as integers.
    INT("Unsigned Integer"), // 4 bytes from 0 to 4,294,967,295
    // Treat all ranges as longs.
    LONG("Unsigned Long"); // 8 bytes from 0 to 9,223,372,036,854,775,807

    public static final List<RangeType> ORDERED_LIST = List.of(
            BYTE,
            SHORT,
            INT,
            LONG);

    private final String displayValue;

    RangeType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
