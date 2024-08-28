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

package stroom.state.impl;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.common.v2.IndexFieldMap;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.state.impl.dao.DaoFactory;
import stroom.state.impl.dao.StateFieldUtil;
import stroom.state.shared.StateDoc;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.string.CIKey;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
    private final StateDocStore stateDocStore;
    private final StateDocCache stateDocCache;
    private final CqlSessionFactory cqlSessionFactory;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public StateSearchProvider(final Executor executor,
                               final StateDocStore stateDocStore,
                               final StateDocCache stateDocCache,
                               final CqlSessionFactory cqlSessionFactory,
                               final CoprocessorsFactory coprocessorsFactory,
                               final ResultStoreFactory resultStoreFactory,
                               final TaskManager taskManager,
                               final TaskContextFactory taskContextFactory) {
        this.executor = executor;
        this.stateDocStore = stateDocStore;
        this.stateDocCache = stateDocCache;
        this.cqlSessionFactory = cqlSessionFactory;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
    }

    private StateDoc getStateDoc(final DocRef docRef) {
        Objects.requireNonNull(docRef, "Null doc reference");
        Objects.requireNonNull(docRef.getName(), "Null doc name");
        final StateDoc doc = stateDocCache.get(docRef.getName());
        Objects.requireNonNull(doc, "Null state doc");
        return doc;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        final StateDoc doc = getStateDoc(criteria.getDataSourceRef());
        final List<QueryField> fields = StateFieldUtil.getQueryableFields(doc.getStateType());
        return FieldInfoResultPageBuilder
                .builder(criteria)
                .addAll(fields)
                .build();
    }

    @Override
    public IndexFieldMap getIndexFields(final DocRef docRef, final CIKey fieldName) {
        final StateDoc doc = getStateDoc(docRef);
        final Map<CIKey, QueryField> fieldMap = StateFieldUtil.getFieldMap(doc.getStateType());
        final QueryField queryField = fieldMap.get(fieldName);

        if (queryField == null) {
            return null;
        } else {
            final IndexFieldImpl indexField = IndexFieldImpl.builder()
                    .fldName(queryField.getFldName())
                    .fldType(queryField.getFldType())
                    .build();

            return IndexFieldMap.forSingleField(fieldName, indexField);
        }
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(stateDocCache.get(docRef.getName())).map(StateDoc::getDescription);
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return null;
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
        final StateDoc doc = stateDocCache.get(docRef.getName());
        Objects.requireNonNull(doc, "Unable to find state doc with name: " + docRef.getName());
        final Provider<CqlSession> sessionProvider = cqlSessionFactory.getSessionProvider(doc.getScyllaDbRef());

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
                    DaoFactory.create(sessionProvider, doc.getStateType(), doc.getName()).search(
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

    @Override
    public QueryField getTimeField(final DocRef docRef) {
        final StateDoc doc = getStateDoc(docRef);
        return StateFieldUtil.getTimeField(doc.getStateType());
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

    @Override
    public List<DocRef> list() {
        return stateDocStore.list();
    }

    @Override
    public String getType() {
        return StateDoc.DOCUMENT_TYPE;
    }
}
