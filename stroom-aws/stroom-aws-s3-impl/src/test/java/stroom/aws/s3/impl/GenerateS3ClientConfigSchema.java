package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ClientConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GenerateS3ClientConfigSchema {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateS3ClientConfigSchema.class);

    public static void main(final String[] args) throws IOException {
        final Path schemaFile = getBasePath().resolve("s3config-schema.json");
        generateJsonSchema(schemaFile);
    }

    static void generateJsonSchema(final Path schemaFile) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        final JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper);

        // If you want to configure it manually:
        // JsonSchemaConfig config = JsonSchemaConfig.create(...);
        // JsonSchemaGenerator generator = new JsonSchemaGenerator(objectMapper, config);

        final JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(S3ClientConfig.class);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        LOGGER.info("Writing schema file to {}", schemaFile.toAbsolutePath());
        objectMapper.writeValue(schemaFile.toFile(), jsonSchema);
    }

    static Path getBasePath() {
        final String codeSourceLocation = GenerateS3ClientConfigSchema.class
                .getProtectionDomain().getCodeSource()
                .getLocation()
                .getPath();

        Path path = Paths.get(codeSourceLocation);

        while (path != null && !path.getFileName().toString().equals("stroom-aws-s3-impl")) {
            path = path.getParent();
        }

        return path.resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("stroom")
                .resolve("aws")
                .resolve("s3")
                .resolve("impl")
                .normalize()
                .toAbsolutePath();
    }
}
