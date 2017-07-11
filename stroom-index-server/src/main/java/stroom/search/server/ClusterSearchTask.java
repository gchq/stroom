/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.server;

import stroom.index.shared.IndexField;
import stroom.node.shared.Node;
import stroom.query.CoprocessorSettings;
import stroom.query.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.api.v1.Query;
import stroom.task.cluster.ClusterTask;

import java.util.List;
import java.util.Map;

public class ClusterSearchTask extends ClusterTask<NodeResult> {
    private static final long serialVersionUID = -1305243739417365803L;

    private final Query query;
    private final List<Long> shards;
    private final Node targetNode;
    private final IndexField[] storedFields;
    private final int resultSendFrequency;
    private final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap;
    private final String dateTimeLocale;
    private final long now;

    public ClusterSearchTask(final String userToken, final String taskName, final Query query,
                             final List<Long> shards, final Node targetNode, final IndexField[] storedFields,
                             final int resultSendFrequency, final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap, final String dateTimeLocale, final long now) {
        super(userToken, taskName);
        this.query = query;
        this.shards = shards;
        this.targetNode = targetNode;
        this.storedFields = storedFields;
        this.resultSendFrequency = resultSendFrequency;
        this.coprocessorMap = coprocessorMap;
        this.dateTimeLocale = dateTimeLocale;
        this.now = now;
    }

    public Node getTargetNode() {
        return targetNode;
    }

    public Query getQuery() {
        return query;
    }

    public List<Long> getShards() {
        return shards;
    }

    public IndexField[] getStoredFields() {
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
