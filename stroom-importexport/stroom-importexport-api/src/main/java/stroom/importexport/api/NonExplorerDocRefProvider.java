package stroom.importexport.api;

import stroom.docref.DocRef;


public interface NonExplorerDocRefProvider {
    /**
     * Find a docref in the explorer tree that is nearest to the provided non-explorer based docref,
     * so that a suitable location can be found, e.g. for keeping serialised content together
     * @param docref the non-explorer based docref known to this class
     * @return an explorer docref that is located in a suitable location for association with the supplied docref
     * (or null if no suitable explorer docref is found)
     */
    DocRef findNearestExplorerDocRef(DocRef docref);

    /**
     * Allows an alternative name to be provided for a docref
     * @param docRef the non-explorer based docref known to this class
     * @return A string that represents a suitable name for this docref.
     */
    String findNameOfDocRef(DocRef docRef);
}
