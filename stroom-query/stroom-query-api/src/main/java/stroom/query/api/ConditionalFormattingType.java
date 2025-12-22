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

package stroom.query.api;

import stroom.docref.HasDisplayValue;

import java.util.ArrayList;
import java.util.List;

public enum ConditionalFormattingType implements HasDisplayValue {
    BACKGROUND("Background"),
    TEXT("Text"),
    CUSTOM("Custom"),
    ;

    public static final List<ConditionalFormattingType> LIST = new ArrayList<>();

    static {
        LIST.add(BACKGROUND);
        LIST.add(TEXT);
        LIST.add(CUSTOM);
    }

    private final String displayValue;

    ConditionalFormattingType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
