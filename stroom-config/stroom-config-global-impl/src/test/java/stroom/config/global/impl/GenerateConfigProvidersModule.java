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

package stroom.config.global.impl;

import stroom.security.impl.StroomOpenIdConfig;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generates the file {@link ConfigProvidersModule} with provider methods for each injectable
 * config object
 */
public class GenerateConfigProvidersModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateConfigProvidersModule.class);

    static final String THROWING_METHOD_SUFFIX = "ButThrow";

    // extra impl => iface mappings where we map a config class to another iface
    // (Stroom)PathConfig is not bound in ConfigProvidersModule, it is bound in AppConfigModule
    static final Map<Class<? extends AbstractConfig>, Class<? extends AbstractConfig>> CUSTOM_CLASS_MAPPINGS = Map.of(
            StroomOpenIdConfig.class, AbstractOpenIdConfig.class
    );

    private static final String CLASS_HEADER = """
            package stroom.config.global.impl;

            import com.google.inject.AbstractModule;
            import com.google.inject.Provides;

            import javax.annotation.processing.Generated;

            /**
             * IMPORTANT - This whole file is generated using
             * {@link %s}
             * DO NOT edit it directly
             */
            @Generated("%s")
            public class ConfigProvidersModule extends AbstractModule {
            """;

    private static final String CLASS_FOOTER = "}\n";

    private static final String SEPARATOR = """
                // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            """;

    public static void main(final String[] args) throws IOException {
        final ConfigMapper configMapper = new ConfigMapper();
        final Set<String> simpleNames = new HashSet<>();
        final Map<String, List<String>> simpleNameToFullNamesMap = new HashMap<>();

        final String header = String.format(CLASS_HEADER,
                GenerateConfigProvidersModule.class.getName(),
                GenerateConfigProvidersModule.class.getName());

        // Filter out the DB ones as they are bound separately.
        final String methodsStr = configMapper.getInjectableConfigClasses()
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .map(clazz -> {
                    final StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(buildMethod(simpleNames, simpleNameToFullNamesMap, clazz));
                    NullSafe.consume(CUSTOM_CLASS_MAPPINGS.get(clazz), interfaceClass ->
                            stringBuilder
                                    .append("\n")
                                    .append("    // Binding ")
                                    .append(clazz.getSimpleName())
                                    .append(" to additional interface ")
                                    .append(interfaceClass.getSimpleName())
                                    .append("\n")
                                    .append(buildMethod(
                                            simpleNames,
                                            simpleNameToFullNamesMap,
                                            clazz,
                                            interfaceClass)));
                    return stringBuilder.toString();
                })
                .collect(Collectors.joining("\n"));

        final Predicate<String> packageNameFilter = name ->
                name.startsWith("stroom") && !name.contains("shaded");

        // Methods that throw for non injectable config
        // We don't want the risk of a dev accidentally injecting a NotInjectable config
        // with the resulting confusion it will cause when it has the wrong values so make
        // providers that always throw.
        // TODO 16/12/2021 AT: Change to use IsStroomConfig in >=7.1
        final ClassLoader classLoader = GenerateConfigProvidersModule.class.getClassLoader();
        final String notInjectableMethodsStr = ClassPath.from(classLoader)
                .getAllClasses()
                .stream()
                .filter(classInfo -> packageNameFilter.test(classInfo.getPackageName()))
                .map(ClassInfo::load)
                .filter(clazz -> clazz.isAnnotationPresent(NotInjectableConfig.class))
                .filter(AbstractConfig.class::isAssignableFrom)
                .filter(clazz -> !IsProxyConfig.class.isAssignableFrom(clazz))
                .sorted(Comparator.comparing(Class::getName))
                .map(clazz ->
                        buildThrowingMethod(simpleNames, simpleNameToFullNamesMap,
                                (Class<? extends AbstractConfig>) clazz))
                .collect(Collectors.joining("\n"));

        final String fileContent = String.join(
                "\n",
                header,
                SEPARATOR,
                methodsStr,
                """
                            // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                        """,
                notInjectableMethodsStr,
                CLASS_FOOTER);

        updateFile(fileContent);
    }

    private static <T extends AbstractConfig> String buildMethod(
            final Set<String> simpleNames,
            final Map<String, List<String>> simpleNameToFullNamesMap,
            final Class<T> instanceClass) {
        return buildMethod(simpleNames, simpleNameToFullNamesMap, instanceClass, instanceClass);
    }

    private static <T extends AbstractConfig, I> String buildMethod(
            final Set<String> simpleNames,
            final Map<String, List<String>> simpleNameToFullNamesMap,
            final Class<T> instanceClass,
            final Class<I> returnClass) {

        final String simpleClassName = returnClass.getSimpleName();
        final String fullInstanceClassName = instanceClass.getCanonicalName();
        final String fullReturnClassName = returnClass.getCanonicalName();

        simpleNameToFullNamesMap.computeIfAbsent(simpleClassName, k -> new ArrayList<>())
                .add(fullReturnClassName);

        String methodNameSuffix = simpleClassName;
        int i = 1;
        while (simpleNames.contains(methodNameSuffix)) {
            methodNameSuffix = simpleClassName + ++i;
        }
        if (i != 1) {
            LOGGER.warn("Simple class name {} is used by multiple classes {}, using method name suffix {}",
                    simpleClassName,
                    simpleNameToFullNamesMap.get(simpleClassName),
                    methodNameSuffix);
        }
        simpleNames.add(methodNameSuffix);

        final String template = """
                    @Generated("%s")
                    @Provides
                    @SuppressWarnings("unused")
                    %s get%s(
                            final ConfigMapper configMapper) {
                        return configMapper.getConfigObject(
                                %s.class);
                    }
                """;
        return String.format(
                template,
                GenerateConfigProvidersModule.class.getName(),
                fullReturnClassName,
                methodNameSuffix,
                fullInstanceClassName);
    }

    private static String buildThrowingMethod(final Set<String> simpleNames,
                                              final Map<String, List<String>> simpleNameToFullNamesMap,
                                              final Class<? extends AbstractConfig> clazz) {
        final String simpleClassName = clazz.getSimpleName();
        // Fix the name for nested classes
        final String fullClassName = clazz.getName()
                .replace('$', '.');

        simpleNameToFullNamesMap.computeIfAbsent(simpleClassName, k -> new ArrayList<>())
                .add(fullClassName);

        String methodNameSuffix = simpleClassName;
        int i = 1;
        while (simpleNames.contains(methodNameSuffix)) {
            methodNameSuffix = simpleClassName + ++i;
        }
        if (i != 1) {
            LOGGER.warn("Simple class name {} is used by multiple classes {}, using method name suffix {}",
                    simpleClassName,
                    simpleNameToFullNamesMap.get(simpleClassName),
                    methodNameSuffix);
        }
        simpleNames.add(methodNameSuffix);

        final String template = """
                    @Generated("%s")
                    @Provides
                    @SuppressWarnings("unused")
                    %s get%s%s(
                            final ConfigMapper configMapper) {
                        throw new UnsupportedOperationException(
                                "%s cannot be injected directly. "
                                        + "Inject a config class that uses it or one of its sub-class instead.");
                    }
                """;

        return String.format(
                template,
                GenerateConfigProvidersModule.class.getName(),
                fullClassName,
                methodNameSuffix,
                THROWING_METHOD_SUFFIX,
                fullClassName);

    }

    private static void updateFile(final String content) {
        final Path pwd = Paths.get(".")
                .toAbsolutePath()
                .normalize();

        LOGGER.debug("PWD: {}", pwd.toString());

        final Path moduleFile = pwd.resolve("stroom-config/stroom-config-global-impl/src/main/java")
                .resolve(ConfigProvidersModule.class.getName().replace(".", File.separator) + ".java")
                .normalize();

        if (!Files.isRegularFile(moduleFile)) {
            throw new RuntimeException("Can't find " + moduleFile.toAbsolutePath());
        }

        if (!Files.isWritable(moduleFile)) {
            throw new RuntimeException("File is not writable" + moduleFile.toAbsolutePath());
        }

        try {
            LOGGER.info("Writing file " + moduleFile.toAbsolutePath());
            Files.writeString(moduleFile, content);

        } catch (final IOException e) {
            throw new RuntimeException("Error reading content of " + moduleFile);
        }
    }
}
