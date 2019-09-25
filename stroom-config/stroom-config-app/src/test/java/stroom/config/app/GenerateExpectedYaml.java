package stroom.config.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GenerateExpectedYaml {


    /**
     * Builds a fresh config object tree with all the hard coded default values
     * and generates the yaml serialised form of it, saving the result to the
     * EXPECTED_YAML_FILE_NAME file so that it can be used in
     * {@link TestYamlUtil#testGeneratedYamlAgainstExpected()}
     */
    public static void main(String[] args) throws IOException {

        System.out.println(TestYamlUtil.getExpectedYamlFile().toAbsolutePath());
//        final URL url = TestYamlUtil.class.getResource(EXPECTED_YAML_FILE_NAME);
        final Path expectedFile = TestYamlUtil.getExpectedYamlFile();
        final String generatedYaml = TestYamlUtil.getYamlFromJavaModel();
        System.out.println("Writing generated yaml to " + expectedFile.toAbsolutePath());
        Files.writeString(expectedFile, generatedYaml);
    }
}
