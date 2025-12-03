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

import java.io.Serializable;

class DistributedRequiredTask implements Serializable {

    private static final long serialVersionUID = 2651416970893054545L;

    private final String jobName;
    private final int requiredTaskCount;

    DistributedRequiredTask(final String jobName, final int requiredTaskCount) {
        this.jobName = jobName;
        this.requiredTaskCount = requiredTaskCount;
    }

    public String getJobName() {
        return jobName;
    }

    public int getRequiredTaskCount() {
        return requiredTaskCount;
    }

    @Override
    public String toString() {
        return requiredTaskCount + " : " + jobName;
    }
}
