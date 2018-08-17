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

package stroom.jobsystem;

import stroom.jobsystem.shared.GetScheduledTimesAction;
import stroom.jobsystem.shared.ScheduledTimes;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = GetScheduledTimesAction.class)
class GetScheduledTimesHandler extends AbstractTaskHandler<GetScheduledTimesAction, ScheduledTimes> {
    private final ScheduleService scheduleService;
    private final Security security;

    @Inject
    GetScheduledTimesHandler(final ScheduleService scheduleService,
                             final Security security) {
        this.scheduleService = scheduleService;
        this.security = security;
    }

    @Override
    public ScheduledTimes exec(final GetScheduledTimesAction task) {
        return security.secureResult(() -> scheduleService.getScheduledTimes(task.getJobType(), task.getScheduleReferenceTime(),
                task.getLastExecutedTime(), task.getSchedule()));
    }
}
