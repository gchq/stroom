package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.util.shared.UserRef;

import java.util.List;

public interface ExplorerFavDao {

    /**
     * Set the specified document as a favourite for a particular user
     *
     * @param docRef   Document to set as a favourite
     * @param userUuid UUID of the user account
     */
    void createFavouriteForUser(final DocRef docRef, final UserRef userRef);

    /**
     * Unset the specified document as a favourite for a particular user
     *
     * @param docRef   Document to unset as a favourite
     * @param userRef UUID of the user account
     */
    void deleteFavouriteForUser(final DocRef docRef, final UserRef userRef);

    /**
     * Retrieve document favourites for the specified user
     *
     * @param userRef UUID of the user account
     * @return
     */
    List<DocRef> getUserFavourites(final UserRef userRef);

    /**
     * Return whether the specified document is set as a favourite for the user
     *
     * @return
     */
    boolean isFavourite(final DocRef docRef, final UserRef userRef);
}
