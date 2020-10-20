package stroom.dist;

import stroom.config.app.AppConfig;
import stroom.config.app.YamlUtil;
import stroom.util.ConsoleColour;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenerateDistributionConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateDistributionConfig.class);

    private static final String DIST_KEY = "distribution";
    private static final String USE_VARIABLE_SUBSTITUTION_KEY = "includeEnvVarSubstitution";
    private static final String OUTPUT_FILE_NAME_KEY = "outputFileName";

    private static final Map<String, Object> DOCKER_CONTEXT = Map.of(
            DIST_KEY, "docker",
            OUTPUT_FILE_NAME_KEY, "docker/build/config.yml");

    private static final Map<String, Object> ZIP_CONTEXT = Map.of(
            DIST_KEY, "zip",
            OUTPUT_FILE_NAME_KEY, "build/release/config/config.yml");

    private static final List<Map<String, Object>> CONTEXTS = List.of(
            DOCKER_CONTEXT,
            ZIP_CONTEXT);

    public static void main(String[] args) throws IOException {
        final Jinjava jinjava = new Jinjava();

        jinjava.registerFilter(new EnvVarSubstitutionFilter());

        final Path pwd = Paths.get(".").normalize().toAbsolutePath();
        final Path stroomAppDir;
        // pwd is different when running in IJ vs in gradle build
        if (pwd.endsWith("stroom-app")) {
            stroomAppDir = pwd;
        } else {
            stroomAppDir = pwd
                    .resolve("stroom-app");
        }
        final Path configTemplateFile = stroomAppDir
                .resolve("prod.yml.jinja2")
                .normalize()
                .toAbsolutePath();

        LOGGER.info("PWD: {}", pwd);
        LOGGER.info("configTemplateFile: {}", configTemplateFile);

        Assertions.assertThat(configTemplateFile)
                .isRegularFile();

        final String configTemplate = Files.readString(configTemplateFile);

        final List<Path> generatedFiles = new ArrayList<>();

        CONTEXTS.forEach(context -> {
            LOGGER.info("======================================================================");
            LOGGER.info("Building template for {} distribution", context.get(DIST_KEY));

            final String renderedTemplate = jinjava.render(configTemplate, context);

//            LOGGER.debug("rendered\n{}", renderedTemplate);

            final Path outputFileNameFile = stroomAppDir.resolve((String) context.get(OUTPUT_FILE_NAME_KEY));
            final Path dir = outputFileNameFile.getParent();
            LOGGER.info("dir: {}", dir.normalize().toAbsolutePath());

            try {
                Files.createDirectories(outputFileNameFile.getParent());

                LOGGER.info("Writing file {}", outputFileNameFile.normalize().toAbsolutePath());
                generatedFiles.add(outputFileNameFile);

                Files.writeString(outputFileNameFile, renderedTemplate);

                verifyOutputFile(outputFileNameFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        if (generatedFiles.size() > 1) {
            for (int i = 1; i < generatedFiles.size(); i++) {
                Path file1 = generatedFiles.get(0);
                Path file2 = generatedFiles.get(i);

                unifiedDiff(file1, file2);
            }
        }
    }

    public static void unifiedDiff(final Path file1, final Path file2) {

        try (final Stream<String> expectedStream = Files.lines(file1);
             final Stream<String> actualStream = Files.lines(file2)) {

            final List<String> expectedLines = expectedStream.collect(Collectors.toList());
            final List<String> actualLines = actualStream.collect(Collectors.toList());

            final Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);

            final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
                    file1.toString(),
                    file2.toString(),
                    expectedLines,
                    patch,
                    3);

            if (!unifiedDiff.isEmpty()) {
                LOGGER.info("Comparing {} and {}",
                        FileUtil.getCanonicalPath(file1),
                        FileUtil.getCanonicalPath(file2));

                System.out.println("");
                System.out.println("================================================================================");
                unifiedDiff.forEach(diffLine -> {

                    final ConsoleColour lineColour;
                    if (diffLine.startsWith("+")) {
                        lineColour = ConsoleColour.GREEN;
                    } else if (diffLine.startsWith("-")) {
                        lineColour = ConsoleColour.RED;
                    } else {
                        lineColour = ConsoleColour.NO_COLOUR;
                    }

                    System.out.println(ConsoleColour.colourise(diffLine, lineColour));
                });
                System.out.println("================================================================================");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void verifyOutputFile(final Path configFile) throws IOException {
        // Ensures the output file can be read into the appConfig tree
        final AppConfig appConfig;
        try {
            appConfig = YamlUtil.readAppConfig(configFile);
            Assertions.assertThat(appConfig)
                    .isNotNull();
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Can't read file {} into Config object"), e);
        }
    }

    /**
     * e.g.
     * "{{ '/stroomAdmin' | envVar('ADMIN_CONTEXT_PATH') }}"
     * to produce /stroomAdmin or ${ADMIN_CONTEXT_PATH:-/stroomAdmin}
     * {{ 8080 | envVar('STROOM_APP_PORT') }}
     * to produce 8080 or ${STROOM_APP_PORT:-8080}
     *
     *depending on the value of USE_VARIABLE_SUBSTITUTION_KEY
     */
    private static class EnvVarSubstitutionFilter implements Filter {

        /**
         * @param var The value being piped into the filter
         * @param interpreter
         * @param args The filter args. One argument, the name of the env var to use for
         *             substitution.
         * @return The value as a bash style variable substitution
         */
        @Override
        public Object filter(final Object var, final JinjavaInterpreter interpreter, final String... args) {
            final boolean useEnvVarSubst = (boolean) interpreter.getContext().get(USE_VARIABLE_SUBSTITUTION_KEY);
            if (useEnvVarSubst) {
                String envVarName = args[0];
                return "${" + envVarName + ":-" + var + "}";
            } else {
                return var;
            }
        }

        @Override
        public String getName() {
            return "envVar";
        }
    }
}
