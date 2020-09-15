package stroom.search.extraction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.Query;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.NewCoprocessor;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.TaskContext;
import stroom.util.concurrent.ExecutorProvider;
import stroom.util.config.PropertyUtil;
import stroom.util.shared.HasTerminate;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskWrapper;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Component
@Scope(StroomScope.TASK)
public class ExtractionDecoratorFactory {
    /**
     * We don't want to collect more than 1 million doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000000;

    private final ExtractionTaskExecutor extractionTaskExecutor;
    private final ExtractionTaskProperties extractionTaskProperties;
    private final StreamStore streamStore;
    private final ExecutorProvider executorProvider;
    private final Provider<TaskWrapper> taskWrapperProvider;
    private final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider;
    private final AnnotationsDecoratorFactory receiverDecoratorFactory;
    private final SecurityContext securityContext;
    private final TaskContext taskContext;
    private final int maxStoredDataQueueSize;

    @Inject
    ExtractionDecoratorFactory(final ExtractionTaskExecutor extractionTaskExecutor,
                               final ExtractionTaskProperties extractionTaskProperties,
                               final StreamStore streamStore,
                               final ExecutorProvider executorProvider,
                               final Provider<TaskWrapper> taskWrapperProvider,
                               final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider,
                               final AnnotationsDecoratorFactory receiverDecoratorFactory,
                               final SecurityContext securityContext,
                               final TaskContext taskContext,
                               @Value("#{propertyConfigurer.getProperty('stroom.search.maxStoredDataQueueSize')}") final String maxStoredDataQueueSize) {
        this.extractionTaskExecutor = extractionTaskExecutor;
        this.extractionTaskProperties = extractionTaskProperties;
        this.streamStore = streamStore;
        this.executorProvider = executorProvider;
        this.taskWrapperProvider = taskWrapperProvider;
        this.extractionTaskHandlerProvider = extractionTaskHandlerProvider;
        this.receiverDecoratorFactory = receiverDecoratorFactory;
        this.securityContext = securityContext;
        this.taskContext = taskContext;
        this.maxStoredDataQueueSize = PropertyUtil.toInt(maxStoredDataQueueSize, DEFAULT_MAX_STORED_DATA_QUEUE_SIZE);
    }

    public Receiver create(final Receiver parentReceiver,
                           final String[] storedFields,
                           final Coprocessors coprocessors,
                           final Query query,
                           final AtomicLong totalResults,
                           final HasTerminate hasTerminate) {
        // Update config for extraction task executor.
        extractionTaskExecutor.setMaxThreads(extractionTaskProperties.getMaxThreads());

        // Create an object to make event lists from raw index data.
        final StreamMapCreator streamMapCreator = new StreamMapCreator(
                storedFields,
                streamStore);

        // Group coprocessors by extraction pipeline.
        final Map<DocRef, Set<NewCoprocessor>> map = new HashMap<>();
        coprocessors.getSet().forEach(coprocessor -> {
            DocRef extractionPipeline = null;
            if (coprocessor.getSettings().extractValues()) {
                extractionPipeline = coprocessor.getSettings().getExtractionPipeline();
            }
            map.computeIfAbsent(extractionPipeline, k -> new HashSet<>()).add(coprocessor);
        });

        final Map<DocRef, Receiver> receivers = new HashMap<>();
        map.forEach((docRef, coprocessorSet) -> {
            // Create a receiver that will send data to all coprocessors.
            Receiver receiver;
            if (coprocessorSet.size() == 1) {
                final NewCoprocessor coprocessor = coprocessorSet.iterator().next();
                final FieldIndexMap fieldIndexMap = coprocessor.getFieldIndexMap();
                final Consumer<Values> valuesConsumer = coprocessor.getValuesConsumer();
                final Consumer<Error> errorConsumer = coprocessor.getErrorConsumer();
                final Consumer<Long> completionCountConsumer = coprocessor.getCompletionCountConsumer();
                receiver = new ReceiverImpl(valuesConsumer, errorConsumer, completionCountConsumer, fieldIndexMap);
            } else {
                // We assume all coprocessors for the same extraction use the same field index map.
                // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
                final FieldIndexMap fieldIndexMap = coprocessorSet.iterator().next().getFieldIndexMap();
                final Consumer<Values> valuesConsumer = values -> coprocessorSet.forEach(coprocessor -> coprocessor.getValuesConsumer().accept(values));
                final Consumer<Error> errorConsumer = error -> coprocessorSet.forEach(coprocessor -> coprocessor.getErrorConsumer().accept(error));
                final Consumer<Long> completionCountConsumer = delta -> coprocessorSet.forEach(coprocessor -> coprocessor.getCompletionCountConsumer().accept(delta));
                receiver = new ReceiverImpl(valuesConsumer, errorConsumer, completionCountConsumer, fieldIndexMap);
            }

            // Decorate result with annotations.
            receiver = receiverDecoratorFactory.create(receiver, query);

            receivers.put(docRef, receiver);
        });

        // Make a task producer that will create event data extraction tasks when requested by the executor.
        final ExtractionProgressTracker tracker = new ExtractionProgressTracker(hasTerminate);
        final ExtractionTaskProducer extractionTaskProducer = new ExtractionTaskProducer(
                extractionTaskExecutor,
                streamMapCreator,
                parentReceiver,
                receivers,
                maxStoredDataQueueSize,
                extractionTaskProperties.getMaxThreadsPerTask(),
                executorProvider,
                taskWrapperProvider,
                extractionTaskHandlerProvider,
                securityContext,
                tracker,
                taskContext);

        // Begin processing.
        return extractionTaskProducer.process();
    }
}
