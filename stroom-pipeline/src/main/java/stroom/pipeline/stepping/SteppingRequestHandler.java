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
import stroom.docref.DocRef;
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
import stroom.pipeline.factory.PipelineDataCache;
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
    private final SteppingResponseCache steppingResponseCache;
    private final PipelineDataCache pipelineDataCache;
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
                           final SteppingResponseCache steppingResponseCache,
                           final PipelineDataCache pipelineDataCache,
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
        this.steppingResponseCache = steppingResponseCache;
        this.pipelineDataCache = pipelineDataCache;
        this.pipelineContext = pipelineContext;
        this.securityContext = securityContext;
        this.createTime = Instant.now();
        this.lastRequestTime = createTime;
    }

    public void exec(final TaskContext taskContext,
                     final PipelineStepRequest request) {
        this.taskContext = taskContext;
        this.request = request;
        taskContext.info(() -> "Started stepping");

        securityContext.secure(AppPermission.STEPPING_PERMISSION, () -> {
            // Elevate user permissions so that inherited pipelines that the user only has 'Use' permission
            // on can be read.
            securityContext.useAsRead(() -> {
                // Set the current user so they are visible during translation.
                currentUserHolder.setCurrentUser(securityContext.getUserIdentity());

                loggingErrorReceiver = new LoggingErrorReceiver();
                errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

                // Set the controller for the pipeline.
                controller.setRequest(request);
                controller.setTaskContext(taskContext);

                try {
                    // Initialise the process by finding streams to process and setting
                    // the step location.
                    initialise(request);

                    // Get the first stream to try and process.
                    final Long metaId = getMetaId(request);

                    // Start processing.
                    process(request, metaId);
                } catch (final ProcessException e) {
                    error(e);
                }

                // Make sure all resources are returned to pools.
                if (lastFeedName != null) {
                    // destroy the last pipeline.
                    pipeline.endProcessing();
                    lastFeedName = null;
                }

                setResult(createResult(true));
            });
        });
    }

    private void setResult(final SteppingResult result) {
        this.result = result;
        countDownLatch.countDown();
    }

    public SteppingResult getResult(final PipelineStepRequest request) {
        lastRequestTime = Instant.now();

        try {
            if (!countDownLatch.await(request.getTimeout(), TimeUnit.MILLISECONDS)) {
                LOGGER.debug("Timeout");
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }

        SteppingResult res = this.result;
        if (res == null) {
            res = createResult(false);
        }
        return res;
    }

    private SteppingResult createResult(final boolean complete) {
        final StepData stepData;
        // Set the output.
        if (controller.getLastFoundLocation() != null) {
            currentLocation = controller.getLastFoundLocation();

            // FIXME : Sort out use of response cache so we don't run out of
            // memory.
            stepData = steppingResponseCache.getStepData(currentLocation);

//                // Fill in the source data if it hasn't been already.
//                for (final ElementData elementData : stepData.getElementMap().values()) {
//                    if (elementData.getElementType().hasRole(PipelineElementType.ROLE_PARSER)
//                            && elementData.getInput() == null) {
//                        final String data = getSourceData(currentLocation, stepData.getSourceHighlights());
//                        elementData.setInput(data);
//                    }
//                }

        } else {
            // Pick up any step data that remains so we can deliver any errors
            // that caused the system not to step.
            stepData = controller.createStepData(null);
        }
        // Indicators get cleared at the start of each call to process so merge in any indicators
        // we found when calling startProcessing
        mergeStartProcessingIndicators(stepData);

        taskContext.info(() -> "Finished stepping");

        if (taskContext.isTerminated()) {
            generalErrors.add("Stepping was terminated");
        }

        return new SteppingResult(
                request.getSessionUuid(),
                request.getStepFilterMap(),
                controller.getProgressLocation(),
                currentLocation,
                NullSafe.get(stepData, StepData::convertToShared),
                curentStreamOffset,
                controller.isFound(),
                generalErrors,
                isSegmentedData,
                complete);
    }


    private void mergeStartProcessingIndicators(final StepData stepData) {
        if (stepData != null) {
            final Map<String, ElementData> elementIdToDataMap = NullSafe.map(stepData.getElementMap());

            startProcessIndicatorMap.forEach((elementId, startProcessingIndicators) -> {
                final ElementData elementData = elementIdToDataMap.get(elementId.getId());
                Objects.requireNonNull(elementData, () -> "No elementData for elementId " + elementId);

                final Indicators combinedIndicators = Indicators.combine(
                        startProcessingIndicators,
                        elementData.getIndicators());
                elementData.setIndicators(combinedIndicators);
            });
        }
    }

    private void initialise(final PipelineStepRequest request) {
        if (!taskContext.isTerminated()) {
            final StepType stepType = request.getStepType();
            currentLocation = request.getStepLocation();

            // If we are just refreshing then we are just going to do what we
            // did before.
            if (StepType.REFRESH.equals(stepType)) {
                return;
            }

            final FindMetaCriteria criteria = request.getCriteria();
            final List<Long> streamIdList = getFilteredStreamIdList(criteria);
            currentStreamIndex = -1;

            if (!streamIdList.isEmpty()) {
                if (StepType.FIRST.equals(stepType)) {
                    // If we are trying to find the first record then start with
                    // the first stream, first stream no, first record.
                    currentStreamIndex = 0;
                    final long id = getMetaIdAtIndex(currentStreamIndex);
                    currentLocation = StepLocation.first(id);

                } else if (StepType.LAST.equals(stepType)) {
                    // If we are trying to find the last record then start with
                    // the last stream, last stream no, last record.
                    currentStreamIndex = streamIdList.size() - 1;
                    final long id = getMetaIdAtIndex(currentStreamIndex);
                    currentLocation = StepLocation.last(id);

                } else if (currentLocation != null) {
                    // For all other step types we should have an existing
                    // stream index.
                    currentStreamIndex = streamIdList.indexOf(currentLocation.getMetaId());

                    // [Optimisation] If we are moving backward and are at the
                    // beginning of a stream then move to the previous stream.
                    if (StepType.BACKWARD.equals(stepType)
                        && currentStreamIndex != -1
                        && currentLocation.getPartIndex() <= 0
                        && currentLocation.getRecordIndex() <= 0) {
                        currentStreamIndex--;

                        // If there are no more streams then we are at the
                        // beginning.
                        if (currentStreamIndex >= 0) {
                            // Move to the end of this stream.
                            final long id = getMetaIdAtIndex(currentStreamIndex);
                            currentLocation = StepLocation.last(id);
                        }
                    }
                }

                if (StepType.FORWARD.equals(stepType) && currentStreamIndex == -1) {
                    // If we couldn't find a stream index then at least allow
                    // forward to start at the beginning.
                    currentStreamIndex = 0;
                    final long id = getMetaIdAtIndex(currentStreamIndex);
                    currentLocation = StepLocation.first(id);
                }
            }
        }
    }

    private void process(final PipelineStepRequest request, final Long metaId) {
        if (!taskContext.isTerminated()) {
            final StepType stepType = request.getStepType();

            if (metaId != null && !metaId.equals(lastStreamId)) {
                // Stop the process from running in circles, this can happen if
                // refresh is used.
                lastStreamId = metaId;

                // If we have changed stream and are moving forward of backward
                // then we need to change the request.
                if (currentLocation != null && metaId != currentLocation.getMetaId()) {
                    if (StepType.FORWARD.equals(stepType)) {
                        // If we haven't got a position or are moving forward
                        // and the stream id has changed then keep look from the
                        // start of the returned stream.
                        currentLocation = StepLocation.first(metaId);

                    } else if (StepType.BACKWARD.equals(stepType)) {
                        // If we haven't got a position or are moving backward
                        // and the stream id has changed then keep looking for a
                        // match until we reach the end of the stream
                        // (Long.MAX_VALUE)
                        currentLocation = StepLocation.last(metaId);
                    }
                }

                // Get the appropriate stream and source based on the type of
                // translation.
                taskContext.info(() -> "Opening source. stream_id=" + metaId);
                try (final Source source = streamStore.openSource(metaId)) {
                    if (source != null) {
                        // Load the feed.
                        final String feedName = source.getMeta().getFeedName();

                        // Get the stream type.
                        final String childStreamTypeName = request.getChildStreamType();

                        // Now process the data.
                        processStream(controller, feedName, childStreamTypeName, source);

                        if (controller.isFound()) {
                            // Set the offset in the task list where we will be able to find this task. This will
                            // enable us to show the right stream list page.
                            if (allMetaIdList != null) {
                                curentStreamOffset = allMetaIdList.indexOf(metaId);
                            }
                        } else {
                            // If we didn't find what we were looking for then process the next stream.
                            switch (stepType) {
                                case FIRST:
                                    currentStreamIndex++;
                                    break;
                                case FORWARD:
                                    currentStreamIndex++;
                                    break;
                                case BACKWARD:
                                    currentStreamIndex--;
                                    break;
                                case LAST:
                                    currentStreamIndex--;
                                    break;
                            }

                            final Long nextMetaId = getMetaId(request);
                            process(request, nextMetaId);
                        }
                    }
                } catch (final IOException e) {
                    error(e);
                }

            } else {
                // If we didn't find any stream then set the current record
                // number back to what it was when this request was made. This
                // is important when we are moving backwards and have set the
                // current record number to StepLocation.last()
                currentLocation = request.getStepLocation();
            }
        }
    }

    private Long getMetaId(final PipelineStepRequest request) {
        if (!taskContext.isTerminated()) {
            final StepType stepType = request.getStepType();
            // If we are just refreshing then just return the same task we used
            // before.
            if (StepType.REFRESH.equals(stepType)) {
                if (currentLocation == null) {
                    return null;
                }

                return currentLocation.getMetaId();
            }

            // Return the task at the current index or null if the index is out
            // of bounds.
            final long metaId = getMetaIdAtIndex(currentStreamIndex);
            if (metaId != -1) {
                return metaId;
            }
        }

        return null;
    }

    private List<Long> getFilteredStreamIdList(final FindMetaCriteria criteria) {
        // Query the DB to get a list of tasks and associated streams to get
        // the source data from. Put the results into an array for use
        // during this request.
        if (filteredMetaIdList == null) {
            final List<Long> filteredList;

//            if (criteria.getSelectedIdSet() == null
//            || Boolean.TRUE.equals(criteria.getSelectedIdSet().getMatchAll())) {
//                // Don't get back more than 1000 streams or we might run out of
//                // memory.
//                criteria.obtainPageRequest().setOffset(0L);
//                criteria.obtainPageRequest().setLength(1000);
//            }

            // Find streams.
            final List<Meta> allStreamList = metaService.find(criteria).getValues();
            allMetaIdList = new ArrayList<>(allStreamList.size());
            for (final Meta meta : allStreamList) {
                allMetaIdList.add(meta.getId());
            }

//            if (criteria.getSelectedIdSet() == null
//            || Boolean.TRUE.equals(criteria.getSelectedIdSet().getMatchAll())) {
            // If we are including all tasks then don't filter the list.
            filteredList = new ArrayList<>(allStreamList.size());
            for (final Meta meta : allStreamList) {
                filteredList.add(meta.getId());
            }

//            }
//            else if (criteria.getSelectedIdSet() != null && criteria.getSelectedIdSet().getSet() != null
//                    && criteria.getSelectedIdSet().getSet().size() > 0) {
//                // Otherwise filter the list to just selected tasks.
//                filteredList = new ArrayList<>(criteria.getSelectedIdSet().getSet().size());
//                for (final Meta meta : allStreamList) {
//                    if (criteria.getSelectedIdSet().isMatch(meta.getId())) {
//                        filteredList.add(meta.getId());
//                    }
//                }
//            }

            filteredMetaIdList = filteredList;
        }

        return filteredMetaIdList;
    }

    private long getMetaIdAtIndex(final int index) {
        if (index < 0) {
            return -1;
        }

        if (index >= filteredMetaIdList.size()) {
            return -1;
        }

        return filteredMetaIdList.get(index);
    }

    private void processStream(final SteppingController controller,
                               final String feedName,
                               final String streamTypeName,
                               final Source source) {
        // If the feed changes then destroy the last pipeline.
        if (lastFeedName != null && !lastFeedName.equals(feedName)) {
            // destroy the last pipeline.
            try {
                pipeline.endProcessing();
            } catch (final LoggedException e) {
                // Do nothing as we will have recorded this error in the
                // logging error receiver.
            }
            lastFeedName = null;
        }

        startProcessIndicatorMap.clear();
        if (!taskContext.isTerminated()) {
            // Create a new pipeline for a new feed or if the feed has changed.
            if (lastFeedName == null) {
                lastFeedName = feedName;

                // Create the pipeline.
                createPipeline(controller, feedName);

                if (pipeline != null) {
                    try {
                        pipeline.startProcessing();

                        // Capture and hold any errors seen during init of the pipe as the error receiver
                        // will get cleared on each step.
                        startProcessIndicatorMap = getErrorReceiverIndicatorsMap();
                    } catch (final LoggedException e) {
                        // Do nothing as we will have recorded this error in the
                        // logging error receiver.
                    }
                }
            }

            // Make sure we have had no errors before we start processing.
            if (pipeline != null && loggingErrorReceiver.isAllOk()) {
                // Process the stream.
                process(controller, feedName, streamTypeName, source);
            }
        }
    }

    private void process(final SteppingController controller,
                         final String feedName,
                         final String childDataType,
                         final Source source) {
        final Meta meta = source.getMeta();
        final PipelineStepRequest request = controller.getRequest();
        final StepType stepType = request.getStepType();
        controller.setStreamInfo(createStreamInfo(feedName, meta));

        // Set the source meta.
        metaHolder.setMeta(meta);
        metaHolder.setChildDataType(childDataType);

        try {
            final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
            locationFactory.setLocationFactory(streamLocationFactory);

            // Determine which stream number to start with.
            final long maxPartIndex = source.count() - 1;
            long partIndex = 0;
            if (currentLocation != null) {
                // If stream no has been set beyond the last stream no then
                // start at the end.
                if (currentLocation.getPartIndex() > maxPartIndex) {
                    // Start at the last stream number.
                    partIndex = maxPartIndex;
                    // Update the current processing location.
                    currentLocation = new StepLocation(
                            meta.getId(), partIndex, currentLocation.getRecordIndex());
                } else {
                    // Else start at the current location.
                    partIndex = currentLocation.getPartIndex();
                    // Update the current processing location.
                    currentLocation = new StepLocation(
                            meta.getId(), partIndex, currentLocation.getRecordIndex());
                }
            }

            // Get the appropriate encoding for the stream type.
            final String encoding = feedProperties.getEncoding(
                    feedName, meta.getTypeName(), childDataType);

            // Loop over the stream boundaries and process
            // each sequentially until we find a record.
            boolean done = controller.isFound();
            while (!done
                   && partIndex >= 0
                   && partIndex <= maxPartIndex
                   && !taskContext.isTerminated()) {
                // Set the stream number.
                metaHolder.setPartIndex(partIndex);
                streamLocationFactory.setPartIndex(partIndex);

                // Process the boundary making sure to use the right
                // encoding.
                controller.clearAllFilters(null);

                // Get the stream.
                try (final InputStreamProvider inputStreamProvider = source.get(partIndex)) {
                    metaHolder.setInputStreamProvider(inputStreamProvider);
                    final SegmentInputStream inputStream = inputStreamProvider.get(childDataType);

                    // Get the segment count so we can determine if this is raw/segmented
                    isSegmentedData = inputStream.count() > 1;

                    // Process the boundary.
                    try {
                        if (inputStream.size() > 0) {
                            controller.setIsSegmentedData(isSegmentedData);
                            controller.setStepLocation(currentLocation);
                            pipeline.process(inputStream, encoding);
                        }

                        // Are we done?
                        if (StepType.REFRESH.equals(stepType)) {
                            done = true;
                        } else {
                            done = controller.isFound();
                        }
                    } catch (final LoggedException e) {
                        // Do nothing as we will have recorded this error in the
                        // logging error receiver.
                        done = true;
                    } catch (final RuntimeException e) {
                        error(e);
                        done = true;
                    }

                    // Do we need to keep looking?
                    if (!done) {
                        // If we are stepping forward increment the stream
                        // number, otherwise decrement the stream number.
                        if (StepType.FIRST.equals(stepType)) {
                            partIndex++;
                            currentLocation = StepLocation.first(meta.getId(), partIndex);
                        } else if (StepType.BACKWARD.equals(stepType)) {
                            partIndex--;
                            currentLocation = StepLocation.last(meta.getId(), partIndex);
                        } else if (StepType.FORWARD.equals(stepType)) {
                            partIndex++;
                            currentLocation = StepLocation.first(meta.getId(), partIndex);
                        } else if (StepType.LAST.equals(stepType)) {
                            partIndex--;
                            currentLocation = StepLocation.last(meta.getId(), partIndex);
                        }
                    }
                } catch (final IOException | RuntimeException e) {
                    error(e);
                }
            }
        } catch (final IOException | RuntimeException e) {
            error(e);
        }
    }

    private Pipeline createPipeline(final SteppingController controller, final String feedName) {
        if (pipeline == null) {
            final DocRef pipelineRef = controller.getRequest().getPipeline();

            // Set the pipeline so it can be used by a filter if needed.
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

            feedHolder.setFeedName(feedName);

            // Setup the meta data holder.
            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

            pipelineHolder.setPipeline(DocRefUtil.create(pipelineDoc));
            pipelineContext.setStepping(true);

            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
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
        return pipeline;
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

    public Instant getCreateTime() {
        return createTime;
    }

    public Instant getLastRequestTime() {
        return lastRequestTime;
    }

    public TaskContext getTaskContext() {
        return taskContext;
    }
}
