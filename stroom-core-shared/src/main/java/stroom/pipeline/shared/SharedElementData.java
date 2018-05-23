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

import stroom.util.shared.Indicators;
import stroom.docref.SharedObject;

public class SharedElementData implements SharedObject {
    private static final long serialVersionUID = -1614851794579868895L;

    private String input;
    private String output;
    private Indicators codeIndicators;
    private Indicators outputIndicators;
    private boolean formatInput;
    private boolean formatOutput;

    public SharedElementData() {
        // Default constructor necessary for GWT serialisation.
    }

    public SharedElementData(final String input, final String output, final Indicators codeIndicators,
                             final Indicators outputIndicators, final boolean formatInput, final boolean formatOutput) {
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
