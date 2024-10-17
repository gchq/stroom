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

package stroom.index.lucene980;

import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;
import stroom.index.lucene980.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.index.lucene980.analyser.AnalyzerFactory;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.IndexFieldMap;
import stroom.search.extraction.FieldValue;
import stroom.search.impl.SearchException;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import org.apache.lucene980.analysis.Analyzer;
import org.apache.lucene980.index.IndexableField;
import org.apache.lucene980.index.memory.MemoryIndex;
import org.apache.lucene980.search.IndexSearcher;
import org.apache.lucene980.search.TopDocs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class Lucene980MemoryIndex implements stroom.search.extraction.MemoryIndex {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Lucene980MemoryIndex.class);

    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final Map<String, Analyzer> analyzerMap = new HashMap<>();
    //    private Map<CIKey, Optional<IndexField>> cachedFields;
    private Map<CIKey, IndexFieldMap> cachedFields;
    private SearchExpressionQuery cachedQuery;

    private Set<String> caseSenseExpressionFields;
    private Set<CIKey> expressionFields;

    @Inject
    public Lucene980MemoryIndex(final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory) {
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
    }

    @Override
    public boolean match(final SearchRequest searchRequest, final List<FieldValue> fieldValues) {
        // Instantiate the cached fields we care about.
        if (expressionFields == null) {
            // These may be case-sensitive if there are >1 IndexFields with the same name (ignoring case)
            caseSenseExpressionFields = new HashSet<>(ExpressionUtil.fields(searchRequest.getQuery().getExpression()));
            expressionFields = caseSenseExpressionFields.stream()
                    .map(CIKey::of)
                    .collect(Collectors.toSet());
//            cachedFields = new HashMap<>();
//            searchRequestFields.stream()
//                    .map(CIKey::of)
//                    .forEach(field ->
//                            cachedFields.put(field, IndexFieldMap.empty(field)));
        }

        // OPTIMISATION: If we have no fields in the expression then match.
        if (expressionFields.isEmpty()) {
            return true;
        }

        MemoryIndex memoryIndex = null;
        for (final FieldValue fieldValue : fieldValues) {
            final IndexField indexField = fieldValue.field();
            final CIKey caseInsenseFieldName = CIKey.of(indexField.getFldName());

            // Ignore fields that are not in the search expr
            if (!expressionFields.contains(caseInsenseFieldName)) {
                // Merge this field into the cache of IndexFieldMaps
                cachedFields.compute(caseInsenseFieldName, (key, currIndexFieldMap) -> {
                    if (currIndexFieldMap == null) {
                        return IndexFieldMap.forSingleField(key, indexField);
                    } else {
                        return IndexFieldMap.merge(
                                currIndexFieldMap,
                                IndexFieldMap.forSingleField(key, indexField));
                    }
                });

                // All this is to allow for the idx having fields like 'foo' and 'FOO'
                final IndexFieldMap indexFieldMap = cachedFields.get(caseInsenseFieldName);
                boolean hasCacheChanged = false;
                if (indexFieldMap == null) {
                    // Nothing matching this case insense name
                    cachedFields.put(
                            caseInsenseFieldName,
                            IndexFieldMap.forSingleField(caseInsenseFieldName, indexField));
                    hasCacheChanged = true;
                } else {
                    final IndexField existingField = indexFieldMap.getExactMatchingField(indexField.getFldName());
                    if (existingField == null) {
                        // This exact field is not present so merge this field in to the map
                        cachedFields.put(
                                caseInsenseFieldName,
                                IndexFieldMap.merge(
                                        indexFieldMap,
                                        IndexFieldMap.forSingleField(caseInsenseFieldName, indexField)));
                        hasCacheChanged = true;
                    } else if (!Objects.equals(existingField, indexField)) {
                        // Field is different, so we need to update it in the cache
                        final List<IndexField> fields = new ArrayList<>();
                        indexFieldMap.getFields()
                                .stream()
                                .filter(field -> !field.getFldName().equals(indexField.getFldName()))
                                .forEach(fields::add);
                        fields.add(indexField);
                        cachedFields.put(
                                caseInsenseFieldName,
                                IndexFieldMap.fromFieldList(caseInsenseFieldName, fields));
                        hasCacheChanged = true;
                    }
                }
                if (hasCacheChanged) {
                    cachedQuery = null;
                }

                final LuceneIndexField luceneIndexField = LuceneIndexField
                        .fromIndexField(indexField);
                // If the field is indexed then add it to the in memory index.
                if (luceneIndexField.isIndexed()) {
                    final Analyzer fieldAnalyzer = getAnalyser(luceneIndexField);
                    final IndexableField field = FieldFactory.create(fieldValue);
                    if (field != null) {
                        // OPTIMISATION: Lazily create the memory index.
                        if (memoryIndex == null) {
                            memoryIndex = new MemoryIndex();
                        }
                        memoryIndex.addField(field, fieldAnalyzer);
                    }
                }
            }
        }

        // OPTIMISATION: If there was no memory index created then return false as we cannot match.
        if (memoryIndex == null) {
            return false;
        }

        // See if this set of fields matches the rule expression.
        return matchQuery(searchRequest, memoryIndex);
    }

    private boolean matchQuery(final SearchRequest searchRequest,
                               final MemoryIndex memoryIndex) {
        try {
            final SearchExpressionQuery query = getQuery(searchRequest);
            final IndexSearcher indexSearcher = memoryIndex.createSearcher();
            final TopDocs docs = indexSearcher.search(query.getQuery(), 100);

            if (docs.totalHits.value == 0) {
                return false;
            } else if (docs.totalHits.value == 1) {
                return true;
            } else {
                LOGGER.error("Unexpected number of documents {}  found by rule, should be 1 or 0.", docs.totalHits);
            }
        } catch (final SearchException | IOException se) {
            LOGGER.warn("Unable to create alerts for rule " + searchRequest.getQuery() + " due to " + se.getMessage());
        }

        return false;
    }

    private SearchExpressionQuery getQuery(final SearchRequest searchRequest) {
        try {
            // We will cache the query until the fields change.
            if (cachedQuery == null) {
                final SearchExpressionQueryBuilder searchExpressionQueryBuilder =
                        searchExpressionQueryBuilderFactory.create(
                                searchRequest.getQuery().getDataSource(),
                                this::getIndexField,
                                searchRequest.getDateTimeSettings());

                cachedQuery = searchExpressionQueryBuilder
                        .buildQuery(searchRequest.getQuery().getExpression());
            }
            return cachedQuery;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private IndexField getIndexField(final DocRef docRef, final String fieldName) {
        if (cachedFields != null) {
            return NullSafe.get(
                    cachedFields.get(CIKey.of(fieldName)),
                    indexFieldMap ->
                            indexFieldMap.getClosestMatchingField(fieldName));
        } else {
            return null;
        }
    }

    private Analyzer getAnalyser(final LuceneIndexField indexField) {
        try {
            Analyzer fieldAnalyzer = analyzerMap.get(indexField.getFldName());
            if (fieldAnalyzer == null) {
                // Add the field analyser.
                fieldAnalyzer = AnalyzerFactory.create(
                        indexField.getAnalyzerType(),
                        indexField.isCaseSensitive());
                analyzerMap.put(indexField.getFldName(), fieldAnalyzer);
            }
            return fieldAnalyzer;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }
}
