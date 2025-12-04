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

package stroom.index.mock;

import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldService;
import stroom.index.impl.IndexStore;
import stroom.index.shared.AddField;
import stroom.index.shared.DeleteField;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.UpdateField;
import stroom.query.api.StringExpressionUtil;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.util.PredicateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Singleton
public class MockIndexFieldService implements IndexFieldService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MockIndexFieldService.class);

    private final Provider<IndexStore> indexStoreProvider;
    private final Map<DocRef, Set<IndexField>> map = new ConcurrentHashMap<>();
    private final ExpressionPredicateFactory expressionPredicateFactory;

    private final Set<DocRef> loadedIndexes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    MockIndexFieldService(final Provider<IndexStore> indexStoreProvider,
                          final ExpressionPredicateFactory expressionPredicateFactory) {
        this.indexStoreProvider = indexStoreProvider;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    @Override
    public void addFields(final DocRef docRef, final Collection<IndexField> fields) {
        map.computeIfAbsent(docRef, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).addAll(fields);
    }

    @Override
    public ResultPage<IndexField> findFields(final FindFieldCriteria criteria) {
        final DocRef dataSourceRef = criteria.getDataSourceRef();
        ensureLoaded(dataSourceRef);

        final Predicate<IndexField> namePredicate = expressionPredicateFactory
                .create(criteria.getFilter(), IndexField::getFldName);
        final List<Predicate<IndexField>> predicates = new ArrayList<>(2);
        predicates.add(namePredicate);
        if (criteria.getQueryable() != null) {
            predicates.add(queryField -> queryField.isIndexed() == criteria.getQueryable());
        }
        final Predicate<IndexField> predicate = PredicateUtil.andPredicates(predicates, name -> true);
        final Set<IndexField> set = map.get(dataSourceRef);
        final List<IndexField> filtered = set
                .stream()
                .filter(predicate)
                .toList();
        return ResultPage.createPageLimitedList(filtered, criteria.getPageRequest());
    }

    private void ensureLoaded(final DocRef dataSourceRef) {
        if (dataSourceRef != null && !loadedIndexes.contains(dataSourceRef)) {
            transferFieldsToDB(dataSourceRef);
            loadedIndexes.add(dataSourceRef);
        }
    }

    private void transferFieldsToDB(final DocRef docRef) {
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
    public int getFieldCount(final DocRef dataSourceRef) {
        ensureLoaded(dataSourceRef);
        return NullSafe.getOrElse(
                map.get(dataSourceRef),
                Set::size,
                0);
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        final FindFieldCriteria findIndexFieldCriteria = new FindFieldCriteria(
                PageRequest.oneRow(),
                null,
                docRef,
                StringExpressionUtil.equalsCaseSensitive(fieldName),
                null);
        final ResultPage<IndexField> resultPage = findFields(findIndexFieldCriteria);
        if (!resultPage.isEmpty()) {
            return resultPage.getFirst();
        }
        return null;
    }

    @Override
    public String getDataSourceType() {
        return LuceneIndexDoc.TYPE;
    }

    @Override
    public Boolean addField(final AddField addField) {
        return null;
    }

    @Override
    public Boolean updateField(final UpdateField updateField) {
        return null;
    }

    @Override
    public Boolean deleteField(final DeleteField deleteField) {
        return null;
    }

    @Override
    public void deleteAll(final DocRef docRef) {

    }

    @Override
    public void copyAll(final DocRef source, final DocRef dest) {

    }
}
