package stroom.searchable.api;

import stroom.docref.DocRef;

public interface SearchableProvider {

    Searchable get(DocRef docRef);
}
