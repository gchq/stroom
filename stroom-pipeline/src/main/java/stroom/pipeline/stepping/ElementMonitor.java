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

import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Element;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.writer.XMLWriter;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.TextRange;

public class ElementMonitor {

    private final ElementId elementId;
    private final PipelineElementType elementType;
    private final Element element;
    private Recorder inputRecorder;
    private Recorder outputRecorder;
    private SteppingFilter steppingFilter;

    public ElementMonitor(final ElementId elementId, final PipelineElementType elementType, final Element element) {
        this.elementId = elementId;
        this.elementType = elementType;
        this.element = element;
    }

    public ElementId getElementId() {
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

    public boolean filterMatches(final long currentRecordIndex) {
        return steppingFilter != null && steppingFilter.filterMatches(currentRecordIndex);
    }

    public void clear(final TextRange highlight) {
        if (inputRecorder != null) {
            inputRecorder.clear(highlight);
        }
        if (outputRecorder != null) {
            outputRecorder.clear(highlight);
        }
    }

    public ElementData getElementData(final LoggingErrorReceiver loggingErrorReceiver,
                                      final TextRange textRange) {
        final ElementData elementData = new ElementData(elementId, elementType);

        if (inputRecorder != null) {
            try {
                final Object data = inputRecorder.getData(textRange);
                elementData.setInput(data);
                elementData.setFormatInput(!(data == null || data instanceof String));
            } catch (final Exception e) {
                elementData.setInput(null);
                elementData.setFormatInput(false);
                logError(loggingErrorReceiver, textRange, "input", e);
            }
        }

        if (outputRecorder != null) {
            try {
                final Object data = outputRecorder.getData(textRange);
                elementData.setOutput(data);
                elementData.setFormatOutput(!(data == null || data instanceof String) || element instanceof XMLWriter);
            } catch (final Exception e) {
                elementData.setOutput(null);
                elementData.setFormatOutput(false);
                logError(loggingErrorReceiver, textRange, "output", e);
            }
        }

        if (loggingErrorReceiver != null) {
            // Get indicators.
            final Indicators indicators = loggingErrorReceiver.getIndicators(elementId);
            if (indicators != null && indicators.getMaxSeverity() != null) {
                elementData.setIndicators(new Indicators(indicators));
            }
        }

        return elementData;
    }

    private void logError(final LoggingErrorReceiver loggingErrorReceiver,
                          final TextRange textRange,
                          final String pane,
                          final Exception e) {
        NullSafe.consume(loggingErrorReceiver, receiver ->
                receiver.log(
                        Severity.FATAL_ERROR,
                        textRange.getFrom(),
                        elementId,
                        LogUtil.message(
                                "Error getting {} data for element {}: {}",
                                pane,
                                elementType.getType(),
                                e.getMessage()),
                        e));
    }

    @Override
    public String toString() {
        return elementId.toString();
    }
}
