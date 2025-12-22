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

package stroom.query.language.functions;

import stroom.util.logging.LogUtil;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class FunctionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionFactory.class);

    // Hold them statically as we only want to scan the class path once
    private static final Map<String, Class<? extends Function>> ALIAS_MAP = new HashMap<>();
    private static final Map<Class<? extends Function>, FunctionDef> FUNCTION_DEF_MAP = new HashMap<>();

    static {
        scanClassPathForFunctions();
    }

    private static void scanClassPathForFunctions() {
//        final StringBuilder functions = new StringBuilder();

        // Scan the class path to find all the classes with @FunctionDef
        try (final ScanResult result = new ClassGraph()
                .acceptPackages(Function.class.getPackageName())
                .enableAnnotationInfo()
                .enableClassInfo()
                .ignoreClassVisibility()
                .scan()) {

            result.getClassesWithAnnotation(FunctionDef.class.getName())
                    .forEach(classInfo -> {

                        final Class<?> clazz = classInfo.loadClass();
                        if (Function.class.isAssignableFrom(clazz)) {
                            final Class<? extends Function> functionClazz = (Class<? extends Function>) clazz;

                            final FunctionDef functionDef = clazz.getAnnotation(FunctionDef.class);

                            FUNCTION_DEF_MAP.put(functionClazz, functionDef);

                            // Add the class to our alias map for each name it has
                            Stream.concat(Stream.of(functionDef.name()), Stream.of(functionDef.aliases()))
                                    .filter(Objects::nonNull)
                                    .map(String::toLowerCase)
                                    .forEach(name -> {
//                                        functions.append(name);
//                                        functions.append("|");

                                        if (ALIAS_MAP.containsKey(name)) {
                                            final Class<? extends Function> existingClass = ALIAS_MAP.get(name);
                                            throw new RuntimeException(("Name/alias [" + name +
                                                                        "] for class " + clazz.getName() +
                                                                        " already exists for class " +
                                                                        existingClass.getName()));
                                        }
                                        ALIAS_MAP.put(name, functionClazz);
                                    });


                            LOGGER.debug("Adding function {}", functionClazz.getName());
                        }
                    });
        }

//        System.out.println(functions);
    }

    public static Function create(final ExpressionContext expressionContext,
                                  final String functionName) {
        final Class<? extends Function> clazz = ALIAS_MAP.get(functionName.toLowerCase());
        if (clazz != null) {
            try {
                final Constructor<? extends Function> constructor = clazz
                        .getConstructor(ExpressionContext.class, String.class);
                try {
                    return constructor.newInstance(expressionContext, functionName);
                } catch (final InvocationTargetException
                               | InstantiationException
                               | IllegalAccessException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            } catch (final NoSuchMethodException e) {
                LOGGER.trace(e.getMessage(), e);
            }

            try {
                return clazz
                        .getConstructor(String.class)
                        .newInstance(functionName);
            } catch (final InvocationTargetException
                           | InstantiationException
                           | IllegalAccessException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (final NoSuchMethodException e) {
                throw new RuntimeException(LogUtil.message(
                        "Expecting to find a constructor like {}(expressionContext, functionName) " +
                        "of {}(functionName). {}",
                        clazz.getSimpleName(),
                        clazz.getSimpleName(),
                        LogUtil.exceptionMessage(e)), e);
            }
        }

        return null;
    }

    public static Optional<FunctionDef> getFunctionDefinition(final Class<? extends Function> clazz) {
        return Optional.ofNullable(FUNCTION_DEF_MAP.get(clazz));
    }

    public static List<FunctionDef> getFunctionDefinitions() {
        return new ArrayList<>(FUNCTION_DEF_MAP.values());
    }
}
