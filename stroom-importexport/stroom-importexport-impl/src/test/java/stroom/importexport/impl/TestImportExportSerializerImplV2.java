/*
 * Copyright 2026 Crown Copyright
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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.importexport.api.ImportExportVersion;
import stroom.processor.api.ProcessorFilterService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestImportExportSerializerImplV2 {

    private static final String DOC_TYPE = "TestDocType";
    private static final DocRef DOC_REF = new DocRef(
            DOC_TYPE, "5b0c1e4e-2f63-4a4b-9a0e-6c1f3d2a7b18", "Test doc");
    private static final ExplorerNode DOC_NODE = ExplorerNode.builder()
            .docRef(DOC_REF)
            .rootNodeUuid(ExplorerConstants.SYSTEM_DOC_REF.getUuid())
            .build();

    @Mock
    private ImportExportSerializerImpl mockImportExportSerializerV1;
    @Mock
    private ExplorerService mockExplorerService;
    @Mock
    private ExplorerNodeService mockExplorerNodeService;
    @Mock
    private ImportExportActionHandlersImpl mockImportExportActionHandlers;
    @Mock
    private ProcessorFilterService mockProcessorFilterService;
    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ImportExportDocumentEventLog mockImportExportDocumentEventLog;
    @Mock
    private ImportExportActionHandler mockHandler;

    @TempDir
    private Path exportDir;

    private ImportExportSerializerImplV2 serializer;

    @BeforeEach
    void setUp() {
        Mockito.when(mockSecurityContext.hasDocumentPermission(Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(mockExplorerNodeService.getChildren(ExplorerConstants.SYSTEM_DOC_REF))
                .thenReturn(List.of(DOC_NODE));
        Mockito.when(mockImportExportActionHandlers.getHandler(DOC_TYPE))
                .thenReturn(mockHandler);
        Mockito.when(mockHandler.exportDocument(Mockito.eq(DOC_REF), Mockito.anyBoolean(), Mockito.any()))
                .thenReturn(new ImportExportDocument());

        serializer = new ImportExportSerializerImplV2(
                mockImportExportSerializerV1,
                mockExplorerService,
                mockExplorerNodeService,
                mockImportExportActionHandlers,
                mockProcessorFilterService,
                mockSecurityContext,
                mockImportExportDocumentEventLog);
    }

    @Test
    void testWrite_noPermissionForAssociatedDocs() {
        // e.g. a pipeline's processor filters, which need Manage Processors to read
        Mockito.when(mockHandler.findAssociatedNonExplorerDocRefs(DOC_REF))
                .thenThrow(new PermissionException(null, "No permission to read processor filters"));

        final ExportSummary exportSummary = exportAll();

        assertThat(exportSummary.getSuccessCountsByType())
                .containsEntry(DOC_TYPE, 1);
        assertThat(exportSummary.getFailedTotal())
                .isZero();
        assertThat(exportSummary.getMessages())
                .filteredOn(message -> message.getSeverity() == Severity.WARNING)
                .extracting(Message::getMessage)
                .containsExactly("Skipped documents associated with 'Test doc': "
                                 + "No permission to read processor filters");
        assertThat(exportDir.resolve(ImportExportFileNameUtil.createFilePrefix(DOC_REF) + ".node"))
                .exists();
    }

    @Test
    void testWrite_noAssociatedDocs() {
        Mockito.when(mockHandler.findAssociatedNonExplorerDocRefs(DOC_REF))
                .thenReturn(Collections.emptySet());

        final ExportSummary exportSummary = exportAll();

        assertThat(exportSummary.getSuccessCountsByType())
                .containsEntry(DOC_TYPE, 1);
        assertThat(exportSummary.getMessages())
                .extracting(Message::getSeverity)
                .doesNotContain(Severity.WARNING, Severity.ERROR);
    }

    private ExportSummary exportAll() {
        return serializer.write(
                null,
                exportDir,
                Set.of(ExplorerConstants.SYSTEM_DOC_REF),
                Collections.emptySet(),
                null,
                true,
                ImportExportVersion.V2);
    }
}
