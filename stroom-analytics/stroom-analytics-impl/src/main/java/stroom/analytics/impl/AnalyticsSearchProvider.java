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

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.explorer.api.HasDataSourceDocRefs;
import stroom.expression.api.DateTimeSettings;
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
import stroom.search.impl.FederatedSearchExecutor;
import stroom.search.impl.FederatedSearchTask;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AnalyticsSearchProvider implements SearchProvider, HasDataSourceDocRefs {

    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final FederatedSearchExecutor federatedSearchExecutor;
    private final AnalyticsNodeSearchTaskCreator nodeSearchTaskCreator;

    @Inject
    public AnalyticsSearchProvider(final CoprocessorsFactory coprocessorsFactory,
                                   final ResultStoreFactory resultStoreFactory,
                                   final FederatedSearchExecutor federatedSearchExecutor,
                                   final AnalyticsNodeSearchTaskCreator nodeSearchTaskCreator) {
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.federatedSearchExecutor = federatedSearchExecutor;
        this.nodeSearchTaskCreator = nodeSearchTaskCreator;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!getType().equals(criteria.getDataSourceRef().getType())) {
            return ResultPage.empty();
        }
        return FieldInfoResultPageBuilder.builder(criteria).addAll(AnalyticFields.getFields()).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return null;
    }

    @Override
    public QueryField getTimeField(final DocRef docRef) {
        return AnalyticFields.TIME_FIELD;
    }

    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Extract highlights.
        final Set<String> highlights = getHighlights(
                query.getExpression(),
                modifiedSearchRequest.getDateTimeSettings());

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

    /**
     * Compiles the query, extracts terms and then returns them for use in hit
     * highlighting.
     */
    private Set<String> getHighlights(final ExpressionOperator expression,
                                      DateTimeSettings dateTimeSettings) {
        Set<String> highlights = Collections.emptySet();

//        try {
//            // Create a map of index fields keyed by name.
//            final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getFields());
//            // Parse the query.
//            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
//                    wordListProvider, indexFieldsMap, maxBooleanClauseCount, dateTimeSettings, nowEpochMilli);
//            final SearchExpressionQuery query = searchExpressionQueryBuilder
//                    .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, expression);
//
//            highlights = query.getTerms();
//        } catch (final RuntimeException e) {
//            LOGGER.debug(e.getMessage(), e);
//        }

        return highlights;
    }

    @Override
    public List<DocRef> list() {
        return List.of(AnalyticFields.ANALYTICS_DOC_REF);
    }

    @Override
    public String getType() {
        return AnalyticFields.ANALYTICS_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return List.of(AnalyticFields.ANALYTICS_DOC_REF);
    }
}
