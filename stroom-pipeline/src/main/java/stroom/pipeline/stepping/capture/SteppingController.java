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

import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TextRange;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The pipeline framework's hook into stepping: the thing a record detector calls when it reaches the end of
 * a record.
 * <p>
 * {@code PipelineFactory} wires one of these into a stepping pipeline and registers an
 * {@link ElementMonitor} per steppable element; the detectors ({@code SAXRecordDetector},
 * {@code ReaderRecordDetector}, {@code InputStreamRecordDetector}) call {@link #endRecord} as records go
 * past. Each call persists every monitored element's IO for that record.
 * <p>
 * It captures every record and <b>never terminates the parse</b>. Which record answers the user's step -
 * and which records match their filters - is decided later, by reading the store back. That is the whole
 * difference from the engine this replaced, which ran the pipeline again for every keypress and stopped at
 * the first match.
 */
@PipelineScoped
public class SteppingController {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SteppingController.class);

    private static final TextRange DEFAULT_TEXT_RANGE = new TextRange(
            new DefaultLocation(1, 1),
            new DefaultLocation(1, 1));

    private final Set<ElementMonitor> monitors = new HashSet<>();

    private final MetaHolder metaHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationHolder locationHolder;

    private String streamInfo;
    private PipelineStepRequest request;
    private StepLocation progressLocation;
    private RecordDetector recordDetector;

    // The capture target. Set for the whole life of a capture, so every record of the stream is persisted;
    // which of them answers the user's step is decided later, by reading the store back.
    private StepDataStore stepDataStore;
    private ElementFingerprints fingerprints;
    private Consumer<StepLocation> recordListener;

    private TaskContext taskContext;

    @Inject
    SteppingController(final MetaHolder metaHolder,
                       final ErrorReceiverProxy errorReceiverProxy,
                       final LocationHolder locationHolder) {
        this.metaHolder = metaHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationHolder = locationHolder;
    }

    public void registerMonitor(final ElementMonitor monitor) {
        monitors.add(monitor);
    }

    public Set<ElementMonitor> getMonitors() {
        return monitors;
    }

    public RecordDetector getRecordDetector() {
        return recordDetector;
    }

    public void setRecordDetector(final RecordDetector recordDetector) {
        this.recordDetector = recordDetector;
    }

    /**
     * Set where this controller captures to. Must be called before the pipeline is built, because building
     * it reads the capture target; the driver does so as its first act.
     * <p>
     * There is no "not capturing" mode: a stepping pipeline exists to capture. Every record's per-element
     * IO is persisted to the store, keyed by each element's cumulative fingerprint, and the parse runs to
     * the end of the stream. {@code recordListener} is notified once each record has been fully committed,
     * so an async sweep can advance its progress signal.
     */
    public void setCaptureTarget(final StepDataStore stepDataStore,
                                 final ElementFingerprints fingerprints,
                                 final Consumer<StepLocation> recordListener) {
        this.stepDataStore = Objects.requireNonNull(stepDataStore);
        this.fingerprints = Objects.requireNonNull(fingerprints);
        this.recordListener = recordListener;
    }

    public PipelineStepRequest getRequest() {
        return request;
    }

    public void setRequest(final PipelineStepRequest request) {
        this.request = request;
    }

    public void resetSourceLocation() {
        locationHolder.reset();
    }

    public void setTaskContext(final TaskContext taskContext) {
        this.taskContext = taskContext;
    }

    public StepLocation getProgressLocation() {
        return progressLocation;
    }

    /**
     * Called by the step detector to tell us that we have reached the end of a
     * record.
     *
     * @return True if the step detector should terminate stepping.
     */
    public boolean endRecord(final long currentRecordIndex) {
        // Get the current stream number.
        final long currentStreamIndex = metaHolder.getPartIndex();
        progressLocation = new StepLocation(
                metaHolder.getMeta().getId(),
                currentStreamIndex,
                currentRecordIndex);

        if (taskContext.isTerminated()) {
            return true;
        }

        // Update the progress monitor.
        taskContext.info(() ->
                "Stepping {" +
                streamInfo +
                "} [" +
                (currentStreamIndex + 1) +
                ":" +
                (currentRecordIndex + 1) +
                "]");

        LOGGER.debug("endRecord() stream index {} record index {}", currentStreamIndex, currentRecordIndex);

        // Figure out what the highlighted portion of the input stream should be.
        TextRange highlight = DEFAULT_TEXT_RANGE;
        if (locationHolder != null && locationHolder.getCurrentLocation() != null) {
            highlight = NullSafe.get(locationHolder.getCurrentLocation().getFirstHighlight(),
                    DataRange::getAsTextRange,
                    opt -> opt.orElse(null));
        }

        captureRecord(progressLocation, highlight);
        clearAllFilters(highlight);

        // Never terminate. A capture runs to the end of the stream, and the requested step - including
        // which records match the filters - is decided later by reading the store back, not here.
        return false;
    }

    /**
     * Persist each monitored element's IO for this record to the store, keyed by the element's cumulative
     * fingerprint - the key that makes the IO reusable until that element, or something upstream of it,
     * changes.
     */
    private void captureRecord(final StepLocation location, final TextRange highlight) {
        final LoggingErrorReceiver errorReceiver = getErrorReceiver();
        final List<StepDataStore.ElementRecord> records = new ArrayList<>();
        for (final ElementMonitor monitor : monitors) {
            // An element with no fingerprint is not part of the fingerprinted pipeline, so there is no key
            // to store its IO under and nothing could ever read it back.
            final String fingerprint = fingerprints.getCumulativeFingerprint(monitor.getElementId().getId());
            if (fingerprint != null) {
                records.add(new StepDataStore.ElementRecord(
                        monitor.getElementId(),
                        fingerprint,
                        monitor.getCapturedElementData(errorReceiver, highlight)));
            }
        }
        // Commit the whole record atomically, then signal that it is available.
        stepDataStore.putRecord(location, records);
        if (recordListener != null) {
            recordListener.accept(location);
        }
    }

    /**
     * This method resets all filters, so they are ready for the next record.
     */
    void clearAllFilters(final TextRange highlight) {
        // Store the current data for each filter.
        monitors.forEach(elementMonitor -> elementMonitor.clear(highlight));

        // Clear all indicators.
        final LoggingErrorReceiver loggingErrorReceiver = getErrorReceiver();
        if (loggingErrorReceiver != null) {
            loggingErrorReceiver.clearIndicators();
        }
    }

    private LoggingErrorReceiver getErrorReceiver() {
        final ErrorReceiver errorReceiver = errorReceiverProxy.getErrorReceiver();
        if (errorReceiver instanceof LoggingErrorReceiver) {
            return (LoggingErrorReceiver) errorReceiver;
        }
        return null;
    }


    public void setStreamInfo(final String streamInfo) {
        this.streamInfo = streamInfo;
    }
}
