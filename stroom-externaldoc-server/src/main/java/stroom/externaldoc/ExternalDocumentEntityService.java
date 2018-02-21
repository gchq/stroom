package stroom.externaldoc;

import stroom.docstore.DocumentActionHandler;
import stroom.entity.shared.SharedDocRef;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.util.shared.HasType;

public interface ExternalDocumentEntityService extends
        ExplorerActionHandler,
        ImportExportActionHandler,
        DocumentActionHandler<SharedDocRef>,
        HasType {
}
