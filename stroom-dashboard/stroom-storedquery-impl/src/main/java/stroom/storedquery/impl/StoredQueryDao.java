package stroom.storedquery.impl;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.Set;

public interface StoredQueryDao extends HasIntCrud<StoredQuery> {

    ResultPage<StoredQuery> find(final FindStoredQueryCriteria criteria);

    void clean(final String ownerUuid,
               final int historyItemsRetention,
               final long oldestCrtMs);

    /**
     * @return The {@link Set} of user UUIDs for users that have at least one non-favourite
     * stored query.
     */
    Set<String> getUsersWithNonFavourites();

    Integer getOldestId(final String ownerUuid,
                        final boolean favourite,
                        final int retain);

    int delete(UserRef ownerUuid);
}
