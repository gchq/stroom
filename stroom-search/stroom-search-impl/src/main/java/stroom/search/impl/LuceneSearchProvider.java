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

import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.datasource.api.v2.QueryFieldService;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.impl.LuceneProviderFactory;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldProvider;
import stroom.index.shared.LuceneIndexDoc;
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
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LuceneSearchProvider implements SearchProvider, IndexFieldProvider {

    private final IndexStore indexStore;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final FederatedSearchExecutor federatedSearchExecutor;
    private final NodeSearchTaskCreator nodeSearchTaskCreator;
    private final LuceneProviderFactory luceneProviderFactory;
    private final QueryFieldService queryFieldService;

    private static final Map<DocRef, Integer> FIELD_SOURCE_MAP = new ConcurrentHashMap<>();

    @Inject
    public LuceneSearchProvider(final IndexStore indexStore,
                                final SecurityContext securityContext,
                                final CoprocessorsFactory coprocessorsFactory,
                                final ResultStoreFactory resultStoreFactory,
                                final FederatedSearchExecutor federatedSearchExecutor,
                                final NodeSearchTaskCreator nodeSearchTaskCreator,
                                final LuceneProviderFactory luceneProviderFactory,
                                final QueryFieldService queryFieldService) {
        this.indexStore = indexStore;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.federatedSearchExecutor = federatedSearchExecutor;
        this.nodeSearchTaskCreator = nodeSearchTaskCreator;
        this.luceneProviderFactory = luceneProviderFactory;
        this.queryFieldService = queryFieldService;
    }

    @Override
    public ResultPage<FieldInfo> getFieldInfo(final FindFieldInfoCriteria criteria) {
        return securityContext.useAsReadResult(() -> {
            final DocRef docRef = criteria.getDataSourceRef();

            // Check for read permission.
            if (!securityContext.hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.READ)) {
                // If there is no read permission then return no fields.
                return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
            }

            if (!FIELD_SOURCE_MAP.containsKey(docRef)) {
                // Load fields.
                final LuceneIndexDoc index = indexStore.readDocument(docRef);
                if (index == null) {
                    // We can't read the index so return no fields.
                    return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
                }

                final List<QueryField> fields = IndexDataSourceFieldUtil.getDataSourceFields(index);
                final int fieldSourceId = queryFieldService.getOrCreateFieldSource(docRef);
                final List<FieldInfo> mapped = fields.stream().map(FieldInfo::create).toList();
                queryFieldService.addFields(fieldSourceId, mapped);

//                // TEST DATA
//                for (int i = 0; i < 1000; i++) {
//                    addField(fieldSourceId, new IdField("test" + i));
//                    for (int j = 0; j < 1000; j++) {
//                        addField(fieldSourceId, new IdField("test" + i + ".test" + j));
//                        for (int k = 0; k < 1000; k++) {
//                            addField(fieldSourceId, new IdField("test" + i + ".test" + j + ".test" + k));
//                        }
//                    }
//                }
                FIELD_SOURCE_MAP.put(docRef, fieldSourceId);
            }

            return queryFieldService.findFieldInfo(criteria);
        });
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        // TODO : GET FIELD
//        final ElasticIndexDoc index = elasticIndexStore.readDocument(docRef);
//        if (index != null) {
//            final Map<String, ElasticIndexField> indexFieldMap = getFieldsMap(index);
//            return indexFieldMap.get(fieldName);
//        }
        return null;
    }

    private void addField(final int fieldSourceId, final QueryField field) {
        queryFieldService.addFields(fieldSourceId,
                Collections.singletonList(FieldInfo.create(field)));
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(indexStore.readDocument(docRef)).map(LuceneIndexDoc::getDescription);
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() -> {
            final LuceneIndexDoc index = indexStore.readDocument(dataSourceRef);
            if (index != null) {
                return index.getDefaultExtractionPipeline();
            }
            return null;
        });
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final LuceneIndexDoc index = indexStore.readDocument(docRef);
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
        final LuceneIndexDoc index = securityContext.useAsReadResult(() ->
                indexStore.readDocument(query.getDataSource()));

        // Extract highlights.
        final Set<String> highlights = luceneProviderFactory
                .get(LuceneVersionUtil.CURRENT_LUCENE_VERSION)
                .createHighlightProvider()
                .getHighlights(index, query.getExpression(), modifiedSearchRequest.getDateTimeSettings());

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
    public List<DocRef> list() {
        return indexStore.list();
    }

    @Override
    public String getType() {
        return LuceneIndexDoc.DOCUMENT_TYPE;
    }
}
