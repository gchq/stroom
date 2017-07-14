/*
 * Copyright 2016 Crown Copyright
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

package stroom.headless;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import stroom.test.ContentImportService.ContentPack;
import stroom.test.ContentPackDownloader;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.test.ComparisonHelper;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;
import stroom.util.shared.Version;
import stroom.util.zip.ZipUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

public class TestHeadless {
    private static final Version CORE_XML_SCHEMAS_VERSION = Version.of(1, 0);
    private static final Version EVENT_LOGGING_XML_SCHEMA_VERSION = Version.of(1, 0);

    @Test
    public void test() throws Exception {
        try {
            Path newTempDir = FileUtil.getTempDir().toPath().resolve("headless");
            StroomProperties.setOverrideProperty("stroom.temp", newTempDir.toAbsolutePath().toString(), StroomProperties.Source.TEST);

            // Make sure the new temp directory is empty.
            if (Files.isDirectory(newTempDir)) {
                FileUtils.deleteDirectory(newTempDir.toFile());
            }

            final Path base = StroomProcessTestFileUtil.getTestResourcesDir().toPath();
            final Path testPath = base.resolve("TestHeadless");
            final Path tmpPath = testPath.resolve("tmp");
            FileSystemUtil.deleteDirectory(tmpPath);
            Files.createDirectories(tmpPath);

            final Path inputDirPath = tmpPath.resolve("input");
            final Path outputDirPath = tmpPath.resolve("output");

            Files.createDirectories(inputDirPath);
            Files.createDirectories(outputDirPath);

            final Path samplesPath = base.resolve("../../../../stroom-core-server/src/test/resources/samples").toAbsolutePath();
            final Path outputFilePath = outputDirPath.resolve("output");
            final Path expectedOutputFilePath = testPath.resolve("expectedOutput");

            // Create input zip file
            final Path rawInputPath = testPath.resolve("input");
            Files.createDirectories(rawInputPath);
            final Path inputFilePath = inputDirPath.resolve("001.zip");
            Files.deleteIfExists(inputFilePath);
            ZipUtil.zip(inputFilePath.toFile(), rawInputPath.toFile());

            // Create config zip file
            final Path contentPacks = tmpPath.resolve("contentPacks");
            Files.createDirectories(contentPacks);
            importXmlSchemas(contentPacks);

            // Copy required config into the temp dir.
            final Path rawConfigPath = tmpPath.resolve("config");
            Files.createDirectories(rawConfigPath);
            final Path configUnzippedDirPath = samplesPath.resolve("config");
            FileUtils.copyDirectory(configUnzippedDirPath.toFile(), rawConfigPath.toFile());

            // Unzip the downloaded content packs into the temp dir.
            try (final Stream<Path> files = Files.list(contentPacks)) {
                files.forEach(f -> {
                    try {
                        ZipUtil.unzip(f, rawConfigPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            // Build the config zip file.
            final Path configFilePath = tmpPath.resolve("config.zip");
            Files.deleteIfExists(configFilePath);
            ZipUtil.zip(configFilePath.toFile(), rawConfigPath.toFile());

            final Headless headless = new Headless();

            headless.setConfig(configFilePath.toAbsolutePath().toString());
            headless.setInput(inputDirPath.toAbsolutePath().toString());
            headless.setOutput(outputFilePath.toAbsolutePath().toString());
            headless.setTmp(newTempDir.toAbsolutePath().toString());
            headless.run();

            final List<String> expectedLines = Files.readAllLines(expectedOutputFilePath, Charset.defaultCharset());
            final List<String> outputLines = Files.readAllLines(outputFilePath, Charset.defaultCharset());

            // same number of lines output as expected
            Assert.assertEquals(expectedLines.size(), outputLines.size());

            // make sure all lines are present in both
            Assert.assertEquals(new HashSet<>(expectedLines), new HashSet<>(outputLines));

            // content should exactly match expected file
            ComparisonHelper.compareFiles(expectedOutputFilePath.toFile(), outputFilePath.toFile());

        } finally {
            StroomProperties.removeOverrides();
        }
    }

    private void importXmlSchemas(final Path path) {
        importContentPacks(Arrays.asList(
                ContentPack.of("core-xml-schemas", CORE_XML_SCHEMAS_VERSION),
                ContentPack.of("event-logging-xml-schema", EVENT_LOGGING_XML_SCHEMA_VERSION)
        ), path);
    }

    private void importContentPacks(final List<ContentPack> packs, final Path path) {
        packs.forEach(pack -> ContentPackDownloader.downloadContentPack(pack.getName(), pack.getVersion(), path));
    }
}
