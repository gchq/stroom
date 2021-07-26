package stroom.proxy.dist;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyYamlUtil;
import stroom.util.io.DiffUtil;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestProxyYamlUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyYamlUtil.class);

    static final String EXPECTED_YAML_FILE_NAME = "proxy-expected.yaml";
    static final String ACTUAL_YAML_FILE_NAME = "proxy-actual.yaml";

    /**
     * *** IMPORTANT ***
     * If the test fails it is because you have made changes to part of the
     * {@link stroom.util.shared.AbstractConfig} object model
     * and the resulting generated yaml is different to what it was before. If you are happy that
     * the change to the yaml matches what you expect then run {@link GenerateProxyExpectedYaml#main} to
     * re-generate the expected yaml file, then the test will pass. If you are not happy then re-think
     * your change to the object model.
     * *** IMPORTANT ***
     */
    @Test
    void testGeneratedYamlAgainstExpected() throws IOException {
        final Path expectedFile = getExpectedYamlFilePath();
        final Path actualFile = getActualYamlFilePath();

        final String actual = getYamlFromJavaModel();

        // The expected file has already had the DW lines removed
        final List<String> actualLines = GenerateProxyExpectedYaml.removeDropWizardLines(actual);

        // write the actual out so we can compare in other tools
        Files.write(actualFile, actualLines);

        final Consumer<List<String>> diffLinesConsumer = diffLines -> {
            LOGGER.error(
                    "\n  Differences exist between the expected serialised form of AppConfig and the actual. " +
                            "\n  If the difference is what you would expect based on the changes you have made to " +
                            "the config model " +
                            "\n  then run the main() method in GenerateExpectedYaml to re-generate the " +
                            "expected yaml\n{}",
                    String.join("\n", diffLines));

            LOGGER.info("\nvimdiff {} {}", expectedFile, actualFile);
        };

        final boolean haveDifferences = DiffUtil.unifiedDiff(
                expectedFile,
                actualFile,
                true,
                3,
                diffLinesConsumer);

        assertThat(haveDifferences)
                .withFailMessage("Expected and actual YAML do not match!")
                .isFalse();
    }

    static Path getExpectedYamlFilePath() {
        return getBasePath().resolve(EXPECTED_YAML_FILE_NAME);
    }

    static Path getActualYamlFilePath() {
        return getBasePath().resolve(ACTUAL_YAML_FILE_NAME);
    }

    static Path getBasePath() {
        final String codeSourceLocation = TestProxyYamlUtil.class
                .getProtectionDomain().getCodeSource()
                .getLocation()
                .getPath();

        Path path = Paths.get(codeSourceLocation);

        while (path != null && !path.getFileName().toString().equals("stroom-proxy-app")) {
            path = path.getParent();
        }

        return path.resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("stroom")
                .resolve("dist")
                .normalize()
                .toAbsolutePath();
    }

    static String getYamlFromJavaModel() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ProxyConfig appConfig = new ProxyConfig();
        ProxyYamlUtil.writeConfig(appConfig, byteArrayOutputStream);
        return byteArrayOutputStream.toString();
    }

    /**
     * Verify dev.yml can be de-serialised into the config object model
     */
    @Test
    void testDevYaml() throws FileNotFoundException {
        loadYamlFile("proxy-dev.yml");

        // prod.yml is tested as part of GenerateDistributionConfig
    }


    private static ProxyConfig loadYamlFile(final String filename) throws FileNotFoundException {
        Path path = getStroomProxyAppFile(filename);

        try {
            return ProxyYamlUtil.readProxyConfig(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path getStroomProxyAppFile(final String filename) throws FileNotFoundException {
        final String codeSourceLocation = TestProxyYamlUtil.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-proxy-app")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.resolve(filename);
        }

        if (path == null) {
            throw new FileNotFoundException("Unable to find " + filename);
        }
        return path;
    }
}
