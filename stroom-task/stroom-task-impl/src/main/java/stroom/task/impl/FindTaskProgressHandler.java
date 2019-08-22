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

import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.shared.FindTaskProgressAction;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.ResultList;

import javax.inject.Inject;


class FindTaskProgressHandler
        extends FindTaskProgressHandlerBase<FindTaskProgressAction, ResultList<TaskProgress>> {
    private final SecurityContext securityContext;

    @Inject
    FindTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                            final SecurityContext securityContext) {
        super(dispatchHelper);
        this.securityContext = securityContext;
    }

    @Override
    public ResultList<TaskProgress> exec(final FindTaskProgressAction action) {
        return securityContext.secureResult(PermissionNames.MANAGE_TASKS_PERMISSION, () -> doExec(action, action.getCriteria()));
    }
}
