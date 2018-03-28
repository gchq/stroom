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

package stroom.task;

import stroom.util.shared.Task;

class TaskThread<R> {
    private final Task<?> task;
    private final Monitor monitor;
    private final long submitTimeMs = System.currentTimeMillis();
    private volatile Thread thread;

    TaskThread(final Task<?> task, final Monitor monitor) {
        this.task = task;
        this.monitor = monitor;
    }

    public Task<?> getTask() {
        return task;
    }

    Monitor getMonitor() {
        return monitor;
    }

    public synchronized void setThread(final Thread thread) {
        this.thread = thread;
    }

    public void terminate() {
        monitor.terminate();
    }

    public boolean isTerminated() {
        return monitor.isTerminated();
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
        String name = monitor.getName();
        if (name == null) {
            name = task.getTaskName();
        }
        if (monitor.isTerminated()) {
            name = "<<terminated>> " + name;
        }

        return name;
    }

    public void setName(final String name) {
        monitor.setName(name);
    }

    public String getInfo() {
        return monitor.getInfo();
    }

    public void info(final Object... args) {
        monitor.info(args);
    }
}
