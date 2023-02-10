package stroom.docstore.fav.api;

import stroom.docref.DocRef;

import java.util.List;

public interface DocFavService {

    void create(final DocRef docRef);

    void delete(final DocRef docRef);

    List<DocRef> fetchDocFavs();

    boolean isDocFav(final DocRef docRef);
}
