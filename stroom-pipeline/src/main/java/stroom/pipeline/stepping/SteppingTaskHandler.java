/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.pipeline.stepping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.io.StreamCloser;
import stroom.pipeline.EncodingSelection;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineService;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.shared.StepType;
import stroom.pipeline.shared.SteppingResult;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.security.Security;
import stroom.security.UserTokenUtil;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.StreamSource;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTypeService;
import stroom.streamstore.fs.serializable.NestedInputStream;
import stroom.streamstore.fs.serializable.RANestedInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskContext;
import stroom.task.TaskHandlerBean;
import stroom.util.date.DateUtil;
import stroom.util.shared.Highlight;

import javax.inject.Inject;
import javax.inject.Named;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TaskHandlerBean(task = SteppingTask.class)
class SteppingTaskHandler extends AbstractTaskHandler<SteppingTask, SteppingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SteppingTaskHandler.class);

    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactory.newInstance();
        PARSER_FACTORY.setNamespaceAware(true);
    }

    private final StreamStore streamStore;
    private final StreamCloser streamCloser;
    private final FeedService feedService;
    private final StreamTypeService streamTypeService;
    private final TaskContext taskContext;
    private final FeedHolder feedHolder;
    private final PipelineHolder pipelineHolder;
    private final StreamHolder streamHolder;
    private final LocationFactoryProxy locationFactory;
    private final CurrentUserHolder currentUserHolder;
    private final SteppingController controller;
    private final PipelineService pipelineService;
    private final PipelineFactory pipelineFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final SteppingResponseCache steppingResponseCache;
    private final PipelineDataCache pipelineDataCache;
    private final PipelineContext pipelineContext;
    private final Security security;

    private List<Long> allStreamIdList;
    private List<Long> filteredStreamIdList;
    private int currentStreamIndex = -1;
    private int curentStreamOffset;
    private StepLocation currentLocation;
    private Long lastStreamId;
    private Feed lastFeed;
    private Pipeline pipeline;
    private LoggingErrorReceiver loggingErrorReceiver;
    private Set<String> generalErrors;

    @Inject
    SteppingTaskHandler(final StreamStore streamStore,
                        final StreamCloser streamCloser,
                        final FeedService feedService,
                        @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                        final TaskContext taskContext,
                        final FeedHolder feedHolder,
                        final PipelineHolder pipelineHolder,
                        final StreamHolder streamHolder,
                        final LocationFactoryProxy locationFactory,
                        final CurrentUserHolder currentUserHolder,
                        final SteppingController controller,
                        final PipelineService pipelineService,
                        final PipelineFactory pipelineFactory,
                        final ErrorReceiverProxy errorReceiverProxy,
                        final SteppingResponseCache steppingResponseCache,
                        final PipelineDataCache pipelineDataCache,
                        final PipelineContext pipelineContext,
                        final Security security) {
        this.streamStore = streamStore;
        this.streamCloser = streamCloser;
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
        this.taskContext = taskContext;
        this.feedHolder = feedHolder;
        this.pipelineHolder = pipelineHolder;
        this.streamHolder = streamHolder;
        this.locationFactory = locationFactory;
        this.currentUserHolder = currentUserHolder;
        this.controller = controller;
        this.pipelineService = pipelineService;
        this.pipelineFactory = pipelineFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.steppingResponseCache = steppingResponseCache;
        this.pipelineDataCache = pipelineDataCache;
        this.pipelineContext = pipelineContext;
        this.security = security;
    }

    @Override
    public SteppingResult exec(final SteppingTask request) {
        return security.secureResult(PermissionNames.STEPPING_PERMISSION, () -> {
            // Elevate user permissions so that inherited pipelines that the user only has 'Use' permission on can be read.
            return security.useAsReadResult(() -> {
                // Set the current user so they are visible during translation.
                currentUserHolder.setCurrentUser(UserTokenUtil.getUserId(request.getUserToken()));

                StepData stepData = null;
                generalErrors = new HashSet<>();

                loggingErrorReceiver = new LoggingErrorReceiver();
                errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

                // Set the controller for the pipeline.
                controller.setRequest(request);
                controller.setTaskMonitor(taskContext);

                try {
                    // Initialise the process by finding streams to process and setting
                    // the step location.
                    initialise(request);

                    // Get the first stream to try and process.
                    final Long streamId = getStreamId(request);

                    // Start processing.
                    process(request, streamId);
                } catch (final ProcessException e) {
                    error(e);
                }

                // Make sure all resources are returned to pools.
                if (lastFeed != null) {
                    // destroy the last pipeline.
                    pipeline.endProcessing();
                    lastFeed = null;
                }

                // Set the output.
                if (controller.getLastFoundLocation() != null) {
                    currentLocation = controller.getLastFoundLocation();

                    // FIXME : Sort out use of response cache so we don't run out of
                    // memory.
                    stepData = steppingResponseCache.getStepData(currentLocation);

                    // Fill in the source data if it hasn't been already.
                    for (final ElementData elementData : stepData.getElementMap().values()) {
                        if (elementData.getElementType().hasRole(PipelineElementType.ROLE_PARSER)
                                && elementData.getInput() == null) {
                            final String data = getSourceData(currentLocation, stepData.getSourceHighlights());
                            elementData.setInput(data);
                        }
                    }

                } else {
                    // Pick up any step data that remains so we can deliver any errors
                    // that caused the system not to step.
                    stepData = new StepData();
                    controller.storeStepData(stepData);
                }

                return new SteppingResult(request.getStepFilterMap(), currentLocation, stepData.convertToShared(),
                        curentStreamOffset, controller.isFound(), generalErrors);
            });
        });
    }

    private void initialise(final SteppingTask request) {
        if (!taskContext.isTerminated()) {
            final StepType stepType = request.getStepType();
            currentLocation = request.getStepLocation();

            // If we are just refreshing then we are just going to do what we
            // did before.
            if (StepType.REFRESH.equals(stepType)) {
                return;
            }

            final FindStreamCriteria criteria = request.getCriteria();
            final List<Long> streamIdList = getFilteredStreamIdList(criteria);
            currentStreamIndex = -1;

            if (streamIdList.size() > 0) {
                if (StepType.FIRST.equals(stepType)) {
                    // If we are trying to find the first record then start with
                    // the first stream, first stream no, first record.
                    currentStreamIndex = 0;
                    final long id = getStreamIdAtIndex(currentStreamIndex);
                    currentLocation = new StepLocation(id, 1, 0);

                } else if (StepType.LAST.equals(stepType)) {
                    // If we are trying to find the last record then start with
                    // the last stream, last stream no, last record.
                    currentStreamIndex = streamIdList.size() - 1;
                    final long id = getStreamIdAtIndex(currentStreamIndex);
                    currentLocation = new StepLocation(id, Long.MAX_VALUE, Long.MAX_VALUE);

                } else if (currentLocation != null) {
                    // For all other step types we should have an existing
                    // stream index.
                    currentStreamIndex = streamIdList.indexOf(currentLocation.getStreamId());

                    // [Optimisation] If we are moving backward and are at the
                    // beginning of a stream then move to the previous stream.
                    if (StepType.BACKWARD.equals(stepType) && currentStreamIndex != -1
                            && currentLocation.getStreamNo() <= 1 && currentLocation.getRecordNo() <= 1) {
                        currentStreamIndex--;

                        // If there are no more streams then we are at the
                        // beginning.
                        if (currentStreamIndex >= 0) {
                            // Move to the end of this stream.
                            final long id = getStreamIdAtIndex(currentStreamIndex);
                            currentLocation = new StepLocation(id, Long.MAX_VALUE, Long.MAX_VALUE);
                        }
                    }
                }

                if (StepType.FORWARD.equals(stepType) && currentStreamIndex == -1) {
                    // If we couldn't find a stream index then at least allow
                    // forward to start at the beginning.
                    currentStreamIndex = 0;
                    final long id = getStreamIdAtIndex(currentStreamIndex);
                    currentLocation = new StepLocation(id, 1, 0);
                }
            }
        }
    }

    private void process(final SteppingTask request, final Long streamId) {
        if (!taskContext.isTerminated()) {
            final StepType stepType = request.getStepType();

            if (streamId != null && !streamId.equals(lastStreamId)) {
                // Stop the process from running in circles, this can happen if
                // refresh is used.
                lastStreamId = streamId;

                // If we have changed stream and are moving forward of backward
                // then we need to change the request.
                if (currentLocation != null && streamId != currentLocation.getStreamId()) {
                    if (StepType.FORWARD.equals(stepType)) {
                        // If we haven't got a position or are moving forward
                        // and the stream id has changed then keep look from the
                        // start of the returned stream.
                        currentLocation = new StepLocation(streamId, 1, 0);

                    } else if (StepType.BACKWARD.equals(stepType)) {
                        // If we haven't got a position or are moving backward
                        // and the stream id has changed then keep looking for a
                        // match until we reach the end of the stream
                        // (Long.MAX_VALUE)
                        currentLocation = new StepLocation(streamId, Long.MAX_VALUE, Long.MAX_VALUE);
                    }
                }

                // Get the appropriate stream and source based on the type of
                // translation.
                final StreamSource streamSource = streamStore.openStreamSource(streamId);
                if (streamSource != null) {
                    StreamSource stepSource = streamSource;
                    if (StreamType.CONTEXT.equals(request.getChildStreamType())) {
                        stepSource = streamSource.getChildStream(StreamType.CONTEXT);
                    }

                    // Load the feed.
                    final Feed feed = feedService.load(streamSource.getStream().getFeed());

                    // Get the stream type.
                    final StreamType streamType = streamTypeService.load(stepSource.getType());

                    // Now process the data.
                    processStream(controller, feed, streamType, stepSource);

                    try {
                        // Close all open streams.
                        streamCloser.close();
                    } catch (final IOException e) {
                        error(e);

                    } finally {
                        // Close the stream source.
                        if (streamSource != null) {
                            try {
                                streamStore.closeStreamSource(streamSource);
                            } catch (final RuntimeException e) {
                                error(e);
                            }
                        }
                    }

                    if (controller.isFound()) {
                        // Set the offset in the task list where we will be able
                        // to
                        // find this task. This will enable us to show the right
                        // stream list page.
                        if (allStreamIdList != null) {
                            curentStreamOffset = allStreamIdList.indexOf(streamId);
                        }
                    } else {
                        // If we didn't find what we were looking for then
                        // process
                        // the next stream.
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

                        final Long nextStream = getStreamId(request);
                        process(request, nextStream);
                    }
                }

            } else {
                // If we didn't find any stream then set the current record
                // number back to what it was when this request was made. This
                // is important when we are moving backwards and have set the
                // current record number to Long.MAX_VALUE.
                currentLocation = request.getStepLocation();
            }
        }
    }

    private Long getStreamId(final SteppingTask request) {
        if (!taskContext.isTerminated()) {
            final StepType stepType = request.getStepType();
            // If we are just refreshing then just return the same task we used
            // before.
            if (StepType.REFRESH.equals(stepType)) {
                if (currentLocation == null) {
                    return null;
                }

                return currentLocation.getStreamId();
            }

            // Return the task at the current index or null if the index is out
            // of bounds.
            final Long streamId = getStreamIdAtIndex(currentStreamIndex);
            if (streamId != null) {
                return streamId;
            }
        }

        return null;
    }

    private List<Long> getFilteredStreamIdList(final FindStreamCriteria criteria) {
        // Query the DB to get a list of tasks and associated streams to get
        // the source data from. Put the results into an array for use
        // during this request.
        if (filteredStreamIdList == null) {
            List<Long> filteredList = Collections.emptyList();

            if (criteria.getSelectedIdSet() == null || Boolean.TRUE.equals(criteria.getSelectedIdSet().getMatchAll())) {
                // Don't get back more than 1000 streams or we might run out of
                // memory.
                criteria.obtainPageRequest().setOffset(0L);
                criteria.obtainPageRequest().setLength(1000);
            }

            criteria.getFetchSet().add(StreamType.ENTITY_TYPE);

            // Find streams.
            final List<Stream> allStreamList = streamStore.find(criteria);
            allStreamIdList = new ArrayList<>(allStreamList.size());
            for (final Stream stream : allStreamList) {
                allStreamIdList.add(stream.getId());
            }

            if (criteria.getSelectedIdSet() == null || Boolean.TRUE.equals(criteria.getSelectedIdSet().getMatchAll())) {
                // If we are including all tasks then don't filter the list.
                filteredList = new ArrayList<>(allStreamList.size());
                for (final Stream stream : allStreamList) {
                    filteredList.add(stream.getId());
                }

            } else if (criteria.getSelectedIdSet() != null && criteria.getSelectedIdSet().getSet() != null
                    && criteria.getSelectedIdSet().getSet().size() > 0) {
                // Otherwise filter the list to just selected tasks.
                filteredList = new ArrayList<>(criteria.getSelectedIdSet().getSet().size());
                for (final Stream stream : allStreamList) {
                    if (criteria.getSelectedIdSet().isMatch(stream.getId())) {
                        filteredList.add(stream.getId());
                    }
                }
            }

            filteredStreamIdList = filteredList;
        }

        return filteredStreamIdList;
    }

    private Long getStreamIdAtIndex(final int index) {
        if (index < 0) {
            return null;
        }

        if (index >= filteredStreamIdList.size()) {
            return null;
        }

        return filteredStreamIdList.get(index);
    }

    private void processStream(final SteppingController controller, final Feed feed, final StreamType streamType,
                               final StreamSource source) {
        // If the feed changes then destroy the last pipeline.
        if (lastFeed != null && !lastFeed.equals(feed)) {
            // destroy the last pipeline.
            try {
                pipeline.endProcessing();
            } catch (final LoggedException e) {
                // Do nothing as we will have recorded this error in the
                // logging error receiver.
            }
            lastFeed = null;
        }

        if (!taskContext.isTerminated()) {
            // Create a new pipeline for a new feed or if the feed has changed.
            if (lastFeed == null) {
                lastFeed = feed;

                // Create the pipeline.
                createPipeline(controller, feed);

                if (pipeline != null) {
                    try {
                        pipeline.startProcessing();
                    } catch (final LoggedException e) {
                        // Do nothing as we will have recorded this error in the
                        // logging error receiver.
                    }
                }
            }

            // Make sure we have had no errors before we start processing.
            if (pipeline != null && loggingErrorReceiver.isAllOk()) {
                // Process the stream.
                process(controller, feed, streamType, source);
            }
        }
    }

    private void process(final SteppingController controller, final Feed feed, final StreamType streamType,
                         final StreamSource streamSource) {
        try {
            final Stream stream = streamSource.getStream();
            final SteppingTask request = controller.getRequest();
            final StepType stepType = request.getStepType();
            controller.setStreamInfo(createStreamInfo(feed, stream));

            // Get the stream providers.
            streamHolder.setStream(stream);
            streamHolder.addProvider(streamSource);
            streamHolder.addProvider(streamSource.getChildStream(StreamType.META));
            streamHolder.addProvider(streamSource.getChildStream(StreamType.CONTEXT));

            // Get the main stream provider.
            final StreamSourceInputStreamProvider mainProvider = streamHolder.getProvider(streamSource.getType());

            try {
                final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
                locationFactory.setLocationFactory(streamLocationFactory);

                // Determine which stream number to start with.
                final long streamCount = mainProvider.getStreamCount();
                long streamNo = 1;
                if (currentLocation != null) {
                    // If stream no has been set beyond the last stream no then
                    // start at the end.
                    if (currentLocation.getStreamNo() > streamCount) {
                        // Start at the last stream number.
                        streamNo = streamCount;
                        // Update the current processing location.
                        currentLocation = new StepLocation(stream.getId(), streamNo, currentLocation.getRecordNo());
                    } else {
                        // Else start at the current location.
                        streamNo = currentLocation.getStreamNo();
                        // Update the current processing location.
                        currentLocation = new StepLocation(stream.getId(), streamNo, currentLocation.getRecordNo());
                    }
                }

                // Get the appropriate encoding for the stream type.
                final String encoding = EncodingSelection.select(feed, streamType);

                // Loop over the stream boundaries and process each
                // sequentially. Loop over the stream boundaries and process
                // each sequentially until we find a record.
                boolean done = controller.isFound();
                while (!done && streamNo > 0 && streamNo <= streamCount && !taskContext.isTerminated()) {
                    // Set the stream number.
                    streamHolder.setStreamNo(streamNo - 1);
                    streamLocationFactory.setStreamNo(streamNo);

                    // Process the boundary making sure to use the right
                    // encoding.
                    controller.clearAllFilters();

                    // Get the stream.
                    final StreamSourceInputStream inputStream = mainProvider.getStream(streamNo - 1);

                    // Process the boundary.
                    try {
                        if (inputStream.size() > 0) {
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
                            streamNo++;
                            currentLocation = new StepLocation(stream.getId(), streamNo, 0);
                        } else if (StepType.BACKWARD.equals(stepType)) {
                            streamNo--;
                            currentLocation = new StepLocation(stream.getId(), streamNo, Long.MAX_VALUE);
                        } else if (StepType.FORWARD.equals(stepType)) {
                            streamNo++;
                            currentLocation = new StepLocation(stream.getId(), streamNo, 0);
                        } else if (StepType.LAST.equals(stepType)) {
                            streamNo--;
                            currentLocation = new StepLocation(stream.getId(), streamNo, Long.MAX_VALUE);
                        }
                    }
                }
            } catch (final SAXException | IOException | RuntimeException e) {
                error(e);
            }
        } catch (final IOException | RuntimeException e) {
            error(e);
        }
    }

    private Pipeline createPipeline(final SteppingController controller, final Feed feed) {
        if (pipeline == null) {
            // Set the pipeline so it can be used by a filter if needed.
            final PipelineEntity pipelineEntity = pipelineService
                    .loadByUuid(controller.getRequest().getPipeline().getUuid());

            feedHolder.setFeed(feed);
            pipelineHolder.setPipeline(pipelineEntity);
            pipelineContext.setStepping(true);

            final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
            pipeline = pipelineFactory.create(pipelineData, controller);

            // Don't return a pipeline if we cannot step with it.
            if (pipeline == null || controller.getRecordDetector() == null || controller.getMonitors() == null
                    || controller.getMonitors().size() == 0) {
                throw new ProcessException(
                        "You cannot step with this pipeline as it does not contain required elements.");
            }
        }

        return pipeline;
    }

    private String createStreamInfo(final Feed feed, final Stream stream) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Feed: ");
        sb.append(feed.getName());
        sb.append(" Received: ");
        sb.append(DateUtil.createNormalDateTimeString(stream.getCreateMs()));
        sb.append(" [");
        sb.append(stream.getId());
        return sb.toString();
    }

    private String getSourceData(final StepLocation location, final List<Highlight> highlights) {
        String data = null;
        if (location != null && highlights != null && highlights.size() > 0) {
            try {
                final StreamSource streamSource = streamStore.openStreamSource(location.getStreamId());
                if (streamSource != null) {
                    final NestedInputStream inputStream = new RANestedInputStream(streamSource);

                    try {
                        // Skip to the appropriate stream.
                        if (inputStream.getEntry(location.getStreamNo() - 1)) {
                            // Load the feed.
                            final Feed feed = feedService.load(streamSource.getStream().getFeed());

                            // Get the stream type.
                            final StreamType streamType = streamTypeService.load(streamSource.getType());

                            // Get the appropriate encoding for the stream type.
                            final String encoding = EncodingSelection.select(feed, streamType);

                            final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, encoding);
                            final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                            final StringBuilder sb = new StringBuilder();

                            int i = 0;
                            boolean found = false;
                            int lineNo = 1;
                            int colNo = 0;
                            boolean inRecord = false;

                            while ((i = bufferedReader.read()) != -1 && !found) {
                                final char c = (char) i;

                                if (c == '\n') {
                                    lineNo++;
                                    colNo = 0;
                                } else {
                                    colNo++;
                                }

                                for (final Highlight highlight : highlights) {
                                    if (!inRecord) {
                                        if (lineNo > highlight.getLineFrom() || (lineNo >= highlight.getLineFrom()
                                                && colNo >= highlight.getColFrom())) {
                                            inRecord = true;
                                            break;
                                        }
                                    } else if (lineNo > highlight.getLineTo()
                                            || (lineNo >= highlight.getLineTo() && colNo >= highlight.getColTo())) {
                                        inRecord = false;
                                        found = true;
                                        break;
                                    }
                                }

                                if (inRecord) {
                                    sb.append(c);
                                }
                            }

                            inputStream.closeEntry();
                            bufferedReader.close();

                            data = sb.toString();
                        }
                    } finally {
                        try {
                            inputStream.close();
                        } finally {
                            streamStore.closeStreamSource(streamSource);
                        }
                    }
                }
            } catch (final IOException e) {
                error(e);
            }
        }

        return data;
    }

    private void error(final Exception e) {
        LOGGER.debug(e.getMessage(), e);

        if (e.getMessage() == null || e.getMessage().trim().length() == 0) {
            generalErrors.add(e.toString());
        } else {
            generalErrors.add(e.getMessage());
        }
    }
}
