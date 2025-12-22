/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.config;


import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.google.common.base.CaseFormat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
//        System.out.println(object.getClass().getSimpleName() + " => " + String.join(", ", propMap.keySet()));

        propMap.values().stream()
                .filter(propFilter)
                .forEach(prop -> {
                    LOGGER.trace("{}{}#{}",
                            indent,
                            object.getClass().getSimpleName(),
                            prop.getName());

                    // process the prop
                    propConsumer.accept(prop);
                    final Object childValue;
                    try {
                        childValue = prop.getValueFromConfigObject();
                    } catch (final Exception e) {
                        LOGGER.error("Error getting value for prop {}, object {}", prop, object, e);
                        throw e;
                    }
                    if (childValue == null) {
                        LOGGER.trace("{}Null value", indent + "  ");
                    } else if (childValue instanceof Enum<?>) {
                        // We don't want to recurse into enums
                        LOGGER.trace(() -> LogUtil.message("{}Ignoring Enum value of type {}",
                                indent + "  ",
                                childValue.getClass().getSimpleName()));
                    } else if (childValue instanceof Collection<?>) {
                        // We don't want to recurse into collection types
                        LOGGER.trace(() -> LogUtil.message("{}Ignoring Collection value of type {}",
                                indent + "  ",
                                childValue.getClass().getSimpleName()));
                    } else {
                        // descend into the prop, which may or may not have its own props
                        walkObjectTree(
                                childValue,
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
//    public static Map<String, Prop> getProperties(final Object object) {
//        Objects.requireNonNull(object);
//        LOGGER.trace("getProperties called for {}", object);
//        final Map<String, Prop> propMap = new HashMap<>();
//        final Class<?> clazz = object.getClass();
//
//        // Scan all the fields and methods on the object to build up a map of possible props
//        getPropsFromFields(object, propMap);
//        getPropsFromMethods(object, propMap);
//
//        // Now filter out all the prop objects that are not pojo props with getter+setter
//        return propMap
//                .entrySet()
//                .stream()
//                .filter(e -> {
//                    final String name = e.getKey();
//                    final Prop prop = e.getValue();
//                    final boolean hasJsonPropertyAnno = prop.hasAnnotation(JsonProperty.class);
//
//                    LOGGER.trace(() -> "Property " + name + " on " + clazz.getName() +
//                            " hasJsonProperty: " + hasJsonPropertyAnno +
//                            " hasSetter:" + prop.hasSetter());
//
//                    if (prop.hasGetter() && !prop.hasSetter() && hasJsonPropertyAnno) {
//                        // Need to check for the jsonProperty anno to ignore props of non-pojo classes,
//                        // e.g when it recurses into TreeMap or something like that.
//                        throw new RuntimeException("Property " + name + " on " + clazz.getName() +
//                                " has no setter. Either add a setter or remove @JsonProperty from its " +
//                                "getter/field");
//                    } else if (prop.getter == null || prop.setter == null) {
//                        // could be a static field for internal use
//                        LOGGER.trace(() -> "Property " + name + " on " + clazz.getName() +
//                                " has no getter or setter, ignoring.");
//                        return false;
//                    } else {
//                        return true;
//                    }
//                })
//                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
//    }
    public static Map<String, Prop> getProperties(final Object object) {
        final ObjectMapper objectMapper = JsonUtil.getMapper();
        ;
        return getProperties(objectMapper, object);
    }

    public static <T> Map<String, Prop> getProperties(final ObjectMapper objectMapper, final T object) {
        final Class<T> clazz = (Class<T>) object.getClass();

        final JavaType userType = objectMapper.getTypeFactory().constructType(object.getClass());
        final BeanDescription beanDescription =
                objectMapper.getSerializationConfig().introspect(userType);

        final List<BeanPropertyDefinition> beanPropDefs = beanDescription.findProperties();
        // enum types seem to have a declaringClass prop that results in a stack overflow
        final Map<String, Prop> propMap = beanPropDefs.stream()
                .filter(propDef ->
                        !((object instanceof Enum<?>) && propDef.getName().equals("declaringClass")))
                .filter(propDef ->
                        !((object instanceof List<?>)))
                .map(propDef -> {
                    final Prop prop = new Prop(propDef.getName(), object);
                    if (propDef.getField() != null) {
                        propDef.getField()
                                .getAllAnnotations()
                                .annotations()
                                .forEach(prop::addFieldAnnotation);
                    }
                    if (propDef.hasGetter()) {
                        prop.setGetter(propDef.getGetter().getAnnotated());
                    } else {
                        throw new RuntimeException("Property " + propDef.getName() + " on " + clazz.getName() +
                                                   " has no getter. Do the constructor and field/getter " +
                                                   "@JsonProperty names match up?");
                    }
                    if (propDef.hasSetter()) {
                        prop.setSetter(propDef.getSetter().getAnnotated());
                    }
                    return prop;
                })
                .collect(Collectors.toMap(Prop::getName, Function.identity()));

        return propMap;
    }

    /**
     * Builds a map of property name to {@link Prop} object that provides access to the getter/setter.
     * Only includes public properties, not package private
     */
    public static <T> ObjectInfo<T> getObjectInfo(final ObjectMapper objectMapper,
                                                  final String name,
                                                  final T object) {
        Objects.requireNonNull(object);
        LOGGER.trace("getProperties called for {}", object);
        final Map<String, Prop> propMap = new HashMap<>();

        final Class<T> clazz = (Class<T>) object.getClass();

        final JavaType userType = objectMapper.getTypeFactory().constructType(object.getClass());
        final BeanDescription beanDescription =
                objectMapper.getSerializationConfig().introspect(userType);

        final List<BeanPropertyDefinition> props = beanDescription.findProperties();
        final Set<String> propNames = props.stream()
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toSet());

        // Now filter out all the prop objects that are not pojo props with getter+setter
        final Map<String, Prop> propertyMap = props
                .stream()
                .map(propDef -> {
                    final String propName = propDef.getName();

                    final Prop prop = new Prop(propName, object);
                    if (propDef.getField() != null) {
                        propDef.getField()
                                .getAllAnnotations()
                                .annotations()
                                .forEach(prop::addFieldAnnotation);
                    }
                    if (propDef.hasGetter()) {
                        prop.setGetter(propDef.getGetter().getAnnotated());
                    }
                    if (propDef.hasSetter()) {
                        prop.setSetter(propDef.getSetter().getAnnotated());
                    }

                    LOGGER.trace(() -> LogUtil.message(
                            "name: {}, hasGetter: {}, hasSetter: {}, field annotations: {}, getter annotations: {}",
                            prop.name,
                            prop.getter != null,
                            prop.setter != null,
                            prop.getFieldAnnotations(),
                            prop.getGetterAnnotations()));

                    return prop;
                })
                .collect(Collectors.toMap(Prop::getName, Function.identity()));

        final Optional<Constructor<T>> optJsonCreatorConstructor = getJsonCreatorConstructor(beanDescription);
        final List<String> constructorArgNames = optJsonCreatorConstructor
                .map(PropertyUtil::getConstructorArgNames)
                .orElseGet(Collections::emptyList);

        constructorArgNames.forEach(argName -> {
            if (!propNames.contains(argName)) {
                throw new RuntimeException(LogUtil.message(
                        "No matching property found for constructor property {} on {}", argName, object));
            }
        });

        return new ObjectInfo<>(
                name,
                clazz,
                propertyMap,
                constructorArgNames,
                optJsonCreatorConstructor.orElse(null));
    }

    /**
     * @return otherValue if it is non-null or copyNulls is true, else thisValue
     */
    public static <T> T mergeValues(final T thisValue,
                                    final T otherValue,
                                    final T defaultValue,
                                    final boolean copyNulls,
                                    final boolean copyDefaults) {
        if (otherValue != null || copyNulls) {
            if (!Objects.equals(defaultValue, otherValue) || copyDefaults) {
                return otherValue;
            } else {
                return thisValue;
            }
        } else {
            return thisValue;
        }
    }

    public static <T> T copyObject(final T source) {
        final ObjectMapper objectMapper = JsonUtil.getMapper();
        final TokenBuffer tb = new TokenBuffer(objectMapper, false); // or one of factory methods
        try {
            objectMapper.writeValue(tb, source);
            return (T) objectMapper.readValue(tb.asParser(), source.getClass());
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error copying object {}: {}", source, e.getMessage()), e);
        }
    }

    private static <T> Optional<Constructor<T>> getJsonCreatorConstructor(final Class<T> clazz) {
        final List<Constructor<T>> jsonCreatorConstructors = Arrays.stream(clazz.getConstructors())
                .filter(constructor ->
                        constructor.isAnnotationPresent(JsonCreator.class))
                .map(constructor -> (Constructor<T>) constructor)
                .collect(Collectors.toList());

        final Optional<Constructor<T>> optConstructor;
        if (!jsonCreatorConstructors.isEmpty()) {
            if (jsonCreatorConstructors.size() > 1) {
                LOGGER.warn("Found multiple @JsonCreator annotations on {}. Using first one.", clazz.getName());
            }
            final Constructor<T> constructor = jsonCreatorConstructors.get(0);

            optConstructor = Optional.of(constructor);
        } else {
            optConstructor = Optional.empty();
        }
        return optConstructor;
    }

    private static <T> Optional<Constructor<T>> getJsonCreatorConstructor(final BeanDescription beanDescription) {
        final List<Constructor<T>> jsonCreatorConstructors = beanDescription.getConstructors()
                .stream()
                .map(constructor -> (Constructor<T>) constructor.getAnnotated())
                .filter(constructor ->
                        constructor.isAnnotationPresent(JsonCreator.class))
                .collect(Collectors.toList());

        final Optional<Constructor<T>> optConstructor;
        if (!jsonCreatorConstructors.isEmpty()) {
            if (jsonCreatorConstructors.size() > 1) {
                LOGGER.warn("Found multiple @JsonCreator annotations on {}. Using first one.",
                        beanDescription.getBeanClass().getName());
            }
            final Constructor<T> constructor = jsonCreatorConstructors.get(0);

            optConstructor = Optional.of(constructor);
        } else {
            optConstructor = Optional.empty();
        }
        return optConstructor;
    }

    private static <T> List<String> getConstructorArgNames(final Constructor<T> constructor) {
        return Arrays.stream(constructor.getParameters())
                .map(parameter -> {
                    if (parameter.isAnnotationPresent(JsonProperty.class)) {
                        return parameter.getAnnotation(JsonProperty.class).value();
                    } else {
                        LOGGER.warn("No @JsonProperty annotation on {} {}", constructor, parameter.getName());
                        return parameter.getName();
                    }
                })
                .collect(Collectors.toList());
    }

    public static Class<?> getDataType(final Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return clazz;
        }

        if (clazz.isArray()) {
            return getDataType(clazz.getComponentType());
        }

        return clazz;
    }

    public static Class<?> getDataType(final Type type) {
        if (type instanceof Class) {
            return getDataType((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) type;
            return getDataType(pt.getRawType());
        } else {
            throw new RuntimeException(LogUtil.message("Unexpected type of type {}",
                    type.getClass().getName()));
        }
    }

    public static List<Type> getGenericTypes(final Type type) {
        if (type instanceof Class) {
            return Collections.emptyList();
        } else if (type instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) type;
            final Type[] specificTypes = pt.getActualTypeArguments();

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
//                final String name = getName(declaredField, declaredField::getName);
                final String name = declaredField.getName();
                final Prop prop = propMap.computeIfAbsent(name, k -> new Prop(name, object));
                prop.addFieldAnnotations(declaredField.getDeclaredAnnotations());
            }
        }
    }

    private static String getName(final AnnotatedElement annotatedElement,
                                  final Supplier<String> defaultNameSupplier) {
        final JsonProperty jsonPropertyAnno = annotatedElement.getAnnotation(JsonProperty.class);
        if (jsonPropertyAnno == null) {
            return defaultNameSupplier.get();
        } else {
            final String jsonPropertyValue = jsonPropertyAnno.value();
            return jsonPropertyValue.isEmpty()
                    ? defaultNameSupplier.get()
                    : jsonPropertyValue;
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
//                        final String name = getName(method, () ->
//                                getPropertyName(methodName, 2));
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
//                        final String name = getName(method, () ->
//                                getPropertyName(methodName, 3));
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
//                        final String name = getName(method, () -> getPropertyName(methodName, 3));
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


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class ObjectInfo<T> {

        // The unqualified name of the property branch, e.g. 'node'
        private final String name;

        private final Class<T> objectClass;

        private final Map<String, Prop> propertyMap;

        // The unqualified property names in the order they appear in the @JsonCreator constructor
        // if there is one.
        private final List<String> constructorArgList;

        private final Constructor<T> constructor;

        public ObjectInfo(final String name,
                          final Class<T> objectClass,
                          final Map<String, Prop> propertyMap,
                          final List<String> constructorArgList,
                          final Constructor<T> constructor) {
            this.name = name;
            this.objectClass = objectClass;
            this.propertyMap = propertyMap;
            this.constructorArgList = constructorArgList;
            this.constructor = constructor;
        }

        public Class<T> getObjectClass() {
            return objectClass;
        }

        public Type getObjectType() {
            return objectClass;
        }

        public String getName() {
            return name;
        }

        public Map<String, Prop> getPropertyMap() {
            return propertyMap;
        }

        public List<String> getConstructorArgList() {
            return constructorArgList;
        }

        public Constructor<T> getConstructor() {
            return constructor;
        }

        public T createInstance(final Function<String, Object> propertyValueSupplier) {
            // For each property in the arg list, extract its value
            final Object[] args = constructorArgList.stream()
                    .map(propertyValueSupplier)
                    .toArray(Object[]::new);
            try {
                if (constructor == null) {
                    throw new RuntimeException("Missing @JsonCreator constructor for class " + objectClass.getName());
                }
                return constructor.newInstance(args);
            } catch (final InvocationTargetException
                           | IllegalAccessException
                           | InstantiationException
                           | IllegalArgumentException e) {
                throw new RuntimeException(
                        LogUtil.message("Error creating new instance of {} with args {}. Message: {}",
                                objectClass.getName(),
                                args,
                                NullSafe.get(e, Throwable::getMessage)),
                        e);
            }
        }

        @Override
        public String toString() {
            return "ObjectInfo{" +
                   "name='" + name + '\'' +
                   ", objectClass=" + objectClass +
                   '}';
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


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

        private void setSetter(final Method setter) {
            this.setter = Objects.requireNonNull(setter);
        }

        private void addFieldAnnotations(final Annotation... annotations) {
            for (final Annotation annotation : Objects.requireNonNull(annotations)) {
                this.fieldAnnotationsMap.put(annotation.annotationType(), annotation);
            }
        }

        private void addFieldAnnotation(final Annotation annotation) {
            this.fieldAnnotationsMap.put(annotation.annotationType(), annotation);
        }

//        private void addConstructorArgs(final String)


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

        /**
         * @return True if prop is non-null and either the field or getter have
         * the passed {@link Annotation} class.
         */
        public static boolean hasAnnotation(final Prop prop,
                                            final Class<? extends Annotation> clazz) {
            return prop != null
                   && prop.hasAnnotation(clazz);
        }

        public Collection<Annotation> getFieldAnnotations() {
            return fieldAnnotationsMap.values();
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

        public Collection<Annotation> getGetterAnnotations() {
            return getterAnnotationsMap.values();
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
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(LogUtil.message("Error getting value for prop {}", name), e);
            }
        }

        public Object getValueFromConfigObject(final Object obj) {
            try {
                return getter.invoke(obj);
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(LogUtil.message("Error getting value for prop {}", name), e);
            }
        }

        public void setValueOnConfigObject(final Object newValue) {
            try {
                if (setter != null) {
                    setter.invoke(parentObject, newValue);
                }
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(LogUtil.message("Error setting value for prop {}", name), e);
            }
        }

        public void setValueOnConfigObject(final Object parentObject, final Object newValue) {
            try {
                setter.invoke(parentObject, newValue);
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(LogUtil.message("Error setting value for prop {} on {}",
                        name, parentObject), e);
            }
        }

        public Type getValueType() {
            return getter.getGenericReturnType();
//            setter.getGenericParameterTypes()[0];
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
