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

package stroom.pipeline.server.task;

import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.factory.Element;
import stroom.pipeline.server.writer.XMLWriter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.Indicators;

public class ElementMonitor {
    private final String elementId;
    private final PipelineElementType elementType;
    private final Element element;
    private Recorder inputRecorder;
    private Recorder outputRecorder;
    private SteppingFilter steppingFilter;

    public ElementMonitor(final String elementId, final PipelineElementType elementType, final Element element) {
        this.elementId = elementId;
        this.elementType = elementType;
        this.element = element;
    }

    public String getElementId() {
        return elementId;
    }

    public void setInputMonitor(final Recorder inputMonitor) {
        this.inputRecorder = inputMonitor;
    }

    public void setOutputMonitor(final Recorder outputMonitor) {
        this.outputRecorder = outputMonitor;
    }

    public void setSteppingFilter(final SteppingFilter steppingFilter) {
        this.steppingFilter = steppingFilter;
    }

    public boolean isFilterApplied() {
        return steppingFilter != null && steppingFilter.isFilterApplied();
    }

    public boolean filterMatches(final long currentRecordNo) {
        return steppingFilter != null && steppingFilter.filterMatches(currentRecordNo);
    }

    public void clear() {
        if (inputRecorder != null) {
            inputRecorder.clear();
        }
        if (outputRecorder != null) {
            outputRecorder.clear();
        }
    }

    public ElementData getElementData(final LoggingErrorReceiver loggingErrorReceiver) {
        final ElementData elementData = new ElementData(elementId, elementType);

        if (inputRecorder != null) {
            final Object data = inputRecorder.getData();
            elementData.setInput(data);
            elementData.setFormatInput(!(data == null || data instanceof String));
        }
        if (outputRecorder != null) {
            final Object data = outputRecorder.getData();
            elementData.setOutput(data);
            elementData.setFormatOutput(!(data == null || data instanceof String) || element instanceof XMLWriter);
        }

        if (loggingErrorReceiver != null) {
            // Get indicators.
            final Indicators indicators = loggingErrorReceiver.getIndicatorsMap().get(elementId);
            if (indicators != null && indicators.getMaxSeverity() != null) {
                if (elementType.hasRole(PipelineElementType.ROLE_HAS_CODE)) {
                    // Take a copy.
                    elementData.setCodeIndicators(new Indicators(indicators));
                } else {
                    // Take a copy.
                    elementData.setOutputIndicators(new Indicators(indicators));
                }
            }
        }

        return elementData;
    }
}
