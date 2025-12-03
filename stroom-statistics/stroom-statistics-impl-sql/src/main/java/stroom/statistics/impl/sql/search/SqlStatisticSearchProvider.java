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

package stroom.statistics.impl.sql.search;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.Sizes;
import stroom.statistics.impl.sql.Statistics;
import stroom.statistics.impl.sql.entity.StatisticStoreCache;
import stroom.statistics.impl.sql.entity.StatisticStoreStore;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class SqlStatisticSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticSearchProvider.class);
    public static final String TASK_NAME = "Sql Statistic Search";

    private final StatisticStoreStore statisticStoreStore;
    private final StatisticStoreCache statisticStoreCache;
    private final StatisticsSearchService statisticsSearchService;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;
    private final TaskManager taskManager;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final Statistics statistics;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    public SqlStatisticSearchProvider(final StatisticStoreStore statisticStoreStore,
                                      final StatisticStoreCache statisticStoreCache,
                                      final StatisticsSearchService statisticsSearchService,
                                      final TaskContextFactory taskContextFactory,
                                      final Executor executor,
                                      final TaskManager taskManager,
                                      final SearchConfig searchConfig,
                                      final UiConfig clientConfig,
                                      final CoprocessorsFactory coprocessorsFactory,
                                      final ResultStoreFactory resultStoreFactory,
                                      final Statistics statistics,
                                      final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.statisticStoreStore = statisticStoreStore;
        this.statisticStoreCache = statisticStoreCache;
        this.statisticsSearchService = statisticsSearchService;
        this.taskContextFactory = taskContextFactory;
        this.executor = executor;
        this.taskManager = taskManager;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.statistics = statistics;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        final StatisticStoreDoc entity = statisticStoreCache.getStatisticsDataSource(criteria.getDataSourceRef());
        if (entity == null) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, buildFields(entity));
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        final StatisticStoreDoc entity = statisticStoreCache.getStatisticsDataSource(docRef);
        // We could just get a count without building fields, but we end up duplicating logic
        return NullSafe.getOrElse(
                entity,
                this::buildFields,
                List::size,
                0);
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(statisticStoreCache.getStatisticsDataSource(docRef))
                .map(StatisticStoreDoc::getDescription);
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.of(QueryField.createDate(StatisticStoreDoc.FIELD_NAME_DATE_TIME));
    }

    /**
     * Turn the {@link StatisticStoreDoc} into an {@link List<QueryField>} object
     * <p>
     * This builds the standard set of fields for a statistics store, which can
     * be filtered by the relevant statistics store instance
     */
    private List<QueryField> buildFields(final StatisticStoreDoc entity) {
        List<QueryField> fields = new ArrayList<>();

        // TODO currently only BETWEEN is supported, but need to add support for
        // more conditions like >, >=, <, <=, =
        fields.add(QueryField
                .builder()
                .fldName(StatisticStoreDoc.FIELD_NAME_DATE_TIME)
                .fldType(FieldType.DATE)
                .conditionSet(ConditionSet.STAT_DATE)
                .queryable(true)
                .build());

        // one field per tag
        if (entity.getConfig() != null) {
            for (final StatisticField statisticField : entity.getStatisticFields()) {
                // TODO currently only EQUALS is supported, but need to add
                // support for more conditions like CONTAINS
                fields.add(QueryField
                        .builder()
                        .fldName(statisticField.getFieldName())
                        .fldType(FieldType.TEXT)
                        .conditionSet(ConditionSet.STAT_TEXT)
                        .queryable(true)
                        .build());
            }
        }

        fields.add(QueryField.createLong(StatisticStoreDoc.FIELD_NAME_COUNT, false));

        if (entity.getStatisticType().equals(StatisticType.VALUE)) {
            fields.add(QueryField.createLong(StatisticStoreDoc.FIELD_NAME_VALUE, false));
        }

        fields.add(QueryField.createLong(StatisticStoreDoc.FIELD_NAME_PRECISION_MS, false));

        // Filter fields.
        if (entity.getConfig() != null) {
            fields = statistics.getSupportedFields(fields);
        }

        return fields;
    }


    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        LOGGER.debug("create called for searchRequest {} ", searchRequest);

        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                                Preconditions.checkNotNull(searchRequest)
                                        .getQuery())
                        .getDataSource());
        Preconditions.checkNotNull(searchRequest.getResultRequests(),
                "searchRequest must have at least one resultRequest");
        Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(),
                "searchRequest must have at least one resultRequest");

        final StatisticStoreDoc statisticStoreDoc = statisticStoreCache.getStatisticsDataSource(docRef);

        Preconditions.checkNotNull(statisticStoreDoc, "Statistic configuration could not be found for uuid "
                                                      + docRef.getUuid());

        return buildStore(searchRequest, statisticStoreDoc);
    }

    private ResultStore buildStore(final SearchRequest searchRequest,
                                   final StatisticStoreDoc statisticStoreDoc) {
        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreDoc);

        final String searchKey = searchRequest.getKey().toString();

        // convert the search into something stats understands
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);
        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(
                statisticStoreDoc,
                modifiedSearchRequest.getQuery().getExpression(),
                modifiedSearchRequest.getDateTimeSettings());

        // Create coprocessors.
        final CoprocessorsImpl coprocessors =
                coprocessorsFactory.create(modifiedSearchRequest,
                        DataStoreSettings.createBasicSearchResultStoreSettings());
        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);

        final Runnable runnable = taskContextFactory.context(TASK_NAME, taskContext -> {
            try {
                final AtomicBoolean destroyed = new AtomicBoolean();

                final SearchProcess searchProcess = new SearchProcess() {
                    @Override
                    public SearchTaskProgress getSearchTaskProgress() {
                        final TaskProgress taskProgress =
                                taskManager.getTaskProgress(taskContext);
                        if (taskProgress != null) {
                            return new SearchTaskProgress(
                                    taskProgress.getTaskName(),
                                    taskProgress.getTaskInfo(),
                                    taskProgress.getUserRef(),
                                    taskProgress.getThreadName(),
                                    taskProgress.getNodeName(),
                                    taskProgress.getSubmitTimeMs(),
                                    taskProgress.getTimeNowMs());
                        }
                        return null;
                    }

                    @Override
                    public void onTerminate() {
                        destroyed.set(true);
                        taskManager.terminate(taskContext.getTaskId());
                    }
                };

                // Set the search process.
                resultStore.setSearchProcess(searchProcess);

                // Don't begin execution if we have been asked to complete already.
                if (!destroyed.get()) {
                    // Create the object that will receive results.
                    LOGGER.debug(() -> "Starting search with key " + searchKey);
                    taskContext.info(() -> "Sql Statistics search " + searchKey + " - running query");

                    // Execute the search asynchronously.
                    // We have to create a wrapped runnable so that the task context references a managed task.
                    statisticsSearchService.search(
                            taskContext, statisticStoreDoc, criteria, coprocessors.getFieldIndex(), coprocessors,
                            coprocessors.getErrorConsumer());
                }

                coprocessors.getCompletionState().signalComplete();
                coprocessors.getCompletionState().awaitCompletion();
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }
        });
        executor.execute(runnable);

        LOGGER.debug(() -> "Async search task started for key " + searchKey);

        return resultStore;
    }

    private Sizes extractValues(final String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Long::valueOf)
                        .collect(Collectors.toList()));
            } catch (final Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.unlimited();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return statisticStoreStore.list();
    }

    @Override
    public String getDataSourceType() {
        return StatisticStoreDoc.TYPE;
    }
}
