package stroom.explorer.client.presenter;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.docstore.shared.DocumentType;

public interface DocumentTypeSelectionModel {

    TickBoxState getState(DocumentType type);
}
