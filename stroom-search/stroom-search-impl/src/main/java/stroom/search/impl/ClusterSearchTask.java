/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class ClusterSearchTask implements Serializable {
    private final String taskName;
    private final QueryKey key;
    private final Query query;
    private final List<Long> shards;
    private final String[] storedFields;
    private final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap;
    private final String dateTimeLocale;
    private final long now;

    public ClusterSearchTask(final String taskName,
                             final QueryKey key,
                             final Query query,
                             final List<Long> shards,
                             final String[] storedFields,
                             final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap,
                             final String dateTimeLocale,
                             final long now) {
        this.taskName = taskName;
        this.key = key;
        this.query = query;
        this.shards = shards;
        this.storedFields = storedFields;
        this.coprocessorMap = coprocessorMap;
        this.dateTimeLocale = dateTimeLocale;
        this.now = now;
    }

    public String getTaskName() {
        return taskName;
    }

    public QueryKey getKey() {
        return key;
    }

    public Query getQuery() {
        return query;
    }

    public List<Long> getShards() {
        return shards;
    }

    public String[] getStoredFields() {
        return storedFields;
    }

    public Map<CoprocessorKey, CoprocessorSettings> getCoprocessorMap() {
        return coprocessorMap;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public long getNow() {
        return now;
    }
}
