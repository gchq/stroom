/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl;

import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.ai.impl.tools.StroomTool;
import stroom.mcp.impl.tools.PortedToolsModule;

import com.google.inject.Binding;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/// Every tool Stroom offers, built-in ([BuiltInToolsModule]) and ported from the Stroom MCP server
/// ([PortedToolsModule]), each made with its injected constructor and only asked for its definition. What a tool
/// needs from this module or `stroom-ai-impl` is made the same way; anything else (Stroom's services, interfaces)
/// is a Mockito mock that is never used, and a provider, optional or collection is given empty or lazily.
final class EveryTool {

    // The packages whose classes are made rather than mocked.
    private static final List<String> MADE_PACKAGES = List.of("stroom.ai.impl", "stroom.mcp.impl");

    private final Map<Class<?>, Object> made = new HashMap<>();
    private final Set<Class<?>> making = new HashSet<>();

    /// @return Every tool bound by the two modules.
    List<StroomTool> tools() {
        final List<StroomTool> tools = new ArrayList<>();
        for (final Class<?> toolClass : toolClasses(new BuiltInToolsModule(), new PortedToolsModule())) {
            tools.add((StroomTool) make(toolClass));
        }
        return tools;
    }

    // The tool classes the modules add to the StroomTool multibinder.
    private static List<Class<?>> toolClasses(final Module... modules) {
        final List<Class<?>> classes = new ArrayList<>();
        for (final Element element : Elements.getElements(modules)) {
            if (element instanceof final LinkedKeyBinding<?> binding && isToolBinding(binding)) {
                classes.add(binding.getLinkedKey().getTypeLiteral().getRawType());
            }
        }
        return classes;
    }

    private static boolean isToolBinding(final Binding<?> binding) {
        return StroomTool.class.equals(binding.getKey().getTypeLiteral().getRawType());
    }

    // A value for a constructor parameter of this type.
    private Object value(final Type type) {
        if (type instanceof final ParameterizedType parameterized) {
            final Class<?> raw = (Class<?>) parameterized.getRawType();
            final Type argument = parameterized.getActualTypeArguments()[0];
            if (jakarta.inject.Provider.class.equals(raw)) {
                return (jakarta.inject.Provider<Object>) () -> value(argument);
            }
            if (com.google.inject.Provider.class.equals(raw)) {
                return (com.google.inject.Provider<Object>) () -> value(argument);
            }
            if (Supplier.class.equals(raw)) {
                return (Supplier<Object>) () -> value(argument);
            }
            if (Optional.class.equals(raw)) {
                return Optional.empty();
            }
            return value(raw);
        }
        return value((Class<?>) type);
    }

    private Object value(final Class<?> type) {
        if (type.isPrimitive()) {
            return primitive(type);
        }
        if (String.class.equals(type)) {
            return "";
        }
        if (Set.class.isAssignableFrom(type)) {
            return Set.of();
        }
        if (List.class.isAssignableFrom(type) || Collection.class.equals(type)) {
            return List.of();
        }
        if (Map.class.isAssignableFrom(type)) {
            return Map.of();
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        return make(type);
    }

    // An instance of a class, made once: with its injected constructor if it is one of ours, else a mock.
    private Object make(final Class<?> type) {
        final Object existing = made.get(type);
        if (existing != null) {
            return existing;
        }
        final Constructor<?> constructor = isOurs(type) && !making.contains(type)
                ? injectedConstructor(type)
                : null;
        final Object instance;
        if (constructor != null) {
            making.add(type);
            try {
                constructor.setAccessible(true);
                final Object[] arguments = Arrays.stream(constructor.getGenericParameterTypes())
                        .map(this::value)
                        .toArray();
                instance = constructor.newInstance(arguments);
            } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException("Couldn't make " + type.getName() + ": " + e.getCause(), e);
            } finally {
                making.remove(type);
            }
        } else {
            instance = Mockito.mock(type);
        }
        made.put(type, instance);
        return instance;
    }

    private static boolean isOurs(final Class<?> type) {
        return !type.isInterface()
               && !Modifier.isAbstract(type.getModifiers())
               && MADE_PACKAGES.stream().anyMatch(prefix -> type.getName().startsWith(prefix + "."));
    }

    private static Constructor<?> injectedConstructor(final Class<?> type) {
        for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(jakarta.inject.Inject.class)
                || constructor.isAnnotationPresent(com.google.inject.Inject.class)) {
                return constructor;
            }
        }
        // A class Guice makes with its no-argument constructor (e.g. a config class).
        for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                return constructor;
            }
        }
        return null;
    }

    private static Object primitive(final Class<?> type) {
        if (boolean.class.equals(type)) {
            return false;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (double.class.equals(type)) {
            return 0.0;
        }
        return 0;
    }
}
