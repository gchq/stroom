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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

public class ThreadScope implements Scope {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadScope.class);

    @SuppressWarnings({"rawtypes"})
    @Override
    public Object get(final String name, final ObjectFactory factory) {
        Object result = null;

        try {
            final ThreadScopeContext context = ThreadScopeContextHolder.getContext();
            if (context != null) {
                result = context.get(name);
                if (result == null) {
                    result = factory.getObject();
                    context.put(name, result);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        return result;
    }

    @Override
    public Object remove(final String name) {
        Object result = null;

        try {
            final ThreadScopeContext context = ThreadScopeContextHolder.getContext();
            if (context != null) {
                result = context.remove(name);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        return result;
    }

    @Override
    public void registerDestructionCallback(final String name, final Runnable runnable) {
        try {
            final ThreadScopeContext context = ThreadScopeContextHolder.getContext();
            if (context != null) {
                context.registerDestructionCallback(name, runnable);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Object resolveContextualObject(final String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return null;
    }
}
