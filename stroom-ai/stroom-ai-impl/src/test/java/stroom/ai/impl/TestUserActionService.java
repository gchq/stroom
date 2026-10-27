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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.impl.UserActionRecord.Status;
import stroom.ai.impl.extraction.ExtractionActions;
import stroom.ai.impl.extraction.ExtractionField;
import stroom.ai.impl.extraction.ExtractionPlan;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.vis.VisualisationActions;
import stroom.ai.shared.AiChangeExtraction;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiCreateExtraction;
import stroom.ai.shared.AiCreateVisualisation;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiUserActionResult;
import stroom.ai.shared.AiWriteExtractionRequest;
import stroom.ai.shared.AiWriteExtractionResult;
import stroom.dashboard.shared.DashboardConfig;
import stroom.docref.DocRef;
import stroom.explorer.shared.PermissionInheritance;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.exception.AuthenticationException;
import stroom.util.json.JsonUtil;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestUserActionService {

    private static final int CHAT = 7;
    private static final int MESSAGE = 50;
    private static final String PIPELINE_UUID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";
    private static final DocRef PIPELINE = new DocRef("Pipeline", PIPELINE_UUID, "Acme-Events");
    private static final DocRef DASHBOARD = new DocRef("Dashboard", "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", "Logons");
    private static final DocRef XSLT = new DocRef("XSLT", "6b1d3c2e-8f4a-4b9c-a1d2-3e4f5a6b7c8d", "Event translation");

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final UserActionLookup lookup = Mockito.mock(UserActionLookup.class);
    private final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final DashboardBuilder dashboardBuilder = Mockito.mock(DashboardBuilder.class);
    private final VisualisationActions visualisationActions = Mockito.mock(VisualisationActions.class);
    private final ExtractionActions extractionActions = Mockito.mock(ExtractionActions.class);
    private final UserActionService service = new UserActionService(aiService, aiDao, new UserActions(lookup),
            lookup, processorFilterService, auditLog, dashboardBuilder, visualisationActions, extractionActions);

    @BeforeEach
    void setUp() {
        Mockito.when(lookup.filter(31)).thenReturn(Optional.of(filter(false)));
        Mockito.when(lookup.find(PIPELINE)).thenReturn(Optional.of(PIPELINE));
        Mockito.when(aiDao.compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(true);
    }

    @Test
    void enablingAFilterChangesItAsTheUserAndUsesTheButtonUp() {
        final String stored = storeResult(enable(Status.PENDING, Long.MAX_VALUE));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "enable-31");

        assertThat(result.isDone()).isTrue();
        Mockito.verify(processorFilterService).setEnabled(31, true);
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(stored), written.capture());
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .singleElement()
                .extracting(UserActionRecord::status)
                .isEqualTo(Status.DONE);
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq("enable_processor_filter"),
                Mockito.anyString(), Mockito.anyString(), Mockito.eq(true));
        // The model is told on its next turn.
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.contains("enabled processor filter 31 on pipeline 'Acme-Events'"));
    }

    @Test
    void onlyTheChatsOwnerCanUseItsButtons() {
        storeResult(enable(Status.PENDING, Long.MAX_VALUE));
        Mockito.doThrow(new AuthenticationException("Not your chat")).when(aiService).verifyOwnership(CHAT);

        assertThatThrownBy(() -> service.doUserAction(CHAT, MESSAGE, "enable-31"))
                .isInstanceOf(AuthenticationException.class);
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void aButtonIsOnlyUsedOnce() {
        storeResult(enable(Status.DONE, Long.MAX_VALUE));

        assertThat(service.doUserAction(CHAT, MESSAGE, "enable-31").getMessage()).contains("already been done");
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void aClickThatLosesARaceDoesNothing() {
        storeResult(enable(Status.PENDING, Long.MAX_VALUE));
        Mockito.when(aiDao.compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(false);

        assertThat(service.doUserAction(CHAT, MESSAGE, "enable-31").isDone()).isFalse();
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void anExpiredButtonDoesNothing() {
        storeResult(enable(Status.PENDING, 1L));

        assertThat(service.doUserAction(CHAT, MESSAGE, "enable-31").getMessage()).contains("expired");
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void aFilterThatHasChangedSinceIsNotTouched() {
        storeResult(enable(Status.PENDING, Long.MAX_VALUE));
        Mockito.when(lookup.filter(31)).thenReturn(Optional.of(filter(true)));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "enable-31");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("already enabled");
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void aFailureIsRecorded() {
        storeResult(enable(Status.PENDING, Long.MAX_VALUE));
        Mockito.doThrow(new RuntimeException("You do not have permission to update this processor filter"))
                .when(processorFilterService).setEnabled(31, true);

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "enable-31");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("do not have permission");
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)), Mockito.anyString(),
                Mockito.anyString(),
                Mockito.contains("permission"), Mockito.eq(false));
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void openingADocumentGivesItToTheBrowser() {
        storeResult(new UserActionRecord("open", UserActions.OPEN_DOCUMENT, "Open Pipeline 'Acme-Events'", null,
                null, null, PIPELINE, null, List.of(), Long.MAX_VALUE, Status.PENDING, null));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "open");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getOpenDocument()).isEqualTo(PIPELINE);
    }

    @Test
    void elasticsearchRequestsAreNeverRun() {
        storeResult(new UserActionRecord("drop", UserActions.ELASTICSEARCH_REQUEST, "Elasticsearch request", null,
                null, null, null, "DELETE ecs-acme-v1", List.of(), Long.MAX_VALUE, Status.PENDING, null));

        assertThat(service.doUserAction(CHAT, MESSAGE, "drop").isDone()).isFalse();
    }

    @Test
    void createQueryLetsTheBrowserOpenTheNewQueryDialogWithoutCreatingAnything() {
        final String query = "from \"Windows events\" select User";
        Mockito.when(lookup.usableDataSource(query)).thenReturn(Optional.of(new DocRef("View", "v", "V")));
        storeResult(createQuery(query));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "q1");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getCreateQueryName()).isEqualTo("Windows users");
        assertThat(result.getCreateQueryText()).isEqualTo(query);
        // The button can be used again (e.g. after the dialog was cancelled), so the result isn't changed.
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString());
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(UserActions.CREATE_QUERY),
                Mockito.anyString(), Mockito.eq("Opened the New Query dialog"), Mockito.eq(true));
    }

    @Test
    void createQueryIsRefusedOnceTheUserCanNoLongerRunTheQuery() {
        final String query = "from \"Windows events\" select User";
        Mockito.when(lookup.usableDataSource(query)).thenReturn(Optional.empty());
        storeResult(createQuery(query));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "q1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getCreateQueryText()).isNull();
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(UserActions.CREATE_QUERY),
                Mockito.anyString(), Mockito.contains("can't use"), Mockito.eq(false));
    }

    @Test
    void createDashboardGivesTheBrowserTheSettingsBuiltFromTheQueryNow() {
        final String query = "from \"Windows events\" select User";
        Mockito.when(lookup.usableDataSource(query)).thenReturn(Optional.of(new DocRef("View", "v", "V")));
        final DashboardConfig config = DashboardConfig.builder().build();
        Mockito.when(dashboardBuilder.build("Windows users", query, DashboardBuilder.CLASSIC, true))
                .thenReturn(new DashboardBuilder.Built(config, new DocRef("View", "v", "V"),
                        DashboardBuilder.CLASSIC, List.of("User"), false, true, List.of()));
        storeResult(createDashboard(query));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "d1");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getCreateDashboardName()).isEqualTo("Windows users");
        assertThat(result.getCreateDashboardConfig()).isSameAs(config);
        // The button can be used again (e.g. after the dialog was cancelled), so the result isn't changed.
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString());
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)),
                Mockito.eq(UserActions.CREATE_DASHBOARD),
                Mockito.anyString(), Mockito.eq("Opened the New Dashboard dialog"), Mockito.eq(true));
    }

    @Test
    void createDashboardIsRefusedOnceTheUserCanNoLongerRunTheQuery() {
        final String query = "from \"Windows events\" select User";
        Mockito.when(lookup.usableDataSource(query)).thenReturn(Optional.empty());
        storeResult(createDashboard(query));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "d1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getCreateDashboardConfig()).isNull();
        Mockito.verifyNoInteractions(dashboardBuilder);
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)),
                Mockito.eq(UserActions.CREATE_DASHBOARD),
                Mockito.anyString(), Mockito.contains("can't use"), Mockito.eq(false));
    }

    @Test
    void createDashboardIsRefusedIfItCanNoLongerBeBuilt() {
        final String query = "from \"Windows events\" select User";
        Mockito.when(lookup.usableDataSource(query)).thenReturn(Optional.of(new DocRef("View", "v", "V")));
        Mockito.when(dashboardBuilder.build(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenThrow(new IllegalArgumentException("The query can't be read: bad field"));
        storeResult(createDashboard(query));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "d1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).isEqualTo("The dashboard can no longer be built from the query: The query "
                                                  + "can't be read: bad field");
    }

    @Test
    void anUnknownActionOrMessageDoesNothing() {
        storeResult(enable(Status.PENDING, Long.MAX_VALUE));

        assertThat(service.doUserAction(CHAT, MESSAGE, "other").getMessage()).contains("not in the chat");
        assertThat(service.doUserAction(CHAT, MESSAGE + 1, "enable-31").getMessage()).contains("not in the chat");
    }

    @Test
    void applyingACodeChangeNeedsEditAndRecordsItWithoutSavingAnything() {
        Mockito.when(lookup.find(XSLT)).thenReturn(Optional.of(XSLT));
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(true);
        final String stored = storeResult(codeChange(Status.PENDING));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "code-1");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Applied, not saved");
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(stored), written.capture());
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.status()).isEqualTo(Status.DONE);
                    assertThat(action.outcome()).isEqualTo("Applied, not saved");
                    // What it was proposed against is kept, for Undo apply and the card.
                    assertThat(action.codeChange().baseCode()).isEqualTo("<a/>");
                });
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(XSLT),
                Mockito.eq(UserActionService.APPLY_COMMAND), Mockito.contains("element 'xsltFilter'"),
                Mockito.eq("Applied a proposed change, not saved: +2 −1 lines"), Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.contains("It is not saved"));
        // The browser applies it to the editor; nothing on the server changes the document.
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void aCodeChangeAppliedInPartRecordsWhatWasAppliedAndWhatWasRejected() {
        Mockito.when(lookup.find(XSLT)).thenReturn(Optional.of(XSLT));
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(true);
        // Two parts: the value changed, and the final line break removed.
        storeResult(codeChange(Status.PENDING, "<a>\n  <b>Test</b>\n</a>\n", "<a>\n  <b>Development</b>\n</a>"));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "code-1", "1");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Applied, not saved (1 of 2 parts)");
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(XSLT),
                Mockito.eq(UserActionService.APPLY_COMMAND), Mockito.contains("element 'xsltFilter'"),
                Mockito.eq("Applied part of a proposed change, not saved: +1 −1 lines, 1 of 2 parts"),
                Mockito.eq(true));
        final ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                note.capture());
        assertThat(note.getValue())
                .contains("I applied part of the proposed change")
                .contains("(+1 −1 lines, 1 of 2 parts)")
                .contains("- Part 2, at line 4: the removal of the final line break");
    }

    @Test
    void aRejectedPartOfAChangeIsDescribedByItsLines() {
        final LineDiffResult diff = LineDiff.diff("a\nb\nc", "a\nB\nc");

        assertThat(UserActionService.describeParts(diff, Set.of(0)))
                .isEqualTo("- Part 1, at line 2:\n  - b\n  + B");
    }

    @Test
    void rejectingEveryPartOrPartsThatArentThereIsRefused() {
        Mockito.when(lookup.find(XSLT)).thenReturn(Optional.of(XSLT));
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(true);
        storeResult(codeChange(Status.PENDING, "<a>\n  <b>Test</b>\n</a>\n", "<a>\n  <b>Development</b>\n</a>"));

        assertThat(service.doUserAction(CHAT, MESSAGE, "code-1", "0,1").getMessage())
                .contains("Every part of the change was rejected");
        assertThat(service.doUserAction(CHAT, MESSAGE, "code-1", "2").getMessage())
                .contains("aren't parts of this change");
        assertThat(service.doUserAction(CHAT, MESSAGE, "code-1", "x").getMessage()).contains("weren't understood");
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.any(),
                Mockito.any());

        // Only a code change can be applied in part.
        storeResult(enable(Status.PENDING, Long.MAX_VALUE));
        assertThat(service.doUserAction(CHAT, MESSAGE, "enable-31", "0").getMessage())
                .contains("Only a proposed code change");
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void applyingADashboardChangeNeedsEditAndRecordsTheOperationsWithoutSaving() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);
        final String stored = storeResult(dashboardChange());

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "dash-1");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getMessage()).isEqualTo(UserActionService.APPLIED_DASHBOARD);
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(stored), written.capture());
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.status()).isEqualTo(Status.DONE);
                    assertThat(action.dashboardChange().operations()).hasSize(1);
                });
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.APPLY_DASHBOARD_COMMAND), Mockito.contains("table 'Events'"),
                Mockito.eq("Applied a proposed change, not saved: Group by 'EventType' (level 1)"), Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.contains("It is not saved"));
    }

    @Test
    void aDashboardChangeIsNotAppliedWithoutEdit() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(false);
        storeResult(dashboardChange());

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "dash-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("don't have permission to edit Dashboard 'Logons'");
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.any(),
                Mockito.any());
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.APPLY_DASHBOARD_COMMAND), Mockito.anyString(), Mockito.anyString(),
                Mockito.eq(false));
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.any());
    }

    @Test
    void aDashboardChangeGivesTheChartsItNamesTheirCurrentSettingsFirst() {
        final DocRef chart = new DocRef("Visualisation", "6d1c2f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6", "People");
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);
        storeResult(dashboardChange(List.of(chart)));

        assertThat(service.doUserAction(CHAT, MESSAGE, "dash-1").isDone()).isTrue();

        Mockito.verify(visualisationActions).updateSettings(chart);
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(chart),
                Mockito.eq(UserActionService.APPLY_DASHBOARD_COMMAND), Mockito.contains("Visualisation 'People'"),
                Mockito.contains("saved"), Mockito.eq(true));
    }

    @Test
    void aDashboardChangeIsNotAppliedIfItsChartCantBeGivenItsSettings() {
        final DocRef chart = new DocRef("Visualisation", "6d1c2f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6", "People");
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);
        Mockito.when(visualisationActions.updateSettings(chart)).thenReturn("needs Edit permission on it");
        storeResult(dashboardChange(List.of(chart)));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "dash-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).isEqualTo("needs Edit permission on it");
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.any(),
                Mockito.any());
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.any());
    }

    @Test
    void anExtractionsButtonShowsWhatWouldBeWrittenAndWritesNothing() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        final AiCreateExtraction create = new AiCreateExtraction("Logons extraction", null, DASHBOARD, "table-1",
                "Events", "a child of the template", "<xsl:stylesheet/>", List.of("UserId"), List.of(), "x", "p",
                null, "hash");
        Mockito.when(extractionActions.prepare(Mockito.eq(DASHBOARD), Mockito.eq("Logons extraction"),
                Mockito.any(), Mockito.eq(ToolSession.chat(CHAT)))).thenReturn(create);
        storeResult(extraction());

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "extract-1");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getCreateExtraction()).isSameAs(create);
        // Nothing is written, and the button is still to be used.
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.any(),
                Mockito.any());
        Mockito.verify(extractionActions, Mockito.never()).write(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any());
    }

    @Test
    void aConfirmedExtractionIsWrittenOnceAndRecorded() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        final DocRef pipeline = new DocRef("Pipeline", "p1", "Logons extraction");
        final DocRef xslt = new DocRef("XSLT", "x1", "Logons extraction");
        final AiWriteExtractionRequest request = new AiWriteExtractionRequest("Logons extraction",
                new DocRef("Folder", "f1", "Reports"), PermissionInheritance.DESTINATION, "hash");
        Mockito.when(extractionActions.write(Mockito.eq(DASHBOARD), Mockito.any(), Mockito.eq(request), Mockito.any()))
                .thenReturn(new ExtractionActions.Written(pipeline, xslt, "[]"));
        final String stored = storeResult(extraction());

        final AiWriteExtractionResult result = service.writeExtraction(CHAT, MESSAGE, "extract-1", request);

        assertThat(result.isDone()).isTrue();
        assertThat(result.getPipeline()).isEqualTo(pipeline);
        assertThat(result.getTableChanges()).isEqualTo("[]");
        // Claimed before it was written.
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        final InOrder order = Mockito.inOrder(aiDao, extractionActions);
        order.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(stored), written.capture());
        order.verify(extractionActions).write(Mockito.eq(DASHBOARD), Mockito.any(), Mockito.eq(request),
                Mockito.eq(ToolSession.chat(CHAT)));
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .singleElement()
                .extracting(UserActionRecord::status)
                .isEqualTo(Status.DONE);
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(pipeline),
                Mockito.eq(UserActionService.WRITE_EXTRACTION_COMMAND), Mockito.anyString(),
                Mockito.contains("Created pipeline 'Logons extraction'"), Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.contains("I created the extraction"));
    }

    @Test
    void anExtractionThatCantBeWrittenLeavesTheButtonToUseAgain() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(extractionActions.write(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("What would be written has changed since you were shown it"));
        final String stored = storeResult(extraction());

        final AiWriteExtractionResult result = service.writeExtraction(CHAT, MESSAGE, "extract-1",
                new AiWriteExtractionRequest("Logons extraction", null, null, "old"));

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("has changed since you were shown it");
        // Claimed, then given back.
        final ArgumentCaptor<String> before = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao, Mockito.times(2)).compareAndSetMessageText(Mockito.eq(MESSAGE), before.capture(),
                after.capture());
        assertThat(after.getAllValues().get(1)).isEqualTo(stored);
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.any());
    }

    @Test
    void anyFailureToWriteAnExtractionLeavesTheButtonToUseAgain() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(extractionActions.write(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new NullPointerException("folder"));
        final String stored = storeResult(extraction());

        final AiWriteExtractionResult result = service.writeExtraction(CHAT, MESSAGE, "extract-1",
                new AiWriteExtractionRequest("Logons extraction", null, null, "hash"));

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).isEqualTo("The extraction couldn't be written: folder");
        final ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao, Mockito.times(2)).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.any(),
                after.capture());
        assertThat(after.getAllValues().get(1)).isEqualTo(stored);
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.WRITE_EXTRACTION_COMMAND), Mockito.anyString(),
                Mockito.eq("The extraction couldn't be written: folder"), Mockito.eq(false));
    }

    @Test
    void aButtonChangedSinceItWasClaimedIsStillMadePendingAgain() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(extractionActions.write(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("No"));
        // Claimed; the message then changes, so it can't simply be put back; it is made pending in it as it is.
        Mockito.when(aiDao.compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(true, false, true);
        storeResult(extraction());

        service.writeExtraction(CHAT, MESSAGE, "extract-1", new AiWriteExtractionRequest("x", null, null, "h"));

        final ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao, Mockito.times(3)).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.any(),
                after.capture());
        assertThat(JsonUtil.readValue(after.getAllValues().get(2), ToolResultRecord.class).userActions())
                .singleElement()
                .extracting(UserActionRecord::status)
                .isEqualTo(Status.PENDING);
    }

    @Test
    void anExpiredExtractionsButtonWritesNothing() {
        final UserActionRecord action = extraction();
        storeResult(new UserActionRecord(action.id(), action.action(), action.label(), null, null, null,
                action.doc(), action.request(), List.of(), 1L, Status.PENDING, null, action.name(), null, null));

        assertThat(service.writeExtraction(CHAT, MESSAGE, "extract-1", new AiWriteExtractionRequest("x", null,
                null, "h")).getMessage()).contains("has expired");
        Mockito.verifyNoInteractions(extractionActions);
    }

    @Test
    void aChangeToAnExtractionPipelineIsShownThenWrittenInPlaceOnce() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        final DocRef pipeline = new DocRef("Pipeline", "p1", "Logons extraction");
        final DocRef xslt = new DocRef("XSLT", "x1", "Logons extraction");
        final AiChangeExtraction shown = new AiChangeExtraction(DASHBOARD, "table-1", "Events", pipeline, xslt,
                "<a/>", "<b/>", "Written again", List.of(), List.of(), "hash");
        Mockito.when(extractionActions.prepareChange(Mockito.eq(DASHBOARD), Mockito.any(), Mockito.any()))
                .thenReturn(shown);
        Mockito.when(extractionActions.writeChange(Mockito.eq(DASHBOARD), Mockito.any(), Mockito.eq("hash"),
                Mockito.any()))
                .thenReturn(new ExtractionActions.Written(pipeline, xslt, "[]"));
        final String stored = storeResult(change());

        // Shown: nothing written.
        assertThat(service.doUserAction(CHAT, MESSAGE, "change-1").getChangeExtraction()).isSameAs(shown);
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.any(),
                Mockito.any());

        final AiWriteExtractionResult result = service.writeExtraction(CHAT, MESSAGE, "change-1",
                new AiWriteExtractionRequest(null, null, null, "hash"));

        assertThat(result.isDone()).isTrue();
        assertThat(result.getMessage()).isEqualTo(UserActionService.CHANGED_EXTRACTION);
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(stored), written.capture());
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .singleElement()
                .extracting(UserActionRecord::status)
                .isEqualTo(Status.DONE);
        Mockito.verify(extractionActions, Mockito.never()).write(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any());
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(xslt),
                Mockito.eq(UserActionService.WRITE_EXTRACTION_COMMAND), Mockito.anyString(),
                Mockito.contains("Changed XSLT 'Logons extraction' of pipeline 'Logons extraction' in place"),
                Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.contains("I confirmed the change"));
    }

    @Test
    void usingOneOfAnExtractionsTwoButtonsMakesTheOtherUnusable() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        final DocRef pipeline = new DocRef("Pipeline", "p2", "New");
        Mockito.when(extractionActions.write(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new ExtractionActions.Written(pipeline, new DocRef("XSLT", "x2", "New"), "[]"));
        // Offered together for the same extraction: change in place, or a new child.
        final UserActionRecord change = change();
        final UserActionRecord create = new UserActionRecord("create-1", UserActions.CREATE_EXTRACTION_PIPELINE,
                "Create", null, null, null, DASHBOARD, change.request(), List.of(), Long.MAX_VALUE, Status.PENDING,
                null, "New", null, null);
        final String json = JsonUtil.writeValueAsString(new ToolResultRecord("c1", "propose_extraction_pipeline",
                false, "offered", null, 7, 1, List.of(change, create)), false);
        Mockito.when(aiDao.getMessagesSince(CHAT, MESSAGE - 1)).thenReturn(List.of(
                new AiChatMessage(MESSAGE, CHAT, 0L, AiMessageType.TOOL_RESULT, null, json, 1, null)));

        assertThat(service.writeExtraction(CHAT, MESSAGE, "create-1", new AiWriteExtractionRequest("New", null,
                null, "h")).isDone()).isTrue();

        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(json), written.capture());
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .extracting(UserActionRecord::id, UserActionRecord::status, UserActionRecord::outcome)
                .containsExactly(
                        tuple("change-1", Status.DONE,
                                UserActionService.OTHER_EXTRACTION_USED),
                        tuple("create-1", Status.DONE,
                                UserActionService.WROTE_EXTRACTION));

        // Its sibling, clicked now, says why it can't be used.
        Mockito.when(aiDao.getMessagesSince(CHAT, MESSAGE - 1)).thenReturn(List.of(
                new AiChatMessage(MESSAGE, CHAT, 0L, AiMessageType.TOOL_RESULT, null, written.getValue(), 1, null)));
        assertThat(service.doUserAction(CHAT, MESSAGE, "change-1").getMessage())
                .isEqualTo(UserActionService.OTHER_EXTRACTION_USED);
        Mockito.verify(extractionActions, Mockito.never()).prepareChange(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void aChangeToAnXsltChangedSinceIsntShown() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(extractionActions.prepareChange(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("XSLT 'X' has changed since this was proposed"));
        storeResult(change());

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "change-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("has changed since this was proposed");
    }

    @Test
    void onlyAnExtractionsButtonWritesOne() {
        storeResult(dashboardChange());

        assertThat(service.writeExtraction(CHAT, MESSAGE, "dash-1", new AiWriteExtractionRequest("x", null, null,
                "h")).isDone()).isFalse();
        storeResult(extraction().withStatus(Status.DONE, "Written"));
        assertThat(service.writeExtraction(CHAT, MESSAGE, "extract-1", new AiWriteExtractionRequest("x", null,
                null, "h")).getMessage()).isEqualTo("This has already been done.");
        Mockito.verifyNoInteractions(extractionActions);
    }

    @Test
    void aDashboardChangeIsAppliedOnce() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);
        storeResult(dashboardChange().withStatus(Status.DONE, UserActionService.APPLIED_DASHBOARD));

        assertThat(service.doUserAction(CHAT, MESSAGE, "dash-1").getMessage()).isEqualTo("This has already been done.");
    }

    @Test
    void aCodeChangeIsNotAppliedWithoutEdit() {
        Mockito.when(lookup.find(XSLT)).thenReturn(Optional.of(XSLT));
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(false);
        storeResult(codeChange(Status.PENDING));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "code-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("permission to edit XSLT 'Event translation'");
        // The button stays, in case the user is given Edit.
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString());
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(XSLT),
                Mockito.eq(UserActionService.APPLY_COMMAND), Mockito.anyString(), Mockito.contains("permission"),
                Mockito.eq(false));
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void aCodeChangeForADocumentTheUserCanNoLongerSeeIsNotApplied() {
        Mockito.when(lookup.find(XSLT)).thenReturn(Optional.empty());
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(true);
        storeResult(codeChange(Status.PENDING));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "code-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("no longer exists, or you cannot see it");
    }

    @Test
    void undoingAnAppliedCodeChangeIsRecordedAndOffersTheChangeAgain() {
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(true);
        final String stored = storeResult(codeChange(Status.DONE));

        final AiUserActionResult result = service.undoUserAction(CHAT, MESSAGE, "code-1");

        assertThat(result.isDone()).isTrue();
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(stored), written.capture());
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .singleElement()
                .extracting(UserActionRecord::status)
                .isEqualTo(Status.PENDING);
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(XSLT),
                Mockito.eq(UserActionService.UNDO_COMMAND), Mockito.anyString(),
                Mockito.eq("Undid an applied change: +2 −1 lines"), Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.contains("I undid the proposed change"));
    }

    @Test
    void aQueryChangeIsAppliedAndUndoneAsACodeChangeIsWithEditOnTheQuery() {
        final DocRef query = new DocRef("Query", "7c1d3c2e-8f4a-4b9c-a1d2-3e4f5a6b7c8d", "Twitter tweets");
        Mockito.when(lookup.find(query)).thenReturn(Optional.of(query));
        Mockito.when(lookup.canEdit(query)).thenReturn(true);
        storeResult(new UserActionRecord("query-1", UserActions.APPLY_QUERY_CHANGE, "Change to Query", null, null,
                null, query, "from v\nselect a, b", List.of(), Long.MAX_VALUE, Status.PENDING, null, null,
                new UserActionRecord.CodeChange(null, null, "from v\nselect a", "0".repeat(64), "h", 1, 1)));

        final AiUserActionResult applied = service.doUserAction(CHAT, MESSAGE, "query-1");

        assertThat(applied.isDone()).isTrue();
        assertThat(applied.getMessage()).isEqualTo("Applied, not saved");
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(query),
                Mockito.eq(UserActionService.APPLY_COMMAND), Mockito.eq("Query 'Twitter tweets'"),
                Mockito.eq("Applied a proposed change, not saved: +1 −1 lines"), Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.eq("(With a button in the chat) I applied the proposed change to Query 'Twitter tweets' "
                           + "(+1 −1 lines). It is not saved."));

        // Without Edit, it isn't applied.
        Mockito.when(lookup.canEdit(query)).thenReturn(false);
        storeResult(new UserActionRecord("query-1", UserActions.APPLY_QUERY_CHANGE, "Change to Query", null, null,
                null, query, "from v\nselect a, b", List.of(), Long.MAX_VALUE, Status.PENDING, null, null,
                new UserActionRecord.CodeChange(null, null, "from v\nselect a", "0".repeat(64), "h", 1, 1)));
        assertThat(service.doUserAction(CHAT, MESSAGE, "query-1").isDone()).isFalse();

        // Once applied, it can be undone.
        Mockito.when(lookup.canEdit(query)).thenReturn(true);
        storeResult(new UserActionRecord("query-1", UserActions.APPLY_QUERY_CHANGE, "Change to Query", null, null,
                null, query, "from v\nselect a, b", List.of(), Long.MAX_VALUE, Status.DONE, "Applied, not saved",
                null, new UserActionRecord.CodeChange(null, null, "from v\nselect a", "0".repeat(64), "h", 1, 1)));
        assertThat(service.undoUserAction(CHAT, MESSAGE, "query-1").isDone()).isTrue();
    }

    @Test
    void aDocumentationChangeIsAppliedAndUndoneWithEditOnTheDocument() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);
        storeResult(documentationChange(Status.PENDING, "snap-hash"));

        final AiUserActionResult applied = service.doUserAction(CHAT, MESSAGE, "docs-1");

        assertThat(applied.isDone()).isTrue();
        assertThat(applied.getMessage()).isEqualTo("Applied, not saved");
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.APPLY_DOCUMENTATION_COMMAND),
                Mockito.eq("the documentation of Dashboard 'Logons'"),
                Mockito.eq("Applied a proposed change, not saved: +1 −1 lines"), Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.eq("(With a button in the chat) I applied the proposed change to the documentation of "
                           + "Dashboard 'Logons' (+1 −1 lines). It is not saved."));
        // Proposed against the tab's text as the user had it, which only the browser can check.
        Mockito.verify(lookup, Mockito.never()).savedDocumentation(Mockito.any());

        storeResult(documentationChange(Status.DONE, "snap-hash"));
        assertThat(service.undoUserAction(CHAT, MESSAGE, "docs-1").isDone()).isTrue();
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.UNDO_DOCUMENTATION_COMMAND),
                Mockito.eq("the documentation of Dashboard 'Logons'"),
                Mockito.eq("Undid an applied change: +1 −1 lines"), Mockito.eq(true));
    }

    @Test
    void aDocumentationChangeIsNotAppliedWithoutEdit() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(false);
        storeResult(documentationChange(Status.PENDING, "snap-hash"));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "docs-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("You don't have permission to edit Dashboard 'Logons'");
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.APPLY_DOCUMENTATION_COMMAND), Mockito.anyString(),
                Mockito.contains("permission"), Mockito.eq(false));
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    void aDocumentationChangeToTheSavedTextIsNotAppliedOnceThatHasChanged() {
        Mockito.when(lookup.find(DASHBOARD)).thenReturn(Optional.of(DASHBOARD));
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);

        // Saved with other text since it was proposed.
        Mockito.when(lookup.savedDocumentation(DASHBOARD)).thenReturn(Optional.of("Someone else's text"));
        storeResult(documentationChange(Status.PENDING, null));
        final AiUserActionResult stale = service.doUserAction(CHAT, MESSAGE, "docs-1");
        assertThat(stale.isDone()).isFalse();
        assertThat(stale.getMessage()).isEqualTo(UserActionService.DOCUMENTATION_CHANGED);
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.APPLY_DOCUMENTATION_COMMAND), Mockito.anyString(),
                Mockito.eq(UserActionService.DOCUMENTATION_CHANGED), Mockito.eq(false));

        // No longer readable.
        Mockito.when(lookup.savedDocumentation(DASHBOARD)).thenReturn(Optional.empty());
        assertThat(service.doUserAction(CHAT, MESSAGE, "docs-1").isDone()).isFalse();

        // As it was proposed against.
        Mockito.when(lookup.savedDocumentation(DASHBOARD)).thenReturn(Optional.of("Old text"));
        assertThat(service.doUserAction(CHAT, MESSAGE, "docs-1").isDone()).isTrue();
    }

    @Test
    void undoingAnAppliedDashboardChangeIsRecordedAndOffersTheChangeAgain() {
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);
        final String stored = storeResult(dashboardChange().withStatus(Status.DONE,
                UserActionService.APPLIED_DASHBOARD));

        final AiUserActionResult result = service.undoUserAction(CHAT, MESSAGE, "dash-1");

        assertThat(result.isDone()).isTrue();
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiDao).compareAndSetMessageText(Mockito.eq(MESSAGE), Mockito.eq(stored), written.capture());
        assertThat(JsonUtil.readValue(written.getValue(), ToolResultRecord.class).userActions())
                .singleElement()
                .extracting(UserActionRecord::status)
                .isEqualTo(Status.PENDING);
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.UNDO_DASHBOARD_COMMAND), Mockito.contains("table 'Events'"),
                Mockito.eq("Undid an applied change: Group by 'EventType' (level 1)"), Mockito.eq(true));
        Mockito.verify(aiService).storeMessage(Mockito.eq(CHAT), Mockito.eq(AiMessageType.STEER_MESSAGE),
                Mockito.contains("I undid the proposed change I had applied to table 'Events'"));
    }

    @Test
    void undoingADashboardChangeNeedsEditAndThatItWasApplied() {
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(false);
        storeResult(dashboardChange().withStatus(Status.DONE, UserActionService.APPLIED_DASHBOARD));

        final AiUserActionResult result = service.undoUserAction(CHAT, MESSAGE, "dash-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("permission to edit");
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.UNDO_DASHBOARD_COMMAND), Mockito.anyString(), Mockito.anyString(),
                Mockito.eq(false));

        // Not yet applied.
        Mockito.when(lookup.canEdit(DASHBOARD)).thenReturn(true);
        storeResult(dashboardChange());
        assertThat(service.undoUserAction(CHAT, MESSAGE, "dash-1").getMessage())
                .contains("no applied change to undo");
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    void onlyAnAppliedCodeChangeCanBeUndone() {
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(true);
        storeResult(codeChange(Status.PENDING));

        assertThat(service.undoUserAction(CHAT, MESSAGE, "code-1").getMessage())
                .contains("no applied change to undo");

        storeResult(enable(Status.DONE, Long.MAX_VALUE));
        assertThat(service.undoUserAction(CHAT, MESSAGE, "enable-31").isDone()).isFalse();
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    void undoingNeedsEditAndTheChatsOwner() {
        Mockito.when(lookup.canEdit(XSLT)).thenReturn(false);
        storeResult(codeChange(Status.DONE));

        final AiUserActionResult result = service.undoUserAction(CHAT, MESSAGE, "code-1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("Ctrl+Z");
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(XSLT),
                Mockito.eq(UserActionService.UNDO_COMMAND), Mockito.anyString(), Mockito.contains("permission"),
                Mockito.eq(false));

        Mockito.doThrow(new AuthenticationException("Not your chat")).when(aiService).verifyOwnership(CHAT);
        assertThatThrownBy(() -> service.undoUserAction(CHAT, MESSAGE, "code-1"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void createVisualisationGivesTheBrowserWhatToCreate() {
        final DocRef dashboard = new DocRef("Dashboard", "d", "Chats");
        Mockito.when(lookup.find(dashboard)).thenReturn(Optional.of(dashboard));
        final AiCreateVisualisation create = new AiCreateVisualisation("Chat user connections", null,
                "visualisations.EChartsGeneric", new DocRef("Script", "s", "ECharts Generic"), "{}", "Generated",
                dashboard, "table-1", "Messages", "Chat user connections", "{}", "below", "abc", null, null, null,
                null, null, null);
        Mockito.when(visualisationActions.prepare(dashboard, "Chat user connections", "{\"kind\":\"graph\"}"))
                .thenReturn(create);
        storeResult(createVisualisation(dashboard));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "v1");

        assertThat(result.isDone()).isTrue();
        assertThat(result.getCreateVisualisation()).isSameAs(create);
        // The button can be used again (e.g. after the dialog was cancelled), so the result isn't changed.
        Mockito.verify(aiDao, Mockito.never()).compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString());
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)),
                Mockito.eq(UserActions.CREATE_VISUALISATION), Mockito.anyString(),
                Mockito.eq("Opened the New Visualisation dialog"), Mockito.eq(true));
    }

    @Test
    void createVisualisationSaysWhyItCantBeMadeNow() {
        final DocRef dashboard = new DocRef("Dashboard", "d", "Chats");
        Mockito.when(lookup.find(dashboard)).thenReturn(Optional.of(dashboard));
        Mockito.when(visualisationActions.prepare(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("ECharts is no longer installed as a Script doc"));
        storeResult(createVisualisation(dashboard));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "v1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).isEqualTo("ECharts is no longer installed as a Script doc");
        assertThat(result.getCreateVisualisation()).isNull();
        Mockito.verify(auditLog).userAction(Mockito.eq(ToolSession.chat(CHAT)),
                Mockito.eq(UserActions.CREATE_VISUALISATION), Mockito.anyString(),
                Mockito.contains("no longer installed"), Mockito.eq(false));
    }

    @Test
    void createVisualisationIsRefusedOnceTheDashboardIsGone() {
        final DocRef dashboard = new DocRef("Dashboard", "d", "Chats");
        Mockito.when(lookup.find(dashboard)).thenReturn(Optional.empty());
        storeResult(createVisualisation(dashboard));

        final AiUserActionResult result = service.doUserAction(CHAT, MESSAGE, "v1");

        assertThat(result.isDone()).isFalse();
        assertThat(result.getMessage()).contains("no longer exists");
        Mockito.verifyNoInteractions(visualisationActions);
    }

    private static UserActionRecord createVisualisation(final DocRef dashboard) {
        return new UserActionRecord("v1", UserActions.CREATE_VISUALISATION,
                "Create visualisation 'Chat user connections'", null, null, null, dashboard,
                "{\"kind\":\"graph\"}", List.of(), Long.MAX_VALUE, Status.PENDING, null, "Chat user connections");
    }

    private static UserActionRecord createDashboard(final String query) {
        return new UserActionRecord("d1", UserActions.CREATE_DASHBOARD, "Create dashboard 'Windows users'", null,
                null, null, new DocRef("View", "v", "V"), query, List.of(), Long.MAX_VALUE, Status.PENDING, null,
                "Windows users", null, null, new UserActionRecord.NewDashboard(DashboardBuilder.CLASSIC, true,
                List.of()));
    }

    private String storeResult(final UserActionRecord action) {
        final String json = JsonUtil.writeValueAsString(new ToolResultRecord("c1", "fastmcp__create_filter", false,
                "created", null, 7, 1, List.of(action)), false);
        Mockito.when(aiDao.getMessagesSince(CHAT, MESSAGE - 1)).thenReturn(List.of(
                new AiChatMessage(MESSAGE, CHAT, 0L, AiMessageType.TOOL_RESULT, null, json, 1, null)));
        return json;
    }

    private static UserActionRecord enable(final Status status, final long expiresMs) {
        return new UserActionRecord("enable-31", UserActions.ENABLE_PROCESSOR_FILTER,
                "Enable processor filter 31 on pipeline 'Acme-Events'", null, null, 31, PIPELINE, null, List.of(),
                expiresMs, status, null);
    }

    private static UserActionRecord extraction() {
        final ExtractionPlan plan = new ExtractionPlan("Events", "table-1", new ExtractionPlan.Sample(7, 1),
                List.of(new ExtractionField("UserId", "EventSource/User/Id", null)), List.of());
        return new UserActionRecord("extract-1", UserActions.CREATE_EXTRACTION_PIPELINE,
                "Create extraction pipeline 'Logons extraction'", null, null, null, DASHBOARD, plan.toJson(),
                List.of(), Long.MAX_VALUE, Status.PENDING, null, "Logons extraction", null, null);
    }

    private static UserActionRecord change() {
        final ExtractionPlan plan = new ExtractionPlan("Events", "table-1", new ExtractionPlan.Sample(7, 1),
                List.of(new ExtractionField("UserId", "EventSource/User/Id", null)), List.of(),
                new ExtractionPlan.Existing(new DocRef("Pipeline", "p1", "Logons extraction"), "xsltFilter",
                        new DocRef("XSLT", "x1", "Logons extraction"), "0".repeat(64)));
        return new UserActionRecord("change-1", UserActions.CHANGE_EXTRACTION_PIPELINE,
                "Change extraction pipeline 'Logons extraction'", null, null, null, DASHBOARD, plan.toJson(),
                List.of(), Long.MAX_VALUE, Status.PENDING, null, "Logons extraction", null, null);
    }

    private static UserActionRecord createQuery(final String query) {
        return new UserActionRecord("q1", UserActions.CREATE_QUERY, "Create query 'Windows users'", null, null,
                null, null, query, List.of(), Long.MAX_VALUE, Status.PENDING, null, "Windows users");
    }

    private static UserActionRecord codeChange(final Status status) {
        return new UserActionRecord("code-1", UserActions.APPLY_CODE_CHANGE, "Change to XSLT 'Event translation'",
                "Lower-cases the user name.", null, null, XSLT, "<b/>\n<c/>", List.of(), Long.MAX_VALUE, status, null,
                null, new UserActionRecord.CodeChange(PIPELINE, "xsltFilter", "<a/>", "0".repeat(64), "h", 2, 1));
    }

    private static UserActionRecord codeChange(final Status status, final String base, final String proposed) {
        return new UserActionRecord("code-1", UserActions.APPLY_CODE_CHANGE, "Change to XSLT 'Event translation'",
                null, null, null, XSLT, proposed, List.of(), Long.MAX_VALUE, status, null, null,
                new UserActionRecord.CodeChange(PIPELINE, "xsltFilter", base, "0".repeat(64), "h", 1, 2));
    }

    // A documentation change, proposed against the attached tab's text (with the snapshot's hash) or, with no
    // snapshot hash, against the documentation as saved.
    private static UserActionRecord documentationChange(final Status status, final String snapshotHash) {
        return new UserActionRecord("docs-1", UserActions.APPLY_DOCUMENTATION_CHANGE,
                "Change the documentation of Dashboard 'Logons': +1 −1 lines", null, null, null, DASHBOARD,
                "New text", List.of(), Long.MAX_VALUE, status, null, null,
                new UserActionRecord.CodeChange(null, null, "Old text", UserActionService.sha256("Old text"),
                        snapshotHash, 1, 1));
    }

    private static UserActionRecord dashboardChange() {
        return dashboardChange(null);
    }

    private static UserActionRecord dashboardChange(final List<DocRef> charts) {
        return new UserActionRecord("dash-1", UserActions.APPLY_DASHBOARD_CHANGE,
                "Change to table 'Events' on dashboard 'Logons': 1 change(s)", null, null, null, DASHBOARD, null,
                List.of(), Long.MAX_VALUE, Status.PENDING, null, null, null,
                new UserActionRecord.DashboardChange("Events", "table-1", "abc", true,
                        List.of(Map.of("op", "group", "column", "EventType", "level", 1)),
                        List.of("Group by 'EventType' (level 1)"), charts));
    }

    private static ProcessorFilter filter(final boolean enabled) {
        return ProcessorFilter.builder()
                .id(31)
                .pipelineUuid(PIPELINE_UUID)
                .enabled(enabled)
                .build();
    }
}
