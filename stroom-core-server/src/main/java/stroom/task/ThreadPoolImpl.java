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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.task.shared.ThreadPool;

public class ThreadPoolImpl implements ThreadPool {
    private final String name;
    private final int priority;
    private final int corePoolSize;
    private final int maxPoolSize;

    public ThreadPoolImpl(final String name, final int priority, final int corePoolSize, final int maxPoolSize) {
        this.name = name;
        this.priority = priority;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
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
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(name);
        builder.append(priority);
        builder.append(corePoolSize);
        builder.append(maxPoolSize);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ThreadPoolImpl)) {
            return false;
        }

        final ThreadPoolImpl threadPoolImpl = (ThreadPoolImpl) obj;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(threadPoolImpl.name, name);
        builder.append(threadPoolImpl.priority, priority);
        builder.append(threadPoolImpl.corePoolSize, corePoolSize);
        builder.append(threadPoolImpl.maxPoolSize, maxPoolSize);
        return builder.isEquals();
    }

    @Override
    public String toString() {
        return name;
    }
}
