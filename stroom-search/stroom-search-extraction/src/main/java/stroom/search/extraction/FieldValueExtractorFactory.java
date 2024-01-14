package stroom.search.extraction;

import stroom.docref.DocRef;
import stroom.index.impl.IndexStructure;
import stroom.index.impl.IndexStructureCache;
import stroom.query.language.functions.FieldIndex;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import javax.inject.Inject;

public class FieldValueExtractorFactory {

    private final IndexStructureCache indexStructureCache;
    private final ViewStore viewStore;

    @Inject
    public FieldValueExtractorFactory(final IndexStructureCache indexStructureCache,
                                      final ViewStore viewStore) {
        this.indexStructureCache = indexStructureCache;
        this.viewStore = viewStore;
    }

    public FieldValueExtractor create(final DocRef dataSource, final FieldIndex fieldIndex) {

        DocRef docRef = dataSource;

        if (ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
            final ViewDoc viewDoc = viewStore.readDocument(dataSource);
            docRef = viewDoc.getDataSource();
        }

        final IndexStructure indexStructure = indexStructureCache.get(docRef);
        return new FieldValueExtractor(fieldIndex, indexStructure);
    }
}
