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

package stroom.core.dataprocess;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.pipeline.DefaultErrorWriter;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.AbstractElement;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.pipeline.task.ProcessStatisticsFactory;
import stroom.pipeline.task.ProcessStatisticsFactory.ProcessStatistics;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.api.InclusiveRanges.InclusiveRange;
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorResultImpl;
import stroom.processor.api.ProcessorTaskExecutor;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.io.PreviewInputStream;
import stroom.util.io.WrappedOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ElementId;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import com.google.common.collect.ImmutableSortedMap;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractProcessorTaskExecutor implements ProcessorTaskExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractProcessorTaskExecutor.class);
    private static final String PROCESSING = "Processing:";
    private static final String FINISHED = "Finished:";
    private static final int PREVIEW_SIZE = 100;
    private static final int MIN_STREAM_SIZE = 1;
    private static final Pattern XML_DECL_PATTERN = Pattern.compile(
            "<\\?\\s*xml[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final ElementId ELEMENT_ID = new ElementId("PipelineStreamProcessor");

    private final PipelineFactory pipelineFactory;
    private final Store streamStore;
    private final PipelineStore pipelineStore;
    private final MetaService metaService;
    private final ProcessorTaskService processorTaskService;
    private final PipelineHolder pipelineHolder;
    private final FeedHolder feedHolder;
    private final FeedProperties feedProperties;
    private final MetaDataHolder metaDataHolder;
    private final MetaHolder metaHolder;
    private final SearchIdHolder searchIdHolder;
    private final LocationFactoryProxy locationFactory;
    private final StreamProcessorHolder streamProcessorHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final ErrorWriterProxy errorWriterProxy;
    private final MetaData metaData;
    private final RecordCount recordCount;
    private final RecordErrorReceiver recordErrorReceiver;
    private final NodeInfo nodeInfo;
    private final PipelineDataCache pipelineDataCache;
    private final InternalStatisticsReceiver internalStatisticsReceiver;
    private final VolumeGroupNameProvider volumeGroupNameProvider;

    private Processor processor;
    private ProcessorFilter processorFilter;
    private ProcessorTask processorTask;
    private Source streamSource;

    private long startTime;

    protected AbstractProcessorTaskExecutor(final PipelineFactory pipelineFactory,
                                            final Store store,
                                            final PipelineStore pipelineStore,
                                            final MetaService metaService,
                                            final ProcessorTaskService processorTaskService,
                                            final PipelineHolder pipelineHolder,
                                            final FeedHolder feedHolder,
                                            final FeedProperties feedProperties,
                                            final MetaDataHolder metaDataHolder,
                                            final MetaHolder metaHolder,
                                            final SearchIdHolder searchIdHolder,
                                            final LocationFactoryProxy locationFactory,
                                            final StreamProcessorHolder streamProcessorHolder,
                                            final ErrorReceiverProxy errorReceiverProxy,
                                            final ErrorWriterProxy errorWriterProxy,
                                            final MetaData metaData,
                                            final RecordCount recordCount,
                                            final RecordErrorReceiver recordErrorReceiver,
                                            final NodeInfo nodeInfo,
                                            final PipelineDataCache pipelineDataCache,
                                            final InternalStatisticsReceiver internalStatisticsReceiver,
                                            final VolumeGroupNameProvider volumeGroupNameProvider) {
        this.pipelineFactory = pipelineFactory;
        this.streamStore = store;
        this.pipelineStore = pipelineStore;
        this.metaService = metaService;
        this.processorTaskService = processorTaskService;
        this.pipelineHolder = pipelineHolder;
        this.feedHolder = feedHolder;
        this.feedProperties = feedProperties;
        this.metaDataHolder = metaDataHolder;
        this.metaHolder = metaHolder;
        this.searchIdHolder = searchIdHolder;
        this.locationFactory = locationFactory;
        this.streamProcessorHolder = streamProcessorHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.errorWriterProxy = errorWriterProxy;
        this.metaData = metaData;
        this.recordCount = recordCount;
        this.recordErrorReceiver = recordErrorReceiver;
        this.nodeInfo = nodeInfo;
        this.pipelineDataCache = pipelineDataCache;
        this.internalStatisticsReceiver = internalStatisticsReceiver;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
    }

    @Override
    public ProcessorResult exec(final TaskContext taskContext,
                                final Processor processor,
                                final ProcessorFilter processorFilter,
                                final ProcessorTask processorTask,
                                final Source streamSource) {
        this.processor = processor;
        this.processorFilter = processorFilter;
        this.processorTask = processorTask;
        this.streamSource = streamSource;

        // Record when processing began so we know how long it took afterwards.
        startTime = System.currentTimeMillis();

        // Setup the error handler and receiver.
        errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

        final Meta meta = streamSource.getMeta();
        final String errorFeedName = meta.getFeedName();

        // Setup the process info writer.
        try (final ProcessInfoOutputStreamProvider processInfoOutputStreamProvider =
                new ProcessInfoOutputStreamProvider(
                        streamStore,
                        metaData,
                        meta,
                        errorFeedName,
                        processor,
                        processorFilter,
                        processorTask,
                        recordCount,
                        errorReceiverProxy,
                        volumeGroupNameProvider)) {
            ProcessorTaskDecorator processDecorator = null;
            try {
                final DefaultErrorWriter errorWriter = new DefaultErrorWriter();
                errorWriter.addOutputStreamProvider(processInfoOutputStreamProvider);
                errorWriterProxy.setErrorWriter(errorWriter);

                processDecorator = getProcessDecorator();
                processDecorator.beforeProcessing(processorFilter);

                process(taskContext);

            } catch (final Exception e) {
                outputFatalError(e);
            } finally {
                try {
                    if (processDecorator != null) {
                        processDecorator.afterProcessing();
                    }
                } catch (final Exception e) {
                    outputFatalError(e);
                }

                // Ensure we are no longer interrupting if necessary.
                if (Thread.interrupted()) {
                    LOGGER.debug(() -> "Cleared interrupt flag");
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        } finally {
            // Delete duplicate output.
            deleteDuplicateOutput(meta, processor);
        }

        // Produce processing result.
        final long read = recordCount.getRead();
        final long written = recordCount.getWritten();
        final Map<Severity, Long> markerCounts = new HashMap<>();
        if (errorReceiverProxy.getErrorReceiver() instanceof final ErrorStatistics statistics) {
            for (final Severity sev : statistics.getSeverities()) {
                markerCounts.put(sev, statistics.getRecords(sev));
            }
        }
        return new ProcessorResultImpl(read, written, markerCounts);
    }

    protected abstract ProcessorTaskDecorator getProcessDecorator();

    private void process(final TaskContext taskContext) {
        String feedName = null;
        PipelineDoc pipelineDoc = null;

        try {
            final Meta meta = streamSource.getMeta();

            // Update the meta data for all output streams to use.
            metaData.put("Source Stream", String.valueOf(meta.getId()));
            metaData.put("Processing Node", nodeInfo.getThisNodeName());

            // Set the search id to be the id of the stream processor filter.
            // Only do this where the task has specific data ranges that need extracting as this is only the case
            // with a batch search.
            if (processorFilter != null
                && processorTask.getData() != null
                && !processorTask.getData().isEmpty()) {
                searchIdHolder.setSearchId(Long.toString(processorFilter.getId()));
            }

            // Load the feed.
            feedName = meta.getFeedName();
            feedHolder.setFeedName(feedName);

            // Setup the meta data holder.
            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

            // Set the pipeline so it can be used by a filter if needed.
            pipelineDoc = pipelineStore.readDocument(getProcessDecorator().getPipeline());
            if (pipelineDoc == null) {
                final String msg = "Pipeline " + processor.getPipelineUuid()
                                   + " cannot be found for processor with id " + processor.getId();
                LOGGER.error(msg);
                throw new RuntimeException(msg);
            }
            pipelineHolder.setPipeline(DocRefUtil.create(pipelineDoc));

            // Create some processing info.
            final String info = " pipeline=" +
                                pipelineDoc.getName() +
                                ", pipeline uuid=" + pipelineDoc.getUuid() +
                                ", feed=" +
                                feedName +
                                ", meta_id=" +
                                meta.getId() +
                                ", created=" +
                                DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                                ", processor_filter_id=" +
                                processorFilter.getId() +
                                ", task_id=" +
                                processorTask.getId();

            // Create processing start message.
            final String processingInfo = PROCESSING + info;

            // Log that we are starting to process.
            taskContext.info(() -> processingInfo);
            LOGGER.info(() -> processingInfo);

            // Hold the source and feed so the pipeline filters can get them.
            streamProcessorHolder.setStreamProcessor(processor, processorTask);

            // Process the streams.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactory.create(pipelineData, taskContext);
            processNestedStreams(pipeline, meta, streamSource, taskContext);

            final String finishedInfo = FINISHED +
                                        info +
                                        ", finished in  " +
                                        ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime);

            // Log that we have finished processing.
            taskContext.info(() -> finishedInfo);
            LOGGER.info(() -> finishedInfo);

        } catch (final RuntimeException e) {
            outputFatalError(e);

        } finally {
            // Record some statistics about processing.
            recordStats(feedName, pipelineDoc);
        }
    }

    private void recordStats(final String feedName, final PipelineDoc pipelineDoc) {
        if (feedName != null && pipelineDoc != null) {
            try {
                final InternalStatisticEvent event = InternalStatisticEvent.createPlusOneCountStat(
                        InternalStatisticKey.PIPELINE_STREAM_PROCESSOR,
                        System.currentTimeMillis(),
                        ImmutableSortedMap.of(
                                "Feed", feedName,
                                "Pipeline", pipelineDoc.getName(),
                                "Node", nodeInfo.getThisNodeName()));

                internalStatisticsReceiver.putEvent(event);

            } catch (final RuntimeException e) {
                LOGGER.error(() -> "recordStats", e);
            }
        } else {
            LOGGER.warn("Unable to record stats. feedName: {}, pipelineDoc: {}", feedName, pipelineDoc);
        }
    }

    /**
     * Processes a source and writes the result to a target.
     */
    private void processNestedStreams(final Pipeline pipeline,
                                      final Meta meta,
                                      final Source source,
                                      final TaskContext taskContext) {
        boolean startedProcessing = false;

        // Get the stream providers.
        metaHolder.setMeta(meta);

        try {
            final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
            locationFactory.setLocationFactory(streamLocationFactory);

            // Loop over the stream boundaries and process each
            // sequentially.
            final long count = source.count();
            for (long index = 0; index < count && !taskContext.isTerminated(); index++) {
                try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                    final InputStream inputStream;

                    // If the task requires specific events to be processed then
                    // add them.
                    final String data = processorTask.getData();
                    if (data != null && !data.isEmpty()) {
                        final List<InclusiveRange> ranges = InclusiveRanges.rangesFromString(data);
                        final SegmentInputStream raSegmentInputStream = inputStreamProvider.get();
                        raSegmentInputStream.include(0);
                        for (final InclusiveRange range : ranges) {
                            for (long i = range.getMin(); i <= range.getMax(); i++) {
                                raSegmentInputStream.include(i);
                            }
                        }
                        raSegmentInputStream.include(raSegmentInputStream.count() - 1);
                        inputStream = raSegmentInputStream;

                    } else {
                        // Get the stream.
                        inputStream = inputStreamProvider.get();
                    }

                    // Get the appropriate encoding for the stream type.
                    final String encoding = feedProperties.getEncoding(
                            meta.getFeedName(), meta.getTypeName(), null);

                    // We want to get a preview of the input stream so we can
                    // skip it if it is effectively empty.
                    final PreviewInputStream previewInputStream = new PreviewInputStream(inputStream);
                    String preview = previewInputStream.previewAsString(PREVIEW_SIZE, encoding);
                    // Remove whitespace from the preview.
                    preview = preview.trim();

                    // If there are still characters in the preview then
                    // continue.
                    if (preview.length() >= MIN_STREAM_SIZE) {
                        // Try and remove XML declaration for cases where the
                        // input is blank except for an XML declaration.
                        preview = XML_DECL_PATTERN.matcher(preview).replaceFirst("");
                        // Remove whitespace from the preview.
                        preview = preview.trim();

                        // Skip the input stream if it is empty. replaces:
                        // inputStream.size >= MIN_STREAM_SIZE
                        if (preview.length() >= MIN_STREAM_SIZE) {
                            // Start processing if we haven't already.
                            if (!startedProcessing) {
                                startedProcessing = true;
                                pipeline.startProcessing();
                            }

                            metaHolder.setInputStreamProvider(inputStreamProvider);
                            metaHolder.setPartIndex(index);
                            streamLocationFactory.setPartIndex(index);

                            // Process the boundary.
                            try {
                                pipeline.process(previewInputStream, encoding);
                            } catch (final Throwable e) {
                                handleProcessingException(e, meta);
                            }

                            // Reset the error statistics for the next stream.
                            if (errorReceiverProxy.getErrorReceiver() instanceof final ErrorStatistics errorStats) {
                                errorStats.reset();
                            }
                        }
                    }
                }
            }
        } catch (final Throwable e) {
            handleProcessingException(e, meta);
        } finally {
            try {
                if (startedProcessing) {
                    pipeline.endProcessing();
                }
            } catch (final Throwable e) {
                handleProcessingException(e, meta);
            }
        }
    }

    private void handleProcessingException(final Throwable e,
                                           final Meta meta) {
        if (e instanceof LoggedException) {
            // The exception has already been logged so ignore it.
            LOGGER.trace(() -> "Error while processing data task: id = " + NullSafe.get(meta, Meta::getId), e);
        } else if (e instanceof IOException
                   || e instanceof RuntimeException) {
            // An exception that's gets here is definitely a failure.
            outputFatalError(e);
        } else if (e instanceof final Error err) {
            // If we get here we are into OOM, stackOverflow type critical JVM errors so try to log
            // the failure (if the JVM allows) but re-throw as we should not really be swallowing JVM Errors.
            try {
                LOGGER.error("Error while processing data task: id = {}", NullSafe.get(meta, Meta::getId), e);
                outputFatalError(e);
            } catch (final Exception e2) {
                // Error while logging
                LOGGER.error("Error while trying to log error '{}'", e.getMessage(), e2);
            }
            throw err;
        }
    }

    private void outputFatalError(final Throwable e) {
        outputError(e, Severity.FATAL_ERROR);
    }

    /**
     * Used to handle any errors that may occur during translation.
     */
    private void outputError(final Throwable e, final Severity severity) {
        if (errorReceiverProxy != null && !(e instanceof LoggedException)) {
            try {
                if (e.getMessage() != null) {
                    errorReceiverProxy.log(severity, null, ELEMENT_ID, e.getMessage(), e);
                } else {
                    errorReceiverProxy.log(severity, null, ELEMENT_ID, e.toString(), e);
                }
            } catch (final RuntimeException e2) {
                // Ignore exception as we generated it.
                LOGGER.error("Error while trying to log {} to errorReceiverProxy with message '{}'",
                        severity, e.getMessage(), e2);
            }

            if (errorReceiverProxy.getErrorReceiver() instanceof final ErrorStatistics errorStatistics) {
                errorStatistics.checkRecord(-1);
            }

            if (streamSource.getMeta() != null) {
                LOGGER.trace(() -> "Error while processing data task: id = " + streamSource.getMeta().getId(), e);
            }
        } else {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(streamSource.getMeta());
    }

    private void deleteDuplicateOutput(final Meta meta,
                                       final Processor processor) {
        final AtomicInteger count = new AtomicInteger();
        try {
            final ExpressionOperator findMetaExpression = ExpressionOperator.builder()
                    .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, meta.getId())
                    .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, processor.getPipeline())
                    .addOperator(
                            ExpressionOperator
                                    .builder()
                                    .op(Op.OR)
                                    .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.toString())
                                    .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.toString())
                                    .build())
                    .build();
            final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(findMetaExpression);
            final List<Meta> streamList = metaService.find(findMetaCriteria).getValues();

            Map<Long, ProcessorTask> taskMap = null;
            if (streamList != null && streamList.size() > 0) {
                for (final Meta oldMeta : streamList) {
                    try {
                        if (oldMeta.getProcessorTaskId() == null) {
                            metaService.updateStatus(oldMeta, oldMeta.getStatus(), Status.DELETED);
                            count.incrementAndGet();
                        } else if (oldMeta.getProcessorTaskId() != processorTask.getId()) {
                            if (taskMap == null) {
                                final ExpressionOperator findTaskExpression = ExpressionOperator.builder()
                                        .addIdTerm(ProcessorTaskFields.META_ID, Condition.EQUALS, meta.getId())
                                        .addIdTerm(
                                                ProcessorTaskFields.PROCESSOR_ID, Condition.EQUALS, processor.getId())
                                        .build();
                                final ResultPage<ProcessorTask> tasks = processorTaskService.find(
                                        new ExpressionCriteria(findTaskExpression));
                                taskMap = tasks
                                        .stream()
                                        .collect(Collectors.toMap(ProcessorTask::getId, Function.identity()));
                            }

                            final ProcessorTask task = taskMap.get(oldMeta.getProcessorTaskId());
                            if (task == null ||
                                TaskStatus.COMPLETE.equals(task.getStatus()) ||
                                TaskStatus.DELETED.equals(task.getStatus())) {
                                // If the task associated with the other output is complete in some way then delete the
                                // output.
                                metaService.updateStatus(oldMeta, oldMeta.getStatus(), Status.DELETED);
                                count.incrementAndGet();
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        if (count.get() > 0) {
            LOGGER.info(() -> "Deleted " + count.get() + " duplicates");
        }
    }

    private static class ProcessInfoOutputStreamProvider extends AbstractElement
            implements DestinationProvider, Destination, AutoCloseable {

        private final Store streamStore;
        private final MetaData metaData;
        private final Meta meta;
        private final String feedName;
        private final Processor processor;
        private final ProcessorFilter processorFilter;
        private final ProcessorTask processorTask;
        private final RecordCount recordCount;
        private final ErrorReceiverProxy errorReceiverProxy;
        private final VolumeGroupNameProvider volumeGroupNameProvider;

        private OutputStream processInfoOutputStream;
        private Target processInfoStreamTarget;

        ProcessInfoOutputStreamProvider(final Store streamStore,
                                        final MetaData metaData,
                                        final Meta meta,
                                        final String feedName,
                                        final Processor processor,
                                        final ProcessorFilter processorFilter,
                                        final ProcessorTask processorTask,
                                        final RecordCount recordCount,
                                        final ErrorReceiverProxy errorReceiverProxy,
                                        final VolumeGroupNameProvider volumeGroupNameProvider) {
            this.streamStore = streamStore;
            this.metaData = metaData;
            this.meta = meta;
            this.feedName = feedName;
            this.processor = processor;
            this.processorFilter = processorFilter;
            this.processorTask = processorTask;
            this.recordCount = recordCount;
            this.errorReceiverProxy = errorReceiverProxy;
            this.volumeGroupNameProvider = volumeGroupNameProvider;
        }

        @Override
        public Destination borrowDestination() {
            return this;
        }

        @Override
        public void returnDestination(final Destination destination) {
        }

        @Override
        public OutputStream getOutputStream() {
            return getOutputStream(null, null);
        }

        @Override
        public OutputStream getOutputStream(final byte[] header, final byte[] footer) {
            if (processInfoOutputStream == null) {
                String processorUuid = null;
                Integer processorFilterId = null;
                String pipelineUuid = null;
                Long processorTaskId = null;

                if (processor != null) {
                    processorUuid = processor.getUuid();
                    pipelineUuid = processor.getPipelineUuid();
                }
                if (processorFilter != null) {
                    processorFilterId = processorFilter.getId();
                }
                if (processorTask != null) {
                    processorTaskId = processorTask.getId();
                }

                // Create a processing info stream to write all processing
                // information to.
                final MetaProperties dataProperties = MetaProperties.builder()
                        .feedName(feedName)
                        .typeName(StreamTypeNames.ERROR)
                        .parent(meta)
                        .processorUuid(processorUuid)
                        .pipelineUuid(pipelineUuid)
                        .processorFilterId(processorFilterId)
                        .processorTaskId(processorTaskId)
                        .build();

                final String volumeGroupName = volumeGroupNameProvider
                        .getVolumeGroupName(meta.getFeedName(), StreamTypeNames.ERROR, null);
                processInfoStreamTarget = streamStore.openTarget(dataProperties, volumeGroupName);
                processInfoOutputStream = new WrappedOutputStream(processInfoStreamTarget.next().get()) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.flush();
                            super.close();

                        } finally {
                            // Only do something if an output stream was used.
                            if (processInfoStreamTarget != null) {
                                // Write meta data.
                                final AttributeMap attributeMap = metaData.getAttributes();
                                processInfoStreamTarget.getAttributes().putAll(attributeMap);

                                try {
                                    // Write statistics meta data.
                                    // Get current process statistics
                                    final ProcessStatistics processStatistics = ProcessStatisticsFactory.create(
                                            recordCount, errorReceiverProxy);
                                    processStatistics.write(processInfoStreamTarget.getAttributes());
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e::getMessage, e);
                                }

                                // Close the stream target.
                                try {
                                    processInfoStreamTarget.close();
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e::getMessage, e);
                                }
                            }
                        }
                    }
                };
            }

            return processInfoOutputStream;
        }

        public void close() throws IOException {
            if (processInfoOutputStream != null) {
                processInfoOutputStream.close();
            }
        }

        @Override
        public List<stroom.pipeline.factory.Processor> createProcessors() {
            return Collections.emptyList();
        }
    }
}
