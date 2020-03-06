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

package stroom.job.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.ScheduledTimeResource;
import stroom.job.shared.ScheduledTimes;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;

public class ScheduledTimeResourceImpl implements ScheduledTimeResource, HasHealthCheck {
    private final ScheduleService scheduleService;
    private final SecurityContext securityContext;

    @Inject
    private ScheduledTimeResourceImpl(final ScheduleService scheduleService,
                                      final SecurityContext securityContext) {
        this.scheduleService = scheduleService;
        this.securityContext = securityContext;
    }

    @Override
    public ScheduledTimes get(final GetScheduledTimesRequest request) {
        return securityContext.secureResult(() ->
                scheduleService.getScheduledTimes(
                        request.getJobType(),
                        request.getScheduleReferenceTime(),
                        request.getLastExecutedTime(),
                        request.getSchedule()));
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}