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

import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;


class FindTaskProgressClusterHandler
        extends AbstractTaskHandler<FindTaskProgressClusterTask, ResultPage<TaskProgress>> {
    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    FindTaskProgressClusterHandler(final TaskManager taskManager,
                                   final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<TaskProgress> exec(final FindTaskProgressClusterTask task) {
        return securityContext.secureResult(() -> taskManager.find(task.getCriteria()));
    }
}
