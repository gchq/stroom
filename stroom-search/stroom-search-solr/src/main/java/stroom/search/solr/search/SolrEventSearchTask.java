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

package stroom.search.solr.search;

import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.common.v2.EventRef;

public class SolrEventSearchTask {
    private final SearchRequestSource searchRequestSource;
    private final QueryKey key;
    private final Query query;
    private final EventRef minEvent;
    private final EventRef maxEvent;
    private final long maxStreams;
    private final long maxEvents;
    private final long maxEventsPerStream;

    public SolrEventSearchTask(final SearchRequestSource searchRequestSource,
                               final QueryKey key,
                               final Query query,
                               final EventRef minEvent,
                               final EventRef maxEvent,
                               final long maxStreams,
                               final long maxEvents,
                               final long maxEventsPerStream) {
        this.searchRequestSource = searchRequestSource;
        this.key = key;
        this.query = query;
        this.minEvent = minEvent;
        this.maxEvent = maxEvent;
        this.maxStreams = maxStreams;
        this.maxEvents = maxEvents;
        this.maxEventsPerStream = maxEventsPerStream;
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public QueryKey getKey() {
        return key;
    }

    public Query getQuery() {
        return query;
    }

    public EventRef getMinEvent() {
        return minEvent;
    }

    public EventRef getMaxEvent() {
        return maxEvent;
    }

    public long getMaxStreams() {
        return maxStreams;
    }

    public long getMaxEvents() {
        return maxEvents;
    }

    public long getMaxEventsPerStream() {
        return maxEventsPerStream;
    }
}
