package stroom.config.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GenerateExpectedYaml {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateExpectedYaml.class);

    /**
     * Builds a fresh config object tree with all the hard coded default values
     * and generates the yaml serialised form of it, saving the result to the
     * EXPECTED_YAML_FILE_NAME file so that it can be used in
     * {@link TestYamlUtil#testGeneratedYamlAgainstExpected()}
     *
     * NOTE: This main method is called from the stroom-app gradle build so if it
     * is moved you will need to refactor that too.
     */
    public static void main(String[] args) throws IOException {

        Path outputFile;
        if (args.length > 0) {
            outputFile = Paths.get(args[0]);
        } else {
            outputFile = TestYamlUtil.getExpectedYamlFile();
        }

        Path parentDir = outputFile.getParent();

        if (!Files.isDirectory(parentDir)) {
            LOGGER.info("Creating directory {}", outputFile.toAbsolutePath());
            Files.createDirectories(parentDir);
        }

        final String generatedYaml = TestYamlUtil.getYamlFromJavaModel();
        LOGGER.info("Writing generated yaml to {}", outputFile.toAbsolutePath());
        Files.writeString(outputFile, generatedYaml);
    }
}
