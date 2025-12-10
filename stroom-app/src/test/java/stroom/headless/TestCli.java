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

package stroom.headless;


import stroom.test.common.ComparisonHelper;
import stroom.util.io.FileUtil;
import stroom.util.zip.ZipUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled // Until we figure out what Cli is actually for
class TestCli {
//    private static final Version CORE_XML_SCHEMAS_VERSION = Version.of(1, 0);
//    private static final Version EVENT_LOGGING_XML_SCHEMA_VERSION = Version.of(1, 0);

    @Test
    void test() throws IOException {
//            Path newTempDir = FileUtil.getTempDir().resolve("headless");
//            StroomProperties.setOverrideProperty(
//            "stroom.temp", FileUtil.getCanonicalPath(newTempDir), StroomProperties.Source.TEST);
//
//            // Make sure the new temp directory is empty.
//            if (Files.isDirectory(newTempDir)) {
//                FileUtils.deleteDirectory(newTempDir.toFile());
//            }

        final Path base = StroomHeadlessTestFileUtil.getTestResourcesDir();
        final Path testPath = base.resolve("TestHeadless");
        final Path outputPath = testPath.resolve("output");
        FileUtil.deleteDir(outputPath);
        Files.createDirectories(outputPath);

//            StroomProperties.setOverrideProperty(
//            "stroom.temp", FileUtil.getCanonicalPath(outputPath), StroomProperties.Source.TEST);

        final Path contentDirPath = testPath.resolve("content");
        final Path inputDirPath = testPath.resolve("input");

        Files.createDirectories(contentDirPath);
        Files.createDirectories(inputDirPath);

//            final Path samplesPath = base.resolve(
//            "../../../../stroom-core/src/test/resources/samples").toAbsolutePath().normalize();
        final Path errorFilePath = outputPath.resolve("error.log");
        final Path expectedOutputFilePath = testPath.resolve("expectedOutput");

        // Create input zip file
        final Path inputSourcePath = testPath.resolve("input_source");
        Files.createDirectories(inputSourcePath);
        final Path inputFilePath = inputDirPath.resolve("001.zip");
        Files.deleteIfExists(inputFilePath);
        ZipUtil.zip(inputFilePath, inputSourcePath);

        final Path outputFilePath = outputPath.resolve("output.log");

//            // Create config zip file
//            final Path contentPacks = tmpPath.resolve("contentPacks");
//            Files.createDirectories(contentPacks);
//            importXmlSchemas(contentPacks);
//
//            // Copy required config into the temp dir.
//            final Path rawConfigPath = tmpPath.resolve("config");
//            Files.createDirectories(rawConfigPath);
//            final Path configUnzippedDirPath = samplesPath.resolve("config");
//            FileUtils.copyDirectory(configUnzippedDirPath.toFile(), rawConfigPath.toFile());
//
//            // Unzip the downloaded content packs into the temp dir.
//            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(contentPacks)) {
//                stream.forEach(file -> {
//                    try {
//                        ZipUtil.unzip(file, rawConfigPath);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                });
//            } catch (final IOException e) {
//                throw new UncheckedIOException(e);
//            }
//
//            // Build the config zip file.
//            final Path configFilePath = tmpPath.resolve("config.zip");
//            Files.deleteIfExists(configFilePath);
//            ZipUtil.zip(configFilePath, rawConfigPath);

        final Cli cli = new Cli();

//            cli.setConfig(FileUtil.getCanonicalPath(configFilePath));
        cli.setContent(FileUtil.getCanonicalPath(contentDirPath));
        cli.setInput(FileUtil.getCanonicalPath(inputDirPath));
        cli.setError(FileUtil.getCanonicalPath(errorFilePath));
        cli.setTmp(FileUtil.getCanonicalPath(outputPath));
        cli.run();

        final List<String> expectedLines = Files.readAllLines(expectedOutputFilePath, Charset.defaultCharset());
        final List<String> outputLines = Files.readAllLines(outputFilePath, Charset.defaultCharset());
        final List<String> errorLines = Files.readAllLines(errorFilePath, Charset.defaultCharset());

        // same number of lines output as expected
        assertThat(outputLines).hasSize(expectedLines.size());
        assertThat(errorLines).isEmpty();

        // make sure all lines are present in both
        assertThat(new HashSet<>(outputLines)).isEqualTo(new HashSet<>(expectedLines));

        // content should exactly match expected file
        ComparisonHelper.compareFiles(expectedOutputFilePath, outputFilePath);
    }

//    private void importXmlSchemas(final Path path) {
//        importContentPacks(Arrays.asList(
//                ContentPack.of("core-xml-schemas", CORE_XML_SCHEMAS_VERSION),
//                ContentPack.of("event-logging-xml-schema", EVENT_LOGGING_XML_SCHEMA_VERSION)
//        ), path);
//    }
//
//    private void importContentPacks(final List<ContentPack> packs, final Path path) {
//        packs.forEach(pack -> ContentPackDownloader.downloadContentPack(pack.getName(), pack.getVersion(), path));
//    }
}
