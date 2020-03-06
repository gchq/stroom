/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.Indicators;

@JsonInclude(Include.NON_NULL)
public class SharedElementData {
    @JsonProperty
    private final String input;
    @JsonProperty
    private final String output;
    @JsonProperty
    private final Indicators codeIndicators;
    @JsonProperty
    private final Indicators outputIndicators;
    @JsonProperty
    private final boolean formatInput;
    @JsonProperty
    private final boolean formatOutput;

    @JsonCreator
    public SharedElementData(@JsonProperty("input") final String input,
                             @JsonProperty("output") final String output,
                             @JsonProperty("codeIndicators") final Indicators codeIndicators,
                             @JsonProperty("outputIndicators") final Indicators outputIndicators,
                             @JsonProperty("formatInput") final boolean formatInput,
                             @JsonProperty("formatOutput") final boolean formatOutput) {
        this.input = input;
        this.output = output;
        this.codeIndicators = codeIndicators;
        this.outputIndicators = outputIndicators;
        this.formatInput = formatInput;
        this.formatOutput = formatOutput;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public Indicators getCodeIndicators() {
        return codeIndicators;
    }

    public Indicators getOutputIndicators() {
        return outputIndicators;
    }

    public boolean isFormatInput() {
        return formatInput;
    }

    public boolean isFormatOutput() {
        return formatOutput;
    }
}

