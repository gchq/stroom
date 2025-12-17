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

package stroom.config.app;

import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class GenerateExpectedYaml {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateExpectedYaml.class);

    private static final String APP_CONFIG = "appConfig";

    private static final String HEADER = """
            # This file is generated based on all the default configuration values that are built into stroom.
            # It serves as an example of the structure of the full configuration tree.
            # If any configuration item is not explicitly set then these defaults will be used instead.
            # Some configuration items are expected to set,
            # e.g. appConfig.commonDbDetails.connection.jdbcDriverUrl,
            # but most can be left with their default values.""";

    /**
     * Builds a fresh config object tree with all the hard coded default values
     * and generates the yaml serialised form of it, saving the result to the
     * EXPECTED_YAML_FILE_NAME file so that it can be used in
     * {@link TestStroomYamlUtil#testGeneratedYamlAgainstExpected()}
     * <p>
     * NOTE: This main method is called from the stroom-app gradle build so if it
     * is moved you will need to refactor that too.
     */
    public static void main(final String[] args) throws IOException {

        final Path defaultsFile;
        final Path schemaFile;
        if (args.length == 2) {
            defaultsFile = Paths.get(args[0]);
            schemaFile = Paths.get(args[1]);
        } else {
            defaultsFile = TestStroomYamlUtil.getExpectedYamlFilePath();
            schemaFile = null;
        }

        final Path parentDir = defaultsFile.getParent();

        if (!Files.isDirectory(parentDir)) {
            LOGGER.info("Creating directory {}", defaultsFile.toAbsolutePath());
            Files.createDirectories(parentDir);
        }

        final String generatedYaml = TestStroomYamlUtil.getYamlFromJavaModel();


        final List<String> outputLines;
        if (args.length > 0) {
            // called for a specific output location so add a header

            outputLines = generatedYaml.replace("---", "---\n" + HEADER)
                    .lines()
                    .collect(Collectors.toList());
        } else {
            // called manually for TestYamlUtil so don't modify the content else it will break the test
            outputLines = removeDropWizardLines(generatedYaml);
        }

        LOGGER.info("Writing generated yaml to {}", defaultsFile.toAbsolutePath());
        Files.write(defaultsFile, outputLines);

        if (!generatedYaml.contains(APP_CONFIG + ":")) {
            throw new RuntimeException(LogUtil.message("Expecting to find {} in {}",
                    APP_CONFIG + ":", defaultsFile.normalize().toAbsolutePath().toString()));
        }

        if (schemaFile != null) {
            generateJsonSchema(schemaFile);
        }
    }

    public static List<String> removeDropWizardLines(final String value) {
        return value.lines()
                .sequential()
                .dropWhile(line -> !line.startsWith(APP_CONFIG + ":"))
                .takeWhile(line -> line.startsWith(APP_CONFIG + ":") || line.startsWith("  "))
                .collect(Collectors.toList());
    }

    static void generateJsonSchema(final Path schemaFile) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        final JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper);

        // If you want to configure it manually:
        // JsonSchemaConfig config = JsonSchemaConfig.create(...);
        // JsonSchemaGenerator generator = new JsonSchemaGenerator(objectMapper, config);

        final JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(AppConfig.class);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        LOGGER.info("Writing schema file to {}", schemaFile.toAbsolutePath());
        objectMapper.writeValue(schemaFile.toFile(), jsonSchema);
    }
}
