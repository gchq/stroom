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

package stroom.util.http;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// Singleton so we only have to discover all the getters/setters once
@Singleton
public class ReflectionMapper {

    private final Map<TypeMapping, Function<?, ?>> mappingFunctionMap = new HashMap<>();

    @Inject
    ReflectionMapper(final PathCreator pathCreator) {

        // Generic mappings
        put(String.class, String.class, in -> in);
        put(Boolean.class, Boolean.class, in -> in);
        put(Byte.class, Byte.class, in -> in);
        put(Short.class, Short.class, in -> in);
        put(Integer.class, Integer.class, in -> in);
        put(Long.class, Long.class, in -> in);
        put(Float.class, Float.class, in -> in);
        put(Double.class, Double.class, in -> in);
        put(boolean.class, boolean.class, in -> in);
        put(byte.class, byte.class, in -> in);
        put(short.class, short.class, in -> in);
        put(int.class, int.class, in -> in);
        put(long.class, long.class, in -> in);
        put(float.class, float.class, in -> in);
        put(double.class, double.class, in -> in);
        put(Boolean.class, boolean.class, in -> in);
        put(Byte.class, byte.class, in -> in);
        put(Short.class, short.class, in -> in);
        put(Integer.class, int.class, in -> in);
        put(Long.class, long.class, in -> in);
        put(Float.class, float.class, in -> in);
        put(Double.class, double.class, in -> in);
        put(boolean.class, Boolean.class, Boolean::valueOf);
        put(byte.class, Byte.class, Byte::valueOf);
        put(short.class, Short.class, Short::valueOf);
        put(int.class, Integer.class, Integer::valueOf);
        put(long.class, Long.class, Long::valueOf);
        put(float.class, Float.class, Float::valueOf);
        put(double.class, Double.class, Double::valueOf);
        put(StroomDuration.class, io.dropwizard.util.Duration.class, in -> {
            try {
                // May fail due to overflow
                return io.dropwizard.util.Duration.nanoseconds(in.toNanos());
            } catch (final ArithmeticException e) {
                // Fall back to conversion using millis with possible loss of precision
                return io.dropwizard.util.Duration.milliseconds(in.toMillis());
            }
        });
        put(io.dropwizard.util.Duration.class, StroomDuration.class, in -> {
            final long nanos = in.toNanoseconds();
            // Conversions from coarser to
            //     * finer granularities with arguments that would numerically
            //     * overflow saturate to {@code Long.MIN_VALUE} if negative or
            //     * {@code Long.MAX_VALUE} if positive.
            if (nanos != Long.MIN_VALUE && nanos != Long.MAX_VALUE) {
                return StroomDuration.ofNanos(nanos);
            }
            return StroomDuration.ofMillis(in.toMilliseconds());
        });
        put(File.class, String.class, in -> FileUtil.getCanonicalPath(in.toPath()));
        put(String.class, File.class, in -> pathCreator.toAppPath(in).toFile());
    }

    private <T_IN, T_OUT> void put(final Class<T_IN> inClass,
                                   final Class<T_OUT> outClass,
                                   final Function<T_IN, T_OUT> function) {
        mappingFunctionMap.put(new TypeMapping(inClass, outClass), function);
    }


//    private <T_OUT, T_IN> Optional<T_OUT> convertOptional(final T_IN in, final Class<T_OUT> outClass) {
//        return Optional.ofNullable(convert(in, outClass));
//    }
//
//    private <T_OUT, T_IN> List<T_OUT> convertList(final List<T_IN> in, final Class<T_OUT> outClass) {
//        if (in == null) {
//            return null;
//        }
//        return in.stream().map(i -> convert(i, outClass)).toList();
//    }

    @SuppressWarnings("unchecked")
    public <T_OUT, T_IN> T_OUT convert(final T_IN in, final Class<T_OUT> outClass) {
        // Deal with optionals.
        if (outClass == Optional.class) {
            if (in == null) {
                return (T_OUT) Optional.empty();
            } else if (in.getClass() == Optional.class) {
                final Optional<?> optional = (Optional<?>) in;
                final Object inner = optional.orElse(null);
                if (inner == null) {
                    return (T_OUT) Optional.empty();
                }
                return (T_OUT) Optional.of(convert(inner, inner.getClass()));
            } else {
                return (T_OUT) Optional.of(convert(in, in.getClass()));
            }
        }

        if (in == null) {
            return null;
        }

        if (in.getClass() == Optional.class) {
            final Optional<?> optional = (Optional<?>) in;
            final Object inner = optional.orElse(null);
            if (inner == null) {
                return null;
            }
            return (T_OUT) convert(inner, inner.getClass());
        }

        // Deal with lists.
        if (in instanceof final List<?> list) {
            return (T_OUT) list
                    .stream()
                    .map(item -> convert(item, item.getClass()))
                    .toList();
        }

        final TypeMapping typeMapping = new TypeMapping(in.getClass(), outClass);
        final Function<?, ?> mapping = mappingFunctionMap.computeIfAbsent(typeMapping,
                k -> createMappingWithReflection(in.getClass(), outClass));
        final Function<T_IN, T_OUT> cast = (Function<T_IN, T_OUT>) mapping;
        return cast.apply(in);
    }

