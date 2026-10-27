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
import stroom.ai.shared.AiChangeExtraction;
import stroom.ai.shared.AiCreateExtraction;
import stroom.ai.shared.AiWriteExtractionRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.xslt.XsltStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

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
    private static final DocRef TABLE_PIPELINE = new DocRef(PipelineDoc.TYPE, "tp", "Logons pipeline");
    private static final DocRef TABLE_XSLT = new DocRef("XSLT", "tx", "Logons XSLT");
    // The table's XSLT, as Stroom AI wrote it.
    private static final String GENERATED = ExtractionXslt.xslt("event-logging:3",
            List.of(new ExtractionField("UserId", "EventSource/User/Id", null)));
    // A change to the table's extraction: Bytes added.
    private static final ExtractionPlan CHANGE = new ExtractionPlan("Events", "table-1", "event-logging:3",
            List.of(new ExtractionField("UserId", "EventSource/User/Id", null),
                    new ExtractionField("Bytes", "Data/@Size", "number")),
            List.of("Bytes"),
            new ExtractionPlan.Existing(TABLE_PIPELINE, "extractXslt", TABLE_XSLT,
                    ExtractionChange.sha256(GENERATED)));
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
    private final DocDependencyService docDependencyService = Mockito.mock(DocDependencyService.class);
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
                () -> pipelineStore, () -> docDependencyService, documentAccess, securityContext);
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

    @Test
    void aNewChildOfTheTablesPipelineGivesOnlyItsXsltElementANewXslt() {
        existing(GENERATED);
        final AiCreateExtraction create = actions.prepare(DASHBOARD, "Logons extraction 2", CHANGE);

        assertThat(create.getBase()).isEqualTo("a child of pipeline 'Logons pipeline', with this XSLT in place of "
                                               + "XSLT 'Logons XSLT' (which is left as it is)");
        assertThat(create.getXslt()).isEqualTo(ExtractionXslt.xslt("event-logging:3", CHANGE.fields()));
        assertThat(create.getDashboardChanges()).containsExactly(
                "Set table 'Events' to extract its values with the new pipeline in place of pipeline 'Logons "
                + "pipeline'",
                "Add column 'Bytes' (${Bytes}, formatted as Number)");

        actions.write(DASHBOARD, CHANGE, request("Logons extraction 2", create.getContentHash()));

        final ArgumentCaptor<PipelineDoc> pipeline = ArgumentCaptor.forClass(PipelineDoc.class);
        Mockito.verify(pipelineStore).writeDocument(pipeline.capture());
        assertThat(pipeline.getValue().getParentPipeline()).isEqualTo(TABLE_PIPELINE);
        assertThat(pipeline.getValue().getPipelineData().getAddedProperties()).singleElement().satisfies(property -> {
            assertThat(property.getElement()).isEqualTo("extractXslt");
            assertThat(property.getValue().getEntity().getUuid()).isEqualTo("x1");
        });
        // The table's XSLT is left as it is.
        Mockito.verify(xsltStore, Mockito.never()).writeDocument(Mockito.argThat(doc -> "tx".equals(doc.getUuid())));
    }

    @Test
    void aChangeToAnXsltChangedSinceItWasProposedIsntShown() {
        existing(GENERATED + "\n<!-- edited -->");
        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(true);

        assertThatThrownBy(() -> actions.prepare(DASHBOARD, "Logons extraction 2", CHANGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("XSLT 'Logons XSLT' has changed since this was proposed");
        assertThatThrownBy(() -> actions.prepareChange(DASHBOARD, CHANGE))
                .hasMessageContaining("has changed since this was proposed");
    }

    @Test
    void aChangeInPlaceIsShownAsADiffWithWhatElseUsesIt() {
        existing(GENERATED);
        final DocRef child = new DocRef(PipelineDoc.TYPE, "cp", "Child pipeline");
        final DocRef other = new DocRef(DashboardDoc.TYPE, "other", "Other");
        final DocRef query = new DocRef("Query", "q", "Secret query");
        uses(List.of(new Dependency(TABLE_PIPELINE, TABLE_XSLT, true),
                new Dependency(DASHBOARD, TABLE_PIPELINE, true),
                new Dependency(other, TABLE_PIPELINE, true),
                new Dependency(child, TABLE_PIPELINE, true),
                new Dependency(query, child, true)));
        Mockito.when(documentAccess.canView(any())).thenAnswer(invocation -> !query.equals(invocation.getArgument(0)));
        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(true);

        final AiChangeExtraction change = actions.prepareChange(DASHBOARD, CHANGE);

        assertThat(change.getPipeline()).isEqualTo(TABLE_PIPELINE);
        assertThat(change.getXslt()).isEqualTo(TABLE_XSLT);
        assertThat(change.getCurrentXslt()).isEqualTo(GENERATED);
        assertThat(change.getProposedXslt()).isEqualTo(ExtractionXslt.xslt("event-logging:3", CHANGE.fields()));
        assertThat(change.getChange()).isEqualTo("XSLT 'Logons XSLT' written again by Stroom AI from the fields: "
                                                 + "UserId, Bytes");
        // The table's own pipeline is the one changed, not another user of it. The query is counted, though the user
        // can't see it: what uses the XSLT is found as the processing user, as Stroom only links what the user can
        // see both ends of.
        assertThat(change.getUsedBy()).containsExactly("Dashboard 'Logons' (this dashboard)", "Dashboard 'Other'",
                "Pipeline 'Child pipeline'", "1 other document(s) you can't see");
        Mockito.verify(securityContext).asProcessingUserResult(any());
        assertThat(change.getDashboardChanges()).containsExactly("Add column 'Bytes' (${Bytes}, formatted as Number)");
        Mockito.verifyNoInteractions(xsltStore, explorerService);
    }

    @Test
    void aChangeInPlaceIsntShownIfWhatUsesItCantBeFoundOrTheUserCantEditIt() {
        existing(GENERATED);

        assertThatThrownBy(() -> actions.prepareChange(DASHBOARD, CHANGE))
                .hasMessageContaining("You can't edit XSLT 'Logons XSLT'");

        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(true);
        Mockito.when(docDependencyService.fetchDependencies(any(), any())).thenThrow(new RuntimeException("down"));
        // Not "nothing else uses it".
        assertThatThrownBy(() -> actions.prepareChange(DASHBOARD, CHANGE))
                .hasMessageContaining("What else uses the XSLT can't be found");
    }

    @Test
    void aChangeInPlaceIsntWrittenIfWhatUsesItHasChanged() {
        existing(GENERATED);
        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(true);
        uses(List.of(new Dependency(TABLE_PIPELINE, TABLE_XSLT, true)));
        final String hash = actions.prepareChange(DASHBOARD, CHANGE).getContentHash();

        // A dashboard starts using it after the user was shown what does.
        uses(List.of(new Dependency(TABLE_PIPELINE, TABLE_XSLT, true),
                new Dependency(new DocRef(DashboardDoc.TYPE, "new", "New"), TABLE_PIPELINE, true)));
        Mockito.when(documentAccess.canView(any())).thenReturn(true);

        assertThatThrownBy(() -> actions.writeChange(DASHBOARD, CHANGE, hash))
                .hasMessageContaining("or what uses it, has changed since you were shown it");
        Mockito.verify(xsltStore, Mockito.never()).writeDocument(any());
    }

    @Test
    void aChangeInPlaceWritesTheXsltTheUserWasShown() {
        existing(GENERATED);
        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(true);
        Mockito.when(xsltStore.readDocument(TABLE_XSLT)).thenReturn(tableXslt(GENERATED));
        final AiChangeExtraction change = actions.prepareChange(DASHBOARD, CHANGE);

        final Written written = actions.writeChange(DASHBOARD, CHANGE, change.getContentHash());

        final ArgumentCaptor<XsltDoc> xslt = ArgumentCaptor.forClass(XsltDoc.class);
        Mockito.verify(xsltStore).writeDocument(xslt.capture());
        assertThat(xslt.getValue().getUuid()).isEqualTo("tx");
        assertThat(xslt.getValue().getData()).isEqualTo(change.getProposedXslt());
        assertThat(written.pipeline()).isEqualTo(TABLE_PIPELINE);
        // Only a column for the new field: the table uses the pipeline already.
        @SuppressWarnings("unchecked") final List<Map<String, Object>> changes =
                (List<Map<String, Object>>) JsonUtil.readValue(written.tableChanges(), List.class);
        assertThat(changes).singleElement().satisfies(op -> assertThat(op).containsEntry("op", "add_column")
                .containsEntry("name", "Bytes"));
        Mockito.verifyNoInteractions(explorerService, pipelineStore);
    }

    @Test
    void aChangeInPlaceIsntWrittenOverANewerVersionOrWithoutEdit() {
        existing(GENERATED);
        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(true);
        final String hash = actions.prepareChange(DASHBOARD, CHANGE).getContentHash();

        assertThatThrownBy(() -> actions.writeChange(DASHBOARD, CHANGE, "bad"))
                .hasMessageContaining("has changed since you were shown it");
        // Edit taken away since.
        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(false);
        assertThatThrownBy(() -> actions.writeChange(DASHBOARD, CHANGE, hash))
                .hasMessageContaining("You can't edit XSLT 'Logons XSLT'");

        // Saved by someone else as the user confirmed.
        Mockito.when(securityContext.hasDocumentPermission(TABLE_XSLT, DocumentPermission.EDIT)).thenReturn(true);
        Mockito.when(xsltStore.readDocument(TABLE_XSLT)).thenReturn(tableXslt(GENERATED + "<!-- newer -->"));
        assertThatThrownBy(() -> actions.writeChange(DASHBOARD, CHANGE, hash))
                .hasMessageContaining("has changed since this was proposed");

        // Or as it was written.
        Mockito.when(xsltStore.readDocument(TABLE_XSLT)).thenReturn(tableXslt(GENERATED));
        Mockito.when(xsltStore.writeDocument(any())).thenThrow(new RuntimeException("Document has been updated"));
        assertThatThrownBy(() -> actions.writeChange(DASHBOARD, CHANGE, hash))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("couldn't be written, so nothing was changed: Document has been updated");
        assertThatThrownBy(() -> actions.writeChange(DASHBOARD, PLAN, hash))
                .hasMessageContaining("no extraction pipeline to change");
    }

    // The table's extraction pipeline and its XSLT, as the change was proposed for.
    private void existing(final String code) {
        Mockito.when(documentAccess.read(TABLE_PIPELINE, PipelineDoc.class)).thenReturn(Optional.of(
                PipelineDoc.builder().uuid("tp").name("Logons pipeline").build()));
        Mockito.when(documentAccess.read(TABLE_XSLT, XsltDoc.class)).thenReturn(Optional.of(tableXslt(code)));
    }

    private static XsltDoc tableXslt(final String code) {
        return XsltDoc.builder().uuid("tx").name("Logons XSLT").data(code).build();
    }

    // The links Stroom's Dependencies screen shows, from the document using another to it.
    private void uses(final List<Dependency> dependencies) {
        Mockito.doAnswer(invocation -> {
            final DependencyCriteria criteria = invocation.getArgument(0);
            return ResultPage.createUnboundedList(dependencies.stream()
                    .filter(dependency -> criteria.getPartialName().contains(dependency.getTo().getUuid()))
                    .toList());
        }).when(docDependencyService).fetchDependencies(any(), any());
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
