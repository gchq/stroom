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
import stroom.docref.DocRef;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.exception.AuthenticationException;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserActionService {

    private static final int CHAT = 7;
    private static final int MESSAGE = 50;
    private static final String PIPELINE_UUID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";
    private static final DocRef PIPELINE = new DocRef("Pipeline", PIPELINE_UUID, "Acme-Events");

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final UserActionLookup lookup = Mockito.mock(UserActionLookup.class);
    private final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final UserActionService service = new UserActionService(aiService, aiDao, new UserActions(lookup),
            lookup, processorFilterService, auditLog);

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
    void anUnknownActionOrMessageDoesNothing() {
        storeResult(enable(Status.PENDING, Long.MAX_VALUE));

        assertThat(service.doUserAction(CHAT, MESSAGE, "other").getMessage()).contains("not in the chat");
        assertThat(service.doUserAction(CHAT, MESSAGE + 1, "enable-31").getMessage()).contains("not in the chat");
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

    private static ProcessorFilter filter(final boolean enabled) {
        return ProcessorFilter.builder()
                .id(31)
                .pipelineUuid(PIPELINE_UUID)
                .enabled(enabled)
                .build();
    }
}
