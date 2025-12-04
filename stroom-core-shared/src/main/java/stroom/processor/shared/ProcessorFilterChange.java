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

package stroom.processor.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum ProcessorFilterChange implements HasDisplayValue {
    ENABLE(
            "Enable",
            "Enable."),
    DISABLE(
            "Disable",
            "Disable."),
    DELETE(
            "Delete",
            "Delete."),
    SET_RUN_AS_USER(
            "Set run as user",
            "Set a run asuser.");

    public static final List<ProcessorFilterChange> LIST = List.of(
            ENABLE,
            DISABLE,
            DELETE,
            SET_RUN_AS_USER
    );

    private final String displayValue;
    private final String description;

    ProcessorFilterChange(final String displayValue,
                          final String description) {
        this.displayValue = displayValue;
        this.description = description;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getDescription() {
        return description;
    }
}
