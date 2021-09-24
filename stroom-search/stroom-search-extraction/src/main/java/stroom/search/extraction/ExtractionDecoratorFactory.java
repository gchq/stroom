package stroom.search.extraction;

import stroom.alert.api.AlertDefinition;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.docref.DocRef;
import stroom.index.shared.IndexConstants;
import stroom.meta.api.MetaService;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.Receiver;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;

public class ExtractionDecoratorFactory {

    private final ExtractionTaskExecutor extractionTaskExecutor;
    private final ExtractionConfig extractionConfig;
    private final MetaService metaService;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider;
    private final AnnotationsDecoratorFactory receiverDecoratorFactory;
    private final SecurityContext securityContext;

    @Inject
    ExtractionDecoratorFactory(final ExtractionTaskExecutor extractionTaskExecutor,
                               final ExtractionConfig extractionConfig,
                               final MetaService metaService,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider,
                               final AnnotationsDecoratorFactory receiverDecoratorFactory,
                               final SecurityContext securityContext) {
        this.extractionTaskExecutor = extractionTaskExecutor;
        this.extractionConfig = extractionConfig;
        this.metaService = metaService;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.extractionTaskHandlerProvider = extractionTaskHandlerProvider;
        this.receiverDecoratorFactory = receiverDecoratorFactory;
        this.securityContext = securityContext;
    }

    public Receiver create(final TaskContext parentContext,
                           final Coprocessors coprocessors,
                           final Query query) {
        // We are going to do extraction or at least filter streams so add fields to the field index to do this.
        coprocessors.getFieldIndex().create(IndexConstants.STREAM_ID);
        coprocessors.getFieldIndex().create(IndexConstants.EVENT_ID);

        // Update config for extraction task executor.
        extractionTaskExecutor.setMaxThreads(extractionConfig.getMaxThreads());

        // Create an object to make event lists from raw index data.
        final StreamMapCreator streamMapCreator = new StreamMapCreator(
                coprocessors.getFieldIndex(),
                metaService);

        final Map<DocRef, ExtractionReceiver> receivers = new HashMap<>();
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

        // Make a task producer that will create event data extraction tasks when requested by the executor.
        final ExtractionProgressTracker tracker = new ExtractionProgressTracker();
        final ExtractionTaskProducer extractionTaskProducer = new ExtractionTaskProducer(
                extractionTaskExecutor,
                streamMapCreator,
                coprocessors.getErrorConsumer(),
                receivers,
                extractionConfig.getMaxStoredDataQueueSize(),
                extractionConfig.getMaxThreadsPerTask(),
                extractionConfig.getMaxStreamEventMapSize(),
                executorProvider,
                taskContextFactory,
                parentContext,
                extractionTaskHandlerProvider,
                securityContext,
                tracker);

        // Begin processing.
        return extractionTaskProducer.process();
    }

    public Receiver createAlertExtractionTask(final ExtractionReceiver receiver,
                                              final long streamId, final long[] sortedEventIds,
                                              DocRef extractionPipeline, List<AlertDefinition> alertDefinitions,
                                              final Map<String, String> params) {
        return taskContextFactory.contextResult("Create Alerts", taskContext -> {
            final AlertExtractionSingleTaskProducer extractionTaskProducer =
                    new AlertExtractionSingleTaskProducer(streamId, sortedEventIds,
                            extractionPipeline, receiver,
                            alertDefinitions, params,
                            extractionTaskExecutor,
                            extractionTaskHandlerProvider,
                            taskContextFactory,
                            taskContext);
            return extractionTaskProducer.process();
        }).get();
    }
}
