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

package stroom.jobsystem.server;

import java.io.Serializable;

import stroom.jobsystem.shared.JobNode;

public class DistributedRequiredTask implements Serializable {
    private static final long serialVersionUID = 2651416970893054545L;

    private JobNode jobNode;
    private int requiredTaskCount;

    public DistributedRequiredTask(final JobNode jobNode, final int requiredTaskCount) {
        this.jobNode = jobNode;
        this.requiredTaskCount = requiredTaskCount;
    }

    public JobNode getJobNode() {
        return jobNode;
    }

    public int getRequiredTaskCount() {
        return requiredTaskCount;
    }

    @Override
    public String toString() {
        return requiredTaskCount + " : " + jobNode.toString();
    }
}
