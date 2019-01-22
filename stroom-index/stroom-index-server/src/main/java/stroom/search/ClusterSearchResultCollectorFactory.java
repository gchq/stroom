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

import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.cluster.api.ClusterDispatchAsyncHelper;

import javax.inject.Inject;
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
                                               final String nodeName,
                                               final Set<String> highlights,
                                               final ResultHandler resultHandler,
                                               final Sizes defaultMaxResultsSizes,
                                               final Sizes storeSize,
                                               final CompletionState completionState) {
        return new ClusterSearchResultCollector(taskManager,
                taskContext,
                task,
                dispatchHelper,
                nodeName,
                highlights,
                clusterResultCollectorCache,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize,
                completionState);
    }
}
