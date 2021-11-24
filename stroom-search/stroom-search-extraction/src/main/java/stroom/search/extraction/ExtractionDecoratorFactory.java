package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.docref.DocRef;
import stroom.index.shared.IndexConstants;
import stroom.meta.api.MetaService;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.Receiver;
import stroom.query.common.v2.ReceiverImpl;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SearchProgressLog;
import stroom.util.logging.SearchProgressLog.SearchPhase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
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
    private final LinkedBlockingQueue<Optional<Val[]>> storedDataQueue;
    private final Map<DocRef, ExtractionReceiver> receivers;
    private final AtomicLong indexSearchTotalValues = new AtomicLong();
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final ExtractionProgressTracker tracker = new ExtractionProgressTracker();

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
        storedDataQueue = new LinkedBlockingQueue<>(extractionConfig.getMaxStoredDataQueueSize());
        receivers = new HashMap<>();
    }

    public Receiver create(final TaskContext parentContext,
                           final Coprocessors coprocessors,
                           final Query query) {
        // We are going to do extraction or at least filter streams so add fields to the field index to do this.
        coprocessors.getFieldIndex().create(IndexConstants.STREAM_ID);
        coprocessors.getFieldIndex().create(IndexConstants.EVENT_ID);

        // Create an object to make event lists from raw index data.
        final StreamMapCreator streamMapCreator = new StreamMapCreator(
                coprocessors.getFieldIndex(),
                metaService);

        coprocessors.forEachExtractionCoprocessor((docRef, coprocessorSet) -> {
            // Create a receiver that will send data to all coprocessors.
            ExtractionReceiver receiver;
            if (coprocessorSet.size() == 1) {
                final Coprocessor coprocessor = coprocessorSet.iterator().next();
                final FieldIndex fieldIndex = coprocessors.getFieldIndex();
                final Consumer<Val[]> valuesConsumer = coprocessor.getValuesConsumer();
                final Consumer<Throwable> errorConsumer = coprocessor.getErrorConsumer();
                final Consumer<Long> completionConsumer = coprocessor.getCompletionConsumer();
                receiver = new ExtractionReceiverImpl(valuesConsumer, errorConsumer, completionConsumer, fieldIndex);
            } else {
                // We assume all coprocessors for the same extraction use the same field index map.
                // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
                final FieldIndex fieldIndex = coprocessors.getFieldIndex();
                final Consumer<Val[]> valuesConsumer = values ->
                        coprocessorSet.forEach(coprocessor ->
                                coprocessor.getValuesConsumer().accept(values));
                final Consumer<Throwable> errorConsumer = error ->
                        coprocessorSet.forEach(coprocessor ->
                                coprocessor.getErrorConsumer().accept(error));
                final Consumer<Long> completionConsumer = delta ->
                        coprocessorSet.forEach(coprocessor ->
                                coprocessor.getCompletionConsumer().accept(delta));
                receiver = new ExtractionReceiverImpl(valuesConsumer, errorConsumer, completionConsumer, fieldIndex);
            }

            // Decorate result with annotations.
            receiver = receiverDecoratorFactory.create(receiver, query);

            receivers.put(docRef, receiver);
        });

        // Start mapping streams.
        startMapping(parentContext, streamMapCreator);

        // Start extracting data.
        startExtraction(parentContext);

        return new ReceiverImpl(
                this::addToStoredDataQueue,
                coprocessors.getErrorConsumer(),
                count -> {
                    indexSearchTotalValues.set(count);

                    // Add null values to signal completion.
                    try {
                        storedDataQueue.put(Optional.empty());
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        // Continue to interrupt.
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e.getMessage(), e);
                    }
                });
    }

    private void startMapping(final TaskContext parentContext,
                              final StreamMapCreator streamMapCreator) {
        final Executor executor = executorProvider.get(STREAM_MAP_CREATOR_THREAD_POOL);
        final Runnable runnable = create(
                parentContext,
                storedDataQueue,
                streamMapCreator,
                receivers);
        CompletableFuture.runAsync(runnable, executor);
    }

    private Runnable create(final TaskContext parentContext,
                            final LinkedBlockingQueue<Optional<Val[]>> storedDataQueue,
                            final StreamMapCreator streamMapCreator,
                            final Map<DocRef, ExtractionReceiver> receivers) {
        final Consumer<TaskContext> consumer = tc -> {
            // Elevate permissions so users with only `Use` feed permission can `Read` streams.
            securityContext.asProcessingUser(() -> {
                info(tc, () -> "Starting extraction task producer");
                try {
                    boolean complete = false;
                    while (!complete && !Thread.currentThread().isInterrupted()) {
                        info(tc, () -> "" +
                                "Creating extraction tasks - stored data queue size: " +
                                storedDataQueue.size() +
                                " stream event map size: " +
                                streamEventMap.size());

                        // Poll for the next set of values.
                        final Optional<Val[]> optional = storedDataQueue.take();
                        try {
                            // We will have a value here unless index search has finished adding values in which case we
                            // will have an empty optional.
                            if (optional.isPresent()) {
                                try {
                                    // If we have some values then map them.
                                    SearchProgressLog
                                            .increment(SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_TAKE);
                                    streamMapCreator.addEvent(streamEventMap, optional.get());

                                } catch (final RuntimeException e) {
                                    LOGGER.debug(e::getMessage, e);
                                    receivers.values().forEach(receiver ->
                                            receiver.getErrorConsumer().accept(e));
                                }
                            } else {
                                // We got no values from the topic so if index search ois complete then we have finished
                                // mapping too.
                                complete = true;
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e::getMessage, e);
                            throw e;
                        }
                    }
                } catch (final InterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    // Continue to interrupt.
                    Thread.currentThread().interrupt();
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                } finally {
                    info(tc, () -> "Finished creating extraction tasks");

                    try {
                        // Put null to signal we are complete.
                        streamEventMap.put(null);
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        // Continue to interrupt.
                        Thread.currentThread().interrupt();
                    }
                }
            });
        };
        return taskContextFactory.childContext(parentContext, "Extraction Task Mapper", consumer);
    }

    private void info(final TaskContext taskContext, final Supplier<String> messageSupplier) {
        taskContext.info(messageSupplier);
        LOGGER.debug(messageSupplier);
    }

    public void addToStoredDataQueue(final Val[] values) {
        try {
            SearchProgressLog.increment(SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_PUT);
            storedDataQueue.put(Optional.of(values));
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            // Continue to interrupt.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    private void startExtraction(final TaskContext parentContext) {
        final Executor executor = executorProvider.get(EXTRACTION_THREAD_POOL);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < extractionConfig.getMaxThreadsPerTask(); i++) {
            final Runnable runnable = extractData(parentContext);
            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
            futures.add(completableFuture);
        }
        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .whenCompleteAsync((r, e) -> {
                    tracker.finishedAddingTasks();
                    receivers.forEach((docRef, receiver) ->
                            receiver.getCompletionConsumer().accept(indexSearchTotalValues.get()));
                });
    }

    private Runnable extractData(final TaskContext parentContext) {
        final Consumer<TaskContext> consumer = tc -> {
            boolean complete = false;
            while (!complete) {
                try {
                    final Optional<Entry<Long, List<Event>>> optional = streamEventMap.take();
                    if (optional.isPresent()) {
                        final Entry<Long, List<Event>> entry = optional.get();
                        SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_STREAM_EVENT_MAP_TAKE,
                                entry.getValue().size());
                        createTasks(tc, entry.getKey(), entry.getValue());

                    } else {
                        // if we didn't get any events from the event map and we have completed event mapping then
                        // there are no more tasks to create.
                        complete = true;
                    }
                } catch (final InterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    Thread.currentThread().interrupt();
                    complete = true;
                }
            }
        };
        return taskContextFactory.childContext(parentContext, "Extraction Task", consumer);
    }

    private void createTasks(final TaskContext taskContext,
                             final long streamId,
                             final List<Event> events) {
        SearchProgressLog.increment(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS);
        SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_EVENTS, events.size());
        final long[] eventIds = createEventIdArray(events, receivers);
        receivers.forEach((docRef, receiver) -> {
            tracker.incrementTasksTotal();

            if (docRef != null) {
                SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_DOCREF, events.size());
                try {
                    final ExtractionTaskHandler handler = handlerProvider.get();
                    handler.exec(taskContext, new ExtractionTask(streamId, eventIds, docRef, receiver));
                } finally {
                    tracker.incrementTasksCompleted();
                }

            } else {
                SearchProgressLog.add(SearchPhase.EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_NO_DOCREF, events.size());
                try {
                    info(taskContext, () -> "Transferring " + events.size() + " records from stream " + streamId);
                    // Pass raw values to coprocessors that are not requesting values to be extracted.
                    for (final Event event : events) {
                        receiver.getValuesConsumer().accept(event.getValues());
                    }
                } finally {
                    tracker.incrementTasksCompleted();
                }
            }
        });
    }

    private long[] createEventIdArray(final List<Event> events,
                                      final Map<DocRef, ExtractionReceiver> receivers) {
        // If we don't have any coprocessors that will perform extraction then don't bother sorting events.
        if (receivers.size() == 0 ||
                (receivers.size() == 1 && receivers.keySet().iterator().next() == null)) {
            return null;
        }

        // Get a list of the event ids we are extracting for this stream and sort them.
        final long[] eventIds = new long[events.size()];
        for (int i = 0; i < eventIds.length; i++) {
            eventIds[i] = events.get(i).getEventId();
        }
        // Sort the ids as the extraction expects them in order.
        Arrays.sort(eventIds);
        return eventIds;
    }
}
