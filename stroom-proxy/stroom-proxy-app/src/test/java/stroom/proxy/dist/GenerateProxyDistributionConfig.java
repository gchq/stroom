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

package stroom.proxy.dist;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyYamlUtil;
import stroom.util.io.DiffUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GenerateProxyDistributionConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateProxyDistributionConfig.class);

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

    public static void main(final String[] args) throws IOException {
        final Jinjava jinjava = new Jinjava();

        jinjava.registerFilter(new EnvVarSubstitutionFilter());

        final Path pwd = Paths.get(".")
                .normalize()
                .toAbsolutePath();
        final Path proxyAppDir;

        // pwd is different when running in IJ vs in gradle build
        if (pwd.endsWith("stroom-proxy-app")) {
            proxyAppDir = pwd;
        } else {
            proxyAppDir = pwd
                    .resolve("stroom-proxy")
                    .resolve("stroom-proxy-app");
        }

        final Path configTemplateFile = proxyAppDir
                .resolve("proxy-prod.yml.jinja2")
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

            String renderedTemplate = jinjava.render(configTemplate, context);

//            LOGGER.debug("rendered\n{}", renderedTemplate);

            final Path outputFileNameFile = proxyAppDir.resolve((String) context.get(OUTPUT_FILE_NAME_KEY));
            final Path dir = outputFileNameFile.getParent();
            LOGGER.info("dir: {}", dir.normalize().toAbsolutePath());

            try {
                Files.createDirectories(outputFileNameFile.getParent());

                LOGGER.info("Writing file {}", outputFileNameFile.normalize().toAbsolutePath());
                generatedFiles.add(outputFileNameFile);

                // The conditional jinja2 blocks leave extra blank lines so remove them all
                // then replace lines with an empty comment with an empty line so that
                // we can still have empty lines where we want.
                renderedTemplate = renderedTemplate
                        .replaceAll("(?m)^\\s*\\n", "") // remove empty lines
                        .replaceAll("(?m)^\\s*#\\s*$", ""); // clear lines with empty comment

                Files.writeString(outputFileNameFile, renderedTemplate);

                LOGGER.info("Verifying {} can be read into Config tree",
                        outputFileNameFile.toAbsolutePath().normalize());
                LOGGER.info("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
                verifyOutputFile(outputFileNameFile);
                LOGGER.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        });

        if (generatedFiles.size() > 1) {
            for (int i = 1; i < generatedFiles.size(); i++) {
                final Path file1 = generatedFiles.get(0);
                final Path file2 = generatedFiles.get(i);

                outputUnifiedDiff(file1, file2);
            }
        }
    }

    public static void outputUnifiedDiff(final Path file1, final Path file2) {

        DiffUtil.unifiedDiff(
                file1,
                file2,
                true,
                3,
                diffLines ->
                        LOGGER.info("Comparing {} and {}\n{}",
                                FileUtil.getCanonicalPath(file1),
                                FileUtil.getCanonicalPath(file2),
                                String.join("\n", diffLines)));
        LOGGER.info("vimdiff {} {}",
                FileUtil.getCanonicalPath(file1),
                FileUtil.getCanonicalPath(file2));

    }

    private static void verifyOutputFile(final Path configFile) throws IOException {
        // Ensures the output file can be read into the appConfig tree
        final ProxyConfig proxyConfig;
        try {
            proxyConfig = ProxyYamlUtil.readProxyConfig(configFile);
            Assertions.assertThat(proxyConfig)
                    .isNotNull();
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Can't read file {} into Config object"), e);
        }
    }

    /**
     * e.g.
     * "{{ '/stroomAdmin' | envVar('ADMIN_CONTEXT_PATH') }}"
     * to produce /stroomAdmin or ${ADMIN_CONTEXT_PATH:-/stroomAdmin}
     * {{ 8080 | envVar('STROOM_APP_PORT') }}
     * to produce 8080 or ${STROOM_APP_PORT:-8080}
     * <p>
     * depending on the value of USE_VARIABLE_SUBSTITUTION_KEY
     */
    private static class EnvVarSubstitutionFilter implements Filter {

        /**
         * @param var         The value being piped into the filter
         * @param interpreter
         * @param args        The filter args. One argument, the name of the env var to use for
         *                    substitution.
         * @return The value as a bash style variable substitution
         */
        @Override
        public Object filter(final Object var, final JinjavaInterpreter interpreter, final String... args) {
            final boolean useEnvVarSubst = (boolean) interpreter.getContext().get(USE_VARIABLE_SUBSTITUTION_KEY);
            if (useEnvVarSubst) {
                final String envVarName = args[0];
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
