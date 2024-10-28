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

package stroom.expression.matcher;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.QueryField;
import stroom.dictionary.api.WordListProvider;
import stroom.expression.api.DateTimeSettings;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;

import java.util.Map;

public class ExpressionMatcherFactory {

    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    @Inject
    ExpressionMatcherFactory(final WordListProvider wordListProvider, final CollectionService collectionService) {
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
    }

    public ExpressionMatcher create(final Map<CIKey, QueryField> fieldMap) {
        return new ExpressionMatcher(fieldMap,
                wordListProvider,
                collectionService,
                DateTimeSettings.builder().build());
    }
}
