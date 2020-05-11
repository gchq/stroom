package stroom.util.shared;

import stroom.docref.DocRef;

import java.util.Map;
import java.util.Set;

public interface HasDependencies {
    /**
     * Get a map of dependencies for all documents.
     *
     * @return A map of dependencies.
     */
    Map<DocRef, Set<DocRef>> getDependencies();

    /**
     * Get a map of dependencies for a document.
     *
     * @param docRef The document to get dependencies for.
     * @return The set of document dependencies for the document.
     */
    Set<DocRef> getDependencies(DocRef docRef);

    /**
     * Remap dependencies for a document.
     *
     * @param docRef     The document to apply dependency remappings to.
     * @param remappings The remappings to apply where relevant.
     */
    void remapDependencies(DocRef docRef, Map<DocRef, DocRef> remappings);
}
