package stroom.searchable.api;

import stroom.query.api.v2.DocRef;

import java.util.List;

public interface SearchableProvider {
    List<DocRef> list();

    Searchable get(DocRef docRef);
}
