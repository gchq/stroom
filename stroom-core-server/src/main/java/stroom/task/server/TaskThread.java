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

package stroom.task.server;

import stroom.util.shared.Monitor;
import stroom.util.shared.Task;
import stroom.util.task.HasMonitor;

public class TaskThread<R> {
    private final Task<?> task;
    private volatile Thread thread;
    private final long submitTimeMs = System.currentTimeMillis();

    public TaskThread(final Task<?> task) {
        this.task = task;
    }

    public Task<?> getTask() {
        return task;
    }

    public synchronized void setThread(final Thread thread) {
        this.thread = thread;
    }

    public void terminate() {
        task.terminate();
    }

    @SuppressWarnings("deprecation")
    synchronized void kill() {
        final Thread thread = this.thread;
        if (thread != null) {
            thread.stop();
        }
    }

    String getThreadName() {
        final Thread threadCopy = thread;
        if (threadCopy != null) {
            return threadCopy.getName();
        }
        return null;
    }

    long getSubmitTimeMs() {
        return submitTimeMs;
    }

    public String getName() {
        String name = null;
        if (task instanceof HasMonitor) {
            final Monitor monitor = ((HasMonitor) task).getMonitor();
            if (monitor != null) {
                name = monitor.getName();
            }
        }
        if (name == null) {
            name = task.getTaskName();
        }
        if (task.isTerminated()) {
            name = "<<terminated>> " + name;
        }

        return name;
    }

    public String getInfo() {
        String info = null;
        if (task instanceof HasMonitor) {
            final Monitor monitor = ((HasMonitor) task).getMonitor();
            if (monitor != null) {
                info = monitor.getInfo();
            }
        }

        return info;
    }
}
