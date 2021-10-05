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


import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.CaseFormat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class PropertyUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PropertyUtil.class);

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

        // Only care about stroom pojos
        final Map<String, Prop> propMap = getProperties(object);

        propMap.values().stream()
                .filter(propFilter)
                .forEach(prop -> {
                    LOGGER.trace("{}{}#{}",
                            indent,
                            object.getClass().getSimpleName(),
                            prop.getName());

                    // process the prop
                    propConsumer.accept(prop);
                    final Object childValue = prop.getValueFromConfigObject();
                    if (childValue == null) {
                        LOGGER.trace("{}Null value", indent + "  ");
                    } else {
                        // descend into the prop, which may or may not have its own props
                        walkObjectTree(
                                prop.getValueFromConfigObject(),
                                propFilter,
                                propConsumer,
                                indent + "  ");
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
        final Map<String, Prop> propMap = new HashMap<>();
        final Class<?> clazz = object.getClass();

        // Scan all the fields and methods on the object to build up a map of possible props
        getPropsFromFields(object, propMap);
        getPropsFromMethods(object, propMap);

        // Now filter out all the prop objects that are not pojo props with getter+setter
        return propMap
                .entrySet()
                .stream()
                .filter(e -> {
                    final String name = e.getKey();
                    final Prop prop = e.getValue();
                    final boolean hasJsonPropertyAnno = prop.hasAnnotation(JsonProperty.class);

                    LOGGER.trace(() -> "Property " + name + " on " + clazz.getName() +
                            " hasJsonProperty: " + hasJsonPropertyAnno +
                            " hasSetter:" + prop.hasSetter());

                    if (prop.hasGetter() && !prop.hasSetter() && hasJsonPropertyAnno) {
                        // Need to check for the jsonProperty anno to ignore props of non-pojo classes,
                        // e.g when it recurses into TreeMap or something like that.
                        throw new RuntimeException("Property " + name + " on " + clazz.getName() +
                                " has no setter. Either add a setter or remove @JsonProperty from its " +
                                "getter/field");
                    } else if (prop.getter == null || prop.setter == null) {
                        // could be a static field for internal use
                        LOGGER.trace(() -> "Property " + name + " on " + clazz.getName() +
                                " has no getter or setter, ignoring.");
                        return false;
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    public static Class<?> getDataType(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return clazz;
        }

        if (clazz.isArray()) {
            return getDataType(clazz.getComponentType());
        }

        return clazz;
    }

    public static Class<?> getDataType(Type type) {
        if (type instanceof Class) {
            return getDataType((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            return getDataType(pt.getRawType());
        } else {
            throw new RuntimeException(LogUtil.message("Unexpected type of type {}",
                    type.getClass().getName()));
        }
    }

    public static List<Type> getGenericTypes(Type type) {
        if (type instanceof Class) {
            return Collections.emptyList();
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type[] specificTypes = pt.getActualTypeArguments();

            return Arrays.asList(specificTypes);
        } else {
            throw new RuntimeException(LogUtil.message("Unexpected type of type {}",
                    type.getClass().getName()));
        }
    }

    private static void getPropsFromFields(final Object object, final Map<String, Prop> propMap) {
        final Class<?> clazz = object.getClass();

        for (final Field declaredField : clazz.getDeclaredFields()) {
            if (declaredField.getDeclaredAnnotation(JsonIgnore.class) == null) {
                final String name = declaredField.getName();
                final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                prop.addFieldAnnotations(declaredField.getDeclaredAnnotations());
            }
        }
    }

    private static void getPropsFromMethods(final Object object, final Map<String, Prop> propMap) {
        final Class<?> clazz = object.getClass();
        // Using getMethods rather than getDeclaredMethods means we have to make the methods public
        // but it does allow us to see inherited methods, e.g. on CommonDbConfig
        final Method[] methods = clazz.getMethods();
        final List<String> propsWithGetter = new ArrayList<>();
        final List<String> propsWithSetter = new ArrayList<>();

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
                        propsWithGetter.add(name);
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
                        propsWithGetter.add(name);
                    }
                } else if (methodName.startsWith("set")) {
                    // Setter.

                    if (methodName.length() > 3
                            && method.getParameterTypes().length == 1
                            && method.getReturnType().equals(Void.TYPE)) {
                        final String name = getPropertyName(methodName, 3);
                        final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                        prop.setSetter(method);
                        propsWithSetter.add(name);
                    }
                }
            }
        }
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

        private final Map<Class<? extends Annotation>, Annotation> fieldAnnotationsMap = new HashMap<>();
        private final Map<Class<? extends Annotation>, Annotation> getterAnnotationsMap = new HashMap<>();

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

        public boolean hasGetter() {
            return getter != null;
        }

        void setGetter(final Method getter) {
            this.getter = Objects.requireNonNull(getter);

            for (final Annotation annotation : getter.getDeclaredAnnotations()) {
                this.getterAnnotationsMap.put(annotation.annotationType(), annotation);
            }
        }

        public Method getSetter() {
            return setter;
        }

        public boolean hasSetter() {
            return setter != null;
        }

        void setSetter(final Method setter) {
            this.setter = Objects.requireNonNull(setter);
        }

        void addFieldAnnotations(final Annotation... annotations) {
            for (final Annotation annotation : Objects.requireNonNull(annotations)) {
                this.fieldAnnotationsMap.put(annotation.annotationType(), annotation);
            }
        }

        /**
         * @return True if the field has the passed {@link Annotation} class.
         */
        public boolean hasFieldAnnotation(final Class<? extends Annotation> clazz) {
            Objects.requireNonNull(clazz);
            return fieldAnnotationsMap.containsKey(clazz);
        }

        /**
         * @return True if the getter has the passed {@link Annotation} class.
         */
        public boolean hasGetterAnnotation(final Class<? extends Annotation> clazz) {
            Objects.requireNonNull(clazz);
            return getterAnnotationsMap.containsKey(clazz);
        }

        /**
         * @return True if either the field or getter have the passed {@link Annotation} class.
         */
        public boolean hasAnnotation(final Class<? extends Annotation> clazz) {
            Objects.requireNonNull(clazz);
            return fieldAnnotationsMap.containsKey(clazz) || getterAnnotationsMap.containsKey(clazz);
        }

        public <T extends Annotation> Optional<T> getFieldAnnotation(final Class<T> clazz) {
            Objects.requireNonNull(clazz);
            return Optional.ofNullable(fieldAnnotationsMap.get(clazz))
                    .map(clazz::cast);
        }

        public <T extends Annotation> Optional<T> getGetterAnnotation(final Class<T> clazz) {
            Objects.requireNonNull(clazz);
            return Optional.ofNullable(getterAnnotationsMap.get(clazz))
                    .map(clazz::cast);
        }

        /**
         * @return The {@link Annotation} if it is found on the field or getter, in that order.
         */
        public <T extends Annotation> Optional<T> getAnnotation(final Class<T> clazz) {
            Objects.requireNonNull(clazz);
            return Optional.ofNullable(fieldAnnotationsMap.get(clazz))
                    .or(() -> Optional.ofNullable(getterAnnotationsMap.get(clazz)))
                    .map(clazz::cast);
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
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
