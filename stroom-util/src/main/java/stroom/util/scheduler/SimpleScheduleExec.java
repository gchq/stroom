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

package stroom.util.scheduler;

import stroom.util.date.DateUtil;

import java.time.Instant;

public class SimpleScheduleExec {

    private final Trigger trigger;
    private Instant lastExecute;
    private Instant nextExecute;

    public SimpleScheduleExec(final Trigger trigger) {
        this.trigger = trigger;
    }

    /**
     * Should we execute.
     *
     * @return
     */
    public boolean execute() {
        return execute(Instant.now());
    }

    public boolean execute(final Instant now) {
        if (nextExecute == null) {
            nextExecute = trigger.getNextExecutionTimeAfter(now);
        } else if (now.isAfter(nextExecute)) {
            nextExecute = trigger.getNextExecutionTimeAfter(now);
            lastExecute = now;
            return true;
        }
        return false;
    }

    public Instant getLastExecutionTime() {
        if (lastExecute != null) {
            return lastExecute;
        }
        return Instant.now();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SimpleCron ");
        if (lastExecute != null) {
            sb.append("lastExecute=\"");
            sb.append(DateUtil.createNormalDateTimeString(lastExecute));
            sb.append("\" ");
        }
        if (nextExecute != null) {
            sb.append("nextExecute=\"");
            sb.append(DateUtil.createNormalDateTimeString(nextExecute));
            sb.append("\" ");
        }
        return sb.toString();
    }
}
