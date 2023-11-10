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

package stroom.search.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.index.impl.IndexStore;
import stroom.index.impl.LuceneProviderFactory;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.LuceneVersionUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProvider;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class LuceneSearchProvider implements SearchProvider {

    private final IndexStore indexStore;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;
    private final FederatedSearchExecutor federatedSearchExecutor;
    private final NodeSearchTaskCreator nodeSearchTaskCreator;
    private final LuceneProviderFactory luceneProviderFactory;

    @Inject
    public LuceneSearchProvider(final IndexStore indexStore,
                                final SecurityContext securityContext,
                                final CoprocessorsFactory coprocessorsFactory,
                                final ResultStoreFactory resultStoreFactory,
                                final TaskContextFactory taskContextFactory,
                                final ExecutorProvider executorProvider,
                                final FederatedSearchExecutor federatedSearchExecutor,
                                final NodeSearchTaskCreator nodeSearchTaskCreator,
                                final LuceneProviderFactory luceneProviderFactory) {
        this.indexStore = indexStore;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
        this.federatedSearchExecutor = federatedSearchExecutor;
        this.nodeSearchTaskCreator = nodeSearchTaskCreator;
        this.luceneProviderFactory = luceneProviderFactory;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final Supplier<DataSource> supplier = taskContextFactory.contextResult(
                    "Getting Data Source",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    taskContext -> {
                        final IndexDoc index = indexStore.readDocument(docRef);
                        return DataSource
                                .builder()
                                .docRef(DocRefUtil.create(index))
                                .fields(IndexDataSourceFieldUtil.getDataSourceFields(index, securityContext))
                                .defaultExtractionPipeline(index.getDefaultExtractionPipeline())
                                .build();
                    });
            final Executor executor = executorProvider.get();
            final CompletableFuture<DataSource> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
            try {
                return completableFuture.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final IndexDoc index = indexStore.readDocument(docRef);
            DateField timeField = null;
            if (index.getTimeField() != null && !index.getTimeField().isBlank()) {
                timeField = new DateField(index.getTimeField());
            }
            return timeField;
        });
    }

    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the index.
        final IndexDoc index = securityContext.useAsReadResult(() ->
                indexStore.readDocument(query.getDataSource()));

        // Extract highlights.
        final Set<String> highlights = luceneProviderFactory
                .get(LuceneVersionUtil.CURRENT_LUCENE_VERSION)
                .createHighlightProvider()
                .getHighlights(index, query.getExpression(), modifiedSearchRequest.getDateTimeSettings());

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
        final FederatedSearchTask federatedSearchTask = new FederatedSearchTask(
                modifiedSearchRequest.getSearchRequestSource(),
                modifiedSearchRequest.getKey(),
                searchName,
                query,
                coprocessorSettingsList,
                modifiedSearchRequest.getDateTimeSettings());

        // Create the search result collector.
        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);
        resultStore.addHighlights(highlights);

        federatedSearchExecutor.start(federatedSearchTask, resultStore, nodeSearchTaskCreator);

        return resultStore;
    }

    @Override
    public String getType() {
        return IndexDoc.DOCUMENT_TYPE;
    }
}
