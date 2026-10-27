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

package stroom.ai.impl.extraction;

import stroom.ai.impl.extraction.ExtractionActions.Written;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.shared.AiCreateExtraction;
import stroom.ai.shared.AiWriteExtractionRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.xslt.XsltStore;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestExtractionActions {

    private static final DocRef DASHBOARD = new DocRef(DashboardDoc.TYPE, "dash", "Logons");
    private static final DocRef FOLDER = new DocRef("Folder", "folder", "Reports");
    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE,
            ExtractionPipelines.SEARCH_EXTRACTION_TEMPLATE_UUID, "Search Extraction");
    private static final ExtractionPlan PLAN = new ExtractionPlan("Events", "table-1", "event-logging:3",
            List.of(new ExtractionField("UserId", "EventSource/User/Id", null),
                    new ExtractionField("Bytes", "Data/@Size", "number")),
            List.of("Bytes"));

    private final ExplorerService explorerService = Mockito.mock(ExplorerService.class);
    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final XsltStore xsltStore = Mockito.mock(XsltStore.class);
    private final PipelineStore pipelineStore = Mockito.mock(PipelineStore.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final ExplorerNode folder = ExplorerNode.builder().docRef(FOLDER).build();
    private final ExplorerNode xsltNode = ExplorerNode.builder().docRef(new DocRef(XsltDoc.TYPE, "x1", "Logons "
                                                                                                   + "extraction"))
            .build();
    private final ExplorerNode pipelineNode = ExplorerNode.builder().docRef(new DocRef(PipelineDoc.TYPE, "p1",
            "Logons extraction")).build();
    private ExtractionActions actions;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(securityContext.asProcessingUserResult(any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(0)).get());
        Mockito.when(explorerNodeService.getParent(DASHBOARD)).thenReturn(Optional.of(folder));
        Mockito.when(explorerNodeService.getNode(FOLDER)).thenReturn(Optional.of(folder));
        Mockito.when(securityContext.hasDocumentCreatePermission(eq(FOLDER), any())).thenReturn(true);
        Mockito.when(explorerService.create(eq(XsltDoc.TYPE), any(), any(), any())).thenReturn(xsltNode);
        Mockito.when(explorerService.create(eq(PipelineDoc.TYPE), any(), any(), any())).thenReturn(pipelineNode);
        Mockito.when(xsltStore.readDocument(xsltNode.getDocRef())).thenReturn(XsltDoc.builder().uuid("x1")
                .name("Logons extraction").build());
        Mockito.when(pipelineStore.readDocument(pipelineNode.getDocRef())).thenReturn(PipelineDoc.builder()
                .uuid("p1").name("Logons extraction").build());
        template(true);
        actions = new ExtractionActions(() -> explorerService, () -> explorerNodeService, () -> xsltStore,
                () -> pipelineStore, documentAccess, securityContext);
    }

    @Test
    void whatWouldBeWrittenIsShownFirstAndNothingWritten() {
        final AiCreateExtraction create = actions.prepare(DASHBOARD, "Logons extraction", PLAN);

        assertThat(create.getName()).isEqualTo("Logons extraction");
        assertThat(create.getFolder().getDocRef()).isEqualTo(FOLDER);
        assertThat(create.getXslt()).isEqualTo(ExtractionXslt.xslt("event-logging:3", PLAN.fields()));
        assertThat(create.getBase()).startsWith("a child of the Search Extraction template");
        assertThat(create.getFields()).containsExactly("UserId (text), from EventSource/User/Id",
                "Bytes (number), from Data/@Size");
        assertThat(create.getDashboardChanges()).containsExactly(
                "Set table 'Events' to extract its values with the new pipeline (turning extraction on)",
                "Add column 'Bytes' (${Bytes}, formatted as Number)");
        assertThat(create.getXsltDescription()).isEqualTo("The search extraction of table 'Events' on dashboard "
                                                          + "'Logons', written by Stroom AI from its fields: "
                                                          + "UserId, Bytes.");
        assertThat(create.getPipelineDescription()).startsWith("Extracts UserId, Bytes from the events of table");
        assertThat(create.getContentHash()).hasSize(64);
        Mockito.verifyNoInteractions(explorerService, xsltStore, pipelineStore);
    }

    @Test
    void theConfirmedExtractionIsWrittenAsAChildOfTheTemplate() {
        final String hash = actions.prepare(DASHBOARD, "Logons extraction", PLAN).getContentHash();

        final Written written = actions.write(DASHBOARD, PLAN, request("Logons extraction", hash));

        Mockito.verify(explorerService).create(XsltDoc.TYPE, "Logons extraction", folder,
                PermissionInheritance.DESTINATION);
        final ArgumentCaptor<XsltDoc> xslt = ArgumentCaptor.forClass(XsltDoc.class);
        Mockito.verify(xsltStore).writeDocument(xslt.capture());
        assertThat(xslt.getValue().getData()).isEqualTo(ExtractionXslt.xslt("event-logging:3", PLAN.fields()));
        // Described as the user was shown.
        final AiCreateExtraction shown = actions.prepare(DASHBOARD, "Logons extraction", PLAN);
        assertThat(xslt.getValue().getDescription()).isEqualTo(shown.getXsltDescription());
        final ArgumentCaptor<PipelineDoc> pipeline = ArgumentCaptor.forClass(PipelineDoc.class);
        Mockito.verify(pipelineStore).writeDocument(pipeline.capture());
        assertThat(pipeline.getValue().getParentPipeline()).isEqualTo(TEMPLATE);
        assertThat(pipeline.getValue().getDescription()).isEqualTo(shown.getPipelineDescription());
        assertThat(pipeline.getValue().getPipelineData().getAddedProperties()).singleElement().satisfies(property -> {
            assertThat(property.getElement()).isEqualTo(ExtractionPipelines.XSLT_ELEMENT);
            assertThat(property.getValue().getEntity().getUuid()).isEqualTo("x1");
        });
        assertThat(written.pipeline().getUuid()).isEqualTo("p1");
        // The table: its extraction pipeline set (which turns extraction on), and a column for the new field.
        @SuppressWarnings("unchecked") final List<Map<String, Object>> changes =
                (List<Map<String, Object>>) JsonUtil.readValue(written.tableChanges(), List.class);
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0)).containsEntry("op", "set_setting")
                .containsEntry("setting", "extractionPipeline")
                .containsEntry("doc", Map.of("type", PipelineDoc.TYPE, "uuid", "p1", "name", "Logons extraction"));
        assertThat(changes.get(1)).containsEntry("op", "add_column")
                .containsEntry("name", "Bytes")
                .containsEntry("expression", "${Bytes}")
                .containsEntry("format", "Number");
    }

    @Test
    void withoutTheTemplateThePipelineIsOneOfItsOwn() {
        template(false);
        final AiCreateExtraction create = actions.prepare(DASHBOARD, "Logons extraction", PLAN);

        actions.write(DASHBOARD, PLAN, request("Logons extraction", create.getContentHash()));

        assertThat(create.getBase()).startsWith("a pipeline of its own");
        final ArgumentCaptor<PipelineDoc> pipeline = ArgumentCaptor.forClass(PipelineDoc.class);
        Mockito.verify(pipelineStore).writeDocument(pipeline.capture());
        assertThat(pipeline.getValue().getParentPipeline()).isNull();
        assertThat(pipeline.getValue().getPipelineData().getAddedElements())
                .extracting(element -> element.getType())
                .contains("IdEnrichmentFilter", "XSLTFilter", "SearchResultOutputFilter");
    }

    @Test
    void onlyWhatTheUserWasShownIsWritten() {
        // E.g. the template has since been deleted, so what would be written isn't what they confirmed.
        final String hash = actions.prepare(DASHBOARD, "Logons extraction", PLAN).getContentHash();
        template(false);

        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request("Logons extraction", hash)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has changed since you were shown it");
        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request("Logons extraction", "bad")))
                .hasMessageContaining("has changed since you were shown it");
        Mockito.verifyNoInteractions(explorerService);
    }

    @Test
    void theUserMustBeAbleToCreateInTheFolder() {
        final String hash = actions.prepare(DASHBOARD, "Logons extraction", PLAN).getContentHash();
        Mockito.when(securityContext.hasDocumentCreatePermission(FOLDER, PipelineDoc.TYPE)).thenReturn(false);

        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request("Logons extraction", hash)))
                .hasMessageContaining("You can't create Pipeline documents in folder 'Reports'");
        Mockito.verifyNoInteractions(explorerService);
    }

    @Test
    void ifThePipelineCantBeWrittenTheXsltIsntKept() {
        final String hash = actions.prepare(DASHBOARD, "Logons extraction", PLAN).getContentHash();
        Mockito.when(pipelineStore.writeDocument(any())).thenThrow(new RuntimeException("Document has been "
                                                                                          + "updated"));

        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request("Logons extraction", hash)))
                .hasMessageContaining("nothing was kept")
                .hasMessageContaining("Document has been updated");
        Mockito.verify(explorerService).delete(List.of(xsltNode, pipelineNode));
    }

    @Test
    void whatCouldntBeRemovedIsSaid() {
        final String hash = actions.prepare(DASHBOARD, "Logons extraction", PLAN).getContentHash();
        Mockito.when(pipelineStore.writeDocument(any())).thenThrow(new RuntimeException("No permission"));
        // Stroom's explorer says what it couldn't delete, rather than throwing.
        Mockito.when(explorerService.delete(any())).thenReturn(new BulkActionResult(List.of(),
                "Unable to delete 'Logons extraction'"));

        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request("Logons extraction", hash)))
                .hasMessageContaining("couldn't all be removed: XSLT 'Logons extraction', Pipeline 'Logons "
                                      + "extraction'")
                .hasMessageNotContaining("nothing was kept");
    }

    @Test
    void theFolderMustBeOneThereAndThePermissionsOnesTheDialogOffers() {
        final String hash = actions.prepare(DASHBOARD, "Logons extraction", PLAN).getContentHash();

        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, new AiWriteExtractionRequest("Logons extraction",
                new DocRef("Folder", "gone"), PermissionInheritance.DESTINATION, hash)))
                .hasMessageContaining("The folder chosen no longer exists");
        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, new AiWriteExtractionRequest("Logons extraction",
                FOLDER, PermissionInheritance.SOURCE, hash)))
                .hasMessageContaining("take their permissions from their folder or from none");
        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request("a".repeat(101), hash)))
                .hasMessageContaining("a name of up to 100 characters");
        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request("Logons\u0007", hash)))
                .hasMessageContaining("a name of up to 100 characters");
        Mockito.verifyNoInteractions(explorerService);
    }

    @Test
    void aNameIsNeeded() {
        final String hash = actions.prepare(DASHBOARD, "Logons extraction", PLAN).getContentHash();

        assertThatThrownBy(() -> actions.write(DASHBOARD, PLAN, request(" ", hash)))
                .hasMessageContaining("Give the pipeline a name");
    }

    private void template(final boolean installed) {
        Mockito.when(documentAccess.findDocRef(ExtractionPipelines.SEARCH_EXTRACTION_TEMPLATE_UUID))
                .thenReturn(installed
                        ? Optional.of(TEMPLATE)
                        : Optional.empty());
        final PipelineDataBuilder data = new PipelineDataBuilder();
        for (final String[] element : new String[][]{{"Source", "Source"}, {"xmlParser", "XMLParser"},
                {"xsltFilter", "XSLTFilter"}, {"searchResultOutputFilter", "SearchResultOutputFilter"}}) {
            data.addElement(PipelineDataUtil.createElement(element[0], element[1], null, null));
        }
        data.addLink("Source", "xmlParser").addLink("xmlParser", "xsltFilter")
                .addLink("xsltFilter", "searchResultOutputFilter");
        Mockito.when(documentAccess.read(TEMPLATE, PipelineDoc.class)).thenReturn(Optional.of(PipelineDoc.builder()
                .uuid(TEMPLATE.getUuid()).name(TEMPLATE.getName()).pipelineData(data.build()).build()));
    }

    private static AiWriteExtractionRequest request(final String name, final String hash) {
        return new AiWriteExtractionRequest(name, FOLDER, PermissionInheritance.DESTINATION, hash);
    }
}
