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

package stroom.cluster.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterTaskHandler;
import stroom.cluster.task.api.ClusterTaskRef;
import stroom.cluster.task.api.TerminateTaskClusterTask;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;


class TerminateTaskClusterHandler implements ClusterTaskHandler<TerminateTaskClusterTask, ResultPage<TaskProgress>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminateTaskClusterHandler.class);

    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    TerminateTaskClusterHandler(final TaskManager taskManager,
                                final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public void exec(final TerminateTaskClusterTask task, final ClusterTaskRef<ResultPage<TaskProgress>> clusterTaskRef) {
        securityContext.secure(() -> {
            final FindTaskCriteria criteria = task.getCriteria();
            if (criteria != null) {
                LOGGER.debug("exec() - {}", criteria.toString());

                // Terminate tasks on this node
                taskManager.terminate(criteria, task.isKill());
            }
        });
    }
}
