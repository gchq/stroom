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

package stroom.config.global.impl;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.CaseFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final class PropertyUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyUtil.class);

    private PropertyUtil() {
        // Utility class.
    }

    public static Map<String, Prop> getProperties(final Object object) {
        final Class<?> clazz = object.getClass();
        final Method[] methods = clazz.getDeclaredMethods();
        final Map<String, Prop> propMap = new HashMap<>();
        for (final Method method : methods) {
            final String methodName = method.getName();

            if (method.getDeclaredAnnotation(JsonIgnore.class) == null) {
                if (methodName.startsWith("is")) {
                    // Boolean Getter.

                    if (methodName.length() > 2
                            && method.getParameterTypes().length == 0
                            && !method.getReturnType().equals(Void.TYPE)) {
                        final String name = getPropertyName(methodName, 2);
                        final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                        prop.getter = method;
                    }

                } else if (methodName.startsWith("get")) {
                    // Getter.

                    if (methodName.length() > 3
                            && !methodName.equals("getClass")
                            && method.getParameterTypes().length == 0
                            && !method.getReturnType().equals(Void.TYPE)) {
                        final String name = getPropertyName(methodName, 3);
                        final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                        prop.getter = method;
                    }
                } else if (methodName.startsWith("set")) {
                    // Setter.

                    if (methodName.length() > 3
                            && method.getParameterTypes().length == 1
                            && method.getReturnType().equals(Void.TYPE)) {
                        final String name = getPropertyName(methodName, 3);
                        final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                        prop.setter = method;
                    }
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
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name);
    }

    /**
     * Class to define a config property in the config object tree
     */
    public static class Prop {
        // The unqualified name of the property, e.g. 'nodeName'
        private final String name;
        // The config object that the property exists in
        private final Object parentObject;
        // The getter method to get the value of the property
        private Method getter;
        // The getter method to set the value of the property
        private Method setter;

        Prop(final String name, final Object parentObject) {
            this.name = name;
            this.parentObject = parentObject;
        }

        public String getName() {
            return name;
        }

        public Object getParentObject() {
            return parentObject;
        }

        public Method getGetter() {
            return getter;
        }

        public Method getSetter() {
            return setter;
        }

        public Object getValueFromConfigObject() throws InvocationTargetException, IllegalAccessException {
            return getter.invoke(parentObject);
        }

        public void setValueOnConfigObject(final Object value) throws InvocationTargetException, IllegalAccessException {
            setter.invoke(parentObject, value);
        }

        public Type getValueType() {
            return setter.getGenericParameterTypes()[0];
        }

        public Class<?> getValueClass() {
            return getter.getReturnType();
        }

        @Override
        public String toString() {
            return "Prop{" +
                    "name='" + name + '\'' +
                    ", parentObject=" + parentObject +
                    '}';
        }
    }
}
