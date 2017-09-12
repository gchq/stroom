package stroom.importexport.server;

import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.query.api.v1.DocRef;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;

public interface ImportExportActionHandler {
    DocRef importDocument(DocRef docRef, Map<String, String> dataMap, final ImportState importState, final ImportMode importMode);

    Map<String, String> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList);
}
