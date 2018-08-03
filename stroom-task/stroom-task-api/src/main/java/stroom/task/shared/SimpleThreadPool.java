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

public class SimpleThreadPool implements ThreadPool {
    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = Integer.MAX_VALUE;

    private final int priority;
    private final String name;

    public SimpleThreadPool(final int priority) {
        this("Stroom P" + priority, priority);
    }

    public SimpleThreadPool(final String name, final int priority) {
        this.name = name;
        this.priority = priority;
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
        return CORE_POOL_SIZE;
    }

    @Override
    public int getMaxPoolSize() {
        return MAX_POOL_SIZE;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SimpleThreadPool)) {
            return false;
        }

        final SimpleThreadPool simpleThreadPool = (SimpleThreadPool) obj;
        return simpleThreadPool.name.equals(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
