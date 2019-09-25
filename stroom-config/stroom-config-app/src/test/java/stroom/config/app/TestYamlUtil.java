package stroom.config.app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;


class TestYamlUtil {

    static final String EXPECTED_YAML_FILE_NAME = "expected.yaml";

    /**
     * *** IMPORTANT ***
     * If the test fails it is because you have made changes to part of the IsConfig object model
     * and the resulting generated yaml is different to what it was before. If you are happy that
     * the change to the yaml matches what you expect then run {@link GenerateExpectedYaml#main} to
     * re-generate the expected yaml file, then the test will pass. If you are not happy then re-think
     * your change to the object model.
     * *** IMPORTANT ***
     */
    @Test
    void testGeneratedYamlAgainstExpected() throws IOException {
        final String expected = Files.readString(getExpectedYamlFile());
        final String actual = getYamlFromJavaModel();

        assertThat(actual)
                .isEqualTo(expected);
    }

    static Path getExpectedYamlFile() {
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

    static String getYamlFromJavaModel() throws IOException {
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
