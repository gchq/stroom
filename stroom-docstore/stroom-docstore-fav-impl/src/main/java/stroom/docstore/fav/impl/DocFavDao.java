package stroom.docstore.fav.impl;

import stroom.docref.DocRef;
import stroom.security.api.UserIdentity;

import java.util.List;

public interface DocFavDao {

    /**
     * Set the specified document as a favourite for a particular user
     * @param docRef Document to set as a favourite
     * @param userId UUID of the user account
     */
    void setDocFavForUser(final DocRef docRef, final String userId);

    /**
     * Unset the specified document as a favourite for a particular user
     * @param docRef Document to unset as a favourite
     * @param userId UUID of the user account
     */
    void deleteDocFavForUser(final DocRef docRef, final String userId);

    /**
     * Retrieve document favourites for the specified user
     *
     * @param userId UUID of the user account
     * @return
     */
    List<DocRef> getUserDocFavs(final String userId);

    /**
     * Return whether the specified document is set as a favourite for the user
     * @return
     */
    boolean isDocFav(final DocRef docRef, final String userId);
}
