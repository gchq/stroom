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


import org.junit.jupiter.api.Test;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportState;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.zip.ZipUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportServiceImpl2 extends AbstractCoreIntegrationTest {
    @Inject
    private ImportExportService importExportService;

    @Test
    void testImportZip() throws IOException {
        final Path rootTestDir = StroomCoreServerTestFileUtil.getTestResourcesDir();
        final Path importDir = rootTestDir.resolve("samples/config");
        final Path zipFile = getCurrentTestDir().resolve(UUID.randomUUID().toString() + ".zip");

        ZipUtil.zip(zipFile, importDir, Pattern.compile("Feeds and Translations/Benchmark.*|.*Folder\\.xml"),
                Pattern.compile(".*/\\..*"));

        assertThat(Files.isRegularFile(zipFile)).isTrue();
        assertThat(Files.isDirectory(importDir)).isTrue();

        final List<ImportState> confirmList = importExportService.createImportConfirmationList(zipFile);
        assertThat(confirmList).isNotNull();

        importExportService.performImportWithoutConfirmation(zipFile);
    }
}
