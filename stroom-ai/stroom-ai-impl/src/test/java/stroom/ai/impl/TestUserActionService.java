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
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiUserActionResult;
import stroom.dashboard.shared.DashboardConfig;
import stroom.docref.DocRef;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.exception.AuthenticationException;
import stroom.util.json.JsonUtil;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private final UserActionService service = new UserActionService(aiService, aiDao, new UserActions(lookup),
            lookup, processorFilterService, auditLog, dashboardBuilder);

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
        Mockito.verify(auditLog).userAction(Mockito.eq(CHAT), Mockito.eq("enable_processor_filter"),
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
        Mockito.verify(auditLog).userAction(Mockito.eq(CHAT), Mockito.anyString(), Mockito.anyString(),
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
        Mockito.verify(auditLog).userAction(Mockito.eq(CHAT), Mockito.eq(UserActions.CREATE_QUERY),
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
        Mockito.verify(auditLog).userAction(Mockito.eq(CHAT), Mockito.eq(UserActions.CREATE_QUERY),
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
        Mockito.verify(auditLog).userAction(Mockito.eq(CHAT), Mockito.eq(UserActions.CREATE_DASHBOARD),
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
        Mockito.verify(auditLog).userAction(Mockito.eq(CHAT), Mockito.eq(UserActions.CREATE_DASHBOARD),
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(XSLT),
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(XSLT),
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(DASHBOARD),
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(DASHBOARD),
                Mockito.eq(UserActionService.APPLY_DASHBOARD_COMMAND), Mockito.anyString(), Mockito.anyString(),
                Mockito.eq(false));
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.any());
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(XSLT),
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(XSLT),
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(query),
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
        Mockito.verify(auditLog).processed(Mockito.eq(CHAT), Mockito.eq(XSLT),
                Mockito.eq(UserActionService.UNDO_COMMAND), Mockito.anyString(), Mockito.contains("permission"),
                Mockito.eq(false));

        Mockito.doThrow(new AuthenticationException("Not your chat")).when(aiService).verifyOwnership(CHAT);
        assertThatThrownBy(() -> service.undoUserAction(CHAT, MESSAGE, "code-1"))
                .isInstanceOf(AuthenticationException.class);
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

    private static UserActionRecord dashboardChange() {
        return new UserActionRecord("dash-1", UserActions.APPLY_DASHBOARD_CHANGE,
                "Change to table 'Events' on dashboard 'Logons': 1 change(s)", null, null, null, DASHBOARD, null,
                List.of(), Long.MAX_VALUE, Status.PENDING, null, null, null,
                new UserActionRecord.DashboardChange("Events", "table-1", "abc", true,
                        List.of(Map.of("op", "group", "column", "EventType", "level", 1)),
                        List.of("Group by 'EventType' (level 1)")));
    }

    private static ProcessorFilter filter(final boolean enabled) {
        return ProcessorFilter.builder()
                .id(31)
                .pipelineUuid(PIPELINE_UUID)
                .enabled(enabled)
                .build();
    }
}
