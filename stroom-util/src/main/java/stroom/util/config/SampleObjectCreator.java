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

import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class SampleObjectCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SampleObjectCreator.class);

    private static final Map<Class<?>, Supplier<Object>> TEST_VAL_SUPPLIERS = new HashMap<>();

    static {
        TEST_VAL_SUPPLIERS.put(String.class, () -> UUID.randomUUID().toString());
        TEST_VAL_SUPPLIERS.put(Long.class, () -> (long) (Math.random() * Long.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(long.class, () -> (long) (Math.random() * Long.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(Integer.class, () -> (int) (Math.random() * Integer.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(int.class, () -> (int) (Math.random() * Integer.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(Short.class, () -> (short) (Math.random() * Short.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(short.class, () -> (short) (Math.random() * Short.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(Byte.class, () -> (byte) (Math.random() * Byte.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(byte.class, () -> (byte) (Math.random() * Byte.MAX_VALUE));
        TEST_VAL_SUPPLIERS.put(Double.class, Math::random);
        TEST_VAL_SUPPLIERS.put(double.class, Math::random);
        TEST_VAL_SUPPLIERS.put(Float.class, () -> (float) Math.random());
        TEST_VAL_SUPPLIERS.put(float.class, () -> (float) Math.random());
        TEST_VAL_SUPPLIERS.put(Boolean.class, () -> true);
        TEST_VAL_SUPPLIERS.put(boolean.class, () -> true);
        TEST_VAL_SUPPLIERS.put(Duration.class, () -> Duration.ofMillis((long) (Math.random() * Long.MAX_VALUE)));
        TEST_VAL_SUPPLIERS.put(StroomDuration.class, () -> StroomDuration.ofMillis((long) (Math.random() *
                                                                                           Long.MAX_VALUE)));
        TEST_VAL_SUPPLIERS.put(ByteSize.class, () -> ByteSize.ofMebibytes((long) (Math.abs(Math.random() * 1000))));
    }

    @SuppressWarnings("unchecked")
    public static <T> T createPopulatedObject(final Class<T> clazz,
                                              final T sample) {
        if (TEST_VAL_SUPPLIERS.containsKey(clazz)) {
            return (T) TEST_VAL_SUPPLIERS.get(clazz).get();
        }

        try {
            // Find zero arg and JsonCreator constructors.
            final Constructor<?>[] constructors = clazz.getConstructors();
            Constructor<T> zeroArgs = null;
            Constructor<T> jsonCreator = null;
            for (final Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    zeroArgs = (Constructor<T>) constructor;
                }
                if (constructor.isAnnotationPresent(JsonCreator.class)) {
                    jsonCreator = (Constructor<T>) constructor;
                }
            }

            if (jsonCreator != null) {
                if (jsonCreator.getParameterCount() == 0) {
                    // Create object with zero arg constructor.
                    return jsonCreator.newInstance();
                } else {
                    // Otherwise we need to create the object using only the creator.
                    final java.lang.reflect.Parameter[] parameters = jsonCreator.getParameters();
                    final Object[] params = new Object[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        final java.lang.reflect.Parameter parameter = parameters[i];
                        final Object param = getParameterValueFromGetter(clazz, parameter, sample);
                        params[i] = param;
                    }
                    return jsonCreator.newInstance(params);
                }
            } else if (zeroArgs != null) {
                // Create object with zero arg constructor.
                return zeroArgs.newInstance();
            }

            throw new RuntimeException("Unable to create sample: " + clazz.getName());

        } catch (final InstantiationException |
                       IllegalAccessException |
                       InvocationTargetException |
                       RuntimeException e) {
            throw new RuntimeException("Unable to create sample: " + clazz.getName() + " " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object getParameterValueFromGetter(final Class<?> clazz,
                                                      final Parameter parameter,
                                                      final Object sample) {
        try {
            final Class<?> type = parameter.getType();
            final JsonProperty jsonProperty = parameter.getAnnotation(JsonProperty.class);
            final String name;
            if (jsonProperty != null) {
                name = jsonProperty.value();
            } else {
                name = parameter.getName();
            }

            Method getter;

            // Look for getter.
            getter = findAnnotatedGetter(clazz, type, name);
            if (getter == null) {
                // Look for field.
                Field field = findAnnotatedField(clazz, type, name);
                if (field == null) {
                    field = findField(clazz, type, name);
                }

                if (field != null) {
                    getter = findAnnotatedGetter(clazz, type, field.getName());
                    if (getter == null) {
                        getter = findGetter(clazz, type, field.getName());
                    }
                } else {
                    getter = findGetter(clazz, type, name);
                }
            }

            if (getter == null) {
                throw new RuntimeException("No getter found for class: " + clazz.getName() + ", name: " + name);
            } else {
                Object value = null;

                // See if we can get a value from the sample object.
                if (sample != null) {
                    value = getter.invoke(sample);
                }

                if (getter.getReturnType().isArray()) {
                    return createArray(getter, value);
                } else if (List.class.isAssignableFrom(getter.getReturnType())) {
                    return createList(getter, value);
                } else if (Set.class.isAssignableFrom(getter.getReturnType())) {
                    return createSet(getter, value);
                } else if (Map.class.isAssignableFrom(getter.getReturnType())) {
                    return createMap(getter, value);
                } else if (getter.getReturnType().isEnum()) {
                    final Object[] constants = getter.getReturnType().getEnumConstants();
                    if (constants != null && constants.length > 0) {
                        final int index = (int) Math.round(Math.random() * (constants.length - 1));
                        return constants[index];
                    }
                }

                Class<?> valueClass = getter.getReturnType();
                if (Modifier.isAbstract(valueClass.getModifiers())) {
                    valueClass = parameter.getType();
                }
                if (!valueClass.isArray() &&
                    !valueClass.isEnum() &&
                    !valueClass.isPrimitive() &&
                    Modifier.isAbstract(valueClass.getModifiers())) {
                    LOGGER.error("Unable to instantiate: " + valueClass.getName());
                    return null;
                }
                return createPopulatedObject((Class<Object>) valueClass, value);
            }
        } catch (final IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static Method findAnnotatedGetter(final Class<?> clazz, final Class<?> type, final String name) {
        final String is = toIs(name);
        final String get = toGet(name);
        final Method[] methods = clazz.getMethods();
        for (final Method method : methods) {
            if (method.getParameterCount() == 0) {
                if (method.getName().startsWith("get") || method.getName().startsWith("is")) {
                    final JsonProperty jsonProperty = method.getAnnotation(JsonProperty.class);
                    if (jsonProperty != null) {
                        if (name.equals(jsonProperty.value())) {
//                            if (!type.isAssignableFrom(method.getReturnType())) {
//                                throw new RuntimeException("Conflicting getter and constructor types for class: " +
//                                                           clazz.getName() +
//                                                           ", method: " +
//                                                           method.getName());
//                            }
                            return method;
                        } else if (jsonProperty.value().isEmpty()) {
                            if (is.equals(method.getName()) || get.equals(method.getName())) {
//                                if (!type.isAssignableFrom(method.getReturnType())) {
//                                    throw new RuntimeException("Conflicting getter and constructor types for " +
//                                    "class: " +
//                                                               clazz.getName() +
//                                                               ", method: " +
//                                                               method.getName());
//                                }
                                return method;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private static Method findGetter(final Class<?> clazz, final Class<?> type, final String name) {
        final String is = toIs(name);
        final String get = toGet(name);
        final Method[] methods = clazz.getMethods();
        for (final Method method : methods) {
            if (method.getParameterCount() == 0) {
                if (is.equals(method.getName()) || get.equals(method.getName())) {
//                    if (!type.isAssignableFrom(method.getReturnType())) {
//                        throw new RuntimeException("Conflicting getter and constructor types for class: " +
//                                                   clazz.getName() +
//                                                   ", method: " +
//                                                   method.getName());
//                    }
                    return method;
                }
            }
        }

        return null;
    }

    private static Field findAnnotatedField(final Class<?> clazz, final Class<?> type, final String name) {
        final Field[] fields = clazz.getDeclaredFields();
        for (final Field field : fields) {
            final JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
            if (jsonProperty != null) {
                if (name.equals(jsonProperty.value())) {
//                    if (!type.isAssignableFrom(field.getType())) {
//                        throw new RuntimeException("Conflicting field and constructor types for class: " +
//                                                   clazz.getName() +
//                                                   ", field: " +
//                                                   field.getName());
//                    }
                    return field;
                }
            }
        }

        return null;
    }

    private static Field findField(final Class<?> clazz, final Class<?> type, final String name) {
        final Field[] fields = clazz.getDeclaredFields();
        for (final Field field : fields) {
            if (name.equals(field.getName())) {
//                if (!type.isAssignableFrom(field.getType())) {
//                    throw new RuntimeException("Conflicting field and constructor types for class: " +
//                                               clazz.getName() +
//                                               ", field: " +
//                                               field.getName());
//                }
                return field;
            }
        }

        return null;
    }

    private static String toIs(final String in) {
        return "is" + firstUpper(in);
    }

    private static String toGet(final String in) {
        return "get" + firstUpper(in);
    }

    private static String firstUpper(final String in) {
        if (in.isEmpty()) {
            return in;
        }
        return in.substring(0, 1).toUpperCase() + in.substring(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T createArray(final Method method, final T sample) throws ClassNotFoundException {
        final Class<?> componentType = method.getReturnType().getComponentType();
        final Class<?> componentTypeClass = Class.forName("[L" + componentType.getName() + ";");
        final Object[] in = (Object[]) sample;
        if (in != null && in.length > 0) {
            final Object[] out = new Object[in.length];
            for (int i = 0; i < in.length; i++) {
                out[i] = createPopulatedObject((Class<Object>) componentTypeClass, in[i]);
            }
            return (T) out;
        } else {
            final Object[] out = new Object[10];
            for (int i = 0; i < out.length; i++) {
                out[i] = createPopulatedObject(componentTypeClass, null);
            }
            return (T) out;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T createList(final Method method, final T sample) {
        final ParameterizedType componentType = (ParameterizedType) method.getGenericReturnType();
        final Class<?> componentTypeClass = (Class<?>) (componentType.getActualTypeArguments()[0]);
        final List<Object> in = (List<Object>) sample;
        if (in != null && !in.isEmpty()) {
            final List<Object> out = new ArrayList<>(in.size());
            for (final Object o : in) {
                out.add(createPopulatedObject((Class<Object>) componentTypeClass, o));
            }
            return (T) out;
        } else {
            final List<Object> out = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) {
                out.add(createPopulatedObject((Class<Object>) componentTypeClass, null));
            }
            return (T) out;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T createSet(final Method method, final T sample) {
        final ParameterizedType componentType = (ParameterizedType) method.getGenericReturnType();
        final Class<?> componentTypeClass = (Class<?>) (componentType.getActualTypeArguments()[0]);
        final Set<Object> in = (Set<Object>) sample;
        if (in != null && !in.isEmpty()) {
            final Set<Object> out = new HashSet<>(in.size());
            for (final Object o : in) {
                out.add(createPopulatedObject((Class<Object>) componentTypeClass, o));
            }
            return (T) out;
        } else {
            final Set<Object> out = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                out.add(createPopulatedObject((Class<Object>) componentTypeClass, null));
            }
            return (T) out;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T createMap(final Method method, final T sample) {
        final ParameterizedType componentType = (ParameterizedType) method.getGenericReturnType();
        final Class<?> keyClass = (Class<?>) (componentType.getActualTypeArguments()[0]);
        final Class<?> valueClass = (Class<?>) (componentType.getActualTypeArguments()[1]);
        final Map<Object, Object> in = (Map<Object, Object>) sample;
        if (in != null && !in.isEmpty()) {
            final Map<Object, Object> out = new HashMap<>(in.size());
            for (final Entry<Object, Object> entry : in.entrySet()) {
                final Object key = createPopulatedObject((Class<Object>) keyClass, entry.getKey());
                final Object value = createPopulatedObject((Class<Object>) valueClass, entry.getValue());
                out.put(key, value);
            }
            return (T) out;
        } else {
            final Map<Object, Object> out = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                final Object key = createPopulatedObject((Class<Object>) keyClass, null);
                final Object value = createPopulatedObject((Class<Object>) valueClass, null);
                out.put(key, value);
            }
            return (T) out;
        }
    }
}
