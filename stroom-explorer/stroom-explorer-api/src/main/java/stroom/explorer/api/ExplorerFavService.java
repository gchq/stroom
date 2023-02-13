package stroom.explorer.api;

import stroom.docref.DocRef;

import java.util.List;

public interface ExplorerFavService {

    void create(final DocRef docRef);

    void delete(final DocRef docRef);

    List<DocRef> getUserFavourites();

    boolean isFavourite(final DocRef docRef);
}
