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

package stroom.util.config;


import stroom.util.logging.LogUtil;

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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class PropertyUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyUtil.class);

    private PropertyUtil() {
        // Utility class.
    }

    /**
     * Walks the public properties of the supplied object, passing each property to the
     * property consumer if it passes the filter test.
     */
    public static void walkObjectTree(final Object object,
                                      final Predicate<Prop> propFilter,
                                      final Consumer<Prop> propConsumer) {
        walkObjectTree(object, propFilter, propConsumer, "");
    }

    private static void walkObjectTree(final Object object,
                                       final Predicate<Prop> propFilter,
                                       final Consumer<Prop> propConsumer,
                                       final String indent) {

        final Map<String, Prop> propMap = getProperties(object);

        propMap.values().stream()
                .filter(propFilter)
                .forEach(prop -> {
                    LOGGER.trace("{}{}#{}", indent, object.getClass().getSimpleName(), prop.getName());

                    // process the prop
                    propConsumer.accept(prop);
                    Object childValue = prop.getValueFromConfigObject();
                    if (childValue == null) {
                        LOGGER.trace("{}Null value", indent + "  ");
                    } else {
                        // descend into the prop, which may or may not have its own props
                        walkObjectTree(prop.getValueFromConfigObject(), propFilter, propConsumer, indent + "  ");
                    }
                });
    }

    /**
     * Builds a map of property names to a {@link Prop} object that provides access to the getter/setter.
     * Only includes public properties, not package private
     */
    public static Map<String, Prop> getProperties(final Object object) {
        Objects.requireNonNull(object);
        LOGGER.trace("getProperties called for {}", object);
        final Class<?> clazz = object.getClass();
        // Using getMethods rather than getDeclaredMethods means we have to make the methods public
        // but it does allow us to see inherited methods, e.g. on CommonDbConfig
        final Method[] methods = clazz.getMethods();
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
                        prop.setGetter(method);
                    }

                } else if (methodName.startsWith("get")) {
                    // Getter.

                    if (methodName.length() > 3
                            && !methodName.equals("getClass")
                            && method.getParameterTypes().length == 0
                            && !method.getReturnType().equals(Void.TYPE)) {
                        final String name = getPropertyName(methodName, 3);
                        final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                        prop.setGetter(method);
                    }
                } else if (methodName.startsWith("set")) {
                    // Setter.

                    if (methodName.length() > 3
                            && method.getParameterTypes().length == 1
                            && method.getReturnType().equals(Void.TYPE)) {
                        final String name = getPropertyName(methodName, 3);
                        final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                        prop.setSetter(method);
                    }
                }
            }
        }

        return propMap
                .entrySet()
                .stream()
                .filter(e -> {
                    if (e.getValue().getter == null || e.getValue().setter == null) {
                        LOGGER.trace("Invalid property " + e.getKey() + " on " + clazz.getName());
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
        // The unqualified name of the property, e.g. 'node'
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

        void setGetter(final Method getter) {
            this.getter = getter;
        }

        public Method getSetter() {
            return setter;
        }

        void setSetter(final Method setter) {
            this.setter = setter;
        }

        public Object getValueFromConfigObject() {
            try {
                return getter.invoke(parentObject);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(LogUtil.message("Error getting value for prop {}", name), e);
            }
        }

        public void setValueOnConfigObject(final Object value) {
            try {
                setter.invoke(parentObject, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(LogUtil.message("Error setting value for prop {}", name), e);
            }
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

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Prop prop = (Prop) o;
            return Objects.equals(name, prop.name) &&
                Objects.equals(parentObject, prop.parentObject) &&
                Objects.equals(getter, prop.getter) &&
                Objects.equals(setter, prop.setter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, parentObject, getter, setter);
        }
    }

}
