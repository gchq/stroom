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

import stroom.util.shared.SharedObject;

public class JobNodeInfo implements SharedObject {
    private static final long serialVersionUID = 5184354985064930910L;

    private Integer currentTaskCount;
    private Long scheduleReferenceTime;
    private Long lastExecutedTime;

    public JobNodeInfo() {
        // Default constructor necessary for GWT serialisation.
    }

    public JobNodeInfo(final Integer currentTaskCount, final Long scheduleReferenceTime, final Long lastExecutedTime) {
        this.currentTaskCount = currentTaskCount;
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
    }

    public Integer getCurrentTaskCount() {
        return currentTaskCount;
    }

    public Long getScheduleReferenceTime() {
        return scheduleReferenceTime;
    }

    public Long getLastExecutedTime() {
        return lastExecutedTime;
    }
}
