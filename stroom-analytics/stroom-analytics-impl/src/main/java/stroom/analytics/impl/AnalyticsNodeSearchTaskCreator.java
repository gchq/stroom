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

package stroom.analytics.impl;

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.query.api.Query;
import stroom.search.impl.FederatedSearchTask;
import stroom.search.impl.NodeSearchTask;
import stroom.search.impl.NodeSearchTaskType;
import stroom.search.impl.NodeTaskCreator;
import stroom.task.api.TaskContext;

import jakarta.inject.Inject;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnalyticsNodeSearchTaskCreator implements NodeTaskCreator {

    private final TargetNodeSetFactory targetNodeSetFactory;

    @Inject
    public AnalyticsNodeSearchTaskCreator(final TargetNodeSetFactory targetNodeSetFactory) {
        this.targetNodeSetFactory = targetNodeSetFactory;
    }

    @Override
    public Map<String, NodeSearchTask> createNodeSearchTasks(final FederatedSearchTask task,
                                                             final Query query,
                                                             final TaskContext parentContext) {
        try {
            final NodeSearchTask nodeSearchTask = new NodeSearchTask(
                    NodeSearchTaskType.ANALYTICS,
                    parentContext.getTaskId(),
                    "Analytics Node Search",
                    task.getSearchRequestSource(),
                    task.getKey(),
                    query,
                    task.getSettings(),
                    task.getDateTimeSettings(),
                    null);

            // Get the nodes that we are going to send the search request to.
            final Set<String> targetNodes = targetNodeSetFactory.getEnabledTargetNodeSet();
            return targetNodes.stream().collect(Collectors.toMap(Function.identity(), (e) -> nodeSearchTask));
        } catch (final NodeNotFoundException | NullClusterStateException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
