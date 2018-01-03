package stroom.importexport.server;

import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.util.shared.Message;
import stroom.query.api.v2.DocRef;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImportExportActionHandler {
    DocRef importDocument(DocRef docRef, Map<String, String> dataMap, final ImportState importState, final ImportMode importMode);

    Map<String, String> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList);

    Set<DocRef> listDocuments();
}
