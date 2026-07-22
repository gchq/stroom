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

package stroom.pipeline.stepping.capture;

import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Element;
import stroom.pipeline.filter.SAXEventRecorder;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.writer.XMLWriter;
import stroom.pipeline.xml.event.EventList;
import stroom.pipeline.xml.event.EventListSerializer;
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

    /**
     * Capture this element's IO for the current record in the store's element-specific form: an XML
     * element's SAX output as replayable events, a reader/writer's as text. The {@code formatInput}/
     * {@code formatOutput}/{@code hasOutput} flags are computed exactly as the wire form always has.
     */
    public CapturedElementData getCapturedElementData(final LoggingErrorReceiver loggingErrorReceiver,
                                                      final TextRange textRange) {
        CapturedData input = null;
        CapturedData output = null;
        boolean formatInput = false;
        boolean formatOutput = false;
        boolean hasOutput = false;
        Indicators indicators = null;

        if (inputRecorder != null) {
            try {
                if (inputRecorder instanceof final SAXEventRecorder saxEventRecorder) {
                    input = saxEvents(saxEventRecorder);
                    // Structured (SAX) input is "formatted"; matches the old !(data instanceof String).
                    formatInput = input != null;
                } else {
                    final Object data = inputRecorder.getData(textRange);
                    input = data == null ? null : CapturedData.text(data.toString());
                }
            } catch (final Exception e) {
                input = null;
                formatInput = false;
                logError(loggingErrorReceiver, textRange, "input", e);
            }
        }

        if (outputRecorder != null) {
            try {
                if (outputRecorder instanceof final SAXEventRecorder saxEventRecorder) {
                    output = saxEvents(saxEventRecorder);
                    formatOutput = output != null || element instanceof XMLWriter;
                    // Match the live skip-to-output rule (maxElementDepth > 1).
                    hasOutput = saxEventRecorder.hasContent();
                } else {
                    final Object data = outputRecorder.getData(textRange);
                    output = data == null ? null : CapturedData.text(data.toString());
                    formatOutput = element instanceof XMLWriter;
                    hasOutput = data != null && !data.toString().isBlank();
                }
            } catch (final Exception e) {
                output = null;
                formatOutput = false;
                hasOutput = false;
                logError(loggingErrorReceiver, textRange, "output", e);
            }
        }

        if (loggingErrorReceiver != null) {
            final Indicators found = loggingErrorReceiver.getIndicators(elementId);
            if (found != null && found.getMaxSeverity() != null) {
                indicators = new Indicators(found);
            }
        }

        return new CapturedElementData(input, output, formatInput, formatOutput, hasOutput, indicators);
    }

    private static CapturedData saxEvents(final SAXEventRecorder recorder) {
        final EventList events = recorder.getEventList();
        return events == null ? null : CapturedData.saxEvents(EventListSerializer.toBytes(events));
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
