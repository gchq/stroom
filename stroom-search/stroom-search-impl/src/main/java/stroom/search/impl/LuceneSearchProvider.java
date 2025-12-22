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

package stroom.search.impl;

import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldService;
import stroom.index.impl.IndexStore;
import stroom.index.impl.LuceneIndexDocCache;
import stroom.index.impl.LuceneProviderFactory;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneVersionUtil;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProvider;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LuceneSearchProvider implements SearchProvider {

    private final IndexStore indexStore;
    private final LuceneIndexDocCache luceneIndexDocCache;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final FederatedSearchExecutor federatedSearchExecutor;
    private final NodeSearchTaskCreator nodeSearchTaskCreator;
    private final LuceneProviderFactory luceneProviderFactory;
    private final IndexFieldService indexFieldService;
    private final IndexFieldCache indexFieldCache;

    @Inject
    public LuceneSearchProvider(final IndexStore indexStore,
                                final LuceneIndexDocCache luceneIndexDocCache,
                                final SecurityContext securityContext,
                                final CoprocessorsFactory coprocessorsFactory,
                                final ResultStoreFactory resultStoreFactory,
                                final FederatedSearchExecutor federatedSearchExecutor,
                                final NodeSearchTaskCreator nodeSearchTaskCreator,
                                final LuceneProviderFactory luceneProviderFactory,
                                final IndexFieldService indexFieldService,
                                final IndexFieldCache indexFieldCache) {
        this.indexStore = indexStore;
        this.luceneIndexDocCache = luceneIndexDocCache;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.federatedSearchExecutor = federatedSearchExecutor;
        this.nodeSearchTaskCreator = nodeSearchTaskCreator;
        this.luceneProviderFactory = luceneProviderFactory;
        this.indexFieldService = indexFieldService;
        this.indexFieldCache = indexFieldCache;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return securityContext.useAsReadResult(() -> {
            final DocRef docRef = criteria.getDataSourceRef();

            // Check for read permission.
            if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                // If there is no read permission then return no fields.
                return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
            }

            final ResultPage<IndexField> resultPage = indexFieldService.findFields(criteria);
            final List<QueryField> queryFields = resultPage
                    .getValues()
                    .stream()
                    .map(indexField -> QueryField
                            .builder()
                            .fldName(indexField.getFldName())
                            .fldType(indexField.getFldType())
                            .conditionSet(ConditionSet.getDefault(indexField.getFldType()))
                            .queryable(indexField.isIndexed())
                            .build())
                    .toList();
            return new ResultPage<>(queryFields, resultPage.getPageResponse());
        });
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            // Check for read permission.
            if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                // If there is no read permission then return no fields.
                return 0;
            } else {
                return indexFieldService.getFieldCount(docRef);
            }
        });
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(luceneIndexDocCache.get(docRef))
                .map(LuceneIndexDoc::getDescription);
    }

    @Override
    public Optional<DocRef> fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() -> {
            final LuceneIndexDoc index = luceneIndexDocCache.get(dataSourceRef);
            return Optional.ofNullable(index).map(LuceneIndexDoc::getDefaultExtractionPipeline);
        });
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final LuceneIndexDoc index = luceneIndexDocCache.get(docRef);
            QueryField timeField = null;
            if (index != null && index.getTimeField() != null && !index.getTimeField().isBlank()) {
                timeField = QueryField.createDate(index.getTimeField());
            }
            return Optional.ofNullable(timeField);
        });
    }

    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the index.
        final LuceneIndexDoc index = securityContext.useAsReadResult(() ->
                luceneIndexDocCache.get(query.getDataSource()));

        // Extract highlights.
        final Set<String> highlights = luceneProviderFactory
                .get(LuceneVersionUtil.CURRENT_LUCENE_VERSION)
                .createHighlightProvider()
                .getHighlights(
                        query.getDataSource(),
                        indexFieldCache,
                        query.getExpression(),
                        modifiedSearchRequest.getDateTimeSettings());

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest, index.getDefaultExtractionPipeline());

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
    public List<DocRef> getDataSourceDocRefs() {
        return indexStore.list();
    }

    @Override
    public String getDataSourceType() {
        return LuceneIndexDoc.TYPE;
    }
}
