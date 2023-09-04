package stroom.proxy.app.guice;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefDecorator;

import java.util.List;

/**
 * A decorator that does no actual decoration
 */
public class NoDecorationDocRefDecorator implements DocRefDecorator {

    @Override
    public List<DocRef> decorate(final List<DocRef> docRefs) {
        return docRefs;
    }

    @Override
    public DocRef decorate(final DocRef docRef, final boolean force) {
        return docRef;
    }
}
