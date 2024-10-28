/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.impl.AnalyticDataStores.AnalyticDataStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.annotation.api.AnnotationFields;
import stroom.datasource.api.v2.QueryField;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeFilter;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.LmdbDataStore;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.DateUtil;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.extraction.AnnotationsDecoratorFactory;
import stroom.search.extraction.ExpressionFilter;
import stroom.search.impl.NodeSearchTask;
import stroom.search.impl.NodeSearchTaskHandler;
import stroom.search.impl.SearchException;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;


class AnalyticsNodeSearchTaskHandler implements NodeSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticsNodeSearchTaskHandler.class);

    private final AnnotationsDecoratorFactory annotationsDecoratorFactory;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final AnalyticDataStores analyticDataStores;

    private final LongAdder hitCount = new LongAdder();
    private final LongAdder extractionCount = new LongAdder();

    private TaskContext parentContext;

    @Inject
    AnalyticsNodeSearchTaskHandler(final AnnotationsDecoratorFactory annotationsDecoratorFactory,
                                   final SecurityContext securityContext,
                                   final ExecutorProvider executorProvider,
                                   final Executor executor,
                                   final TaskContextFactory taskContextFactory,
                                   final AnalyticDataStores analyticDataStores) {
        this.annotationsDecoratorFactory = annotationsDecoratorFactory;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.analyticDataStores = analyticDataStores;
    }

    @Override
    public void search(final TaskContext parentContext,
                       final NodeSearchTask task,
                       final Coprocessors coprocessors) {
        SearchProgressLog.increment(task.getKey(), SearchPhase.CLUSTER_SEARCH_TASK_HANDLER_EXEC);
        this.parentContext = parentContext;
        securityContext.useAsRead(() -> {
            if (!Thread.currentThread().isInterrupted()) {
                parentContext.info(() -> "Initialising...");

                // Start searching.
                doSearch(parentContext, task, task.getQuery(), coprocessors);
            }
        });
    }

    private void doSearch(final TaskContext parentContext,
                          final NodeSearchTask task,
                          final Query query,
                          final Coprocessors coprocessors) {
        parentContext.info(() -> "Searching...");
        LOGGER.debug(() -> "Incoming search request:\n" + query.getExpression().toString());

        // Start searching.
        SearchProgressLog.increment(task.getKey(), SearchPhase.CLUSTER_SEARCH_TASK_HANDLER_SEARCH);

        try {
            final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                    .addPrefixExcludeFilter(AnnotationFields.ANNOTATION_FIELD_PREFIX)
                    .build();
            final ExpressionOperator expression = expressionFilter.copy(query.getExpression());

            // Decorate result with annotations.
            ValuesConsumer valuesConsumer =
                    annotationsDecoratorFactory.create(coprocessors, coprocessors.getFieldIndex(), query);

            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            try {
                final FieldIndex fieldIndex = coprocessors.getFieldIndex();
                final Map<CIKey, QueryField> fieldMap = AnalyticFields.getFieldMap();
                final QueryField[] fieldArray = new QueryField[fieldIndex.size()];
                for (int i = 0; i < fieldArray.length; i++) {
                    final CIKey fieldName = fieldIndex.getFieldAsCIKey(i);
                    final QueryField field = fieldMap.get(fieldName);
                    if (field == null) {
                        throw new RuntimeException("Field '" + fieldName + "' is not valid for this datasource");
                    } else {
                        fieldArray[i] = field;
                    }
                }

                final ExpressionMatcher expressionMatcher = new ExpressionMatcher(fieldMap);
                final Set<AnalyticRuleDoc> currentRules = analyticDataStores.getCurrentRules();
                currentRules.forEach(doc -> {
                    final Runnable runnable = taskContextFactory
                            .childContext(parentContext, "Analytic Search - " + doc.getName(), taskContext ->
                                    searchAnalyticDoc(
                                            task,
                                            doc,
                                            expression,
                                            fieldIndex,
                                            taskContext,
                                            hitCount,
                                            valuesConsumer,
                                            coprocessors.getErrorConsumer(),
                                            fieldArray,
                                            expressionMatcher));
                    futures.add(CompletableFuture.runAsync(runnable));
                });
            } catch (final RuntimeException e) {
                coprocessors.getErrorConsumer().add(e);
            }

            // Create a countdown latch to keep updating status until we complete.
            final CountDownLatch complete = new CountDownLatch(1);

            // Wait for all to complete.
            final CompletableFuture<Void> all = CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]));
            all.whenCompleteAsync((r, t) -> complete.countDown(), executorProvider.get());

            // Update status until we complete.
            while (!complete.await(1, TimeUnit.SECONDS)) {
                updateInfo();
            }

            LOGGER.debug(() -> "Complete");
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        } catch (final RuntimeException e) {
            throw SearchException.wrap(e);
        }
    }

    private void searchAnalyticDoc(final NodeSearchTask task,
                                   final AnalyticRuleDoc doc,
                                   final ExpressionOperator expression,
                                   final FieldIndex fieldIndex,
                                   final TaskContext parentContext,
                                   final LongAdder hitCount,
                                   final ValuesConsumer valuesConsumer,
                                   final ErrorConsumer errorConsumer,
                                   final QueryField[] fieldArray,
                                   final ExpressionMatcher expressionMatcher) {
        try {

            final Optional<AnalyticDataStore> optionalAnalyticDataStore = analyticDataStores.getIfExists(doc);
            if (!parentContext.isTerminated() && optionalAnalyticDataStore.isPresent()) {
                final AnalyticDataStore analyticDataStore = optionalAnalyticDataStore.get();
                final SearchRequest searchRequest = analyticDataStore.getSearchRequest();
                final LmdbDataStore lmdbDataStore = analyticDataStore.getLmdbDataStore();

                try {
                    ResultRequest resultRequest = searchRequest.getResultRequests().get(0);
                    TableSettings tableSettings = resultRequest.getMappings().get(0);

                    tableSettings = tableSettings
                            .copy()
                            .maxResults(List.of(1000000L))
                            .build();
                    final List<TableSettings> mappings = List.of(tableSettings);
                    final OffsetRange requestRange = OffsetRange.ZERO_100;
                    final TimeFilter timeFilter = DateExpressionParser
                            .getTimeFilter(
                                    task.getQuery().getTimeRange(),
                                    task.getDateTimeSettings());
                    resultRequest = resultRequest
                            .copy()
                            .mappings(mappings)
                            .requestedRange(requestRange)
                            .timeFilter(timeFilter)
                            .build();

                    final TableResultConsumer tableResultConsumer = new TableResultConsumer(
                            doc, fieldArray, hitCount, valuesConsumer, expression, expressionMatcher);
                    final ColumnFormatter fieldFormatter =
                            new ColumnFormatter(new FormatterFactory(null));
                    final TableResultCreator resultCreator = new TableResultCreator(fieldFormatter) {
                        @Override
                        public TableResultBuilder createTableResultBuilder() {
                            return tableResultConsumer;
                        }
                    };

                    // Create result.
                    resultCreator.create(lmdbDataStore, resultRequest);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    errorConsumer.add(new RuntimeException(
                            "Error getting results for analytic '" +
                                    doc.getName() +
                                    "' (" +
                                    doc.getUuid() +
                                    ") - " +
                                    e.getMessage(), e));
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }

    @Override
    public void updateInfo() {
        parentContext.info(() -> "Searching... " +
                "found "
                + hitCount.sum() +
                " documents" +
                " performed " +
                extractionCount.sum() +
                " extractions");
    }

    private static class TableResultConsumer implements TableResultBuilder {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultConsumer.class);

        private final AnalyticRuleDoc analyticRuleDoc;

        private FieldIndex fieldIndex;
        private final QueryField[] requestedFields;
        private final LongAdder hitCount;
        private final ValuesConsumer consumer;

        private final ExpressionOperator expression;
        private final ExpressionMatcher expressionMatcher;

        private List<Column> columns;

        public TableResultConsumer(final AnalyticRuleDoc analyticRuleDoc,
                                   final QueryField[] requestedFields,
                                   final LongAdder hitCount,
                                   final ValuesConsumer consumer,
                                   final ExpressionOperator expression,
                                   final ExpressionMatcher expressionMatcher) {
            this.analyticRuleDoc = analyticRuleDoc;
            this.requestedFields = requestedFields;
            this.hitCount = hitCount;
            this.consumer = consumer;
            this.expression = expression;
            this.expressionMatcher = expressionMatcher;
        }

        @Override
        public TableResultConsumer componentId(final String componentId) {
            return this;
        }

        @Override
        public TableResultConsumer errors(final List<String> errors) {
            for (final String error : errors) {
                LOGGER.error(error);
            }
            return this;
        }

        @Override
        public TableResultConsumer columns(final List<Column> columns) {
            this.columns = columns;
            fieldIndex = new FieldIndex();
            columns.forEach(column -> fieldIndex.create(column.getNameAsCIKey()));
            return this;
        }

        @Override
        public TableResultConsumer addRow(final Row row) {
            try {
                Long time = null;
                final int timeFieldIndex = fieldIndex.getTimeFieldIndex();
                if (timeFieldIndex != -1) {
                    time = DateUtil.parseNormalDateTimeString(row.getValues().get(timeFieldIndex));
                }

                // Get value.
                final StringBuilder sb = new StringBuilder();
                for (int j = 0; j < columns.size(); j++) {
                    final Column column = columns.get(j);
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(column.getName());
                    sb.append(": ");
                    sb.append(row.getValues().get(j));
                }
                final String value = sb.toString();

                final Map<CIKey, Object> attributeMap = new HashMap<>();
                attributeMap.put(AnalyticFields.NAME_FIELD.getFldNameAsCIKey(), analyticRuleDoc.getName());
                attributeMap.put(AnalyticFields.UUID_FIELD.getFldNameAsCIKey(), analyticRuleDoc.getUuid());
                attributeMap.put(AnalyticFields.TIME_FIELD.getFldNameAsCIKey(), time);
                attributeMap.put(AnalyticFields.VALUE_FIELD.getFldNameAsCIKey(), value);

                if (expressionMatcher.match(attributeMap, expression)) {
                    hitCount.increment();
                    final Val[] values = new Val[requestedFields.length];
                    for (int i = 0; i < requestedFields.length; i++) {
                        final QueryField field = requestedFields[i];
                        if (field.equals(AnalyticFields.NAME_FIELD)) {
                            values[i] = ValString.create(analyticRuleDoc.getName());
                        } else if (field.equals(AnalyticFields.UUID_FIELD)) {
                            values[i] = ValString.create(analyticRuleDoc.getUuid());
                        } else if (field.equals(AnalyticFields.TIME_FIELD)) {
                            if (time == null) {
                                values[i] = ValNull.INSTANCE;
                            } else {
                                values[i] = ValDate.create(time);
                            }
                        } else if (field.equals(AnalyticFields.VALUE_FIELD)) {
                            values[i] = ValString.create(value);
                        }
                    }
                    consumer.accept(values);
                }

            } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }

            return this;
        }

        @Override
        public TableResultConsumer resultRange(final OffsetRange resultRange) {
            return this;
        }

        @Override
        public TableResultConsumer totalResults(final Long totalResults) {
            return this;
        }

        @Override
        public TableResult build() {
            return null;
        }
    }
}
