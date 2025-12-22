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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ProcessorType implements HasDisplayValue {
    PIPELINE("pipelineStreamProcessor"),
    STREAMING_ANALYTIC("streamingAnalyticProcessor");

    private static final Map<String, ProcessorType> map;

    static {
        map = Arrays
                .stream(ProcessorType.values())
                .collect(Collectors.toMap(ProcessorType::getDisplayValue, Function.identity()));
    }

    public static ProcessorType fromDisplayValue(final String displayValue) {
        return map.get(displayValue);
    }

    private final String displayValue;

    ProcessorType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
