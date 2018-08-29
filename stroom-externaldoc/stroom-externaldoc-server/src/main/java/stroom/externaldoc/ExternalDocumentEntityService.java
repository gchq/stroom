package stroom.externaldoc;

import stroom.docstore.DocumentActionHandler;
import stroom.entity.shared.SharedDocRef;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.util.shared.HasType;

interface ExternalDocumentEntityService extends
        ExplorerActionHandler,
        ImportExportActionHandler,
        DocumentActionHandler<SharedDocRef>,
        HasType {
}
