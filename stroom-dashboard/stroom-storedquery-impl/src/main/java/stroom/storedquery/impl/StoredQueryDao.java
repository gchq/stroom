package stroom.storedquery.impl;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Clearable;
import stroom.util.shared.HasIntCrud;

import java.util.List;

public interface StoredQueryDao extends HasIntCrud<StoredQuery>, Clearable {
    ResultPage<StoredQuery> find(FindStoredQueryCriteria criteria);

    void clean(final String user, final boolean favourite, final Integer oldestId, final long oldestCrtMs);

    List<String> getUsers(final boolean favourite);

    Integer getOldestId(final String user, final boolean favourite, final int retain);
}
