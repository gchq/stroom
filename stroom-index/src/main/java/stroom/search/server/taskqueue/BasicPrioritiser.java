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

package stroom.search.server.taskqueue;

import java.util.List;

import stroom.util.shared.Task;

public class BasicPrioritiser implements Prioritiser {
    @Override
    public Task<?> select(final List<Task<?>> list) {
        Task<?> task = null;
        while (task == null && list.size() > 0) {
            task = list.remove(0);
            // Ignore terminated jobs.
            if (task.isTerminated()) {
                task = null;
            }
        }
        return task;
    }
}
