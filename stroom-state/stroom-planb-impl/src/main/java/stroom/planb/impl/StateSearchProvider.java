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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexFieldImpl;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.shared.PlanBDoc;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class StateSearchProvider implements SearchProvider, IndexFieldProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateSearchProvider.class);

    private final Executor executor;
    private final PlanBDocStore stateDocStore;
    private final PlanBDocCache stateDocCache;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;
    private final ShardManager shardManager;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final SecurityContext securityContext;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    public StateSearchProvider(final Executor executor,
                               final PlanBDocStore stateDocStore,
                               final PlanBDocCache stateDocCache,
                               final CoprocessorsFactory coprocessorsFactory,
                               final ResultStoreFactory resultStoreFactory,
                               final TaskManager taskManager,
                               final TaskContextFactory taskContextFactory,
                               final ShardManager shardManager,
                               final ExpressionPredicateFactory expressionPredicateFactory,
                               final SecurityContext securityContext,
                               final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.executor = executor;
        this.stateDocStore = stateDocStore;
        this.stateDocCache = stateDocCache;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
        this.shardManager = shardManager;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.securityContext = securityContext;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
    }

    private PlanBDoc getPlanBDoc(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            Objects.requireNonNull(docRef, "Null doc reference");
            Objects.requireNonNull(docRef.getName(), "Null doc key");
            final PlanBDoc doc = stateDocCache.get(docRef.getName());
            Objects.requireNonNull(doc, "Null state doc");
            return doc;
        });
    }

    @Override
    public String getDataSourceType() {
        return PlanBDoc.TYPE;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return stateDocStore.list();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        final PlanBDoc doc = getPlanBDoc(docRef);
        return Optional.ofNullable(StateFieldUtil.getTimeField(doc));
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        final PlanBDoc doc = getPlanBDoc(criteria.getDataSourceRef());
        final List<QueryField> fields = StateFieldUtil.getQueryableFields(doc);
        return fieldInfoResultPageFactory.create(criteria, fields);
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        final PlanBDoc doc = getPlanBDoc(docRef);
        return NullSafe.getOrElse(
                doc,
                d -> StateFieldUtil.getQueryableFields(doc),
                List::size,
                0);
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        final PlanBDoc doc = getPlanBDoc(docRef);
        final Map<String, QueryField> fieldMap = StateFieldUtil.getFieldMap(doc);
        final QueryField queryField = fieldMap.get(fieldName);
        if (queryField == null) {
            return null;
        }
        return IndexFieldImpl.builder().fldName(queryField.getFldName()).fldType(queryField.getFldType()).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(getPlanBDoc(docRef)).map(PlanBDoc::getDescription);
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the doc.
        final DocRef docRef = query.getDataSource();

        // Check we have permission to read the doc.
        final PlanBDoc doc = getPlanBDoc(docRef);
        Objects.requireNonNull(doc, "Unable to find state doc with key: " + docRef.getName());

        // Extract highlights.
        final Set<String> highlights = Collections.emptySet();

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest);

        // Create a handler for search results.
        final DataStoreSettings dataStoreSettings = DataStoreSettings
                .createBasicSearchResultStoreSettings();
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                modifiedSearchRequest.getDateTimeSettings(),
                modifiedSearchRequest.getKey(),
                coprocessorSettingsList,
                query.getParams(),
                dataStoreSettings);

        // Create an asynchronous search task.
        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";

        // Create the search result collector.
        final ResultStore resultStore = resultStoreFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                coprocessors);
        resultStore.addHighlights(highlights);

        final String infoPrefix = LogUtil.message(
                "Querying {} {} - ",
                getStoreName(docRef),
                modifiedSearchRequest.getKey().toString());
        final String taskName = getTaskName(docRef);
        final ExpressionCriteria criteria = new ExpressionCriteria(query.getExpression());
        final Runnable runnable = taskContextFactory.context(searchName, taskContext -> {
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
                    shardManager.get(doc.getName(), reader -> {
                        reader.search(
                                criteria,
                                coprocessors.getFieldIndex(),
                                searchRequest.getDateTimeSettings(),
                                expressionPredicateFactory,
                                coprocessors);
                        return null;
                    });
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
}
