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

import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.ClusterResultCollectorCache;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Set;

public class ClusterSearchResultCollectorFactory {
    private final TaskManager taskManager;
    private final Provider<TaskContext> taskContextProvider;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final SecurityContext securityContext;

    @Inject
    private ClusterSearchResultCollectorFactory(final TaskManager taskManager,
                                                final Provider<TaskContext> taskContextProvider,
                                                final ClusterDispatchAsyncHelper dispatchHelper,
                                                final ClusterResultCollectorCache clusterResultCollectorCache,
                                                final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.taskContextProvider = taskContextProvider;
        this.dispatchHelper = dispatchHelper;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.securityContext = securityContext;
    }

    public ClusterSearchResultCollector create(final AsyncSearchTask task,
                                               final String nodeName,
                                               final Set<String> highlights,
                                               final ResultHandler resultHandler,
                                               final Sizes defaultMaxResultsSizes,
                                               final Sizes storeSize,
                                               final CompletionState completionState) {
        return new ClusterSearchResultCollector(taskManager,
                taskContextProvider.get(),
                task,
                dispatchHelper,
                nodeName,
                highlights,
                clusterResultCollectorCache,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize,
                completionState,
                securityContext);
    }
}
