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

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.DocRefs;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.streamstore.FeedEntityService;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.PipelineStore;
import stroom.resource.ResourceStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.shared.Message;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.zip.ZipUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TestImportExportServiceImpl3 extends AbstractCoreIntegrationTest {
    @Inject
    private ImportExportService importExportService;
    @Inject
    private ResourceStore resourceStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private FeedEntityService feedService;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private ExplorerService explorerService;

    @Test
    public void testImportZip() throws IOException {
        final int BATCH_SIZE = 200;
        for (int i = 0; i < BATCH_SIZE; i++) {
            explorerService.create(FeedDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), null, null);
        }
        final List<Message> msgList = new ArrayList<>();

        final Path testFile = getCurrentTestDir().resolve("ExportTest" + FileSystemTestUtil.getUniqueTestString() + ".zip");

        final DocRefs docRefs = new DocRefs();
        docRefs.add(ExplorerConstants.ROOT_DOC_REF);

        importExportService.exportConfig(null, testFile, msgList);

        Assert.assertEquals(0, msgList.size());

        final List<String> list = ZipUtil.pathList(testFile);

        // Expected size is 1 greater than batch size because it should contain the parent folder for the feeds.
        final int expectedSize = BATCH_SIZE * 2;

        Assert.assertEquals(expectedSize, list.size());

        final List<ImportState> confirmList = importExportService.createImportConfirmationList(testFile);

        Assert.assertEquals(BATCH_SIZE, confirmList.size());
    }
}
