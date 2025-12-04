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
import stroom.explorer.shared.PermissionInheritance;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.shared.PipelineDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportServiceImpl4 extends AbstractCoreIntegrationTest {

    private static final DocRef PIPELINE_DOC_REF = new DocRef(
            PipelineDoc.TYPE,
            "d6fb12ff-fb94-437e-90c3-b95372572efd",
            "DATA_SPLITTER-EVENTS");
    private static final ExplorerNode PIPELINE_EXPLORER_NODE = ExplorerNode.builder()
            .docRef(PIPELINE_DOC_REF)
            .build();

    @Inject
    private ImportExportService importExportService;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private ExplorerService explorerService;

    @Test
    void testAdvancedFeatures() throws IOException {
        final Path rootTestDir = StroomCoreServerTestFileUtil.getTestResourcesDir();
        final Path importDir = rootTestDir.resolve("samples/config");
        final Path zipFile = getCurrentTestDir().resolve(UUID.randomUUID() + ".zip");
        final ImportSettings.Builder builder = ImportSettings.builder();

        final Predicate<Path> filePredicate = path -> !path.equals(zipFile);
        final Predicate<String> entryPredicate = ZipUtil
                .createIncludeExcludeEntryPredicate(Pattern.compile(".*DATA_SPLITTER.*"), null);

        ZipUtil.zip(zipFile, importDir, filePredicate, entryPredicate);
        assertThat(Files.isRegularFile(zipFile)).isTrue();
        assertThat(Files.isDirectory(importDir)).isTrue();

        // Make sure doc doesn't exist in the explorer.
        final Optional<ExplorerNode> node = explorerNodeService.getNode(PIPELINE_DOC_REF);
        assertThat(node.isPresent()).isFalse();

        List<ImportState> confirmList = new ArrayList<>();
        confirmList = performImport(
                confirmList,
                zipFile,
                "Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                builder);

        /////////////////////////////////////////////////
        // CHECK RENAME
        /////////////////////////////////////////////////

        // Rename doc.
        final DocRef renamedPipelineDocRef = new DocRef(
                PipelineDoc.TYPE,
                "d6fb12ff-fb94-437e-90c3-b95372572efd",
                "RENAMED_DATA_SPLITTER-EVENTS");
        explorerNodeService.renameNode(renamedPipelineDocRef);

        // Perform import again.
        confirmList = performImport(
                confirmList,
                zipFile,
                "Feeds and Translations/Test",
                "RENAMED_DATA_SPLITTER-EVENTS",
                builder);

        // Now import with import name preservation.
        builder.useImportNames(true);
        confirmList = performImport(
                confirmList,
                zipFile,
                "Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                builder);

        /////////////////////////////////////////////////
        // CHECK FOLDER MOVE
        /////////////////////////////////////////////////

        builder.useImportNames(false);
        builder.useImportFolders(false);

        // Now move the item.
        // Create a new dest dir.
        final ExplorerNode rootNode = explorerNodeService.getRoot();
        assertThat(rootNode).isNotNull();

        final DocRef destFolder =
                new DocRef(ExplorerConstants.FOLDER_TYPE, UUID.randomUUID().toString(), "Destination Folder");
        explorerNodeService.createNode(destFolder, rootNode.getDocRef(), PermissionInheritance.DESTINATION);
        explorerNodeService.moveNode(PIPELINE_DOC_REF, destFolder, PermissionInheritance.DESTINATION);
        explorerNodeService.renameNode(renamedPipelineDocRef);

        // Import again.
        confirmList = performImport(
                confirmList,
                zipFile,
                "Destination Folder",
                "RENAMED_DATA_SPLITTER-EVENTS",
                builder);
        builder.useImportNames(false);
        builder.useImportFolders(true);
        confirmList = performImport(
                confirmList,
                zipFile,
                "Feeds and Translations/Test",
                "RENAMED_DATA_SPLITTER-EVENTS",
                builder);

        // Move back.
        explorerNodeService.moveNode(PIPELINE_DOC_REF, destFolder, PermissionInheritance.DESTINATION);
        builder.useImportNames(true);
        builder.useImportFolders(true);
        confirmList = performImport(
                confirmList,
                zipFile,
                "Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                builder);

        /////////////////////////////////////////////////
        // CHECK NEW
        /////////////////////////////////////////////////
        explorerNodeService.renameNode(renamedPipelineDocRef);
        explorerNodeService.moveNode(PIPELINE_DOC_REF, destFolder, PermissionInheritance.DESTINATION);
        builder.useImportNames(false);
        builder.useImportFolders(false);
        confirmList = performImport(
                confirmList,
                zipFile,
                "Destination Folder",
                "RENAMED_DATA_SPLITTER-EVENTS",
                builder);
        explorerService.delete(Collections.singletonList(PIPELINE_EXPLORER_NODE));
        confirmList = performImport(
                confirmList,
                zipFile,
                "Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                builder);

        /////////////////////////////////////////////////
        // CHECK NEW ROOT
        /////////////////////////////////////////////////
        final DocRef rootDocRef =
                new DocRef(ExplorerConstants.FOLDER_TYPE, UUID.randomUUID().toString(), "New Root");
        explorerNodeService.createNode(rootDocRef, rootNode.getDocRef(), PermissionInheritance.DESTINATION);
        builder.rootDocRef(rootDocRef);
        builder.useImportNames(false);
        builder.useImportFolders(false);
        confirmList = performImport(
                confirmList,
                zipFile,
                "Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                builder);

        explorerService.delete(Collections.singletonList(PIPELINE_EXPLORER_NODE));
        builder.rootDocRef(rootDocRef);
        builder.useImportNames(false);
        builder.useImportFolders(false);
        confirmList = performImport(
                confirmList,
                zipFile,
                "New Root/Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                builder);

        explorerService.delete(Collections.singletonList(PIPELINE_EXPLORER_NODE));
        builder.rootDocRef(rootDocRef);
        builder.useImportNames(false);
        builder.useImportFolders(true);
        confirmList = performImport(
                confirmList,
                zipFile,
                "New Root/Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                "New Root/",
                builder);

        explorerService.delete(Collections.singletonList(PIPELINE_EXPLORER_NODE));
        builder.rootDocRef(rootDocRef);
        builder.useImportNames(true);
        builder.useImportFolders(true);
        confirmList = performImport(
                confirmList,
                zipFile,
                "New Root/Feeds and Translations/Test",
                "DATA_SPLITTER-EVENTS",
                "New Root/",
                builder);
    }

    private List<ImportState> performImport(final List<ImportState> in,
                                            final Path zipFile,
                                            final String expectedPath,
                                            final String expectedName,
                                            final ImportSettings.Builder builder) {
        return performImport(in, zipFile, expectedPath, expectedName, "", builder);
    }

    private List<ImportState> performImport(final List<ImportState> in,
                                            final Path zipFile,
                                            final String expectedPath,
                                            final String expectedName,
                                            final String prefix,
                                            final ImportSettings.Builder builder) {
        final List<ImportState> confirmList =
                importExportService.importConfig(
                        zipFile,
                        builder.importMode(ImportMode.CREATE_CONFIRMATION).build(),
                        in);
        assertThat(confirmList).isNotNull();
        assertThat(confirmList.size()).isGreaterThan(0);
        confirmList.forEach(state -> {
            state.setAction(true);
            if (state.getDocRef().getUuid().equals(PIPELINE_DOC_REF.getUuid())) {
                assertThat(state.getDestPath()).isEqualTo(expectedPath + "/" + expectedName);
            } else {
                assertThat(prefix + state.getSourcePath()).isEqualTo(state.getDestPath());
            }
        });
        importExportService.importConfig(
                zipFile,
                builder.importMode(ImportMode.ACTION_CONFIRMATION).build(),
                confirmList);

        confirmList.forEach(state -> {
            final DocRef docRef = state.getDocRef();
            final Optional<ExplorerNode> node = explorerNodeService.getNode(docRef);
            assertThat(node.isPresent()).isTrue();
            final String currentPath = getParentPath(explorerNodeService.getPath(docRef));

            if (docRef.getUuid().equals(PIPELINE_DOC_REF.getUuid())) {
                assertThat(node.get().getName()).isEqualTo(expectedName);
                assertThat(currentPath).isEqualTo(expectedPath);

            } else {
                assertThat(currentPath).startsWith(prefix);
            }
        });

        return confirmList;
    }

    private String getParentPath(final List<ExplorerNode> parents) {
        if (parents != null && parents.size() > 0) {
            String parentPath = parents.stream()
                    .map(ExplorerNode::getName)
                    .collect(Collectors.joining("/"));
            int index = parentPath.indexOf("System");
            if (index == 0) {
                parentPath = parentPath.substring(index + "System".length());
            }
            index = parentPath.indexOf("/");
            if (index == 0) {
                parentPath = parentPath.substring(1);
            }
            return parentPath;
        }
        return "";
    }
}
