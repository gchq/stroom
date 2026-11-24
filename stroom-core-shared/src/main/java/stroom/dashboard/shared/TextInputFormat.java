/*
 * Copyright 2026 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.docref.HasDisplayValue;

import java.util.Objects;

/// The format constraint applied to the value of a dashboard text input component.
public enum TextInputFormat implements HasDisplayValue {
    TEXT("Text"),
    NUMBER("Number"),
    DATE_TIME("Date/Time"),
    PATTERN("Regex Pattern"),
    ;

    private final String displayValue;

    TextInputFormat(final String displayValue) {
        this.displayValue = Objects.requireNonNull(displayValue);
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
