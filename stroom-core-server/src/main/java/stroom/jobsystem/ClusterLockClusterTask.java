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

package stroom.jobsystem;

import stroom.task.cluster.ClusterTask;
import stroom.util.shared.SharedBoolean;
import stroom.task.shared.Task;

public class ClusterLockClusterTask extends ClusterTask<SharedBoolean> {
    private static final long serialVersionUID = -2025366263627949409L;

    private final Task<?> parentTask;
    private final ClusterLockKey key;
    private final ClusterLockStyle lockStyle;

    public ClusterLockClusterTask(final ClusterLockTask parent) {
        super(parent.getUserToken(), "ClusterLockClusterTask");
        this.parentTask = parent;
        this.key = parent.getKey();
        this.lockStyle = parent.getLockStyle();
    }

    @Override
    public Task<?> getParentTask() {
        return parentTask;
    }

    public ClusterLockKey getKey() {
        return key;
    }

    public ClusterLockStyle getLockStyle() {
        return lockStyle;
    }

    @Override
    public String toString() {
        return key.toString() + " lockStyle=" + lockStyle;
    }
}
