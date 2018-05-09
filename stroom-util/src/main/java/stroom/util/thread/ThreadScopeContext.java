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

package stroom.util.thread;

import stroom.util.logging.StroomLogger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class to hold the spring thread bound variables.
 */
public class ThreadScopeContext {
    protected static final StroomLogger LOGGER = StroomLogger.getLogger(ThreadScopeContext.class);

    private Map<String, Object> beanMap;
    private Map<String, Runnable> requestDestructionCallback;

    public ThreadScopeContext() {
        beanMap = new HashMap<>();
        requestDestructionCallback = new LinkedHashMap<>(8);
    }

    final Object getMutex() {
        return beanMap;
    }

    final Object get(final String name) {
        return beanMap.get(name);
    }

    public final Object put(final String name, final Object bean) {
        return beanMap.put(name, bean);
    }

    final Object remove(final String name) {
        requestDestructionCallback.remove(name);
        return beanMap.remove(name);
    }

    final void registerDestructionCallback(final String name, final Runnable runnable) {
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
    }
}
