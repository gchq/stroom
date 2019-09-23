package stroom.config.app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;


class TestYamlUtil {

    private static final String EXPECTED_YAML_FILE_NAME = "expected.yaml";

    /**
     * Builds a fresh config object tree with all the hard coded default values
     * and generates the yaml serialised form of it, saving the result to the
     * EXPECTED_YAML_FILE_NAME file so that it can be used in
     * testGeneratedYamlAgainstExpected.
     */
    public static void main(String[] args) throws URISyntaxException, IOException {

        System.out.println(getExpectedYamlFile().toAbsolutePath());
//        final URL url = TestYamlUtil.class.getResource(EXPECTED_YAML_FILE_NAME);
        final Path expectedFile = getExpectedYamlFile();
        final String generatedYaml = getYamlFromJavaModel();
        System.out.println("Writing generated yaml to " + expectedFile.toAbsolutePath());
        Files.writeString(expectedFile, generatedYaml);
    }

    @Test
    void testGeneratedYamlAgainstExpected() throws IOException {
        final String expected = Files.readString(getExpectedYamlFile());
        final String actual = getYamlFromJavaModel();

        // *** IMPORTANT ***
        // If the test fails here it is because you have made changes to part of the IsConfig object model
        // and the resulting generated yaml is different to what it was before. If you are happy that
        // the change to the yaml matches what you expect then run the main() method above to
        // re-generate the expected yaml file, then the test will pass. If you are not happy then re-think
        // your change to the object model.
        // *** IMPORTANT ***
        assertThat(actual)
                .isEqualTo(expected);
    }

    private static Path getExpectedYamlFile() {
        final String codeSourceLocation = TestYamlUtil.class
                .getProtectionDomain().getCodeSource().getLocation().getPath();

        Path path = Paths.get(codeSourceLocation);

        while (path != null && !path.getFileName().toString().equals("stroom-config-app")) {
            path = path.getParent();
        }

        return path.resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("stroom")
                .resolve("config")
                .resolve("app")
                .resolve(EXPECTED_YAML_FILE_NAME)
                .normalize()
                .toAbsolutePath();
    }

    private static String getYamlFromJavaModel() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        YamlUtil.writeConfig(new AppConfig(), byteArrayOutputStream);
        return new String(byteArrayOutputStream.toByteArray());
    }

    /**
     * Verify dev.yml can be de-serialised into the config object model
     */
    @Test
    void testDevYaml() throws FileNotFoundException {
        loadDevYaml();
    }

    private static AppConfig loadDevYaml() throws FileNotFoundException {
        Path path = getDevYamlPath();

        try  {
            return YamlUtil.readAppConfig(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path getDevYamlPath() throws FileNotFoundException {
        // Load dev.yaml
        final String codeSourceLocation = TestYamlUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-config")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.getParent();
            path = path.resolve("stroom-app");
            path = path.resolve("dev.yml");
        }

        if (path == null) {
            throw new FileNotFoundException("Unable to find dev.yml");
        }
        return path;
    }
}
