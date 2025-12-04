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

package stroom.searchable.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.Sizes;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.common.base.Preconditions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
class SearchableSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchableSearchProvider.class);

    private final Executor executor;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;
    private final UiConfig clientConfig;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final SecurityContext securityContext;
    private final Searchable searchable;

    SearchableSearchProvider(final Executor executor,
                             final TaskManager taskManager,
                             final TaskContextFactory taskContextFactory,
                             final UiConfig clientConfig,
                             final CoprocessorsFactory coprocessorsFactory,
                             final ResultStoreFactory resultStoreFactory,
                             final SecurityContext securityContext,
                             final Searchable searchable) {
        this.executor = executor;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
        this.clientConfig = clientConfig;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.securityContext = securityContext;
        this.searchable = searchable;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        final Optional<ResultPage<QueryField>> optional = securityContext.useAsReadResult(() ->
                Optional.ofNullable(searchable.getFieldInfo(criteria)));
        return optional.orElseGet(() -> {
            final List<QueryField> list = Collections.emptyList();
            return ResultPage.createCriterialBasedList(list, criteria);
        });
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return searchable.getFieldCount(docRef);
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return searchable.fetchDocumentation(docRef);
    }

    @Override
    public Optional<DocRef> fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return searchable.fetchDefaultExtractionPipeline(dataSourceRef);
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                                Preconditions.checkNotNull(searchRequest)
                                        .getQuery())
                        .getDataSource());
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
            final CoprocessorsImpl coprocessors =
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
                                   final CoprocessorsImpl coprocessors,
                                   final ExpressionOperator expression) {
        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(searchable);

        final List<DocRef> docRefs = searchable.getDataSourceDocRefs();
        if (docRefs == null || docRefs.isEmpty()) {
            throw new RuntimeException("Unable to access data source");
        }

        final DocRef docRef = docRefs.getFirst();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final int resultHandlerBatchSize = getResultHandlerBatchSize();

        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);
        final String searchKey = searchRequest.getKey().toString();
        final String taskName = getTaskName(docRef);

        final String infoPrefix = LogUtil.message(
                "Querying {} {} - ",
                getStoreName(docRef),
                searchKey);

        LOGGER.debug(() -> LogUtil.message("{} Starting search with key {}", taskName, searchKey));
        parentTaskContext.info(() -> infoPrefix + "initialising query");

        final ExpressionCriteria criteria = new ExpressionCriteria(expression);

        final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                new PageRequest(0, 1000),
                FindFieldCriteria.DEFAULT_SORT_LIST,
                docRef);
        final ResultPage<QueryField> resultPage = searchable.getFieldInfo(findFieldInfoCriteria);
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
                taskContext.info(() -> infoPrefix + "running query");

                final Instant queryStart = Instant.now();
                try {
                    // Give the data array to each of our coprocessors
                    searchable.search(
                            criteria,
                            coprocessors.getFieldIndex(),
                            searchRequest.getDateTimeSettings(),
                            coprocessors);

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

    private int getResultHandlerBatchSize() {
        return 5000;
    }

    private Sizes extractValues(final String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Long::valueOf)
                        .toList());
            } catch (final Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.unlimited();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return searchable.getTimeField(docRef);
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return searchable.getDataSourceDocRefs();
    }

    @Override
    public String getDataSourceType() {
        return searchable.getDataSourceType();
    }
}
