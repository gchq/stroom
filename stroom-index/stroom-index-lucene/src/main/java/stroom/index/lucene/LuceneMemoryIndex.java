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

package stroom.index.lucene;

import stroom.index.lucene.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.index.lucene.analyser.AnalyzerFactory;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.extraction.FieldValue;
import stroom.search.impl.SearchException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class LuceneMemoryIndex implements stroom.search.extraction.MemoryIndex {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneMemoryIndex.class);

    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final Map<String, Analyzer> analyzerMap = new HashMap<>();
    private Map<String, Optional<IndexField>> cachedFields;
    private SearchExpressionQuery cachedQuery;

    @Inject
    public LuceneMemoryIndex(final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory) {
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
    }

    @Override
    public boolean match(final SearchRequest searchRequest, final List<FieldValue> fieldValues) {
        // Instantiate the cached fields we care about.
        if (cachedFields == null) {
            final List<String> fields = ExpressionUtil.fields(searchRequest.getQuery().getExpression());
            cachedFields = new HashMap<>();
            fields.forEach(field -> cachedFields.put(field, Optional.empty()));
        }

        // OPTIMISATION: If we have no fields in the expression then match.
        if (cachedFields.isEmpty()) {
            return true;
        }

        MemoryIndex memoryIndex = null;
        for (final FieldValue fieldValue : fieldValues) {
            final IndexField indexField = fieldValue.field();
            final LuceneIndexField luceneIndexField = LuceneIndexField
                    .fromIndexField(indexField);

            final Optional<IndexField> cachedField = cachedFields.get(indexField.getFldName());
            // Ignore fields we don't need.
            if (cachedField != null) {
                if (!Objects.equals(cachedField.orElse(null), indexField)) {
                    // We are adding a field.
                    cachedFields.put(indexField.getFldName(), Optional.of(indexField));
                    // Since we changed the fields we will need to recreate the query.
                    cachedQuery = null;
                }

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

            if (docs.totalHits.value() == 0) {
                return false;
            } else if (docs.totalHits.value() == 1) {
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
                final IndexFieldCache indexFieldCache = (key, fieldName) -> cachedFields.get(fieldName).orElse(null);
                final SearchExpressionQueryBuilder searchExpressionQueryBuilder =
                        searchExpressionQueryBuilderFactory.create(
                                searchRequest.getQuery().getDataSource(),
                                indexFieldCache,
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

    private Analyzer getAnalyser(final LuceneIndexField indexField) {
        try {
            Analyzer fieldAnalyzer = analyzerMap.get(indexField.getFldName());
            if (fieldAnalyzer == null) {
                // Add the field analyser.
                fieldAnalyzer = AnalyzerFactory.create(indexField.getAnalyzerType(),
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
