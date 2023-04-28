package stroom.searchable.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreConfig;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.Sizes;
import stroom.searchable.api.Searchable;
import stroom.searchable.api.SearchableProvider;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.ui.config.shared.UiConfig;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;

@SuppressWarnings("unused")
class SearchableSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchableSearchProvider.class);

    private final Executor executor;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;
    private final ResultStoreConfig config;
    private final UiConfig clientConfig;
    private final SearchableProvider searchableProvider;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final SecurityContext securityContext;

    @Inject
    SearchableSearchProvider(final Executor executor,
                             final TaskManager taskManager,
                             final TaskContextFactory taskContextFactory,
                             final ResultStoreConfig config,
                             final UiConfig clientConfig,
                             final SearchableProvider searchableProvider,
                             final CoprocessorsFactory coprocessorsFactory,
                             final ResultStoreFactory resultStoreFactory,
                             final SecurityContext securityContext) {
        this.executor = executor;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
        this.config = config;
        this.clientConfig = clientConfig;
        this.searchableProvider = searchableProvider;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.securityContext = securityContext;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            LOGGER.debug(() -> "getDataSource called for docRef " + docRef);
            final Searchable searchable = searchableProvider.get(docRef);
            if (searchable == null) {
                return null;
            }
            return searchable.getDataSource();
        });
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                                Preconditions.checkNotNull(searchRequest)
                                        .getQuery())
                        .getDataSource());
        final Searchable searchable = searchableProvider.get(docRef);
        Preconditions.checkNotNull(searchable, "Searchable could not be found for uuid " + docRef.getUuid());

        final String taskName = getTaskName(docRef);

        return taskContextFactory.contextResult(taskName, taskContext -> {
            LOGGER.debug("create called for searchRequest {} ", searchRequest);

            Preconditions.checkNotNull(searchRequest.getResultRequests(),
                    "searchRequest must have at least one resultRequest");
            Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(),
                    "searchRequest must have at least one resultRequest");

            Preconditions.checkNotNull(searchable, "Searchable could not be found for " + docRef);

            // Replace expression parameters.
            final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

            // Create a handler for search results.
            final Coprocessors coprocessors =
                    coprocessorsFactory.create(modifiedSearchRequest,
                            DataStoreSettings.createBasicSearchResultStoreSettings());

            return buildStore(taskContext,
                    modifiedSearchRequest,
                    searchable,
                    coprocessors,
                    modifiedSearchRequest.getQuery().getExpression());
        }).get();
    }

    private ResultStore buildStore(final TaskContext parentTaskContext,
                                   final SearchRequest searchRequest,
                                   final Searchable searchable,
                                   final Coprocessors coprocessors,
                                   final ExpressionOperator expression) {
        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(searchable);

        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final int resultHandlerBatchSize = getResultHandlerBatchSize();

        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);
        final String searchKey = searchRequest.getKey().toString();
        final String taskName = getTaskName(searchable.getDocRef());

        final String infoPrefix = LogUtil.message(
                "Querying {} {} - ",
                getStoreName(searchable.getDocRef()),
                searchKey);

        LOGGER.debug(() -> LogUtil.message("{} Starting search with key {}", taskName, searchKey));
        parentTaskContext.info(() -> infoPrefix + "initialising query");

        final ExpressionCriteria criteria = new ExpressionCriteria(expression);

        final Map<String, AbstractField> fieldMap = searchable.getDataSource().getFields()
                .stream()
                .collect(Collectors.toMap(AbstractField::getName, Function.identity()));

        final FieldIndex fieldIndex = coprocessors.getFieldIndex();
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

        final Runnable runnable = taskContextFactory.context(taskName, taskContext -> {
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
                taskContext.info(() -> infoPrefix + "running query");

                final Instant queryStart = Instant.now();
                try {
                    // Give the data array to each of our coprocessors
                    searchable.search(criteria, fieldArray, coprocessors);

                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    resultStore.addError(e);
                }

                LOGGER.debug(() ->
                        String.format("%s complete called, counter: %s",
                                taskName,
                                coprocessors.getValueCount()));
                taskContext.info(() -> infoPrefix + "complete");
                LOGGER.debug(() -> taskName + " completeSearch called");
                resultStore.signalComplete();
                LOGGER.debug(() -> taskName + " Query finished in " + Duration.between(queryStart, Instant.now()));
            }
        });
        CompletableFuture.runAsync(runnable, executor);

        return resultStore;
    }

    private String getStoreName(final DocRef docRef) {
        return NullSafe.toStringOrElse(
                docRef,
                DocRef::getName,
                "Unknown Store");
    }

    private String getTaskName(final DocRef docRef) {
        return getStoreName(docRef) + " Search";
    }

    private Sizes getDefaultMaxResultsSizes() {
        final String value = clientConfig.getDefaultMaxResults();
        return extractValues(value);
    }

    private Sizes getStoreSizes() {
        final String value = config.getStoreSize();
        return extractValues(value);
    }

    private int getResultHandlerBatchSize() {
        return 5000;
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (final Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return searchableProvider.get(docRef).getTimeField();
    }

    @Override
    public String getType() {
        return "Searchable";
    }
}
