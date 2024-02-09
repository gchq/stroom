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

import stroom.job.shared.Schedule;
import stroom.job.shared.ScheduleType;
import stroom.job.shared.ScheduledTimes;
import stroom.util.date.DateUtil;
import stroom.util.scheduler.QuartzCronScheduler;
import stroom.util.shared.ModelStringUtil;

class ScheduleService {

    /**
     * Gets a scheduled time object for a given schedule based on the current
     * time. The scheduled time object holds the reference time, last scheduled
     * time and next scheduled time.
     *
     * @param expression The cron expression to use.
     * @return The scheduled times based on the supplied cron expression.
     * @throws RuntimeException Could be thrown.
     */
    ScheduledTimes getScheduledTimes(final Schedule schedule,
                                     final Long scheduleReferenceTime,
                                     final Long lastExecutedTime) {
        ScheduledTimes scheduledTimes = null;

        if (ScheduleType.CRON.equals(schedule.getType())) {
            final QuartzCronScheduler cron = new QuartzCronScheduler(schedule.getExpression());
            Long time = System.currentTimeMillis();
            time = cron.getNextExecute(time);
            scheduledTimes = getScheduledTimes(lastExecutedTime, time);

        } else if (ScheduleType.FREQUENCY.equals(schedule.getType())) {
            if (schedule.getExpression() == null || schedule.getExpression().trim().length() == 0) {
                throw new NumberFormatException("Frequency expression cannot be null");
            }

            final Long duration = ModelStringUtil.parseDurationString(schedule.getExpression());
            if (duration == null) {
                throw new NumberFormatException("Unable to parse frequency expression");
            }

            Long time = scheduleReferenceTime;
            if (time != null) {
                time = time + duration;
            }
            scheduledTimes = getScheduledTimes(lastExecutedTime, time);
        }

        return scheduledTimes;
    }

    private ScheduledTimes getScheduledTimes(final Long lastExecutedTime, final Long nextScheduledTime) {
        return new ScheduledTimes(getDateString(lastExecutedTime), getDateString(nextScheduledTime));
    }

    private String getDateString(final Long ms) {
        if (ms == null) {
            return "Never";
        }
        return DateUtil.createNormalDateTimeString(ms);
    }
}
