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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to hold the spring task bound variables.
 */
public class TaskScopeContext {
    private final Map<String, Object> beanMap;

    private final Map<String, Runnable> requestDestructionCallback;

    private final TaskScopeContext parent;
    private final Task<?> task;

    public TaskScopeContext(final TaskScopeContext parent, final Task<?> task) {
        this.parent = parent;
        this.task = task;
        this.beanMap = new ConcurrentHashMap<>();
        this.requestDestructionCallback = new ConcurrentHashMap<>();
    }

    public Task<?> getTask() {
        return task;
    }

    final Object getMutex() {
        return beanMap;
    }

    final Object get(final String name) {
        return beanMap.get(name);
    }

    final Object put(final String name, final Object bean) {
        return beanMap.put(name, bean);
    }

    final Object remove(final String name) {
        requestDestructionCallback.remove(name);
        return beanMap.remove(name);
    }

    final void registerDestructionCallback(final String name, final Runnable runnable) {
        requestDestructionCallback.put(name, runnable);
    }

    public TaskScopeContext getParent() {
        return parent;
    }

    final void clear() {
        for (final String key : requestDestructionCallback.keySet()) {
            requestDestructionCallback.get(key).run();
        }

        requestDestructionCallback.clear();
        beanMap.clear();
    }
}
