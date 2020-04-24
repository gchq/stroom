package stroom.index.impl;

import com.codahale.metrics.annotation.Timed;
import stroom.docref.DocRef;
import stroom.importexport.api.DocumentData;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.index.shared.IndexDoc;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class NewUIIndexResourceImpl implements NewUIIndexResource {
    private final IndexStore indexStore;

    @Inject
    public NewUIIndexResourceImpl(final IndexStore indexStore) {
        this.indexStore = indexStore;
    }

    @Override
    @Timed
    public Set<DocRef> listDocuments() {
        return indexStore.listDocuments();
    }

    @Override
    @Timed
    public DocRef importDocument(final Base64EncodedDocumentData encodedDocumentData) {
        final DocumentData documentData = DocumentData.fromBase64EncodedDocumentData(encodedDocumentData);
        final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
        final ImportExportActionHandler.ImpexDetails result = indexStore.importDocument(documentData.getDocRef(), documentData.getDataMap(), importState, ImportMode.IGNORE_CONFIRMATION);

        if (result != null)
            return result.getDocRef();
        else
            return null;
    }

    @Override
    @Timed
    public Base64EncodedDocumentData exportDocument(final DocRef docRef) {
        final Map<String, byte[]> map = indexStore.exportDocument(docRef, true, new ArrayList<>());
        return DocumentData.toBase64EncodedDocumentData(new DocumentData(docRef, map));
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
            doc.setVolumeGroupName(updates.getVolumeGroupName());
            doc.setFields(updates.getFields());
            indexStore.writeDocument(doc);
        }

        return Response.noContent().build();
    }
}
