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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;

public class ElementData {

    private final ElementId elementId;
    private final PipelineElementType elementType;
    private Object input;
    private Object output;
    private boolean formatInput;
    private boolean formatOutput;
    private Indicators indicators;

    public ElementData(final ElementId elementId, final PipelineElementType elementType) {
        this.elementId = elementId;
        this.elementType = elementType;
    }

    public ElementId getElementId() {
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

    public boolean isFormatInput() {
        return formatInput;
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

    public boolean isFormatOutput() {
        return formatOutput;
    }

    public void setFormatOutput(final boolean formatOutput) {
        this.formatOutput = formatOutput;
    }

    public Indicators getIndicators() {
        return indicators;
    }

    public void setIndicators(final Indicators indicators) {
        this.indicators = indicators;
    }

    public SharedElementData convertToShared() {
        return new SharedElementData(getInput(), getOutput(), indicators, formatInput,
                formatOutput);
    }

    @Override
    public String toString() {
        return "ElementData{" +
               "elementId='" + elementId + '\'' +
               ", maxSeverity=" + indicators.getMaxSeverity() +
               '}';
    }
}
