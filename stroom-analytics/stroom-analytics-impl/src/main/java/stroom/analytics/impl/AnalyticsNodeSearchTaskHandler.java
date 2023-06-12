/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.annotation.api.AnnotationFields;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValDate;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Field;
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
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import javax.inject.Inject;


class AnalyticsNodeSearchTaskHandler implements NodeSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticsNodeSearchTaskHandler.class);

    private final AnnotationsDecoratorFactory annotationsDecoratorFactory;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final AnalyticDataStores analyticDataStores;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;


    private final LongAdder hitCount = new LongAdder();
    private final LongAdder extractionCount = new LongAdder();

    private TaskContext parentContext;

    @Inject
    AnalyticsNodeSearchTaskHandler(final AnnotationsDecoratorFactory annotationsDecoratorFactory,
                                   final SecurityContext securityContext,
                                   final ExecutorProvider executorProvider,
                                   final Executor executor,
                                   final TaskContextFactory taskContextFactory,
                                   final AnalyticDataStores analyticDataStores,
                                   final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper) {
        this.annotationsDecoratorFactory = annotationsDecoratorFactory;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.analyticDataStores = analyticDataStores;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
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
                final Map<String, AbstractField> fieldMap = AnalyticFields.getFieldMap();
                final AbstractField[] fieldArray = new AbstractField[fieldIndex.size()];
                for (int i = 0; i < fieldArray.length; i++) {
                    final String fieldName = fieldIndex.getField(i);
                    final AbstractField field = fieldMap.get(fieldName);
                    if (field == null) {
                        throw new RuntimeException("Field '" + fieldName + "' is not valid for this datasource");
                    } else {
                        fieldArray[i] = field;
                    }
                }

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
                                            fieldArray));
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
                                   final AbstractField[] fieldArray) {
        try {
            final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(doc);
            final String componentId = analyticDataStores.getComponentId(searchRequest);
            final String dir = analyticDataStores.getAnalyticStoreDir(searchRequest.getKey(),
                    componentId);
            final Path path = analyticDataStores.getAnalyticResultStoreDir().resolve(dir);
            if (!parentContext.isTerminated() && Files.isDirectory(path)) {
                final LmdbDataStore lmdbDataStore = analyticDataStores.createStore(searchRequest);
                ResultRequest resultRequest = searchRequest.getResultRequests().get(0);
                TableSettings tableSettings = resultRequest.getMappings().get(0);
                tableSettings = tableSettings
                        .copy()
                        .maxResults(List.of(1000000))
                        .build();
                final List<TableSettings> mappings = List.of(tableSettings);
                final OffsetRange requestRange = OffsetRange.builder().offset(0L).length(100L).build();
                final TimeFilter timeFilter = DateExpressionParser
                        .getTimeFilter(
                                task.getQuery().getTimeRange(),
                                task.getDateTimeSettings(),
                                System.currentTimeMillis());
                resultRequest = resultRequest
                        .copy()
                        .mappings(mappings)
                        .requestedRange(requestRange)
                        .timeFilter(timeFilter)
                        .build();

                final TableResultConsumer tableResultConsumer = new TableResultConsumer(
                        doc, fieldArray, hitCount, valuesConsumer);
                final FieldFormatter fieldFormatter =
                        new FieldFormatter(new FormatterFactory(null));
                final TableResultCreator resultCreator = new TableResultCreator(
                        fieldFormatter,
                        Sizes.create(Integer.MAX_VALUE));

                // Create result.
                resultCreator.create(lmdbDataStore, resultRequest, tableResultConsumer);
            }
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


//
//
////    public ResultStore createResultStore(final SearchRequest searchRequest) {
////        final String taskName = "Analytics Search";
////
////        return taskContextFactory.contextResult(taskName, taskContext -> {
////            LOGGER.debug("create called for searchRequest {} ", searchRequest);
////
////            Preconditions.checkNotNull(searchRequest.getResultRequests(),
////                    "searchRequest must have at least one resultRequest");
////            Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(),
////                    "searchRequest must have at least one resultRequest");
////
////            // Replace expression parameters.
////            final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);
////
////            // Create a handler for search results.
////            final Coprocessors coprocessors =
////                    coprocessorsFactory.create(modifiedSearchRequest,
////                            DataStoreSettings.createBasicSearchResultStoreSettings());
////
////            return buildStore(taskContext,
////                    modifiedSearchRequest,
////                    coprocessors,
////                    modifiedSearchRequest.getQuery().getExpression());
////        }).get();
////    }
//
//    private ResultStore buildStore(final TaskContext parentTaskContext,
//                                   final SearchRequest searchRequest,
//                                   final Coprocessors coprocessors,
//                                   final ExpressionOperator expression) {
//        Preconditions.checkNotNull(searchRequest);
//
//        final Sizes storeSize = getStoreSizes();
//        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
//        final int resultHandlerBatchSize = getResultHandlerBatchSize();
//
//        final ResultStore resultStore = resultStoreFactory.create(
//                searchRequest.getSearchRequestSource(),
//                coprocessors);
//        final String searchKey = searchRequest.getKey().toString();
//        final String taskName = "Analytics Search";
//
//        final String infoPrefix = LogUtil.message(
//                "Querying {} {} - ",
//                "Analytics",
//                searchKey);
//
//        LOGGER.debug(() -> LogUtil.message("{} Starting search with key {}", taskName, searchKey));
//        parentTaskContext.info(() -> infoPrefix + "initialising query");
//
//        final ExpressionCriteria criteria = new ExpressionCriteria(expression);
//
//        final Map<String, AbstractField> fieldMap = AnalyticFields.getFieldMap();
//
//        final FieldIndex fieldIndex = coprocessors.getFieldIndex();
//        final AbstractField[] fieldArray = new AbstractField[fieldIndex.size()];
//        for (int i = 0; i < fieldArray.length; i++) {
//            final String fieldName = fieldIndex.getField(i);
//            final AbstractField field = fieldMap.get(fieldName);
//            if (field == null) {
//                throw new RuntimeException("Field '" + fieldName + "' is not valid for this datasource");
//            } else {
//                fieldArray[i] = field;
//            }
//        }
//
//        final Runnable runnable = taskContextFactory.context(taskName, taskContext -> {
//            final AtomicBoolean destroyed = new AtomicBoolean();
//
//            final SearchProcess searchProcess = new SearchProcess() {
//                @Override
//                public SearchTaskProgress getSearchTaskProgress() {
//                    final TaskProgress taskProgress =
//                            taskManager.getTaskProgress(taskContext);
//                    if (taskProgress != null) {
//                        return new SearchTaskProgress(
//                                taskProgress.getTaskName(),
//                                taskProgress.getTaskInfo(),
//                                taskProgress.getUserName(),
//                                taskProgress.getThreadName(),
//                                taskProgress.getNodeName(),
//                                taskProgress.getSubmitTimeMs(),
//                                taskProgress.getTimeNowMs());
//                    }
//                    return null;
//                }
//
//                @Override
//                public void onTerminate() {
//                    destroyed.set(true);
//                    taskManager.terminate(taskContext.getTaskId());
//                }
//            };
//
//            // Set the search process.
//            resultStore.setSearchProcess(searchProcess);
//
//            // Don't begin execution if we have been asked to complete already.
//            if (!destroyed.get()) {
//                taskContext.info(() -> infoPrefix + "running query");
//
//                final Instant queryStart = Instant.now();
//                try {
//                    // Give the data array to each of our coprocessors
//                    search(
//                            searchRequest.getQuery().getTimeRange(),
//                            searchRequest.getDateTimeSettings(),
//                            criteria,
//                            fieldArray,
//                            coprocessors);
//
//                } catch (final RuntimeException e) {
//                    LOGGER.debug(e::getMessage, e);
//                    resultStore.addError(e);
//                }
//
//                LOGGER.debug(() ->
//                        String.format("%s complete called, counter: %s",
//                                taskName,
//                                coprocessors.getValueCount()));
//                taskContext.info(() -> infoPrefix + "complete");
//                LOGGER.debug(() -> taskName + " completeSearch called");
//                resultStore.signalComplete();
//                LOGGER.debug(() -> taskName + " Query finished in " + Duration.between(queryStart, Instant.now()));
//            }
//        });
//        CompletableFuture.runAsync(runnable, executor);
//
//        return resultStore;
//    }
//
//    private Sizes getDefaultMaxResultsSizes() {
//        final String value = clientConfig.getDefaultMaxResults();
//        return extractValues(value);
//    }
//
//    private Sizes getStoreSizes() {
//        final String value = config.getStoreSize();
//        return extractValues(value);
//    }
//
//    private int getResultHandlerBatchSize() {
//        return 5000;
//    }
//
//    private Sizes extractValues(String value) {
//        if (value != null) {
//            try {
//                return Sizes.create(Arrays.stream(value.split(","))
//                        .map(String::trim)
//                        .map(Integer::valueOf)
//                        .collect(Collectors.toList()));
//            } catch (final Exception e) {
//                LOGGER.warn(e.getMessage());
//            }
//        }
//        return Sizes.create(Integer.MAX_VALUE);
//    }
//
//    public CacheInfoResponse info(final String cacheName, final String nodeName) {
//        final List<CacheInfo> cacheInfoList;
//        // If this is the node that was contacted then just return our local info.
//        if (NodeCallUtil.shouldExecuteLocally(nodeInfo.get(), nodeName)) {
//            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
//            criteria.setName(new StringCriteria(cacheName, null));
//            cacheInfoList = cacheManagerService.get().find(criteria);
//        } else {
//            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo.get(), nodeService.get(), nodeName)
//                    + ResourcePaths.buildAuthenticatedApiPath(CacheResource.INFO_PATH);
//            try {
//                WebTarget webTarget = webTargetFactory.get().create(url);
//                webTarget = UriBuilderUtil.addParam(webTarget, "cacheName", cacheName);
//                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
//                CacheInfoResponse result;
//                try (Response response = webTarget
//                        .request(MediaType.APPLICATION_JSON)
//                        .get()) {
//                    if (response.getStatus() != 200) {
//                        throw new WebApplicationException(response);
//                    }
//                    result = response.readEntity(CacheInfoResponse.class);
//                }
//                if (result == null) {
//                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
//                }
//                cacheInfoList = result.getValues();
//            } catch (Exception e) {
//                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
//            }
//        }
//
//        // Add the node name.
//        final List<CacheInfo> decoratedCacheInfoList = cacheInfoList.stream()
//                .map(cacheInfo -> cacheInfo.withNodeName(nodeName))
//                .collect(Collectors.toList());
//
//        return new CacheInfoResponse(decoratedCacheInfoList);
//    }
//
//
//    public void search(final TimeRange timeRange,
//                       final DateTimeSettings dateTimeSettings,
//                       final ExpressionCriteria criteria,
//                       final AbstractField[] fields,
//                       final ValuesConsumer consumer) {
//        final OffsetRange requestRange = OffsetRange.builder().offset(0L).length(100L).build();
//        final Set<AnalyticRuleDoc> currentRules = analyticDataStores.getCurrentRules();
//        currentRules.forEach(doc -> {
//            try {
//                final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(doc);
//                final String componentId = analyticDataStores.getComponentId(searchRequest);
//                final String dir = analyticDataStores.getAnalyticStoreDir(searchRequest.getKey(), componentId);
//                final Path path = analyticDataStores.getAnalyticResultStoreDir().resolve(dir);
//                if (Files.isDirectory(path)) {
//
//                    final LmdbDataStore lmdbDataStore = analyticDataStores.createStore(searchRequest);
//                    ResultRequest resultRequest = searchRequest.getResultRequests().get(0);
//                    TableSettings tableSettings = resultRequest.getMappings().get(0);
//                    tableSettings = tableSettings
//                            .copy()
//                            .maxResults(List.of(1000000))
//                            .build();
//                    final List<TableSettings> mappings = List.of(tableSettings);
//                    final TimeFilter timeFilter = DateExpressionParser
//                            .getTimeFilter(
//                                    timeRange,
//                                    dateTimeSettings,
//                                    System.currentTimeMillis());
//                    resultRequest = resultRequest
//                            .copy()
//                            .mappings(mappings)
//                            .requestedRange(requestRange)
//                            .timeFilter(timeFilter)
//                            .build();
//
//                    final TableResultConsumer tableResultConsumer = new TableResultConsumer(
//                            doc, lmdbDataStore.getFieldIndex(), fields, consumer);
//                    final FieldFormatter fieldFormatter =
//                            new FieldFormatter(new FormatterFactory(null));
//                    final TableResultCreator resultCreator = new TableResultCreator(
//                            fieldFormatter,
//                            Sizes.create(Integer.MAX_VALUE));
//
//                    // Create result.
//                    resultCreator.create(lmdbDataStore, resultRequest, tableResultConsumer);
//                }
//            } catch (final RuntimeException e) {
//                LOGGER.debug(e::getMessage, e);
//                throw e;
//            }
//        });
//    }


    private static class TableResultConsumer implements TableResultBuilder {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultConsumer.class);

        private final AnalyticRuleDoc analyticRuleDoc;

        private FieldIndex fieldIndex;
        private final AbstractField[] requestedFields;
        private final LongAdder hitCount;
        private final ValuesConsumer consumer;

        private List<Field> fields;

        public TableResultConsumer(final AnalyticRuleDoc analyticRuleDoc,
                                   final AbstractField[] requestedFields,
                                   final LongAdder hitCount,
                                   final ValuesConsumer consumer) {
            this.analyticRuleDoc = analyticRuleDoc;
            this.requestedFields = requestedFields;
            this.hitCount = hitCount;
            this.consumer = consumer;
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
        public TableResultConsumer fields(final List<Field> fields) {
            this.fields = fields;
            fieldIndex = new FieldIndex();
            fields.forEach(field -> fieldIndex.create(field.getName()));
            return this;
        }

        @Override
        public TableResultConsumer addRow(final Row row) {
            try {
                hitCount.increment();
                final Val[] values = new Val[requestedFields.length];
                for (int i = 0; i < requestedFields.length; i++) {
                    final AbstractField field = requestedFields[i];
                    if (field.equals(AnalyticFields.NAME_FIELD)) {
                        values[i] = ValString.create(analyticRuleDoc.getName());
                    } else if (field.equals(AnalyticFields.UUID_FIELD)) {
                        values[i] = ValString.create(analyticRuleDoc.getUuid());
                    } else if (field.equals(AnalyticFields.TIME_FIELD)) {
                        final int timeFieldPos = fieldIndex.getTimeFieldPos();
                        if (timeFieldPos != -1) {
                            values[i] = ValDate.create(row.getValues().get(timeFieldPos));
                        }
                    } else if (field.equals(AnalyticFields.VALUE_FIELD)) {
                        final StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < fields.size(); j++) {
                            final Field f = fields.get(j);
                            if (sb.length() > 0) {
                                sb.append(", ");
                            }
                            sb.append(f.getName());
                            sb.append(": ");
                            sb.append(row.getValues().get(j));
                        }
                        values[i] = ValString.create(sb.toString());
                    }
                }

                consumer.add(values);

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
        public TableResultConsumer totalResults(final Integer totalResults) {
            return this;
        }

        @Override
        public TableResult build() {
            return null;
        }
    }
}
