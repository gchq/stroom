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
    private boolean runIfDisabled = false;

    public SimpleScheduleExec(final Trigger trigger) {
        this.trigger = trigger;
    }

    public static SimpleScheduleExec createForImmediateExecution(final Trigger trigger) {
        return new SimpleScheduleExec(trigger, Instant.now(), true);
    }

    private SimpleScheduleExec(final Trigger trigger, final Instant nextExecute) {
        this.trigger = trigger;
        this.nextExecute = nextExecute;
    }

    private SimpleScheduleExec(final Trigger trigger, final Instant nextExecute, final boolean runIfDisabled) {
        this.trigger = trigger;
        this.nextExecute = nextExecute;
        this.runIfDisabled = runIfDisabled;
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
        LOGGER.trace("execute() (before) - now: {}, lastExecute: {}, nextExecute: {}, runIfDisabled: {}",
                now, lastExecute, nextExecute, runIfDisabled);
        boolean willExecute = false;
        if (nextExecute == null) {
            nextExecute = trigger.getNextExecutionTimeAfter(now);
        } else if (now.isAfter(nextExecute)) {
            nextExecute = trigger.getNextExecutionTimeAfter(now);
            lastExecute = now;
            // We are executing, so clear the runIfDisabled state for the next check
            // which may be scheduled rather than as a result of the user doing a 'run now'.
            runIfDisabled = false;
            willExecute = true;
        }
        LOGGER.trace(
                "execute() (after) - now: {}, lastExecute: {}, nextExecute: {}, willExecute: {}, runIfDisabled: {}",
                now, lastExecute, nextExecute, willExecute, runIfDisabled);
        return willExecute;
    }

    public Instant getLastExecutionTime() {
        if (lastExecute != null) {
            return lastExecute;
        }
        return Instant.now();
    }

    /**
     * @return True if this schedule has been marked to disregard the enabled state of the
     * job or jobNode. This MUST be called before {@link SimpleScheduleExec#execute()} is called
     * as {@link SimpleScheduleExec#execute()} will reset the runIfDisabled state.
     */
    public boolean isRunIfDisabled() {
        return runIfDisabled;
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
        sb.append("runIfDisabled=")
                .append(runIfDisabled);
        return sb.toString();
    }

    /**
     * Set the nextExecute time to now, so that the next time execute() is called for this job, it will
     * return true and thus run.
     */
    public SimpleScheduleExec cloneForImmediateExecution() {
        final Instant now = Instant.now();
        LOGGER.debug("cloneWithImmediateExecution() - now: {}, lastExecute: {}, nextExecute: {}",
                now, lastExecute, nextExecute);
        return new SimpleScheduleExec(trigger, now, true);
    }
}
