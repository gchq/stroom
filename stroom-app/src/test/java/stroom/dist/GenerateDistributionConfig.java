package stroom.dist;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class GenerateDistributionConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateDistributionConfig.class);

    private static final String DIST_KEY = "distribution";
    private static final String USE_VARIABLE_SUBSTITUTION_KEY = "includeEnvVarSubstitution";
    private static final String ROOT_PATH_KEY = "rootPath";
    private static final String OUTPUT_FILE_NAME_KEY = "outputFileName";

    private static final Map<String, Object> DOCKER_CONTEXT = Map.of(
            DIST_KEY, "docker",
//            ROOT_PATH_KEY, "/stroom/",
//            USE_VARIABLE_SUBSTITUTION_KEY, true,
            OUTPUT_FILE_NAME_KEY, "docker/build/prod.yml"
    );

    private static final Map<String, Object> ZIP_CONTEXT = Map.of(
            DIST_KEY, "zip",
//            ROOT_PATH_KEY, "",
//            USE_VARIABLE_SUBSTITUTION_KEY, false,
            OUTPUT_FILE_NAME_KEY, "build/release/config/config.yml"
    );

    private static final List<Map<String, Object>> CONTEXTS = List.of(
            DOCKER_CONTEXT,
            ZIP_CONTEXT
    );

    public static void main(String[] args) throws IOException {
        final Jinjava jinjava = new Jinjava();

        jinjava.registerFilter(new EnvVarSubstitutionFilter());

        final Path pwd = Paths.get(".").normalize().toAbsolutePath();
        final Path stroomAppDir = Paths.get(".")
                .resolve("stroom-app");
        final Path configTemplateFile = stroomAppDir
                .resolve("prod.yml.jinja2").normalize().toAbsolutePath();

        LOGGER.info("PWD: {}", pwd);
        LOGGER.info("configTemplateFile: {}", pwd);

        Assertions.assertThat(configTemplateFile)
                .isRegularFile();

        final String configTemplate = Files.readString(configTemplateFile);

        CONTEXTS.forEach(context -> {
            LOGGER.info("======================================================================");
            LOGGER.info("Building template for {} distribution", context.get(DIST_KEY));

            final String renderedTemplate = jinjava.render(configTemplate, context);

            LOGGER.info("rendered\n{}", renderedTemplate);

            final Path outputFileNameFile = stroomAppDir.resolve((String) context.get(OUTPUT_FILE_NAME_KEY));
            final Path dir = outputFileNameFile.getParent();
            LOGGER.info("dir: {}", dir.normalize().toAbsolutePath());

            try {
                Files.createDirectories(outputFileNameFile.getParent());

                LOGGER.info("Writing file {}", outputFileNameFile.normalize().toAbsolutePath());

                Files.writeString(outputFileNameFile, renderedTemplate);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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
