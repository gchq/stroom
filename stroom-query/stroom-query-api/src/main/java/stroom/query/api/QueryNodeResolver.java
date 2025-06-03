package stroom.query.api;

import stroom.docref.DocRef;

public interface QueryNodeResolver {

    String getNode(final DocRef docRef);
}
