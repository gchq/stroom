/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.server;

import stroom.query.shared.QueryKey;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveQueries {
    private final ConcurrentHashMap<QueryKey, ActiveQuery> activeQueries = new ConcurrentHashMap<>();

    public void destroyUnusedQueries(final Set<QueryKey> keys) {
        // Kill off any searches that are no longer required by the UI.
        final Iterator<Entry<QueryKey, ActiveQuery>> iter = activeQueries.entrySet().iterator();
        while (iter.hasNext()) {
            final Entry<QueryKey, ActiveQuery> entry = iter.next();
            final QueryKey queryKey = entry.getKey();
            final ActiveQuery query = entry.getValue();
            if (keys == null || !keys.contains(queryKey)) {
                // Terminate the associated search task.
                query.getSearchResultCollector().destroy();

                // Remove the collector from the available searches as it is no longer required by the UI.
                iter.remove();
            }
        }
    }

    public ActiveQuery getExistingQuery(final QueryKey queryKey) {
        return activeQueries.get(queryKey);
    }

    public void addNewQuery(final QueryKey queryKey, final ActiveQuery query) {
        final ActiveQuery existingQuery = activeQueries.put(queryKey, query);
        if (existingQuery != null) {
            throw new RuntimeException(
                    "Existing active query found in active query map for '" + queryKey.toString() + "'");
        }
    }

    public void destroy() {
        destroyUnusedQueries(null);
    }
}
