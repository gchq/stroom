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

package stroom.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterResult;
import stroom.cluster.task.api.ClusterTaskHandler;
import stroom.cluster.task.api.ClusterTaskRef;
import stroom.cluster.task.api.ClusterWorker;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;


class FindTaskProgressClusterHandler implements ClusterTaskHandler<FindTaskProgressClusterTask, ResultPage<TaskProgress>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FindTaskProgressClusterHandler.class);

    private final ClusterWorker clusterWorker;
    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    FindTaskProgressClusterHandler(final ClusterWorker clusterWorker,
                                   final TaskManager taskManager,
                                   final SecurityContext securityContext) {
        this.clusterWorker = clusterWorker;
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public void exec(final FindTaskProgressClusterTask task, final ClusterTaskRef<ResultPage<TaskProgress>> clusterTaskRef) {
        try {
            final ResultPage<TaskProgress> resultPage = securityContext.secureResult(() ->
                    taskManager.find(task.getCriteria()));
            clusterWorker.sendResult(ClusterResult.success(clusterTaskRef, resultPage));
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            clusterWorker.sendResult(ClusterResult.failure(clusterTaskRef, e));
        }
    }
}
