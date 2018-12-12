/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.importexport;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomExpectedException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentPackImport {
    private static Path CONTENT_PACK_DIR;

    static {
        CONTENT_PACK_DIR = FileUtil.getTempDir().resolve(ContentPackImport.CONTENT_PACK_IMPORT_DIR);
    }

    //This is needed as you can't have to RunWith annotations
    //so this is the same as     @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private ImportExportService importExportService;
    @Mock
    private ContentPackImportConfig contentPackImportConfig;

    private Path testPack1 = CONTENT_PACK_DIR.resolve("testPack1.zip");
    private Path testPack2 = CONTENT_PACK_DIR.resolve("testPack2.zip");
    private Path testPack3 = CONTENT_PACK_DIR.resolve("testPack3.badExtension");

    @BeforeEach
    void setup() throws IOException {
        Path contentPackDir = FileUtil.getTempDir().resolve(ContentPackImport.CONTENT_PACK_IMPORT_DIR);
        Files.createDirectories(contentPackDir);

        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(contentPackDir)) {
            stream.forEach(file -> {
                try {
                    if (Files.isRegularFile(file)) {
                        Files.deleteIfExists(file);
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(String.format("Error deleting files from %s",
                            contentPackDir.toAbsolutePath().toString()), e);
                }
            });
        }
    }

    @AfterEach
    void teardown() throws IOException {
        deleteTestFiles();
    }

    private void deleteTestFiles() throws IOException {
        Files.deleteIfExists(testPack1);
        Files.deleteIfExists(testPack2);
        Files.deleteIfExists(testPack3);
    }


    @Test
    void testStartup_disabled() throws IOException {
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(false);
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, contentPackImportConfig);

        FileUtil.touch(testPack1);

        contentPackImport.startup();

        Mockito.verifyZeroInteractions(importExportService);
        assertThat(Files.exists(testPack1)).isTrue();
    }

    @Test
    void testStartup_enabledNoFiles() {
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(true);
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, contentPackImportConfig);
        contentPackImport.startup();
        Mockito.verifyZeroInteractions(importExportService);
    }

    @Test
    void testStartup_enabledThreeFiles() throws IOException {
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(true);
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, contentPackImportConfig);

        FileUtil.touch(testPack1);
        FileUtil.touch(testPack2);
        FileUtil.touch(testPack3);

        contentPackImport.startup();
        Mockito.verify(importExportService, Mockito.times(1))
                .performImportWithoutConfirmation(testPack1);
        Mockito.verify(importExportService, Mockito.times(1))
                .performImportWithoutConfirmation(testPack2);
        //not a zip extension so should not be called
        Mockito.verify(importExportService, Mockito.times(0))
                .performImportWithoutConfirmation(testPack3);

        assertThat(Files.exists(testPack1)).isFalse();

        //File should have moved into the imported dir
        assertThat(Files.exists(testPack1)).isFalse();
        assertThat(Files.exists(CONTENT_PACK_DIR.resolve(ContentPackImport.IMPORTED_DIR).resolve(testPack1.getFileName()))).isTrue();
    }

    @Test
    @StroomExpectedException(exception = RuntimeException.class)
    public void testStartup_failedImport() throws IOException {
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(true);
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, contentPackImportConfig);

        Mockito.doThrow(new RuntimeException("Error thrown by mock import service for test"))
                .when(importExportService)
                .performImportWithoutConfirmation(ArgumentMatchers.any());

        FileUtil.touch(testPack1);

        contentPackImport.startup();

        //File should have moved into the failed dir
        assertThat(Files.exists(testPack1)).isFalse();
        assertThat(Files.exists(CONTENT_PACK_DIR.resolve(ContentPackImport.FAILED_DIR).resolve(testPack1.getFileName()))).isTrue();
    }
}
