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

package stroom.task;

import stroom.entity.shared.ResultList;
import stroom.security.shared.PermissionNames;
import stroom.security.Security;
import stroom.task.api.TaskHandlerBean;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.shared.FindTaskProgressAction;
import stroom.task.shared.TaskProgress;

import javax.inject.Inject;

@TaskHandlerBean(task = FindTaskProgressAction.class)
class FindTaskProgressHandler
        extends FindTaskProgressHandlerBase<FindTaskProgressAction, ResultList<TaskProgress>> {
    private final Security security;

    @Inject
    FindTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                            final Security security) {
        super(dispatchHelper);
        this.security = security;
    }

    @Override
    public ResultList<TaskProgress> exec(final FindTaskProgressAction action) {
        return security.secureResult(PermissionNames.MANAGE_TASKS_PERMISSION, () -> doExec(action, action.getCriteria()));
    }
}
