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

package stroom.query.api.current;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates a text representation (a 'portrait') of a class's public methods.
 * <p>
 * Only recurses over those classes that match a base package.
 */
public class ClassPhotographer {

    private static final String newLine = System.getProperty("line.separator");

    public static String takePortraitOf(final Class<?> clazz, final String basePackage) {
        final Map<Class<?>, List<String>> portrait = new HashMap<>();
        takePortraitOf(clazz, portrait, basePackage);
        final List<String> flattenedPortrait = flattenPortrait(portrait);
        // If we don't sort the portrait it might have a different order every time.
        flattenedPortrait.sort(String::compareTo);
        return String.join(newLine, flattenedPortrait);
    }

    private static void takePortraitOf(final Class<?> clazz,
                                       final Map<Class<?>, List<String>> portrait,
                                       final String basePackage) {
        final List<String> methodSignatures = new ArrayList<>();
        final List<Class<?>> classesForRecursion = new ArrayList<>();

        // We don't need to filter by 'public' because `getMethods()` only gets public methods.
        Arrays.stream(clazz.getMethods())
                .forEach(method -> {
                    methodSignatures.add(method.toGenericString());
                    classesForRecursion.addAll(getClassesForRecursion(basePackage, method));
                });

        portrait.put(clazz, methodSignatures);

        // Time to recurse
        classesForRecursion.stream()
                .filter(typeToAnalyse -> !portrait.containsKey(typeToAnalyse))
                .forEach(typeToAnalyse -> takePortraitOf(typeToAnalyse, portrait, basePackage));
    }

    private static List<String> flattenPortrait(final Map<Class<?>, List<String>> portraitMap) {
        final List<String> flattenedPortrait = new ArrayList<>();
        for (final Class<?> key : portraitMap.keySet()) {
            flattenedPortrait.addAll(portraitMap.get(key).stream()
                    .map(method -> String.format("%s - %s", key.toString(), method))
                    .collect(Collectors.toList()));
        }
        return flattenedPortrait;
    }

    /**
     * Looks at a method and gets any return or parameter types that match the basePackage.
     */
    private static List<Class<?>> getClassesForRecursion(final String basePackage, final Method method) {
        final List<Class<?>> classesForRecursion = new ArrayList<>();

        // Check regular return types
        if (method.getReturnType().toString().contains(basePackage)) {
            classesForRecursion.add(method.getReturnType());
        } else {
            // Check for memory return types
            if (method.getGenericReturnType() instanceof ParameterizedType) {
                final ParameterizedType type = (ParameterizedType) method.getGenericReturnType();
                Arrays.stream(type.getActualTypeArguments())
                        .filter(actualType -> actualType.getTypeName().contains(basePackage))
                        .forEach(actualType -> classesForRecursion.add((Class<?>) actualType));
            }
        }

        // Check for parameter types
        Arrays.stream(method.getParameterTypes())
                .filter(paramType -> paramType.toString().contains(basePackage))
                .forEach(classesForRecursion::add);

        return classesForRecursion;
    }
}
