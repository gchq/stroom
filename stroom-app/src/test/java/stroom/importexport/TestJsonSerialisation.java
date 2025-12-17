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

package stroom.importexport;

import stroom.util.json.JsonUtil;
import stroom.util.shared.RestResource;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.fusesource.restygwt.client.DirectRestService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 1. Discover which classes are used by Resources and don't have JSON annotations
 * 2. Discover classes with JSON annotations that don't include @JsonInclude
 * 3. Discover classes with JSON annotations that don't include @JsonCreator
 * 4. Identify classes in .shared. packages that don't have JSON annotations.
 * 5. Ensure all JSON annotated classes default serialisation behaviour is consistent.
 * 6. Build complex JSON annotated objects and perform serialisation testing.
 * 7. Ensure all JSON classes that use Map have a string key
 */
class TestJsonSerialisation {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestJsonSerialisation.class);
    private static final String PACKAGE_NAME = "stroom";
    private static final String PACKAGE_START = PACKAGE_NAME + ".";
    private static List<Class<?>> RESOURCE_RELATED_CLASSES;
//
//
//    @Test
//    void test() {
//        final ObjectMapper objectMapper = JsonUtil.getMapper();
//
//        String routeAnnotation = JsonCreator.class.getName();// "com.fasterxml.jackson.annotation.JsonCreator";
//        try (ScanResult scanResult =
//                     new ClassGraph()
//                             .enableAllInfo()             // Scan classes, methods, fields, annotations
//                             .acceptPackages(PACKAGE_NAME)      // Scan com.xyz and subpackages (omit to
//                                                                      scan all packages)
//                             .scan()) {                   // Start the scan
//            for (ClassInfo routeClassInfo : scanResult.getClassesWithMethodAnnotation(routeAnnotation)) {
//                try {
//                    Class<?> clazz = routeClassInfo.loadClass();
//                    Constructor<?> constructor = clazz.getConstructor();
//                    Object o = constructor.newInstance();
//
//                    String json1 = objectMapper.writeValueAsString(o);
//                    Object o2 = objectMapper.readValue(json1, clazz);
//                    String json2 = objectMapper.writeValueAsString(o2);
//
//                    if (json1.equals(json2)) {
//                        System.out.println(routeClassInfo.getName());
//                    } else {
//                        System.err.println(routeClassInfo.getName());
//                    }
////                    Assertions.assertThat(json1).isEqualTo(json2);
//
//                } catch (final NoSuchMethodException e) {
////                    System.err.println("No default constructor: " + routeClassInfo.getName());
//                } catch (final RuntimeException |
//                        IOException |
//                        InvocationTargetException |
//                        InstantiationException |
//                        IllegalAccessException e) {
//                    System.err.println(e.getMessage());
//                }
////                AnnotationInfo routeAnnotationInfo = routeClassInfo.getAnnotationInfo(routeAnnotation);
////                List<AnnotationParameterValue> routeParamVals = routeAnnotationInfo.getParameterValues();
////                // @com.xyz.Route has one required parameter
////                String route = (String) routeParamVals.get(0).getValue();
////                System.out.println(routeClassInfo.getName() + " is annotated with route " + route);
//            }
//        }
//    }

    @BeforeAll
    static void setup() {
        RESOURCE_RELATED_CLASSES = getResourceRelatedClasses();
        Assertions.assertThat(RESOURCE_RELATED_CLASSES.size()).isGreaterThan(700);
        LOGGER.info("Found {} resource related classes", RESOURCE_RELATED_CLASSES.size());
    }

    /**
     * Tests that constructing an object with the default constructor, serialising, de-serialising, re-serialising,
     * looks the same.
     */
    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> testDefaultValues() {
        final ObjectMapper objectMapper = JsonUtil.getMapper();

        return buildRelatedResourceTests(clazz -> {
            // Try and find the no args constructor if there is any.
            if (!Modifier.isInterface(clazz.getModifiers())
                && !Modifier.isAbstract(clazz.getModifiers())) {

                final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                Constructor<?> noArgsConstructor = null;
                for (final Constructor<?> constructor : constructors) {
                    if (constructor.getParameterCount() == 0) {
                        noArgsConstructor = constructor;
                    }
                }

                if (noArgsConstructor != null) {
                    noArgsConstructor.setAccessible(true);

                    final String json1;
                    final String json2;
                    try {
                        final Object o = noArgsConstructor.newInstance();
                        json1 = objectMapper.writeValueAsString(o);
                        final Object o2 = objectMapper.readValue(json1, clazz);
                        json2 = objectMapper.writeValueAsString(o2);
                    } catch (final InstantiationException
                                   | IllegalAccessException
                                   | InvocationTargetException
                                   | IOException e) {
                        throw new RuntimeException(e);
                    }

                    Assertions.assertThat(json2)
                            .describedAs(
                                    "%s - Checking default values on (de)serialisation", clazz.getName())
                            .isEqualTo(json1);
                }
            }
        });

    }


    /**
     * Tests full serialisation.
     */
    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> testFullSerialisation() {
        final Map<Class<?>, Object> valueStrategies = initializeValueStrategies();
        final ObjectMapper objectMapper = JsonUtil.getMapper();

        return buildRelatedResourceTests(clazz -> {
            if (!Modifier.isInterface(clazz.getModifiers())
                && !Modifier.isAbstract(clazz.getModifiers())
                && !clazz.isEnum()) {

                final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                final Constructor<?> test = findTestConstructor(constructors);
                if (test != null) {
                    construct(clazz, test, valueStrategies, objectMapper);
                } else {
                    final Constructor<?> creator = findJsonCreator(constructors);
                    if (creator != null) {
                        construct(clazz, creator, valueStrategies, objectMapper);
                    } else {
                        for (final Constructor<?> constructor : constructors) {
                            construct(clazz, constructor, valueStrategies, objectMapper);
                        }
                    }
                }
            }
        });
    }

    private static Constructor<?> findJsonCreator(final Constructor<?>[] constructors) {
        for (final Constructor<?> constructor : constructors) {
            final JsonCreator jsonCreator = constructor.getAnnotation(JsonCreator.class);
            if (jsonCreator != null) {
                return constructor;
            }
        }
        return null;
    }

    private static Constructor<?> findTestConstructor(final Constructor<?>[] constructors) {
        for (final Constructor<?> constructor : constructors) {
            final SerialisationTestConstructor serialisationTestConstructor =
                    constructor.getAnnotation(SerialisationTestConstructor.class);
            if (serialisationTestConstructor != null) {
                return constructor;
            }
        }
        return null;
    }

    private void construct(final Class<?> clazz,
                           final Constructor<?> constructor,
                           final Map<Class<?>, Object> valueStrategies,
                           final ObjectMapper objectMapper) {
        final Class<?>[] paramTypes = constructor.getParameterTypes();
        final Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < params.length; i++) {
            final Class<?> paramType = paramTypes[i];
            params[i] = valueStrategies.get(paramType);
        }
        try {
            constructor.setAccessible(true);
            final Object o = constructor.newInstance(params);
            final String json1;
            final String json2;
            try {
                json1 = objectMapper.writeValueAsString(o);
                final Object o2 = objectMapper.readValue(json1, clazz);
                json2 = objectMapper.writeValueAsString(o2);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }

            Assertions.assertThat(json2)
                    .describedAs(
                            "%s - Checking default values on (de)serialisation", clazz.getName())
                    .isEqualTo(json1);

        } catch (final InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test that all objects that will be serialised as JSON and contain maps do not use anything other than String as
     * map keys as this isn't serialisable.
     */
    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> testNoComplexMaps() {
        return buildRelatedResourceTests(clazz -> {
            // Try and find the no args constructor if there is any.
            if (!Modifier.isInterface(clazz.getModifiers())) {
                final Field[] fields = clazz.getDeclaredFields();
                for (final Field field : fields) {
                    // Don't care about static as they are not serialised.
                    if (Map.class.isAssignableFrom(field.getType())
                        && !Modifier.isStatic(field.getModifiers())) {
                        final ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
                        final Type keyType = parameterizedType.getActualTypeArguments()[0];
                        if (!(keyType instanceof Class && ((Class<?>) keyType).isEnum())) {
                            Assertions.assertThat(keyType.getTypeName())
                                    .describedAs(
                                            "%s - Bad key type, maps must have string keys", clazz.getName())
                                    .isEqualTo(String.class.getName());
                        }
                    }
                }
            }
        });
    }

    /**
     * Test that classes that will be subject to JSON serialisation have no extra properties or redundant JSON
     * annotations.
     */
    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> testNoExtraProps() {
        return buildRelatedResourceTests(clazz -> {
            // Try and find the no args constructor if there is any.
            if (!Modifier.isInterface(clazz.getModifiers()) && !clazz.isEnum()) {
                final Set<String> fieldNames = new HashSet<>();
                final Field[] fields = clazz.getDeclaredFields();
                for (final Field field : fields) {
                    final JsonIgnore jsonIgnore = field.getDeclaredAnnotation(JsonIgnore.class);
                    if (jsonIgnore == null) {
                        final String fieldName = normaliseFieldName(field.getName());
                        fieldNames.add(fieldName);
                    }
                }

                final Set<String> getters = new HashSet<>();
                final Set<String> setters = new HashSet<>();
                final Set<String> uselessIgnore = new HashSet<>();
                final Method[] methods = clazz.getDeclaredMethods();
                for (final Method method : methods) {
                    final JsonIgnore jsonIgnore = method.getDeclaredAnnotation(JsonIgnore.class);
                    final String methodName = method.getName();
                    final boolean getter = isGetter(method);
                    final boolean setter = isSetter(method);
                    if (!getter && !setter) {
                        if (jsonIgnore != null) {
                            uselessIgnore.add(methodName);
                        }
                    } else if (jsonIgnore == null) {
                        final String fieldName = convertMethodToFieldName(methodName);
                        if (setter) {
                            setters.add(fieldName);
                        } else {
                            getters.add(fieldName);
                        }
                    }
                }

                final Set<String> additionalGetters = new HashSet<>(getters);
                additionalGetters.removeAll(fieldNames);

                final Set<String> additionalSetters = new HashSet<>(setters);
                additionalSetters.removeAll(fieldNames);

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(additionalGetters)
                            .describedAs(
                                    "%s - Additional getters: %s",
                                    clazz.getName(),
                                    additionalGetters)
                            .isEmpty();

                    softly.assertThat(additionalSetters)
                            .describedAs(
                                    "%s - Additional setters: %s",
                                    clazz.getName(),
                                    additionalSetters)
                            .isEmpty();

                    softly.assertThat(uselessIgnore)
                            .describedAs(
                                    "%s - Useless @JsonIgnore annotation: %s",
                                    clazz.getName(),
                                    uselessIgnore)
                            .isEmpty();
                });
            }

        });
    }

    private void dumpErrors(final Map<String, String> classErrors) {
        classErrors.forEach((className, msg) ->
                LOGGER.error("Class {} has error(s): \n{}", className, msg));
    }

    /**
     * Test that classes that will be subject to JSON serialisation have the full complement of JSON annotations.
     */
    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> testJsonAnnotations() {
        return buildRelatedResourceTests(clazz -> {

            if (!Modifier.isInterface(clazz.getModifiers())
                && !Modifier.isAbstract(clazz.getModifiers())
                && !clazz.isEnum()) {
                final boolean hasJsonInclude = clazz.getAnnotation(JsonInclude.class) != null;
                final boolean hasJsonPropertyOrder = clazz.getAnnotation(JsonPropertyOrder.class) != null;
                final AtomicInteger jsonCreatorCount = new AtomicInteger(0);
                final Set<String> fieldsWithoutAnnotations = new HashSet<>();
                final Set<String> methodsWithAnnotations = new HashSet<>();
                final Set<String> constructorPropNames = new HashSet<>();
                final Set<String> fieldPropNames;

                // If we only have a default constructor then we don't want the JsonCreator annotation to be
                // used as it breaks RestyGWT.
                final AtomicBoolean singleEmptyConstructor = new AtomicBoolean(true);

                for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                    final JsonCreator jsonCreator = constructor.getAnnotation(JsonCreator.class);
                    if (jsonCreator != null) {
                        jsonCreatorCount.incrementAndGet();
                        constructorPropNames.addAll(getConstructorPropNames(constructor));
                    }
                    if (constructor.getParameterCount() > 0) {
                        singleEmptyConstructor.set(false);
                    }
                }

                fieldPropNames = getAllFields(clazz).stream()
                        .filter(field -> field.getDeclaredAnnotation(JsonIgnore.class) == null
                                         && field.getDeclaredAnnotation(JsonProperty.class) != null)
                        .map(field -> {
                            final JsonProperty jsonProperty = field.getDeclaredAnnotation(JsonProperty.class);
                            if (!jsonProperty.value().isEmpty()) {
                                return jsonProperty.value();
                            } else {
                                return field.getName();
                            }
                        })
                        .collect(Collectors.toSet());

//                    LOGGER.info("{}", fieldPropNames);

                final Field[] fields = clazz.getDeclaredFields();
                for (final Field field : fields) {
                    final JsonIgnore jsonIgnore = field.getDeclaredAnnotation(JsonIgnore.class);
                    final JsonProperty jsonProperty = field.getDeclaredAnnotation(JsonProperty.class);
                    if (jsonIgnore == null
                        && jsonProperty == null
                        && !Modifier.isStatic(field.getModifiers())) {
                        final String fieldName = field.getName();
                        fieldsWithoutAnnotations.add(fieldName);
                    }
                }

                final Method[] methods = clazz.getDeclaredMethods();
                for (final Method method : methods) {
                    final JsonProperty jsonProperty = method.getDeclaredAnnotation(JsonProperty.class);
                    if (jsonProperty != null) {
                        methodsWithAnnotations.add(method.getName());
                    }
                }

                SoftAssertions.assertSoftly(softly -> {
                    // We allow type to be set statically for docs.
                    if (fieldPropNames.contains("type")) {
                        constructorPropNames.add("type");
                    }

                    softly.assertThat(constructorPropNames)
                            .describedAs("%s - JsonProperties defined in the constructor must have a " +
                                         "corresponding JsonProperty on the field.", clazz.getName())
                            .containsExactlyInAnyOrderElementsOf(fieldPropNames);

                    softly.assertThat(hasJsonInclude)
                            .describedAs("%s - Missing JsonInclude annotation.", clazz.getName())
                            .isTrue();
//                                softly.assertThat(hasJsonPropertyOrder)
//                                        .withFailMessage("No JsonPropertyOrder")
//                                        .isTrue();
                    if (singleEmptyConstructor.get()) {
                        // If we only have a default constructor then we don't want the JsonCreator annotation to be
                        // used as it breaks RestyGWT.
                        softly.assertThat(jsonCreatorCount)
                                .withFailMessage(
                                        "%s - Should have no JsonCreator on default constructor, found %s",
                                        clazz.getName(), jsonCreatorCount.get())
                                .hasValue(0);
                    } else {
                        softly.assertThat(jsonCreatorCount)
                                .withFailMessage(
                                        "%s - Should have exactly one JsonCreator, found %s",
                                        clazz.getName(), jsonCreatorCount.get())
                                .hasValue(1);
                    }
                    softly.assertThat(fieldsWithoutAnnotations)
                            .withFailMessage(
                                    "%s - Fields without annotations: %s",
                                    clazz.getName(), fieldsWithoutAnnotations)
                            .isEmpty();
                    softly.assertThat(methodsWithAnnotations)
                            .withFailMessage(
                                    "%s - Methods with annotations: %s",
                                    clazz.getName(), methodsWithAnnotations)
                            .isEmpty();
                });
            }
        });
    }

    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void testAllSharedAreResources() {
        final List<Class<?>> sharedClasses = getSharedClasses();

        sharedClasses.removeAll(RESOURCE_RELATED_CLASSES);

        LOGGER.info("Shared classes that are not resource related:\n{}",
                sharedClasses.stream()
                        .map(Class::getName)
                        .collect(Collectors.joining("\n")));
    }

    private Stream<DynamicTest> buildRelatedResourceTests(final Consumer<Class<?>> work) {

        return RESOURCE_RELATED_CLASSES.parallelStream()
                .map(clazz -> {
                    final String className = clazz.getName();

                    return DynamicTest.dynamicTest(className, () -> {
                        // Do the test
                        work.accept(clazz);
                    });
                });
    }


    private Set<String> getConstructorPropNames(final Constructor<?> constructor) {
        final Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
        final Set<String> propNames = new HashSet<>();
        for (final Annotation[] singleParamAnnos : parameterAnnotations) {
            for (final Annotation annotation : singleParamAnnos) {
                if (JsonProperty.class.isAssignableFrom(annotation.annotationType())) {
                    final JsonProperty jsonProperty = (JsonProperty) annotation;
                    propNames.add(jsonProperty.value());
                    break;
                }
            }
        }
        return propNames;
    }

    private String checkAllGettersAndSetters(final Class<?> clazz) {
        final StringBuilder sb = new StringBuilder();

        final String className = clazz.getName();
        LOGGER.info(className);

        try {
            // Try and find the no args constructor if there is any.
            if (!Modifier.isInterface(clazz.getModifiers()) && !clazz.isEnum()) {
                final Set<String> fieldNames = new HashSet<>();
                final Field[] fields = clazz.getDeclaredFields();
                for (final Field field : fields) {
                    final JsonIgnore jsonIgnore = field.getDeclaredAnnotation(JsonIgnore.class);
                    if (jsonIgnore == null && !Modifier.isStatic(field.getModifiers())) {
                        final String fieldName = normaliseFieldName(field.getName());
                        fieldNames.add(fieldName);
                    }
                }

                final Set<String> getters = new HashSet<>();
                final Set<String> setters = new HashSet<>();
                final Method[] methods = clazz.getDeclaredMethods();
                for (final Method method : methods) {
                    final JsonIgnore jsonIgnore = method.getDeclaredAnnotation(JsonIgnore.class);
                    final String methodName = method.getName();
                    if (jsonIgnore == null) {
                        final boolean getter = isGetter(method);
                        final boolean setter = isSetter(method);
                        if (getter || setter) {
                            final String fieldName = convertMethodToFieldName(methodName);
                            if (setter) {
                                setters.add(fieldName);
                            } else {
                                getters.add(fieldName);
                            }
                        }
                    }
                }

                if (!getters.containsAll(fieldNames)) {
                    final Set<String> missing = new HashSet<>(fieldNames);
                    missing.removeAll(getters);
                    sb.append("\nMissing getters ");
                    sb.append(missing.toString());
                }

                if (!setters.containsAll(fieldNames)) {
                    final Set<String> missing = new HashSet<>(fieldNames);
                    missing.removeAll(setters);
                    sb.append("\nMissing setters ");
                    sb.append(missing.toString());
                }

                if (!fieldNames.containsAll(getters)) {
                    final Set<String> missing = new HashSet<>(fieldNames);
                    missing.removeAll(getters);
                    sb.append("\nOrphan fields ");
                    sb.append(missing.toString());
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return sb.toString().trim();
    }

    private String normaliseFieldName(final String fieldName) {
        if (fieldName.startsWith("_")) {
            return fieldName.substring(1);
        }
        return fieldName;
    }

    private boolean isGetter(final Method method) {
        return !Modifier.isAbstract(method.getModifiers()) &&
               Modifier.isPublic(method.getModifiers()) &&
               method.getParameterCount() == 0 &&
               (method.getName().startsWith("is") || method.getName().startsWith("get"));
    }

    private boolean isSetter(final Method method) {
        return !Modifier.isAbstract(method.getModifiers()) &&
               Modifier.isPublic(method.getModifiers()) &&
               method.getParameterCount() == 1 &&
               method.getName().startsWith("set");
    }

    private String convertMethodToFieldName(final String methodName) {
        String name = methodName;
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get")) {
            name = name.substring(3);
        } else if (name.startsWith("set")) {
            name = name.substring(3);
        }

        if (name.length() > 0) {
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        return name;
    }

    private static void addPublicMethods(final Set<Class<?>> stroomClasses, final Class<?> clazz) {
        final Method[] methods = clazz.getMethods();
        for (final Method method : methods) {
            if (Modifier.isPublic(method.getModifiers()) && !method.getName().equals("getClass")) {
                // Add method parameters.
                final Class<?>[] parameterTypes = method.getParameterTypes();
                final Type[] genericParameterTypes = method.getGenericParameterTypes();

                for (int i = 0; i < parameterTypes.length; i++) {
                    addType(stroomClasses, genericParameterTypes[i]);
                }

                // Add return type.
                addType(stroomClasses, method.getGenericReturnType());
            }
        }
    }

    private static void addType(final Set<Class<?>> stroomClasses, final Type type) {
        if (Class.class.isAssignableFrom(type.getClass())) {
            addClass(stroomClasses, (Class<?>) type, null);
        } else if (TypeVariable.class.isAssignableFrom(type.getClass())) {
            final Class<?> clazz = (Class<?>) ((TypeVariable<?>) type).getGenericDeclaration();
            addClass(stroomClasses, clazz, null);

        } else if (ParameterizedType.class.isAssignableFrom(type.getClass())) {
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            addType(stroomClasses, parameterizedType.getRawType());
            for (final Type arg : parameterizedType.getActualTypeArguments()) {
                addType(stroomClasses, arg);
            }

        } else {
            throw new RuntimeException("Didn't expect to get here");
        }
    }

    private static void addClass(final Set<Class<?>> stroomClasses, final Class<?> clazz, final Type type) {
        if (clazz.isArray()) {
            addClass(stroomClasses, clazz.getComponentType(), null);

        } else if (type != null && Map.class.isAssignableFrom(clazz)) {
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            for (final Type arg : parameterizedType.getActualTypeArguments()) {
                addType(stroomClasses, arg);
            }

        } else if (type != null && Collection.class.isAssignableFrom(clazz)) {
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            for (final Type arg : parameterizedType.getActualTypeArguments()) {
                addType(stroomClasses, arg);
            }

        } else if (clazz.getName().startsWith(PACKAGE_START)
                   && !clazz.getName().contains("StroomDuration")) { // Non POJO
            // IF the class references sub classes then include those too.
            final JsonSubTypes jsonSubTypes = clazz.getAnnotation(JsonSubTypes.class);
            if (jsonSubTypes != null) {
                for (final JsonSubTypes.Type subType : jsonSubTypes.value()) {
                    final Class<?> subTypeClass = subType.value();
                    addClass(stroomClasses, subTypeClass, null);
                }
            }

            if (!stroomClasses.contains(clazz)) {
                stroomClasses.add(clazz);
                addFields(stroomClasses, clazz);
            }
        }
    }

    private static void addFields(final Set<Class<?>> stroomClasses, final Class<?> parentClazz) {
        final Field[] fields = parentClazz.getDeclaredFields();
        for (final Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                addType(stroomClasses, field.getGenericType());
//                addClass(stroomClasses, field.getType(), field.getGenericType());
            }
        }

        final Class<?> superClazz = parentClazz.getSuperclass();
        if (superClazz != null && superClazz.getName().startsWith(PACKAGE_START)) {
            for (final Constructor<?> constructor : parentClazz.getDeclaredConstructors()) {
                final JsonCreator jsonCreator = constructor.getAnnotation(JsonCreator.class);
                if (jsonCreator != null) {
                    for (final Type parameterType : constructor.getGenericParameterTypes()) {
                        addType(stroomClasses, parameterType);
                    }
                }
            }
        }
    }

    private static List<Class<?>> getResourceRelatedClasses() {
        final Set<Class<?>> stroomClasses = new HashSet<>();
        try (final ScanResult scanResult =
                new ClassGraph()
                        .enableClassInfo()             // Scan classes, methods, fields, annotations
                        .acceptPackages(PACKAGE_NAME)  // Scan com.xyz and subpackages (omit to scan all packages)
                        .enableAnnotationInfo()
                        .scan()) {

            // Start the scan
            for (final ClassInfo routeClassInfo : scanResult.getClassesWithAnyAnnotation(
                    JsonCreator.class,
                    JsonInclude.class,
                    JsonTypeInfo.class,
                    JsonSubTypes.class,
                    JsonProperty.class)) {
                final Class<?> clazz = routeClassInfo.loadClass();
                addJsonRelatedClasses(clazz, scanResult, stroomClasses);
            }

            for (final ClassInfo routeClassInfo : scanResult.getClassesImplementing(
                    DirectRestService.class.getName())) {

                final Class<?> clazz = routeClassInfo.loadClass();
                addPublicMethods(stroomClasses, clazz);
            }
            for (final ClassInfo routeClassInfo : scanResult.getClassesImplementing(RestResource.class.getName())) {
                final Class<?> clazz = routeClassInfo.loadClass();
                addPublicMethods(stroomClasses, clazz);
            }
        }

        return stroomClasses
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toList());
    }

    private static void addJsonRelatedClasses(final Class<?> clazz,
                                              final ScanResult scanResult,
                                              final Set<Class<?>> stroomClasses) {
        if (!Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
            addClass(stroomClasses, clazz, null);
        }

        final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        final Constructor<?> creator = findJsonCreator(constructors);
        if (creator != null) {
            addJsonRelatedClasses(scanResult, stroomClasses, creator);
        } else {
            for (final Constructor<?> constructor : constructors) {
                addJsonRelatedClasses(scanResult, stroomClasses, constructor);
            }
        }
    }

    private static void addJsonRelatedClasses(final ScanResult scanResult,
                                              final Set<Class<?>> stroomClasses,
                                              final Constructor<?> constructor) {
        final Type[] types = constructor.getGenericParameterTypes();
        for (final Type type : types) {
            addType(stroomClasses, type);

            for (final ClassInfo routeClassInfo : scanResult.getSubclasses(type.getClass())) {
                final Class<?> inner = routeClassInfo.loadClass();
                addJsonRelatedClasses(inner, scanResult, stroomClasses);
            }
        }
    }

    private List<Class<?>> getSharedClasses() {
        final Set<Class<?>> stroomClasses = new HashSet<>();

        try (final ScanResult scanResult =
                new ClassGraph()
                        .enableClassInfo()
                        .acceptPackages(PACKAGE_NAME)
                        .rejectPackages("hadoopcommonshaded")
                        .scan()) {

            LOGGER.info("class count {}", scanResult.getAllClasses().size());

            for (final ClassInfo routeClassInfo : scanResult.getAllClasses()) {
//                LOGGER.info("{}", routeClassInfo.getName());
                final String name = routeClassInfo.getName();
                if (name.contains(".shared.") &&
                    !name.contains("hadoopcommonshaded") &&
                    !name.contains("Util") &&
                    !name.contains("$") &&
                    !name.contains("_")) {
                    try {
                        final Class<?> clazz = routeClassInfo.loadClass();
                        if (!Modifier.isInterface(clazz.getModifiers()) &&
                            !Modifier.isAbstract(clazz.getModifiers()) &&
                            !RestResource.class.isAssignableFrom(clazz) &&
                            !DirectRestService.class.isAssignableFrom(clazz)) {
                            stroomClasses.add(clazz);
                        }
                    } catch (final IllegalArgumentException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                }
            }
        }

        LOGGER.info("Shared class count {}", stroomClasses.size());

        return stroomClasses
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toList());
    }

    /**
     * Gets all @{@link JsonProperty} annotated fields on this obj and its supers
     */
    private <T> List<Field> getAllFields(final Class<T> clazz) {
        final List<Field> fields = new ArrayList<>();
        Class<?> clazz2 = clazz;
        while (clazz2 != Object.class) {
            fields.addAll(Arrays.asList(clazz2.getDeclaredFields()));
            clazz2 = clazz2.getSuperclass();
        }
        return fields;
    }

    /**
     * Initialize value strategies for common types
     */
    private Map<Class<?>, Object> initializeValueStrategies() {
        final Map<Class<?>, Object> strategies = new HashMap<>();

        // Primitives and their wrappers
        strategies.put(boolean.class, false);
        strategies.put(Boolean.class, false);

        strategies.put(byte.class, (byte) 0);
        strategies.put(Byte.class, (byte) 0);

        strategies.put(short.class, (short) 0);
        strategies.put(Short.class, (short) 0);

        strategies.put(int.class, 0);
        strategies.put(Integer.class, 0);

        strategies.put(long.class, 0L);
        strategies.put(Long.class, 0L);

        strategies.put(float.class, 0.0f);
        strategies.put(Float.class, 0.0f);

        strategies.put(double.class, 0.0);
        strategies.put(Double.class, 0.0);

        strategies.put(char.class, 'a');
        strategies.put(Character.class, 'a');

        // String
        strategies.put(String.class, "test");

        return strategies;
    }
}

