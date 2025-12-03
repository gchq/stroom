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

package stroom.job.shared;

import stroom.job.shared.JobNode.JobType;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

public class JobNodeUtil {

    public static void setSchedule(final JobNode jobNode, final Schedule schedule) {
        final JobType jobType = getJobType(schedule);
        jobNode.setJobType(jobType);
        jobNode.setSchedule(schedule.getExpression());
    }

    public static JobType getJobType(final Schedule schedule) {
        JobType jobType = JobType.UNKNOWN;
        if (schedule.getType() != null) {
            switch (schedule.getType()) {
                case FREQUENCY: {
                    jobType = JobType.FREQUENCY;
                    break;
                }
                case CRON: {
                    jobType = JobType.CRON;
                    break;
                }
            }
        }
        return jobType;
    }

    public static Schedule getSchedule(final JobNode jobNode) {
        ScheduleType scheduleType = null;
        if (jobNode.getJobType() != null) {
            switch (jobNode.getJobType()) {
                case FREQUENCY: {
                    scheduleType = ScheduleType.FREQUENCY;
                    break;
                }
                case CRON: {
                    scheduleType = ScheduleType.CRON;
                    break;
                }
            }
        }
        if (scheduleType == null) {
            return null;
        }
        return new Schedule(scheduleType, jobNode.getSchedule());
    }
}
