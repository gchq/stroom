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


import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportServiceImpl extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportService importExportService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private FeedStore feedStore;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;

    @TempDir
    private Path tempDir;

    private Path createTempFile(final String filename) {
        return tempDir.resolve(filename);
    }

    @Test
    void testExport() {
        final ExplorerNode systemNode = explorerNodeService.getRoot();
        final DocRef systemDocRef = systemNode != null
                ? systemNode.getDocRef()
                : null;
        assertThat(explorerNodeService.getDescendants(systemDocRef).size()).isEqualTo(1);

        final ExplorerNode folder1 = explorerService.create(ExplorerConstants.FOLDER_TYPE, "Root1_", systemNode, null);
        final ExplorerNode folder2 = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                "Root2_" + FileSystemTestUtil.getUniqueTestString(),
                systemNode,
                null);
        final ExplorerNode folder2child1 = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                "Root2_Child1_" + FileSystemTestUtil.getUniqueTestString(),
                folder2,
                null);
        final ExplorerNode folder2child2 = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                "Root2_Child2_" + FileSystemTestUtil.getUniqueTestString(),
                folder2,
                null);

        assertThat(explorerNodeService.getDescendants(systemDocRef).size()).isEqualTo(5);

        final ExplorerNode tran1Ref = explorerService.create(PipelineDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                folder1,
                null);
        final PipelineDoc tran1 = pipelineStore.readDocument(tran1Ref.getDocRef());
        tran1.setDescription("Description");
        pipelineStore.writeDocument(tran1);

        final ExplorerNode tran2Ref = explorerService.create(PipelineDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                folder2,
                null);
        PipelineDoc tran2 = pipelineStore.readDocument(tran2Ref.getDocRef());
        tran2.setDescription("Description");
        tran2.setParentPipeline(tran1Ref.getDocRef());
        tran2 = pipelineStore.writeDocument(tran2);

        final ExplorerNode referenceFeedRef = explorerService.create(FeedDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                folder1,
                null);
        final FeedDoc referenceFeed = feedStore.readDocument(referenceFeedRef.getDocRef());
        referenceFeed.setDescription("Description");
        feedStore.writeDocument(referenceFeed);

        final ExplorerNode eventFeedNode = explorerService.create(FeedDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                folder2,
                null);
        final FeedDoc eventFeed = feedStore.readDocument(eventFeedNode.getDocRef());
        eventFeed.setDescription("Description");
        feedStore.writeDocument(eventFeed);

        final ExplorerNode eventFeedChildNode = explorerService.create(FeedDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                folder2child1,
                null);
        final FeedDoc eventFeedChild = feedStore.readDocument(eventFeedChildNode.getDocRef());
        eventFeedChild.setDescription("Description");
        feedStore.writeDocument(eventFeedChild);

        final ExplorerNode eventFeedChild2Node = explorerService.create(FeedDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                folder2child2,
                null);
        final FeedDoc eventFeedChild2 = feedStore.readDocument(eventFeedChild2Node.getDocRef());
        eventFeedChild2.setDescription("Description2");
        feedStore.writeDocument(eventFeedChild2);

        final int startTranslationSize = pipelineStore.list().size();
        final int startFeedSize = feedStore.list().size();

        final Path file = createTempFile("Export.zip");
        final Set<DocRef> docRefs = new HashSet<>();
        docRefs.add(folder1.getDocRef());
        docRefs.add(folder2.getDocRef());

        // Export
        importExportService.exportConfig(docRefs, file);

        final Path exportConfig = createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, exportConfig);

        // Delete it and check
        pipelineStore.deleteDocument(tran2.asDocRef());
        assertThat(pipelineStore.list().size()).isEqualTo(startTranslationSize - 1);

        feedStore.deleteDocument(eventFeedNode.getDocRef());
        assertThat(feedStore.list().size()).isEqualTo(startFeedSize - 1);

        // Import
        final List<ImportState> confirmations = importExportService.importConfig(
                file,
                ImportSettings.createConfirmation(),
                new ArrayList<>());

        for (final ImportState confirmation : confirmations) {
            confirmation.setAction(true);
        }

        importExportService.importConfig(
                file,
                ImportSettings.actionConfirmation(),
                confirmations);

        assertThat(feedStore.list().size()).isEqualTo(startFeedSize);
        assertThat(pipelineStore.list().size()).isEqualTo(startTranslationSize);

        final Path fileChild = createTempFile("ExportChild.zip");
        final Set<DocRef> criteriaChild = new HashSet<>();
        criteriaChild.add(folder2child2.getDocRef());

        // Export
        importExportService.exportConfig(criteriaChild, fileChild);
    }
}
