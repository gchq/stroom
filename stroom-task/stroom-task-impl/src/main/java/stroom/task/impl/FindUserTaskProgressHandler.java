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
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindUserTaskProgressAction;
import stroom.task.shared.TaskProgress;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Sort.Direction;

import javax.inject.Inject;

class FindUserTaskProgressHandler
        extends FindTaskProgressHandlerBase<FindUserTaskProgressAction, BaseResultList<TaskProgress>> {
    private final SessionIdProvider sessionIdProvider;
    private final SecurityContext securityContext;

    @Inject
    FindUserTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                                final SessionIdProvider sessionIdProvider,
                                final SecurityContext securityContext) {
        super(dispatchHelper);
        this.sessionIdProvider = sessionIdProvider;
        this.securityContext = securityContext;
    }

    @Override
    public BaseResultList<TaskProgress> exec(final FindUserTaskProgressAction action) {
        return securityContext.secureResult(() -> {
            final ExtendedFindTaskProgressCriteria criteria = new ExtendedFindTaskProgressCriteria();
            criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, Direction.DESCENDING, false);
            criteria.setSessionId(sessionIdProvider.get());
            return doExec(action, criteria);
        });
    }
}
