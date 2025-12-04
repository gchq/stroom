/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.ScheduledTimes;
import stroom.util.scheduler.Trigger;
import stroom.util.scheduler.TriggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class ScheduleService {

    /**
     * Gets a scheduled time object for a given schedule based on the current
     * time. The scheduled time object holds the reference time, last scheduled
     * time and next scheduled time.
     *
     * @param request The cron expression to use.
     * @return The scheduled times based on the supplied cron expression.
     * @throws RuntimeException Could be thrown.
     */
    ScheduledTimes getScheduledTimes(final GetScheduledTimesRequest request) {
        try {
            final Schedule schedule = request.getSchedule();
            if (schedule == null) {
                throw new RuntimeException("No schedule has been set");
            }
            if (schedule.getType() == null) {
                throw new RuntimeException("Schedule type has not been set");
            }
            if (schedule.getExpression() == null || schedule.getExpression().isEmpty()) {
                throw new RuntimeException("Schedule expression has not been set");
            }

            Trigger trigger;
            Schedule workingSchedule = schedule;
            try {
                trigger = TriggerFactory.create(workingSchedule);
            } catch (final RuntimeException e) {
                switch (workingSchedule.getType()) {
                    // Try other schedule types.
                    case ScheduleType.CRON -> {
                        workingSchedule = new Schedule(ScheduleType.FREQUENCY, workingSchedule.getExpression());
                        trigger = TriggerFactory.create(workingSchedule);
                    }
                    case ScheduleType.FREQUENCY -> {
                        workingSchedule = new Schedule(ScheduleType.CRON, workingSchedule.getExpression());
                        trigger = TriggerFactory.create(workingSchedule);
                    }
                    default -> throw e;
                }
            }

            // Validate the schedule meets the restrictions.
            if (request.getScheduleRestriction() != null) {
                if (ScheduleType.CRON.equals(workingSchedule.getType())) {
                    final String[] expressionParts = workingSchedule.getExpression().split(" ");
                    if (expressionParts.length > 0 && expressionParts[0].equals("*")) {
                        if (!request.getScheduleRestriction().isAllowSecond()) {
                            throw new RuntimeException("You cannot execute every second");
                        }
                    } else if (expressionParts.length > 1 && expressionParts[1].equals("*")) {
                        if (!request.getScheduleRestriction().isAllowMinute()) {
                            throw new RuntimeException("You cannot execute every minute");
                        }
                    } else if (expressionParts.length > 2 && expressionParts[2].equals("*")) {
                        if (!request.getScheduleRestriction().isAllowHour()) {
                            throw new RuntimeException("You cannot execute every hour");
                        }
                    }
                } else if (ScheduleType.FREQUENCY.equals(workingSchedule.getType())) {
                    final Instant now = Instant.now();
                    final Instant next = trigger.getNextExecutionTimeAfter(now);
                    if (!request.getScheduleRestriction().isAllowSecond()) {
                        if (!next.isAfter(now.plus(1, ChronoUnit.SECONDS))) {
                            throw new RuntimeException("You cannot execute every second");
                        }
                    }
                    if (!request.getScheduleRestriction().isAllowMinute()) {
                        if (!next.isAfter(now.plus(1, ChronoUnit.MINUTES))) {
                            throw new RuntimeException("You cannot execute every minute");
                        }
                    }
                    if (!request.getScheduleRestriction().isAllowHour()) {
                        if (!next.isAfter(now.plus(1, ChronoUnit.HOURS))) {
                            throw new RuntimeException("You cannot execute every hour");
                        }
                    }
                }
            }

            final Instant afterTime = request.getScheduleReferenceTime() == null
                    ? Instant.now()
                    : Instant.ofEpochMilli(request.getScheduleReferenceTime());
            final Instant nextScheduledTime = trigger.getNextExecutionTimeAfter(afterTime);
            return new ScheduledTimes(
                    workingSchedule,
                    NullSafe.get(nextScheduledTime, Instant::toEpochMilli),
                    null);
        } catch (final Exception e) {
            return new ScheduledTimes(
                    request.getSchedule(),
                    null,
                    e.getMessage());
        }
    }
}
