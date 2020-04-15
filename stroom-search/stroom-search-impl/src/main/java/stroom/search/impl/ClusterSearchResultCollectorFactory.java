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

import stroom.cluster.task.api.ClusterResultCollectorCache;
import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Set;
import java.util.concurrent.Executor;

public class ClusterSearchResultCollectorFactory {
    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider;
    private final Provider<ClusterTaskTerminator> clusterTaskTerminatorProvider;
    private final ClusterResultCollectorCache clusterResultCollectorCache;

    @Inject
    private ClusterSearchResultCollectorFactory(final Executor executor,
                                                final Provider<TaskContext> taskContextProvider,
                                                final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider,
                                                final Provider<ClusterTaskTerminator> clusterTaskTerminatorProvider,
                                                final ClusterResultCollectorCache clusterResultCollectorCache) {
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.asyncSearchTaskHandlerProvider = asyncSearchTaskHandlerProvider;
        this.clusterTaskTerminatorProvider = clusterTaskTerminatorProvider;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
    }

    public ClusterSearchResultCollector create(final AsyncSearchTask task,
                                               final String nodeName,
                                               final Set<String> highlights,
                                               final ResultHandler resultHandler,
                                               final Sizes defaultMaxResultsSizes,
                                               final Sizes storeSize,
                                               final CompletionState completionState) {
        return new ClusterSearchResultCollector(executor,
                taskContextProvider,
                asyncSearchTaskHandlerProvider,
                clusterTaskTerminatorProvider.get(),
                task,
                nodeName,
                highlights,
                clusterResultCollectorCache,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize,
                completionState);
    }
}
