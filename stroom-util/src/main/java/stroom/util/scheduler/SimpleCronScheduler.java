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

public class SimpleCronScheduler implements Scheduler {
    private final SimpleCron simpleCron;
    private Long lastExecute;
    private Long nextExecute;

    public SimpleCronScheduler(final String expression) {
        this.simpleCron = SimpleCron.compile(expression);
    }

    SimpleCronScheduler(final SimpleCron simpleCron) {
        this.simpleCron = simpleCron;
    }

    /**
     * @return date to aid testing.
     */
    protected Long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     * Should we execute.
     *
     * @return
     */
    public boolean execute(final long timeNow) {
        final Long now = timeNow;
        if (nextExecute == null) {
            nextExecute = simpleCron.getNextTime(now);
        } else if (now > nextExecute) {
            nextExecute = simpleCron.getNextTime(now);
            lastExecute = now;
            return true;
        }
        return false;
    }

    /**
     * Should we execute.
     *
     * @return
     */
    @Override
    public boolean execute() {
        return execute(getCurrentTime());
    }

    @Override
    public Long getScheduleReferenceTime() {
        if (lastExecute != null) {
            return lastExecute;
        }

        return System.currentTimeMillis();
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
