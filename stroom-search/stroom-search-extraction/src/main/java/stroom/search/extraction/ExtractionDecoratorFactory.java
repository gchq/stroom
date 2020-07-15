package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.TableSettings;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.*;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
                           final Receiver parentReceiver,
                           final String[] storedFields,
                           final Coprocessors coprocessors,
                           final Query query) {
        // Update config for extraction task executor.
        extractionTaskExecutor.setMaxThreads(extractionConfig.getMaxThreads());

        // Create an object to make event lists from raw index data.
        final StreamMapCreator streamMapCreator = new StreamMapCreator(
                storedFields,
                metaService);

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
        final ExtractionTaskProducer extractionTaskProducer = new ExtractionTaskProducer(
                extractionTaskExecutor,
                streamMapCreator,
                parentReceiver,
                receivers,
                extractionConfig.getMaxStoredDataQueueSize(),
                extractionConfig.getMaxThreadsPerTask(),
                executorProvider,
                taskContextFactory,
                parentContext,
                extractionTaskHandlerProvider,
                securityContext);

        // Begin processing.
        return extractionTaskProducer.process();
    }

    public Receiver createAlertExtractionTask(final Receiver receiver, final Receiver parentReceiver,
                                              final long streamId, final long[] sortedEventIds, DocRef extractionPipeline,
                                              List<AlertDefinition> alertDefinitions, final Map<String, String> params){
        final ExtractionTaskProducer extractionTaskProducer = new ExtractionTaskProducer(
                extractionTaskExecutor,
                null,
                parentReceiver,
                null,
                extractionConfig.getMaxStoredDataQueueSize(),
                extractionConfig.getMaxThreadsPerTask(),
                executorProvider,
                taskContextFactory,
                taskContextFactory.currentContext(),
                extractionTaskHandlerProvider,
                securityContext);
        extractionTaskProducer.createAlertExtractionTask(streamId, sortedEventIds,
                extractionPipeline, alertDefinitions, params, receiver);
        return extractionTaskProducer.process();
    }

    static public class AlertDefinition {
        private final Map<String,String> attributes;
        private final TableComponentSettings tableComponentSettings;

        public AlertDefinition(final TableComponentSettings tableComponentSettings){
            this(tableComponentSettings, null);
        }
        public AlertDefinition(final TableComponentSettings tableComponentSettings, final Map<String, String> attributes) {
            this.tableComponentSettings = tableComponentSettings;
            this.attributes = attributes;
        }

        public Map<String, String> getAttributes() {
            if (attributes == null) {
                return Map.of();
            }
            return attributes;
        }

        public final TableComponentSettings getTableComponentSettings() {
            return tableComponentSettings;
        }
    }
}
