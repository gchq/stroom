package stroom.index.impl.rest;

import com.codahale.metrics.annotation.Timed;
import stroom.docref.DocRef;
import stroom.importexport.api.OldDocumentData;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.index.impl.IndexResource;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.util.shared.DocRefs;
import stroom.util.string.EncodingUtil;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexResourceImpl implements IndexResource {

    private final IndexStore indexStore;

    @Inject
    public IndexResourceImpl(final IndexStore indexStore) {
        this.indexStore = indexStore;
    }

    @Override
    @Timed
    public DocRefs listDocuments() {

        final Set<DocRef> docRefSet = indexStore.listDocuments();

        final DocRefs result = new DocRefs();
        result.setDoc(docRefSet);
        return result;
    }

    @Override
    @Timed
    public DocRef importDocument(final OldDocumentData documentData) {
        final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
        if (documentData.getDataMap() == null) {
            return indexStore.importDocument(documentData.getDocRef(), null, importState, ImportMode.IGNORE_CONFIRMATION);
        }
        final Map<String, byte[]> data = documentData.getDataMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> EncodingUtil.asBytes(e.getValue())));
        return indexStore.importDocument(documentData.getDocRef(), data, importState, ImportMode.IGNORE_CONFIRMATION);

    }

    @Override
    @Timed
    public OldDocumentData exportDocument(final DocRef docRef) {
        final Map<String, byte[]> map = indexStore.exportDocument(docRef, true, new ArrayList<>());
        if (map == null) {
            return new OldDocumentData(docRef, null);
        }
        final Map<String, String> data = map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> EncodingUtil.asString(e.getValue())));
        return new OldDocumentData(docRef, data);
    }

    private DocRef getDocRef(final String pipelineId) {
        return new DocRef.Builder()
                .uuid(pipelineId)
                .type(IndexDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    @Timed
    public Response fetch(final String indexUuid) {
        final IndexDoc doc = indexStore.readDocument(getDocRef(indexUuid));

        return Response.ok(doc).build();
    }

    @Override
    @Timed
    public Response save(final String dictionaryUuid,
                         final IndexDoc updates) {
        final IndexDoc doc = indexStore.readDocument(getDocRef(dictionaryUuid));

        if (doc != null) {
            doc.setDescription(updates.getDescription());
            indexStore.writeDocument(doc);
        }

        return Response.ok().build();
    }
}
