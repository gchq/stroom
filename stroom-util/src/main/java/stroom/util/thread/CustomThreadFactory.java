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

package stroom.util.thread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {

    private final String prefix;
    private ThreadGroup threadGroup;
    private int priority = Thread.NORM_PRIORITY;
    private boolean daemon = false;
    private final AtomicInteger threadNo = new AtomicInteger();

    public CustomThreadFactory(final String prefix) {
        this.prefix = prefix;
    }

    public CustomThreadFactory(final String prefix, final ThreadGroup threadGroup, final int priority) {
        this.prefix = prefix;
        this.threadGroup = threadGroup;
        this.priority = priority;
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        final Thread thread = new Thread(threadGroup, runnable, prefix + threadNo.incrementAndGet());
        thread.setPriority(priority);
        thread.setDaemon(daemon);
        return thread;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(final boolean daemon) {
        this.daemon = daemon;
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    public void setThreadGroup(final ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
    }
}
