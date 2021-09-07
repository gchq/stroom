/*
 * Copyright 2017 Crown Copyright
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

package stroom.task.impl;

import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.TaskId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TaskContextImpl implements TaskContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskContextImpl.class);

    private final TaskId taskId;
    private final UserIdentity userIdentity;
    private final String name;
    private final long submitTimeMs = System.currentTimeMillis();
    private final Set<TaskContextImpl> children = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private volatile boolean terminate;
    private volatile Supplier<String> messageSupplier;
    private volatile Thread thread;

    public TaskContextImpl(final TaskId taskId, final String name, final UserIdentity userIdentity) {
        Objects.requireNonNull(taskId, "Task has null id");
        Objects.requireNonNull(name, "Task has null name");
        Objects.requireNonNull(userIdentity, "Task has null user identity: " + name);

        this.taskId = taskId;
        this.userIdentity = userIdentity;
        this.name = name;
    }

    @Override
    public void info(final Supplier<String> messageSupplier) {
        this.messageSupplier = messageSupplier;
    }

    @Override
    public TaskId getTaskId() {
        return taskId;
    }

    synchronized void terminate() {
        this.terminate = true;
        children.forEach(TaskContextImpl::terminate);

        interrupt();
    }

    UserIdentity getUserIdentity() {
        return userIdentity;
    }

    String getUserId() {
        return userIdentity.getId();
    }

    String getSessionId() {
        return userIdentity.getSessionId();
    }

    synchronized void setThread(final Thread thread) {
        final Thread currentThread = this.thread;
        this.thread = thread;

        if (thread == null && currentThread != null) {
            if (terminate) {
                if (currentThread.isInterrupted()) {
                    LOGGER.debug("Clearing interrupted state");
                    final Thread ct = Thread.currentThread();
                    if (currentThread != ct) {
                        LOGGER.error("Unexpected current thread");
                    }

                    if (Thread.interrupted()) {
                        if (currentThread.isInterrupted()) {
                            LOGGER.error("Unable to clear interrupted state");
                        } else {
                            LOGGER.debug("Cleared interrupted state");
                        }

                        throw new TaskTerminatedException();
                    }
                }
            }
        } else if (terminate) {
            interrupt();
        }
    }

    boolean isTerminated() {
        final Thread thread = this.thread;
        if (thread != null) {
            if (thread.isInterrupted()) {
                // Make sure the thread hasn't been reassigned.
                if (thread == this.thread) {
                    return true;
                }
            }
        }

        return terminate;
    }

    synchronized void interrupt() {
        final Thread thread = this.thread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @SuppressWarnings("deprecation")
    synchronized void kill() {
        final Thread thread = this.thread;
        if (thread != null) {
            thread.stop();
        }
    }

    String getThreadName() {
        final Thread thread = this.thread;
        if (thread != null) {
            return thread.getName();
        }
        return null;
    }

    long getSubmitTimeMs() {
        return submitTimeMs;
    }

    String getName() {
        String name = this.name;
        if (terminate) {
            name = "<<terminated>> " + name;
        }

        return name;
    }


    String getInfo() {
        final Supplier<String> messageSupplier = this.messageSupplier;
        if (messageSupplier != null) {
            return messageSupplier.get();
        }
        return "";
    }

    synchronized void addChild(final TaskContextImpl taskState) {
        children.add(taskState);
        if (terminate) {
            taskState.terminate();
        }
    }

    void removeChild(final TaskContextImpl taskState) {
        children.remove(taskState);
    }

    Set<TaskContextImpl> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return getInfo();
    }
}
