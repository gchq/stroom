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

package stroom.importexport.server;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Test;

import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.EntityActionConfirmation;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.FolderService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.resource.server.ResourceStore;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.zip.ZipUtil;

public class TestImportExportServiceImpl3 extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ResourceStore resourceStore;
    @Resource
    private FolderService folderService;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private FeedService feedService;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;

    @Test
    public void testImportZip() throws Exception {
        Feed feed = null;

        final int BATCH_SIZE = 200;
        for (int i = 0; i < BATCH_SIZE; i++) {
            feed = commonTestScenarioCreator.createSimpleFeed();
        }
        final List<String> msgList = new ArrayList<>();

        final File testFile = new File(getCurrentTestDir(),
                "ExportTest" + FileSystemTestUtil.getUniqueTestString() + ".zip");

        final FindFolderCriteria criteria = new FindFolderCriteria();
        criteria.getFolderIdSet().add(feed.getFolder());

        importExportService.exportConfig(criteria, testFile, false, msgList);

        Assert.assertEquals(0, msgList.size());

        final List<String> list = ZipUtil.pathList(testFile);

        // Expected size is 1 greater than batch size because it should contain the parent folder for the feeds.
        final int expectedSize = BATCH_SIZE + 1;

        Assert.assertEquals(expectedSize, list.size());

        final List<EntityActionConfirmation> confirmList = importExportService.createImportConfirmationList(testFile);

        Assert.assertEquals(expectedSize, confirmList.size());
    }
}
