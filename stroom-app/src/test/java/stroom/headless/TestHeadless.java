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

import stroom.content.ContentPack;
import stroom.content.ContentPacks;
import stroom.test.common.util.test.ContentPackZipDownloader;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.DiffUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestHeadless {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHeadless.class);
    private static final List<ContentPack> CONTENT_PACKS = List.of(
            ContentPacks.CORE_XML_SCHEMAS_PACK,
            ContentPacks.EVENT_LOGGING_XML_SCHEMA_PACK);

    @Test
    void testArgs1() {
        Assertions.assertThatThrownBy(
                        () -> {
                            Headless.main(new String[0]);
                        }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("input")
                .hasMessageContaining("must be supplied");
    }

    @Test
    void testArgs2() {
        Assertions.assertThatThrownBy(
                        () -> {
                            Headless.main(new String[]{"input=foo"});
                        }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("output")
                .hasMessageContaining("must be supplied");
    }

    @Test
    void testArgs3() {
        Assertions.assertThatThrownBy(
                        () -> {
                            Headless.main(new String[]{
                                    "input=/tmp/input",
                                    "output=/tmp/output"});
                        }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("content")
                .hasMessageContaining("must be supplied");
    }

    @Test
    void testArgs4() {
        Assertions.assertThatThrownBy(
                        () -> {
                            Headless.main(new String[]{
                                    "input=/tmp/input",
                                    "output=/tmp/output",
                                    "content=/tmp/content"});
                        }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("tmp")
                .hasMessageContaining("must be supplied");
    }

    @Test
    void testArgs5(@TempDir Path tempDir) {
        Assertions.assertThatThrownBy(
                        () -> {
                            Headless.main(new String[]{
                                    makeDirArg(tempDir, "input"),
                                    makeArg(tempDir, "output"),
                                    makeDirArg(tempDir, "content"),
                                    makeDirArg(tempDir, "tmp")});
                        }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Content")
                .hasMessageContaining("is empty");
    }

    private String makeArg(final Path tempDir, final String arg) {
        return arg + "=" + tempDir.resolve(arg).toAbsolutePath();
    }

    private String makeDirArg(final Path tempDir, final String arg) throws IOException {
        final Path path = tempDir.resolve(arg).toAbsolutePath();
        Files.createDirectories(path);
        return arg + "=" + tempDir.resolve(arg).toAbsolutePath();
    }

    @Test
    void test(@TempDir Path newTempDir) throws IOException {
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

        // This dir holds unzipped content local to this repo
        final Path configSourceLocalDirPath = testPath.resolve("config_source_local");
        // This is where Headless will store its unzipped content
        final Path contentDirPath = tmpPath.resolve("content");
        // This is where Headless reads the input data zips from
        final Path inputDirPath = tmpPath.resolve("input");
        // This is where the pipeline output goes
        final Path outputDirPath = tmpPath.resolve("output");

        if (!Files.isDirectory(configSourceLocalDirPath)) {
            throw new RuntimeException("Can't find directory " + configSourceLocalDirPath);
        }
        Files.createDirectories(contentDirPath);
        Files.createDirectories(inputDirPath);
        Files.createDirectories(outputDirPath);

        final Path outputFilePath = outputDirPath.resolve("output");
        final Path expectedOutputFilePath = testPath.resolve("expectedOutput");
//        LOGGER.info("samplesPath: {}", samplesPath);
        LOGGER.info("outputFilePath: {}", outputFilePath);
        LOGGER.info("expectedOutputFilePath: {}", expectedOutputFilePath);

        // The dir containing the unzipped input data for the test
        final Path rawInputPath = testPath.resolve("input_source");
        // Create input zip file from the source
        Files.createDirectories(rawInputPath);
        final Path inputZipFilePath = inputDirPath.resolve("001.zip");
        LOGGER.debug("rawInputPath: {}", rawInputPath.toAbsolutePath());
        LOGGER.debug("inputFilePath: {}", inputZipFilePath.toAbsolutePath());
        Files.deleteIfExists(inputZipFilePath);
        ZipUtil.zip(inputZipFilePath, rawInputPath);

        // Copy required config into the temp dir.
        final Path rawConfigPath = tmpPath.resolve("config");
        if (Files.exists(rawConfigPath)) {
            FileUtil.deleteContents(rawConfigPath);
        }
        Files.createDirectories(rawConfigPath);

        // Add the XML schemas from content packs
        final Path downloadedContentPacks = FileSystemTestUtil.getContentPackDownloadsDir();
        LOGGER.debug("downloadedContentPacks: {}", downloadedContentPacks.toAbsolutePath());
        LOGGER.debug("rawConfigPath: {}", rawConfigPath.toAbsolutePath());
        addXmlSchemas(downloadedContentPacks, rawConfigPath);
        // Add the content that is local to this repo
        addLocalConfig(configSourceLocalDirPath, rawConfigPath);

        // Build the config zip file from the content packs and the local config
        final Path configFilePath = tmpPath.resolve("config.zip");
        LOGGER.info("configFilePath: {}", configFilePath);
        Files.deleteIfExists(configFilePath);
        ZipUtil.zip(configFilePath, rawConfigPath);

        // Run headless
        Headless.main(new String[]{
                "input=" + FileUtil.getCanonicalPath(inputDirPath),
                "output=" + FileUtil.getCanonicalPath(outputFilePath),
                "config=" + FileUtil.getCanonicalPath(configFilePath),
                "content=" + FileUtil.getCanonicalPath(contentDirPath),
                "tmp=" + FileUtil.getCanonicalPath(newTempDir)});

        final List<String> expectedLines = Files.readAllLines(expectedOutputFilePath, Charset.defaultCharset());
        final List<String> outputLines = Files.readAllLines(outputFilePath, Charset.defaultCharset());

        final boolean diffResult = DiffUtil.unifiedDiff(expectedOutputFilePath.toAbsolutePath(),
                outputFilePath.toAbsolutePath(),
                true,
                3,
                diffLines -> {
                    LOGGER.info("Comparing expected vs actual:\n{}",
                            String.join("\n", diffLines));
                });

        Assertions.assertThat(diffResult)
                .isFalse();

        // same number of lines output as expected
        assertThat(outputLines)
                .hasSize(expectedLines.size());
        // make sure all lines are present in both
        assertThat(new HashSet<>(outputLines))
                .isEqualTo(new HashSet<>(expectedLines));
    }

    private static void addLocalConfig(final Path configSourceLocalDirPath, final Path rawConfigPath)
            throws IOException {
        try (Stream<Path> pathStream = Files.list(configSourceLocalDirPath)) {
            pathStream.forEach(path -> {
                final Path name = path.getFileName();
                try {
                    FileUtil.deepCopy(
                            configSourceLocalDirPath.resolve(name),
                            rawConfigPath.resolve(name));
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private void addXmlSchemas(final Path path, final Path dest) {
        CONTENT_PACKS.forEach(contentPack -> {
            LOGGER.debug("Downloading content pack {} to {}", contentPack.getName(), path);
            final Path p = ContentPackZipDownloader.downloadContentPack(contentPack, path);
            final Path sub = p.resolve(contentPack.getPath());
            final Path destDir = dest.resolve(contentPack.getName());
            try {
                LOGGER.debug("Copying {} to {}", sub, destDir);
                FileUtil.deepCopy(sub, destDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(LogUtil.message("Error copying {} to {}", sub, destDir), e);
            }
        });
    }
}
