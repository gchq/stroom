package stroom.explorer.client.presenter;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.document.client.ClientDocumentType;

public interface DocumentTypeSelectionModel {

    TickBoxState getState(ClientDocumentType type);
}
