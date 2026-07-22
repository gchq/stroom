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

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docstore.shared.DocRefUtil;
import stroom.feed.api.FeedProperties;
import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataHolder;
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.SessionStepResolver;
import stroom.pipeline.stepping.read.StoreStepResolver;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives one stream's capture: opens the stream, builds a stepping pipeline around a
 * {@link SteppingController}, and runs every part of it through, so that every record's per-element IO
 * lands in the sweep's store.
 * <p>
 * Runs on a sweep thread, and is the producer half of the async model - {@link SessionStepResolver} is
 * blocked on the {@link StreamSweep} this fills. That gives it one hard obligation: <b>every way out must
 * signal the sweep</b>, and only a capture that reached the end of the stream without failing may signal
 * success. A truncated stream that looks complete makes a step navigate straight past the records that were
 * never captured, into the next stream, silently.
 * <p>
 * {@code @PipelineScoped}, so one instance drives one capture and holds that capture's state.
 */
public class StreamCaptureDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamCaptureDriver.class);

    private final Store streamStore;
    private final FeedProperties feedProperties;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final PipelineHolder pipelineHolder;
    private final MetaHolder metaHolder;
    private final LocationFactoryProxy locationFactory;
    private final CurrentUserHolder currentUserHolder;
    private final SteppingController controller;
    private final PipelineStore pipelineStore;
    private final PipelineFactory pipelineFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineDataHolderFactory pipelineDataHolderFactory;
    private final PipelineContext pipelineContext;
    private final SecurityContext securityContext;

    private TaskContext taskContext;
    private String lastFeedName;
    private Pipeline pipeline;
    private LoggingErrorReceiver loggingErrorReceiver;
    // elementId => Indicators
    private Map<ElementId, Indicators> startProcessIndicatorMap = Collections.emptyMap();
    private PipelineStepRequest request;
    // The fingerprints keying the captured IO, exposed so a caller can read the chunks back.
    private ElementFingerprints captureFingerprints;
    // A reader only ever sees the sweep's complete/error signal, so a capture must remember its first
    // failure and turn it into markError - otherwise a truncated stream is indistinguishable from a fully
    // captured one, and a step would silently navigate past the end of it into the next stream.
    private volatile Throwable captureFailure;
    // The sweep being captured into, so per-stream state can be recorded on it as it is discovered.
    private StreamSweep captureSweep;

    @Inject
    StreamCaptureDriver(final Store streamStore,
                           final FeedProperties feedProperties,
                           final FeedHolder feedHolder,
                           final MetaDataHolder metaDataHolder,
                           final PipelineHolder pipelineHolder,
                           final MetaHolder metaHolder,
                           final LocationFactoryProxy locationFactory,
                           final CurrentUserHolder currentUserHolder,
                           final SteppingController controller,
                           final PipelineStore pipelineStore,
                           final PipelineFactory pipelineFactory,
                           final ErrorReceiverProxy errorReceiverProxy,
                           final PipelineDataHolderFactory pipelineDataHolderFactory,
                           final PipelineContext pipelineContext,
                           final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.feedProperties = feedProperties;
        this.feedHolder = feedHolder;
        this.metaDataHolder = metaDataHolder;
        this.pipelineHolder = pipelineHolder;
        this.metaHolder = metaHolder;
        this.locationFactory = locationFactory;
        this.currentUserHolder = currentUserHolder;
        this.controller = controller;
        this.pipelineStore = pipelineStore;
        this.pipelineFactory = pipelineFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineDataHolderFactory = pipelineDataHolderFactory;
        this.pipelineContext = pipelineContext;
        this.securityContext = securityContext;
    }

    /**
     * Run one whole stream through the pipeline in capture mode, persisting every record's per-element IO
     * into the sweep's store. This does no step navigation and never exits early: it captures the lot, and
     * the requested step is served afterwards by reading the store back ({@link StoreStepResolver}).
     * <p>
     * Runs on a sweep thread. Every exit path must signal the {@link StreamSweep} - readers block on it -
     * so a failure marks the sweep errored rather than letting it look like a clean, complete capture of a
     * stream that is actually truncated.
     */
    public void capture(final TaskContext taskContext,
                            final PipelineStepRequest request,
                            final long metaId,
                            final StreamSweep streamSweep,
                            final ElementFingerprints fingerprints) {
        this.taskContext = taskContext;
        this.request = request;
        this.captureFingerprints = fingerprints;
        this.captureFailure = null;
        this.captureSweep = streamSweep;

        // Publish the task context BEFORE reading the terminate flag. The session sets that flag before
        // reading the context, so whichever of us runs second sees the other's write and the sweep cannot
        // start after its session has already been closed.
        streamSweep.setTaskContext(taskContext);
        if (streamSweep.isTerminateRequested()) {
            streamSweep.markError(new RuntimeException(
                    "Stepping capture of stream " + metaId + " was terminated before it started"));
            return;
        }

        taskContext.info(() -> "Capturing stepping data");
        final StepDataStore store = streamSweep.getStore();

        try {
            securityContext.secure(AppPermission.STEPPING_PERMISSION, () -> {
                securityContext.useAsRead(() -> {
                    currentUserHolder.setCurrentUser(securityContext.getUserIdentity());

                    loggingErrorReceiver = new LoggingErrorReceiver();
                    errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

                    controller.setRequest(request);
                    controller.setTaskContext(taskContext);
                    // Capture mode: persist each record atomically and advance the sweep's progress signal.
                    controller.setCaptureTarget(store, fingerprints, streamSweep::recordCaptured);

                    try (final Source source = streamStore.openSource(metaId)) {
                        if (source != null) {
                            captureStream(source, request.getChildStreamType());
                        }
                    } catch (final IOException | RuntimeException e) {
                        error(e);
                    }

                    if (lastFeedName != null && pipeline != null) {
                        pipeline.endProcessing();
                        lastFeedName = null;
                    }
                });
            });

            // Only a capture that ran to the end of the stream without failing may be reported as complete:
            // a reader treats "complete" as "every record this stream will ever have is now in the store",
            // and will happily navigate past the end of a truncated stream into the next one.
            if (captureFailure != null) {
                streamSweep.markError(captureFailure);
            } else if (taskContext.isTerminated() || streamSweep.isTerminateRequested()) {
                streamSweep.markError(new RuntimeException(
                        "Stepping capture of stream " + metaId + " was terminated"));
            } else {
                streamSweep.markFullyCaptured();
            }
        } catch (final Throwable t) {
            // Throwable, not RuntimeException: an Error here (OOM is plausible on a large stream) would
            // otherwise leave the sweep neither complete nor errored, hanging every reader on it.
            streamSweep.markError(t);
            throw t;
        } finally {
        }
    }

    private void captureStream(final Source source, final String childDataType) {
        final Meta meta = source.getMeta();
        final String feedName = meta.getFeedName();
        controller.setStreamInfo(createStreamInfo(feedName, meta));
        metaHolder.setMeta(meta);
        metaHolder.setChildDataType(childDataType);

        // Build the pipeline (this also wires the feed/meta/pipeline holders) and start it.
        lastFeedName = feedName;
        createPipeline(controller, feedName);
        if (pipeline == null || !loggingErrorReceiver.isAllOk()) {
            // The pipeline could not be built (isAllOk is only false for ERROR/FATAL_ERROR, and nothing has
            // been processed yet). The live path shows the user these errors via the step result; a capture
            // has to surface them through the sweep or the reader just sees an empty, "successful" stream.
            final String detail = loggingErrorReceiver.getMessage();
            error(ProcessException.create("Unable to create pipeline for stepping capture"
                                          + (detail == null || detail.isBlank()
                                                  ? ""
                                                  : ": " + detail)));
            return;
        }
        try {
            pipeline.startProcessing();
            startProcessIndicatorMap = getErrorReceiverIndicatorsMap();
            // These belong to the stream, not to a record, so a step served from the store later has no
            // other way to find them.
            captureSweep.setStartProcessIndicators(startProcessIndicatorMap);
        } catch (final LoggedException e) {
            return;
        }

        try {
            final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
            locationFactory.setLocationFactory(streamLocationFactory);
            final long maxPartIndex = source.count() - 1;
            final String encoding = feedProperties.getEncoding(feedName, meta.getTypeName(), childDataType);

            for (long partIndex = 0; partIndex <= maxPartIndex && !taskContext.isTerminated(); partIndex++) {
                metaHolder.setPartIndex(partIndex);
                streamLocationFactory.setPartIndex(partIndex);
                controller.clearAllFilters(null);

                try (final InputStreamProvider inputStreamProvider = source.get(partIndex)) {
                    metaHolder.setInputStreamProvider(inputStreamProvider);
                    final SegmentInputStream inputStream = inputStreamProvider.get(childDataType);
                    // Recorded per part on the sweep: a step result reports the flag for the part holding
                    // the found record, which may not be the last part captured.
                    captureSweep.setSegmented(partIndex, inputStream.count() > 1);
                    if (inputStream.size() > 0) {
                        pipeline.process(inputStream, encoding);
                    }
                } catch (final LoggedException e) {
                    // Already recorded in the logging error receiver.
                } catch (final IOException | RuntimeException e) {
                    error(e);
                }
            }
        } catch (final IOException | RuntimeException e) {
            error(e);
        }
    }

    /**
     * @return the fingerprints used to key the captured IO (available after {@link #capture}).
     */
    public ElementFingerprints getCaptureFingerprints() {
        return captureFingerprints;
    }

    private void createPipeline(final SteppingController controller, final String feedName) {
        if (pipeline == null) {
            // Set the pipeline so it can be used by a filter if needed.
            final PipelineDoc pipelineDoc = controller.getRequest().getPipelineDoc();

            feedHolder.setFeedName(feedName);

            // Setup the meta data holder.
            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

            pipelineHolder.setPipeline(DocRefUtil.create(pipelineDoc));
            pipelineContext.setStepping(true);

            final PipelineDataHolder pipelineDataHolder = pipelineDataHolderFactory.create(pipelineDoc);
            final PipelineData pipelineData = pipelineDataHolder.getMergedPipelineData();

            pipeline = pipelineFactory.create(pipelineData, taskContext, controller);

            // Don't return a pipeline if we cannot step with it.
            if (pipeline == null
                || controller.getRecordDetector() == null
                || controller.getMonitors() == null
                || controller.getMonitors().isEmpty()) {
                throw ProcessException.create(
                        "You cannot step with this pipeline as it does not contain required elements.");
            }
        }
    }

    private String createStreamInfo(final String feedName, final Meta meta) {
        return "id=" +
               meta.getId() +
               ", feed=" +
               feedName +
               ", received=" +
               DateUtil.createNormalDateTimeString(meta.getCreateMs());
    }

    /**
     * Record a failure during capture. The first one wins: later errors are usually fallout from it, and it
     * is what the sweep reports to whoever is waiting on this stream.
     */
    private void error(final Exception e) {
        LOGGER.debug(e.getMessage(), e);

        if (captureFailure == null) {
            captureFailure = e;
        }
    }

    private Map<ElementId, Indicators> getErrorReceiverIndicatorsMap() {
        final ErrorReceiver errorReceiver = errorReceiverProxy.getErrorReceiver();
        if (errorReceiver instanceof final LoggingErrorReceiver loggingErrorReceiver2) {
            return new ConcurrentHashMap<>(NullSafe.map(loggingErrorReceiver2.getIndicatorsMap()));
        } else {
            return Collections.emptyMap();
        }
    }

}
