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

package stroom.job.impl;

import stroom.job.shared.GetScheduledTimesAction;
import stroom.job.shared.ScheduledTimes;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class GetScheduledTimesHandler extends AbstractTaskHandler<GetScheduledTimesAction, ScheduledTimes> {
    private final ScheduleService scheduleService;
    private final SecurityContext securityContext;

    @Inject
    GetScheduledTimesHandler(final ScheduleService scheduleService,
                             final SecurityContext securityContext) {
        this.scheduleService = scheduleService;
        this.securityContext = securityContext;
    }

    @Override
    public ScheduledTimes exec(final GetScheduledTimesAction task) {
        return securityContext.secureResult(() -> scheduleService.getScheduledTimes(task.getJobType(), task.getScheduleReferenceTime(),
                task.getLastExecutedTime(), task.getSchedule()));
    }
}
