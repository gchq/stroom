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

package stroom.search.server;

import stroom.node.shared.Node;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.api.v2.Query;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

import java.io.Serializable;
import java.util.Map;

public class AsyncSearchTask extends ServerTask<VoidResult> implements Serializable {
    private static final long serialVersionUID = -1305243739417365803L;

    private final String searchName;
    private final Query query;
    private final Node targetNode;
    private final int resultSendFrequency;
    private final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap;
    private final String dateTimeLocale;
    private final long now;

    private volatile transient ClusterSearchResultCollector resultCollector;

    public AsyncSearchTask(final String userToken, final String searchName, final Query query,
                           final Node targetNode, final int resultSendFrequency,
                           final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap, final String dateTimeLocale, final long now) {
        super(null, userToken);
        this.searchName = searchName;
        this.query = query;
        this.targetNode = targetNode;
        this.resultSendFrequency = resultSendFrequency;
        this.coprocessorMap = coprocessorMap;
        this.dateTimeLocale = dateTimeLocale;
        this.now = now;
    }

    public String getSearchName() {
        return searchName;
    }

    public Query getQuery() {
        return query;
    }

    public Node getTargetNode() {
        return targetNode;
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

    public ClusterSearchResultCollector getResultCollector() {
        return resultCollector;
    }

    public void setResultCollector(final ClusterSearchResultCollector resultCollector) {
        this.resultCollector = resultCollector;
    }
}
