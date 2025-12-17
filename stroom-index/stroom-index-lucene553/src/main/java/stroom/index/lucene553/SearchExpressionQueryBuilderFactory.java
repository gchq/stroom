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

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.impl.SearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

class SearchExpressionQueryBuilderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchExpressionQueryBuilderFactory.class);

    private final WordListProvider wordListProvider;
    private final Provider<SearchConfig> searchConfigProvider;

    @Inject
    SearchExpressionQueryBuilderFactory(final WordListProvider wordListProvider,
                                        final Provider<SearchConfig> searchConfigProvider) {
        this.wordListProvider = wordListProvider;
        this.searchConfigProvider = searchConfigProvider;
    }

    public SearchExpressionQueryBuilder create(final DocRef indexDocRef,
                                               final IndexFieldCache indexFieldCache,
                                               final DateTimeSettings dateTimeSettings) {
        try {
            return new SearchExpressionQueryBuilder(
                    indexDocRef,
                    indexFieldCache,
                    wordListProvider,
                    searchConfigProvider.get().getMaxBooleanClauseCount(),
                    dateTimeSettings);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }
}
