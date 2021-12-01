package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.docref.DocRef;
import stroom.index.shared.IndexConstants;
import stroom.meta.api.MetaService;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CompletionStateImpl;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ErrorConsumer;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SearchProgressLog;
import stroom.util.logging.SearchProgressLog.SearchPhase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

public class ExtractionDecoratorFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExtractionDecoratorFactory.class);

    private static final ThreadPool STREAM_MAP_CREATOR_THREAD_POOL = new ThreadPoolImpl(
            "Extraction - Stream Map Creator");
    private static final ThreadPool EXTRACTION_THREAD_POOL = new ThreadPoolImpl("Extraction");

    private final ExtractionConfig extractionConfig;
    private final MetaService metaService;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final AnnotationsDecoratorFactory receiverDecoratorFactory;
    private final SecurityContext securityContext;
    private final StreamEventMap streamEventMap;
    private final StoredDataQueue storedDataQueue;
    private final Map<DocRef, ExtractionReceiver> receivers;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final CompletionState completionState = new CompletionStateImpl();

    @Inject
    ExtractionDecoratorFactory(final ExtractionConfig extractionConfig,
                               final MetaService metaService,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final AnnotationsDecoratorFactory receiverDecoratorFactory,
                               final SecurityContext securityContext,
                               final Provider<ExtractionTaskHandler> handlerProvider) {
        this.extractionConfig = extractionConfig;
        this.metaService = metaService;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.receiverDecoratorFactory = receiverDecoratorFactory;
        this.securityContext = securityContext;
        this.handlerProvider = handlerProvider;

        // Create a queue to receive values and store them for asynchronous processing.
        streamEventMap = new StreamEventMap(extractionConfig.getMaxStreamEventMapSize());
        storedDataQueue = new StoredDataQueue(extractionConfig.getMaxStoredDataQueueSize());
        receivers = new HashMap<>();
    }

    public StoredDataQueue create(final TaskContext parentContext,
                                  final Coprocessors coprocessors,
                                  final AtomicLong extractionCount,
                                  final Query query) {
        // We are going to do extraction or at least filter streams so add fields to the field index to do this.
        coprocessors.getFieldIndex().create(IndexConstants.STREAM_ID);
        coprocessors.getFieldIndex().create(IndexConstants.EVENT_ID);

        // Create an object to make event lists from raw index data.
        final StreamMapCreator streamMapCreator = new StreamMapCreator(
                coprocessors.getFieldIndex(),
                metaService);

        coprocessors.forEachExtractionCoprocessor((docRef, coprocessorSet) -> {
            // We assume all coprocessors for the same extraction use the same field index map.
            // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
            final FieldIndex fieldIndex = coprocessors.getFieldIndex();

            // Create a receiver that will send data to all coprocessors.
            ExtractionReceiver receiver;
            if (coprocessorSet.size() == 1) {
                final Coprocessor coprocessor = coprocessorSet.iterator().next();
                receiver = new ExtractionReceiver() {
                    @Override
                    public void add(final Val[] values) {
                        coprocessor.add(values);
                    }

                    @Override
                    public FieldIndex getFieldMap() {
                        return fieldIndex;
                    }
                };
            } else {
                receiver = new ExtractionReceiver() {
                    @Override
                    public void add(final Val[] values) {
                        coprocessorSet.forEach(coprocessor -> coprocessor.add(values));
                    }

                    @Override
                    public FieldIndex getFieldMap() {
                        return fieldIndex;
                    }
                };
            }

            // Decorate result with annotations.
            receiver = receiverDecoratorFactory.create(receiver, query);

            receivers.put(docRef, receiver);
        });

        // Start mapping streams.
        startMapping(parentContext, streamMapCreator, coprocessors.getErrorConsumer());

        // Start extracting data.
        final List<CompletableFuture<Void>> futures = startExtraction(parentContext, extractionCount,
                coprocessors.getErrorConsumer());
        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .whenCompleteAsync((r, e) -> {
                    LOGGER.debug("Completed extraction");
                    completionState.signalComplete();
                });

        return storedDataQueue;
    }

    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return completionState.awaitCompletion(timeout, unit);
    }

    private void startMapping(final TaskContext parentContext,
                              final StreamMapCreator streamMapCreator,
                              final ErrorConsumer errorConsumer) {
        final Executor executor = executorProvider.get(STREAM_MAP_CREATOR_THREAD_POOL);
        final Runnable runnable = mapStreams(
                parentContext,
                storedDataQueue,
                streamMapCreator,
                receivers,
                errorConsumer);
        CompletableFuture.runAsync(runnable, executor).whenCompleteAsync((v, t) -> {
            try {
                // We have finished mapping streams so mark the stream event map as complete.
                LOGGER.debug(() -> "Completed stream mapping");
                streamEventMap.complete();
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }
        });
    }

    private Runnable mapStreams(final TaskContext parentContext,
                                final StoredDataQueue storedDataQueue,
                                final StreamMapCreator streamMapCreator,
                                final Map<DocRef, ExtractionReceiver> receivers,
                                final ErrorConsumer errorConsumer) {
        final Consumer<TaskContext> consumer = taskContext -> {
            // Elevate permissions so users with only `Use` feed permission can `Read` streams.
            securityContext.asProcessingUser(() -> {
                info(taskContext, () -> "Starting extraction task producer");
                try {
                    while (!taskContext.isTerminated()) {
                        info(taskContext, () -> "" +
                                "Creating extraction tasks - stored data queue size: " +
                                storedDataQueue.size() +
                                " stream event map size: " +
                                streamEventMap.size());

                        // Poll for the next set of values.
                        final Val[] values = storedDataQueue.take();
                        try {
                            // If we have some values then map them.
                            SearchProgressLog
                                    .increment(SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_TAKE);
                            streamMapCreator.addEvent(streamEventMap, values);

                        } catch (final RuntimeException e) {
                            LOGGER.debug(e::getMessage, e);
                            receivers.values().forEach(receiver ->
                                    errorConsumer.add(e));
                        }
                    }
                } catch (final InterruptedException e) {
                    LOGGER.trace(e::getMessage, e);
                    // Keep interrupting this thread.
                    Thread.currentThread().interrupt();
                } catch (final CompleteException e) {
                    LOGGER.debug(() -> "Complete");
                    LOGGER.trace(e::getMessage, e);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                } finally {
                    info(taskContext, () -> "Finished creating extraction tasks");
                }
            });
        };
        return taskContextFactory.childContext(parentContext, "Extraction Task Mapper", consumer);
    }

    private void info(final TaskContext taskContext, final Supplier<String> messageSupplier) {
        taskContext.info(messageSupplier);
        LOGGER.debug(messageSupplier);
    }

    private List<CompletableFuture<Void>> startExtraction(final TaskContext parentContext,
                                                          final AtomicLong extractionCount,
                                                          final ErrorConsumer errorConsumer) {
        final Executor executor = executorProvider.get(EXTRACTION_THREAD_POOL);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < extractionConfig.getMaxThreadsPerTask(); i++) {
            futures.add(CompletableFuture.runAsync(() ->
                    extractData(parentContext, extractionCount, errorConsumer), executor));
        }
        return futures;
    }

    private void extractData(final TaskContext parentContext,
                             final AtomicLong extractionCount,
                             final ErrorConsumer errorConsumer) {
        try {
            while (!parentContext.isTerminated()) {
                final Entry<Long, Set<Event>> entry = streamEventMap.take();

                taskContextFactory.childContext(parentContext, "Extraction Task", taskContext -> {
                    SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_STREAM_EVENT_MAP_TAKE,
                            entry.getValue().size());
                    extractEvents(taskContext, entry.getKey(), entry.getValue(), extractionCount, errorConsumer);
                }).run();
            }
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        } catch (final CompleteException e) {
            LOGGER.debug(() -> "Complete");
            LOGGER.trace(e::getMessage, e);
        } finally {
            LOGGER.debug("Completed extraction thread");
        }
    }

    private void extractEvents(final TaskContext taskContext,
                               final long streamId,
                               final Set<Event> events,
                               final AtomicLong extractionCount,
                               final ErrorConsumer errorConsumer) {
        SearchProgressLog.increment(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS);
        SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_EVENTS, events.size());

        // Sort events if we are performing extraction.
        final long[] eventIds;
        if (receivers.size() > 1 ||
                (receivers.size() == 1 && receivers.keySet().iterator().next() != null)) {
            eventIds = events.stream().sorted().mapToLong(Event::getEventId).toArray();
        } else {
            eventIds = null;
        }

        if (receivers.size() > 0) {
            receivers.forEach((docRef, receiver) -> {
                if (docRef != null) {
                    SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_DOCREF,
                            events.size());
                    final ExtractionTaskHandler handler = handlerProvider.get();
                    handler.exec(taskContext, new ExtractionTask(streamId, eventIds, docRef, receiver, errorConsumer));
                    extractionCount.addAndGet(events.size());

                } else {
                    SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_NO_DOCREF,
                            events.size());
                    info(taskContext, () -> "Transferring " + events.size() + " records from stream " + streamId);
                    // Pass raw values to coprocessors that are not requesting values to be extracted.
                    for (final Event event : events) {
                        receiver.add(event.getValues());
                        extractionCount.incrementAndGet();
                    }
                }
            });
        }
    }
}
