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

import stroom.explorer.api.ExplorerService;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.Message;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportServiceImpl3 extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportService importExportService;
    @Inject
    private ExplorerService explorerService;

    @Test
    void testImportZip() throws IOException {
        final int BATCH_SIZE = 200;
        for (int i = 0; i < BATCH_SIZE; i++) {
            explorerService.create(
                    FeedDoc.TYPE,
                    FileSystemTestUtil.getUniqueTestString(),
                    null,
                    null);
        }
        final List<Message> msgList = new ArrayList<>();

        final Path testFile = getCurrentTestDir()
                .resolve("ExportTest" + FileSystemTestUtil.getUniqueTestString() + ".zip");
        System.err.println("Test directory: " + testFile);
        importExportService.exportConfig(null, testFile);

        assertThat(msgList.size())
                .isEqualTo(0);

        final List<String> list = ZipUtil.pathList(testFile);

        final int expectedSize = BATCH_SIZE * 2;

        assertThat(list.size())
                .isEqualTo(expectedSize);

        final List<ImportState> confirmList = importExportService.importConfig(
                testFile,
                ImportSettings.createConfirmation(),
                new ArrayList<>());

        assertThat(confirmList.size())
                .isEqualTo(BATCH_SIZE);
    }
}
