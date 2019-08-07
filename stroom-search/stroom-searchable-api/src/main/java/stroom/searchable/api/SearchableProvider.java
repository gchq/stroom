package stroom.searchable.api;

import stroom.docref.DocRef;

import java.util.List;

public interface SearchableProvider {
    List<DocRef> list();

    Searchable get(DocRef docRef);
}
