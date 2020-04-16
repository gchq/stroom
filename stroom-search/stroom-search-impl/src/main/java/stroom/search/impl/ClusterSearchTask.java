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

import stroom.cluster.task.api.ClusterTask;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.search.resultsender.NodeResult;

import java.util.List;
import java.util.Map;

public class ClusterSearchTask extends ClusterTask<NodeResult> {
    private static final long serialVersionUID = -1305243739417365803L;

    private final Query query;
    private final List<Long> shards;
    private final String[] storedFields;
    private final int resultSendFrequency;
    private final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap;
    private final String dateTimeLocale;
    private final long now;

    public ClusterSearchTask(final String taskName,
                             final Query query,
                             final List<Long> shards,
                             final String[] storedFields,
                             final int resultSendFrequency,
                             final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap,
                             final String dateTimeLocale,
                             final long now) {
        super(taskName);
        this.query = query;
        this.shards = shards;
        this.storedFields = storedFields;
        this.resultSendFrequency = resultSendFrequency;
        this.coprocessorMap = coprocessorMap;
        this.dateTimeLocale = dateTimeLocale;
        this.now = now;
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

    public int getResultSendFrequency() {
        return resultSendFrequency;
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
