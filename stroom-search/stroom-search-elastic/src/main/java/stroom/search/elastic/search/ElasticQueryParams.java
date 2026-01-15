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

package stroom.search.elastic.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.HashMap;
import java.util.Map;

public class ElasticQueryParams {

    private Query query;
    private final Map<String, String> knnFieldQueries;

    public ElasticQueryParams() {
        this.knnFieldQueries = new HashMap<>();
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(final Query query) {
        this.query = query;
    }

    public Map<String, String> getKnnFieldQueries() {
        return knnFieldQueries;
    }

    public void addKnnFieldQuery(final String fieldName, final String query) {
        this.knnFieldQueries.put(fieldName, query);
    }
}
