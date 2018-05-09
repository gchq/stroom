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

import stroom.util.logging.StroomLogger;
import stroom.util.shared.Task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to hold the spring task bound variables.
 */
public class TaskScopeContext {
    public static final StroomLogger LOGGER = StroomLogger.getLogger(TaskScopeContext.class);

    private Map<String, Object> beanMap;
    private Map<String, Runnable> requestDestructionCallback;
    private Task<?> task;

    TaskScopeContext(final Task<?> task) {
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
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("put called with name %s and bean %s %s",
                    name, System.identityHashCode(bean), bean.getClass().getName());
        }
        return beanMap.put(name, bean);
    }

    final Object remove(final String name) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("remove called with name %s", name);
        }
        requestDestructionCallback.remove(name);
        return beanMap.remove(name);
    }

    final void registerDestructionCallback(final String name, final Runnable runnable) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("registerDestructionCallback called with name %s and runnable", name, runnable);
        }
        requestDestructionCallback.put(name, runnable);
    }

    final void clear() {
        for (final Runnable runnable : requestDestructionCallback.values()) {
            runnable.run();
        }

        requestDestructionCallback.clear();
        requestDestructionCallback = null;

        beanMap.clear();
        beanMap = null;

        task = null;
    }
}
