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

package stroom.search.solr.search;

import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldProvider;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProvider;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.SolrIndexCache;
import stroom.search.solr.SolrIndexStore;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndexDataSourceFieldUtil;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

// used by DI
@SuppressWarnings("unused")
public class SolrSearchProvider implements SearchProvider, IndexFieldProvider {

    public static final String ENTITY_TYPE = SolrIndexDoc.DOCUMENT_TYPE;
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchProvider.class);
    private static final int SEND_INTERACTIVE_SEARCH_RESULT_FREQUENCY = 500;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final SolrIndexCache solrIndexCache;
    private final WordListProvider wordListProvider;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider;
    private final SolrSearchConfig searchConfig;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final SolrIndexStore solrIndexStore;
    private final SolrSearchExecutor solrSearchExecutor;

    @Inject
    public SolrSearchProvider(final SolrIndexCache solrIndexCache,
                              final WordListProvider wordListProvider,
                              final Executor executor,
                              final TaskContextFactory taskContextFactory,
                              final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                              final SolrSearchConfig searchConfig,
                              final CoprocessorsFactory coprocessorsFactory,
                              final ResultStoreFactory resultStoreFactory,
                              final SolrIndexStore solrIndexStore,
                              final SecurityContext securityContext,
                              final SolrSearchExecutor solrSearchExecutor) {
        this.solrIndexCache = solrIndexCache;
        this.wordListProvider = wordListProvider;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.solrAsyncSearchTaskHandlerProvider = solrAsyncSearchTaskHandlerProvider;
        this.searchConfig = searchConfig;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.solrIndexStore = solrIndexStore;
        this.securityContext = securityContext;
        this.solrSearchExecutor = solrSearchExecutor;
    }

    @Override
    public ResultPage<FieldInfo> getFieldInfo(final FindFieldInfoCriteria criteria) {
        return securityContext.useAsReadResult(() -> {
            final FieldInfoResultPageBuilder builder = FieldInfoResultPageBuilder.builder(criteria);
            final SolrIndexDoc index = solrIndexStore.readDocument(criteria.getDataSourceRef());
            if (index != null) {
                final List<QueryField> fields = SolrIndexDataSourceFieldUtil.getDataSourceFields(index);
                builder.addAll(fields);
            }
            return builder.build();
        });
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        final SolrIndexDoc index = solrIndexStore.readDocument(docRef);
        if (index != null && index.getFields() != null) {
            final Optional<SolrIndexField> optionalSolrIndexField = index
                    .getFields()
                    .stream()
                    .filter(field -> Objects.equals(fieldName, field.getName()))
                    .findFirst();
            return optionalSolrIndexField.orElse(null);
        }
        return null;
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(solrIndexStore.readDocument(docRef)).map(SolrIndexDoc::getDescription);
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() -> {
            final SolrIndexDoc index = solrIndexStore.readDocument(dataSourceRef);
            if (index != null) {
                return index.getDefaultExtractionPipeline();
            }
            return null;
        });
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final SolrIndexDoc index = solrIndexStore.readDocument(docRef);
            DateField timeField = null;
            if (index.getTimeField() != null && !index.getTimeField().isBlank()) {
                return new DateField(index.getTimeField());
            }

            return null;
        });
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the index.
        final CachedSolrIndex index = securityContext.useAsReadResult(() -> solrIndexCache.get(query.getDataSource()));

        // Extract highlights.
        final Set<String> highlights = getHighlights(
                index,
                query.getExpression(),
                modifiedSearchRequest.getDateTimeSettings());

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest);

        // Create a handler for search results.
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                modifiedSearchRequest.getDateTimeSettings(),
                modifiedSearchRequest.getKey(),
                coprocessorSettingsList,
                modifiedSearchRequest.getQuery().getParams(),
                DataStoreSettings.createBasicSearchResultStoreSettings());

        // Create an asynchronous search task.
        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";
        final SolrAsyncSearchTask asyncSearchTask = new SolrAsyncSearchTask(
                modifiedSearchRequest.getKey(),
                searchName,
                query,
                coprocessorSettingsList,
                modifiedSearchRequest.getDateTimeSettings());

        // Create the search result store.
        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);
        resultStore.addHighlights(highlights);

        // Start asynchronous search execution.
        solrSearchExecutor.start(asyncSearchTask, resultStore);

        return resultStore;
    }

    /**
     * Compiles the query, extracts terms and then returns them for use in hit
     * highlighting.
     */
    private Set<String> getHighlights(final CachedSolrIndex index,
                                      final ExpressionOperator expression,
                                      final DateTimeSettings dateTimeSettings) {
        Set<String> highlights = Collections.emptySet();

        try {
            // Create a map of index fields keyed by name.
            final Map<String, SolrIndexField> indexFieldsMap = index
                    .getFields()
                    .stream()
                    .collect(Collectors.toMap(SolrIndexField::getName, Function.identity()));
            // Parse the query.
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider,
                    indexFieldsMap,
                    searchConfig.getMaxBooleanClauseCount(),
                    dateTimeSettings);
            final SearchExpressionQuery query = searchExpressionQueryBuilder
                    .buildQuery(expression);

            highlights = query.getTerms();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return highlights;
    }

    @Override
    public List<DocRef> list() {
        return solrIndexStore.list();
    }

    @Override
    public String getType() {
        return SolrIndexDoc.DOCUMENT_TYPE;
    }
}
