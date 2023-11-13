package stroom.searchable.api;

import stroom.docref.DocRef;

import java.util.List;

public interface SearchableProvider {

    Searchable get(DocRef docRef);

    List<DocRef> list();
}
