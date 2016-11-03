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

package stroom.spring;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

/**
 * For use in testing where there aren't web-only scopes. E.g. session and
 * request.
 */
public class DummyScope implements Scope {
    private final Map<String, Object> beanMap = new HashMap<>();

    @Override
    public Object get(final String name, final ObjectFactory<?> objectFactory) {
        Object bean = beanMap.get(name);
        if (bean == null) {
            bean = objectFactory.getObject();
            beanMap.put(name, bean);
        }
        return bean;
    }

    @Override
    public Object remove(final String name) {
        return beanMap.remove(name);
    }

    @Override
    public void registerDestructionCallback(final String name, final Runnable callback) {
        // Don't need to implement this.
    }

    @Override
    public Object resolveContextualObject(final String key) {
        // Don't need to implement this.
        return null;
    }

    @Override
    public String getConversationId() {
        // Don't need to implement this.
        return null;
    }
}
