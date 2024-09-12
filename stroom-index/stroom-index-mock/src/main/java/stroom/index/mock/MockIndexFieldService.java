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

package stroom.index.mock;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.index.impl.IndexFieldService;
import stroom.index.impl.IndexStore;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.common.v2.IndexFieldMap;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.string.CIKey;
import stroom.util.string.StringMatcher;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MockIndexFieldService implements IndexFieldService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MockIndexFieldService.class);

    private final Provider<IndexStore> indexStoreProvider;
    private final Map<DocRef, Set<IndexField>> map = new ConcurrentHashMap<>();

    private final Set<DocRef> loadedIndexes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    MockIndexFieldService(final Provider<IndexStore> indexStoreProvider) {
        this.indexStoreProvider = indexStoreProvider;
    }

    @Override
    public void addFields(final DocRef docRef, final Collection<IndexField> fields) {
        map.computeIfAbsent(docRef, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).addAll(fields);
    }

    @Override
    public ResultPage<IndexField> findFields(final FindFieldCriteria criteria) {
        if (criteria.getDataSourceRef() != null && !loadedIndexes.contains(criteria.getDataSourceRef())) {
            transferFieldsToDB(criteria.getDataSourceRef());
            loadedIndexes.add(criteria.getDataSourceRef());
        }

        final StringMatcher stringMatcher = new StringMatcher(criteria.getStringMatch());
        final Set<IndexField> set = map.get(criteria.getDataSourceRef());
        final List<IndexField> filtered = set
                .stream()
                .filter(field -> {
                    if (criteria.getQueryable() == null || criteria.getQueryable().equals(field.isIndexed())) {
                        return stringMatcher.match(field.getFldName()).isPresent();
                    }
                    return false;
                })
                .toList();
        return ResultPage.createPageLimitedList(filtered, criteria.getPageRequest());
    }

    @Override
    public void transferFieldsToDB(final DocRef docRef) {
        try {
            // Load fields.
            final IndexStore indexStore = indexStoreProvider.get();
            final LuceneIndexDoc index = indexStore.readDocument(docRef);
            if (index != null) {
                final List<IndexField> fields = NullSafe.list(index.getFields())
                        .stream()
                        .map(field -> (IndexField) field)
                        .toList();
                addFields(docRef, fields);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public IndexFieldMap getIndexFields(final DocRef docRef, final CIKey fieldName) {
        final FindFieldCriteria findIndexFieldCriteria = new FindFieldCriteria(
                PageRequest.oneRow(),
                null,
                docRef,
                StringMatch.equalsIgnoreCase(fieldName.get()),
                null);
        final ResultPage<IndexField> resultPage = findFields(findIndexFieldCriteria);

        if (resultPage.size() > 0) {
            return IndexFieldMap.fromFieldList(fieldName, resultPage.getValues());
        } else {
            return null;
        }
    }

    @Override
    public String getType() {
        return LuceneIndexDoc.DOCUMENT_TYPE;
    }
}
