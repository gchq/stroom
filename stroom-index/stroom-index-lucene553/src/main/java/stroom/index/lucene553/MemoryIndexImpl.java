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

package stroom.index.lucene553;

import stroom.index.lucene553.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.IndexField;
import stroom.search.extraction.FieldValue;
import stroom.search.impl.SearchException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.apache.lucene553.analysis.Analyzer;
import org.apache.lucene553.analysis.TokenStream;
import org.apache.lucene553.index.IndexableField;
import org.apache.lucene553.index.memory.MemoryIndex;
import org.apache.lucene553.search.IndexSearcher;
import org.apache.lucene553.search.TopDocs;

import java.io.IOException;
import java.util.List;

class MemoryIndexImpl implements stroom.search.extraction.MemoryIndex {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MemoryIndexImpl.class);

    private final SearchExpressionQueryCache searchExpressionQueryCache;

    @Inject
    public MemoryIndexImpl(final SearchExpressionQueryCache searchExpressionQueryCache) {
        this.searchExpressionQueryCache = searchExpressionQueryCache;
    }

    @Override
    public boolean match(final SearchRequest searchRequest, final List<FieldValue> fieldValues) {
        final MemoryIndex memoryIndex = new MemoryIndex();
        for (final FieldValue fieldValue : fieldValues) {
            final IndexField indexField = fieldValue.field();
            final LuceneIndexField luceneIndexField = LuceneIndexField
                    .fromIndexField(indexField);

            if (luceneIndexField.isIndexed()) {
                final Analyzer fieldAnalyzer = searchExpressionQueryCache.getAnalyser(luceneIndexField);
                final IndexableField field = FieldFactory.create(fieldValue);
                final TokenStream tokenStream = field.tokenStream(fieldAnalyzer, null);
                if (tokenStream != null) {
                    memoryIndex.addField(field.name(), tokenStream, field.boost());
                }
            }
        }

        // See if this set of fields matches the rule expression.
        return matchQuery(searchRequest, memoryIndex);
    }

    private boolean matchQuery(final SearchRequest searchRequest, final MemoryIndex memoryIndex) {
        try {
            final SearchExpressionQuery query = searchExpressionQueryCache.getQuery(searchRequest, true);
            final IndexSearcher indexSearcher = memoryIndex.createSearcher();
            final TopDocs docs = indexSearcher.search(query.getQuery(), 100);

            if (docs.totalHits == 0) {
                return false;
            } else if (docs.totalHits == 1) {
                return true;
            } else {
                LOGGER.error("Unexpected number of documents {}  found by rule, should be 1 or 0.", docs.totalHits);
            }
        } catch (final SearchException | IOException se) {
            LOGGER.warn("Unable to create alerts for rule " + searchRequest.getQuery() + " due to " + se.getMessage());
        }

        return false;
    }
}
