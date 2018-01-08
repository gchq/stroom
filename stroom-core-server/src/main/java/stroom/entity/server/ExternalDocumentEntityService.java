package stroom.entity.server;

import stroom.docstore.server.DocumentActionHandler;
import stroom.entity.shared.SharedDocRef;
import stroom.explorer.server.ExplorerActionHandler;
import stroom.importexport.server.ImportExportActionHandler;
import stroom.util.shared.HasType;

public interface ExternalDocumentEntityService extends
        ExplorerActionHandler,
        ImportExportActionHandler,
        DocumentActionHandler<SharedDocRef>,
        HasType {
}
