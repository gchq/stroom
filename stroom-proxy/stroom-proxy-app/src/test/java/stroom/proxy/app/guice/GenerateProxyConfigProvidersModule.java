package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyPathConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoDbConfig;
import stroom.proxy.repo.RepoConfig;
import stroom.proxy.repo.RepoDbConfig;
import stroom.util.io.PathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

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
 * Generates the file {@link ProxyConfigProvidersModule} with provider methods for each injectable
 * config object
 */
public class GenerateProxyConfigProvidersModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateProxyConfigProvidersModule.class);

    static final String THROWING_METHOD_SUFFIX = "ButThrow";

    private static final String CLASS_HEADER = """
            package stroom.proxy.app.guice;

            import com.google.inject.AbstractModule;
            import com.google.inject.Provides;

            import javax.annotation.processing.Generated;

            /**
             * IMPORTANT - This whole file is generated using
             * {@link %s}
             * DO NOT edit it directly
             */
            @Generated("%s")
            public class ProxyConfigProvidersModule extends AbstractModule {
            """;

    private static final String CLASS_FOOTER = "}\n";

    private static final String SEPARATOR = """
                // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            """;

    public static void main(String[] args) throws IOException {
//        final ConfigMapper configMapper = new ConfigMapper();
        final Set<String> simpleNames = new HashSet<>();
        final Map<String, List<String>> simpleNameToFullNamesMap = new HashMap<>();

        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(new ProxyConfig());
        final Set<Class<? extends AbstractConfig>> injectableClasses = proxyConfigProvider.getInjectableClasses();

        final String header = String.format(CLASS_HEADER,
                GenerateProxyConfigProvidersModule.class.getName(),
                GenerateProxyConfigProvidersModule.class.getName());

        // Special case to allow ProxyPathConfig to be injected as itself or as
        // PathConfig
        final String pathConfigMethodStr = buildMethod(
                simpleNames,
                simpleNameToFullNamesMap,
                ProxyPathConfig.class,
                PathConfig.class);
        final String repoConfigMethodStr = buildMethod(
                simpleNames,
                simpleNameToFullNamesMap,
                ProxyRepoConfig.class,
                RepoConfig.class);
        final String repoDbConfigMethodStr = buildMethod(
                simpleNames,
                simpleNameToFullNamesMap,
                ProxyRepoDbConfig.class,
                RepoDbConfig.class);

        final String methodsStr = injectableClasses
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .map(clazz ->
                        buildMethod(simpleNames, simpleNameToFullNamesMap, clazz))
                .collect(Collectors.joining("\n"));

        final Predicate<String> packageNameFilter = name ->
                name.startsWith("stroom") && !name.contains("shaded");

        // Methods that throw for non injectable config
        // We don't want the risk of a dev accidentally injecting a NotInjectable config
        // with the resulting confusion it will cause when it has the wrong values so make
        // providers that always throw.
        final ClassLoader classLoader = GenerateProxyConfigProvidersModule.class.getClassLoader();
        final String notInjectableMethodsStr = ClassPath.from(classLoader)
                .getAllClasses()
                .stream()
                .filter(classInfo -> packageNameFilter.test(classInfo.getPackageName()))
                .map(ClassInfo::load)
                .filter(clazz -> clazz.isAnnotationPresent(NotInjectableConfig.class))
                .filter(AbstractConfig.class::isAssignableFrom)
                .filter(clazz -> IsProxyConfig.class.isAssignableFrom(clazz))
                .map(clazz ->
                        buildThrowingMethod(simpleNames, simpleNameToFullNamesMap,
                                (Class<? extends AbstractConfig>) clazz))
                .collect(Collectors.joining("\n"));

        final String fileContent = String.join(
                "\n",
                header,
                pathConfigMethodStr,
                repoConfigMethodStr,
                repoDbConfigMethodStr,
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
        int i = 2;
        while (simpleNames.contains(methodNameSuffix)) {
            LOGGER.warn("Simple class name {} is used by multiple classes {}",
                    methodNameSuffix,
                    simpleNameToFullNamesMap.get(simpleClassName));
            methodNameSuffix = simpleClassName + i++;
        }
        simpleNames.add(methodNameSuffix);

        final String template = """
                    @Generated("%s")
                    @Provides
                    @SuppressWarnings("unused")
                    %s get%s(
                            final ProxyConfigProvider proxyConfigProvider) {
                        return proxyConfigProvider.getConfigObject(
                                %s.class);
                    }
                """;

        return String.format(
                template,
                GenerateProxyConfigProvidersModule.class.getName(),
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
                .replace("$", ".");

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
                            final ProxyConfigProvider proxyConfigProvider) {
                        throw new UnsupportedOperationException(
                                "%s cannot be injected directly. "
                                        + "Inject a config class that uses it or one of its sub-class instead.");
                    }
                """;

        return String.format(
                template,
                GenerateProxyConfigProvidersModule.class.getName(),
                fullClassName,
                methodNameSuffix,
                THROWING_METHOD_SUFFIX,
                fullClassName);

    }

    private static void updateFile(final String content) {
        Path pwd = Paths.get(".")
                .toAbsolutePath()
                .normalize();

        LOGGER.debug("PWD: {}", pwd.toString());

        final Path moduleFile = pwd.resolve("stroom-proxy/stroom-proxy-app/src/main/java")
                .resolve(ProxyConfigProvidersModule.class.getName().replace(".", File.separator) + ".java")
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

        } catch (IOException e) {
            throw new RuntimeException("Error reading content of " + moduleFile);
        }
    }
}
