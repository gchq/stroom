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

package stroom.search;

import stroom.node.shared.Node;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.StoreSize;
import stroom.task.api.TaskContext;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.ClusterResultCollectorCache;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

public class ClusterSearchResultCollectorFactory {
    private final TaskManager taskManager;
    private final TaskContext taskContext;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final ClusterResultCollectorCache clusterResultCollectorCache;

    @Inject
    private ClusterSearchResultCollectorFactory(final TaskManager taskManager,
                                                final TaskContext taskContext,
                                                final ClusterDispatchAsyncHelper dispatchHelper,
                                                final ClusterResultCollectorCache clusterResultCollectorCache) {
        this.taskManager = taskManager;
        this.taskContext = taskContext;
        this.dispatchHelper = dispatchHelper;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
    }

    public ClusterSearchResultCollector create(final AsyncSearchTask task,
                                               final Node node,
                                               final Set<String> highlights,
                                               final ResultHandler resultHandler,
                                               final List<Integer> defaultMaxResultsSizes,
                                               final StoreSize storeSize,
                                               final CompletionState completionState) {
        return new ClusterSearchResultCollector(taskManager,
                taskContext,
                task,
                dispatchHelper,
                node,
                highlights,
                clusterResultCollectorCache,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize,
                completionState);
    }
}
