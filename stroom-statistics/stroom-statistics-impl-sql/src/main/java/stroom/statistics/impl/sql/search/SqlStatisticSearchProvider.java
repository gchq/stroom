package stroom.statistics.impl.sql.search;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.Sizes;
import stroom.statistics.impl.sql.Statistics;
import stroom.statistics.impl.sql.entity.StatisticStoreCache;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;

@SuppressWarnings("unused")
public class SqlStatisticSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticSearchProvider.class);
    public static final String TASK_NAME = "Sql Statistic Search";

    private final StatisticStoreCache statisticStoreCache;
    private final StatisticsSearchService statisticsSearchService;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;
    private final TaskManager taskManager;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final Statistics statistics;

    @Inject
    public SqlStatisticSearchProvider(final StatisticStoreCache statisticStoreCache,
                                      final StatisticsSearchService statisticsSearchService,
                                      final TaskContextFactory taskContextFactory,
                                      final Executor executor,
                                      final TaskManager taskManager,
                                      final SearchConfig searchConfig,
                                      final UiConfig clientConfig,
                                      final CoprocessorsFactory coprocessorsFactory,
                                      final ResultStoreFactory resultStoreFactory,
                                      final Statistics statistics) {
        this.statisticStoreCache = statisticStoreCache;
        this.statisticsSearchService = statisticsSearchService;
        this.taskContextFactory = taskContextFactory;
        this.executor = executor;
        this.taskManager = taskManager;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.statistics = statistics;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        final StatisticStoreDoc entity = statisticStoreCache.getStatisticsDataSource(docRef);
        if (entity == null) {
            return null;
        }

        final List<AbstractField> fields = buildFields(entity);

        return DataSource
                .builder()
                .fields(fields)
                .build();
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return new DateField(StatisticStoreDoc.FIELD_NAME_DATE_TIME);
    }

    /**
     * Turn the {@link StatisticStoreDoc} into an {@link List<AbstractField>} object
     * <p>
     * This builds the standard set of fields for a statistics store, which can
     * be filtered by the relevant statistics store instance
     */
    private List<AbstractField> buildFields(final StatisticStoreDoc entity) {
        List<AbstractField> fields = new ArrayList<>();

        // TODO currently only BETWEEN is supported, but need to add support for
        // more conditions like >, >=, <, <=, =
        fields.add(new DateField(StatisticStoreDoc.FIELD_NAME_DATE_TIME,
                true,
                Collections.singletonList(Condition.BETWEEN)));

        // one field per tag
        if (entity.getConfig() != null) {
            final List<Condition> supportedConditions = Arrays.asList(Condition.EQUALS, Condition.IN);

            for (final StatisticField statisticField : entity.getStatisticFields()) {
                // TODO currently only EQUALS is supported, but need to add
                // support for more conditions like CONTAINS
                fields.add(new TextField(statisticField.getFieldName(), true, supportedConditions));
            }
        }

        fields.add(new LongField(StatisticStoreDoc.FIELD_NAME_COUNT, false, Collections.emptyList()));

        if (entity.getStatisticType().equals(StatisticType.VALUE)) {
            fields.add(new LongField(StatisticStoreDoc.FIELD_NAME_VALUE, false, Collections.emptyList()));
        }

        fields.add(new LongField(StatisticStoreDoc.FIELD_NAME_PRECISION_MS, false, Collections.emptyList()));

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
        final Coprocessors coprocessors =
                coprocessorsFactory.create(modifiedSearchRequest, DataStoreSettings.BASIC_SETTINGS);
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
                                    taskProgress.getUserName(),
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

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }

    @Override
    public String getType() {
        return StatisticStoreDoc.DOCUMENT_TYPE;
    }
}
