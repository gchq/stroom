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
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.resource.api.ResourceStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
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
        final DocRef system = explorerNodeService.getRoot().map(ExplorerNode::getDocRef).orElse(null);
        assertThat(explorerNodeService.getDescendants(system).size()).isEqualTo(1);

        final DocRef folder1 = explorerService.create(ExplorerConstants.FOLDER, "Root1_", system, null);
        DocRef folder2 = explorerService.create(ExplorerConstants.FOLDER, "Root2_" + FileSystemTestUtil.getUniqueTestString(), system, null);
        DocRef folder2child1 = explorerService.create(ExplorerConstants.FOLDER, "Root2_Child1_" + FileSystemTestUtil.getUniqueTestString(), folder2, null);
        DocRef folder2child2 = explorerService.create(ExplorerConstants.FOLDER, "Root2_Child2_" + FileSystemTestUtil.getUniqueTestString(), folder2, null);

        assertThat(explorerNodeService.getDescendants(system).size()).isEqualTo(5);

        final DocRef tran1Ref = explorerService.create(PipelineDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), folder1, null);
        final PipelineDoc tran1 = pipelineStore.readDocument(tran1Ref);
        tran1.setDescription("Description");
        pipelineStore.writeDocument(tran1);

        final DocRef tran2Ref = explorerService.create(PipelineDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), folder2, null);
        PipelineDoc tran2 = pipelineStore.readDocument(tran2Ref);
        tran2.setDescription("Description");
        tran2.setParentPipeline(tran1Ref);
        tran2 = pipelineStore.writeDocument(tran2);

        final DocRef referenceFeedRef = explorerService.create(FeedDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), folder1, null);
        final FeedDoc referenceFeed = feedStore.readDocument(referenceFeedRef);
        referenceFeed.setDescription("Description");
        feedStore.writeDocument(referenceFeed);

        final DocRef eventFeedRef = explorerService.create(FeedDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), folder2, null);
        FeedDoc eventFeed = feedStore.readDocument(eventFeedRef);
        eventFeed.setDescription("Description");
        feedStore.writeDocument(eventFeed);

        final DocRef eventFeedChildRef = explorerService.create(FeedDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), folder2child1, null);
        final FeedDoc eventFeedChild = feedStore.readDocument(eventFeedChildRef);
        eventFeedChild.setDescription("Description");
        feedStore.writeDocument(eventFeedChild);

        final DocRef eventFeedChild2Ref = explorerService.create(FeedDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), folder2child2, null);
        final FeedDoc eventFeedChild2 = feedStore.readDocument(eventFeedChild2Ref);
        eventFeedChild2.setDescription("Description2");
        feedStore.writeDocument(eventFeedChild2);

        final int startTranslationSize = pipelineStore.list().size();
        final int startFeedSize = feedStore.list().size();

        final ResourceKey file = resourceStore.createTempFile("Export.zip");
        final Set<DocRef> docRefs = new HashSet<>();
        docRefs.add(folder1);
        docRefs.add(folder2);

        // Export
        importExportService.exportConfig(docRefs, resourceStore.getTempFile(file), null);

        final ResourceKey exportConfig = resourceStore.createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, resourceStore.getTempFile(exportConfig), null);

        // Delete it and check
        pipelineStore.deleteDocument(tran2.getUuid());
        assertThat(pipelineStore.list().size()).isEqualTo(startTranslationSize - 1);

        feedStore.deleteDocument(eventFeedRef.getUuid());
        assertThat(feedStore.list().size()).isEqualTo(startFeedSize - 1);

        // Import
        final List<ImportState> confirmations = importExportService
                .createImportConfirmationList(resourceStore.getTempFile(file));

        for (final ImportState confirmation : confirmations) {
            confirmation.setAction(true);
        }

        importExportService.performImportWithConfirmation(resourceStore.getTempFile(file), confirmations);

        assertThat(feedStore.list().size()).isEqualTo(startFeedSize);
        assertThat(pipelineStore.list().size()).isEqualTo(startTranslationSize);

        final ResourceKey fileChild = resourceStore.createTempFile("ExportChild.zip");
        final Set<DocRef> criteriaChild = new HashSet<>();
        criteriaChild.add(folder2child2);

        // Export
        importExportService.exportConfig(criteriaChild, resourceStore.getTempFile(fileChild), null);
    }
}
