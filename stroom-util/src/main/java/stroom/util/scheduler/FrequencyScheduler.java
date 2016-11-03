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
import stroom.util.shared.ModelStringUtil;

public class FrequencyScheduler implements Scheduler {
    private final long interval;
    private volatile long intervalToUse;
    private volatile long lastExecution;

    public FrequencyScheduler(final String frequency) {
        if (frequency == null || frequency.trim().length() == 0) {
            throw new NumberFormatException("Frequency expression cannot be null");
        }

        final Long duration = ModelStringUtil.parseDurationString(frequency);
        if (duration == null) {
            throw new NumberFormatException("Unable to parse frequency expression");
        }

        interval = duration;
        calculateIntervalToUse();
        lastExecution = System.currentTimeMillis();
    }

    /**
     * Add in a +-5% random on the interval so that all jobs don't fire at same
     * time.
     */
    private void calculateIntervalToUse() {
        intervalToUse = interval;
        if (intervalToUse > 100) {
            double offset = intervalToUse;
            offset = offset * 0.05D * (Math.random() - 0.5D);
            intervalToUse += offset;
        }
    }

    @Override
    public boolean execute() {
        final long now = System.currentTimeMillis();
        if (lastExecution + intervalToUse <= now) {
            lastExecution = now;
            calculateIntervalToUse();
            return true;
        }

        return false;
    }

    @Override
    public Long getScheduleReferenceTime() {
        return lastExecution;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("FrequencyScheduler ");
        sb.append("lastExecution=\"");
        sb.append(DateUtil.createNormalDateTimeString(lastExecution));
        sb.append("\" ");
        sb.append("interval=\"");
        sb.append(ModelStringUtil.formatDurationString(interval));
        sb.append("\" ");
        return sb.toString();
    }
}
