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

package stroom.importexport.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.ImportState;
import stroom.explorer.server.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.pipeline.server.PipelineService;
import stroom.resource.server.ResourceStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.shared.Message;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.zip.ZipUtil;

import javax.annotation.Resource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TestImportExportServiceImpl3 extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ResourceStore resourceStore;
    @Resource
    private PipelineService pipelineService;
    @Resource
    private FeedService feedService;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private ExplorerService explorerService;

    @Test
    public void testImportZip() throws Exception {
        final int BATCH_SIZE = 200;
        for (int i = 0; i < BATCH_SIZE; i++) {
            explorerService.create(Feed.ENTITY_TYPE, FileSystemTestUtil.getUniqueTestString(), null, null);
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
