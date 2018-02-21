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

import stroom.node.shared.Node;
import stroom.util.shared.VoidResult;

import java.util.Collections;
import java.util.List;

public class MockTaskFactory implements DistributedTaskFactory<MockTask, VoidResult> {
    private int taskCount;

    /**
     * Gets a task if one is available, returns null otherwise.
     *
     * @return A task.
     */
    @Override
    public List<MockTask> fetch(final Node node, final int count) {
        return Collections.singletonList(new MockTask("MockTask " + (taskCount++)));
    }

    @Override
    public void abandon(final Node node, final List<MockTask> tasks) {
    }
}
