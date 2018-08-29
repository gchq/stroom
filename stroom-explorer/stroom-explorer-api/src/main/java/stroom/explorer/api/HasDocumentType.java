package stroom.explorer.api;

import stroom.explorer.shared.DocumentType;

public interface HasDocumentType {
    /**
     * Get the document type that this explorer action handler acts on.
     * @return The document type that this explorer action handler acts on.
     */
    DocumentType getDocumentType();
}
