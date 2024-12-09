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


import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.resource.api.ResourceStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportServiceImpl extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportService importExportService;
    @Inject
    private ResourceStore resourceStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private FeedStore feedStore;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;

    @Test
    void testExport() {
        final ExplorerNode systemNode = explorerNodeService.getRoot();
        final DocRef systemDocRef = systemNode != null
                ? systemNode.getDocRef()
                : null;
        assertThat(explorerNodeService.getDescendants(systemDocRef).size()).isEqualTo(1);

        final ExplorerNode folder1 = explorerService.createFolder(
                "Root1_",
                systemNode, null);
        final ExplorerNode folder2 = explorerService.createFolder(
                "Root2_" + FileSystemTestUtil.getUniqueTestString(),
                systemNode,
                null);
        final ExplorerNode folder2child1 = explorerService.createFolder(
                "Root2_Child1_" + FileSystemTestUtil.getUniqueTestString(),
                folder2,
                null);
        final ExplorerNode folder2child2 = explorerService.createFolder(
                "Root2_Child2_" + FileSystemTestUtil.getUniqueTestString(),
                folder2,
                null);

        assertThat(explorerNodeService.getDescendants(systemDocRef).size()).isEqualTo(5);

        PipelineDoc tran1 = pipelineStore.createDocument();
        tran1.setName(FileSystemTestUtil.getUniqueTestString());
        tran1.setDescription("Description");
        tran1 = pipelineStore.writeDocument(tran1);
        explorerService.create(tran1.asDocRef(), folder1, null);

        PipelineDoc tran2 = pipelineStore.createDocument();
        tran2.setName(FileSystemTestUtil.getUniqueTestString());
        tran2.setDescription("Description");
        tran2.setParentPipeline(tran1.asDocRef());
        tran2 = pipelineStore.writeDocument(tran2);
        explorerService.create(tran2.asDocRef(), folder2, null);


        FeedDoc referenceFeed = feedStore.createDocument();
        referenceFeed.setName(FileSystemTestUtil.getUniqueTestString());
        referenceFeed.setDescription("Description");
        referenceFeed = feedStore.writeDocument(referenceFeed);
        explorerService.create(referenceFeed.asDocRef(), folder1, null);

        FeedDoc eventFeed = feedStore.createDocument();
        eventFeed.setName(FileSystemTestUtil.getUniqueTestString());
        eventFeed.setDescription("Description");
        eventFeed = feedStore.writeDocument(eventFeed);
        explorerService.create(eventFeed.asDocRef(), folder2, null);

        FeedDoc eventFeedChild = feedStore.createDocument();
        eventFeedChild.setName(FileSystemTestUtil.getUniqueTestString());
        eventFeedChild.setDescription("Description");
        eventFeedChild = feedStore.writeDocument(eventFeedChild);
        explorerService.create(eventFeedChild.asDocRef(), folder2child1, null);

        FeedDoc eventFeedChild2 = feedStore.createDocument();
        eventFeedChild2.setName(FileSystemTestUtil.getUniqueTestString());
        eventFeedChild2.setDescription("Description2");
        eventFeedChild2 = feedStore.writeDocument(eventFeedChild2);
        explorerService.create(eventFeedChild2.asDocRef(), folder2child2, null);

        final int startTranslationSize = pipelineStore.list().size();
        final int startFeedSize = feedStore.list().size();

        final ResourceKey file = resourceStore.createTempFile("Export.zip");
        final Set<DocRef> docRefs = new HashSet<>();
        docRefs.add(folder1.getDocRef());
        docRefs.add(folder2.getDocRef());

        // Export
        importExportService.exportConfig(docRefs, resourceStore.getTempFile(file));

        final ResourceKey exportConfig = resourceStore.createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, resourceStore.getTempFile(exportConfig));

        // Delete it and check
        pipelineStore.deleteDocument(tran2.asDocRef());
        assertThat(pipelineStore.list().size()).isEqualTo(startTranslationSize - 1);

        feedStore.deleteDocument(eventFeed.asDocRef());
        assertThat(feedStore.list().size()).isEqualTo(startFeedSize - 1);

        // Import
        final List<ImportState> confirmations = importExportService.importConfig(
                resourceStore.getTempFile(file),
                ImportSettings.createConfirmation(),
                new ArrayList<>());

        for (final ImportState confirmation : confirmations) {
            confirmation.setAction(true);
        }

        importExportService.importConfig(
                resourceStore.getTempFile(file),
                ImportSettings.actionConfirmation(),
                confirmations);

        assertThat(feedStore.list().size()).isEqualTo(startFeedSize);
        assertThat(pipelineStore.list().size()).isEqualTo(startTranslationSize);

        final ResourceKey fileChild = resourceStore.createTempFile("ExportChild.zip");
        final Set<DocRef> criteriaChild = new HashSet<>();
        criteriaChild.add(folder2child2.getDocRef());

        // Export
        importExportService.exportConfig(criteriaChild, resourceStore.getTempFile(fileChild));
    }
}
