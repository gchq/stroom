package stroom.index.impl;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class IndexFieldServiceImpl implements IndexFieldService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexFieldServiceImpl.class);

    private final IndexFieldDao indexFieldDao;
    private final Provider<IndexStore> indexStoreProvider;
    private final SecurityContext securityContext;
    private final Set<DocRef> loadedIndexes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    public IndexFieldServiceImpl(final IndexFieldDao indexFieldDao,
                                 final Provider<IndexStore> indexStoreProvider,
                                 final SecurityContext securityContext) {
        this.indexFieldDao = indexFieldDao;
        this.indexStoreProvider = indexStoreProvider;
        this.securityContext = securityContext;
    }

    @Override
    public void addFields(final DocRef docRef, final Collection<IndexField> fields) {
        indexFieldDao.addFields(docRef, fields);
    }

    @Override
    public ResultPage<IndexField> findFields(final FindFieldCriteria criteria) {
        if (criteria.getDataSourceRef() != null && !loadedIndexes.contains(criteria.getDataSourceRef())) {
            transferFieldsToDB(criteria.getDataSourceRef());
            loadedIndexes.add(criteria.getDataSourceRef());
        }

        return indexFieldDao.findFields(criteria);
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

//                // TEST DATA
//                for (int i = 0; i < 1000; i++) {
//                    addField(fieldSourceId, QueryField.createId("test" + i));
//                    for (int j = 0; j < 1000; j++) {
//                        addField(fieldSourceId, QueryField.createId("test" + i + ".test" + j));
//                        for (int k = 0; k < 1000; k++) {
//                            addField(fieldSourceId, QueryField.createId("test" + i + ".test" + j + ".test" + k));
//                        }
//                    }
//                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        return securityContext.useAsReadResult(() -> {

            // Check for read permission.
            if (!securityContext.hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.READ)) {
                // If there is no read permission then return no fields.
                return null;
            }

            final FindFieldCriteria findIndexFieldCriteria = new FindFieldCriteria(
                    PageRequest.oneRow(),
                    null,
                    docRef,
                    StringMatch.equalsIgnoreCase(fieldName),
                    null);
            final ResultPage<IndexField> resultPage = findFields(findIndexFieldCriteria);
            if (resultPage.size() > 0) {
                return resultPage.getFirst();
            }
            return null;
        });
    }

    @Override
    public String getType() {
        return LuceneIndexDoc.DOCUMENT_TYPE;
    }
}
