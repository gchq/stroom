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
import stroom.index.lucene553.analyser.AnalyzerFactory;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.SearchRequest;
import stroom.query.common.v2.IndexFieldCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.apache.lucene553.analysis.Analyzer;

import java.util.HashMap;
import java.util.Map;

class SearchExpressionQueryCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchExpressionQueryCache.class);

    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final IndexFieldCache indexFieldCache;
    private final Map<String, Analyzer> analyzerMap = new HashMap<>();
    private SearchExpressionQuery luceneQuery;

    @Inject
    SearchExpressionQueryCache(final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                               final IndexFieldCache indexFieldCache) {
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
        this.indexFieldCache = indexFieldCache;
    }

    SearchExpressionQuery getQuery(final SearchRequest searchRequest, final boolean ignoreMissingFields) {
        try {
            if (luceneQuery == null) {
                final SearchExpressionQueryBuilder searchExpressionQueryBuilder =
                        searchExpressionQueryBuilderFactory.create(
                                searchRequest.getQuery().getDataSource(),
                                indexFieldCache,
                                searchRequest.getDateTimeSettings());
                luceneQuery = searchExpressionQueryBuilder
                        .buildQuery(searchRequest.getQuery().getExpression());
            }
            return luceneQuery;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    Analyzer getAnalyser(final LuceneIndexField indexField) {
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
