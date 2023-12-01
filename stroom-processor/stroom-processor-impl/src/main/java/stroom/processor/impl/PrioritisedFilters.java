package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.task.api.TaskContextFactory;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Singleton
public class PrioritisedFilters {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PrioritisedFilters.class);

    private final ProcessorFilterService processorFilterService;
    private final TaskContextFactory taskContextFactory;

    private final AtomicReference<List<ProcessorFilter>> prioritisedFiltersRef = new AtomicReference<>();
    private volatile Instant lastUpdate = Instant.ofEpochMilli(0);

    @Inject
    public PrioritisedFilters(final ProcessorFilterService processorFilterService,
                              final TaskContextFactory taskContextFactory) {
        this.processorFilterService = processorFilterService;
        this.taskContextFactory = taskContextFactory;
    }

    public List<ProcessorFilter> get() {
        List<ProcessorFilter> filters = prioritisedFiltersRef.get();

        final Instant now = Instant.now();
        if (filters == null || lastUpdate.isBefore(now.minusSeconds(10))) {
            lastUpdate = now;
            filters = fetch();
            prioritisedFiltersRef.set(filters);
        }

        return filters;
    }

    private List<ProcessorFilter> fetch() {
        // Get an up-to-date list of all enabled stream processor filters.
        LOGGER.trace("Getting enabled non deleted filters");
        info(() -> "Getting enabled non deleted filters");
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ProcessorFields.ENABLED, Condition.EQUALS, true)
                .addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addTerm(ProcessorFilterFields.ENABLED, Condition.EQUALS, true)
                .addTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();

        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria(expression);
        final List<ProcessorFilter> filters = processorFilterService
                .find(findProcessorFilterCriteria)
                .getValues();
        LOGGER.trace("Found {} filters", filters.size());
        info(() -> "Found " + filters.size() + " filters");

        // Sort the stream processor filters by priority.
        filters.sort(ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR);

        // Try and ensure we have pipeline names for each filter
        for (ProcessorFilter filter : NullSafe.list(filters)) {
            try {
                if (filter != null
                        && filter.getPipelineUuid() != null
                        && NullSafe.isEmptyString(filter.getPipelineName())) {
                    final Optional<String> pipelineName = processorFilterService
                            .getPipelineName(filter.getProcessorType(), filter.getPipelineUuid());
                    pipelineName.ifPresent(newPipeName -> {
                        if (!Objects.equals(filter.getPipelineName(), newPipeName)) {
                            filter.setPipelineName(newPipeName);
                        }
                    });
                }
            } catch (final RuntimeException e) {
                // This error is expected in tests and the pipeline name isn't essential
                // as it is only used in here for logging purposes.
                LOGGER.trace(e::getMessage, e);
            }
        }
        return filters;
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.debug(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    public void reset() {
        prioritisedFiltersRef.set(null);
    }
}
