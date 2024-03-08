package stroom.docrefinfo.api;

import stroom.docref.DocRef;

import java.util.List;

public interface DocRefDecorator {

    /**
     * Decorate the passed {@link DocRef}s with their names if the names are not present.
     * Null {@link DocRef}s are ignored and not returned. The passed list is not modified.
     * @param docRefs A list of {@link DocRef} with at least their UUID and type set.
     * @return A list of fully populated {@link DocRef}s.
     */
    List<DocRef> decorate(final List<DocRef> docRefs);

    /**
     * Decorate the passed {@link DocRef} with its name if the name is not present.
     * @param docRef A {@link DocRef} with at least the UUID and type set.
     * @return A fully populated {@link DocRef}.
     */
    default DocRef decorate(final DocRef docRef) {
        return decorate(docRef, false);
    }

    /**
     * Decorate the passed {@link DocRef} with its name if the name is not present.
     * @param docRef A {@link DocRef} with at least the UUID and type set.
     * @param force Decorate the name even if a name is present.
     *              This is to allow for docRefs with a name that is out of date with
     *              the document it represents, e.g. after a rename.
     * @return A fully populated {@link DocRef}.
     */
    DocRef decorate(final DocRef docRef, final boolean force);
}
