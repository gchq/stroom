package stroom.config.app;

import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.ConsoleColour;
import stroom.util.logging.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestYamlUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestYamlUtil.class);

    static final String EXPECTED_YAML_FILE_NAME = "expected.yaml";
    static final String ACTUAL_YAML_FILE_NAME = "actual.yaml";

    /**
     * *** IMPORTANT ***
     * If the test fails it is because you have made changes to part of the
     * {@link stroom.util.shared.AbstractConfig} object model
     * and the resulting generated yaml is different to what it was before. If you are happy that
     * the change to the yaml matches what you expect then run {@link GenerateExpectedYaml#main} to
     * re-generate the expected yaml file, then the test will pass. If you are not happy then re-think
     * your change to the object model.
     * *** IMPORTANT ***
     */
    @Test
    void testGeneratedYamlAgainstExpected() throws IOException {
        final Path expectedFile = getExpectedYamlFilePath();
        final Path actualFile = getActualYamlFilePath();

        final String expected = Files.readString(expectedFile);
        final String actual = getYamlFromJavaModel();

        // The expected file has already had the DW lines removed
        final List<String> expectedLines = expected.lines().collect(Collectors.toList());
        final List<String> actualLines = GenerateExpectedYaml.removeDropWizardLines(actual);

        // write the actual out so we can compare in other tools
        Files.write(actualFile, actualLines);

        final Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);

        final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
            expectedFile.toString(),
            actualFile.toString(),
            expectedLines,
            patch,
            3);

        if (!unifiedDiff.isEmpty()) {
            LOGGER.error("\n  Differences exist between the expected serialised form of AppConfig and the actual. " +
                "\n  If the difference is what you would expect based on the changes you have made to the config model " +
                "\n  then run the main() method in GenerateExpectedYaml to re-generate the expected yaml");

            System.out.println("");
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
            System.out.println(LogUtil.message("\nvimdiff {} {}", expectedFile, actualFile));
        }

        assertThat(actualLines.equals(expectedLines))
            .withFailMessage("Expected and actual YAML do not match!")
            .isEqualTo(true);
    }



    static Path getExpectedYamlFilePath() {
        return getBasePath().resolve(EXPECTED_YAML_FILE_NAME);
    }

    static Path getActualYamlFilePath() {
        return getBasePath().resolve(ACTUAL_YAML_FILE_NAME);
    }

    static Path getBasePath() {
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
                .normalize()
                .toAbsolutePath();
    }

    static String getYamlFromJavaModel() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AppConfig appConfig = new AppConfig();
        YamlUtil.writeConfig(appConfig, byteArrayOutputStream);
        return new String(byteArrayOutputStream.toByteArray());
    }

    /**
     * Verify dev.yml can be de-serialised into the config object model
     */
    @Test
    void testDevYaml() throws FileNotFoundException {
        loadYamlFile("dev.yml");
    }

    /**
     * Verify prod.yml can be de-serialised into the config object model
     */
    @Test
    void testProdYaml() throws FileNotFoundException {
        loadYamlFile("prod.yml");
    }


    private static AppConfig loadYamlFile(final String filename) throws FileNotFoundException {
        Path path = getStroomAppFile(filename);

        try  {
            return YamlUtil.readAppConfig(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path getStroomAppFile(final String filename) throws FileNotFoundException {
        final String codeSourceLocation = TestYamlUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-config")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.getParent();
            path = path.resolve("stroom-app");
            path = path.resolve(filename);
        }

        if (path == null) {
            throw new FileNotFoundException("Unable to find " + filename);
        }
        return path;
    }
}
