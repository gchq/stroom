package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.data.store.api.DataException;
import stroom.docref.DocRef;
import stroom.index.shared.IndexConstants;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ErrorConsumer;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.search.extraction.StreamEventMap.EventSet;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import javax.inject.Provider;

public class ExtractionDecorator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExtractionDecorator.class);

    private static final DocRef NULL_SELECTION = DocRef.builder().uuid("").name("None").type("").build();

    private static final ThreadPool STREAM_MAP_CREATOR_THREAD_POOL = new ThreadPoolImpl(
            "Extraction - Stream Map Creator");
    private static final ThreadPool EXTRACTION_THREAD_POOL = new ThreadPoolImpl("Extraction");

    private final ExtractionConfig extractionConfig;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final SecurityContext securityContext;
    private final AnnotationsDecoratorFactory receiverDecoratorFactory;
    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Provider<ExtractionStateHolder> extractionStateHolderProvider;
    private final QueryKey queryKey;

    private final Map<DocRef, PipelineData> pipelineDataMap = new ConcurrentHashMap<>();
    private final StreamEventMap streamEventMap;
    private final StoredDataQueue storedDataQueue;
    private final Map<DocRef, Receiver> receivers;

    ExtractionDecorator(final ExtractionConfig extractionConfig,
                        final ExecutorProvider executorProvider,
                        final TaskContextFactory taskContextFactory,
                        final PipelineScopeRunnable pipelineScopeRunnable,
                        final SecurityContext securityContext,
                        final AnnotationsDecoratorFactory receiverDecoratorFactory,
                        final MetaService metaService,
                        final PipelineStore pipelineStore,
                        final PipelineDataCache pipelineDataCache,
                        final Provider<ExtractionTaskHandler> handlerProvider,
                        final Provider<ExtractionStateHolder> extractionStateHolderProvider,
                        final QueryKey queryKey) {
        this.extractionConfig = extractionConfig;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.securityContext = securityContext;
        this.receiverDecoratorFactory = receiverDecoratorFactory;
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.handlerProvider = handlerProvider;
        this.extractionStateHolderProvider = extractionStateHolderProvider;
        this.queryKey = queryKey;

        // Create a queue to receive values and store them for asynchronous processing.
        streamEventMap = new StreamEventMap(extractionConfig.getMaxStreamEventMapSize());
        storedDataQueue = new StoredDataQueue(queryKey, extractionConfig.getMaxStoredDataQueueSize());
        receivers = new HashMap<>();
    }

    public StoredDataQueue createStoredDataQueue(final Coprocessors coprocessors,
                                                 final Query query) {
        // We are going to do extraction or at least filter streams so add fields to the field index to do this.
        coprocessors.getFieldIndex().create(IndexConstants.STREAM_ID);
        coprocessors.getFieldIndex().create(IndexConstants.EVENT_ID);
        coprocessors.forEachExtractionCoprocessor((docRef, coprocessorSet) -> {
            // We assume all coprocessors for the same extraction use the same field index map.
            // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
            final FieldIndex fieldIndex = coprocessors.getFieldIndex();

            // Create a receiver that will send data to all coprocessors.
            ValuesConsumer valuesConsumer;
            if (coprocessorSet.size() == 1) {
                valuesConsumer = coprocessorSet.iterator().next();
            } else {
                valuesConsumer = values -> coprocessorSet.forEach(coprocessor -> coprocessor.add(values));
            }

            // Decorate result with annotations.
            valuesConsumer = receiverDecoratorFactory.create(valuesConsumer, fieldIndex, query);
            receivers.put(docRef, new Receiver(fieldIndex, valuesConsumer));
        });

        // Set the delay to use for extraction of each stream.
        streamEventMap.setExtractionDelayMs(getExtractionDelayMs());

        return storedDataQueue;
    }

    public CompletableFuture<Void> startMapping(final TaskContext parentContext,
                                                final Coprocessors coprocessors) {
        final Executor executor = executorProvider.get(STREAM_MAP_CREATOR_THREAD_POOL);
        final Runnable runnable = mapStreams(
                parentContext,
                storedDataQueue,
                receivers,
                coprocessors);
        return CompletableFuture.runAsync(runnable, executor);
    }

    private Runnable mapStreams(final TaskContext parentContext,
                                final StoredDataQueue storedDataQueue,
                                final Map<DocRef, Receiver> receivers,
                                final Coprocessors coprocessors) {
        // Create an object to make event lists from raw index data.
        final EventFactory eventFactory = new EventFactory(coprocessors.getFieldIndex());
        final ErrorConsumer errorConsumer = coprocessors.getErrorConsumer();

        return taskContextFactory.childContext(
                parentContext,
                "Extraction Task Mapper",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    info(taskContext, () -> "Starting extraction task producer");
                    try {
                        boolean done = false;
                        while (!done) {
                            // Poll for the next set of values.
                            // When we get null we are done.
                            final Val[] values = storedDataQueue.take();
                            if (values != null) {
                                if (!taskContext.isTerminated()) {
                                    try {
                                        info(taskContext, () -> "" +
                                                "Creating extraction tasks - stored data queue size: " +
                                                storedDataQueue.size() +
                                                " stream event map size: " +
                                                streamEventMap.size());

                                        // If we have some values then map them.
                                        SearchProgressLog.increment(queryKey,
                                                SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_TAKE);
                                        final Event event = eventFactory.create(values);
                                        SearchProgressLog.increment(
                                                queryKey,
                                                SearchPhase.EXTRACTION_DECORATOR_FACTORY_STREAM_EVENT_MAP_PUT);
                                        streamEventMap.put(event);

                                    } catch (final RuntimeException e) {
                                        LOGGER.debug(e::getMessage, e);
                                        receivers.values().forEach(receiver ->
                                                errorConsumer.add(e));
                                    }
                                }
                            } else {
                                done = true;
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    } finally {
                        info(taskContext, () -> "Finished creating extraction tasks");
                        // We have finished mapping streams so mark the stream event map as complete.
                        LOGGER.debug(() -> "Completed stream mapping");
                        streamEventMap.complete();
                    }
                });
    }

    private void info(final TaskContext taskContext, final Supplier<String> messageSupplier) {
        taskContext.info(messageSupplier);
        LOGGER.debug(messageSupplier);
    }

    private long getExtractionDelayMs() {
        // Delay extraction if we are going to use one or more extraction pipelines.
        return receivers.keySet()
                .stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(docRef -> extractionConfig.getExtractionDelayMs())
                .orElse(0L);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> startExtraction(final TaskContext parentContext,
                                                   final LongAdder extractionCount,
                                                   final ErrorConsumer errorConsumer) {
        // Delay extraction if we are going to use one or more extraction pipelines.
        receivers.keySet().stream().filter(Objects::nonNull).findFirst().ifPresent(docRef -> {
            try {
                Thread.sleep(extractionConfig.getExtractionDelayMs());
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }
        });

        final Executor executor = executorProvider.get(EXTRACTION_THREAD_POOL);
        final int threadCount = extractionConfig.getMaxThreadsPerTask();
        final CompletableFuture<Void>[] futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            futures[i] = CompletableFuture.runAsync(() ->
                    extractData(parentContext, queryKey, extractionCount, errorConsumer), executor);
        }
        return CompletableFuture.allOf(futures);
    }

    private void extractData(final TaskContext parentContext,
                             final QueryKey queryKey,
                             final LongAdder extractionCount,
                             final ErrorConsumer errorConsumer) {
        try {
            while (true) {
                final EventSet eventSet = streamEventMap.take();
                if (!parentContext.isTerminated() && eventSet != null) {
                    SearchProgressLog.add(queryKey,
                            SearchPhase.EXTRACTION_DECORATOR_FACTORY_STREAM_EVENT_MAP_TAKE,
                            eventSet.size());

                    taskContextFactory.childContext(
                            parentContext,
                            "Extraction Task",
                            taskContext ->
                                    securityContext.useAsRead(() ->
                                            extractEvents(taskContext,
                                                    eventSet.getStreamId(),
                                                    eventSet.getEvents(),
                                                    extractionCount,
                                                    errorConsumer))).run();
                    LOGGER.debug("Completed extraction thread");
                }
            }
        } catch (final CompleteException e) {
            LOGGER.debug(() -> "Complete");
            LOGGER.trace(e::getMessage, e);
        }
    }

    private void extractEvents(final TaskContext taskContext,
                               final long streamId,
                               final Set<Event> events,
                               final LongAdder extractionCount,
                               final ErrorConsumer errorConsumer) {
        SearchProgressLog.increment(queryKey, SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS);
        SearchProgressLog.add(queryKey, SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_EVENTS, events.size());

        // Sort events if we are performing extraction.
        final long[] eventIds;
        if (receivers.size() > 1 ||
                (receivers.size() == 1 && receivers.keySet().iterator().next() != null)) {
            eventIds = events.stream().mapToLong(Event::getEventId).sorted().toArray();
        } else {
            eventIds = null;
        }

        if (receivers.size() > 0 && !Thread.currentThread().isInterrupted()) {
            try {
                Meta meta = null;

                for (final Entry<DocRef, Receiver> entry : receivers.entrySet()) {
                    final DocRef docRef = entry.getKey();
                    final Receiver receiver = entry.getValue();

                    if (docRef != null) {
                        SearchProgressLog.add(queryKey, SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_DOCREF,
                                events.size());

                        // Get cached pipeline data.
                        final PipelineData pipelineData = getPipelineData(docRef);

                        // Execute the extraction within a fresh pipeline scope.
                        meta = pipelineScopeRunnable.scopeResult(() -> {
                            final ExtractionTaskHandler handler = handlerProvider.get();
                            final ExtractionStateHolder extractionStateHolder = extractionStateHolderProvider.get();
                            extractionStateHolder.setQueryKey(queryKey);
                            extractionStateHolder.setFieldIndex(receiver.fieldIndex);
                            extractionStateHolder.setReceiver(receiver.valuesConsumer);

                            return handler.extract(
                                    taskContext,
                                    queryKey,
                                    streamId,
                                    eventIds,
                                    docRef,
                                    errorConsumer,
                                    pipelineData);
                        });

                        extractionCount.add(events.size());

                    } else {
                        // See if we can load the stream. We might get a StreamPermissionException if we aren't
                        // allowed to read from this stream.
                        if (meta == null) {
                            meta = metaService.getMeta(streamId);
                            if (meta == null) {
                                throw new DataException(
                                        "Unable to find data, could be due to lack of permissions");
                            }
                        }

                        SearchProgressLog.add(queryKey,
                                SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_NO_DOCREF,
                                events.size());
                        taskContext.reset();
                        info(taskContext,
                                () -> "Transferring " + events.size() + " records from stream " + streamId);
                        // Pass raw values to coprocessors that are not requesting values to be extracted.
                        for (final Event event : events) {
                            receiver.valuesConsumer.add(event.getValues());
                            extractionCount.increment();
                        }
                    }
                }
            } catch (final DataException e) {
                LOGGER.debug(e::getMessage, e);
            } catch (final ExtractionException e) {
                // Something went wrong extracting data from this stream.
                errorConsumer.add(e);
            } catch (final RuntimeException e) {
                // Something went wrong extracting data from this stream.
                final ExtractionException extractionException =
                        new ExtractionException("Unable to extract data from stream source with id: " +
                                streamId + " - " + e.getMessage(), e);
                errorConsumer.add(extractionException);
            }
        }
    }

    private PipelineData getPipelineData(final DocRef pipelineRef) {
        return pipelineDataMap.computeIfAbsent(pipelineRef, k -> {
            // Check the pipelineRef is not our 'NULL SELECTION'
            if (pipelineRef == null || NULL_SELECTION.compareTo(pipelineRef) == 0) {
                throw new ExtractionException("Extraction is enabled, but no extraction pipeline is configured.");
            }

            // Get the translation that will be used to display results.
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            if (pipelineDoc == null) {
                throw new ExtractionException("Unable to find result pipeline: " + pipelineRef);
            }

            // Create the parser.
            return pipelineDataCache.get(pipelineDoc);
        });
    }

    private record Receiver(FieldIndex fieldIndex, ValuesConsumer valuesConsumer) {

    }
}
