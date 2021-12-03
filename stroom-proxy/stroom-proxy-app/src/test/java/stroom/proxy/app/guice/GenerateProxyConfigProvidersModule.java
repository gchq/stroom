package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;

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
import java.util.stream.Collectors;

/**
 * Generates the file {@link ProxyConfigProvidersModule} with provider methods for each injectable
 * config object
 */
public class GenerateProxyConfigProvidersModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateProxyConfigProvidersModule.class);

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
                        
                // Special case to allow ProxyPathConfig to be injected as itself or as
                // PathConfig
                @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
                @Provides
                @SuppressWarnings("unused")
                stroom.util.io.PathConfig getPathConfig(
                        final ProxyConfigProvider proxyConfigProvider) {
                    return proxyConfigProvider.getConfigObject(
                            stroom.proxy.app.ProxyPathConfig.class);
                }
            """;

    private static final String CLASS_FOOTER = """
            }
            """;

    public static void main(String[] args) {
//        final ConfigMapper configMapper = new ConfigMapper();
        final Set<String> simpleNames = new HashSet<>();
        final Map<String, List<String>> simpleNameToFullNamesMap = new HashMap<>();

        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(new ProxyConfig());
        final Set<Class<? extends AbstractConfig>> injectableClasses = proxyConfigProvider.getInjectableClasses();

        final String methodsStr = injectableClasses
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .map(clazz ->
                        buildMethod(simpleNames, simpleNameToFullNamesMap, clazz))
                .collect(Collectors.joining("\n"));

        final String header = String.format(CLASS_HEADER,
                GenerateProxyConfigProvidersModule.class.getName(),
                GenerateProxyConfigProvidersModule.class.getName());

        final String fileContent = String.join(
                "\n",
                header,
                methodsStr,
                CLASS_FOOTER);

        updateFile(fileContent);
    }

    private static String buildMethod(final Set<String> simpleNames,
                                      final Map<String, List<String>> simpleNameToFullNamesMap,
                                      final Class<? extends AbstractConfig> clazz) {
        final String simpleClassName = clazz.getSimpleName();
        // Fix the name for nested classes
        final String fullClassName = clazz.getName()
                .replace("$", ".");

        simpleNameToFullNamesMap.computeIfAbsent(simpleClassName, k -> new ArrayList<>())
                .add(fullClassName);

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
                fullClassName,
                methodNameSuffix,
                fullClassName);
    }

    private static void updateFile(final String content) {
        Path pwd = Paths.get(".")
                .toAbsolutePath()
                .normalize();

        LOGGER.debug("PWD: {}", pwd.toString());

        Path moduleFile = pwd.resolve("stroom-proxy/stroom-proxy-app/src/main/java")
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
