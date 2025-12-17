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

package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.api.datasource.QueryField;

import com.google.inject.Provider;
import jakarta.inject.Inject;
import org.jooq.Field;

public class TermHandlerFactory {

    private final Provider<WordListProvider> wordListProvider;
    private final Provider<CollectionService> collectionService;
    private final Provider<DocRefInfoService> docRefInfoService;

    @Inject
    public TermHandlerFactory(final Provider<WordListProvider> wordListProvider,
                              final Provider<CollectionService> collectionService,
                              final Provider<DocRefInfoService> docRefInfoService) {
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
        this.docRefInfoService = docRefInfoService;
    }

    public <T> TermHandler<T> create(final QueryField dataSourceField,
                                     final Field<T> field,
                                     final ExpressionMapper.MultiConverter<T> converter) {
        return create(dataSourceField, field, converter, false);
    }

    public <T> TermHandler<T> create(final QueryField dataSourceField,
                                     final Field<T> field,
                                     final ExpressionMapper.MultiConverter<T> converter,
                                     final boolean useName) {
        return new TermHandler<>(dataSourceField,
                field,
                converter,
                wordListProvider,
                collectionService,
                docRefInfoService,
                useName,
                false);
    }

}
