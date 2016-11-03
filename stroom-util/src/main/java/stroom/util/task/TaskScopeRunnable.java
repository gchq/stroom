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

package stroom.util.task;

import stroom.util.shared.Task;

public abstract class TaskScopeRunnable implements Runnable {
    private final Task<?> task;

    public TaskScopeRunnable(final Task<?> task) {
        this.task = task;
    }

    @Override
    public final void run() {
        TaskScopeContextHolder.addContext(task);
        try {
            exec();
        } finally {
            TaskScopeContextHolder.removeContext();
        }
    }

    protected abstract void exec();
}
