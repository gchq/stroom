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
 *
 */

package stroom.config.global.impl.db;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final class BeanUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeanUtil.class);

    private BeanUtil() {
        // Utility class.
    }

    public static Map<String, Prop> getProperties(final Object object) {
        final Class<?> clazz = object.getClass();
        final Method[] methods = clazz.getDeclaredMethods();
        final Map<String, Prop> propMap = new HashMap<>();
        for (final Method method : methods) {
            final String methodName = method.getName();

            if (methodName.startsWith("is")) {
                // Boolean Getter.

                if (methodName.length() > 2 && method.getParameterTypes().length == 0 && !method.getReturnType().equals(Void.TYPE)) {
                    final String name = getPropertyName(methodName, 2);
                    final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                    prop.getter = method;
                }

            } else if (methodName.startsWith("get")) {
                // Getter.

                if (methodName.length() > 3 && !methodName.equals("getClass") && method.getParameterTypes().length == 0 && !method.getReturnType().equals(Void.TYPE)) {
                    final String name = getPropertyName(methodName, 3);
                    final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                    prop.getter = method;
                }
            } else if (methodName.startsWith("set")) {
                // Setter.

                if (methodName.length() > 3 && method.getParameterTypes().length == 1 && method.getReturnType().equals(Void.TYPE)) {
                    final String name = getPropertyName(methodName, 3);
                    final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                    prop.setter = method;
                }
            }
        }

        return propMap
                .entrySet()
                .stream()
                .filter(e -> {
                    if (e.getValue().getter == null || e.getValue().setter == null) {
                        LOGGER.debug("Invalid property " + e.getKey() + " on " + clazz.getName());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private static String getPropertyName(final String methodName, final int len) {
        final String name = methodName.substring(len);
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    public static class Prop {
        private final String name;
        private final Object object;
        private Method getter;
        private Method setter;

        Prop(final String name, final Object object) {
            this.name = name;
            this.object = object;
        }

        public String getName() {
            return name;
        }

        public Object getObject() {
            return object;
        }

        public Method getGetter() {
            return getter;
        }

        public Method getSetter() {
            return setter;
        }
    }
}
