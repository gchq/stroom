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

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.index.impl.HighlightProvider;
import stroom.index.lucene.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.langchain.api.OpenAIService;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.impl.SearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.lucene.search.IndexSearcher;

import java.util.Collections;
import java.util.Set;

class LuceneHighlightProvider implements HighlightProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneHighlightProvider.class);

    private final WordListProvider wordListProvider;
    private final Provider<SearchConfig> searchConfigProvider;
    private final Provider<OpenAIService> openAIServiceProvider;

    @Inject
    LuceneHighlightProvider(final WordListProvider wordListProvider,
                            final Provider<SearchConfig> searchConfigProvider,
                            final Provider<OpenAIService> openAIServiceProvider) {
        this.wordListProvider = wordListProvider;
        this.searchConfigProvider = searchConfigProvider;
        this.openAIServiceProvider = openAIServiceProvider;
    }

    /**
     * Compiles the query, extracts terms and then returns them for use in hit
     * highlighting.
     */
    @Override
    public Set<String> getHighlights(final DocRef indexDocRef,
                                     final IndexFieldCache indexFieldCache,
                                     final ExpressionOperator expression,
                                     final DateTimeSettings dateTimeSettings) {
        Set<String> highlights = Collections.emptySet();

        try {
            // Parse the query.
            IndexSearcher.setMaxClauseCount(searchConfigProvider.get().getMaxBooleanClauseCount());
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    indexDocRef,
                    indexFieldCache,
                    wordListProvider,
                    dateTimeSettings,
                    openAIServiceProvider.get());
            final SearchExpressionQuery query = searchExpressionQueryBuilder.buildQuery(expression);

            highlights = query.getTerms();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return highlights;
    }

}
