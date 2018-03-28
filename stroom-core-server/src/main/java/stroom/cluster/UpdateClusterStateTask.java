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

package stroom.cluster;

import stroom.util.shared.VoidResult;
import stroom.task.ServerTask;

public class UpdateClusterStateTask extends ServerTask<VoidResult> {
    private ClusterState clusterState;
    private int delay;
    private boolean testActiveNodes;

    public UpdateClusterStateTask(final ClusterState clusterState, final int delay, final boolean testActiveNodes) {
        this.clusterState = clusterState;
        this.delay = delay;
        this.testActiveNodes = testActiveNodes;
    }

    public ClusterState getClusterState() {
        return clusterState;
    }

    public int getDelay() {
        return delay;
    }

    public boolean isTestActiveNodes() {
        return testActiveNodes;
    }

    @Override
    public String getTaskName() {
        return "Create Cluster State";
    }
}
