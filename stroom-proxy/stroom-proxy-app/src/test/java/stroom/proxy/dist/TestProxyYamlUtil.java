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
import stroom.proxy.app.SqsConnectorConfig;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.util.io.DiffUtil;
import stroom.util.shared.NullSafe;

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

        final String actual;
        try {
            actual = getYamlFromJavaModel();
        } catch (final Exception e) {
            throw new RuntimeException("Error getting actual", e);
        }
        if (NullSafe.isBlankString(actual)) {
            throw new RuntimeException("Actual is blank");
        }

        // The expected file has already had the DW lines removed
        final List<String> actualLines = GenerateProxyExpectedYaml.removeDropWizardLines(actual);

        // write the actual out, so we can compare in other tools
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
        final ProxyConfig proxyConfig = new ProxyConfig();

        // These list based props are empty by default, so that make it hard to see what the structure
        // of the list items is when looking at the generated yaml. Thus, we seed each one with a vanilla item.
        // This is not ideal as it means the generated yaml is not a true default, but hey ho?
        // This is also a bit of a hack adding items to lists. The lists ought to be immutable but
        // walking the whole tree to build it as you go is non-trivial.
        proxyConfig.getForwardHttpDestinations().add(new ForwardHttpPostConfig());
        proxyConfig.getForwardFileDestinations().add(new ForwardFileConfig());
        proxyConfig.getSqsConnectors().add(new SqsConnectorConfig());

        ProxyYamlUtil.writeConfig(proxyConfig, byteArrayOutputStream);
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
        final Path path = getStroomProxyAppFile(filename);

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
