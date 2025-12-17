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

package stroom.importexport;


import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportServiceImpl2 extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportService importExportService;

    @Test
    void testImportZip() throws IOException {
        final Path rootTestDir = StroomCoreServerTestFileUtil.getTestResourcesDir();
        final Path importDir = rootTestDir.resolve("samples/config");
        final Path zipFile = getCurrentTestDir().resolve(UUID.randomUUID() + ".zip");

        final Predicate<Path> filePredicate = path -> !path.equals(zipFile);
        final Predicate<String> entryPredicate = ZipUtil
                .createIncludeExcludeEntryPredicate(Pattern.compile(".*DATA_SPLITTER.*"), null);

        ZipUtil.zip(zipFile, importDir, filePredicate, entryPredicate);
        assertThat(Files.isRegularFile(zipFile)).isTrue();
        assertThat(Files.isDirectory(importDir)).isTrue();

        final List<ImportState> confirmList =
                importExportService.importConfig(zipFile, ImportSettings.createConfirmation(), new ArrayList<>());
        assertThat(confirmList).isNotNull();
        assertThat(confirmList.size()).isGreaterThan(0);

        importExportService.importConfig(zipFile, ImportSettings.auto(), new ArrayList<>());
    }
}
