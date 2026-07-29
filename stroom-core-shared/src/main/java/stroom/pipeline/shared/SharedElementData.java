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

package stroom.pipeline.shared;

import stroom.util.shared.Indicators;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class SharedElementData {

    @JsonProperty
    private final String input;
    @JsonProperty
    private final String output;
    @JsonProperty
    private final Indicators indicators;
    @JsonProperty
    private final boolean formatInput;
    @JsonProperty
    private final boolean formatOutput;
    // Whether the element actually produced output for the record. Captured explicitly rather than
    // inferred from the output string as an empty XML element (e.g. <Event/>) has a non-empty output
    // string but no real content, which the skip-to-empty-output filter needs to distinguish.
    @JsonProperty
    private final boolean hasOutput;
    // True when a running count observable in this element's output (an EventId) reflects only the records
    // the producing run processed rather than the whole stream - a record materialised on demand with no
    // counter state to restore. Exact wherever materialisation has been contiguous from the stream start.
    @JsonProperty
    private final boolean indicativeCounts;

    public SharedElementData(final String input,
                             final String output,
                             final Indicators indicators,
                             final boolean formatInput,
                             final boolean formatOutput,
                             final boolean hasOutput) {
        this(input, output, indicators, formatInput, formatOutput, hasOutput, false);
    }

    @JsonCreator
    public SharedElementData(@JsonProperty("input") final String input,
                             @JsonProperty("output") final String output,
                             @JsonProperty("indicators") final Indicators indicators,
                             @JsonProperty("formatInput") final boolean formatInput,
                             @JsonProperty("formatOutput") final boolean formatOutput,
                             @JsonProperty("hasOutput") final boolean hasOutput,
                             @JsonProperty("indicativeCounts") final boolean indicativeCounts) {
        this.input = input;
        this.output = output;
        this.indicators = indicators;
        this.formatInput = formatInput;
        this.formatOutput = formatOutput;
        this.hasOutput = hasOutput;
        this.indicativeCounts = indicativeCounts;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public Indicators getIndicators() {
        return indicators;
    }

//    public Indicators getIndicators(final ErrorType... includedErrorTypes) {
//        return NullSafe.get(
//                indicators,
//                indicators2 -> indicators2.filter(includedErrorTypes));
//    }

    public boolean isFormatInput() {
        return formatInput;
    }

    public boolean isFormatOutput() {
        return formatOutput;
    }

    public boolean isIndicativeCounts() {
        return indicativeCounts;
    }

    public boolean isHasOutput() {
        return hasOutput;
    }

    @Override
    public String toString() {
        return "SharedElementData{" +
                "input='" + input + '\'' +
                ", output='" + output + '\'' +
                ", indicators=" + indicators +
                ", formatInput=" + formatInput +
                ", formatOutput=" + formatOutput +
                ", hasOutput=" + hasOutput +
                '}';
    }
}

