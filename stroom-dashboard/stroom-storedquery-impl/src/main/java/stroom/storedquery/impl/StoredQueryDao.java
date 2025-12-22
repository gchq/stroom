/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
