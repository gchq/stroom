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

package stroom.analytics.impl;

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ExecutionTracker;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

@AutoLogged(OperationType.UNLOGGED)
class ExecutionScheduleResourceImpl implements ExecutionScheduleResource {

    private final Provider<ExecutionScheduleDao> executionScheduleDaoProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    ExecutionScheduleResourceImpl(final Provider<ExecutionScheduleDao> executionScheduleDaoProvider,
                                  final Provider<SecurityContext> securityContextProvider) {
        this.executionScheduleDaoProvider = executionScheduleDaoProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    public ExecutionSchedule createExecutionSchedule(ExecutionSchedule executionSchedule) {
        return executionScheduleDaoProvider.get().createExecutionSchedule(checkRunAs(executionSchedule));
    }

    @Override
    public ExecutionSchedule updateExecutionSchedule(final ExecutionSchedule executionSchedule) {
        return executionScheduleDaoProvider.get().updateExecutionSchedule(checkRunAs(executionSchedule));
    }

    @Override
    public Boolean deleteExecutionSchedule(final ExecutionSchedule executionSchedule) {
        return executionScheduleDaoProvider.get().deleteExecutionSchedule(executionSchedule);
    }

    @Override
    public ResultPage<ExecutionSchedule> fetchExecutionSchedule(final ExecutionScheduleRequest request) {
        return executionScheduleDaoProvider.get().fetchExecutionSchedule(request);
    }

    @Override
    public ResultPage<ExecutionHistory> fetchExecutionHistory(final ExecutionHistoryRequest request) {
        return executionScheduleDaoProvider.get().fetchExecutionHistory(request);
    }

    @Override
    public ExecutionTracker fetchTracker(final ExecutionSchedule schedule) {
        return executionScheduleDaoProvider.get().fetchTracker(schedule);
    }

    private ExecutionSchedule checkRunAs(final ExecutionSchedule executionSchedule) {
        final SecurityContext securityContext = securityContextProvider.get();
        final UserRef currentUser = securityContext.getUserRef();
        if (executionSchedule.getRunAsUser() == null) {
            return executionSchedule
                    .copy()
                    .runAsUser(currentUser)
                    .build();
        } else if (!Objects.equals(executionSchedule.getRunAsUser(), currentUser) && !securityContext.hasAppPermission(
                AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(currentUser, "You do not have permission to execute as " +
                    executionSchedule.getRunAsUser().toDisplayString());
        }
        return executionSchedule;
    }
}
