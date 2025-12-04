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

public enum TemporalPrecision implements HasDisplayValue {
    DAY("Day"),
    HOUR("Hour"),
    MINUTE("Minute"),
    SECOND("Second"),
    MILLISECOND("Millisecond"),
    NANOSECOND("Nanosecond");

    public static final List<TemporalPrecision> ORDERED_LIST = List.of(
            DAY,
            HOUR,
            MINUTE,
            SECOND,
            MILLISECOND,
            NANOSECOND);

    private final String displayValue;

    TemporalPrecision(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
