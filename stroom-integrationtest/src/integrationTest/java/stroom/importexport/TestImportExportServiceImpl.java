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
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.shared.ImportState;
import stroom.explorer.ExplorerNodeService;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.resource.ResourceStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.shared.ResourceKey;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;
import java.util.List;

public class TestImportExportServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ResourceStore resourceStore;
    @Resource
    private PipelineService pipelineService;
    @Resource
    private FeedService feedService;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private ExplorerService explorerService;
    @Resource
    private ExplorerNodeService explorerNodeService;

    @Test
    public void testExport() {
        // commonTestControl.deleteDir();

        final DocRef system = explorerNodeService.getRoot().map(ExplorerNode::getDocRef).orElse(null);
        Assert.assertEquals(1, explorerNodeService.getDescendants(system).size());

        final DocRef folder1 = explorerService.create(ExplorerConstants.FOLDER, "Root1_", system, null);
        DocRef folder2 = explorerService.create(ExplorerConstants.FOLDER, "Root2_" + FileSystemTestUtil.getUniqueTestString(), system, null);
        DocRef folder2child1 = explorerService.create(ExplorerConstants.FOLDER, "Root2_Child1_" + FileSystemTestUtil.getUniqueTestString(), folder2, null);
        DocRef folder2child2 = explorerService.create(ExplorerConstants.FOLDER, "Root2_Child2_" + FileSystemTestUtil.getUniqueTestString(), folder2, null);

        Assert.assertEquals(5, explorerNodeService.getDescendants(system).size());

        final DocRef tran1Ref = explorerService.create(PipelineEntity.ENTITY_TYPE, FileSystemTestUtil.getUniqueTestString(), folder1, null);
        final PipelineEntity tran1 = pipelineService.readDocument(tran1Ref);
        tran1.setDescription("Description");
        pipelineService.save(tran1);

        final DocRef tran2Ref = explorerService.create(PipelineEntity.ENTITY_TYPE, FileSystemTestUtil.getUniqueTestString(), folder2, null);
        PipelineEntity tran2 = pipelineService.readDocument(tran2Ref);
        tran2.setDescription("Description");
        tran2.setParentPipeline(DocRefUtil.create(tran1));
        tran2 = pipelineService.save(tran2);

        final DocRef referenceFeedRef = explorerService.create(Feed.ENTITY_TYPE, FileSystemTestUtil.getUniqueTestString(), folder1, null);
        final Feed referenceFeed = feedService.readDocument(referenceFeedRef);
        referenceFeed.setDescription("Description");
        feedService.save(referenceFeed);

        final DocRef eventFeedRef = explorerService.create(Feed.ENTITY_TYPE, FileSystemTestUtil.getUniqueTestString(), folder2, null);
        Feed eventFeed = feedService.readDocument(eventFeedRef);
        eventFeed.setDescription("Description");
        // eventFeed.getReferenceFeed().add(referenceFeed);
        eventFeed = feedService.save(eventFeed);

        final DocRef eventFeedChildRef = explorerService.create(Feed.ENTITY_TYPE, FileSystemTestUtil.getUniqueTestString(), folder2child1, null);
        final Feed eventFeedChild = feedService.readDocument(eventFeedChildRef);
        eventFeedChild.setDescription("Description");
        // eventFeedChild.getReferenceFeed().add(referenceFeed);
        feedService.save(eventFeedChild);

//        final int startFolderSize = commonTestControl.countEntity(Folder.class);
        final int startTranslationSize = commonTestControl.countEntity(PipelineEntity.class);
        final int startFeedSize = commonTestControl.countEntity(Feed.class);

        final ResourceKey file = resourceStore.createTempFile("Export.zip");
        final DocRefs docRefs = new DocRefs();
        docRefs.add(folder1);
        docRefs.add(folder2);

        // Export
        importExportService.exportConfig(docRefs, resourceStore.getTempFile(file), null);

        final ResourceKey exportConfig = resourceStore.createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, resourceStore.getTempFile(exportConfig), null);

        // Delete it and check
        pipelineService.delete(tran2);
        Assert.assertEquals(startTranslationSize - 1, commonTestControl.countEntity(PipelineEntity.class));

        feedService.delete(eventFeed);
        Assert.assertEquals(startFeedSize - 1, commonTestControl.countEntity(Feed.class));

//        Assert.assertEquals(4, commonTestControl.countEntity(Folder.class));

        // Import
        final List<ImportState> confirmations = importExportService
                .createImportConfirmationList(resourceStore.getTempFile(file));

        for (final ImportState confirmation : confirmations) {
            confirmation.setAction(true);
        }

        importExportService.performImportWithConfirmation(resourceStore.getTempFile(file), confirmations);

//        Assert.assertEquals(startFolderSize, commonTestControl.countEntity(Folder.class));
        Assert.assertEquals(startFeedSize, commonTestControl.countEntity(Feed.class));
        Assert.assertEquals(startTranslationSize, commonTestControl.countEntity(PipelineEntity.class));

        final ResourceKey fileChild = resourceStore.createTempFile("ExportChild.zip");
        final DocRefs criteriaChild = new DocRefs();
        criteriaChild.add(folder2child2);

        // Export
        importExportService.exportConfig(criteriaChild, resourceStore.getTempFile(fileChild), null);
    }
}
