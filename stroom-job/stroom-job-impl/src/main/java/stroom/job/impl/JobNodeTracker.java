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

package stroom.job.impl;

import stroom.job.shared.JobNode;
import stroom.util.date.DateUtil;

import java.util.concurrent.atomic.AtomicInteger;

public class JobNodeTracker {

    private final AtomicInteger currentTaskCount = new AtomicInteger();
    private JobNode jobNode;
    /**
     * Last time we actually executed a task for this job.
     */
    private Long lastExecutedTime;

    public JobNodeTracker(final JobNode jobNode) {
        setJobNode(jobNode);
    }

    public JobNode getJobNode() {
        return jobNode;
    }

    public void setJobNode(final JobNode jobNode) {
        if (jobNode == null) {
            throw new IllegalStateException("jobNode required");
        }
        this.jobNode = jobNode;
    }

    public int getCurrentTaskCount() {
        return currentTaskCount.get();
    }

    public void incrementTaskCount() {
        currentTaskCount.incrementAndGet();
    }

    public void decrementTaskCount() {
        currentTaskCount.decrementAndGet();
    }

    public Long getLastExecutedTime() {
        return lastExecutedTime;
    }

    public void setLastExecutedTime(final Long lastExecutedTime) {
        this.lastExecutedTime = lastExecutedTime;
    }

    @Override
    public int hashCode() {
        return jobNode.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof JobNodeTracker)) {
            return false;
        }

        final JobNodeTracker tracker = (JobNodeTracker) obj;
        return jobNode.equals(tracker.jobNode);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(jobNode.toString());
        sb.append("currentTaskCount=\"");
        sb.append(currentTaskCount.get());
        sb.append("\" ");
        if (lastExecutedTime != null) {
            sb.append("lastExecutedTime=\"");
            sb.append(DateUtil.createNormalDateTimeString(lastExecutedTime));
            sb.append("\" ");
        }
        return sb.toString();
    }
}
