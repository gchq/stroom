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

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docstore.shared.DocRefUtil;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
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
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.state.PipelineHolder;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SteppingRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SteppingRequestHandler.class);

    private final Store streamStore;
    private final MetaService metaService;
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
    private List<Long> allMetaIdList;
    private List<Long> filteredMetaIdList;
    private int currentStreamIndex = -1;
    private int curentStreamOffset;
    private StepLocation currentLocation;
    private Long lastStreamId;
    private String lastFeedName;
    private Pipeline pipeline;
    private LoggingErrorReceiver loggingErrorReceiver;
    private final Set<String> generalErrors = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean isSegmentedData;
    // elementId => Indicators
    private Map<ElementId, Indicators> startProcessIndicatorMap = Collections.emptyMap();
    private PipelineStepRequest request;
    private SteppingResult result;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final Instant createTime;
    private Instant lastRequestTime;
    // Phase 2 capture: the fingerprints used to key captured IO, exposed so the caller can read chunks back.
    private ElementFingerprints captureFingerprints;
    // Capture mode only. On the live step() path a failure is reported to the user through the result's
    // generalErrors, but an async reader never sees that set - it only sees the sweep's complete/error
    // signal. So a capture must remember its first failure and turn it into markError, or a truncated
    // stream would be indistinguishable from a fully captured one.
    private volatile boolean capturing;
    private volatile Throwable captureFailure;
    // The sweep being captured into, so per-stream state can be recorded on it as it is discovered.
    private StreamSweep captureSweep;

    @Inject
    SteppingRequestHandler(final Store streamStore,
                           final MetaService metaService,
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
        this.metaService = metaService;
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
        this.createTime = Instant.now();
        this.lastRequestTime = createTime;
    }

    /**
     * Phase 2 capture: process one whole stream through the pipeline in capture mode, persisting every
     * record's per-element IO into the given store. Unlike {@link #exec} this does no step navigation and
     * never terminates early - the requested step is served afterwards by scanning the store.
     */
    public void execCapture(final TaskContext taskContext,
                            final PipelineStepRequest request,
                            final long metaId,
                            final StreamSweep streamSweep,
                            final ElementFingerprints fingerprints) {
        this.taskContext = taskContext;
        this.request = request;
        this.captureFingerprints = fingerprints;
        this.capturing = true;
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
                streamSweep.markComplete();
            }
        } catch (final Throwable t) {
            // Throwable, not RuntimeException: an Error here (OOM is plausible on a large stream) would
            // otherwise leave the sweep neither complete nor errored, hanging every reader on it.
            streamSweep.markError(t);
            throw t;
        } finally {
            capturing = false;
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
                    isSegmentedData = inputStream.count() > 1;
                    captureSweep.setSegmented(partIndex, isSegmentedData);
                    if (inputStream.size() > 0) {
                        controller.setIsSegmentedData(isSegmentedData);
                        controller.setStepLocation(null);
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
     * @return the fingerprints used to key the captured IO (available after {@link #execCapture}).
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

    private void error(final Exception e) {
        LOGGER.debug(e.getMessage(), e);

        if (capturing && captureFailure == null) {
            captureFailure = e;
        }

        if (e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            generalErrors.add(e.toString());
        } else {
            generalErrors.add(e.getMessage());
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