    @SuppressWarnings("unchecked")
    private <T_IN, T_OUT> Function<T_IN, T_OUT> createMappingWithReflection(final Class<T_IN> inClass,
                                                                            final Class<T_OUT> outClass) {
        final Method[] inMethods = inClass.getMethods();
        // Find all getters.
        final Map<String, Method> getters = new HashMap<>();
        for (final Method method : inMethods) {
            final String name = method.getName();
            String field = null;
            if (method.getParameterCount() == 0) {
                if (name.length() > 3 && name.startsWith("get") && !"getClass".equals(name)) {
                    field = name.substring(3);
                } else if (name.length() > 2 && name.startsWith("is")) {
                    field = name.substring(2);
                }
            }

            if (field != null) {
                final JsonIgnore jsonIgnore = method.getAnnotation(JsonIgnore.class);
                if (jsonIgnore == null) {
                    field = field.substring(0, 1).toLowerCase() + field.substring(1);
                    getters.put(field, method);
                }
            }
        }

        // Find all setters or constructor.
        Constructor<?> creator = null;
        Constructor<?> zeroArg = null;
        final Constructor<?>[] constructors = outClass.getConstructors();
        for (final Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                zeroArg = constructor;
            }

            final JsonCreator jsonCreator = constructor.getAnnotation(JsonCreator.class);
            if (jsonCreator != null) {
                creator = constructor;
            }
        }

        if (creator != null) {
            final Constructor<?> constructor = creator;

            // We are using the constructor to create the target object.
            final Parameter[] parameters = creator.getParameters();
            final ConstructorMapping[] orderedGetters = new ConstructorMapping[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                final Parameter parameter = parameters[i];
                final JsonProperty jsonProperty = parameter.getAnnotation(JsonProperty.class);

                final String name = parameter.getName();
                Method getter = null;
                if (jsonProperty != null && !jsonProperty.value().isEmpty()) {
                    final String propName = jsonProperty.value();
                    getter = getters.get(propName);
                }
                if (getter == null) {
                    getter = getters.get(name);
                }


                if (getter == null) {
                    throw new RuntimeException("Unable to find getter for: " + inClass.getName() + " " + name);
                }
                orderedGetters[i] = new ConstructorMapping(getter.getReturnType(), parameter.getType(), getter);
            }

            return in -> {
                try {
                    final Object[] initArgs = new Object[parameters.length];
                    for (int i = 0; i < initArgs.length; i++) {
                        final ConstructorMapping constructorMapping = orderedGetters[i];
                        final Object o = constructorMapping.getter().invoke(in);
                        final Object converted = convert(o, constructorMapping.out());
                        initArgs[i] = converted;
                    }
                    final Object out = constructor.newInstance(initArgs);
                    return (T_OUT) out;
                } catch (final InvocationTargetException | IllegalAccessException | InstantiationException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            };


        } else if (zeroArg != null) {

            final Constructor<?> constructor = zeroArg;

            // Find setters.
            final Method[] outMethods = outClass.getMethods();
            final Map<Method, Method> getterToSetterMapping = new HashMap<>();
            for (final Method method : outMethods) {
                final String name = method.getName();
                String field = null;
                if (method.getParameterCount() == 1) {
                    if (name.length() > 3 && name.startsWith("set")) {
                        field = name.substring(3);
                    }
                }

                if (field != null) {
                    field = field.substring(0, 1).toLowerCase() + field.substring(1);
                    final Method getter = getters.remove(field);
                    if (getter == null) {
                        throw new RuntimeException("Unable to find getter for: " + inClass.getName() + " " + field);
                    }
                    getterToSetterMapping.putIfAbsent(getter, method);
                }
            }

            // Ensure we have all mappings.
            if (!getters.isEmpty()) {
                throw new RuntimeException("Unmatched getter/setter for: " + inClass.getName() + " getters " + getters);
            }

            return in -> {
                try {
                    final Object out = constructor.newInstance();
                    getterToSetterMapping.forEach((getter, setter) -> {
                        try {
                            final Object o = getter.invoke(in);
                            final Object converted = convert(o,
                                    setter.getParameters()[0].getType());
                            setter.invoke(out, converted);
                        } catch (final InvocationTargetException | IllegalAccessException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    });
                    return (T_OUT) out;
                } catch (final InvocationTargetException | IllegalAccessException | InstantiationException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            };

        } else {
            throw new RuntimeException("Unable to find suitable constructor for: " + outClass.getName());
        }
    }

    private record ConstructorMapping(Class<?> in, Class<?> out, Method getter) {

    }

    private record TypeMapping(Class<?> in, Class<?> out) {

    }
}
