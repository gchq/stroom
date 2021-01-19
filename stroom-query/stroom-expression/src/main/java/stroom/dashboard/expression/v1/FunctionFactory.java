/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final Map<String, Class<? extends Function>> aliasMap = new HashMap<>();
    private final Map<Class<? extends Function>, FunctionDef> functionDefMap = new HashMap<>();

    public FunctionFactory() {
        scanClassPathForFunctions();
    }

    private void scanClassPathForFunctions() {
        // Scan the class path to find all the classes with @FunctionDef
        try (ScanResult result = new ClassGraph()
                .whitelistPackages(Function.class.getPackageName())
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

                            functionDefMap.put(functionClazz, functionDef);

                            // Add the class to our alias map for each name it has
                            Stream.concat(Stream.of(functionDef.name()), Stream.of(functionDef.aliases()))
                                    .filter(Objects::nonNull)
                                    .map(String::toLowerCase)
                                    .forEach(name -> {
                                        if (aliasMap.containsKey(name)) {
                                            final Class<? extends Function> existingClass = aliasMap.get(name);
                                            throw new RuntimeException(("Name/alias [" + name +
                                                    "] for class " + clazz.getName() +
                                                    " already exists for class " +
                                                    existingClass.getName()));
                                        }
                                        aliasMap.put(name, functionClazz);
                                    });

                            LOGGER.debug("Adding function {}", functionClazz.getName());
                        }
                    });
        }
    }

    public Function create(final String functionName) {
        final Class<? extends Function> clazz = aliasMap.get(functionName.toLowerCase());
        if (clazz != null) {
            try {
                return clazz.getConstructor(String.class).newInstance(functionName);
            } catch (final NoSuchMethodException
                    | InvocationTargetException
                    | InstantiationException
                    | IllegalAccessException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        return null;
    }

    public Optional<FunctionDef> getFunctionDefinition(final Class<? extends Function> clazz) {
        return Optional.ofNullable(functionDefMap.get(clazz));
    }

    public List<FunctionDef> getFunctionDefinitions() {
        return new ArrayList<>(functionDefMap.values());
    }
}
