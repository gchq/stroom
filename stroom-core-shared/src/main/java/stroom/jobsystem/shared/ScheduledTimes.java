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

public class ScheduledTimes implements SharedObject {
    private static final long serialVersionUID = 3064047113379759479L;
    private String lastExecutedTime;
    private String nextScheduledTime;

    public ScheduledTimes() {
        // Default constructor necessary for GWT serialisation.
    }

    public ScheduledTimes(final String lastExecutedTime, final String nextScheduledTime) {
        this.lastExecutedTime = lastExecutedTime;
        this.nextScheduledTime = nextScheduledTime;
    }

    public String getLastExecutedTime() {
        return lastExecutedTime;
    }

    public String getNextScheduledTime() {
        return nextScheduledTime;
    }
}
