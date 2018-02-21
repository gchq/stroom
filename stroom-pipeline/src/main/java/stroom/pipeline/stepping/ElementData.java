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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.Indicators;

public class ElementData {
    private final String elementId;
    private final PipelineElementType elementType;
    private Object input;
    private Object output;
    private boolean formatInput;
    private boolean formatOutput;
    private Indicators codeIndicators;
    private Indicators outputIndicators;

    public ElementData(final String elementId, final PipelineElementType elementType) {
        this.elementId = elementId;
        this.elementType = elementType;
    }

    public String getElementId() {
        return elementId;
    }

    public PipelineElementType getElementType() {
        return elementType;
    }

    public String getInput() {
        if (input != null) {
            return input.toString();
        }
        return null;
    }

    public void setInput(final Object input) {
        this.input = input;
    }

    public void setFormatInput(final boolean formatInput) {
        this.formatInput = formatInput;
    }

    public String getOutput() {
        if (output != null) {
            return output.toString();
        }
        return null;
    }

    public void setOutput(final Object output) {
        this.output = output;
    }

    public void setFormatOutput(final boolean formatOutput) {
        this.formatOutput = formatOutput;
    }

    public Indicators getCodeIndicators() {
        return codeIndicators;
    }

    public void setCodeIndicators(final Indicators codeIndicators) {
        this.codeIndicators = codeIndicators;
    }

    public Indicators getOutputIndicators() {
        return outputIndicators;
    }

    public void setOutputIndicators(final Indicators outputIndicators) {
        this.outputIndicators = outputIndicators;
    }

    public SharedElementData convertToShared() {
        return new SharedElementData(getInput(), getOutput(), codeIndicators, outputIndicators, formatInput,
                formatOutput);
    }
}
