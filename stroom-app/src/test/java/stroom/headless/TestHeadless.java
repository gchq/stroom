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

import stroom.content.ContentPacks;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.util.test.ContentPackZipDownloader;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class TestHeadless {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHeadless.class);

    @Test
    void test(@TempDir final Path newTempDir) throws IOException {
//        StroomProperties.setOverrideProperty(
//        "stroom.temp", FileUtil.getCanonicalPath(newTempDir), StroomProperties.Source.TEST);

        // Make sure the new temp directory is empty.
        if (Files.isDirectory(newTempDir)) {
            FileUtils.deleteDirectory(newTempDir.toFile());
        }

        final Path base = StroomHeadlessTestFileUtil.getTestResourcesDir();
        final Path testPath = base.resolve("TestHeadless");
        final Path tmpPath = testPath.resolve("tmp");
        LOGGER.info("tmpPath: {}", tmpPath);
        FileUtil.deleteDir(tmpPath);
        Files.createDirectories(tmpPath);

        final Path contentDirPath = tmpPath.resolve("content");
        final Path inputDirPath = tmpPath.resolve("input");
        final Path outputDirPath = tmpPath.resolve("output");

        Files.createDirectories(contentDirPath);
        Files.createDirectories(inputDirPath);
        Files.createDirectories(outputDirPath);

        final Path samplesPath = base.resolve("../../../../stroom-core/src/test/resources/samples")
                .toAbsolutePath()
                .normalize();
        final Path outputFilePath = outputDirPath.resolve("output");
        final Path expectedOutputFilePath = testPath.resolve("expectedOutput");
        LOGGER.info("samplesPath: {}", samplesPath);
        LOGGER.info("outputFilePath: {}", outputFilePath);
        LOGGER.info("expectedOutputFilePath: {}", expectedOutputFilePath);

        // Create input zip file
        final Path rawInputPath = testPath.resolve("input");
        LOGGER.info("rawInputPath: {}", rawInputPath);
        Files.createDirectories(rawInputPath);
        final Path inputFilePath = inputDirPath.resolve("001.zip");
        LOGGER.info("inputFilePath: {}", inputFilePath);
        Files.deleteIfExists(inputFilePath);
        ZipUtil.zip(inputFilePath, rawInputPath);

        // Copy required config into the temp dir.
        final Path rawConfigPath = tmpPath.resolve("config");
        LOGGER.info("rawConfigPath: {}", rawConfigPath);
        Files.createDirectories(rawConfigPath);
        final Path configUnzippedDirPath = samplesPath.resolve("config");
        LOGGER.info("configUnzippedDirPath: {}", configUnzippedDirPath);
        FileUtils.copyDirectory(configUnzippedDirPath.toFile(), rawConfigPath.toFile());

        // Add XML schemas.
        final Path downloadedContentPacks = FileSystemTestUtil.getContentPackDownloadsDir();
        LOGGER.info("downloadedContentPacks: {}", downloadedContentPacks);
        addXmlSchemas(downloadedContentPacks, rawConfigPath);

        // Build the config zip file.
        final Path configFilePath = tmpPath.resolve("config.zip");
        LOGGER.info("configFilePath: {}", configFilePath);
        Files.deleteIfExists(configFilePath);
        ZipUtil.zip(configFilePath, rawConfigPath);

        final Headless headless = new Headless();

        headless.setConfig(FileUtil.getCanonicalPath(configFilePath));
        headless.setContent(FileUtil.getCanonicalPath(contentDirPath));
        headless.setInput(FileUtil.getCanonicalPath(inputDirPath));
        headless.setOutput(FileUtil.getCanonicalPath(outputFilePath));
        headless.setTmp(FileUtil.getCanonicalPath(newTempDir));
        headless.run();

        final List<String> expectedLines = Files.readAllLines(expectedOutputFilePath, Charset.defaultCharset());
        final List<String> outputLines = Files.readAllLines(outputFilePath, Charset.defaultCharset());

        LOGGER.info("Comparing expected vs actual:\n\t{}\n\t{}",
                expectedOutputFilePath.toAbsolutePath(),
                outputFilePath.toAbsolutePath());

        // same number of lines output as expected
        assertThat(outputLines).hasSize(expectedLines.size());

        // make sure all lines are present in both
        assertThat(new HashSet<>(outputLines)).isEqualTo(new HashSet<>(expectedLines));

        // content should exactly match expected file
        ComparisonHelper.compareFiles(expectedOutputFilePath, outputFilePath);
    }

    private void addXmlSchemas(final Path path, final Path dest) {
        List.of(ContentPacks.CORE_XML_SCHEMAS_PACK,
                        ContentPacks.EVENT_LOGGING_XML_SCHEMA_PACK)
                .forEach(contentPack -> {
                    try {
                        final Path p = ContentPackZipDownloader.downloadContentPack(contentPack, path);
                        final Path sub = p.resolve(contentPack.getPath());
                        Files.copy(sub, dest);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }
}
