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

package stroom.search.server;

import stroom.node.shared.Node;
import stroom.query.shared.CoprocessorSettings;
import stroom.query.shared.Search;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;

public class AsyncSearchTask extends ServerTask<VoidResult>implements Serializable {
    private static final long serialVersionUID = -1305243739417365803L;

    private final String searchName;
    private final Search search;
    private final Node targetNode;
    private final int resultSendFrequency;
    private final Map<Integer, CoprocessorSettings> coprocessorMap;
    private final ZonedDateTime now;

    private volatile transient ClusterSearchResultCollector resultCollector;

    public AsyncSearchTask(final String sessionId, final String userName, final String searchName, final Search search,
                           final Node targetNode, final int resultSendFrequency,
                           final Map<Integer, CoprocessorSettings> coprocessorMap, final ZonedDateTime now) {
        super(null, sessionId, userName);
        this.searchName = searchName;
        this.search = search;
        this.targetNode = targetNode;
        this.resultSendFrequency = resultSendFrequency;
        this.coprocessorMap = coprocessorMap;
        this.now = now;
    }

    public String getSearchName() {
        return searchName;
    }

    public Node getTargetNode() {
        return targetNode;
    }

    public Search getSearch() {
        return search;
    }

    public int getResultSendFrequency() {
        return resultSendFrequency;
    }

    public Map<Integer, CoprocessorSettings> getCoprocessorMap() {
        return coprocessorMap;
    }

    public ZonedDateTime getNow() {
        return now;
    }

    public ClusterSearchResultCollector getResultCollector() {
        return resultCollector;
    }

    public void setResultCollector(final ClusterSearchResultCollector resultCollector) {
        this.resultCollector = resultCollector;
    }
}
