package stroom.importexport;

import stroom.explorer.HasDocumentType;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.docref.DocRef;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImportExportActionHandler extends HasDocumentType {
    DocRef importDocument(DocRef docRef, Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode);

    Map<String, byte[]> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList);

    Set<DocRef> listDocuments();

    Map<DocRef, Set<DocRef>> getDependencies();
}
