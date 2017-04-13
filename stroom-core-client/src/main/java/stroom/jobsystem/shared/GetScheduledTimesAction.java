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

package stroom.jobsystem.shared;

import stroom.dispatch.shared.Action;
import stroom.jobsystem.shared.JobNode.JobType;
import stroom.widget.customdatebox.client.ClientDateUtil;

public class GetScheduledTimesAction extends Action<ScheduledTimes> {
    private static final long serialVersionUID = -5419140463010782005L;

    private JobType jobType;
    private Long scheduleReferenceTime;
    private Long lastExecutedTime;
    private String schedule;

    public GetScheduledTimesAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public GetScheduledTimesAction(final JobType jobType, final Long scheduleReferenceTime, final Long lastExecutedTime,
            final String schedule) {
        this.jobType = jobType;
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
        this.schedule = schedule;
    }

    public JobType getJobType() {
        return jobType;
    }

    public Long getScheduleReferenceTime() {
        return scheduleReferenceTime;
    }

    public Long getLastExecutedTime() {
        return lastExecutedTime;
    }

    public String getSchedule() {
        return schedule;
    }

    @Override
    public String getTaskName() {
        return "Get Scheduled Times jobType=" + jobType + ", schedule=" + schedule + ", lastExecutedTime="
                + ClientDateUtil.toISOString(lastExecutedTime);
    }
}
