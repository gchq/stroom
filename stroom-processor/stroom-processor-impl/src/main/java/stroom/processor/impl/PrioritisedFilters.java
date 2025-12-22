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

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.concurrent.AsyncReference;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
public class PrioritisedFilters implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PrioritisedFilters.class);

    private final ProcessorFilterService processorFilterService;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;

    private final AsyncReference<List<ProcessorFilter>> asyncReference;

    @Inject
    public PrioritisedFilters(final ProcessorFilterService processorFilterService,
                              final TaskContextFactory taskContextFactory,
                              final SecurityContext securityContext,
                              final ExecutorProvider executorProvider) {
        this.processorFilterService = processorFilterService;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        asyncReference = new AsyncReference<>(filters -> fetch(), Duration.ofSeconds(10), executorProvider.get());
    }

    public List<ProcessorFilter> get() {
        return asyncReference.get();
    }

    private List<ProcessorFilter> fetch() {
        return securityContext.asProcessingUserResult(() -> {
            // Get an up-to-date list of all enabled stream processor filters.
            LOGGER.trace("Getting enabled non deleted filters");
            info(() -> "Getting enabled non deleted filters");
            final ExpressionOperator expression = ExpressionOperator.builder()
                    .addBooleanTerm(ProcessorFields.ENABLED, Condition.EQUALS, true)
                    .addBooleanTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                    .addBooleanTerm(ProcessorFilterFields.ENABLED, Condition.EQUALS, true)
                    .addBooleanTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
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
            for (final ProcessorFilter filter : NullSafe.list(filters)) {
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
        });
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.debug(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    @Override
    public void clear() {
        asyncReference.clear();
    }
}
