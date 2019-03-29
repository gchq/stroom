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

package stroom.task.shared;

import java.util.Objects;

public class ThreadPoolImpl implements ThreadPool {
    private final String name;
    private final int priority;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int maxQueueSize;

    public ThreadPoolImpl(final String name, final int priority, final int corePoolSize, final int maxPoolSize) {
        this.name = name;
        this.priority = priority;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.maxQueueSize = Integer.MAX_VALUE;
    }

    public ThreadPoolImpl(final String name, final int priority, final int corePoolSize, final int maxPoolSize, final int maxQueueSize) {
        this.name = name;
        this.priority = priority;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.maxQueueSize = maxQueueSize;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public int getCorePoolSize() {
        return corePoolSize;
    }

    @Override
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    @Override
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ThreadPoolImpl that = (ThreadPoolImpl) o;
        return priority == that.priority &&
                corePoolSize == that.corePoolSize &&
                maxPoolSize == that.maxPoolSize &&
                maxQueueSize == that.maxQueueSize &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, priority, corePoolSize, maxPoolSize, maxQueueSize);
    }

    @Override
    public String toString() {
        return name;
    }
}
