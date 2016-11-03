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

import stroom.jobsystem.shared.JobNode.JobType;
import stroom.util.shared.SharedObject;

public class TaskType implements SharedObject {
    private static final long serialVersionUID = -520024408007948736L;

    private JobType jobType;
    private String schedule;

    public TaskType() {
        // Default constructor necessary for GWT serialisation.
    }

    public TaskType(final JobType jobType, final String schedule) {
        this.jobType = jobType;
        this.schedule = schedule;
    }

    public JobType getJobType() {
        return jobType;
    }

    public String getSchedule() {
        return schedule;
    }
}
