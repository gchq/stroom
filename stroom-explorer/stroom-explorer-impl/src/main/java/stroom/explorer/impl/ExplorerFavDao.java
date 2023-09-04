package stroom.explorer.impl;

import stroom.docref.DocRef;

import java.util.List;

public interface ExplorerFavDao {

    /**
     * Set the specified document as a favourite for a particular user
     * @param docRef Document to set as a favourite
     * @param userUuid UUID of the user account
     */
    void createFavouriteForUser(final DocRef docRef, final String userUuid);

    /**
     * Unset the specified document as a favourite for a particular user
     * @param docRef Document to unset as a favourite
     * @param userUuid UUID of the user account
     */
    void deleteFavouriteForUser(final DocRef docRef, final String userUuid);

    /**
     * Retrieve document favourites for the specified user
     *
     * @param userUuid UUID of the user account
     * @return
     */
    List<DocRef> getUserFavourites(final String userUuid);

    /**
     * Return whether the specified document is set as a favourite for the user
     * @return
     */
    boolean isFavourite(final DocRef docRef, final String userUuid);
}
