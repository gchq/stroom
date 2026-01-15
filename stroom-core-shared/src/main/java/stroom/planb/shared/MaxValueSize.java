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

public enum MaxValueSize implements HasDisplayValue {
    ONE("Max 255"),
    TWO("Max 65,535"),
    THREE("Max 16,777,215"),
    FOUR("Max 4,294,967,295"),
    FIVE("Max 1,099,511,627,775"),
    SIX("Max 281,474,976,710,655"),
    SEVEN("Max 72,057,594,037,927,900"),
    EIGHT("Max 9,223,372,036,854,780,000");

    public static final List<MaxValueSize> ORDERED_LIST = List.of(
            ONE,
            TWO,
            THREE,
            FOUR,
            FIVE,
            SIX,
            SEVEN,
            EIGHT);

    private final String displayValue;

    MaxValueSize(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
