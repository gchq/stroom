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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Instant;

public class SimpleScheduleExec {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleScheduleExec.class);

    private final Trigger trigger;
    private Instant lastExecute;
    private Instant nextExecute;

    public SimpleScheduleExec(final Trigger trigger) {
        this.trigger = trigger;
    }

    private SimpleScheduleExec(final Trigger trigger, final Instant nextExecute) {
        this.trigger = trigger;
        this.nextExecute = nextExecute;
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
        boolean willExecute = false;
        if (nextExecute == null) {
            nextExecute = trigger.getNextExecutionTimeAfter(now);
        } else if (now.isAfter(nextExecute)) {
            nextExecute = trigger.getNextExecutionTimeAfter(now);
            lastExecute = now;
            willExecute = true;
        }
        LOGGER.trace("execute() - now: {}, lastExecute: {}, nextExecute: {}, willExecute: {}",
                now, lastExecute, nextExecute, willExecute);
        return willExecute;
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

    public SimpleScheduleExec cloneWithImmediateExecution() {
        // Set the nextExecute time to now, so that the next time execute() is called for this job, it will
        // return true and thus run.
        final Instant now = Instant.now();
        LOGGER.debug("cloneWithImmediateExecution() - now: {}, lastExecute: {}, nextExecute: {}",
                now, lastExecute, nextExecute);
        return new SimpleScheduleExec(trigger, now);
    }
}
